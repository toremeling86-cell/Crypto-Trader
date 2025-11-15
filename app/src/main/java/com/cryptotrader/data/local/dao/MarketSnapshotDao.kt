package com.cryptotrader.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cryptotrader.data.local.entities.MarketSnapshotEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for market snapshot operations
 */
@Dao
interface MarketSnapshotDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnapshot(snapshot: MarketSnapshotEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSnapshots(snapshots: List<MarketSnapshotEntity>)

    @Query("SELECT * FROM market_snapshots WHERE symbol = :symbol ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestSnapshotForSymbol(symbol: String): MarketSnapshotEntity?

    @Query("SELECT * FROM market_snapshots WHERE symbol = :symbol ORDER BY timestamp DESC LIMIT 1")
    fun getLatestSnapshotForSymbolFlow(symbol: String): Flow<MarketSnapshotEntity?>

    @Query("SELECT * FROM market_snapshots WHERE symbol IN (:symbols) ORDER BY timestamp DESC")
    fun getLatestSnapshotsForSymbols(symbols: List<String>): Flow<List<MarketSnapshotEntity>>

    @Query("SELECT DISTINCT symbol FROM market_snapshots ORDER BY symbol ASC")
    fun getAllSymbols(): Flow<List<String>>

    @Query("""
        SELECT * FROM market_snapshots
        WHERE symbol = :symbol AND timestamp >= :startTime AND timestamp <= :endTime
        ORDER BY timestamp ASC
    """)
    suspend fun getSnapshotsByDateRange(
        symbol: String,
        startTime: Long,
        endTime: Long
    ): List<MarketSnapshotEntity>

    @Query("SELECT * FROM market_snapshots WHERE symbol = :symbol ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentSnapshotsForSymbol(symbol: String, limit: Int = 100): List<MarketSnapshotEntity>

    @Query("DELETE FROM market_snapshots WHERE timestamp < :before")
    suspend fun deleteSnapshotsBefore(before: Long)

    @Query("DELETE FROM market_snapshots WHERE symbol = :symbol")
    suspend fun deleteSnapshotsForSymbol(symbol: String)

    @Query("DELETE FROM market_snapshots")
    suspend fun deleteAllSnapshots()

    // Analytics queries
    @Query("SELECT AVG(changePercent24h) FROM market_snapshots WHERE symbol = :symbol AND timestamp >= :since")
    suspend fun getAverageChangePercent(symbol: String, since: Long): Double?

    @Query("SELECT MAX(high24h) FROM market_snapshots WHERE symbol = :symbol AND timestamp >= :since")
    suspend fun getHighest24hPrice(symbol: String, since: Long): Double?

    @Query("SELECT MIN(low24h) FROM market_snapshots WHERE symbol = :symbol AND timestamp >= :since")
    suspend fun getLowest24hPrice(symbol: String, since: Long): Double?
}
