package com.cryptotrader.data.local.dao

import androidx.room.*
import com.cryptotrader.data.local.entities.TradeEntity
import kotlinx.coroutines.flow.Flow

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
}
