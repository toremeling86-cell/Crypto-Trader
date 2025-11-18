package com.cryptotrader.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cryptotrader.data.remote.claude.ClaudeApiService
import com.cryptotrader.data.remote.claude.dto.ClaudeMessage
import com.cryptotrader.data.remote.claude.dto.ClaudeRequest
import com.cryptotrader.domain.ai.AIContextBuilder
import com.cryptotrader.domain.model.OpportunityDTO
import com.cryptotrader.domain.model.TradingOpportunity
import com.cryptotrader.notifications.NotificationManager
import com.cryptotrader.utils.CryptoUtils
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.util.concurrent.TimeUnit

/**
 * AI Trading Advisor Worker
 *
 * Performs comprehensive multi-agent analysis to identify trading opportunities:
 *
 * WORKFLOW (6 phases):
 * 1. Pre-flight checks: Verify advisor enabled, API keys, emergency stop, daily limits
 * 2. Market data fetch: Gather current market conditions and portfolio status
 * 3. Agent analysis: Run multiple specialized AI agents (technical, sentiment, risk)
 * 4. Claude synthesis: Use Haiku to synthesize agent reports into opportunities
 * 5. Claude validation: Use Sonnet to validate high-confidence opportunities
 * 6. Notification: Alert user to validated opportunities (>75% confidence)
 *
 * FEATURES:
 * - Hourly execution via PeriodicWorkRequest
 * - Rate limiting: 1 per hour, 5 per day maximum
 * - Timeout: 5 minutes maximum per analysis
 * - Retry policy: Exponential backoff up to 3 attempts
 * - Data retention: 30 days for historical analysis
 * - Cost optimization: Haiku for synthesis, Sonnet only for validation
 * - Fallback mode: Continue without Claude if unavailable
 *
 * DEPENDENCIES (to be implemented):
 * - AdvisorAnalysisEntity: Database entity for storing complete analysis
 * - TradingOpportunityEntity: Database entity for identified opportunities
 * - AdvisorNotificationEntity: Database entity for notification tracking
 * - AIAdvisorRepository: Repository for database operations
 *
 * @see MarketAnalysisWorker for similar pattern
 * @see TradingWorker for trading execution pattern
 */
@HiltWorker
class AIAdvisorWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val claudeApi: ClaudeApiService,
    private val aiContextBuilder: AIContextBuilder,
    private val notificationManager: NotificationManager
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "ai_advisor_periodic"
        const val TAG = "AIAdvisor"

        // Rate limiting constants
        private const val MAX_ANALYSES_PER_HOUR = 1
        private const val MAX_ANALYSES_PER_DAY = 5
        private const val ANALYSIS_TIMEOUT_MINUTES = 5L

        // Confidence thresholds
        private const val MIN_NOTIFICATION_CONFIDENCE = 0.75 // 75%
        private const val MIN_RISK_REWARD_RATIO = 2.0 // 2:1

        // Data retention
        private const val DATA_RETENTION_DAYS = 30L

        // Claude models
        private const val MODEL_HAIKU = "claude-3-5-haiku-20241022" // Fast synthesis
        private const val MODEL_SONNET = "claude-sonnet-4-5-20250929" // Validation

        // Preference keys
        private const val PREF_ADVISOR_ENABLED = "ai_advisor_enabled"
        private const val PREF_LAST_ANALYSIS_TIME = "ai_advisor_last_analysis"
        private const val PREF_DAILY_ANALYSIS_COUNT = "ai_advisor_daily_count"
        private const val PREF_DAILY_RESET_TIME = "ai_advisor_daily_reset"
    }

    /**
     * Main worker execution method
     * Implements 6-phase analysis workflow with comprehensive error handling
     */
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        return@withContext try {
            Timber.i("AI Advisor Worker started (attempt ${runAttemptCount + 1})")

            // Apply 5-minute timeout to entire analysis
            withTimeout(TimeUnit.MINUTES.toMillis(ANALYSIS_TIMEOUT_MINUTES)) {
                executeAnalysis()
            }

        } catch (e: Exception) {
            Timber.e(e, "AI Advisor Worker failed")

            // Retry with exponential backoff up to 3 attempts
            if (runAttemptCount < 2) {
                Timber.w("Retrying AI Advisor (attempt ${runAttemptCount + 2}/3)")
                Result.retry()
            } else {
                Timber.e("AI Advisor Worker failed after 3 attempts")
                Result.failure()
            }
        }
    }

    /**
     * Execute complete 6-phase analysis workflow
     */
    private suspend fun executeAnalysis(): Result {
        // PHASE 1: Pre-flight Checks
        Timber.d("Phase 1/6: Pre-flight checks")
        val preFlightResult = performPreFlightChecks()
        if (!preFlightResult.success) {
            Timber.w("Pre-flight checks failed: ${preFlightResult.reason}")
            return Result.success() // Success to avoid retries for expected conditions
        }

        // PHASE 2: Market Data Fetch
        Timber.d("Phase 2/6: Fetching market data")
        val marketData = fetchMarketData()
        if (marketData == null) {
            Timber.e("Failed to fetch market data")
            return Result.retry()
        }

        // PHASE 3: Agent Analysis
        Timber.d("Phase 3/6: Running agent analysis")
        val agentReports = runAgentAnalysis(marketData)

        // PHASE 4: Claude Synthesis (Haiku - fast and cost-effective)
        Timber.d("Phase 4/6: Synthesizing opportunities with Claude Haiku")
        val synthesis = synthesizeWithClaude(marketData, agentReports)
        if (synthesis == null) {
            Timber.w("Claude synthesis failed, using fallback mode")
            return Result.success()
        }

        // PHASE 5: Claude Validation (Sonnet - only for high-confidence opportunities)
        Timber.d("Phase 5/6: Validating opportunities with Claude Sonnet")
        val validatedOpportunities = validateOpportunities(synthesis, marketData)

        // PHASE 6: Notifications
        Timber.d("Phase 6/6: Processing notifications")
        val notificationsSent = sendNotifications(validatedOpportunities)

        // Persist results to database (requires AIAdvisorRepository)
        saveAnalysisResults(marketData, agentReports, synthesis, validatedOpportunities)

        // Update rate limiting counters
        updateRateLimitCounters()

        // Cleanup old data
        cleanupOldData()

        Timber.i("AI Advisor analysis completed: ${validatedOpportunities.size} opportunities, $notificationsSent notifications sent")
        return Result.success()
    }

    /**
     * PHASE 1: Pre-flight Checks
     * Verify all conditions are met before running analysis
     */
    private fun performPreFlightChecks(): PreFlightResult {
        val prefs = CryptoUtils.getEncryptedPreferences(context)

        // Check 1: Advisor enabled
        val advisorEnabled = prefs.getBoolean(PREF_ADVISOR_ENABLED, true)
        if (!advisorEnabled) {
            return PreFlightResult(false, "AI Advisor is disabled")
        }

        // Check 2: Claude API key configured
        val apiKey = CryptoUtils.getClaudeApiKey(context)
        if (apiKey.isNullOrBlank()) {
            return PreFlightResult(false, "Claude API key not configured")
        }

        // Check 3: Emergency stop not active
        if (CryptoUtils.isEmergencyStopActive(context)) {
            return PreFlightResult(false, "Emergency stop is active")
        }

        // Check 4: Rate limiting - hourly check
        val lastAnalysisTime = prefs.getLong(PREF_LAST_ANALYSIS_TIME, 0L)
        val timeSinceLastAnalysis = System.currentTimeMillis() - lastAnalysisTime
        val oneHourInMillis = TimeUnit.HOURS.toMillis(1)

        if (timeSinceLastAnalysis < oneHourInMillis) {
            val minutesRemaining = TimeUnit.MILLISECONDS.toMinutes(oneHourInMillis - timeSinceLastAnalysis)
            return PreFlightResult(false, "Rate limit: Wait $minutesRemaining minutes")
        }

        // Check 5: Rate limiting - daily check
        val dailyResetTime = prefs.getLong(PREF_DAILY_RESET_TIME, 0L)
        val oneDayInMillis = TimeUnit.DAYS.toMillis(1)
        val needsReset = System.currentTimeMillis() - dailyResetTime > oneDayInMillis

        if (needsReset) {
            // Reset daily counter
            prefs.edit().apply {
                putInt(PREF_DAILY_ANALYSIS_COUNT, 0)
                putLong(PREF_DAILY_RESET_TIME, System.currentTimeMillis())
                apply()
            }
        }

        val dailyCount = prefs.getInt(PREF_DAILY_ANALYSIS_COUNT, 0)
        if (dailyCount >= MAX_ANALYSES_PER_DAY) {
            return PreFlightResult(false, "Daily limit reached: $dailyCount/$MAX_ANALYSES_PER_DAY")
        }

        return PreFlightResult(true, "All checks passed")
    }

    /**
     * PHASE 2: Market Data Fetch
     * Gather current market conditions and portfolio status
     */
    private suspend fun fetchMarketData(): MarketDataSnapshot? {
        return try {
            // Build comprehensive market context using existing infrastructure
            val marketContext = aiContextBuilder.buildFullContext(
                includeMarketData = true,
                includePortfolio = true,
                includeTrades = true
            )

            MarketDataSnapshot(
                timestamp = System.currentTimeMillis(),
                marketContext = marketContext,
                triggerType = "SCHEDULED"
            )
        } catch (e: Exception) {
            Timber.e(e, "Error fetching market data")
            null
        }
    }

    /**
     * PHASE 3: Agent Analysis
     * Run multiple specialized AI agents for comprehensive analysis
     *
     * NOTE: This is a simplified implementation. In production, this would:
     * - Run technical analysis agent (indicators, patterns, support/resistance)
     * - Run sentiment analysis agent (news, social media, market mood)
     * - Run risk analysis agent (volatility, correlations, exposure)
     * - Run fundamental analysis agent (on-chain metrics, project health)
     */
    private suspend fun runAgentAnalysis(marketData: MarketDataSnapshot): AgentReports {
        Timber.d("Running multi-agent analysis")

        // For now, we'll use a simplified approach where agents provide
        // structured observations that will be synthesized by Claude

        return AgentReports(
            technicalAgent = "Technical analysis results would go here",
            sentimentAgent = "Sentiment analysis results would go here",
            riskAgent = "Risk analysis results would go here",
            fundamentalAgent = "Fundamental analysis results would go here",
            timestamp = System.currentTimeMillis()
        )
    }

    /**
     * PHASE 4: Claude Synthesis (Haiku)
     * Use Claude Haiku to synthesize agent reports into trading opportunities
     * Haiku is faster and cheaper, perfect for initial synthesis
     */
    private suspend fun synthesizeWithClaude(
        marketData: MarketDataSnapshot,
        agentReports: AgentReports
    ): AnalysisSynthesis? {
        return try {
            val apiKey = CryptoUtils.getClaudeApiKey(context) ?: return null

            val systemPrompt = buildSynthesisPrompt()
            val userPrompt = buildSynthesisUserPrompt(marketData, agentReports)

            val request = ClaudeRequest(
                model = MODEL_HAIKU,
                maxTokens = 2048,
                messages = listOf(
                    ClaudeMessage(role = "user", content = userPrompt)
                ),
                temperature = 0.7,
                system = systemPrompt
            )

            val response = claudeApi.createMessage(apiKey = apiKey, request = request)

            if (!response.isSuccessful || response.body() == null) {
                Timber.e("Claude synthesis failed: ${response.code()}")
                return null
            }

            val responseText = response.body()!!.content.firstOrNull()?.text ?: ""
            parseSynthesisResponse(responseText)

        } catch (e: Exception) {
            Timber.e(e, "Error in Claude synthesis")
            null
        }
    }

    /**
     * PHASE 5: Claude Validation (Sonnet)
     * Use Claude Sonnet to validate high-confidence opportunities
     * Only called for opportunities that pass initial filters
     */
    private suspend fun validateOpportunities(
        synthesis: AnalysisSynthesis,
        marketData: MarketDataSnapshot
    ): List<TradingOpportunity> {
        val validatedOpportunities = mutableListOf<TradingOpportunity>()

        // Filter opportunities that meet minimum criteria before expensive validation
        val candidateOpportunities = synthesis.opportunities.filter { opp ->
            // Calculate risk/reward ratio from percentages
            val riskRewardRatio = opp.targetProfit / opp.stopLoss
            opp.confidence >= MIN_NOTIFICATION_CONFIDENCE &&
            riskRewardRatio >= MIN_RISK_REWARD_RATIO
        }

        if (candidateOpportunities.isEmpty()) {
            Timber.d("No opportunities meet minimum criteria for validation")
            return emptyList()
        }

        Timber.d("Validating ${candidateOpportunities.size} candidate opportunities with Sonnet")

        // Validate each opportunity with Claude Sonnet
        for (opportunity in candidateOpportunities) {
            try {
                val validated = validateWithSonnet(opportunity, marketData)
                if (validated != null && validated.isValid && validated.opportunity != null) {
                    validatedOpportunities.add(validated.opportunity)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error validating opportunity: ${opportunity.pair}")
            }
        }

        return validatedOpportunities
    }

    /**
     * Validate a single opportunity using Claude Sonnet
     */
    private suspend fun validateWithSonnet(
        opportunity: OpportunityDTO,
        marketData: MarketDataSnapshot
    ): ValidationResult? {
        return try {
            val apiKey = CryptoUtils.getClaudeApiKey(context) ?: return null

            val systemPrompt = buildValidationPrompt()
            val userPrompt = buildValidationUserPrompt(opportunity, marketData)

            val request = ClaudeRequest(
                model = MODEL_SONNET,
                maxTokens = 1024,
                messages = listOf(
                    ClaudeMessage(role = "user", content = userPrompt)
                ),
                temperature = 0.3, // Lower temperature for validation
                system = systemPrompt
            )

            val response = claudeApi.createMessage(apiKey = apiKey, request = request)

            if (!response.isSuccessful || response.body() == null) {
                Timber.w("Sonnet validation failed for ${opportunity.pair}")
                return null
            }

            val responseText = response.body()!!.content.firstOrNull()?.text ?: ""
            parseValidationResponse(responseText, opportunity)

        } catch (e: Exception) {
            Timber.e(e, "Error in Sonnet validation")
            null
        }
    }

    /**
     * PHASE 6: Notifications
     * Send notifications for validated opportunities
     */
    private suspend fun sendNotifications(opportunities: List<TradingOpportunity>): Int {
        var notificationsSent = 0

        for (opportunity in opportunities) {
            try {
                sendOpportunityNotification(opportunity)
                notificationsSent++
            } catch (e: Exception) {
                Timber.e(e, "Error sending notification for ${opportunity.asset}")
            }
        }

        return notificationsSent
    }

    /**
     * Send notification for a trading opportunity
     */
    private fun sendOpportunityNotification(opportunity: TradingOpportunity) {
        if (!notificationManager.hasNotificationPermission()) {
            Timber.w("Notification permission not granted")
            return
        }

        try {
            // Send notification using NotificationManager
            notificationManager.notifyTradingOpportunity(opportunity)

            Timber.i("Notification sent: ${opportunity.asset} ${opportunity.direction} - Confidence: ${(opportunity.confidence * 100).toInt()}%")
        } catch (e: Exception) {
            Timber.e(e, "Error creating opportunity notification")
        }
    }

    /**
     * Save analysis results to database
     * Requires: AIAdvisorRepository implementation
     */
    private suspend fun saveAnalysisResults(
        marketData: MarketDataSnapshot,
        agentReports: AgentReports,
        synthesis: AnalysisSynthesis?,
        opportunities: List<TradingOpportunity>
    ) {
        try {
            Timber.d("Saving analysis results to database")

            // TODO: Implement database persistence when AIAdvisorRepository is available
            // aiAdvisorRepository.insertAnalysis(AdvisorAnalysisEntity(...))
            // opportunities.forEach { aiAdvisorRepository.insertOpportunity(TradingOpportunityEntity(...)) }

        } catch (e: Exception) {
            Timber.e(e, "Error saving analysis results")
        }
    }

    /**
     * Update rate limiting counters
     */
    private fun updateRateLimitCounters() {
        val prefs = CryptoUtils.getEncryptedPreferences(context)
        prefs.edit().apply {
            putLong(PREF_LAST_ANALYSIS_TIME, System.currentTimeMillis())
            putInt(PREF_DAILY_ANALYSIS_COUNT, prefs.getInt(PREF_DAILY_ANALYSIS_COUNT, 0) + 1)
            apply()
        }
    }

    /**
     * Cleanup old analysis data (30 day retention)
     * Requires: AIAdvisorRepository implementation
     */
    private suspend fun cleanupOldData() {
        try {
            val cutoffTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(DATA_RETENTION_DAYS)
            Timber.d("Cleaning up analysis data older than $DATA_RETENTION_DAYS days")

            // TODO: Implement cleanup when AIAdvisorRepository is available
            // aiAdvisorRepository.deleteAnalysesOlderThan(cutoffTime)
            // aiAdvisorRepository.deleteOpportunitiesOlderThan(cutoffTime)

        } catch (e: Exception) {
            Timber.e(e, "Error cleaning up old data")
        }
    }

    // ==================== PROMPT TEMPLATES ====================

    /**
     * Build system prompt for opportunity synthesis (Haiku)
     */
    private fun buildSynthesisPrompt(): String {
        return """
            You are an EXPERT cryptocurrency trading advisor specializing in opportunity identification.

            Your role: Analyze market data and agent reports to identify HIGH-PROBABILITY trading opportunities.

            CRITICAL REQUIREMENTS:
            1. Minimum confidence: 75% (only report high-probability setups)
            2. Minimum risk/reward ratio: 2:1 (reward must be at least 2x the risk)
            3. Multi-timeframe validation: Opportunities must align across timeframes
            4. Position sizing: Maximum 2% risk per trade
            5. Clear entry, stop loss, and take profit levels

            Response format (JSON):
            {
              "opportunities": [
                {
                  "pair": "XXBTZUSD",
                  "direction": "LONG|SHORT",
                  "entryPrice": 65000.0,
                  "stopLoss": 64000.0,
                  "takeProfit": 67000.0,
                  "confidence": 0.82,
                  "riskRewardRatio": 2.0,
                  "reasoning": "Clear technical setup with...",
                  "timeframe": "4H",
                  "positionSizePercent": 2.0
                }
              ],
              "marketOverview": "Brief 2-3 sentence market summary",
              "riskLevel": "LOW|MEDIUM|HIGH"
            }

            IMPORTANT: Only include opportunities with >75% confidence and >2:1 risk/reward.
            If no opportunities meet criteria, return empty opportunities array.
        """.trimIndent()
    }

    /**
     * Build user prompt for synthesis
     */
    private fun buildSynthesisUserPrompt(
        marketData: MarketDataSnapshot,
        agentReports: AgentReports
    ): String {
        return """
            Analyze the following market data and agent reports to identify trading opportunities.

            MARKET DATA:
            ${marketData.marketContext}

            AGENT REPORTS:

            Technical Analysis:
            ${agentReports.technicalAgent}

            Sentiment Analysis:
            ${agentReports.sentimentAgent}

            Risk Analysis:
            ${agentReports.riskAgent}

            Fundamental Analysis:
            ${agentReports.fundamentalAgent}

            Identify high-probability trading opportunities. Respond with JSON only.
        """.trimIndent()
    }

    /**
     * Build system prompt for opportunity validation (Sonnet)
     */
    private fun buildValidationPrompt(): String {
        return """
            You are a SENIOR cryptocurrency trading risk manager validating trading opportunities.

            Your role: Critically evaluate trading opportunities to prevent false signals and poor risk/reward setups.

            Validation criteria:
            1. Technical validity: Is the setup technically sound?
            2. Risk assessment: Is the risk/reward truly favorable?
            3. Market context: Does the opportunity align with broader market conditions?
            4. Timing: Is this the optimal entry point?
            5. Competing factors: Are there conflicting signals?

            Response format (JSON):
            {
              "isValid": true|false,
              "confidence": 0.85,
              "concerns": ["List any concerns or red flags"],
              "strengths": ["List supporting factors"],
              "recommendation": "APPROVE|REJECT|WAIT",
              "reasoning": "Detailed explanation of validation decision"
            }

            Be conservative - only approve high-quality opportunities.
        """.trimIndent()
    }

    /**
     * Build user prompt for validation
     */
    private fun buildValidationUserPrompt(
        opportunity: OpportunityDTO,
        marketData: MarketDataSnapshot
    ): String {
        // Calculate actual prices from percentages
        val stopLossPrice = if (opportunity.direction == "LONG") {
            opportunity.entryPrice * (1 - opportunity.stopLoss / 100.0)
        } else {
            opportunity.entryPrice * (1 + opportunity.stopLoss / 100.0)
        }

        val takeProfitPrice = if (opportunity.direction == "LONG") {
            opportunity.entryPrice * (1 + opportunity.targetProfit / 100.0)
        } else {
            opportunity.entryPrice * (1 - opportunity.targetProfit / 100.0)
        }

        val riskRewardRatio = opportunity.targetProfit / opportunity.stopLoss

        return """
            Validate this trading opportunity:

            OPPORTUNITY:
            Pair: ${opportunity.pair}
            Direction: ${opportunity.direction}
            Entry: ${opportunity.entryPrice}
            Stop Loss: $stopLossPrice (${opportunity.stopLoss}%)
            Take Profit: $takeProfitPrice (${opportunity.targetProfit}%)
            Confidence: ${(opportunity.confidence * 100).toInt()}%
            Risk/Reward: ${String.format("%.2f", riskRewardRatio)}:1
            Time Horizon: ${opportunity.timeHorizon}
            Reasoning: ${opportunity.reasoning.joinToString("; ")}
            Risk Factors: ${opportunity.riskFactors.joinToString("; ")}

            MARKET CONTEXT:
            ${marketData.marketContext}

            Critically evaluate this opportunity. Should it be approved? Respond with JSON only.
        """.trimIndent()
    }

    // ==================== RESPONSE PARSING ====================

    /**
     * Parse synthesis response from Claude Haiku
     */
    private fun parseSynthesisResponse(response: String): AnalysisSynthesis? {
        return try {
            val jsonStart = response.indexOf("{")
            val jsonEnd = response.lastIndexOf("}") + 1

            if (jsonStart < 0 || jsonEnd <= jsonStart) {
                Timber.w("No JSON found in synthesis response")
                return null
            }

            val jsonString = response.substring(jsonStart, jsonEnd)
            val json = JSONObject(jsonString)

            val opportunitiesArray = json.optJSONArray("opportunities") ?: JSONArray()
            val opportunities = mutableListOf<OpportunityDTO>()

            for (i in 0 until opportunitiesArray.length()) {
                val oppJson = opportunitiesArray.getJSONObject(i)

                // Parse reasoning as list or single string
                val reasoningList = if (oppJson.has("reasoning")) {
                    val reasoningValue = oppJson.get("reasoning")
                    if (reasoningValue is JSONArray) {
                        (0 until reasoningValue.length()).map { reasoningValue.getString(it) }
                    } else {
                        listOf(reasoningValue.toString())
                    }
                } else emptyList()

                // Parse risk factors
                val riskFactorsList = if (oppJson.has("riskFactors")) {
                    val riskValue = oppJson.getJSONArray("riskFactors")
                    (0 until riskValue.length()).map { riskValue.getString(it) }
                } else emptyList()

                opportunities.add(
                    OpportunityDTO(
                        pair = oppJson.getString("pair"),
                        direction = oppJson.getString("direction"),
                        confidence = oppJson.getDouble("confidence"),
                        entryPrice = oppJson.getDouble("entryPrice"),
                        entryPriceMax = oppJson.optDouble("entryPriceMax", oppJson.getDouble("entryPrice") * 1.02),
                        targetProfit = oppJson.getDouble("targetProfit"), // Percentage
                        stopLoss = oppJson.getDouble("stopLoss"), // Percentage
                        timeHorizon = oppJson.optString("timeHorizon", "4H"),
                        reasoning = reasoningList,
                        riskFactors = riskFactorsList
                    )
                )
            }

            AnalysisSynthesis(
                opportunities = opportunities,
                marketOverview = json.optString("marketOverview", ""),
                riskLevel = json.optString("riskLevel", "MEDIUM"),
                timestamp = System.currentTimeMillis()
            )

        } catch (e: Exception) {
            Timber.e(e, "Error parsing synthesis response")
            null
        }
    }

    /**
     * Parse validation response from Claude Sonnet
     */
    private fun parseValidationResponse(response: String, opportunity: OpportunityDTO): ValidationResult? {
        return try {
            val jsonStart = response.indexOf("{")
            val jsonEnd = response.lastIndexOf("}") + 1

            if (jsonStart < 0 || jsonEnd <= jsonStart) {
                Timber.w("No JSON found in validation response")
                return null
            }

            val jsonString = response.substring(jsonStart, jsonEnd)
            val json = JSONObject(jsonString)

            val isValid = json.getBoolean("isValid")
            val validatedConfidence = json.getDouble("confidence")
            val recommendation = json.getString("recommendation")

            // Only approve if Sonnet explicitly recommends it
            val shouldApprove = isValid && recommendation == "APPROVE"

            if (shouldApprove) {
                // Convert DTO to domain model
                val baseOpportunity = opportunity.toDomain()

                // Parse additional Claude insights
                val concernsArray = json.optJSONArray("concerns")
                val concerns = if (concernsArray != null) {
                    (0 until concernsArray.length()).map { concernsArray.getString(it) }
                } else emptyList()

                val strengthsArray = json.optJSONArray("strengths")
                val strengths = if (strengthsArray != null) {
                    (0 until strengthsArray.length()).map { strengthsArray.getString(it) }
                } else emptyList()

                val validationReasoning = json.optString("reasoning", "")

                // Add Claude's validation insights to the opportunity
                ValidationResult(
                    isValid = true,
                    opportunity = baseOpportunity.copy(
                        confidence = validatedConfidence,  // Use Sonnet's validated confidence
                        claudeReasoning = validationReasoning,
                        claudeInsights = strengths,
                        claudeRisks = concerns,
                        timestamp = System.currentTimeMillis()
                    )
                )
            } else {
                Timber.d("Opportunity rejected by Sonnet: ${json.optString("reasoning")}")
                ValidationResult(isValid = false, opportunity = null)
            }

        } catch (e: Exception) {
            Timber.e(e, "Error parsing validation response")
            null
        }
    }

    // ==================== DATA CLASSES ====================

    /**
     * Pre-flight check result
     */
    private data class PreFlightResult(
        val success: Boolean,
        val reason: String
    )

    /**
     * Market data snapshot for analysis
     */
    private data class MarketDataSnapshot(
        val timestamp: Long,
        val marketContext: String,
        val triggerType: String
    )

    /**
     * Agent analysis reports
     * In production, these would contain structured data from specialized agents
     */
    private data class AgentReports(
        val technicalAgent: String,
        val sentimentAgent: String,
        val riskAgent: String,
        val fundamentalAgent: String,
        val timestamp: Long
    )

    /**
     * Analysis synthesis from Claude Haiku
     */
    private data class AnalysisSynthesis(
        val opportunities: List<OpportunityDTO>,
        val marketOverview: String,
        val riskLevel: String,
        val timestamp: Long
    )

    /**
     * Validation result from Claude Sonnet
     */
    private data class ValidationResult(
        val isValid: Boolean,
        val opportunity: TradingOpportunity?
    )
}
