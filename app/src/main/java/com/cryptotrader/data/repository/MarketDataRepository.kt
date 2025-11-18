package com.cryptotrader.data.repository

import com.cryptotrader.data.remote.kraken.KrakenApiService
import com.cryptotrader.data.remote.kraken.dto.OHLCCandle
import com.cryptotrader.domain.backtesting.PriceBar
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for fetching real market data from Kraken
 *
 * Professional quant traders NEVER use simulated data - this fetches REAL OHLC data
 */
@Singleton
class MarketDataRepository @Inject constructor(
    private val krakenApi: KrakenApiService
) {

    // Simple in-memory cache to avoid excessive API calls
    private val ohlcCache = mutableMapOf<String, Pair<Long, List<OHLCCandle>>>()
    private val cacheExpiryMs = 5 * 60 * 1000L // 5 minutes

    /**
     * Fetch REAL historical OHLC data from Kraken
     *
     * @param pair Trading pair (e.g., "XXBTZUSD" for BTC/USD)
     * @param intervalMinutes Timeframe in minutes (1, 5, 15, 30, 60, 240, 1440, 10080, 21600)
     * @param numCandles Number of candles to fetch (default 100 for backtesting)
     * @return List of real OHLC candles from Kraken
     */
    suspend fun getHistoricalOHLC(
        pair: String,
        intervalMinutes: Int = 60, // Default: 1 hour candles
        numCandles: Int = 100
    ): Result<List<OHLCCandle>> {
        return try {
            // Check cache first
            val cacheKey = "$pair-$intervalMinutes"
            val cached = ohlcCache[cacheKey]
            if (cached != null && System.currentTimeMillis() - cached.first < cacheExpiryMs) {
                Timber.d("Using cached OHLC data for $pair (${cached.second.size} candles)")
                return Result.success(cached.second.takeLast(numCandles))
            }

            Timber.i("Fetching REAL historical data from Kraken: $pair, ${intervalMinutes}min, $numCandles candles")

            // Calculate 'since' timestamp to get approximately numCandles
            val now = System.currentTimeMillis() / 1000 // Convert to seconds
            val intervalSeconds = intervalMinutes * 60L
            val sinceTimestamp = now - (numCandles * intervalSeconds)

            // Call Kraken OHLC endpoint
            val response = krakenApi.getOHLC(
                pair = pair,
                interval = intervalMinutes,
                since = sinceTimestamp
            )

            if (!response.isSuccessful) {
                val error = response.errorBody()?.string()
                Timber.e("Kraken OHLC API error: ${response.code()} - $error")
                return Result.failure(Exception("Failed to fetch OHLC data: ${response.message()}"))
            }

            val krakenResponse = response.body()
            if (krakenResponse == null) {
                return Result.failure(Exception("Empty OHLC response from Kraken"))
            }

            if (krakenResponse.error.isNotEmpty()) {
                return Result.failure(Exception("Kraken error: ${krakenResponse.error.joinToString()}"))
            }

            // Parse OHLC data from response
            val resultData = krakenResponse.result as? Map<*, *>
                ?: return Result.failure(Exception("Invalid OHLC response format"))

            // Find the pair data (Kraken returns it with the pair as key)
            val pairData = resultData.entries.firstOrNull { it.key.toString().contains(pair.take(3)) }?.value as? List<*>
                ?: return Result.failure(Exception("No OHLC data found for pair: $pair"))

            // Parse candles from array format
            val candles = pairData.mapNotNull { candleArray ->
                if (candleArray is List<*>) {
                    @Suppress("UNCHECKED_CAST")
                    OHLCCandle.fromArray(candleArray as List<Any>)
                } else null
            }

            if (candles.isEmpty()) {
                return Result.failure(Exception("No valid candles parsed from Kraken response"))
            }

            Timber.i("Successfully fetched ${candles.size} REAL candles from Kraken for $pair")

            // Cache the data
            ohlcCache[cacheKey] = Pair(System.currentTimeMillis(), candles)

            // Return requested number of candles
            Result.success(candles.takeLast(numCandles))

        } catch (e: Exception) {
            Timber.e(e, "Error fetching OHLC data from Kraken")
            Result.failure(e)
        }
    }

    /**
     * Convert OHLC candles to PriceBar format for backtesting
     */
    fun convertToPriceBars(candles: List<OHLCCandle>): List<PriceBar> {
        return candles.map { candle ->
            PriceBar(
                timestamp = candle.timestamp * 1000, // Convert to milliseconds
                open = candle.open,
                high = candle.high,
                low = candle.low,
                close = candle.close,
                volume = candle.volume
            )
        }
    }

    /**
     * Clear cache (useful for forcing fresh data fetch)
     */
    fun clearCache() {
        ohlcCache.clear()
        Timber.d("OHLC cache cleared")
    }
}
