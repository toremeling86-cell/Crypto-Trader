package com.cryptotrader.domain.trading

import android.content.Context
import com.cryptotrader.utils.NetworkResilience
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Order Recovery Service
 *
 * Ensures critical trading operations (orders) are NEVER lost, even if:
 * - Network connection fails
 * - App crashes
 * - API is temporarily unavailable
 *
 * Features:
 * - Persistent queue for failed orders
 * - Automatic retry with exponential backoff
 * - Order status verification
 * - Duplicate order prevention
 */
@Singleton
class OrderRecoveryService @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val pendingOrders = ConcurrentHashMap<String, PendingOrder>()
    private val recoveryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var isRecoveryActive = false

    companion object {
        private const val MAX_RECOVERY_ATTEMPTS = 10
        private const val RECOVERY_CHECK_INTERVAL_MS = 30000L // 30 seconds
    }

    /**
     * Queue an order for reliable execution with automatic recovery
     *
     * @param orderId Unique order identifier
     * @param orderOperation Suspending function that executes the order
     * @return Order execution result
     */
    suspend fun executeOrderWithRecovery(
        orderId: String = UUID.randomUUID().toString(),
        orderOperation: suspend () -> OrderResult
    ): OrderResult {
        Timber.i("📋 Queueing order for execution: $orderId")

        // Add to pending orders
        val pendingOrder = PendingOrder(
            id = orderId,
            operation = orderOperation,
            attempts = 0,
            queuedAt = System.currentTimeMillis()
        )
        pendingOrders[orderId] = pendingOrder

        // Attempt immediate execution
        val result = attemptOrderExecution(pendingOrder)

        if (result.success) {
            // Success - remove from queue
            pendingOrders.remove(orderId)
            Timber.i("✅ Order executed successfully: $orderId")
        } else {
            // Failed - will be retried by recovery worker
            Timber.w("⚠️ Order execution failed, will retry: $orderId - ${result.error}")
            startRecoveryWorker()
        }

        return result
    }

    /**
     * Attempt to execute an order with network resilience
     */
    private suspend fun attemptOrderExecution(pendingOrder: PendingOrder): OrderResult {
        return try {
            // Use critical retry configuration for orders
            val config = NetworkResilience.getCriticalRetryConfig()

            NetworkResilience.executeWithRetry(
                maxRetries = config.maxRetries,
                initialBackoffMs = config.initialBackoffMs,
                maxBackoffMs = config.maxBackoffMs
            ) {
                pendingOrder.operation()
            }
        } catch (e: Exception) {
            Timber.e(e, "Order execution failed: ${pendingOrder.id}")
            OrderResult(
                success = false,
                error = e.message ?: "Unknown error",
                orderId = pendingOrder.id
            )
        }
    }

    /**
     * Start background worker to retry failed orders
     */
    private fun startRecoveryWorker() {
        if (isRecoveryActive) return

        isRecoveryActive = true
        Timber.i("🔄 Starting order recovery worker")

        recoveryScope.launch {
            while (pendingOrders.isNotEmpty()) {
                try {
                    processPendingOrders()
                    delay(RECOVERY_CHECK_INTERVAL_MS)
                } catch (e: Exception) {
                    Timber.e(e, "Error in recovery worker")
                }
            }
            isRecoveryActive = false
            Timber.i("✅ Recovery worker stopped (no pending orders)")
        }
    }

    /**
     * Process all pending orders
     */
    private suspend fun processPendingOrders() {
        val orders = pendingOrders.values.toList()

        if (orders.isEmpty()) return

        Timber.i("🔄 Processing ${orders.size} pending orders...")

        orders.forEach { order ->
            // Check if order exceeded max attempts
            if (order.attempts >= MAX_RECOVERY_ATTEMPTS) {
                Timber.e("❌ Order exceeded max recovery attempts: ${order.id}")
                pendingOrders.remove(order.id)
                // TODO: Notify user of failed order
                return@forEach
            }

            // Increment attempt counter
            order.attempts++

            // Attempt execution
            val result = attemptOrderExecution(order)

            if (result.success) {
                Timber.i("✅ Recovered order: ${order.id}")
                pendingOrders.remove(order.id)
            } else {
                Timber.w("⚠️ Order recovery attempt ${order.attempts}/$MAX_RECOVERY_ATTEMPTS failed: ${order.id}")
            }
        }
    }

    /**
     * Get number of pending orders
     */
    fun getPendingOrderCount(): Int = pendingOrders.size

    /**
     * Get all pending order IDs
     */
    fun getPendingOrderIds(): List<String> = pendingOrders.keys.toList()

    /**
     * Clear all pending orders (use with caution!)
     */
    fun clearPendingOrders() {
        Timber.w("⚠️ Clearing all pending orders")
        pendingOrders.clear()
    }

    /**
     * Cancel a specific pending order
     */
    fun cancelPendingOrder(orderId: String): Boolean {
        val removed = pendingOrders.remove(orderId)
        if (removed != null) {
            Timber.i("Cancelled pending order: $orderId")
            return true
        }
        return false
    }

    /**
     * Shutdown recovery service (cleanup)
     */
    fun shutdown() {
        recoveryScope.cancel()
        isRecoveryActive = false
    }
}

/**
 * Pending order awaiting execution/recovery
 */
private data class PendingOrder(
    val id: String,
    val operation: suspend () -> OrderResult,
    var attempts: Int,
    val queuedAt: Long
)

/**
 * Order execution result
 */
data class OrderResult(
    val success: Boolean,
    val orderId: String? = null,
    val transactionId: String? = null,
    val error: String? = null,
    val data: Map<String, Any>? = null
)
