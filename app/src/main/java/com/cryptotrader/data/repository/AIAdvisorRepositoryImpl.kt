package com.cryptotrader.data.repository

import com.cryptotrader.data.local.dao.AdvisorAnalysisDao
import com.cryptotrader.data.local.dao.AdvisorNotificationDao
import com.cryptotrader.data.local.dao.TradingOpportunityDao
import com.cryptotrader.data.local.entities.AdvisorAnalysisEntity
import com.cryptotrader.data.local.entities.AdvisorNotificationEntity
import com.cryptotrader.data.local.entities.TradingOpportunityEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of AIAdvisorRepository
 *
 * Manages persistence for AI Trading Advisor analysis, opportunities, and notifications
 * Provides robust error handling, logging, and proper coroutine dispatching for database operations
 */
@Singleton
class AIAdvisorRepositoryImpl @Inject constructor(
    private val analysisDao: AdvisorAnalysisDao,
    private val opportunityDao: TradingOpportunityDao,
    private val notificationDao: AdvisorNotificationDao,
    @com.cryptotrader.di.IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : AIAdvisorRepository {

    // ==================== Analysis Operations ====================

    override suspend fun insertAnalysis(analysis: AdvisorAnalysisEntity): Long = withContext(ioDispatcher) {
        try {
            Timber.d("Inserting AI analysis: sentiment=${analysis.overallSentiment}, confidence=${analysis.confidenceLevel}")
            val id = analysisDao.insertAnalysis(analysis)
            Timber.d("Successfully inserted analysis with ID: $id")
            id
        } catch (e: Exception) {
            Timber.e(e, "Error inserting analysis")
            throw e
        }
    }

    override suspend fun getLatestAnalysis(): AdvisorAnalysisEntity? = withContext(ioDispatcher) {
        try {
            analysisDao.getLatestAnalysis()
        } catch (e: Exception) {
            Timber.e(e, "Error getting latest analysis")
            null
        }
    }

    override fun getLatestAnalysisFlow(): Flow<AdvisorAnalysisEntity?> {
        return analysisDao.getLatestAnalysisFlow()
            .catch { e ->
                Timber.e(e, "Error in latest analysis flow")
                emit(null)
            }
            .flowOn(ioDispatcher)
    }

    override fun getAllAnalyses(): Flow<List<AdvisorAnalysisEntity>> {
        return analysisDao.getAllAnalyses()
            .catch { e ->
                Timber.e(e, "Error in all analyses flow")
                emit(emptyList())
            }
            .flowOn(ioDispatcher)
    }

    override fun getRecentAnalyses(limit: Int): Flow<List<AdvisorAnalysisEntity>> {
        return analysisDao.getRecentAnalyses(limit)
            .catch { e ->
                Timber.e(e, "Error getting recent analyses (limit=$limit)")
                emit(emptyList())
            }
            .flowOn(ioDispatcher)
    }

    override suspend fun getAnalysisById(id: Long): AdvisorAnalysisEntity? = withContext(ioDispatcher) {
        try {
            analysisDao.getAnalysisById(id)
        } catch (e: Exception) {
            Timber.e(e, "Error getting analysis by ID: $id")
            null
        }
    }

    override suspend fun getAnalysesByDateRange(startTime: Long, endTime: Long): List<AdvisorAnalysisEntity> =
        withContext(ioDispatcher) {
            try {
                analysisDao.getAnalysesByDateRange(startTime, endTime)
            } catch (e: Exception) {
                Timber.e(e, "Error getting analyses by date range: $startTime to $endTime")
                emptyList()
            }
        }

    override suspend fun deleteAnalysesOlderThan(before: Long): Int = withContext(ioDispatcher) {
        try {
            val count = analysisDao.deleteAnalysesBefore(before)
            Timber.d("Deleted $count analyses older than $before")
            count
        } catch (e: Exception) {
            Timber.e(e, "Error deleting old analyses")
            0
        }
    }

    override suspend fun getCurrentSentiment(): String? = withContext(ioDispatcher) {
        try {
            analysisDao.getCurrentSentiment()
        } catch (e: Exception) {
            Timber.e(e, "Error getting current sentiment")
            null
        }
    }

    override suspend fun getAverageConfidence(since: Long): Double? = withContext(ioDispatcher) {
        try {
            analysisDao.getAverageConfidence(since)
        } catch (e: Exception) {
            Timber.e(e, "Error getting average confidence")
            null
        }
    }

    // ==================== Opportunity Operations ====================

    override suspend fun insertOpportunity(opportunity: TradingOpportunityEntity): Long = withContext(ioDispatcher) {
        try {
            Timber.d("Inserting trading opportunity: asset=${opportunity.asset}, direction=${opportunity.direction}, priority=${opportunity.priority}")
            val id = opportunityDao.insertOpportunity(opportunity)
            Timber.d("Successfully inserted opportunity with ID: $id")
            id
        } catch (e: Exception) {
            Timber.e(e, "Error inserting opportunity")
            throw e
        }
    }

    override suspend fun insertOpportunities(opportunities: List<TradingOpportunityEntity>): List<Long> =
        withContext(ioDispatcher) {
            try {
                Timber.d("Inserting ${opportunities.size} trading opportunities")
                val ids = opportunityDao.insertOpportunities(opportunities)
                Timber.d("Successfully inserted ${ids.size} opportunities")
                ids
            } catch (e: Exception) {
                Timber.e(e, "Error inserting multiple opportunities")
                throw e
            }
        }

    override fun getActiveOpportunities(): Flow<List<TradingOpportunityEntity>> {
        return opportunityDao.getActiveOpportunities()
            .catch { e ->
                Timber.e(e, "Error getting active opportunities")
                emit(emptyList())
            }
            .flowOn(ioDispatcher)
    }

    override fun getActiveOpportunitiesForAsset(asset: String): Flow<List<TradingOpportunityEntity>> {
        return opportunityDao.getActiveOpportunitiesForAsset(asset)
            .catch { e ->
                Timber.e(e, "Error getting active opportunities for asset: $asset")
                emit(emptyList())
            }
            .flowOn(ioDispatcher)
    }

    override fun getOpportunitiesByAnalysisId(analysisId: Long): Flow<List<TradingOpportunityEntity>> {
        return opportunityDao.getOpportunitiesByAnalysisId(analysisId)
            .catch { e ->
                Timber.e(e, "Error getting opportunities for analysis ID: $analysisId")
                emit(emptyList())
            }
            .flowOn(ioDispatcher)
    }

    override suspend fun getOpportunityById(id: Long): TradingOpportunityEntity? = withContext(ioDispatcher) {
        try {
            opportunityDao.getOpportunityById(id)
        } catch (e: Exception) {
            Timber.e(e, "Error getting opportunity by ID: $id")
            null
        }
    }

    override fun getOpportunitiesByPriority(priority: String): Flow<List<TradingOpportunityEntity>> {
        return opportunityDao.getOpportunitiesByPriority(priority)
            .catch { e ->
                Timber.e(e, "Error getting opportunities by priority: $priority")
                emit(emptyList())
            }
            .flowOn(ioDispatcher)
    }

    override suspend fun getOpportunitiesCountToday(since: Long): Int = withContext(ioDispatcher) {
        try {
            opportunityDao.getOpportunitiesCountToday(since)
        } catch (e: Exception) {
            Timber.e(e, "Error getting opportunities count today")
            0
        }
    }

    override suspend fun getActiveOpportunitiesCount(since: Long): Int = withContext(ioDispatcher) {
        try {
            opportunityDao.getActiveOpportunitiesCountSince(since)
        } catch (e: Exception) {
            Timber.e(e, "Error getting active opportunities count")
            0
        }
    }

    override suspend fun updateOpportunityStatus(id: Long, status: String) = withContext(ioDispatcher) {
        try {
            Timber.d("Updating opportunity $id status to: $status")
            opportunityDao.updateOpportunityStatus(id, status)
        } catch (e: Exception) {
            Timber.e(e, "Error updating opportunity status")
            throw e
        }
    }

    override suspend fun updateOpportunityNotificationSent(id: Long, sent: Boolean) = withContext(ioDispatcher) {
        try {
            Timber.d("Updating opportunity $id notification sent: $sent")
            opportunityDao.updateNotificationSent(id, sent)
        } catch (e: Exception) {
            Timber.e(e, "Error updating opportunity notification status")
            throw e
        }
    }

    override suspend fun expireOpportunities(currentTime: Long): Int = withContext(ioDispatcher) {
        try {
            val count = opportunityDao.expireOpportunities(currentTime)
            Timber.d("Expired $count opportunities at time: $currentTime")
            count
        } catch (e: Exception) {
            Timber.e(e, "Error expiring opportunities")
            0
        }
    }

    override suspend fun deleteOpportunitiesOlderThan(before: Long): Int = withContext(ioDispatcher) {
        try {
            val count = opportunityDao.deleteOpportunitiesBefore(before)
            Timber.d("Deleted $count opportunities older than $before")
            count
        } catch (e: Exception) {
            Timber.e(e, "Error deleting old opportunities")
            0
        }
    }

    // ==================== Notification Operations ====================

    override suspend fun insertNotification(notification: AdvisorNotificationEntity): Long = withContext(ioDispatcher) {
        try {
            Timber.d("Inserting notification: type=${notification.type}, priority=${notification.priority}")
            val id = notificationDao.insertNotification(notification)
            Timber.d("Successfully inserted notification with ID: $id")
            id
        } catch (e: Exception) {
            Timber.e(e, "Error inserting notification")
            throw e
        }
    }

    override fun getAllNotifications(): Flow<List<AdvisorNotificationEntity>> {
        return notificationDao.getAllNotifications()
            .catch { e ->
                Timber.e(e, "Error getting all notifications")
                emit(emptyList())
            }
            .flowOn(ioDispatcher)
    }

    override fun getUnreadNotifications(): Flow<List<AdvisorNotificationEntity>> {
        return notificationDao.getUnreadNotifications()
            .catch { e ->
                Timber.e(e, "Error getting unread notifications")
                emit(emptyList())
            }
            .flowOn(ioDispatcher)
    }

    override fun getActiveNotifications(): Flow<List<AdvisorNotificationEntity>> {
        return notificationDao.getActiveNotifications()
            .catch { e ->
                Timber.e(e, "Error getting active notifications")
                emit(emptyList())
            }
            .flowOn(ioDispatcher)
    }

    override fun getNotificationsByType(type: String): Flow<List<AdvisorNotificationEntity>> {
        return notificationDao.getNotificationsByType(type)
            .catch { e ->
                Timber.e(e, "Error getting notifications by type: $type")
                emit(emptyList())
            }
            .flowOn(ioDispatcher)
    }

    override fun getUnreadNotificationCount(): Flow<Int> {
        return notificationDao.getUnreadCount()
            .catch { e ->
                Timber.e(e, "Error getting unread notification count")
                emit(0)
            }
            .flowOn(ioDispatcher)
    }

    override suspend fun getNotificationCountToday(since: Long): Int = withContext(ioDispatcher) {
        try {
            notificationDao.getNotificationCountSince(since)
        } catch (e: Exception) {
            Timber.e(e, "Error getting notification count today")
            0
        }
    }

    override suspend fun getLastNotificationTime(): Long? = withContext(ioDispatcher) {
        try {
            notificationDao.getLastNotificationTime()
        } catch (e: Exception) {
            Timber.e(e, "Error getting last notification time")
            null
        }
    }

    override suspend fun getLastNotificationTimeByType(type: String): Long? = withContext(ioDispatcher) {
        try {
            notificationDao.getLastNotificationTimeByType(type)
        } catch (e: Exception) {
            Timber.e(e, "Error getting last notification time by type: $type")
            null
        }
    }

    override suspend fun markNotificationAsRead(id: Long) = withContext(ioDispatcher) {
        try {
            Timber.d("Marking notification $id as read")
            notificationDao.markAsRead(id, System.currentTimeMillis())
        } catch (e: Exception) {
            Timber.e(e, "Error marking notification as read")
            throw e
        }
    }

    override suspend fun markMultipleNotificationsAsRead(ids: List<Long>) = withContext(ioDispatcher) {
        try {
            Timber.d("Marking ${ids.size} notifications as read")
            notificationDao.markMultipleAsRead(ids, System.currentTimeMillis())
        } catch (e: Exception) {
            Timber.e(e, "Error marking multiple notifications as read")
            throw e
        }
    }

    override suspend fun markAllNotificationsAsRead() = withContext(ioDispatcher) {
        try {
            Timber.d("Marking all notifications as read")
            notificationDao.markAllAsRead(System.currentTimeMillis())
        } catch (e: Exception) {
            Timber.e(e, "Error marking all notifications as read")
            throw e
        }
    }

    override suspend fun markNotificationAsDismissed(id: Long) = withContext(ioDispatcher) {
        try {
            Timber.d("Dismissing notification $id")
            notificationDao.markAsDismissed(id, System.currentTimeMillis())
        } catch (e: Exception) {
            Timber.e(e, "Error dismissing notification")
            throw e
        }
    }

    override suspend fun deleteNotificationsOlderThan(before: Long): Int = withContext(ioDispatcher) {
        try {
            val count = notificationDao.deleteNotificationsBefore(before)
            Timber.d("Deleted $count notifications older than $before")
            count
        } catch (e: Exception) {
            Timber.e(e, "Error deleting old notifications")
            0
        }
    }

    override suspend fun cleanupOldNotifications(before: Long): Int = withContext(ioDispatcher) {
        try {
            val count = notificationDao.deleteReadAndDismissedBefore(before)
            Timber.d("Cleaned up $count old read/dismissed notifications")
            count
        } catch (e: Exception) {
            Timber.e(e, "Error cleaning up old notifications")
            0
        }
    }
}
