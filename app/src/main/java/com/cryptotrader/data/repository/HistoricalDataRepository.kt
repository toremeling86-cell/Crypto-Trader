package com.cryptotrader.data.repository

import com.cryptotrader.data.remote.kraken.KrakenApiService
import com.cryptotrader.domain.backtesting.PriceBar
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for fetching and caching historical OHLC data from Kraken
 */
@Singleton
class HistoricalDataRepository @Inject constructor(
    private val krakenApi: KrakenApiService
) {

    // In-memory cache for historical data
    private val cache = mutableMapOf<String, List<PriceBar>>()

    /**
     * Fetch OHLC historical data from Kraken
     *
     * @param pair Trading pair (e.g., "XXBTZUSD")
     * @param interval Timeframe in minutes: 1, 5, 15, 30, 60, 240, 1440, 10080, 21600
     * @param since Unix timestamp to fetch data from (optional)
     * @return List of price bars (OHLC)
     */
    suspend fun fetchHistoricalData(
        pair: String,
        interval: Int = 60, // Default: 1 hour
        since: Long? = null
    ): Result<List<PriceBar>> {
        return try {
            val cacheKey = "$pair-$interval"

            // Check cache first
            if (cache.containsKey(cacheKey) && since == null) {
                Timber.d("Returning cached historical data for $cacheKey")
                return Result.success(cache[cacheKey]!!)
            }

            Timber.d("Fetching historical OHLC data for $pair, interval: ${interval}m")

            val response = krakenApi.getOHLC(
                pair = pair,
                interval = interval,
                since = since
            )

            if (response.isSuccessful && response.body() != null) {
                val body = response.body()!!

                if (body.error.isNotEmpty()) {
                    val errorMsg = body.error.joinToString(", ")
                    Timber.e("Kraken API error: $errorMsg")
                    return Result.failure(Exception("Kraken API error: $errorMsg"))
                }

                // Parse OHLC data
                val ohlcData = body.result?.get(pair)
                if (ohlcData == null) {
                    Timber.e("No OHLC data found for pair: $pair")
                    return Result.failure(Exception("No data found for $pair"))
                }

                // Convert to PriceBar objects
                @Suppress("UNCHECKED_CAST")
                val ohlcList = ohlcData as? List<List<Any>> ?: emptyList()
                val priceBars = ohlcList.map { ohlcArray ->
                    PriceBar(
                        timestamp = (ohlcArray[0] as Double).toLong() * 1000, // Convert to milliseconds
                        open = (ohlcArray[1] as String).toDouble(),
                        high = (ohlcArray[2] as String).toDouble(),
                        low = (ohlcArray[3] as String).toDouble(),
                        close = (ohlcArray[4] as String).toDouble(),
                        volume = (ohlcArray[6] as String).toDouble()
                    )
                }

                // Cache the data
                if (since == null) {
                    cache[cacheKey] = priceBars
                }

                Timber.i("Fetched ${priceBars.size} price bars for $pair")
                Result.success(priceBars)
            } else {
                val errorMsg = "HTTP error: ${response.code()}"
                Timber.e(errorMsg)
                Result.failure(Exception(errorMsg))
            }
        } catch (e: Exception) {
            Timber.e(e, "Error fetching historical data")
            Result.failure(e)
        }
    }

    /**
     * Fetch historical data for multiple timeframes
     *
     * @param pair Trading pair
     * @param timeframes List of intervals in minutes
     * @return Map of interval to price bars
     */
    suspend fun fetchMultiTimeframeData(
        pair: String,
        timeframes: List<Int> = listOf(15, 60, 240) // 15m, 1h, 4h
    ): Result<Map<Int, List<PriceBar>>> {
        return try {
            val results = mutableMapOf<Int, List<PriceBar>>()

            timeframes.forEach { interval ->
                val result = fetchHistoricalData(pair, interval)
                if (result.isSuccess) {
                    results[interval] = result.getOrNull()!!
                } else {
                    return Result.failure(result.exceptionOrNull() ?: Exception("Failed to fetch $interval minute data"))
                }
            }

            Result.success(results)
        } catch (e: Exception) {
            Timber.e(e, "Error fetching multi-timeframe data")
            Result.failure(e)
        }
    }

    /**
     * Get recent historical data (last N bars)
     *
     * @param pair Trading pair
     * @param interval Timeframe in minutes
     * @param count Number of bars to fetch
     * @return List of most recent price bars
     */
    suspend fun getRecentBars(
        pair: String,
        interval: Int = 60,
        count: Int = 100
    ): Result<List<PriceBar>> {
        val result = fetchHistoricalData(pair, interval)
        return if (result.isSuccess) {
            val allBars = result.getOrNull()!!
            val recentBars = allBars.takeLast(count)
            Result.success(recentBars)
        } else {
            result
        }
    }

    /**
     * Clear cached historical data
     */
    fun clearCache() {
        cache.clear()
        Timber.d("Historical data cache cleared")
    }

    /**
     * Get cache statistics
     */
    fun getCacheStats(): Map<String, Int> {
        return cache.mapValues { it.value.size }
    }
}
