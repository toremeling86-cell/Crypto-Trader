package com.cryptotrader.data.local.dao

import androidx.room.*
import com.cryptotrader.data.local.entities.OHLCBarEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for OHLC Bar operations
 *
 * Provides efficient queries for historical candle data
 */
@Dao
interface OHLCBarDao {

    /**
     * Insert single OHLC bar (replace if exists)
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(bar: OHLCBarEntity)

    /**
     * Insert multiple OHLC bars in batch (for efficient imports)
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(bars: List<OHLCBarEntity>)

    /**
     * Get OHLC bars for asset and timeframe within date range
     */
    @Query("""
        SELECT * FROM ohlc_bars
        WHERE asset = :asset
        AND timeframe = :timeframe
        AND timestamp BETWEEN :startTimestamp AND :endTimestamp
        ORDER BY timestamp ASC
    """)
    suspend fun getBarsInRange(
        asset: String,
        timeframe: String,
        startTimestamp: Long,
        endTimestamp: Long
    ): List<OHLCBarEntity>

    /**
     * Get OHLC bars as Flow (reactive updates)
     */
    @Query("""
        SELECT * FROM ohlc_bars
        WHERE asset = :asset
        AND timeframe = :timeframe
        AND timestamp BETWEEN :startTimestamp AND :endTimestamp
        ORDER BY timestamp ASC
    """)
    fun getBarsInRangeFlow(
        asset: String,
        timeframe: String,
        startTimestamp: Long,
        endTimestamp: Long
    ): Flow<List<OHLCBarEntity>>

    /**
     * Get latest OHLC bar for asset and timeframe
     */
    @Query("""
        SELECT * FROM ohlc_bars
        WHERE asset = :asset
        AND timeframe = :timeframe
        ORDER BY timestamp DESC
        LIMIT 1
    """)
    suspend fun getLatestBar(asset: String, timeframe: String): OHLCBarEntity?

    /**
     * Get count of bars for asset and timeframe
     */
    @Query("""
        SELECT COUNT(*) FROM ohlc_bars
        WHERE asset = :asset
        AND timeframe = :timeframe
    """)
    suspend fun getBarCount(asset: String, timeframe: String): Long

    /**
     * Get total count of all bars (for database health check)
     */
    @Query("SELECT COUNT(*) FROM ohlc_bars")
    suspend fun getBarCount(): Long

    /**
     * Get earliest and latest timestamps
     */
    @Query("""
        SELECT MIN(timestamp) as earliest, MAX(timestamp) as latest
        FROM ohlc_bars
        WHERE asset = :asset
        AND timeframe = :timeframe
    """)
    suspend fun getTimestampRange(asset: String, timeframe: String): TimestampRange?

    /**
     * Delete all bars for asset and timeframe
     */
    @Query("DELETE FROM ohlc_bars WHERE asset = :asset AND timeframe = :timeframe")
    suspend fun deleteAllBars(asset: String, timeframe: String)

    /**
     * Delete bars older than timestamp
     */
    @Query("DELETE FROM ohlc_bars WHERE timestamp < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)

    /**
     * Get all unique assets in database
     */
    @Query("SELECT DISTINCT asset FROM ohlc_bars ORDER BY asset")
    suspend fun getAllAssets(): List<String>

    /**
     * Get all unique timeframes for an asset
     */
    @Query("SELECT DISTINCT timeframe FROM ohlc_bars WHERE asset = :asset ORDER BY timeframe")
    suspend fun getTimeframesForAsset(asset: String): List<String>

    /**
     * Get all distinct data tiers for an asset/timeframe combination
     */
    @Query("""
        SELECT DISTINCT dataTier FROM ohlc_bars
        WHERE asset = :asset
        AND timeframe = :timeframe
        ORDER BY dataTier
    """)
    suspend fun getDistinctDataTiers(asset: String, timeframe: String): List<String>
}

/**
 * Data class for timestamp range query result
 */
data class TimestampRange(
    val earliest: Long?,
    val latest: Long?
)
