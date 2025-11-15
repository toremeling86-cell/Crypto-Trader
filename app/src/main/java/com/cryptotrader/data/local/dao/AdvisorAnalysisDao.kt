package com.cryptotrader.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.cryptotrader.data.local.entities.AdvisorAnalysisEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for AI Advisor analysis operations
 */
@Dao
interface AdvisorAnalysisDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnalysis(analysis: AdvisorAnalysisEntity): Long

    @Update
    suspend fun updateAnalysis(analysis: AdvisorAnalysisEntity)

    @Query("SELECT * FROM advisor_analyses ORDER BY timestamp DESC")
    fun getAllAnalyses(): Flow<List<AdvisorAnalysisEntity>>

    @Query("SELECT * FROM advisor_analyses ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestAnalysis(): AdvisorAnalysisEntity?

    @Query("SELECT * FROM advisor_analyses ORDER BY timestamp DESC LIMIT 1")
    fun getLatestAnalysisFlow(): Flow<AdvisorAnalysisEntity?>

    @Query("SELECT * FROM advisor_analyses WHERE id = :id")
    suspend fun getAnalysisById(id: Long): AdvisorAnalysisEntity?

    @Query("SELECT * FROM advisor_analyses WHERE triggerType = :triggerType ORDER BY timestamp DESC")
    fun getAnalysesByTriggerType(triggerType: String): Flow<List<AdvisorAnalysisEntity>>

    @Query("SELECT * FROM advisor_analyses WHERE overallSentiment = :sentiment ORDER BY timestamp DESC")
    fun getAnalysesBySentiment(sentiment: String): Flow<List<AdvisorAnalysisEntity>>

    @Query("""
        SELECT * FROM advisor_analyses
        WHERE timestamp >= :startTime AND timestamp <= :endTime
        ORDER BY timestamp DESC
    """)
    suspend fun getAnalysesByDateRange(startTime: Long, endTime: Long): List<AdvisorAnalysisEntity>

    @Query("SELECT * FROM advisor_analyses ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentAnalyses(limit: Int = 20): Flow<List<AdvisorAnalysisEntity>>

    @Query("DELETE FROM advisor_analyses WHERE id = :id")
    suspend fun deleteAnalysis(id: Long)

    @Query("DELETE FROM advisor_analyses WHERE timestamp < :before")
    suspend fun deleteAnalysesBefore(before: Long): Int

    @Query("DELETE FROM advisor_analyses")
    suspend fun deleteAllAnalyses()

    // Analytics queries
    @Query("SELECT overallSentiment FROM advisor_analyses ORDER BY timestamp DESC LIMIT 1")
    suspend fun getCurrentSentiment(): String?

    @Query("SELECT COUNT(*) FROM advisor_analyses WHERE overallSentiment = :sentiment AND timestamp >= :since")
    suspend fun getSentimentCount(sentiment: String, since: Long): Int

    @Query("SELECT AVG(confidenceLevel) FROM advisor_analyses WHERE timestamp >= :since")
    suspend fun getAverageConfidence(since: Long): Double?

    @Query("SELECT COUNT(*) FROM advisor_analyses WHERE timestamp >= :since")
    suspend fun getAnalysisCountSince(since: Long): Int
}
