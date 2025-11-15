package com.cryptotrader.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.cryptotrader.data.local.entities.AdvisorNotificationEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for advisor notification operations
 */
@Dao
interface AdvisorNotificationDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: AdvisorNotificationEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotifications(notifications: List<AdvisorNotificationEntity>): List<Long>

    @Update
    suspend fun updateNotification(notification: AdvisorNotificationEntity)

    @Query("SELECT * FROM advisor_notifications ORDER BY timestamp DESC")
    fun getAllNotifications(): Flow<List<AdvisorNotificationEntity>>

    @Query("SELECT * FROM advisor_notifications WHERE id = :id")
    suspend fun getNotificationById(id: Long): AdvisorNotificationEntity?

    @Query("SELECT * FROM advisor_notifications WHERE opportunityId = :opportunityId ORDER BY timestamp DESC")
    fun getNotificationsByOpportunityId(opportunityId: Long): Flow<List<AdvisorNotificationEntity>>

    @Query("SELECT * FROM advisor_notifications WHERE isRead = 0 ORDER BY timestamp DESC")
    fun getUnreadNotifications(): Flow<List<AdvisorNotificationEntity>>

    @Query("SELECT * FROM advisor_notifications WHERE isDismissed = 0 ORDER BY timestamp DESC")
    fun getActiveNotifications(): Flow<List<AdvisorNotificationEntity>>

    @Query("SELECT * FROM advisor_notifications WHERE type = :type ORDER BY timestamp DESC")
    fun getNotificationsByType(type: String): Flow<List<AdvisorNotificationEntity>>

    @Query("SELECT * FROM advisor_notifications WHERE priority = :priority AND isDismissed = 0 ORDER BY timestamp DESC")
    fun getNotificationsByPriority(priority: String): Flow<List<AdvisorNotificationEntity>>

    @Query("SELECT COUNT(*) FROM advisor_notifications WHERE isRead = 0")
    fun getUnreadCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM advisor_notifications WHERE timestamp >= :since")
    suspend fun getNotificationCountSince(since: Long): Int

    @Query("SELECT MAX(timestamp) FROM advisor_notifications WHERE type = :type")
    suspend fun getLastNotificationTimeByType(type: String): Long?

    @Query("SELECT MAX(timestamp) FROM advisor_notifications")
    suspend fun getLastNotificationTime(): Long?

    @Query("UPDATE advisor_notifications SET isRead = 1, readAt = :readAt WHERE id = :id")
    suspend fun markAsRead(id: Long, readAt: Long)

    @Query("UPDATE advisor_notifications SET isRead = 1, readAt = :readAt WHERE id IN (:ids)")
    suspend fun markMultipleAsRead(ids: List<Long>, readAt: Long)

    @Query("UPDATE advisor_notifications SET isRead = 1, readAt = :readAt")
    suspend fun markAllAsRead(readAt: Long)

    @Query("UPDATE advisor_notifications SET isDismissed = 1, dismissedAt = :dismissedAt WHERE id = :id")
    suspend fun markAsDismissed(id: Long, dismissedAt: Long)

    @Query("DELETE FROM advisor_notifications WHERE id = :id")
    suspend fun deleteNotification(id: Long)

    @Query("DELETE FROM advisor_notifications WHERE timestamp < :before")
    suspend fun deleteNotificationsBefore(before: Long): Int

    @Query("DELETE FROM advisor_notifications WHERE isRead = 1 AND isDismissed = 1 AND timestamp < :before")
    suspend fun deleteReadAndDismissedBefore(before: Long): Int

    @Query("DELETE FROM advisor_notifications")
    suspend fun deleteAllNotifications()
}
