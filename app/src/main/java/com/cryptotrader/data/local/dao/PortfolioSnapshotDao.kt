package com.cryptotrader.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cryptotrader.data.local.entities.PortfolioSnapshotEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for portfolio snapshot operations
 */
@Dao
interface PortfolioSnapshotDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnapshot(snapshot: PortfolioSnapshotEntity)

    @Query("SELECT * FROM portfolio_snapshots ORDER BY timestamp DESC")
    fun getAllSnapshots(): Flow<List<PortfolioSnapshotEntity>>

    @Query("SELECT * FROM portfolio_snapshots WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp ASC")
    suspend fun getSnapshotsByDateRange(startTime: Long, endTime: Long): List<PortfolioSnapshotEntity>

    @Query("SELECT * FROM portfolio_snapshots ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestSnapshot(): PortfolioSnapshotEntity?

    @Query("SELECT * FROM portfolio_snapshots WHERE timestamp >= :since ORDER BY timestamp ASC")
    suspend fun getSnapshotsSince(since: Long): List<PortfolioSnapshotEntity>

    @Query("DELETE FROM portfolio_snapshots WHERE timestamp < :before")
    suspend fun deleteSnapshotsBefore(before: Long)

    @Query("DELETE FROM portfolio_snapshots")
    suspend fun deleteAllSnapshots()
}
