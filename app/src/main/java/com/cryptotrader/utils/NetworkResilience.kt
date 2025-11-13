package com.cryptotrader.utils

import kotlinx.coroutines.delay
import timber.log.Timber
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.math.min
import kotlin.math.pow

/**
 * Network resilience utilities with retry logic and exponential backoff
 *
 * Handles:
 * - Automatic retries for transient failures
 * - Exponential backoff to avoid overwhelming servers
 * - Circuit breaker to fail fast when service is down
 * - Connection recovery strategies
 */
object NetworkResilience {

    // Retry configuration
    private const val MAX_RETRIES = 3
    private const val INITIAL_BACKOFF_MS = 1000L
    private const val MAX_BACKOFF_MS = 32000L
    private const val BACKOFF_MULTIPLIER = 2.0

    // Circuit breaker configuration
    private const val CIRCUIT_FAILURE_THRESHOLD = 5
    private const val CIRCUIT_RESET_TIMEOUT_MS = 60000L // 1 minute

    /**
     * Execute network operation with automatic retries and exponential backoff
     *
     * @param maxRetries Maximum number of retry attempts (default: 3)
     * @param initialBackoffMs Initial backoff delay in milliseconds (default: 1000)
     * @param maxBackoffMs Maximum backoff delay in milliseconds (default: 32000)
     * @param operation The operation to execute
     * @return Result of the operation
     */
    suspend fun <T> executeWithRetry(
        maxRetries: Int = MAX_RETRIES,
        initialBackoffMs: Long = INITIAL_BACKOFF_MS,
        maxBackoffMs: Long = MAX_BACKOFF_MS,
        operation: suspend () -> T
    ): T {
        var currentTry = 0
        var lastException: Exception? = null

        while (currentTry <= maxRetries) {
            try {
                // Attempt operation
                return operation()

            } catch (e: Exception) {
                lastException = e

                // Check if error is retryable
                if (!isRetryable(e)) {
                    Timber.w("Non-retryable error: ${e.message}")
                    throw e
                }

                currentTry++

                if (currentTry > maxRetries) {
                    Timber.e("Max retries ($maxRetries) exceeded")
                    throw e
                }

                // Calculate backoff with exponential increase
                val backoffMs = min(
                    (initialBackoffMs * BACKOFF_MULTIPLIER.pow(currentTry - 1)).toLong(),
                    maxBackoffMs
                )

                Timber.w("Network error (attempt $currentTry/$maxRetries): ${e.message}. Retrying in ${backoffMs}ms...")
                delay(backoffMs)
            }
        }

        // Should never reach here, but throw last exception if we do
        throw lastException ?: Exception("Unknown error during retry")
    }

    /**
     * Determine if an exception is retryable
     *
     * Retryable: Timeouts, temporary network issues
     * Non-retryable: Authentication errors, invalid requests
     */
    private fun isRetryable(exception: Exception): Boolean {
        return when (exception) {
            // Network errors - retryable
            is SocketTimeoutException -> true
            is UnknownHostException -> true
            is IOException -> true

            // HTTP errors (would need to check status code)
            // 408 (Timeout), 429 (Too Many Requests), 500-503 (Server errors) - retryable
            // 400 (Bad Request), 401 (Unauthorized), 403 (Forbidden) - NOT retryable
            else -> {
                // Check if message indicates a transient error
                val message = exception.message?.lowercase() ?: ""
                message.contains("timeout") ||
                message.contains("temporary") ||
                message.contains("unavailable") ||
                message.contains("connection reset")
            }
        }
    }

    /**
     * Execute operation with rate limiting (prevents API rate limit errors)
     *
     * @param delayMs Delay between calls in milliseconds
     * @param operation The operation to execute
     */
    suspend fun <T> executeWithRateLimit(
        delayMs: Long = 1000L,
        operation: suspend () -> T
    ): T {
        // Execute operation
        val result = operation()

        // Apply rate limit delay
        delay(delayMs)

        return result
    }

    /**
     * Retry configuration for specific operations
     */
    data class RetryConfig(
        val maxRetries: Int = MAX_RETRIES,
        val initialBackoffMs: Long = INITIAL_BACKOFF_MS,
        val maxBackoffMs: Long = MAX_BACKOFF_MS,
        val failureThreshold: Int = CIRCUIT_FAILURE_THRESHOLD
    )

    /**
     * Get retry configuration for critical operations
     * (Orders, balance updates, etc.)
     */
    fun getCriticalRetryConfig(): RetryConfig {
        return RetryConfig(
            maxRetries = 5,
            initialBackoffMs = 500L,
            maxBackoffMs = 16000L
        )
    }

    /**
     * Get retry configuration for non-critical operations
     * (Price updates, strategy evaluation, etc.)
     */
    fun getNonCriticalRetryConfig(): RetryConfig {
        return RetryConfig(
            maxRetries = 2,
            initialBackoffMs = 1000L,
            maxBackoffMs = 8000L
        )
    }
}

/**
 * Circuit Breaker pattern implementation
 *
 * Prevents repeated calls to failing services by "opening the circuit"
 * after a threshold of failures
 */
class CircuitBreaker(
    private val failureThreshold: Int = 5,
    private val resetTimeoutMs: Long = 60000L
) {
    private var failureCount = 0
    private var lastFailureTime: Long = 0
    private var state = CircuitState.CLOSED

    enum class CircuitState {
        CLOSED,  // Normal operation
        OPEN,    // Failing fast
        HALF_OPEN // Testing if service recovered
    }

    /**
     * Execute operation through circuit breaker
     */
    suspend fun <T> execute(operation: suspend () -> T): T {
        when (state) {
            CircuitState.OPEN -> {
                // Check if we should attempt recovery
                if (System.currentTimeMillis() - lastFailureTime >= resetTimeoutMs) {
                    Timber.i("Circuit breaker: Attempting recovery (HALF_OPEN)")
                    state = CircuitState.HALF_OPEN
                } else {
                    throw CircuitBreakerOpenException("Circuit breaker is OPEN. Service unavailable.")
                }
            }
            CircuitState.HALF_OPEN, CircuitState.CLOSED -> {
                // Attempt operation
            }
        }

        return try {
            val result = operation()

            // Success - reset circuit
            if (state == CircuitState.HALF_OPEN) {
                Timber.i("Circuit breaker: Service recovered (CLOSED)")
                state = CircuitState.CLOSED
                failureCount = 0
            }

            result

        } catch (e: Exception) {
            onFailure()
            throw e
        }
    }

    private fun onFailure() {
        failureCount++
        lastFailureTime = System.currentTimeMillis()

        when (state) {
            CircuitState.HALF_OPEN -> {
                // Failed during recovery - open circuit again
                Timber.w("Circuit breaker: Recovery failed (OPEN)")
                state = CircuitState.OPEN
            }
            CircuitState.CLOSED -> {
                if (failureCount >= failureThreshold) {
                    Timber.w("Circuit breaker: Failure threshold reached (OPEN)")
                    state = CircuitState.OPEN
                }
            }
            CircuitState.OPEN -> {
                // Already open
            }
        }
    }

    fun reset() {
        state = CircuitState.CLOSED
        failureCount = 0
        lastFailureTime = 0
    }

    fun getState() = state
    fun getFailureCount() = failureCount
}

class CircuitBreakerOpenException(message: String) : Exception(message)
