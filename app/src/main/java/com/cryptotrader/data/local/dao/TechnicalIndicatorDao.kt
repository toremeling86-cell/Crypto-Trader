package com.cryptotrader.data.local.dao

import androidx.room.*
import com.cryptotrader.data.local.entities.TechnicalIndicatorEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for Technical Indicator operations
 */
@Dao
interface TechnicalIndicatorDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(indicator: TechnicalIndicatorEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(indicators: List<TechnicalIndicatorEntity>)

    /**
     * Get all indicators for asset/timeframe/timestamp
     */
    @Query("""
        SELECT * FROM technical_indicators
        WHERE asset = :asset
        AND timeframe = :timeframe
        AND timestamp = :timestamp
        ORDER BY indicatorType ASC
    """)
    suspend fun getIndicatorsForBar(
        asset: String,
        timeframe: String,
        timestamp: Long
    ): List<TechnicalIndicatorEntity>

    /**
     * Get specific indicator type for date range
     */
    @Query("""
        SELECT * FROM technical_indicators
        WHERE asset = :asset
        AND timeframe = :timeframe
        AND indicatorType = :indicatorType
        AND timestamp BETWEEN :startTimestamp AND :endTimestamp
        ORDER BY timestamp ASC
    """)
    suspend fun getIndicatorInRange(
        asset: String,
        timeframe: String,
        indicatorType: String,
        startTimestamp: Long,
        endTimestamp: Long
    ): List<TechnicalIndicatorEntity>

    /**
     * Get all available indicator types for asset
     */
    @Query("""
        SELECT DISTINCT indicatorType FROM technical_indicators
        WHERE asset = :asset
        AND timeframe = :timeframe
        ORDER BY indicatorType
    """)
    suspend fun getAvailableIndicators(asset: String, timeframe: String): List<String>

    @Query("DELETE FROM technical_indicators WHERE asset = :asset AND timeframe = :timeframe")
    suspend fun deleteAllIndicators(asset: String, timeframe: String)

    @Query("DELETE FROM technical_indicators WHERE timestamp < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)
}
