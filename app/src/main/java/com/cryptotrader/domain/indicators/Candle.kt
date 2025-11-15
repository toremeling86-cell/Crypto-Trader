package com.cryptotrader.domain.indicators

/**
 * Represents a single candlestick (OHLCV) data point
 *
 * @property timestamp Unix timestamp in milliseconds
 * @property open Opening price
 * @property high Highest price during the period
 * @property low Lowest price during the period
 * @property close Closing price
 * @property volume Trading volume during the period
 */
data class Candle(
    val timestamp: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double
) {
    init {
        require(high >= low) { "High must be greater than or equal to low" }
        require(high >= open && high >= close) { "High must be greater than or equal to open and close" }
        require(low <= open && low <= close) { "Low must be less than or equal to open and close" }
        require(volume >= 0) { "Volume must be non-negative" }
    }
}
