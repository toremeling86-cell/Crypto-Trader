package com.cryptotrader.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.cryptotrader.data.local.entities.MetaAnalysisEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for Meta-Analysis operations
 *
 * Handles CRUD operations for Opus 4.1 meta-analysis results
 */
@Dao
interface MetaAnalysisDao {

    // ========== Basic CRUD Operations ==========

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnalysis(analysis: MetaAnalysisEntity): Long

    @Update
    suspend fun updateAnalysis(analysis: MetaAnalysisEntity)

    @Delete
    suspend fun deleteAnalysis(analysis: MetaAnalysisEntity)

    @Query("SELECT * FROM meta_analyses ORDER BY timestamp DESC")
    fun getAllAnalyses(): Flow<List<MetaAnalysisEntity>>

    @Query("SELECT * FROM meta_analyses WHERE id = :id")
    suspend fun getAnalysisById(id: Long): MetaAnalysisEntity?

    @Query("SELECT * FROM meta_analyses WHERE id = :id")
    fun getAnalysisByIdFlow(id: Long): Flow<MetaAnalysisEntity?>

    // ========== Status-based Queries ==========

    /**
     * Get all pending analyses (in progress)
     */
    @Query("SELECT * FROM meta_analyses WHERE status = 'PENDING' ORDER BY timestamp DESC")
    fun getPendingAnalyses(): Flow<List<MetaAnalysisEntity>>

    /**
     * Get all completed analyses awaiting user review
     */
    @Query("SELECT * FROM meta_analyses WHERE status = 'COMPLETED' ORDER BY timestamp DESC")
    fun getCompletedAnalyses(): Flow<List<MetaAnalysisEntity>>

    /**
     * Get all approved analyses
     */
    @Query("SELECT * FROM meta_analyses WHERE status = 'APPROVED' ORDER BY timestamp DESC")
    fun getApprovedAnalyses(): Flow<List<MetaAnalysisEntity>>

    /**
     * Get all active analyses (strategies currently trading)
     */
    @Query("SELECT * FROM meta_analyses WHERE status = 'ACTIVE' ORDER BY timestamp DESC")
    fun getActiveAnalyses(): Flow<List<MetaAnalysisEntity>>

    /**
     * Get analyses by status
     */
    @Query("SELECT * FROM meta_analyses WHERE status = :status ORDER BY timestamp DESC")
    fun getAnalysesByStatus(status: String): Flow<List<MetaAnalysisEntity>>

    // ========== Strategy Linking ==========

    /**
     * Get analysis linked to a specific strategy
     */
    @Query("SELECT * FROM meta_analyses WHERE strategyId = :strategyId LIMIT 1")
    suspend fun getAnalysisByStrategyId(strategyId: Long): MetaAnalysisEntity?

    /**
     * Link analysis to created strategy
     */
    @Query("UPDATE meta_analyses SET strategyId = :strategyId WHERE id = :analysisId")
    suspend fun linkToStrategy(analysisId: Long, strategyId: Long)

    // ========== Status Updates ==========

    /**
     * Mark analysis as completed (ready for user review)
     */
    @Query("UPDATE meta_analyses SET status = 'COMPLETED' WHERE id = :analysisId")
    suspend fun markAsCompleted(analysisId: Long)

    /**
     * Mark analysis as approved by user
     */
    @Query("UPDATE meta_analyses SET status = 'APPROVED', approvedAt = :timestamp WHERE id = :analysisId")
    suspend fun markAsApproved(analysisId: Long, timestamp: Long = System.currentTimeMillis())

    /**
     * Mark analysis as rejected by user
     */
    @Query("UPDATE meta_analyses SET status = 'REJECTED', rejectedAt = :timestamp, rejectionReason = :reason WHERE id = :analysisId")
    suspend fun markAsRejected(analysisId: Long, reason: String?, timestamp: Long = System.currentTimeMillis())

    /**
     * Mark analysis as active (strategy is trading)
     */
    @Query("UPDATE meta_analyses SET status = 'ACTIVE' WHERE id = :analysisId")
    suspend fun markAsActive(analysisId: Long)

    // ========== Time-based Queries ==========

    /**
     * Get analyses within date range
     */
    @Query("""
        SELECT * FROM meta_analyses
        WHERE timestamp >= :startTime AND timestamp <= :endTime
        ORDER BY timestamp DESC
    """)
    suspend fun getAnalysesByDateRange(startTime: Long, endTime: Long): List<MetaAnalysisEntity>

    /**
     * Get recent analyses (last N days)
     */
    @Query("""
        SELECT * FROM meta_analyses
        WHERE timestamp >= :sinceTimestamp
        ORDER BY timestamp DESC
    """)
    fun getRecentAnalyses(sinceTimestamp: Long): Flow<List<MetaAnalysisEntity>>

    // ========== Analytics Queries ==========

    /**
     * Count analyses by status
     */
    @Query("SELECT COUNT(*) FROM meta_analyses WHERE status = :status")
    suspend fun getCountByStatus(status: String): Int

    /**
     * Get total number of analyses
     */
    @Query("SELECT COUNT(*) FROM meta_analyses")
    suspend fun getTotalAnalysesCount(): Int

    /**
     * Get analyses by confidence threshold
     */
    @Query("SELECT * FROM meta_analyses WHERE confidence >= :minConfidence ORDER BY confidence DESC")
    fun getHighConfidenceAnalyses(minConfidence: Double): Flow<List<MetaAnalysisEntity>>

    /**
     * Get analyses by risk level
     */
    @Query("SELECT * FROM meta_analyses WHERE riskLevel = :riskLevel ORDER BY timestamp DESC")
    fun getAnalysesByRiskLevel(riskLevel: String): Flow<List<MetaAnalysisEntity>>

    /**
     * Get average confidence score for all analyses
     */
    @Query("SELECT AVG(confidence) FROM meta_analyses WHERE status IN ('APPROVED', 'ACTIVE')")
    suspend fun getAverageConfidence(): Double?

    /**
     * Get most recent completed analysis (for notification)
     */
    @Query("SELECT * FROM meta_analyses WHERE status = 'COMPLETED' ORDER BY timestamp DESC LIMIT 1")
    suspend fun getMostRecentCompletedAnalysis(): MetaAnalysisEntity?

    // ========== Cleanup Operations ==========

    /**
     * Delete old rejected analyses
     */
    @Query("DELETE FROM meta_analyses WHERE status = 'REJECTED' AND rejectedAt < :before")
    suspend fun deleteOldRejectedAnalyses(before: Long)

    /**
     * Delete analysis by ID
     */
    @Query("DELETE FROM meta_analyses WHERE id = :id")
    suspend fun deleteAnalysisById(id: Long)

    /**
     * Delete all analyses (use with caution)
     */
    @Query("DELETE FROM meta_analyses")
    suspend fun deleteAllAnalyses()

    // ========== Report Count Queries ==========

    /**
     * Get report count for an analysis
     */
    @Query("SELECT reportCount FROM meta_analyses WHERE id = :analysisId")
    suspend fun getReportCount(analysisId: Long): Int?

    /**
     * Get analyses that used specific number of reports
     */
    @Query("SELECT * FROM meta_analyses WHERE reportCount = :count ORDER BY timestamp DESC")
    fun getAnalysesByReportCount(count: Int): Flow<List<MetaAnalysisEntity>>
}
