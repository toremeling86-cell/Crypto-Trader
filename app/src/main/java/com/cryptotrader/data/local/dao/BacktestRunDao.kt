package com.cryptotrader.data.local.dao

import androidx.room.*
import com.cryptotrader.data.local.entities.BacktestRunEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for Backtest Run operations
 *
 * Tracks which data was used for each backtest
 */
@Dao
interface BacktestRunDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(run: BacktestRunEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(runs: List<BacktestRunEntity>)

    /**
     * Get all backtest runs for a strategy
     */
    @Query("""
        SELECT * FROM backtest_runs
        WHERE strategyId = :strategyId
        ORDER BY executedAt DESC
    """)
    suspend fun getRunsForStrategy(strategyId: String): List<BacktestRunEntity>

    /**
     * Get runs as Flow (reactive updates)
     */
    @Query("""
        SELECT * FROM backtest_runs
        WHERE strategyId = :strategyId
        ORDER BY executedAt DESC
    """)
    fun getRunsForStrategyFlow(strategyId: String): Flow<List<BacktestRunEntity>>

    /**
     * Get latest backtest run for strategy
     */
    @Query("""
        SELECT * FROM backtest_runs
        WHERE strategyId = :strategyId
        ORDER BY executedAt DESC
        LIMIT 1
    """)
    suspend fun getLatestRun(strategyId: String): BacktestRunEntity?

    /**
     * Get all backtest runs for specific asset
     */
    @Query("""
        SELECT * FROM backtest_runs
        WHERE asset = :asset
        ORDER BY executedAt DESC
    """)
    suspend fun getRunsForAsset(asset: String): List<BacktestRunEntity>

    /**
     * Get successful backtest runs (win rate > threshold)
     */
    @Query("""
        SELECT * FROM backtest_runs
        WHERE winRate >= :minWinRate
        AND totalPnLPercent >= :minReturn
        ORDER BY totalPnLPercent DESC
    """)
    suspend fun getSuccessfulRuns(minWinRate: Double = 60.0, minReturn: Double = 10.0): List<BacktestRunEntity>

    /**
     * Get backtest run by ID
     */
    @Query("SELECT * FROM backtest_runs WHERE id = :id")
    suspend fun getRunById(id: Long): BacktestRunEntity?

    @Query("DELETE FROM backtest_runs WHERE strategyId = :strategyId")
    suspend fun deleteRunsForStrategy(strategyId: String)

    @Query("DELETE FROM backtest_runs WHERE id = :id")
    suspend fun deleteRun(id: Long)

    @Query("DELETE FROM backtest_runs")
    suspend fun deleteAll()
}
