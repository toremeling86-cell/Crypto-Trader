package com.cryptotrader.data.local.dao

import androidx.room.*
import com.cryptotrader.data.local.entities.DataQualityEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for Data Quality operations
 *
 * Tracks validation results and data quality metrics
 */
@Dao
interface DataQualityDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(quality: DataQualityEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(qualities: List<DataQualityEntity>)

    /**
     * Get latest quality report for asset/timeframe
     */
    @Query("""
        SELECT * FROM data_quality
        WHERE asset = :asset
        AND timeframe = :timeframe
        ORDER BY validatedAt DESC
        LIMIT 1
    """)
    suspend fun getLatestQuality(asset: String, timeframe: String): DataQualityEntity?

    /**
     * Get quality as Flow
     */
    @Query("""
        SELECT * FROM data_quality
        WHERE asset = :asset
        AND timeframe = :timeframe
        ORDER BY validatedAt DESC
        LIMIT 1
    """)
    fun getLatestQualityFlow(asset: String, timeframe: String): Flow<DataQualityEntity?>

    /**
     * Get all quality reports for asset
     */
    @Query("""
        SELECT * FROM data_quality
        WHERE asset = :asset
        ORDER BY validatedAt DESC
    """)
    suspend fun getQualityForAsset(asset: String): List<DataQualityEntity>

    /**
     * Get backtest-suitable data (high quality only)
     */
    @Query("""
        SELECT * FROM data_quality
        WHERE isBacktestSuitable = 1
        AND overallQualityScore >= :minScore
        ORDER BY overallQualityScore DESC
    """)
    suspend fun getBacktestSuitableData(minScore: Double = 0.7): List<DataQualityEntity>

    /**
     * Get all quality reports
     */
    @Query("SELECT * FROM data_quality ORDER BY validatedAt DESC")
    suspend fun getAllQuality(): List<DataQualityEntity>

    /**
     * Get quality report by ID
     */
    @Query("SELECT * FROM data_quality WHERE id = :id")
    suspend fun getQualityById(id: Long): DataQualityEntity?

    @Query("DELETE FROM data_quality WHERE asset = :asset AND timeframe = :timeframe")
    suspend fun deleteQuality(asset: String, timeframe: String)

    @Query("DELETE FROM data_quality WHERE validatedAt < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)

    @Query("DELETE FROM data_quality")
    suspend fun deleteAll()
}
