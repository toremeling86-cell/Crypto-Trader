package com.cryptotrader.data.repository

import com.cryptotrader.data.local.dao.MarketSnapshotDao
import com.cryptotrader.data.local.entities.MarketSnapshotEntity
import com.cryptotrader.data.remote.kraken.KrakenApiService
import com.cryptotrader.data.remote.kraken.RateLimiter
import com.cryptotrader.domain.model.MarketSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MarketSnapshotRepository @Inject constructor(
    private val krakenApi: KrakenApiService,
    private val marketSnapshotDao: MarketSnapshotDao,
    private val rateLimiter: RateLimiter
) {

    /**
     * Fetch live ticker data from Kraken and store in database
     * @param symbols List of trading pairs (e.g., ["XBTUSD", "ETHUSD", "SOLUSD"])
     */
    suspend fun fetchAndStoreMarketData(symbols: List<String>): Result<List<MarketSnapshot>> {
        return try {
            val snapshots = mutableListOf<MarketSnapshot>()

            symbols.forEach { symbol ->
                // Rate limit for public API
                rateLimiter.waitForPublicApiPermission()

                val response = krakenApi.getTicker(symbol)

                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    if (body.error.isEmpty() && body.result != null) {
                        val tickerData = body.result.values.firstOrNull()

                        if (tickerData != null) {
                            val lastPrice = tickerData.lastTradeClosed.getOrNull(0)?.toDoubleOrNull() ?: 0.0
                            val high24h = tickerData.high.getOrNull(1)?.toDoubleOrNull() ?: lastPrice
                            val low24h = tickerData.low.getOrNull(1)?.toDoubleOrNull() ?: lastPrice
                            val volume24h = tickerData.volume.getOrNull(1)?.toDoubleOrNull() ?: 0.0
                            val openPrice = tickerData.openingPrice.toDoubleOrNull() ?: lastPrice

                            val changePercent24h = if (openPrice > 0) {
                                ((lastPrice - openPrice) / openPrice * 100)
                            } else 0.0

                            val snapshot = MarketSnapshot(
                                symbol = symbol,
                                price = lastPrice,
                                volume24h = volume24h,
                                high24h = high24h,
                                low24h = low24h,
                                changePercent24h = changePercent24h,
                                timestamp = System.currentTimeMillis()
                            )

                            // Store in database
                            marketSnapshotDao.insertSnapshot(snapshot.toEntity())
                            snapshots.add(snapshot)

                            Timber.d("Stored market snapshot for $symbol: $lastPrice (${changePercent24h}%)")
                        }
                    } else {
                        Timber.w("Error fetching ticker for $symbol: ${body.error.joinToString()}")
                    }
                } else {
                    Timber.w("API call failed for $symbol: ${response.code()}")
                }
            }

            Result.success(snapshots)
        } catch (e: Exception) {
            Timber.e(e, "Error fetching market data")
            Result.failure(e)
        }
    }

    /**
     * Get latest snapshot for a specific symbol
     */
    suspend fun getLatestSnapshot(symbol: String): MarketSnapshot? {
        return marketSnapshotDao.getLatestSnapshotForSymbol(symbol)?.toDomain()
    }

    /**
     * Get latest snapshots for all symbols
     */
    fun getLatestSnapshots(symbols: List<String>): Flow<List<MarketSnapshot>> {
        return marketSnapshotDao.getLatestSnapshotsForSymbols(symbols).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /**
     * Get historical snapshots for a symbol within a time range
     */
    suspend fun getSnapshotHistory(
        symbol: String,
        startTime: Long,
        endTime: Long
    ): List<MarketSnapshot> {
        return marketSnapshotDao.getSnapshotsByDateRange(symbol, startTime, endTime)
            .map { it.toDomain() }
    }

    /**
     * Get recent snapshots for a symbol
     */
    suspend fun getRecentSnapshots(symbol: String, limit: Int = 100): List<MarketSnapshot> {
        return marketSnapshotDao.getRecentSnapshotsForSymbol(symbol, limit)
            .map { it.toDomain() }
    }

    /**
     * Delete old snapshots to prevent database bloat
     * Keeps snapshots from the last 30 days
     */
    suspend fun cleanupOldSnapshots(daysToKeep: Int = 30) {
        val cutoffTime = System.currentTimeMillis() - (daysToKeep * 24 * 60 * 60 * 1000L)
        marketSnapshotDao.deleteSnapshotsBefore(cutoffTime)
        Timber.d("Cleaned up market snapshots older than $daysToKeep days")
    }

    // Domain <-> Entity mapping
    private fun MarketSnapshot.toEntity() = MarketSnapshotEntity(
        symbol = symbol,
        price = price,
        volume24h = volume24h,
        high24h = high24h,
        low24h = low24h,
        changePercent24h = changePercent24h,
        timestamp = timestamp
    )

    private fun MarketSnapshotEntity.toDomain() = MarketSnapshot(
        symbol = symbol,
        price = price,
        volume24h = volume24h,
        high24h = high24h,
        low24h = low24h,
        changePercent24h = changePercent24h,
        timestamp = timestamp
    )

    companion object {
        // Popular crypto pairs on Kraken
        val DEFAULT_WATCHLIST = listOf(
            "XXBTZUSD",  // BTC/USD
            "XETHZUSD",  // ETH/USD
            "SOLUSD",    // SOL/USD
            "ADAUSD",    // ADA/USD
            "DOTUSD",    // DOT/USD
            "MATICUSD",  // MATIC/USD
            "AVAXUSD",   // AVAX/USD
            "LINKUSD"    // LINK/USD
        )
    }
}
