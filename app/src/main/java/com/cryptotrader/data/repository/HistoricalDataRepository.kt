package com.cryptotrader.data.repository

import com.cryptotrader.data.remote.kraken.KrakenApiService
import com.cryptotrader.domain.backtesting.PriceBar
import timber.log.Timber
import java.util.Date
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

    companion object {
        private const val TAG = "HistoricalDataRepo"
        private const val BITCOIN_GENESIS_TIME = 1231006505000L  // January 3, 2009
        private const val MAX_PRICE_RANGE_PERCENT = 50.0  // Warn if candle has >50% range
    }

    /**
     * Validate OHLC data integrity
     *
     * Checks:
     * 1. Low <= High (basic sanity)
     * 2. Low <= Open <= High
     * 3. Low <= Close <= High
     * 4. All prices > 0
     * 5. Volume >= 0
     * 6. Timestamp is reasonable (not in future, not too old)
     *
     * @return true if valid, false if corrupt
     */
    private fun validateOHLC(
        timestamp: Long,
        open: Double,
        high: Double,
        low: Double,
        close: Double,
        volume: Double,
        pair: String
    ): Boolean {
        val now = System.currentTimeMillis()

        // Check 1: All prices must be positive
        if (open <= 0 || high <= 0 || low <= 0 || close <= 0) {
            Timber.w("[$TAG] Invalid OHLC for $pair: Negative or zero price (O=$open, H=$high, L=$low, C=$close)")
            return false
        }

        // Check 2: Volume must be non-negative
        if (volume < 0) {
            Timber.w("[$TAG] Invalid OHLC for $pair: Negative volume ($volume)")
            return false
        }

        // Check 3: Low must be <= High
        if (low > high) {
            Timber.w("[$TAG] Invalid OHLC for $pair: Low ($low) > High ($high)")
            return false
        }

        // Check 4: Open must be between Low and High
        if (open < low || open > high) {
            Timber.w("[$TAG] Invalid OHLC for $pair: Open ($open) outside [Low=$low, High=$high]")
            return false
        }

        // Check 5: Close must be between Low and High
        if (close < low || close > high) {
            Timber.w("[$TAG] Invalid OHLC for $pair: Close ($close) outside [Low=$low, High=$high]")
            return false
        }

        // Check 6: Timestamp must not be in the future
        if (timestamp > now + (60 * 60 * 1000)) {  // Allow 1 hour tolerance for clock skew
            Timber.w("[$TAG] Invalid OHLC for $pair: Timestamp in future (${Date(timestamp)})")
            return false
        }

        // Check 7: Timestamp must not be too old (e.g., before Bitcoin existed)
        if (timestamp < BITCOIN_GENESIS_TIME) {
            Timber.w("[$TAG] Invalid OHLC for $pair: Timestamp before Bitcoin genesis block (${Date(timestamp)})")
            return false
        }

        // Check 8: Detect price spikes (optional - extreme outliers)
        val priceRange = high - low
        val avgPrice = (high + low) / 2.0
        val rangePercent = (priceRange / avgPrice) * 100.0

        if (rangePercent > MAX_PRICE_RANGE_PERCENT) {  // More than 50% range in a single candle
            Timber.w("[$TAG] Suspicious OHLC for $pair: Extreme price range (${"%.2f".format(rangePercent)}% in single candle)")
            // Don't reject, just warn - could be real volatility
        }

        return true
    }

    /**
     * Check for gaps in historical data timeline
     *
     * @param data OHLC data sorted by timestamp
     * @param expectedInterval Expected interval between candles in milliseconds
     * @return List of detected gaps (each as Pair<timestamp, gap duration>)
     */
    private fun detectDataGaps(
        data: List<PriceBar>,
        expectedInterval: Long
    ): List<Pair<Long, Long>> {
        val gaps = mutableListOf<Pair<Long, Long>>()

        for (i in 1 until data.size) {
            val actualInterval = data[i].timestamp - data[i - 1].timestamp
            val tolerance = expectedInterval * 0.5  // Allow 50% tolerance

            if (actualInterval > expectedInterval + tolerance) {
                val gapDuration = actualInterval - expectedInterval
                gaps.add(Pair(data[i - 1].timestamp, gapDuration))
                Timber.w("[$TAG] Data gap detected: ${gapDuration / (60 * 1000)} minutes after ${Date(data[i - 1].timestamp)}")
            }
        }

        return gaps
    }

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

                // Convert to PriceBar objects with validation
                @Suppress("UNCHECKED_CAST")
                val ohlcList = ohlcData as? List<List<Any>> ?: emptyList()
                val totalBars = ohlcList.size

                val priceBars = ohlcList.mapNotNull { ohlcArray ->  // Use mapNotNull to filter invalid
                    try {
                        val timestamp = (ohlcArray[0] as Double).toLong() * 1000  // Convert to milliseconds
                        val open = (ohlcArray[1] as String).toDouble()
                        val high = (ohlcArray[2] as String).toDouble()
                        val low = (ohlcArray[3] as String).toDouble()
                        val close = (ohlcArray[4] as String).toDouble()
                        val volume = (ohlcArray[6] as String).toDouble()

                        // Validate before creating object
                        if (validateOHLC(timestamp, open, high, low, close, volume, pair)) {
                            PriceBar(
                                timestamp = timestamp,
                                open = open,
                                high = high,
                                low = low,
                                close = close,
                                volume = volume
                            )
                        } else {
                            Timber.e("[$TAG] Skipping invalid OHLC data for $pair at ${Date(timestamp)}")
                            null  // Skip this invalid bar
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "[$TAG] Error parsing OHLC data: ${e.message}")
                        null  // Skip unparseable data
                    }
                }

                // Log validation summary
                val validBars = priceBars.size
                val invalidBars = totalBars - validBars

                if (invalidBars > 0) {
                    Timber.w("[$TAG] Data validation: $validBars valid, $invalidBars invalid bars filtered out for $pair")
                }

                // Check for data gaps
                if (priceBars.isNotEmpty()) {
                    val expectedInterval = interval * 60 * 1000L  // Convert minutes to milliseconds
                    val gaps = detectDataGaps(priceBars, expectedInterval)

                    if (gaps.isNotEmpty()) {
                        Timber.w("[$TAG] Detected ${gaps.size} data gap(s) in $pair data")
                    }
                }

                // Cache the data
                if (since == null) {
                    cache[cacheKey] = priceBars
                }

                Timber.i("[$TAG] Fetched ${priceBars.size} valid price bars for $pair (${invalidBars} invalid filtered out)")
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
