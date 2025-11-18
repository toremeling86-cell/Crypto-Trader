package com.cryptotrader.domain.advisor

import android.content.Context
import com.cryptotrader.data.remote.claude.ClaudeApiService
import com.cryptotrader.data.remote.claude.dto.ClaudeMessage
import com.cryptotrader.data.remote.claude.dto.ClaudeRequest
import com.cryptotrader.domain.model.AnalysisStatus
import com.cryptotrader.domain.model.ExpertReport
import com.cryptotrader.domain.model.MarketOutlook
import com.cryptotrader.domain.model.MetaAnalysis
import com.cryptotrader.domain.model.RecommendedStrategy
import com.cryptotrader.domain.model.RiskLevel
import com.cryptotrader.utils.CryptoUtils
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Meta-Analysis Agent
 *
 * Uses Claude Opus 4.1 to perform comprehensive analysis of multiple expert trading reports
 * Synthesizes insights, identifies consensus/contradictions, and recommends trading strategies
 *
 * Features:
 * - Deep multi-report analysis using Opus 4.1
 * - Structured JSON output parsing
 * - Strategy recommendation generation
 * - Confidence scoring and risk assessment
 * - Timeout handling (60s for deep analysis)
 */
@Singleton
class MetaAnalysisAgent @Inject constructor(
    private val claudeApi: ClaudeApiService,
    @ApplicationContext private val context: Context,
    private val moshi: Moshi
) {

    private val OPUS_MODEL = "claude-opus-4-20250514" // Opus 4.1
    private val ANALYSIS_TIMEOUT_MS = 60_000L // 60 seconds for deep analysis
    private val MAX_TOKENS = 8192 // Larger context for comprehensive analysis

    /**
     * Perform meta-analysis on multiple expert reports with temporal weighting
     *
     * @param allReports All available expert reports
     * @param timeframe Analysis timeframe for rolling window filtering
     * @return Result containing MetaAnalysis or error
     */
    suspend fun analyzeReports(
        allReports: List<ExpertReport>,
        timeframe: com.cryptotrader.domain.model.AnalysisTimeframe = com.cryptotrader.domain.model.AnalysisTimeframe.WEEKLY
    ): Result<MetaAnalysis> = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        try {
            // Phase 3B: Apply rolling window filtering based on timeframe
            val cutoffTime = if (timeframe.daysBack == Int.MAX_VALUE) {
                0L // Include all reports
            } else {
                System.currentTimeMillis() - (timeframe.daysBack * 24 * 60 * 60 * 1000L)
            }

            // Filter reports within timeframe window
            val reports = allReports.filter { report ->
                val reportDate = report.publishedDate ?: report.uploadDate
                reportDate >= cutoffTime
            }.sortedByDescending { it.publishedDate ?: it.uploadDate } // Newest first

            Timber.i("Starting meta-analysis using ${timeframe.displayName} timeframe")
            Timber.i("Filtered ${reports.size} reports from ${allReports.size} total (cutoff: ${formatTimestamp(cutoffTime)})")

            if (reports.isEmpty()) {
                return@withContext Result.failure(IllegalArgumentException("No reports found within ${timeframe.displayName} timeframe"))
            }

            // Calculate temporal weights (newer reports get higher weight)
            val reportWeights = calculateTemporalWeights(reports)
            val avgWeight = reportWeights.values.average()
            Timber.d("Temporal weighting applied. Average weight: %.3f".format(avgWeight))

            // Build comprehensive prompt with temporal weighting
            val prompt = buildMetaAnalysisPrompt(reports, reportWeights, timeframe)
            val systemPrompt = buildSystemPrompt()

            // Get API key
            val apiKey = getClaudeApiKey()
            if (apiKey.isNullOrEmpty()) {
                return@withContext Result.failure(IllegalStateException("Claude API key not configured"))
            }

            // Create request for Opus 4.1
            val request = ClaudeRequest(
                model = OPUS_MODEL,
                maxTokens = MAX_TOKENS,
                messages = listOf(
                    ClaudeMessage(role = "user", content = prompt)
                ),
                temperature = 0.3, // Lower temperature for more focused analysis
                system = systemPrompt
            )

            // Call API with timeout
            val response = withTimeout(ANALYSIS_TIMEOUT_MS) {
                claudeApi.createMessage(
                    apiKey = apiKey,
                    version = "2023-06-01",
                    request = request
                )
            }

            if (!response.isSuccessful || response.body() == null) {
                val errorMsg = "Claude API error: ${response.code()} - ${response.message()}"
                Timber.e(errorMsg)
                return@withContext Result.failure(Exception(errorMsg))
            }

            val claudeResponse = response.body()!!
            val analysisText = claudeResponse.content.firstOrNull()?.text
                ?: return@withContext Result.failure(Exception("Empty response from Claude"))

            Timber.d("Received Opus 4.1 response: ${analysisText.take(200)}...")

            // Parse structured JSON response
            val parsedResult = parseMetaAnalysisResponse(analysisText)
            if (parsedResult.isFailure) {
                return@withContext Result.failure(parsedResult.exceptionOrNull()!!)
            }

            val analysisResult = parsedResult.getOrNull()!!

            // Build MetaAnalysis object with temporal info
            val analysisTimeMs = System.currentTimeMillis() - startTime
            val reportDates = reports.map { it.publishedDate ?: it.uploadDate }
            val metaAnalysis = MetaAnalysis(
                timestamp = System.currentTimeMillis(),
                reportIds = reports.map { it.id },
                reportCount = reports.size,
                timeframe = timeframe,
                oldestReportDate = reportDates.minOrNull(),
                newestReportDate = reportDates.maxOrNull(),
                temporalWeightingApplied = true,
                findings = analysisResult.findings,
                consensus = analysisResult.consensus,
                contradictions = analysisResult.contradictions,
                marketOutlook = MarketOutlook.fromString(analysisResult.marketOutlook),
                recommendedStrategy = analysisResult.strategy,
                strategyName = analysisResult.strategy.name,
                tradingPairs = analysisResult.strategy.tradingPairs,
                confidence = analysisResult.confidence,
                riskLevel = analysisResult.riskLevel,
                expectedReturn = analysisResult.expectedReturn,
                status = AnalysisStatus.COMPLETED,
                opusModel = OPUS_MODEL,
                tokensUsed = claudeResponse.usage?.let { it.inputTokens + it.outputTokens },
                analysisTimeMs = analysisTimeMs
            )

            Timber.i("Meta-analysis completed in ${analysisTimeMs}ms. Strategy: ${metaAnalysis.strategyName}, Confidence: ${metaAnalysis.confidence}")

            Result.success(metaAnalysis)

        } catch (e: Exception) {
            val analysisTimeMs = System.currentTimeMillis() - startTime
            Timber.e(e, "Meta-analysis failed after ${analysisTimeMs}ms")
            Result.failure(e)
        }
    }

    /**
     * Build comprehensive meta-analysis prompt for Opus 4.1 with temporal weighting
     */
    private fun buildMetaAnalysisPrompt(
        reports: List<ExpertReport>,
        reportWeights: Map<Long, Double>,
        timeframe: com.cryptotrader.domain.model.AnalysisTimeframe
    ): String {
        val reportsText = reports.mapIndexed { index, report ->
            val weight = reportWeights[report.id] ?: 1.0
            val weightIndicator = when {
                weight >= 0.9 -> "🔴 HIGHEST WEIGHT"
                weight >= 0.7 -> "🟠 HIGH WEIGHT"
                weight >= 0.5 -> "🟡 MEDIUM WEIGHT"
                else -> "⚪ LOW WEIGHT"
            }
            val reportDate = report.publishedDate ?: report.uploadDate

            """
            ## Report ${index + 1}: ${report.title}
            **Author:** ${report.author ?: "Unknown"}
            **Source:** ${report.source ?: "N/A"}
            **Category:** ${report.category.displayName}
            **Date:** ${formatTimestamp(reportDate)}
            **Temporal Weight:** ${String.format("%.2f", weight)} $weightIndicator
            **Tags:** ${report.tags.joinToString(", ").ifEmpty { "None" }}

            ### Content:
            ${report.content}

            ---
            """.trimIndent()
        }.joinToString("\n\n")

        return """
You are an expert cryptocurrency trading strategist performing meta-analysis of multiple expert trading reports.

# Task
Analyze the following ${reports.size} expert reports from the ${timeframe.displayName} timeframe (${timeframe.description}) and synthesize them into a comprehensive trading strategy recommendation.

# Temporal Weighting System
Each report has been assigned a temporal weight from 0.0 to 1.0 based on its recency:
- HIGHEST WEIGHT (0.9-1.0): Most recent reports - prioritize these insights
- HIGH WEIGHT (0.7-0.9): Recent reports - strong consideration
- MEDIUM WEIGHT (0.5-0.7): Moderate age - balanced consideration
- LOW WEIGHT (0.0-0.5): Older reports - context only

**IMPORTANT**: Weight your analysis towards reports with higher temporal weights. Recent insights should have more influence on your recommended strategy than older insights.

# Expert Reports
$reportsText

# Analysis Requirements

1. **Consensus Analysis**: Identify key points where multiple reports agree (weighted by recency)
2. **Contradiction Analysis**: Highlight significant disagreements between reports
3. **Market Outlook**: Determine overall market sentiment (BULLISH, BEARISH, NEUTRAL, VOLATILE, UNCERTAIN) based on temporal-weighted consensus
4. **Strategy Synthesis**: Create a unified trading strategy that prioritizes recent insights while considering longer-term context

# Output Format

Respond with a valid JSON object in this exact structure:

```json
{
  "findings": "Comprehensive summary of your meta-analysis (2-3 paragraphs)",
  "consensus": "Key points of agreement across reports",
  "contradictions": "Key points of disagreement or conflicting signals",
  "marketOutlook": "BULLISH|BEARISH|NEUTRAL|VOLATILE|UNCERTAIN",
  "confidence": 0.85,
  "riskLevel": "LOW|MEDIUM|HIGH|VERY_HIGH",
  "expectedReturn": "10-15% monthly (optional estimate)",
  "strategy": {
    "name": "Strategy name (concise, descriptive)",
    "description": "1-2 sentence strategy overview",
    "rationale": "Why this strategy makes sense given the reports",
    "tradingPairs": ["XBTUSD", "XETHZUSD"],
    "entryConditions": [
      "RSI(14) < 30 on 4h timeframe",
      "Price breaks above 20 EMA",
      "Volume confirms breakout"
    ],
    "exitConditions": [
      "RSI(14) > 70",
      "Price hits take profit target",
      "Trailing stop triggered"
    ],
    "positionSizePercent": 5.0,
    "stopLossPercent": 3.0,
    "takeProfitPercent": 10.0,
    "riskLevel": "MEDIUM",
    "confidenceScore": 0.85,
    "expectedReturn": "10-15% monthly",
    "keyInsights": [
      "Insight 1",
      "Insight 2"
    ],
    "riskFactors": [
      "Risk 1",
      "Risk 2"
    ]
  }
}
```

# Important Guidelines

- Base strategy ONLY on the provided reports
- If reports conflict heavily, lower confidence and note contradictions
- Be conservative with position sizing and risk management
- Provide specific, actionable entry/exit conditions
- Include 2-4 key insights and risk factors
- Confidence should range from 0.0 to 1.0 (0.7+ is strong)
- Match riskLevel in both top-level and strategy object

Respond ONLY with the JSON object. No additional text.
        """.trimIndent()
    }

    /**
     * Build system prompt for meta-analysis
     */
    private fun buildSystemPrompt(): String {
        return """
You are an expert cryptocurrency trading analyst specializing in synthesizing insights from multiple expert reports.

Your strengths:
- Identifying consensus and contradictions across multiple sources
- Balancing conflicting signals into coherent strategies
- Risk-aware strategy design
- Precise technical analysis
- Conservative position sizing

Always respond with valid JSON matching the requested format exactly.
        """.trimIndent()
    }

    /**
     * Parse meta-analysis response from Claude
     */
    private fun parseMetaAnalysisResponse(responseText: String): Result<MetaAnalysisResponse> {
        return try {
            // Extract JSON from response (handle markdown code blocks)
            val jsonText = extractJson(responseText)

            // Parse JSON
            val adapter = moshi.adapter(MetaAnalysisResponse::class.java)
            val parsed = adapter.fromJson(jsonText)
                ?: return Result.failure(Exception("Failed to parse meta-analysis JSON"))

            // Validate
            if (parsed.strategy.name.isBlank() || parsed.strategy.tradingPairs.isEmpty()) {
                return Result.failure(Exception("Invalid strategy: missing name or trading pairs"))
            }

            Result.success(parsed)

        } catch (e: Exception) {
            Timber.e(e, "Failed to parse meta-analysis response")
            Result.failure(e)
        }
    }

    /**
     * Extract JSON from response text (handle markdown code blocks)
     */
    private fun extractJson(text: String): String {
        // Try to find JSON in markdown code block
        val jsonBlockRegex = Regex("```json\\s*([\\s\\S]*?)```", RegexOption.MULTILINE)
        val match = jsonBlockRegex.find(text)
        if (match != null) {
            return match.groupValues[1].trim()
        }

        // Try to find raw JSON object
        val jsonObjectRegex = Regex("\\{[\\s\\S]*\\}", RegexOption.MULTILINE)
        val objectMatch = jsonObjectRegex.find(text)
        if (objectMatch != null) {
            return objectMatch.value.trim()
        }

        // Return as-is if no patterns found
        return text.trim()
    }

    /**
     * Get Claude API key from encrypted shared preferences
     */
    private fun getClaudeApiKey(): String? {
        return try {
            CryptoUtils.getClaudeApiKey(context)
        } catch (e: Exception) {
            Timber.e(e, "Failed to retrieve Claude API key")
            null
        }
    }

    /**
     * Format timestamp for display
     */
    private fun formatTimestamp(timestamp: Long): String {
        return java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
            .format(java.util.Date(timestamp))
    }

    /**
     * Calculate temporal weights for reports using exponential decay
     *
     * Newer reports get higher weights (up to 1.0), older reports get lower weights
     * Uses exponential decay formula: weight = e^(-lambda * age_in_days)
     *
     * @param reports List of expert reports (should be sorted newest first)
     * @return Map of report ID to temporal weight (0.0 to 1.0)
     */
    private fun calculateTemporalWeights(reports: List<ExpertReport>): Map<Long, Double> {
        if (reports.isEmpty()) return emptyMap()

        val now = System.currentTimeMillis()
        val reportAges = reports.map { report ->
            val reportDate = report.publishedDate ?: report.uploadDate
            val ageInDays = (now - reportDate) / (24.0 * 60 * 60 * 1000)
            report.id to ageInDays
        }.toMap()

        // Decay parameter: higher = faster decay (0.1 = gentle decay over ~30 days)
        val lambda = 0.1

        // Calculate weights using exponential decay
        val weights = reportAges.mapValues { (_, ageInDays) ->
            val weight = Math.exp(-lambda * ageInDays)
            weight.coerceIn(0.1, 1.0) // Minimum weight of 0.1 for context
        }

        return weights
    }
}

/**
 * Meta-analysis response DTO for JSON parsing
 */
@JsonClass(generateAdapter = true)
data class MetaAnalysisResponse(
    @Json(name = "findings") val findings: String,
    @Json(name = "consensus") val consensus: String? = null,
    @Json(name = "contradictions") val contradictions: String? = null,
    @Json(name = "marketOutlook") val marketOutlook: String,
    @Json(name = "confidence") val confidence: Double,
    @Json(name = "riskLevel") val riskLevel: RiskLevel,
    @Json(name = "expectedReturn") val expectedReturn: String? = null,
    @Json(name = "strategy") val strategy: RecommendedStrategy
)
