package com.cryptotrader.domain.advisor

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Advisor Reliability Manager
 *
 * Manages fallback strategies and graceful degradation when Claude API is unavailable.
 *
 * Features:
 * - Circuit breaker pattern for API failures
 * - Fallback to rule-based analysis
 * - Degradation levels based on failure severity
 * - Health monitoring and recovery
 * - Automatic retry with exponential backoff
 */
@Singleton
class AdvisorReliabilityManager @Inject constructor() {

    // Circuit breaker state
    private var circuitState = CircuitState.CLOSED
    private var failureCount = AtomicInteger(0)
    private var lastFailureTime = 0L
    private var lastSuccessTime = System.currentTimeMillis()
    private val mutex = Mutex()

    // Health metrics
    private var totalRequests = 0L
    private var successfulRequests = 0L
    private var failedRequests = 0L
    private var fallbackRequests = 0L

    /**
     * Check if Claude API should be used or if we should fall back
     *
     * @return true if API is available, false if we should use fallback
     */
    suspend fun shouldUseClaude(): Boolean {
        return mutex.withLock {
            when (circuitState) {
                CircuitState.CLOSED -> {
                    // Normal operation
                    true
                }
                CircuitState.OPEN -> {
                    // Circuit is open (too many failures)
                    // Check if enough time has passed to try again
                    val timeSinceFailure = System.currentTimeMillis() - lastFailureTime
                    if (timeSinceFailure > CIRCUIT_RESET_TIMEOUT_MS) {
                        Timber.i("Circuit breaker timeout elapsed, entering HALF_OPEN state")
                        circuitState = CircuitState.HALF_OPEN
                        true
                    } else {
                        Timber.d("Circuit breaker OPEN, using fallback (time remaining: ${CIRCUIT_RESET_TIMEOUT_MS - timeSinceFailure}ms)")
                        false
                    }
                }
                CircuitState.HALF_OPEN -> {
                    // Testing if service has recovered
                    Timber.d("Circuit breaker HALF_OPEN, attempting request")
                    true
                }
            }
        }
    }

    /**
     * Record successful API call
     */
    suspend fun recordSuccess() {
        mutex.withLock {
            totalRequests++
            successfulRequests++
            lastSuccessTime = System.currentTimeMillis()

            when (circuitState) {
                CircuitState.HALF_OPEN -> {
                    Timber.i("Circuit breaker: Request succeeded in HALF_OPEN state, moving to CLOSED")
                    circuitState = CircuitState.CLOSED
                    failureCount.set(0)
                }
                CircuitState.CLOSED -> {
                    // Gradually reduce failure count on success
                    if (failureCount.get() > 0) {
                        failureCount.decrementAndGet()
                    }
                }
                else -> { /* OPEN state - shouldn't happen */ }
            }

            Timber.d("API success recorded. State: $circuitState, Failures: ${failureCount.get()}")
        }
    }

    /**
     * Record failed API call
     *
     * @param exception The exception that caused the failure
     * @return Recommended action for handling this failure
     */
    suspend fun recordFailure(exception: Exception): FailureAction {
        return mutex.withLock {
            totalRequests++
            failedRequests++
            lastFailureTime = System.currentTimeMillis()

            val currentFailures = failureCount.incrementAndGet()

            Timber.e(exception, "API failure recorded. Count: $currentFailures, State: $circuitState")

            // Determine if this is a transient or permanent failure
            val failureType = classifyFailure(exception)

            when (circuitState) {
                CircuitState.CLOSED -> {
                    if (currentFailures >= FAILURE_THRESHOLD) {
                        Timber.w("Circuit breaker: Failure threshold reached ($currentFailures), opening circuit")
                        circuitState = CircuitState.OPEN
                        FailureAction.USE_FALLBACK
                    } else {
                        // Still below threshold, decide based on failure type
                        when (failureType) {
                            FailureType.TRANSIENT -> FailureAction.RETRY_WITH_BACKOFF
                            FailureType.RATE_LIMIT -> FailureAction.RETRY_AFTER_DELAY
                            FailureType.PERMANENT -> FailureAction.USE_FALLBACK
                        }
                    }
                }
                CircuitState.HALF_OPEN -> {
                    Timber.w("Circuit breaker: Request failed in HALF_OPEN state, reopening circuit")
                    circuitState = CircuitState.OPEN
                    FailureAction.USE_FALLBACK
                }
                CircuitState.OPEN -> {
                    FailureAction.USE_FALLBACK
                }
            }
        }
    }

    /**
     * Record use of fallback mechanism
     */
    suspend fun recordFallbackUsed() {
        mutex.withLock {
            totalRequests++
            fallbackRequests++
            Timber.d("Fallback mechanism used. Total fallbacks: $fallbackRequests")
        }
    }

    /**
     * Classify failure type to determine appropriate response
     */
    private fun classifyFailure(exception: Exception): FailureType {
        return when {
            // Network or timeout errors are usually transient
            exception.message?.contains("timeout", ignoreCase = true) == true ||
            exception.message?.contains("connection", ignoreCase = true) == true -> {
                FailureType.TRANSIENT
            }

            // Rate limiting errors need delay
            exception.message?.contains("rate limit", ignoreCase = true) == true ||
            exception.message?.contains("429", ignoreCase = true) == true -> {
                FailureType.RATE_LIMIT
            }

            // Authentication or API key errors are permanent
            exception.message?.contains("api key", ignoreCase = true) == true ||
            exception.message?.contains("401", ignoreCase = true) == true ||
            exception.message?.contains("403", ignoreCase = true) == true -> {
                FailureType.PERMANENT
            }

            // Default to transient for unknown errors
            else -> FailureType.TRANSIENT
        }
    }

    /**
     * Generate fallback synthesis based on simple rules
     *
     * This is a basic rule-based system used when Claude is unavailable
     */
    fun generateFallbackSynthesis(
        agentSignals: Map<String, AgentSignal>,
        riskFactor: Double
    ): ClaudeFastSynthesis {
        Timber.i("Generating fallback synthesis from ${agentSignals.size} agent signals")

        if (agentSignals.isEmpty()) {
            return ClaudeFastSynthesis(
                synthesizedConfidence = 0.3,
                primarySignal = "HOLD",
                keyInsight = "No agent signals available. Maintaining current positions.",
                riskFactor = riskFactor,
                sentimentAdjustment = 0.0
            )
        }

        // Count signals by type
        val signalCounts = agentSignals.values.groupBy { it.signal }
        val buyCount = signalCounts["BUY"]?.size ?: 0
        val sellCount = signalCounts["SELL"]?.size ?: 0
        val holdCount = signalCounts["HOLD"]?.size ?: 0
        val totalCount = agentSignals.size

        // Calculate weighted average confidence
        val avgConfidence = agentSignals.values.map { it.confidence }.average()

        // Determine primary signal based on majority
        val primarySignal = when {
            buyCount > sellCount && buyCount > holdCount -> "BUY"
            sellCount > buyCount && sellCount > holdCount -> "SELL"
            else -> "HOLD"
        }

        // Calculate confidence penalty for using fallback
        val fallbackPenalty = 0.2 // Reduce confidence by 20% for fallback
        val adjustedConfidence = (avgConfidence - fallbackPenalty).coerceIn(0.0, 1.0)

        // Generate simple insight
        val keyInsight = when (primarySignal) {
            "BUY" -> "Majority of agents ($buyCount/$totalCount) recommend buying. " +
                "Fallback mode - verification limited."
            "SELL" -> "Majority of agents ($sellCount/$totalCount) recommend selling. " +
                "Fallback mode - verification limited."
            else -> "Mixed or inconclusive signals from agents. " +
                "Fallback mode recommends holding."
        }

        return ClaudeFastSynthesis(
            synthesizedConfidence = adjustedConfidence,
            primarySignal = primarySignal,
            keyInsight = keyInsight,
            riskFactor = riskFactor,
            sentimentAdjustment = 0.0 // No sentiment adjustment in fallback mode
        )
    }

    /**
     * Generate fallback validation with conservative approach
     */
    fun generateFallbackValidation(
        opportunity: TradingOpportunity,
        riskConstraints: RiskConstraints
    ): ClaudeDeepValidation {
        Timber.i("Generating fallback validation for ${opportunity.symbol}")

        // Be conservative in fallback mode - only approve high-confidence opportunities
        val validationResult = when {
            opportunity.synthesisConfidence < 0.6 -> "REJECTED"
            opportunity.riskRewardRatio < 2.0 -> "REJECTED"
            opportunity.positionSizePercent > riskConstraints.maxPositionSizePercent -> "CONDITIONAL"
            opportunity.synthesisConfidence >= 0.75 && opportunity.riskRewardRatio >= 2.5 -> "APPROVED"
            else -> "CONDITIONAL"
        }

        val reasoning = """
            FALLBACK MODE ANALYSIS (Claude API unavailable):

            This opportunity has been evaluated using rule-based analysis due to AI service unavailability.
            Analysis is conservative and based on predefined risk parameters.

            Opportunity Assessment:
            - Symbol: ${opportunity.symbol}
            - Synthesis Confidence: ${String.format("%.2f", opportunity.synthesisConfidence)}
            - Risk/Reward Ratio: ${String.format("%.2f", opportunity.riskRewardRatio)}
            - Position Size: ${String.format("%.2f", opportunity.positionSizePercent)}%

            Decision Rationale:
            ${when (validationResult) {
                "APPROVED" -> "High confidence (>= 0.75) and favorable risk/reward (>= 2.5). Within risk constraints."
                "REJECTED" -> "Does not meet minimum thresholds for automated approval in fallback mode."
                else -> "Meets basic criteria but requires manual review or adjustments."
            }}

            Limitations:
            - No advanced market context analysis
            - No sentiment or momentum analysis
            - No historical pattern matching
            - Conservative risk assessment

            Recommendation: ${if (validationResult == "APPROVED")
                "Proceed with caution. Monitor closely."
            else
                "Manual review recommended before execution."
            }
        """.trimIndent()

        val keyInsights = listOf(
            "Fallback mode active - AI advisor temporarily unavailable",
            "Using rule-based analysis with conservative thresholds",
            if (opportunity.synthesisConfidence >= 0.6)
                "Synthesis confidence acceptable (${String.format("%.2f", opportunity.synthesisConfidence)})"
            else
                "Synthesis confidence below threshold (${String.format("%.2f", opportunity.synthesisConfidence)})"
        )

        val risks = listOf(
            "Limited analysis depth due to AI service unavailability",
            "No advanced market context or sentiment analysis",
            "Conservative risk assessment may miss opportunities"
        )

        val adjustments = if (validationResult == "CONDITIONAL") {
            RecommendedAdjustments(
                positionSize = "Reduce to ${(opportunity.positionSizePercent * 0.7).coerceAtMost(riskConstraints.maxPositionSizePercent)}% in fallback mode",
                stopLoss = "Tighten stop loss to ${(opportunity.stopLossPercent * 0.8)}% for extra protection",
                takeProfit = null,
                timing = "Wait for AI advisor recovery for full analysis"
            )
        } else null

        return ClaudeDeepValidation(
            validationResult = validationResult,
            finalConfidence = (opportunity.synthesisConfidence * 0.7).coerceIn(0.0, 1.0), // Reduce confidence in fallback
            reasoning = reasoning,
            keyInsights = keyInsights,
            risks = risks,
            recommendedAdjustments = adjustments
        )
    }

    /**
     * Get current health status
     */
    suspend fun getHealthStatus(): HealthStatus {
        return mutex.withLock {
            val successRate = if (totalRequests > 0) {
                successfulRequests.toDouble() / totalRequests
            } else {
                1.0
            }

            val timeSinceLastSuccess = System.currentTimeMillis() - lastSuccessTime

            HealthStatus(
                circuitState = circuitState,
                failureCount = failureCount.get(),
                totalRequests = totalRequests,
                successfulRequests = successfulRequests,
                failedRequests = failedRequests,
                fallbackRequests = fallbackRequests,
                successRate = successRate,
                timeSinceLastSuccess = timeSinceLastSuccess,
                isHealthy = circuitState == CircuitState.CLOSED && successRate > 0.8
            )
        }
    }

    /**
     * Manually reset circuit breaker (for testing or admin intervention)
     */
    suspend fun resetCircuitBreaker() {
        mutex.withLock {
            circuitState = CircuitState.CLOSED
            failureCount.set(0)
            Timber.i("Circuit breaker manually reset")
        }
    }

    /**
     * Reset all statistics
     */
    suspend fun resetStats() {
        mutex.withLock {
            totalRequests = 0L
            successfulRequests = 0L
            failedRequests = 0L
            fallbackRequests = 0L
            Timber.d("Reliability statistics reset")
        }
    }

    companion object {
        // Circuit breaker thresholds
        private const val FAILURE_THRESHOLD = 3 // Open circuit after 3 consecutive failures
        private const val CIRCUIT_RESET_TIMEOUT_MS = 60_000L // Try again after 1 minute

        // Retry delays
        const val TRANSIENT_RETRY_DELAY_MS = 2_000L // 2 seconds
        const val RATE_LIMIT_RETRY_DELAY_MS = 10_000L // 10 seconds
    }
}

/**
 * Circuit breaker states
 */
enum class CircuitState {
    CLOSED,     // Normal operation
    OPEN,       // Too many failures, using fallback
    HALF_OPEN   // Testing if service has recovered
}

/**
 * Types of failures
 */
enum class FailureType {
    TRANSIENT,  // Temporary network/timeout issue
    RATE_LIMIT, // Rate limiting, need to wait
    PERMANENT   // Configuration or authentication issue
}

/**
 * Recommended action for handling failure
 */
enum class FailureAction {
    RETRY_WITH_BACKOFF,    // Retry after exponential backoff
    RETRY_AFTER_DELAY,     // Retry after fixed delay
    USE_FALLBACK           // Use rule-based fallback
}

/**
 * Health status of the advisor system
 */
data class HealthStatus(
    val circuitState: CircuitState,
    val failureCount: Int,
    val totalRequests: Long,
    val successfulRequests: Long,
    val failedRequests: Long,
    val fallbackRequests: Long,
    val successRate: Double,
    val timeSinceLastSuccess: Long,
    val isHealthy: Boolean
) {
    fun toFormattedString(): String {
        return """
            AI Advisor Health Status:
            - State: $circuitState
            - Healthy: $isHealthy
            - Success Rate: ${String.format("%.1f%%", successRate * 100)}
            - Requests: $totalRequests (Success: $successfulRequests, Failed: $failedRequests, Fallback: $fallbackRequests)
            - Consecutive Failures: $failureCount
            - Time Since Last Success: ${timeSinceLastSuccess / 1000}s
        """.trimIndent()
    }
}
