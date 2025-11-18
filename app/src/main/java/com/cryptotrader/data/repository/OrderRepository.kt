package com.cryptotrader.data.repository

import android.content.Context
import com.cryptotrader.data.local.dao.OrderDao
import com.cryptotrader.data.local.dao.PositionDao
import com.cryptotrader.data.local.entities.OrderEntity
import com.cryptotrader.data.remote.kraken.KrakenApiService
import com.cryptotrader.data.remote.kraken.RateLimiter
import com.cryptotrader.data.remote.kraken.dto.OrderInfo
import com.cryptotrader.domain.model.Order
import com.cryptotrader.domain.model.OrderStatus
import com.cryptotrader.domain.model.OrderType
import com.cryptotrader.domain.model.TradeType
import com.cryptotrader.utils.CryptoUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing trading orders
 *
 * Responsibilities:
 * - Place new orders on Kraken exchange
 * - Cancel orders
 * - Sync order status with exchange
 * - Maintain local order database
 * - Handle paper trading mode
 */
@Singleton
class OrderRepository @Inject constructor(
    private val krakenApi: KrakenApiService,
    private val orderDao: OrderDao,
    private val positionDao: PositionDao,
    private val rateLimiter: RateLimiter,
    private val context: Context
) {

    /**
     * Place a new order
     *
     * @param pair Trading pair (e.g., "XBTUSD")
     * @param type Buy or sell
     * @param orderType Market, limit, stop-loss, trailing-stop, etc.
     * @param volume Order quantity
     * @param price Limit price (required for LIMIT orders)
     * @param stopPrice Stop price (required for STOP_LOSS orders)
     * @param strategyId Optional strategy identifier (passed as userref to Kraken)
     * @param positionId Optional position identifier
     * @param leverage Optional leverage (2-5x) - null for no leverage
     * @param postOnly If true, order will only add liquidity (maker-only, lower fees, may be rejected)
     * @param timeInForce Order time-in-force: GTC (default), IOC, or GTD
     * @param expiresAt Expiration timestamp for GTD orders (milliseconds since epoch)
     * @param feeAsset Prefer fees in BASE or QUOTE currency
     * @param volumeInQuote For market buy orders: specify volume in quote currency (e.g., "buy $100 of BTC")
     * @return Result containing the created order or error
     */
    suspend fun placeOrder(
        pair: String,
        type: TradeType,
        orderType: OrderType,
        volume: Double,
        price: Double? = null,
        stopPrice: Double? = null,
        strategyId: String? = null,
        positionId: String? = null,
        leverage: Int? = null,
        postOnly: Boolean = false,
        timeInForce: com.cryptotrader.domain.model.TimeInForce = com.cryptotrader.domain.model.TimeInForce.GTC,
        expiresAt: Long? = null,
        feeAsset: com.cryptotrader.domain.model.FeeAsset = com.cryptotrader.domain.model.FeeAsset.QUOTE,
        volumeInQuote: Boolean = false
    ): Result<Order> {
        return try {
            // Create order entity with PENDING status BEFORE API call
            val orderId = UUID.randomUUID().toString()
            val orderEntity = OrderEntity(
                id = orderId,
                positionId = positionId,
                pair = pair,
                type = type.toString(),
                orderType = orderType.toString(),
                quantity = volume,
                price = price,
                stopPrice = stopPrice,
                krakenOrderId = null,
                status = OrderStatus.PENDING.toString(),
                placedAt = System.currentTimeMillis()
            )

            // Save to database
            orderDao.insertOrder(orderEntity)
            Timber.d("Order created locally with PENDING status: $orderId")

            // Check if paper trading mode
            if (CryptoUtils.isPaperTradingMode(context)) {
                Timber.d("Paper trading mode - simulating order placement")

                // Simulate successful order placement
                val simulatedKrakenId = "PAPER-${UUID.randomUUID()}"
                orderDao.updateKrakenOrderId(orderId, simulatedKrakenId)

                val updatedEntity = orderEntity.copy(
                    krakenOrderId = simulatedKrakenId,
                    status = OrderStatus.OPEN.toString()
                )
                orderDao.updateOrder(updatedEntity)

                Timber.d("Paper order placed successfully: $simulatedKrakenId")
                return Result.success(updatedEntity.toDomain())
            }

            // Rate limit for private API
            rateLimiter.waitForPrivateApiPermission()

            val nonce = CryptoUtils.generateNonce()

            // Prepare price parameters based on order type
            val limitPrice = when (orderType) {
                OrderType.LIMIT, OrderType.STOP_LOSS_LIMIT, OrderType.TAKE_PROFIT_LIMIT -> price?.toString()
                else -> null
            }

            val price2 = when (orderType) {
                OrderType.STOP_LOSS, OrderType.STOP_LOSS_LIMIT -> stopPrice?.toString()
                OrderType.TAKE_PROFIT, OrderType.TAKE_PROFIT_LIMIT -> stopPrice?.toString()
                else -> null
            }

            // Call Kraken API
            val response = krakenApi.addOrder(
                nonce = nonce,
                pair = pair,
                type = type.toString().lowercase(),
                orderType = OrderType.toKrakenFormat(orderType),
                volume = volume.toString(),
                price = limitPrice,
                price2 = price2,
                validate = false
            )

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!

                if (body.error.isEmpty() && body.result != null) {
                    // Success - update order with Kraken order ID and OPEN status
                    val krakenOrderId = body.result.transactionIds.firstOrNull()
                        ?: return Result.failure(Exception("No order ID returned from Kraken"))

                    orderDao.updateKrakenOrderId(orderId, krakenOrderId)

                    val updatedEntity = orderEntity.copy(
                        krakenOrderId = krakenOrderId,
                        status = OrderStatus.OPEN.toString()
                    )
                    orderDao.updateOrder(updatedEntity)

                    Timber.d("Order placed successfully on Kraken: $krakenOrderId")
                    Result.success(updatedEntity.toDomain())
                } else {
                    // API error - mark order as REJECTED
                    val errorMessage = body.error.joinToString(", ")
                    orderDao.markOrderRejected(orderId, errorMessage)

                    Timber.e("Order rejected by Kraken: $errorMessage")
                    Result.failure(Exception("Order rejected: $errorMessage"))
                }
            } else {
                // HTTP error - mark order as REJECTED
                val errorMessage = "HTTP ${response.code()}: ${response.message()}"
                orderDao.markOrderRejected(orderId, errorMessage)

                Timber.e("Order placement failed: $errorMessage")
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            Timber.e(e, "Error placing order")
            Result.failure(e)
        }
    }

    /**
     * Cancel an order by local database ID
     *
     * @param orderId Local database order ID
     * @return Result indicating success or failure
     */
    suspend fun cancelOrder(orderId: String): Result<Boolean> {
        return try {
            // Get order from database
            val orderEntity = orderDao.getOrderById(orderId)
                ?: return Result.failure(Exception("Order not found: $orderId"))

            val krakenOrderId = orderEntity.krakenOrderId
                ?: return Result.failure(Exception("Order has no Kraken ID, cannot cancel"))

            // Check if paper trading mode
            if (CryptoUtils.isPaperTradingMode(context)) {
                Timber.d("Paper trading mode - simulating order cancellation")
                orderDao.markOrderCancelled(orderId, System.currentTimeMillis())
                return Result.success(true)
            }

            // Rate limit for private API
            rateLimiter.waitForPrivateApiPermission()

            val nonce = CryptoUtils.generateNonce()

            // Call Kraken API to cancel order
            val response = krakenApi.cancelOrder(
                nonce = nonce,
                txid = krakenOrderId
            )

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!

                if (body.error.isEmpty() && body.result != null) {
                    // Success - update order status to CANCELLED
                    orderDao.markOrderCancelled(orderId, System.currentTimeMillis())

                    Timber.d("Order cancelled successfully: $krakenOrderId")
                    Result.success(true)
                } else {
                    val errorMessage = body.error.joinToString(", ")
                    Timber.e("Order cancellation failed: $errorMessage")
                    Result.failure(Exception("Cancellation failed: $errorMessage"))
                }
            } else {
                val errorMessage = "HTTP ${response.code()}: ${response.message()}"
                Timber.e("Order cancellation HTTP error: $errorMessage")
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            Timber.e(e, "Error cancelling order")
            Result.failure(e)
        }
    }

    /**
     * Cancel all open orders
     *
     * @return Result containing count of cancelled orders
     */
    suspend fun cancelAllOrders(): Result<Int> {
        return try {
            // Check if paper trading mode
            if (CryptoUtils.isPaperTradingMode(context)) {
                Timber.d("Paper trading mode - simulating cancel all orders")

                // Get all active orders from database
                val activeOrders = orderDao.getActiveOrders()
                var count = 0

                activeOrders.collect { orders ->
                    orders.forEach { order ->
                        orderDao.markOrderCancelled(order.id, System.currentTimeMillis())
                        count++
                    }
                }

                return Result.success(count)
            }

            // Rate limit for private API
            rateLimiter.waitForPrivateApiPermission()

            val nonce = CryptoUtils.generateNonce()

            // Call Kraken API to cancel all orders
            val response = krakenApi.cancelAllOrders(nonce = nonce)

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!

                if (body.error.isEmpty() && body.result != null) {
                    val count = (body.result["count"] as? Number)?.toInt() ?: 0

                    // Update all active orders in database to CANCELLED
                    val timestamp = System.currentTimeMillis()
                    val activeOrders = mutableListOf<OrderEntity>()

                    orderDao.getActiveOrders().collect { orders ->
                        activeOrders.addAll(orders)
                    }

                    activeOrders.forEach { order ->
                        orderDao.markOrderCancelled(order.id, timestamp)
                    }

                    Timber.d("All orders cancelled successfully: $count orders")
                    Result.success(count)
                } else {
                    val errorMessage = body.error.joinToString(", ")
                    Timber.e("Cancel all orders failed: $errorMessage")
                    Result.failure(Exception("Cancel all failed: $errorMessage"))
                }
            } else {
                val errorMessage = "HTTP ${response.code()}: ${response.message()}"
                Timber.e("Cancel all orders HTTP error: $errorMessage")
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            Timber.e(e, "Error cancelling all orders")
            Result.failure(e)
        }
    }

    /**
     * Sync open orders from Kraken API
     * Updates local database with current order status
     *
     * @return Result containing list of open orders
     */
    suspend fun syncOpenOrders(): Result<List<Order>> {
        return try {
            // Skip API call in paper trading mode
            if (CryptoUtils.isPaperTradingMode(context)) {
                Timber.d("Paper trading mode - returning local open orders")
                val orders = mutableListOf<Order>()
                orderDao.getActiveOrders().collect { entities ->
                    orders.addAll(entities.map { it.toDomain() })
                }
                return Result.success(orders)
            }

            // Rate limit for private API
            rateLimiter.waitForPrivateApiPermission()

            val nonce = CryptoUtils.generateNonce()

            // Call Kraken API
            val response = krakenApi.openOrders(
                nonce = nonce,
                trades = false
            )

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!

                if (body.error.isEmpty() && body.result != null) {
                    val openOrders = body.result.open
                    val orders = mutableListOf<Order>()

                    // Update or insert each order
                    openOrders.forEach { (krakenOrderId, orderInfo) ->
                        val order = updateOrCreateOrderFromKraken(krakenOrderId, orderInfo)
                        orders.add(order)
                    }

                    Timber.d("Synced ${orders.size} open orders from Kraken")
                    Result.success(orders)
                } else {
                    val errorMessage = body.error.joinToString(", ")
                    Timber.e("Sync open orders failed: $errorMessage")
                    Result.failure(Exception("Sync failed: $errorMessage"))
                }
            } else {
                val errorMessage = "HTTP ${response.code()}: ${response.message()}"
                Timber.e("Sync open orders HTTP error: $errorMessage")
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            Timber.e(e, "Error syncing open orders")
            Result.failure(e)
        }
    }

    /**
     * Sync closed orders from Kraken API
     *
     * @param count Maximum number of orders to fetch
     * @return Result containing list of closed orders
     */
    suspend fun syncClosedOrders(count: Int = 50): Result<List<Order>> {
        return try {
            // Skip API call in paper trading mode
            if (CryptoUtils.isPaperTradingMode(context)) {
                Timber.d("Paper trading mode - returning local closed orders")
                val orders = mutableListOf<Order>()
                orderDao.getOrdersByStatus("FILLED").collect { entities ->
                    orders.addAll(entities.map { it.toDomain() })
                }
                return Result.success(orders.take(count))
            }

            // Rate limit for private API
            rateLimiter.waitForPrivateApiPermission()

            val nonce = CryptoUtils.generateNonce()

            // Call Kraken API
            val response = krakenApi.closedOrders(
                nonce = nonce,
                trades = false,
                ofs = 0,
                closetime = "both"
            )

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!

                if (body.error.isEmpty() && body.result != null) {
                    val closedOrders = body.result.closed
                    val orders = mutableListOf<Order>()

                    // Update or insert each order
                    closedOrders.forEach { (krakenOrderId, orderInfo) ->
                        val order = updateOrCreateOrderFromKraken(krakenOrderId, orderInfo)
                        orders.add(order)
                    }

                    Timber.d("Synced ${orders.size} closed orders from Kraken")
                    Result.success(orders.take(count))
                } else {
                    val errorMessage = body.error.joinToString(", ")
                    Timber.e("Sync closed orders failed: $errorMessage")
                    Result.failure(Exception("Sync failed: $errorMessage"))
                }
            } else {
                val errorMessage = "HTTP ${response.code()}: ${response.message()}"
                Timber.e("Sync closed orders HTTP error: $errorMessage")
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            Timber.e(e, "Error syncing closed orders")
            Result.failure(e)
        }
    }

    /**
     * Update status of a single order by querying Kraken
     *
     * @param orderId Local database order ID
     * @return Result containing updated order
     */
    suspend fun updateOrderStatus(orderId: String): Result<Order> {
        return try {
            // Get order from database
            val orderEntity = orderDao.getOrderById(orderId)
                ?: return Result.failure(Exception("Order not found: $orderId"))

            val krakenOrderId = orderEntity.krakenOrderId
                ?: return Result.failure(Exception("Order has no Kraken ID"))

            // Skip API call in paper trading mode
            if (CryptoUtils.isPaperTradingMode(context)) {
                Timber.d("Paper trading mode - returning local order status")
                return Result.success(orderEntity.toDomain())
            }

            // Rate limit for private API
            rateLimiter.waitForPrivateApiPermission()

            val nonce = CryptoUtils.generateNonce()

            // Call Kraken API
            val response = krakenApi.queryOrders(
                nonce = nonce,
                txid = krakenOrderId,
                trades = false
            )

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!

                if (body.error.isEmpty() && body.result != null) {
                    val orderInfo = body.result[krakenOrderId]
                        ?: return Result.failure(Exception("Order not found in Kraken response"))

                    val updatedOrder = updateOrCreateOrderFromKraken(krakenOrderId, orderInfo)

                    Timber.d("Order status updated: $krakenOrderId -> ${updatedOrder.status}")
                    Result.success(updatedOrder)
                } else {
                    val errorMessage = body.error.joinToString(", ")
                    Timber.e("Query order failed: $errorMessage")
                    Result.failure(Exception("Query failed: $errorMessage"))
                }
            } else {
                val errorMessage = "HTTP ${response.code()}: ${response.message()}"
                Timber.e("Query order HTTP error: $errorMessage")
                Result.failure(Exception(errorMessage))
            }
        } catch (e: Exception) {
            Timber.e(e, "Error updating order status")
            Result.failure(e)
        }
    }

    /**
     * Get order by local database ID
     *
     * @param id Local database order ID
     * @return Order or null if not found
     */
    suspend fun getOrderById(id: String): Order? {
        return try {
            orderDao.getOrderById(id)?.toDomain()
        } catch (e: Exception) {
            Timber.e(e, "Error getting order by ID")
            null
        }
    }

    /**
     * Get flow of active orders (PENDING, OPEN, PARTIALLY_FILLED)
     *
     * @return Flow emitting list of active orders
     */
    fun getActiveOrdersFlow(): Flow<List<Order>> {
        return orderDao.getActiveOrders().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /**
     * Get orders for a specific position
     *
     * @param positionId Position ID
     * @return Flow emitting list of orders for the position
     */
    fun getOrdersByPosition(positionId: String): Flow<List<Order>> {
        return orderDao.getOrdersByPosition(positionId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /**
     * Update or create order from Kraken OrderInfo
     * Helper method for syncing orders
     */
    private suspend fun updateOrCreateOrderFromKraken(
        krakenOrderId: String,
        orderInfo: OrderInfo
    ): Order {
        // Try to find existing order by Kraken ID
        val existingOrder = orderDao.getOrderByKrakenId(krakenOrderId)

        val volume = orderInfo.volume.toDoubleOrNull() ?: 0.0
        val volumeExecuted = orderInfo.volumeExecuted.toDoubleOrNull() ?: 0.0
        val status = OrderStatus.fromKrakenStatus(orderInfo.status, volumeExecuted, volume)

        val orderEntity = if (existingOrder != null) {
            // Update existing order
            existingOrder.copy(
                status = status.toString(),
                filledQuantity = volumeExecuted,
                averageFillPrice = orderInfo.price.toDoubleOrNull(),
                fee = orderInfo.fee.toDoubleOrNull(),
                filledAt = if (status == OrderStatus.FILLED) {
                    orderInfo.closeTime?.toLong()?.times(1000) ?: System.currentTimeMillis()
                } else null,
                cancelledAt = if (status == OrderStatus.CANCELLED) {
                    orderInfo.closeTime?.toLong()?.times(1000) ?: System.currentTimeMillis()
                } else null
            )
        } else {
            // Create new order from Kraken data
            OrderEntity(
                id = UUID.randomUUID().toString(),
                positionId = null,
                pair = orderInfo.description?.pair ?: "",
                type = orderInfo.description?.type?.uppercase() ?: "BUY",
                orderType = orderInfo.description?.orderType?.uppercase()?.replace("-", "_") ?: "MARKET",
                quantity = volume,
                price = orderInfo.description?.price?.toDoubleOrNull(),
                stopPrice = orderInfo.description?.price2?.toDoubleOrNull(),
                krakenOrderId = krakenOrderId,
                status = status.toString(),
                placedAt = orderInfo.openTime.toLong() * 1000,
                filledAt = if (status == OrderStatus.FILLED) {
                    orderInfo.closeTime?.toLong()?.times(1000)
                } else null,
                cancelledAt = if (status == OrderStatus.CANCELLED) {
                    orderInfo.closeTime?.toLong()?.times(1000)
                } else null,
                filledQuantity = volumeExecuted,
                averageFillPrice = orderInfo.price.toDoubleOrNull(),
                fee = orderInfo.fee.toDoubleOrNull()
            )
        }

        // Save to database
        orderDao.updateOrder(orderEntity)

        return orderEntity.toDomain()
    }

    /**
     * Convert OrderEntity to domain Order
     */
    private fun OrderEntity.toDomain(): Order {
        return Order(
            id = id,
            positionId = positionId,
            pair = pair,
            type = TradeType.fromString(type),
            orderType = OrderType.fromString(orderType),
            quantity = quantity,
            price = price,
            stopPrice = stopPrice,
            krakenOrderId = krakenOrderId,
            status = OrderStatus.fromString(status),
            placedAt = placedAt,
            filledAt = filledAt,
            cancelledAt = cancelledAt,
            filledQuantity = filledQuantity,
            averageFillPrice = averageFillPrice,
            fee = fee,
            errorMessage = errorMessage
        )
    }

    /**
     * Convert domain Order to OrderEntity
     */
    private fun Order.toEntity(): OrderEntity {
        return OrderEntity(
            id = id,
            positionId = positionId,
            pair = pair,
            type = type.toString(),
            orderType = orderType.toString(),
            quantity = quantity,
            price = price,
            stopPrice = stopPrice,
            krakenOrderId = krakenOrderId,
            status = status.toString(),
            placedAt = placedAt,
            filledAt = filledAt,
            cancelledAt = cancelledAt,
            filledQuantity = filledQuantity,
            averageFillPrice = averageFillPrice,
            fee = fee,
            errorMessage = errorMessage
        )
    }
}
