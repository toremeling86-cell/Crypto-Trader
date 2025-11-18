package com.cryptotrader.data.repository

import com.cryptotrader.domain.model.ExpertReport
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for Expert Report operations
 *
 * Manages expert trading reports from the /CryptoTrader/ExpertReports/ directory
 * Provides file monitoring, markdown parsing, and meta-analysis tracking
 */
interface CryptoReportRepository {

    // ===== Report Operations =====

    /**
     * Get all expert reports
     */
    fun getAllReports(): Flow<List<ExpertReport>>

    /**
     * Get unanalyzed reports (for badge count)
     */
    fun getUnanalyzedReports(): Flow<List<ExpertReport>>

    /**
     * Get count of unanalyzed reports (for badge display)
     */
    fun getUnanalyzedReportCount(): Flow<Int>

    /**
     * Get report by ID
     */
    suspend fun getReportById(id: Long): ExpertReport?

    /**
     * Get reports by category
     */
    fun getReportsByCategory(category: String): Flow<List<ExpertReport>>

    /**
     * Insert a new expert report
     * @return The ID of the inserted report
     */
    suspend fun insertReport(report: ExpertReport): Long

    /**
     * Update an existing report
     */
    suspend fun updateReport(report: ExpertReport)

    /**
     * Delete a report
     */
    suspend fun deleteReport(reportId: Long)

    /**
     * Mark reports as analyzed and link to meta-analysis
     */
    suspend fun markReportsAsAnalyzed(reportIds: List<Long>, metaAnalysisId: Long)

    /**
     * Get reports linked to a specific meta-analysis
     */
    fun getReportsByMetaAnalysisId(metaAnalysisId: Long): Flow<List<ExpertReport>>

    // ===== File Monitoring Operations =====

    /**
     * Scan the /CryptoTrader/ExpertReports/ directory for new markdown files
     * Parses and imports any new reports found
     * @return Number of new reports imported
     */
    suspend fun scanAndImportNewReports(): Int

    /**
     * Start monitoring the ExpertReports directory for new files
     * Automatically imports new markdown files as they appear
     */
    suspend fun startFileMonitoring()

    /**
     * Stop monitoring the ExpertReports directory
     */
    suspend fun stopFileMonitoring()

    /**
     * Check if file monitoring is currently active
     */
    fun isMonitoringActive(): Boolean

    // ===== Notification Operations =====

    /**
     * Trigger notification for new unanalyzed reports
     * Called when new reports are detected
     */
    suspend fun notifyNewReports(count: Int)
}
