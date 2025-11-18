package com.cryptotrader.data.local.dao

import androidx.room.*
import com.cryptotrader.data.local.entities.PositionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PositionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPosition(position: PositionEntity): Long

    @Update
    suspend fun updatePosition(position: PositionEntity)

    @Query("SELECT * FROM positions WHERE id = :id")
    suspend fun getPositionById(id: String): PositionEntity?

    @Query("SELECT * FROM positions WHERE status = 'OPEN'")
    fun getOpenPositions(): Flow<List<PositionEntity>>

    @Query("SELECT * FROM positions WHERE status = 'OPEN'")
    suspend fun getOpenPositionsSnapshot(): List<PositionEntity>

    @Query("SELECT * FROM positions WHERE strategyId = :strategyId")
    fun getPositionsByStrategy(strategyId: String): Flow<List<PositionEntity>>

    @Query("SELECT * FROM positions WHERE strategyId = :strategyId AND status = 'OPEN'")
    fun getOpenPositionsByStrategy(strategyId: String): Flow<List<PositionEntity>>

    @Query("SELECT * FROM positions WHERE pair = :pair AND status = 'OPEN'")
    suspend fun getOpenPositionForPair(pair: String): PositionEntity?

    @Query("SELECT * FROM positions WHERE pair = :pair")
    fun getPositionsByPair(pair: String): Flow<List<PositionEntity>>

    @Query("SELECT * FROM positions WHERE status = 'CLOSED' ORDER BY closedAt DESC LIMIT :limit")
    fun getRecentClosedPositions(limit: Int = 50): Flow<List<PositionEntity>>

    @Query("UPDATE positions SET unrealizedPnL = :pnl, unrealizedPnLPercent = :pnlPercent, lastUpdated = :time WHERE id = :id")
    suspend fun updateUnrealizedPnL(id: String, pnl: Double, pnlPercent: Double, time: Long)

    @Query("""
        UPDATE positions
        SET status = 'CLOSED',
            exitPrice = :exitPrice,
            closedAt = :closedAt,
            closeReason = :reason,
            realizedPnL = :pnl,
            realizedPnLPercent = :pnlPercent,
            exitTradeId = :exitTradeId
        WHERE id = :id
    """)
    suspend fun closePosition(
        id: String,
        exitPrice: Double,
        closedAt: Long,
        reason: String,
        pnl: Double,
        pnlPercent: Double,
        exitTradeId: String?
    )

    @Query("UPDATE positions SET stopLossOrderId = :orderId WHERE id = :id")
    suspend fun updateStopLossOrder(id: String, orderId: String?)

    @Query("UPDATE positions SET takeProfitOrderId = :orderId WHERE id = :id")
    suspend fun updateTakeProfitOrder(id: String, orderId: String?)

    @Query("SELECT COUNT(*) FROM positions WHERE strategyId = :strategyId AND status = 'OPEN'")
    suspend fun getOpenPositionCount(strategyId: String): Int

    @Query("SELECT SUM(realizedPnL) FROM positions WHERE strategyId = :strategyId AND status = 'CLOSED'")
    suspend fun getTotalRealizedPnL(strategyId: String): Double?

    @Query("SELECT AVG(realizedPnLPercent) FROM positions WHERE strategyId = :strategyId AND status = 'CLOSED' AND realizedPnLPercent IS NOT NULL")
    suspend fun getAverageReturnPercent(strategyId: String): Double?

    @Query("SELECT COUNT(*) FROM positions WHERE strategyId = :strategyId AND status = 'CLOSED' AND realizedPnL > 0")
    suspend fun getWinningPositionCount(strategyId: String): Int

    @Query("SELECT COUNT(*) FROM positions WHERE strategyId = :strategyId AND status = 'CLOSED'")
    suspend fun getTotalClosedPositionCount(strategyId: String): Int

    @Query("DELETE FROM positions WHERE strategyId = :strategyId")
    suspend fun deletePositionsByStrategy(strategyId: String): Int

    @Query("DELETE FROM positions WHERE closedAt < :before AND status = 'CLOSED'")
    suspend fun deleteOldClosedPositions(before: Long): Int
}
