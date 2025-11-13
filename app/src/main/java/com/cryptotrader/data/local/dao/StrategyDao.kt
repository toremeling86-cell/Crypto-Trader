package com.cryptotrader.data.local.dao

import androidx.room.*
import com.cryptotrader.data.local.entities.StrategyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StrategyDao {
    @Query("SELECT * FROM strategies ORDER BY createdAt DESC")
    fun getAllStrategies(): Flow<List<StrategyEntity>>

    @Query("SELECT * FROM strategies WHERE isActive = 1")
    fun getActiveStrategies(): Flow<List<StrategyEntity>>

    @Query("SELECT * FROM strategies WHERE id = :strategyId")
    suspend fun getStrategyById(strategyId: String): StrategyEntity?

    @Query("SELECT * FROM strategies WHERE id = :strategyId")
    fun getStrategyByIdFlow(strategyId: String): Flow<StrategyEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStrategy(strategy: StrategyEntity)

    @Update
    suspend fun updateStrategy(strategy: StrategyEntity)

    @Delete
    suspend fun deleteStrategy(strategy: StrategyEntity)

    @Query("UPDATE strategies SET isActive = :isActive WHERE id = :strategyId")
    suspend fun setStrategyActive(strategyId: String, isActive: Boolean)

    @Query("DELETE FROM strategies WHERE id = :strategyId")
    suspend fun deleteStrategyById(strategyId: String)

    // Update strategy statistics
    @Query("""
        UPDATE strategies
        SET totalTrades = :totalTrades,
            successfulTrades = :successfulTrades,
            failedTrades = :failedTrades,
            winRate = :winRate,
            totalProfit = :totalProfit,
            lastExecuted = :lastExecuted
        WHERE id = :strategyId
    """)
    suspend fun updateStrategyStats(
        strategyId: String,
        totalTrades: Int,
        successfulTrades: Int,
        failedTrades: Int,
        winRate: Double,
        totalProfit: Double,
        lastExecuted: Long
    )

    @Query("UPDATE strategies SET lastExecuted = :timestamp WHERE id = :strategyId")
    suspend fun updateLastExecuted(strategyId: String, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM strategies WHERE isActive = 1")
    suspend fun getActiveStrategyCount(): Int

    @Query("SELECT * FROM strategies WHERE riskLevel = :riskLevel")
    fun getStrategiesByRiskLevel(riskLevel: String): Flow<List<StrategyEntity>>
}
