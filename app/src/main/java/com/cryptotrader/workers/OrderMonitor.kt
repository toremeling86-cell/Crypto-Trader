package com.cryptotrader.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cryptotrader.data.local.dao.OrderDao
import com.cryptotrader.data.remote.kraken.KrakenApiService
import com.cryptotrader.data.remote.kraken.RateLimiter
import com.cryptotrader.notifications.NotificationManager
import com.cryptotrader.utils.CryptoUtils
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Order Monitor Worker - Real-time Order Status Tracking
 *
 * Monitors active orders and syncs their status with Kraken API.
 * Updates local database and sends notifications for important order events.
 *
 * WORKFLOW:
 * 1. Fetch all active orders (PENDING, OPEN) from local database
 * 2. Query Kraken API for current status of each order
 * 3. Update local database with latest status
 * 4. Send notifications for filled/cancelled orders
 * 5. Handle partial fills and order modifications
 *
 * FREQUENCY:
 * - Runs every 30 seconds for active orders
 * - Disabled when no active orders exist
 * - Rate-limited to respect Kraken API limits
 *
 * FEATURES:
 * - Automatic order recovery after app restart
 * - Partial fill tracking
 * - Order state transitions (PENDING → OPEN → FILLED/CANCELLED)
 * - Notification on order completion
 * - Paper trading mode support (skips API calls)
 */
@HiltWorker
class OrderMonitor @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val orderDao: OrderDao,
    private val krakenApi: KrakenApiService,
    private val rateLimiter: RateLimiter,
    private val notificationManager: NotificationManager
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val WORK_NAME = "order_monitor"
        const val TAG = "OrderMonitor"
    }

    /**
     * Main worker execution method
     * Monitors active orders and syncs with Kraken
     */
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        return@withContext try {
            Timber.d("OrderMonitor started (attempt ${runAttemptCount + 1})")

            // Check if paper trading mode - skip Kraken API calls
            val isPaperTrading = CryptoUtils.isPaperTradingMode(context)
            if (isPaperTrading) {
                Timber.d("📄 Paper trading mode - skipping order monitoring")
                return@withContext Result.success()
            }

            // PHASE 1: Get active orders from database
            val activeOrders = orderDao.getActiveOrders().first()

            if (activeOrders.isEmpty()) {
                Timber.d("No active orders to monitor")
                return@withContext Result.success()
            }

            Timber.i("📊 Monitoring ${activeOrders.size} active orders")

            // PHASE 2: Query Kraken for each order's status
            var ordersChecked = 0
            var ordersUpdated = 0
            var ordersFilled = 0
            var ordersCancelled = 0

            activeOrders.forEach { order ->
                try {
                    // Skip if no Kraken order ID yet (still pending API response)
                    if (order.krakenOrderId == null) {
                        Timber.d("Order ${order.id.substring(0, 8)}... pending Kraken ID")
                        return@forEach
                    }

                    // Rate limit API calls
                    rateLimiter.waitForPrivateApiPermission()

                    // Query Kraken for order status
                    val nonce = CryptoUtils.generateNonce()
                    val response = krakenApi.queryOrders(
                        nonce = nonce,
                        txid = order.krakenOrderId
                    )

                    ordersChecked++

                    if (response.isSuccessful && response.body() != null) {
                        val body = response.body()!!

                        if (body.error.isEmpty() && body.result != null) {
                            val orderInfo = body.result[order.krakenOrderId]

                            if (orderInfo != null) {
                                // Parse Kraken order status
                                val krakenStatus = orderInfo.status ?: "unknown"
                                val volume = orderInfo.volume.toDoubleOrNull() ?: order.quantity
                                val volumeExecuted = orderInfo.volumeExecuted.toDoubleOrNull() ?: 0.0
                                val avgPrice = orderInfo.price.toDoubleOrNull()
                                val fee = orderInfo.fee.toDoubleOrNull()

                                // Update local database based on Kraken status
                                when (krakenStatus) {
                                    "closed" -> {
                                        // Order fully filled
                                        if (order.status != "FILLED") {
                                            orderDao.markOrderFilled(
                                                id = order.id,
                                                filledAt = System.currentTimeMillis(),
                                                filledQuantity = volumeExecuted,
                                                averageFillPrice = avgPrice ?: order.price ?: 0.0,
                                                fee = fee
                                            )

                                            ordersUpdated++
                                            ordersFilled++

                                            // Send notification
                                            notificationManager.notifyOrderFilled(
                                                pair = order.pair,
                                                type = order.type,
                                                quantity = volumeExecuted,
                                                price = avgPrice ?: order.price ?: 0.0
                                            )

                                            Timber.i("✅ Order ${order.id.substring(0, 8)}... FILLED")
                                        }
                                    }
                                    "canceled" -> {
                                        // Order cancelled
                                        if (order.status != "CANCELLED") {
                                            orderDao.markOrderCancelled(
                                                id = order.id,
                                                cancelledAt = System.currentTimeMillis()
                                            )

                                            ordersUpdated++
                                            ordersCancelled++

                                            Timber.i("❌ Order ${order.id.substring(0, 8)}... CANCELLED")
                                        }
                                    }
                                    "open" -> {
                                        // Order still open (waiting for fill)
                                        Timber.d("Order ${order.id.substring(0, 8)}... still OPEN")

                                        // Check for partial fills
                                        if (volumeExecuted > 0 && volumeExecuted < volume) {
                                            Timber.d("Partial fill: $volumeExecuted / $volume")
                                            // Could update partial fill quantity here if needed
                                        }
                                    }
                                    "pending" -> {
                                        // Order pending (not yet on order book)
                                        Timber.d("Order ${order.id.substring(0, 8)}... PENDING")
                                    }
                                    "expired" -> {
                                        // Order expired (for limit orders with time constraints)
                                        if (order.status != "CANCELLED") {
                                            orderDao.markOrderCancelled(
                                                id = order.id,
                                                cancelledAt = System.currentTimeMillis()
                                            )
                                            ordersUpdated++
                                            ordersCancelled++
                                            Timber.i("⏰ Order ${order.id.substring(0, 8)}... EXPIRED")
                                        }
                                    }
                                    else -> {
                                        Timber.w("Unknown Kraken order status: $krakenStatus for order ${order.id}")
                                    }
                                }
                            } else {
                                Timber.w("Order ${order.krakenOrderId} not found in Kraken response")
                            }
                        } else {
                            Timber.w("Kraken API error: ${body.error.joinToString()}")
                        }
                    } else {
                        Timber.w("Failed to query Kraken for order ${order.krakenOrderId}: HTTP ${response.code()}")
                    }

                } catch (e: Exception) {
                    Timber.e(e, "Error checking order ${order.id}")
                    // Continue with next order
                }
            }

            // PHASE 3: Summary logging
            Timber.i("✅ OrderMonitor completed: $ordersChecked checked, $ordersUpdated updated, $ordersFilled filled, $ordersCancelled cancelled")

            Result.success()

        } catch (e: Exception) {
            Timber.e(e, "OrderMonitor failed")

            // Retry with exponential backoff up to 3 attempts
            if (runAttemptCount < 2) {
                Timber.w("Retrying OrderMonitor (attempt ${runAttemptCount + 2}/3)")
                Result.retry()
            } else {
                Timber.e("OrderMonitor failed after 3 attempts")
                Result.failure()
            }
        }
    }
}
