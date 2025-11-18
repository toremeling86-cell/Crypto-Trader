package com.cryptotrader.data.remote.kraken.dto

import com.squareup.moshi.JsonClass

/**
 * Kraken OHLC (candlestick) data response
 *
 * Array structure: [time, open, high, low, close, vwap, volume, count]
 * Example: [1688140800, "30000.0", "30100.0", "29900.0", "30050.0", "30020.5", "12.5", 150]
 */
data class OHLCCandle(
    val timestamp: Long,      // Unix timestamp
    val open: Double,         // Open price
    val high: Double,         // High price
    val low: Double,          // Low price
    val close: Double,        // Close price
    val vwap: Double,         // Volume-weighted average price
    val volume: Double,       // Volume
    val count: Int            // Number of trades
) {
    companion object {
        /**
         * Parse OHLC array from Kraken API response
         * Format: [time, "open", "high", "low", "close", "vwap", "volume", count]
         */
        fun fromArray(array: List<Any>): OHLCCandle? {
            return try {
                OHLCCandle(
                    timestamp = (array[0] as Number).toLong(),
                    open = (array[1] as String).toDouble(),
                    high = (array[2] as String).toDouble(),
                    low = (array[3] as String).toDouble(),
                    close = (array[4] as String).toDouble(),
                    vwap = (array[5] as String).toDouble(),
                    volume = (array[6] as String).toDouble(),
                    count = (array[7] as Number).toInt()
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

/**
 * OHLC response data
 */
data class OHLCResponseData(
    val candles: List<OHLCCandle>,
    val last: Long  // Last committed OHLC timestamp for pagination
)
