package com.cryptotrader.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.cryptotrader.data.local.entities.TradingOpportunityEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for trading opportunity operations
 */
@Dao
interface TradingOpportunityDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOpportunity(opportunity: TradingOpportunityEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOpportunities(opportunities: List<TradingOpportunityEntity>): List<Long>

    @Update
    suspend fun updateOpportunity(opportunity: TradingOpportunityEntity)

    @Query("SELECT * FROM trading_opportunities ORDER BY timestamp DESC")
    fun getAllOpportunities(): Flow<List<TradingOpportunityEntity>>

    @Query("SELECT * FROM trading_opportunities WHERE id = :id")
    suspend fun getOpportunityById(id: Long): TradingOpportunityEntity?

    @Query("SELECT * FROM trading_opportunities WHERE analysisId = :analysisId ORDER BY priority DESC, confidence DESC")
    fun getOpportunitiesByAnalysisId(analysisId: Long): Flow<List<TradingOpportunityEntity>>

    @Query("SELECT * FROM trading_opportunities WHERE status = :status ORDER BY priority DESC, timestamp DESC")
    fun getOpportunitiesByStatus(status: String): Flow<List<TradingOpportunityEntity>>

    @Query("""
        SELECT * FROM trading_opportunities
        WHERE status = 'ACTIVE'
        ORDER BY
            CASE priority
                WHEN 'URGENT' THEN 4
                WHEN 'HIGH' THEN 3
                WHEN 'MEDIUM' THEN 2
                WHEN 'LOW' THEN 1
                ELSE 0
            END DESC,
            confidence DESC,
            timestamp DESC
    """)
    fun getActiveOpportunities(): Flow<List<TradingOpportunityEntity>>

    @Query("SELECT * FROM trading_opportunities WHERE asset = :asset AND status = 'ACTIVE' ORDER BY timestamp DESC")
    fun getActiveOpportunitiesForAsset(asset: String): Flow<List<TradingOpportunityEntity>>

    @Query("SELECT * FROM trading_opportunities WHERE priority = :priority AND status = 'ACTIVE' ORDER BY timestamp DESC")
    fun getOpportunitiesByPriority(priority: String): Flow<List<TradingOpportunityEntity>>

    @Query("""
        SELECT COUNT(*) FROM trading_opportunities
        WHERE status = 'ACTIVE' AND timestamp >= :since
    """)
    suspend fun getActiveOpportunitiesCountSince(since: Long): Int

    @Query("SELECT COUNT(*) FROM trading_opportunities WHERE timestamp >= :since")
    suspend fun getOpportunitiesCountToday(since: Long): Int

    @Query("""
        SELECT * FROM trading_opportunities
        WHERE timestamp >= :startTime AND timestamp <= :endTime
        ORDER BY timestamp DESC
    """)
    suspend fun getOpportunitiesByDateRange(startTime: Long, endTime: Long): List<TradingOpportunityEntity>

    @Query("UPDATE trading_opportunities SET status = :status WHERE id = :id")
    suspend fun updateOpportunityStatus(id: Long, status: String)

    @Query("UPDATE trading_opportunities SET notificationSent = :sent WHERE id = :id")
    suspend fun updateNotificationSent(id: Long, sent: Boolean)

    @Query("""
        UPDATE trading_opportunities
        SET status = 'EXPIRED'
        WHERE status = 'ACTIVE' AND expiresAt IS NOT NULL AND expiresAt < :currentTime
    """)
    suspend fun expireOpportunities(currentTime: Long): Int

    @Query("DELETE FROM trading_opportunities WHERE id = :id")
    suspend fun deleteOpportunity(id: Long)

    @Query("DELETE FROM trading_opportunities WHERE timestamp < :before")
    suspend fun deleteOpportunitiesBefore(before: Long): Int

    @Query("DELETE FROM trading_opportunities WHERE status = :status AND timestamp < :before")
    suspend fun deleteOpportunitiesByStatusBefore(status: String, before: Long): Int

    @Query("DELETE FROM trading_opportunities")
    suspend fun deleteAllOpportunities()
}
