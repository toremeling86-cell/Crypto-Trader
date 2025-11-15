package com.cryptotrader.data.local.dao

import androidx.room.*
import com.cryptotrader.data.local.entities.ExecutionLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExecutionLogDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLog(log: ExecutionLogEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLogs(logs: List<ExecutionLogEntity>)

    @Query("SELECT * FROM execution_logs WHERE id = :id")
    suspend fun getLogById(id: String): ExecutionLogEntity?

    @Query("SELECT * FROM execution_logs WHERE strategyId = :strategyId ORDER BY timestamp DESC")
    fun getLogsByStrategy(strategyId: String): Flow<List<ExecutionLogEntity>>

    @Query("SELECT * FROM execution_logs WHERE strategyId = :strategyId ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentLogsByStrategy(strategyId: String, limit: Int = 50): Flow<List<ExecutionLogEntity>>

    @Query("SELECT * FROM execution_logs WHERE eventType = :eventType ORDER BY timestamp DESC")
    fun getLogsByEventType(eventType: String): Flow<List<ExecutionLogEntity>>

    @Query("SELECT * FROM execution_logs WHERE strategyId = :strategyId AND eventType = :eventType ORDER BY timestamp DESC")
    fun getLogsByStrategyAndEvent(strategyId: String, eventType: String): Flow<List<ExecutionLogEntity>>

    @Query("SELECT * FROM execution_logs WHERE relatedPositionId = :positionId ORDER BY timestamp DESC")
    fun getLogsByPosition(positionId: String): Flow<List<ExecutionLogEntity>>

    @Query("SELECT * FROM execution_logs WHERE relatedOrderId = :orderId ORDER BY timestamp DESC")
    fun getLogsByOrder(orderId: String): Flow<List<ExecutionLogEntity>>

    @Query("SELECT * FROM execution_logs WHERE relatedTradeId = :tradeId ORDER BY timestamp DESC")
    fun getLogsByTrade(tradeId: String): Flow<List<ExecutionLogEntity>>

    @Query("SELECT * FROM execution_logs WHERE timestamp >= :since ORDER BY timestamp DESC")
    fun getLogsSince(since: Long): Flow<List<ExecutionLogEntity>>

    @Query("SELECT * FROM execution_logs WHERE strategyId = :strategyId AND timestamp >= :since ORDER BY timestamp DESC")
    fun getStrategyLogsSince(strategyId: String, since: Long): Flow<List<ExecutionLogEntity>>

    @Query("SELECT * FROM execution_logs WHERE eventType = 'ERROR' AND strategyId = :strategyId ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentErrors(strategyId: String, limit: Int = 10): Flow<List<ExecutionLogEntity>>

    @Query("SELECT * FROM execution_logs WHERE eventType = 'ERROR' ORDER BY timestamp DESC")
    fun getAllErrors(): Flow<List<ExecutionLogEntity>>

    @Query("SELECT COUNT(*) FROM execution_logs WHERE strategyId = :strategyId AND eventType = :eventType")
    suspend fun getEventCount(strategyId: String, eventType: String): Int

    @Query("SELECT COUNT(*) FROM execution_logs WHERE strategyId = :strategyId AND eventType = 'ERROR' AND timestamp >= :since")
    suspend fun getErrorCountSince(strategyId: String, since: Long): Int

    @Query("DELETE FROM execution_logs WHERE strategyId = :strategyId")
    suspend fun deleteLogsByStrategy(strategyId: String): Int

    @Query("DELETE FROM execution_logs WHERE timestamp < :before")
    suspend fun deleteOldLogs(before: Long): Int

    @Query("SELECT * FROM execution_logs ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentLogs(limit: Int = 100): Flow<List<ExecutionLogEntity>>
}
