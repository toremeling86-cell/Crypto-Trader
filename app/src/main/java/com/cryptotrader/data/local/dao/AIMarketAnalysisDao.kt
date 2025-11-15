package com.cryptotrader.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.cryptotrader.data.local.entities.AIMarketAnalysisEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for AI market analysis operations
 */
@Dao
interface AIMarketAnalysisDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnalysis(analysis: AIMarketAnalysisEntity): Long

    @Update
    suspend fun updateAnalysis(analysis: AIMarketAnalysisEntity)

    @Query("SELECT * FROM ai_market_analyses ORDER BY timestamp DESC")
    fun getAllAnalyses(): Flow<List<AIMarketAnalysisEntity>>

    @Query("SELECT * FROM ai_market_analyses ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestAnalysis(): AIMarketAnalysisEntity?

    @Query("SELECT * FROM ai_market_analyses ORDER BY timestamp DESC LIMIT 1")
    fun getLatestAnalysisFlow(): Flow<AIMarketAnalysisEntity?>

    @Query("SELECT * FROM ai_market_analyses WHERE id = :id")
    suspend fun getAnalysisById(id: Long): AIMarketAnalysisEntity?

    @Query("SELECT * FROM ai_market_analyses WHERE triggerType = :triggerType ORDER BY timestamp DESC")
    fun getAnalysesByTriggerType(triggerType: String): Flow<List<AIMarketAnalysisEntity>>

    @Query("SELECT * FROM ai_market_analyses WHERE sentiment = :sentiment ORDER BY timestamp DESC")
    fun getAnalysesBySentiment(sentiment: String): Flow<List<AIMarketAnalysisEntity>>

    @Query("""
        SELECT * FROM ai_market_analyses
        WHERE timestamp >= :startTime AND timestamp <= :endTime
        ORDER BY timestamp DESC
    """)
    suspend fun getAnalysesByDateRange(startTime: Long, endTime: Long): List<AIMarketAnalysisEntity>

    @Query("SELECT * FROM ai_market_analyses ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentAnalyses(limit: Int = 20): Flow<List<AIMarketAnalysisEntity>>

    @Query("DELETE FROM ai_market_analyses WHERE id = :id")
    suspend fun deleteAnalysis(id: Long)

    @Query("DELETE FROM ai_market_analyses WHERE timestamp < :before")
    suspend fun deleteAnalysesBefore(before: Long)

    @Query("DELETE FROM ai_market_analyses")
    suspend fun deleteAllAnalyses()

    // Analytics queries
    @Query("SELECT sentiment FROM ai_market_analyses ORDER BY timestamp DESC LIMIT 1")
    suspend fun getCurrentSentiment(): String?

    @Query("SELECT COUNT(*) FROM ai_market_analyses WHERE sentiment = :sentiment AND timestamp >= :since")
    suspend fun getSentimentCount(sentiment: String, since: Long): Int

    @Query("SELECT AVG(confidence) FROM ai_market_analyses WHERE timestamp >= :since")
    suspend fun getAverageConfidence(since: Long): Double?
}
