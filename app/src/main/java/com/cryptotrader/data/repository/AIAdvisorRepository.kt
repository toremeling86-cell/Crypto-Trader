package com.cryptotrader.data.repository

import com.cryptotrader.data.local.entities.AdvisorAnalysisEntity
import com.cryptotrader.data.local.entities.AdvisorNotificationEntity
import com.cryptotrader.data.local.entities.TradingOpportunityEntity
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for AI Advisor operations
 * Provides access to analysis, opportunities, and notifications
 */
interface AIAdvisorRepository {

    // ===== Analysis Operations =====

    /**
     * Insert a new analysis from the AI advisor
     * @return The ID of the inserted analysis
     */
    suspend fun insertAnalysis(analysis: AdvisorAnalysisEntity): Long

    /**
     * Get the most recent analysis
     */
    suspend fun getLatestAnalysis(): AdvisorAnalysisEntity?

    /**
     * Get the most recent analysis as a Flow for real-time updates
     */
    fun getLatestAnalysisFlow(): Flow<AdvisorAnalysisEntity?>

    /**
     * Get all analyses
     */
    fun getAllAnalyses(): Flow<List<AdvisorAnalysisEntity>>

    /**
     * Get recent analyses with a limit
     */
    fun getRecentAnalyses(limit: Int = 20): Flow<List<AdvisorAnalysisEntity>>

    /**
     * Get analysis by ID
     */
    suspend fun getAnalysisById(id: Long): AdvisorAnalysisEntity?

    /**
     * Get analyses by date range
     */
    suspend fun getAnalysesByDateRange(startTime: Long, endTime: Long): List<AdvisorAnalysisEntity>

    /**
     * Delete analyses older than the specified timestamp
     * @param before Delete all analyses with timestamp < before
     * @return Number of deleted analyses
     */
    suspend fun deleteAnalysesOlderThan(before: Long): Int

    /**
     * Get current market sentiment from the latest analysis
     */
    suspend fun getCurrentSentiment(): String?

    /**
     * Get average confidence level for analyses since a given time
     */
    suspend fun getAverageConfidence(since: Long): Double?

    // ===== Opportunity Operations =====

    /**
     * Insert a new trading opportunity
     * @return The ID of the inserted opportunity
     */
    suspend fun insertOpportunity(opportunity: TradingOpportunityEntity): Long

    /**
     * Insert multiple trading opportunities
     * @return List of IDs for the inserted opportunities
     */
    suspend fun insertOpportunities(opportunities: List<TradingOpportunityEntity>): List<Long>

    /**
     * Get all active trading opportunities, ordered by priority and confidence
     */
    fun getActiveOpportunities(): Flow<List<TradingOpportunityEntity>>

    /**
     * Get active opportunities for a specific asset
     */
    fun getActiveOpportunitiesForAsset(asset: String): Flow<List<TradingOpportunityEntity>>

    /**
     * Get opportunities by analysis ID
     */
    fun getOpportunitiesByAnalysisId(analysisId: Long): Flow<List<TradingOpportunityEntity>>

    /**
     * Get opportunity by ID
     */
    suspend fun getOpportunityById(id: Long): TradingOpportunityEntity?

    /**
     * Get opportunities by priority level
     */
    fun getOpportunitiesByPriority(priority: String): Flow<List<TradingOpportunityEntity>>

    /**
     * Get count of opportunities created today
     * @param since Timestamp for start of "today"
     */
    suspend fun getOpportunitiesCountToday(since: Long): Int

    /**
     * Get count of active opportunities since a given time
     */
    suspend fun getActiveOpportunitiesCount(since: Long): Int

    /**
     * Update opportunity status
     */
    suspend fun updateOpportunityStatus(id: Long, status: String)

    /**
     * Mark opportunity notification as sent
     */
    suspend fun updateOpportunityNotificationSent(id: Long, sent: Boolean)

    /**
     * Expire opportunities that have passed their expiration time
     * @param currentTime Current timestamp
     * @return Number of expired opportunities
     */
    suspend fun expireOpportunities(currentTime: Long): Int

    /**
     * Delete opportunities older than the specified timestamp
     * @param before Delete all opportunities with timestamp < before
     * @return Number of deleted opportunities
     */
    suspend fun deleteOpportunitiesOlderThan(before: Long): Int

    // ===== Notification Operations =====

    /**
     * Insert a new notification
     * @return The ID of the inserted notification
     */
    suspend fun insertNotification(notification: AdvisorNotificationEntity): Long

    /**
     * Get all notifications
     */
    fun getAllNotifications(): Flow<List<AdvisorNotificationEntity>>

    /**
     * Get unread notifications
     */
    fun getUnreadNotifications(): Flow<List<AdvisorNotificationEntity>>

    /**
     * Get active (non-dismissed) notifications
     */
    fun getActiveNotifications(): Flow<List<AdvisorNotificationEntity>>

    /**
     * Get notifications by type
     */
    fun getNotificationsByType(type: String): Flow<List<AdvisorNotificationEntity>>

    /**
     * Get unread notification count
     */
    fun getUnreadNotificationCount(): Flow<Int>

    /**
     * Get count of notifications sent today
     * @param since Timestamp for start of "today"
     */
    suspend fun getNotificationCountToday(since: Long): Int

    /**
     * Get timestamp of the last notification
     */
    suspend fun getLastNotificationTime(): Long?

    /**
     * Get timestamp of the last notification of a specific type
     */
    suspend fun getLastNotificationTimeByType(type: String): Long?

    /**
     * Mark notification as read
     */
    suspend fun markNotificationAsRead(id: Long)

    /**
     * Mark multiple notifications as read
     */
    suspend fun markMultipleNotificationsAsRead(ids: List<Long>)

    /**
     * Mark all notifications as read
     */
    suspend fun markAllNotificationsAsRead()

    /**
     * Mark notification as dismissed
     */
    suspend fun markNotificationAsDismissed(id: Long)

    /**
     * Delete notifications older than the specified timestamp
     * @param before Delete all notifications with timestamp < before
     * @return Number of deleted notifications
     */
    suspend fun deleteNotificationsOlderThan(before: Long): Int

    /**
     * Cleanup old read and dismissed notifications
     * @param before Delete read/dismissed notifications older than this timestamp
     * @return Number of deleted notifications
     */
    suspend fun cleanupOldNotifications(before: Long): Int
}
