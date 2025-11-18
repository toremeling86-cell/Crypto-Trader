package com.cryptotrader.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.cryptotrader.data.local.entities.ExpertReportEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for expert report operations
 */
@Dao
interface ExpertReportDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReport(report: ExpertReportEntity): Long

    @Update
    suspend fun updateReport(report: ExpertReportEntity)

    @Delete
    suspend fun deleteReport(report: ExpertReportEntity)

    @Query("SELECT * FROM expert_reports ORDER BY uploadDate DESC")
    fun getAllReports(): Flow<List<ExpertReportEntity>>

    @Query("SELECT * FROM expert_reports WHERE id = :id")
    suspend fun getReportById(id: Long): ExpertReportEntity?

    @Query("SELECT * FROM expert_reports WHERE id = :id")
    fun getReportByIdFlow(id: Long): Flow<ExpertReportEntity?>

    @Query("SELECT * FROM expert_reports WHERE category = :category ORDER BY uploadDate DESC")
    fun getReportsByCategory(category: String): Flow<List<ExpertReportEntity>>

    @Query("SELECT * FROM expert_reports WHERE isSentToClaude = :isSent ORDER BY uploadDate DESC")
    fun getReportsBySentStatus(isSent: Boolean): Flow<List<ExpertReportEntity>>

    @Query("SELECT * FROM expert_reports WHERE isSentToClaude = 0 ORDER BY uploadDate DESC")
    fun getUnsentReports(): Flow<List<ExpertReportEntity>>

    @Query("""
        SELECT * FROM expert_reports
        WHERE uploadDate >= :startTime AND uploadDate <= :endTime
        ORDER BY uploadDate DESC
    """)
    suspend fun getReportsByDateRange(startTime: Long, endTime: Long): List<ExpertReportEntity>

    @Query("SELECT * FROM expert_reports WHERE title LIKE '%' || :searchQuery || '%' OR content LIKE '%' || :searchQuery || '%' ORDER BY uploadDate DESC")
    fun searchReports(searchQuery: String): Flow<List<ExpertReportEntity>>

    @Query("UPDATE expert_reports SET isSentToClaude = 1, claudeAnalysisId = :analysisId WHERE id = :reportId")
    suspend fun markReportAsSentToClaude(reportId: Long, analysisId: Long)

    @Query("DELETE FROM expert_reports WHERE id = :id")
    suspend fun deleteReportById(id: Long)

    @Query("DELETE FROM expert_reports WHERE uploadDate < :before")
    suspend fun deleteReportsBefore(before: Long)

    @Query("DELETE FROM expert_reports")
    suspend fun deleteAllReports()

    // Analytics queries
    @Query("SELECT COUNT(*) FROM expert_reports WHERE category = :category")
    suspend fun getReportCountByCategory(category: String): Int

    @Query("SELECT COUNT(*) FROM expert_reports WHERE isSentToClaude = 1")
    suspend fun getSentReportsCount(): Int

    @Query("SELECT DISTINCT category FROM expert_reports ORDER BY category ASC")
    fun getAllCategories(): Flow<List<String>>

    // ========== Meta-Analysis Queries (Phase 3) ==========

    /**
     * Get all unanalyzed reports (not included in any meta-analysis yet)
     */
    @Query("SELECT * FROM expert_reports WHERE analyzed = 0 ORDER BY uploadDate DESC")
    fun getUnanalyzedReports(): Flow<List<ExpertReportEntity>>

    /**
     * Get count of unanalyzed reports (for badge display)
     */
    @Query("SELECT COUNT(*) FROM expert_reports WHERE analyzed = 0")
    fun getUnanalyzedReportsCount(): Flow<Int>

    /**
     * Get all reports included in a specific meta-analysis
     */
    @Query("SELECT * FROM expert_reports WHERE metaAnalysisId = :metaAnalysisId ORDER BY uploadDate DESC")
    fun getReportsByMetaAnalysisId(metaAnalysisId: Long): Flow<List<ExpertReportEntity>>

    /**
     * Get reports by analysis status
     */
    @Query("SELECT * FROM expert_reports WHERE analyzed = :analyzed ORDER BY uploadDate DESC")
    fun getReportsByAnalysisStatus(analyzed: Boolean): Flow<List<ExpertReportEntity>>

    /**
     * Mark report as analyzed and link to meta-analysis
     */
    @Query("UPDATE expert_reports SET analyzed = 1, metaAnalysisId = :metaAnalysisId WHERE id = :reportId")
    suspend fun markReportAsAnalyzed(reportId: Long, metaAnalysisId: Long)

    /**
     * Mark multiple reports as analyzed (batch operation)
     */
    @Query("UPDATE expert_reports SET analyzed = 1, metaAnalysisId = :metaAnalysisId WHERE id IN (:reportIds)")
    suspend fun markReportsAsAnalyzed(reportIds: List<Long>, metaAnalysisId: Long)

    /**
     * Reset analysis status (for re-analysis)
     */
    @Query("UPDATE expert_reports SET analyzed = 0, metaAnalysisId = NULL WHERE id = :reportId")
    suspend fun resetAnalysisStatus(reportId: Long)

    /**
     * Get reports by filename (for file-based uploads)
     */
    @Query("SELECT * FROM expert_reports WHERE filename = :filename")
    suspend fun getReportByFilename(filename: String): ExpertReportEntity?

    /**
     * Get reports by file path
     */
    @Query("SELECT * FROM expert_reports WHERE filePath = :filePath")
    suspend fun getReportByFilePath(filePath: String): ExpertReportEntity?
}
