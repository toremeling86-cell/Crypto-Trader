package com.cryptotrader.data.local.dao

import androidx.room.*
import com.cryptotrader.data.local.entities.OrderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrder(order: OrderEntity): Long

    @Update
    suspend fun updateOrder(order: OrderEntity)

    @Delete
    suspend fun deleteOrder(order: OrderEntity)

    @Query("SELECT * FROM orders WHERE id = :id")
    suspend fun getOrderById(id: String): OrderEntity?

    @Query("SELECT * FROM orders WHERE krakenOrderId = :krakenOrderId")
    suspend fun getOrderByKrakenId(krakenOrderId: String): OrderEntity?

    @Query("SELECT * FROM orders WHERE positionId = :positionId")
    fun getOrdersByPosition(positionId: String): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders WHERE status IN ('PENDING', 'OPEN')")
    fun getActiveOrders(): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders WHERE pair = :pair AND status IN ('PENDING', 'OPEN')")
    fun getActiveOrdersForPair(pair: String): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders WHERE status = :status ORDER BY placedAt DESC")
    fun getOrdersByStatus(status: String): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders ORDER BY placedAt DESC LIMIT :limit")
    fun getRecentOrders(limit: Int = 50): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders WHERE placedAt >= :since ORDER BY placedAt DESC")
    fun getOrdersSince(since: Long): Flow<List<OrderEntity>>

    @Query("""
        UPDATE orders
        SET status = 'FILLED',
            filledAt = :filledAt,
            filledQuantity = :filledQuantity,
            averageFillPrice = :averageFillPrice,
            fee = :fee
        WHERE id = :id
    """)
    suspend fun markOrderFilled(
        id: String,
        filledAt: Long,
        filledQuantity: Double,
        averageFillPrice: Double,
        fee: Double?
    )

    @Query("""
        UPDATE orders
        SET status = 'CANCELLED',
            cancelledAt = :cancelledAt
        WHERE id = :id
    """)
    suspend fun markOrderCancelled(id: String, cancelledAt: Long)

    @Query("""
        UPDATE orders
        SET status = 'REJECTED',
            errorMessage = :errorMessage
        WHERE id = :id
    """)
    suspend fun markOrderRejected(id: String, errorMessage: String)

    @Query("UPDATE orders SET krakenOrderId = :krakenOrderId WHERE id = :id")
    suspend fun updateKrakenOrderId(id: String, krakenOrderId: String)

    @Query("SELECT COUNT(*) FROM orders WHERE positionId = :positionId AND status = 'FILLED'")
    suspend fun getFilledOrderCount(positionId: String): Int

    @Query("DELETE FROM orders WHERE placedAt < :before AND status IN ('FILLED', 'CANCELLED', 'REJECTED')")
    suspend fun deleteOldOrders(before: Long): Int
}
