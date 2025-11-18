package com.cryptotrader.data.local.dao

import androidx.room.*
import com.cryptotrader.data.local.entities.DataQuarterCoverageEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for Data Quarter Coverage operations
 *
 * Tracks which quarters of data have been downloaded from cloud storage.
 * Used by CloudDataRepository for download management.
 */
@Dao
interface DataQuarterCoverageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCoverage(coverage: DataQuarterCoverageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(coverages: List<DataQuarterCoverageEntity>)

    /**
     * Get all quarters downloaded for specific asset/timeframe/tier
     */
    @Query("""
        SELECT * FROM data_quarter_coverage
        WHERE asset = :asset
        AND timeframe = :timeframe
        AND dataTier = :dataTier
        ORDER BY quarter ASC
    """)
    suspend fun getCoverage(
        asset: String,
        timeframe: String,
        dataTier: String
    ): List<DataQuarterCoverageEntity>

    /**
     * Get coverage as Flow for reactive updates
     */
    @Query("""
        SELECT * FROM data_quarter_coverage
        WHERE asset = :asset
        AND timeframe = :timeframe
        AND dataTier = :dataTier
        ORDER BY quarter ASC
    """)
    fun getCoverageFlow(
        asset: String,
        timeframe: String,
        dataTier: String
    ): Flow<List<DataQuarterCoverageEntity>>

    /**
     * Check if specific quarter is downloaded
     */
    @Query("""
        SELECT COUNT(*) > 0 FROM data_quarter_coverage
        WHERE asset = :asset
        AND timeframe = :timeframe
        AND dataTier = :dataTier
        AND quarter = :quarter
    """)
    suspend fun isQuarterDownloaded(
        asset: String,
        timeframe: String,
        dataTier: String,
        quarter: String
    ): Boolean

    /**
     * Get all quarters for specific asset/timeframe across all tiers
     */
    @Query("""
        SELECT * FROM data_quarter_coverage
        WHERE asset = :asset
        AND timeframe = :timeframe
        ORDER BY dataTier, quarter ASC
    """)
    suspend fun getCoverageForAssetTimeframe(
        asset: String,
        timeframe: String
    ): List<DataQuarterCoverageEntity>

    /**
     * Get total bar count for asset/timeframe/tier
     */
    @Query("""
        SELECT SUM(barCount) FROM data_quarter_coverage
        WHERE asset = :asset
        AND timeframe = :timeframe
        AND dataTier = :dataTier
    """)
    suspend fun getTotalBarCount(
        asset: String,
        timeframe: String,
        dataTier: String
    ): Int?

    /**
     * Delete coverage for specific asset/timeframe/tier
     */
    @Query("""
        DELETE FROM data_quarter_coverage
        WHERE asset = :asset
        AND timeframe = :timeframe
        AND dataTier = :dataTier
    """)
    suspend fun deleteCoverage(
        asset: String,
        timeframe: String,
        dataTier: String
    )

    /**
     * Delete all quarters for asset/timeframe
     */
    @Query("""
        DELETE FROM data_quarter_coverage
        WHERE asset = :asset
        AND timeframe = :timeframe
    """)
    suspend fun deleteCoverageForAssetTimeframe(
        asset: String,
        timeframe: String
    )

    /**
     * Delete all coverage for an asset
     */
    @Query("DELETE FROM data_quarter_coverage WHERE asset = :asset")
    suspend fun deleteCoverageByAsset(asset: String)

    /**
     * Delete all coverage
     */
    @Query("DELETE FROM data_quarter_coverage")
    suspend fun deleteAll()

    /**
     * Get all unique assets with downloaded quarters
     */
    @Query("SELECT DISTINCT asset FROM data_quarter_coverage ORDER BY asset")
    suspend fun getAllAssets(): List<String>

    /**
     * Get download statistics for UI display
     */
    @Query("""
        SELECT
            COUNT(DISTINCT quarter) as quarterCount,
            SUM(barCount) as totalBars,
            MIN(startTime) as earliestTime,
            MAX(endTime) as latestTime
        FROM data_quarter_coverage
        WHERE asset = :asset
        AND timeframe = :timeframe
        AND dataTier = :dataTier
    """)
    suspend fun getDownloadStats(
        asset: String,
        timeframe: String,
        dataTier: String
    ): DownloadStats?
}

/**
 * Download statistics data class
 */
data class DownloadStats(
    val quarterCount: Int,
    val totalBars: Int,
    val earliestTime: Long,
    val latestTime: Long
)
