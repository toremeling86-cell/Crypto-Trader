package com.cryptotrader.data.local.dao

import androidx.room.*
import com.cryptotrader.data.local.entities.KnowledgeBaseEntity
import kotlinx.coroutines.flow.Flow

/**
 * Knowledge Base DAO - Cross-Strategy Learning Data Access
 *
 * Version: 19+ (P0-2: Meta-Analysis Integration)
 *
 * Provides access to aggregated learnings from meta-analyses and backtest runs.
 */
@Dao
interface KnowledgeBaseDao {

    /**
     * Insert a new knowledge base entry
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(knowledge: KnowledgeBaseEntity): Long

    /**
     * Update existing knowledge base entry
     */
    @Update
    suspend fun update(knowledge: KnowledgeBaseEntity)

    /**
     * Get all active knowledge base entries
     */
    @Query("SELECT * FROM knowledge_base WHERE isActive = 1 ORDER BY confidence DESC, lastUpdatedAt DESC")
    fun getAllActive(): Flow<List<KnowledgeBaseEntity>>

    /**
     * Get knowledge by category
     */
    @Query("SELECT * FROM knowledge_base WHERE category = :category AND isActive = 1 ORDER BY confidence DESC")
    fun getByCategory(category: String): Flow<List<KnowledgeBaseEntity>>

    /**
     * Get knowledge for specific market regime
     */
    @Query("SELECT * FROM knowledge_base WHERE (marketRegime = :regime OR marketRegime IS NULL) AND isActive = 1 ORDER BY confidence DESC")
    fun getByMarketRegime(regime: String): Flow<List<KnowledgeBaseEntity>>

    /**
     * Get knowledge for specific asset class
     */
    @Query("SELECT * FROM knowledge_base WHERE assetClass = :assetClass AND isActive = 1 ORDER BY confidence DESC")
    fun getByAssetClass(assetClass: String): Flow<List<KnowledgeBaseEntity>>

    /**
     * Get high-confidence learnings (confidence >= threshold)
     */
    @Query("SELECT * FROM knowledge_base WHERE confidence >= :minConfidence AND isActive = 1 ORDER BY confidence DESC")
    fun getHighConfidence(minConfidence: Double = 0.7): Flow<List<KnowledgeBaseEntity>>

    /**
     * Get knowledge by ID
     */
    @Query("SELECT * FROM knowledge_base WHERE id = :id")
    suspend fun getById(id: Long): KnowledgeBaseEntity?

    /**
     * Search knowledge base by title or insight
     */
    @Query("SELECT * FROM knowledge_base WHERE (title LIKE :query OR insight LIKE :query) AND isActive = 1 ORDER BY confidence DESC")
    fun search(query: String): Flow<List<KnowledgeBaseEntity>>

    /**
     * Invalidate a knowledge entry (soft delete)
     */
    @Query("UPDATE knowledge_base SET isActive = 0, invalidatedAt = :timestamp, invalidationReason = :reason WHERE id = :id")
    suspend fun invalidate(id: Long, timestamp: Long = System.currentTimeMillis(), reason: String)

    /**
     * Get knowledge count by category
     */
    @Query("SELECT COUNT(*) FROM knowledge_base WHERE category = :category AND isActive = 1")
    suspend fun getCountByCategory(category: String): Int

    /**
     * Get total active knowledge count
     */
    @Query("SELECT COUNT(*) FROM knowledge_base WHERE isActive = 1")
    suspend fun getTotalActiveCount(): Int

    /**
     * Delete all knowledge entries (use with caution)
     */
    @Query("DELETE FROM knowledge_base")
    suspend fun deleteAll()
}
