package com.cryptotrader.data.local.dao

import androidx.room.*
import com.cryptotrader.data.local.entities.ApiKeyEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ApiKeyDao {
    @Query("SELECT * FROM api_keys WHERE id = 1")
    fun getApiKey(): Flow<ApiKeyEntity?>

    @Query("SELECT * FROM api_keys WHERE id = 1 LIMIT 1")
    suspend fun getApiKeySuspend(): ApiKeyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateApiKey(apiKey: ApiKeyEntity)

    @Query("DELETE FROM api_keys")
    suspend fun deleteAllApiKeys()

    @Query("SELECT EXISTS(SELECT 1 FROM api_keys WHERE id = 1)")
    suspend fun apiKeyExists(): Boolean

    @Query("UPDATE api_keys SET lastUsed = :timestamp WHERE id = 1")
    suspend fun updateLastUsed(timestamp: Long = System.currentTimeMillis())
}
