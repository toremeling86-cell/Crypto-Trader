package com.cryptotrader.data.local.dao

import androidx.room.*
import com.cryptotrader.data.local.entities.TradeEntity
import kotlinx.coroutines.flow.Flow

data class StrategyTradeCount(
    val strategyId: String,
    val count: Int
)

@Dao
interface TradeDao {
    @Query("SELECT * FROM trades ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentTrades(limit: Int = 50): Flow<List<TradeEntity>>

    @Query("SELECT * FROM trades WHERE strategyId = :strategyId ORDER BY timestamp DESC")
    fun getTradesByStrategy(strategyId: String): Flow<List<TradeEntity>>

    @Query("SELECT * FROM trades WHERE pair = :pair ORDER BY timestamp DESC LIMIT :limit")
    fun getTradesByPair(pair: String, limit: Int = 100): Flow<List<TradeEntity>>

    @Query("SELECT * FROM trades WHERE status = :status ORDER BY timestamp DESC")
    fun getTradesByStatus(status: String): Flow<List<TradeEntity>>

    @Query("SELECT * FROM trades WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp DESC")
    fun getTradesBetween(startTime: Long, endTime: Long): Flow<List<TradeEntity>>

    @Insert
    suspend fun insertTrade(trade: TradeEntity): Long

    @Update
    suspend fun updateTrade(trade: TradeEntity)

    @Delete
    suspend fun deleteTrade(trade: TradeEntity)

    @Query("DELETE FROM trades WHERE id = :tradeId")
    suspend fun deleteTradeById(tradeId: Long)

    // Analytics queries
    @Query("SELECT SUM(CASE WHEN type = 'buy' THEN -cost ELSE cost - fee END) FROM trades WHERE strategyId = :strategyId")
    suspend fun getStrategyProfit(strategyId: String): Double?

    @Query("SELECT COUNT(*) FROM trades WHERE timestamp >= :startTime AND timestamp <= :endTime")
    suspend fun getTradeCountBetween(startTime: Long, endTime: Long): Int

    @Query("SELECT COUNT(*) FROM trades WHERE strategyId = :strategyId AND status = 'executed'")
    suspend fun getExecutedTradeCount(strategyId: String): Int

    @Query("SELECT AVG(profit) FROM trades WHERE strategyId = :strategyId AND profit IS NOT NULL")
    suspend fun getAverageProfit(strategyId: String): Double?

    @Query("SELECT SUM(fee) FROM trades WHERE strategyId = :strategyId")
    suspend fun getTotalFees(strategyId: String): Double?

    // Get all trades for export
    @Query("SELECT * FROM trades ORDER BY timestamp ASC")
    suspend fun getAllTrades(): List<TradeEntity>

    // Get all trades as Flow for P&L calculation
    @Query("SELECT * FROM trades ORDER BY timestamp ASC")
    fun getAllTradesFlow(): Flow<List<TradeEntity>>

    // Get trades since a specific timestamp (for daily/weekly P&L)
    @Query("SELECT * FROM trades WHERE timestamp >= :since ORDER BY timestamp ASC")
    fun getTradesSince(since: Long): Flow<List<TradeEntity>>

    // New execution tracking queries (version 7+)
    @Query("SELECT * FROM trades WHERE positionId = :positionId ORDER BY executedAt DESC")
    fun getTradesByPosition(positionId: String): Flow<List<TradeEntity>>

    @Query("SELECT * FROM trades WHERE krakenOrderId = :krakenOrderId")
    suspend fun getTradeByKrakenOrderId(krakenOrderId: String): TradeEntity?

    @Query("SELECT * FROM trades WHERE krakenTradeId = :krakenTradeId")
    suspend fun getTradeByKrakenTradeId(krakenTradeId: String): TradeEntity?

    @Query("SELECT * FROM trades WHERE orderType = :orderType ORDER BY executedAt DESC")
    fun getTradesByOrderType(orderType: String): Flow<List<TradeEntity>>

    @Query("SELECT * FROM trades WHERE strategyId = :strategyId AND executedAt >= :since")
    suspend fun getStrategyTradesSince(strategyId: String, since: Long): List<TradeEntity>

    @Query("SELECT SUM(realizedPnL) FROM trades WHERE strategyId = :strategyId AND realizedPnL IS NOT NULL")
    suspend fun getTotalRealizedPnL(strategyId: String): Double?

    @Query("SELECT AVG(realizedPnLPercent) FROM trades WHERE strategyId = :strategyId AND realizedPnLPercent IS NOT NULL")
    suspend fun getAverageRealizedPnLPercent(strategyId: String): Double?

    @Query("SELECT COUNT(*) FROM trades WHERE strategyId = :strategyId AND realizedPnL > 0")
    suspend fun getWinningTradeCount(strategyId: String): Int

    @Query("DELETE FROM trades WHERE executedAt < :before")
    suspend fun deleteTradesBefore(before: Long): Int

    // Advanced query methods for analytics
    /**
     * Get trades filtered by P&L range
     * Uses realizedPnL field or calculates from profit
     */
    @Query("""
        SELECT * FROM trades
        WHERE (realizedPnL IS NOT NULL AND realizedPnL BETWEEN :minPnL AND :maxPnL)
           OR (profit IS NOT NULL AND profit BETWEEN :minPnL AND :maxPnL)
        ORDER BY executedAt DESC
    """)
    fun getTradesByPnLRange(minPnL: Double, maxPnL: Double): Flow<List<TradeEntity>>

    /**
     * Get trades by multiple trading pairs
     */
    @Query("SELECT * FROM trades WHERE pair IN (:pairs) ORDER BY executedAt DESC")
    fun getTradesByPairs(pairs: List<String>): Flow<List<TradeEntity>>

    /**
     * Get recent trades with pagination support
     */
    @Query("SELECT * FROM trades ORDER BY executedAt DESC LIMIT :limit OFFSET :offset")
    fun getTradesPaged(limit: Int, offset: Int): Flow<List<TradeEntity>>

    /**
     * Get trade count by strategy
     */
    @Query("""
        SELECT strategyId, COUNT(*) as count
        FROM trades
        WHERE strategyId IS NOT NULL
        GROUP BY strategyId
    """)
    suspend fun getTradeCountByStrategy(): List<StrategyTradeCount>

    /**
     * Get all distinct trading pairs
     */
    @Query("SELECT DISTINCT pair FROM trades ORDER BY pair ASC")
    suspend fun getAllTradedPairs(): List<String>

    /**
     * Get total count of unique trading pairs
     */
    @Query("SELECT COUNT(DISTINCT pair) FROM trades")
    suspend fun getUniquePairCount(): Int

    /**
     * Get the most traded pair (by count)
     */
    @Query("""
        SELECT pair FROM trades
        GROUP BY pair
        ORDER BY COUNT(*) DESC
        LIMIT 1
    """)
    suspend fun getMostTradedPair(): String?

    /**
     * Get trades for statistics calculation
     * Returns all fields needed for comprehensive analytics
     */
    @Query("SELECT * FROM trades ORDER BY executedAt DESC")
    suspend fun getAllTradesForStatistics(): List<TradeEntity>
}
