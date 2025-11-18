package com.cryptotrader.data.repository

import com.cryptotrader.domain.model.AnalysisStatus
import com.cryptotrader.domain.model.MetaAnalysis
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for Meta-Analysis operations
 *
 * Manages storage and retrieval of Opus 4.1 meta-analysis results
 */
interface MetaAnalysisRepository {

    // ===== Analysis Operations =====

    /**
     * Insert a new meta-analysis result
     * @return The ID of the inserted analysis
     */
    suspend fun insertAnalysis(analysis: MetaAnalysis): Long

    /**
     * Update an existing meta-analysis
     */
    suspend fun updateAnalysis(analysis: MetaAnalysis)

    /**
     * Get analysis by ID
     */
    suspend fun getAnalysisById(id: Long): MetaAnalysis?

    /**
     * Get all meta-analyses
     */
    fun getAllAnalyses(): Flow<List<MetaAnalysis>>

    /**
     * Get recent analyses with limit
     */
    fun getRecentAnalyses(limit: Int = 20): Flow<List<MetaAnalysis>>

    /**
     * Get analyses by status
     */
    fun getAnalysesByStatus(status: AnalysisStatus): Flow<List<MetaAnalysis>>

    /**
     * Get pending analyses (awaiting user approval)
     */
    fun getPendingAnalyses(): Flow<List<MetaAnalysis>>

    /**
     * Get approved analyses
     */
    fun getApprovedAnalyses(): Flow<List<MetaAnalysis>>

    /**
     * Get active analyses (currently trading)
     */
    fun getActiveAnalyses(): Flow<List<MetaAnalysis>>

    // ===== Approval Operations =====

    /**
     * Approve an analysis and optionally link to strategy
     */
    suspend fun approveAnalysis(analysisId: Long, strategyId: Long?)

    /**
     * Reject an analysis with reason
     */
    suspend fun rejectAnalysis(analysisId: Long, reason: String)

    /**
     * Mark analysis as active (strategy is now trading)
     */
    suspend fun markAnalysisAsActive(analysisId: Long, strategyId: Long)

    // ===== Analytics Operations =====

    /**
     * Get average confidence score for completed analyses
     */
    suspend fun getAverageConfidence(): Double?

    /**
     * Get count of analyses by status
     */
    suspend fun getAnalysisCountByStatus(status: AnalysisStatus): Int

    /**
     * Delete analysis by ID
     */
    suspend fun deleteAnalysis(analysisId: Long)

    /**
     * Delete old analyses (before timestamp)
     */
    suspend fun deleteAnalysesOlderThan(before: Long): Int
}
