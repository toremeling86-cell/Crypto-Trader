package com.cryptotrader.data.local.dao

import androidx.room.*
import com.cryptotrader.data.local.entities.DataCoverageEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for Data Coverage operations
 *
 * Used by AI chat to show data availability
 */
@Dao
interface DataCoverageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(coverage: DataCoverageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(coverages: List<DataCoverageEntity>)

    /**
     * Get coverage for specific asset and timeframe
     */
    @Query("""
        SELECT * FROM data_coverage
        WHERE asset = :asset
        AND timeframe = :timeframe
    """)
    suspend fun getCoverage(asset: String, timeframe: String): DataCoverageEntity?

    /**
     * Get coverage as Flow (for reactive UI)
     */
    @Query("""
        SELECT * FROM data_coverage
        WHERE asset = :asset
        AND timeframe = :timeframe
    """)
    fun getCoverageFlow(asset: String, timeframe: String): Flow<DataCoverageEntity?>

    /**
     * Get all coverage for an asset
     */
    @Query("SELECT * FROM data_coverage WHERE asset = :asset ORDER BY timeframe")
    suspend fun getCoverageForAsset(asset: String): List<DataCoverageEntity>

    /**
     * Get all available assets with coverage
     */
    @Query("SELECT DISTINCT asset FROM data_coverage ORDER BY asset")
    suspend fun getAllAssets(): List<String>

    /**
     * Get all coverage entries (for AI chat metadata)
     */
    @Query("SELECT * FROM data_coverage ORDER BY asset, timeframe")
    suspend fun getAllCoverage(): List<DataCoverageEntity>

    /**
     * Get coverage as Flow (for AI chat)
     */
    @Query("SELECT * FROM data_coverage ORDER BY asset, timeframe")
    fun getAllCoverageFlow(): Flow<List<DataCoverageEntity>>

    /**
     * Get high-quality coverage only (score > threshold)
     */
    @Query("""
        SELECT * FROM data_coverage
        WHERE dataQualityScore >= :minScore
        ORDER BY dataQualityScore DESC, asset
    """)
    suspend fun getHighQualityCoverage(minScore: Double = 0.8): List<DataCoverageEntity>

    @Query("DELETE FROM data_coverage WHERE asset = :asset AND timeframe = :timeframe")
    suspend fun deleteCoverage(asset: String, timeframe: String)

    @Query("DELETE FROM data_coverage")
    suspend fun deleteAll()
}
