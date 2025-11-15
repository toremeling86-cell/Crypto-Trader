package com.cryptotrader.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cryptotrader.data.local.entities.MarketCorrelationEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for market correlation operations
 */
@Dao
interface MarketCorrelationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCorrelation(correlation: MarketCorrelationEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCorrelations(correlations: List<MarketCorrelationEntity>)

    @Query("SELECT * FROM market_correlations WHERE symbol1 = :symbol1 AND symbol2 = :symbol2 ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestCorrelation(symbol1: String, symbol2: String): MarketCorrelationEntity?

    @Query("SELECT * FROM market_correlations WHERE symbol1 = :symbol ORDER BY timestamp DESC")
    fun getCorrelationsForSymbol(symbol: String): Flow<List<MarketCorrelationEntity>>

    @Query("""
        SELECT * FROM market_correlations
        WHERE (symbol1 = :symbol OR symbol2 = :symbol)
        AND timestamp >= :since
        ORDER BY timestamp DESC
    """)
    suspend fun getCorrelationsSince(symbol: String, since: Long): List<MarketCorrelationEntity>

    @Query("""
        SELECT * FROM market_correlations
        WHERE timestamp >= :startTime AND timestamp <= :endTime
        ORDER BY timestamp DESC
    """)
    suspend fun getCorrelationsByDateRange(startTime: Long, endTime: Long): List<MarketCorrelationEntity>

    @Query("SELECT * FROM market_correlations ORDER BY timestamp DESC")
    fun getAllCorrelations(): Flow<List<MarketCorrelationEntity>>

    @Query("SELECT * FROM market_correlations ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentCorrelations(limit: Int): List<MarketCorrelationEntity>

    @Query("DELETE FROM market_correlations WHERE timestamp < :before")
    suspend fun deleteCorrelationsBefore(before: Long)

    @Query("DELETE FROM market_correlations WHERE symbol1 = :symbol OR symbol2 = :symbol")
    suspend fun deleteCorrelationsForSymbol(symbol: String)

    @Query("DELETE FROM market_correlations")
    suspend fun deleteAllCorrelations()

    @Query("SELECT COUNT(*) FROM market_correlations")
    suspend fun getCorrelationCount(): Int

    // Find strong correlations (absolute value > threshold)
    @Query("""
        SELECT * FROM market_correlations
        WHERE ABS(correlationValue) >= :threshold
        AND timestamp >= :since
        ORDER BY ABS(correlationValue) DESC
    """)
    suspend fun getStrongCorrelations(threshold: Double, since: Long): List<MarketCorrelationEntity>
}
