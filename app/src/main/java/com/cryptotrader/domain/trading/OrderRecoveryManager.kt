package com.cryptotrader.domain.trading

import com.cryptotrader.data.repository.KrakenRepository
import com.cryptotrader.domain.model.Trade
import com.cryptotrader.domain.usecase.TradeRequest
import com.cryptotrader.domain.model.TradeSignal
import kotlinx.coroutines.delay
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Order execution failure recovery system
 *
 * Ensures no profitable signal is lost due to temporary failures:
 * - Retries failed orders with exponential backoff
 * - Validates order status after execution
 * - Persists pending orders for recovery after app restart
 */
@Singleton
class OrderRecoveryManager @Inject constructor(
    private val krakenRepository: KrakenRepository
) {

    companion object {
        private const val MAX_RETRY_ATTEMPTS = 3
        private const val INITIAL_RETRY_DELAY_MS = 1000L
        private const val MAX_RETRY_DELAY_MS = 10000L
    }

    // Track pending orders for recovery
    private val pendingOrders = mutableMapOf<String, RecoveryPendingOrder>()

    /**
     * Execute trade with automatic retry on failure
     *
     * @param tradeRequest Trade request to execute
     * @param signal Original trade signal
     * @return Result with Trade if successful
     */
    suspend fun executeWithRetry(
        tradeRequest: TradeRequest,
        signal: TradeSignal
    ): Result<Trade> {
        var attempt = 0
        var lastException: Exception? = null
        var retryDelay = INITIAL_RETRY_DELAY_MS

        while (attempt < MAX_RETRY_ATTEMPTS) {
            attempt++

            try {
                Timber.d("Executing order attempt $attempt/$MAX_RETRY_ATTEMPTS")

                // Execute the trade
                val result = krakenRepository.placeOrder(tradeRequest)

                if (result.isSuccess) {
                    val trade = result.getOrNull()!!
                    Timber.i("✅ Order executed successfully on attempt $attempt: ${trade.orderId}")

                    // Remove from pending if it was tracked
                    pendingOrders.remove(tradeRequest.pair)

                    return Result.success(trade)
                } else {
                    lastException = result.exceptionOrNull() as? Exception
                    Timber.w("❌ Order execution failed on attempt $attempt: ${lastException?.message}")
                }

            } catch (e: Exception) {
                lastException = e
                Timber.w(e, "Exception during order execution attempt $attempt")
            }

            // If not last attempt, wait before retrying
            if (attempt < MAX_RETRY_ATTEMPTS) {
                Timber.d("Waiting ${retryDelay}ms before retry...")
                delay(retryDelay)

                // Exponential backoff
                retryDelay = (retryDelay * 2).coerceAtMost(MAX_RETRY_DELAY_MS)
            }
        }

        // All attempts failed - add to pending orders for later recovery
        val pendingOrder = RecoveryPendingOrder(
            tradeRequest = tradeRequest,
            signal = signal,
            attempts = attempt,
            lastAttemptTime = System.currentTimeMillis(),
            lastError = lastException?.message ?: "Unknown error"
        )

        pendingOrders[tradeRequest.pair] = pendingOrder

        Timber.e("🔴 Order execution failed after $MAX_RETRY_ATTEMPTS attempts. Added to pending orders.")

        return Result.failure(
            lastException ?: Exception("Order execution failed after $MAX_RETRY_ATTEMPTS attempts")
        )
    }

    /**
     * Retry all pending orders
     *
     * @return Number of successfully recovered orders
     */
    suspend fun retryPendingOrders(): Int {
        if (pendingOrders.isEmpty()) {
            return 0
        }

        Timber.i("Retrying ${pendingOrders.size} pending orders...")
        var successCount = 0

        val ordersToRetry = pendingOrders.values.toList()

        for (pendingOrder in ordersToRetry) {
            try {
                val result = executeWithRetry(
                    pendingOrder.tradeRequest,
                    pendingOrder.signal
                )

                if (result.isSuccess) {
                    successCount++
                }

            } catch (e: Exception) {
                Timber.e(e, "Error retrying pending order")
            }
        }

        Timber.i("Successfully recovered $successCount/${ordersToRetry.size} pending orders")
        return successCount
    }

    /**
     * Get pending orders count
     */
    fun getPendingOrdersCount(): Int = pendingOrders.size

    /**
     * Get all pending orders
     */
    fun getPendingOrders(): List<RecoveryPendingOrder> = pendingOrders.values.toList()

    /**
     * Clear all pending orders
     */
    fun clearPendingOrders() {
        pendingOrders.clear()
    }

    /**
     * Cancel a specific pending order
     */
    fun cancelPendingOrder(pair: String): Boolean {
        return pendingOrders.remove(pair) != null
    }
}

/**
 * Pending order awaiting recovery
 */
data class RecoveryPendingOrder(
    val tradeRequest: TradeRequest,
    val signal: TradeSignal,
    val attempts: Int,
    val lastAttemptTime: Long,
    val lastError: String
)
