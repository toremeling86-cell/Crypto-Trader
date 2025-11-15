package com.cryptotrader.domain.advisor

import android.content.Context
import com.cryptotrader.data.remote.claude.ClaudeApiService
import com.cryptotrader.data.remote.claude.dto.ClaudeMessage
import com.cryptotrader.data.remote.claude.dto.ClaudeRequest
import com.cryptotrader.data.repository.MarketSnapshotRepository
import com.cryptotrader.domain.ai.AIContextBuilder
import com.cryptotrader.domain.model.MarketSnapshot
import com.cryptotrader.utils.CryptoUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min
import kotlin.math.pow

/**
 * Claude Integration Service
 *
 * Main API wrapper for AI Trading Advisor using Claude API.
 *
 * Features:
 * - Two-tier strategy: Haiku (fast synthesis) + Sonnet (deep validation)
 * - Request queue with rate limiting (45 requests/minute)
 * - Timeout handling (10s synthesis, 20s validation)
 * - Cost tracking per analysis
 * - Automatic fallback when API unavailable
 * - Exponential backoff for retries
 */
@Singleton
class ClaudeIntegrationService @Inject constructor(
    private val claudeApi: ClaudeApiService,
    private val responseParser: ClaudeResponseParser,
    private val analysisCache: ClaudeAnalysisCache,
    private val reliabilityManager: AdvisorReliabilityManager,
    private val aiContextBuilder: AIContextBuilder,
    private val marketSnapshotRepository: MarketSnapshotRepository,
    @ApplicationContext private val context: Context
) {

    // Request queue for rate limiting
    private val requestQueue = Channel<RequestTask>(capacity = Channel.UNLIMITED)
    private val requestScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Cost tracking
    private val _totalCost = MutableStateFlow(0.0)
    val totalCost: StateFlow<Double> = _totalCost.asStateFlow()

    private var requestCount = 0L
    private var lastMinuteTimestamp = System.currentTimeMillis()
    private var requestsThisMinute = 0

    init {
        // Start request processor
        startRequestProcessor()

        // Start cache cleanup job
        startCacheCleanup()
    }

    /**
     * Analyze and synthesize signals from multiple trading agents
     *
     * Uses Claude Haiku for fast analysis (5-10 seconds)
     *
     * @param agentSignals Map of agent names to their signals
     * @param symbols List of symbols being analyzed
     * @param portfolioContext Optional portfolio context
     * @return Fast synthesis result or null on failure
     */
    suspend fun analyzeSynthesis(
        agentSignals: Map<String, AgentSignal>,
        symbols: List<String>,
        portfolioContext: String? = null
    ): Result<ClaudeFastSynthesis> = withContext(Dispatchers.IO) {
        try {
            Timber.d("Starting fast synthesis analysis for ${symbols.joinToString()}")

            // Check cache first
            val cacheKey = analysisCache.generateSynthesisKey(agentSignals, symbols)
            val cachedResult = analysisCache.getSynthesis(cacheKey)
            if (cachedResult != null) {
                Timber.i("Returning cached synthesis result")
                return@withContext Result.success(cachedResult)
            }

            // Check if we should use Claude API or fallback
            if (!reliabilityManager.shouldUseClaude()) {
                Timber.w("Claude API unavailable, using fallback synthesis")
                reliabilityManager.recordFallbackUsed()

                val fallbackResult = reliabilityManager.generateFallbackSynthesis(
                    agentSignals = agentSignals,
                    riskFactor = 0.5 // Default risk factor
                )

                return@withContext Result.success(fallbackResult)
            }

            // Get market data
            val marketData = marketSnapshotRepository.getLatestSnapshots(symbols).first()

            // Build prompt
            val prompt = ClaudePrompts.buildFastSynthesisPrompt(
                agentSignals = agentSignals,
                marketData = marketData,
                portfolioContext = portfolioContext
            )

            // Execute request with timeout
            val response = withTimeout(SYNTHESIS_TIMEOUT_MS) {
                executeRequest(
                    model = MODEL_HAIKU,
                    systemPrompt = prompt,
                    userMessage = "Analyze the provided agent signals and market data. Respond with JSON only.",
                    maxTokens = 1024
                )
            }

            if (response.isFailure) {
                throw response.exceptionOrNull()!!
            }

            val responseText = response.getOrNull()!!

            // Parse response
            val synthesis = responseParser.parseFastSynthesis(responseText)
            if (synthesis == null) {
                throw Exception("Failed to parse synthesis response")
            }

            // Validate quality
            val validation = responseParser.validateSynthesisQuality(synthesis)
            if (!validation.isValid()) {
                Timber.w("Synthesis quality issues: ${(validation as ValidationResult.Invalid).issues}")
            }

            // Cache result
            analysisCache.putSynthesis(cacheKey, synthesis)

            // Track cost
            trackCost(MODEL_HAIKU, prompt.length, responseText.length)

            // Record success
            reliabilityManager.recordSuccess()

            Timber.i("Fast synthesis completed: ${synthesis.primarySignal} (confidence: ${synthesis.synthesizedConfidence})")

            Result.success(synthesis)

        } catch (e: TimeoutCancellationException) {
            Timber.e("Synthesis request timed out after ${SYNTHESIS_TIMEOUT_MS}ms")
            val action = reliabilityManager.recordFailure(Exception("Request timeout"))
            handleFailure(action, agentSignals, symbols)
        } catch (e: Exception) {
            Timber.e(e, "Error performing synthesis analysis")
            val action = reliabilityManager.recordFailure(e)
            handleFailure(action, agentSignals, symbols)
        }
    }

    /**
     * Perform deep validation of a trading opportunity
     *
     * Uses Claude Sonnet for thorough analysis (15-20 seconds)
     *
     * @param opportunity Trading opportunity to validate
     * @param riskConstraints User's risk constraints
     * @return Deep validation result or null on failure
     */
    suspend fun analyzeDeepValidation(
        opportunity: TradingOpportunity,
        riskConstraints: RiskConstraints
    ): Result<ClaudeDeepValidation> = withContext(Dispatchers.IO) {
        try {
            Timber.d("Starting deep validation for ${opportunity.symbol}")

            // Check cache first
            val cacheKey = analysisCache.generateValidationKey(opportunity)
            val cachedResult = analysisCache.getValidation(cacheKey)
            if (cachedResult != null) {
                Timber.i("Returning cached validation result")
                return@withContext Result.success(cachedResult)
            }

            // Check if we should use Claude API or fallback
            if (!reliabilityManager.shouldUseClaude()) {
                Timber.w("Claude API unavailable, using fallback validation")
                reliabilityManager.recordFallbackUsed()

                val fallbackResult = reliabilityManager.generateFallbackValidation(
                    opportunity = opportunity,
                    riskConstraints = riskConstraints
                )

                return@withContext Result.success(fallbackResult)
            }

            // Build comprehensive market context
            val fullMarketContext = aiContextBuilder.buildFullContext(
                includeMarketData = true,
                includePortfolio = true,
                includeTrades = false
            )

            // Historical performance (placeholder - could be enhanced)
            val historicalPerformance = "Historical data not yet implemented"

            // Build prompt
            val prompt = ClaudePrompts.buildDeepValidationPrompt(
                opportunity = opportunity,
                fullMarketContext = fullMarketContext,
                historicalPerformance = historicalPerformance,
                riskConstraints = riskConstraints
            )

            // Execute request with timeout
            val response = withTimeout(VALIDATION_TIMEOUT_MS) {
                executeRequest(
                    model = MODEL_SONNET,
                    systemPrompt = prompt,
                    userMessage = "Validate the provided trading opportunity. Respond with JSON only.",
                    maxTokens = 4096
                )
            }

            if (response.isFailure) {
                throw response.exceptionOrNull()!!
            }

            val responseText = response.getOrNull()!!

            // Parse response
            val validation = responseParser.parseDeepValidation(responseText)
            if (validation == null) {
                throw Exception("Failed to parse validation response")
            }

            // Validate quality
            val qualityCheck = responseParser.validateValidationQuality(validation)
            if (!qualityCheck.isValid()) {
                Timber.w("Validation quality issues: ${(qualityCheck as ValidationResult.Invalid).issues}")
            }

            // Cache result
            analysisCache.putValidation(cacheKey, validation)

            // Track cost
            trackCost(MODEL_SONNET, prompt.length, responseText.length)

            // Record success
            reliabilityManager.recordSuccess()

            Timber.i("Deep validation completed: ${validation.validationResult} (confidence: ${validation.finalConfidence})")

            Result.success(validation)

        } catch (e: TimeoutCancellationException) {
            Timber.e("Validation request timed out after ${VALIDATION_TIMEOUT_MS}ms")
            val action = reliabilityManager.recordFailure(Exception("Request timeout"))
            handleValidationFailure(action, opportunity, riskConstraints)
        } catch (e: Exception) {
            Timber.e(e, "Error performing validation analysis")
            val action = reliabilityManager.recordFailure(e)
            handleValidationFailure(action, opportunity, riskConstraints)
        }
    }

    /**
     * Execute API request through rate-limited queue
     */
    private suspend fun executeRequest(
        model: String,
        systemPrompt: String,
        userMessage: String,
        maxTokens: Int
    ): Result<String> = suspendCancellableCoroutine { continuation ->
        val task = RequestTask(
            model = model,
            systemPrompt = systemPrompt,
            userMessage = userMessage,
            maxTokens = maxTokens,
            continuation = continuation
        )

        requestScope.launch {
            requestQueue.send(task)
        }
    }

    /**
     * Start request processor with rate limiting
     */
    private fun startRequestProcessor() {
        requestScope.launch {
            for (task in requestQueue) {
                try {
                    // Rate limiting: max 45 requests per minute
                    enforceRateLimit()

                    // Execute request
                    val result = executeClaudeRequest(task)
                    task.continuation.resumeWith(Result.success(result))

                } catch (e: Exception) {
                    task.continuation.resumeWith(Result.failure(e))
                }
            }
        }
    }

    /**
     * Enforce rate limiting (45 requests per minute)
     */
    private suspend fun enforceRateLimit() {
        val now = System.currentTimeMillis()
        val minuteElapsed = now - lastMinuteTimestamp

        if (minuteElapsed >= 60_000) {
            // Reset counter for new minute
            lastMinuteTimestamp = now
            requestsThisMinute = 0
        }

        if (requestsThisMinute >= MAX_REQUESTS_PER_MINUTE) {
            // Wait until next minute
            val waitTime = 60_000 - minuteElapsed
            Timber.d("Rate limit reached, waiting ${waitTime}ms")
            delay(waitTime)
            lastMinuteTimestamp = System.currentTimeMillis()
            requestsThisMinute = 0
        }

        requestsThisMinute++
        requestCount++
    }

    /**
     * Execute actual Claude API request
     */
    private suspend fun executeClaudeRequest(task: RequestTask): Result<String> {
        return try {
            val apiKey = CryptoUtils.getClaudeApiKey(context)
            if (apiKey.isNullOrBlank()) {
                return Result.failure(Exception("Claude API key not configured"))
            }

            val request = ClaudeRequest(
                model = task.model,
                maxTokens = task.maxTokens,
                messages = listOf(
                    ClaudeMessage(
                        role = "user",
                        content = task.userMessage
                    )
                ),
                temperature = 0.7,
                system = task.systemPrompt
            )

            val response = claudeApi.createMessage(apiKey = apiKey, request = request)

            if (!response.isSuccessful) {
                val errorBody = response.errorBody()?.string()
                Timber.e("Claude API error: ${response.code()} - $errorBody")
                return Result.failure(Exception("Claude API error: ${response.message()}"))
            }

            val claudeResponse = response.body()
            if (claudeResponse == null || claudeResponse.content.isEmpty()) {
                return Result.failure(Exception("Empty response from Claude"))
            }

            val responseText = claudeResponse.content.first().text

            Result.success(responseText)

        } catch (e: Exception) {
            Timber.e(e, "Error executing Claude request")
            Result.failure(e)
        }
    }

    /**
     * Handle synthesis failure with retry or fallback
     */
    private suspend fun handleFailure(
        action: FailureAction,
        agentSignals: Map<String, AgentSignal>,
        symbols: List<String>
    ): Result<ClaudeFastSynthesis> {
        return when (action) {
            FailureAction.RETRY_WITH_BACKOFF -> {
                Timber.d("Retrying synthesis with backoff")
                delay(AdvisorReliabilityManager.TRANSIENT_RETRY_DELAY_MS)
                analyzeSynthesis(agentSignals, symbols)
            }
            FailureAction.RETRY_AFTER_DELAY -> {
                Timber.d("Retrying synthesis after rate limit delay")
                delay(AdvisorReliabilityManager.RATE_LIMIT_RETRY_DELAY_MS)
                analyzeSynthesis(agentSignals, symbols)
            }
            FailureAction.USE_FALLBACK -> {
                Timber.i("Using fallback synthesis")
                reliabilityManager.recordFallbackUsed()
                val fallback = reliabilityManager.generateFallbackSynthesis(agentSignals, 0.5)
                Result.success(fallback)
            }
        }
    }

    /**
     * Handle validation failure with retry or fallback
     */
    private suspend fun handleValidationFailure(
        action: FailureAction,
        opportunity: TradingOpportunity,
        riskConstraints: RiskConstraints
    ): Result<ClaudeDeepValidation> {
        return when (action) {
            FailureAction.RETRY_WITH_BACKOFF -> {
                Timber.d("Retrying validation with backoff")
                delay(AdvisorReliabilityManager.TRANSIENT_RETRY_DELAY_MS)
                analyzeDeepValidation(opportunity, riskConstraints)
            }
            FailureAction.RETRY_AFTER_DELAY -> {
                Timber.d("Retrying validation after rate limit delay")
                delay(AdvisorReliabilityManager.RATE_LIMIT_RETRY_DELAY_MS)
                analyzeDeepValidation(opportunity, riskConstraints)
            }
            FailureAction.USE_FALLBACK -> {
                Timber.i("Using fallback validation")
                reliabilityManager.recordFallbackUsed()
                val fallback = reliabilityManager.generateFallbackValidation(opportunity, riskConstraints)
                Result.success(fallback)
            }
        }
    }

    /**
     * Track cost of API usage
     * Based on Claude pricing: Haiku ~$0.25/1M tokens, Sonnet ~$3/1M tokens (input)
     */
    private fun trackCost(model: String, inputLength: Int, outputLength: Int) {
        // Rough token estimation: ~4 characters per token
        val inputTokens = inputLength / 4
        val outputTokens = outputLength / 4

        val cost = when (model) {
            MODEL_HAIKU -> {
                // Haiku: $0.25 per 1M input tokens, $1.25 per 1M output tokens
                (inputTokens * 0.25 / 1_000_000) + (outputTokens * 1.25 / 1_000_000)
            }
            MODEL_SONNET -> {
                // Sonnet 4.5: $3 per 1M input tokens, $15 per 1M output tokens
                (inputTokens * 3.0 / 1_000_000) + (outputTokens * 15.0 / 1_000_000)
            }
            else -> 0.0
        }

        _totalCost.value += cost
        Timber.d("Request cost: $${"%.6f".format(cost)} (Total: $${"%.4f".format(_totalCost.value)})")
    }

    /**
     * Start periodic cache cleanup
     */
    private fun startCacheCleanup() {
        requestScope.launch {
            while (true) {
                delay(CACHE_CLEANUP_INTERVAL_MS)
                try {
                    analysisCache.clearExpired()
                } catch (e: Exception) {
                    Timber.e(e, "Error during cache cleanup")
                }
            }
        }
    }

    /**
     * Get service statistics
     */
    suspend fun getServiceStats(): ServiceStats {
        val cacheStats = analysisCache.getStats()
        val healthStatus = reliabilityManager.getHealthStatus()

        return ServiceStats(
            totalRequests = requestCount,
            requestsThisMinute = requestsThisMinute,
            totalCost = _totalCost.value,
            cacheStats = cacheStats,
            healthStatus = healthStatus
        )
    }

    /**
     * Clear all caches and reset statistics
     */
    suspend fun clearAllCaches() {
        analysisCache.clearAll()
        reliabilityManager.resetStats()
        Timber.i("All caches and statistics cleared")
    }

    companion object {
        // Claude models
        private const val MODEL_HAIKU = "claude-3-5-haiku-20241022" // Fast, cost-effective
        private const val MODEL_SONNET = "claude-sonnet-4-5-20250929" // Advanced reasoning

        // Timeouts
        private const val SYNTHESIS_TIMEOUT_MS = 10_000L // 10 seconds
        private const val VALIDATION_TIMEOUT_MS = 20_000L // 20 seconds

        // Rate limiting
        private const val MAX_REQUESTS_PER_MINUTE = 45

        // Cache cleanup
        private const val CACHE_CLEANUP_INTERVAL_MS = 5 * 60 * 1000L // 5 minutes
    }
}

/**
 * Request task for queue processing
 */
private data class RequestTask(
    val model: String,
    val systemPrompt: String,
    val userMessage: String,
    val maxTokens: Int,
    val continuation: CancellableContinuation<Result<String>>
)

/**
 * Service statistics
 */
data class ServiceStats(
    val totalRequests: Long,
    val requestsThisMinute: Int,
    val totalCost: Double,
    val cacheStats: CacheStats,
    val healthStatus: HealthStatus
) {
    fun toFormattedString(): String {
        return """
            Claude Integration Service Statistics:
            - Total Requests: $totalRequests
            - Requests This Minute: $requestsThisMinute
            - Total Cost: $${"%.4f".format(totalCost)}

            ${cacheStats.toFormattedString()}

            ${healthStatus.toFormattedString()}
        """.trimIndent()
    }
}
