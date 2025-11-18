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

    // Soft-delete methods (version 16+)

    /**
     * Mark a strategy as invalid (soft-delete)
     * Used when strategy contains hardcoded prices or other validation issues
     */
    @Query("""
        UPDATE strategies
        SET isInvalid = 1,
            invalidReason = :reason,
            invalidatedAt = :timestamp
        WHERE id = :strategyId
    """)
    suspend fun markAsInvalid(
        strategyId: String,
        reason: String,
        timestamp: Long = System.currentTimeMillis()
    )

    /**
     * Get only valid strategies (excluding soft-deleted ones)
     * This should be used for most strategy queries
     */
    @Query("SELECT * FROM strategies WHERE isInvalid = 0 ORDER BY createdAt DESC")
    fun getValidStrategies(): Flow<List<StrategyEntity>>

    /**
     * Get only invalid strategies (for debugging and review)
     */
    @Query("SELECT * FROM strategies WHERE isInvalid = 1 ORDER BY invalidatedAt DESC")
    fun getInvalidStrategies(): Flow<List<StrategyEntity>>

    /**
     * Get active AND valid strategies (excludes soft-deleted)
     * This prevents invalid strategies from being executed
     */
    @Query("SELECT * FROM strategies WHERE isActive = 1 AND isInvalid = 0")
    fun getActiveValidStrategies(): Flow<List<StrategyEntity>>

    /**
     * Restore an invalidated strategy
     */
    @Query("""
        UPDATE strategies
        SET isInvalid = 0,
            invalidReason = NULL,
            invalidatedAt = NULL
        WHERE id = :strategyId
    """)
    suspend fun restoreStrategy(strategyId: String)
}
