package com.cryptotrader.domain.trading

import com.cryptotrader.domain.indicators.Candle
import com.cryptotrader.domain.model.MarketTicker

/**
 * Adapter for converting market data to Candle format for indicator calculations
 *
 * This adapter bridges the gap between the app's MarketTicker data structure
 * and the Candle (OHLCV) format required by advanced indicator calculators.
 */
class MarketDataAdapter {

    /**
     * Converts a MarketTicker to a Candle
     *
     * When full OHLC data is available, it uses the actual values.
     * When OHLC data is incomplete, it falls back to using the last/close price
     * to ensure a valid Candle can always be created.
     *
     * @param ticker The market ticker data to convert
     * @param timestamp The timestamp for this candle (defaults to ticker timestamp)
     * @return A Candle object representing the ticker data
     */
    fun toCandle(ticker: MarketTicker, timestamp: Long = ticker.timestamp): Candle {
        // Use available OHLC data, falling back to last price if needed
        val close = ticker.last

        // Use 24h high/low if available, otherwise use close price as fallback
        val high = if (ticker.high24h > 0.0) ticker.high24h else close
        val low = if (ticker.low24h > 0.0) ticker.low24h else close

        // For open, we don't have direct data, so we estimate it
        // Open = Close - (Change24h)
        val open = close - ticker.change24h

        // Ensure open is within valid range (between low and high)
        val validOpen = when {
            open < low -> low
            open > high -> high
            else -> open
        }

        // Use 24h volume
        val volume = ticker.volume24h

        return Candle(
            timestamp = timestamp,
            open = validOpen,
            high = high,
            low = low,
            close = close,
            volume = volume
        )
    }

    /**
     * Converts a list of prices and timestamps to a list of Candles
     *
     * This method is useful when you have historical price data stored as simple
     * lists. It creates synthetic OHLC candles where O=H=L=C (all equal to the price).
     *
     * @param prices List of closing prices
     * @param timestamps List of corresponding timestamps (must match prices length)
     * @return List of Candle objects
     * @throws IllegalArgumentException if lists have different sizes
     */
    fun toCandleList(prices: List<Double>, timestamps: List<Long>): List<Candle> {
        require(prices.size == timestamps.size) {
            "Prices and timestamps lists must have the same size. " +
                    "Got ${prices.size} prices and ${timestamps.size} timestamps."
        }

        return prices.zip(timestamps).map { (price, timestamp) ->
            // Create a candle where O=H=L=C (all equal to the price)
            // This represents a single price point without intrabar variation
            Candle(
                timestamp = timestamp,
                open = price,
                high = price,
                low = price,
                close = price,
                volume = 0.0 // Volume unknown for simple price data
            )
        }
    }

    /**
     * Converts a list of prices to candles with auto-generated timestamps
     *
     * Timestamps are generated starting from the current time and going backwards
     * with the specified interval between each candle.
     *
     * @param prices List of closing prices (newest first or oldest first based on newestFirst)
     * @param intervalMillis Time interval between candles in milliseconds
     * @param newestFirst Whether the prices list is ordered newest-first (default: false)
     * @return List of Candle objects with generated timestamps
     */
    fun toCandleListWithInterval(
        prices: List<Double>,
        intervalMillis: Long,
        newestFirst: Boolean = false
    ): List<Candle> {
        val currentTime = System.currentTimeMillis()

        val timestamps = if (newestFirst) {
            // If newest first, assign timestamps going backwards from now
            prices.indices.map { i -> currentTime - (i * intervalMillis) }
        } else {
            // If oldest first, assign timestamps going backwards from now, then reverse
            prices.indices.map { i -> currentTime - ((prices.size - 1 - i) * intervalMillis) }
        }

        return toCandleList(prices, timestamps)
    }

    /**
     * Merges multiple MarketTickers into a time-series of Candles
     *
     * Useful when you have collected multiple ticker snapshots over time
     * and want to convert them into a candle series.
     *
     * @param tickers List of market tickers ordered by time
     * @return List of Candle objects
     */
    fun tickersToCandles(tickers: List<MarketTicker>): List<Candle> {
        return tickers.map { ticker -> toCandle(ticker) }
    }

    /**
     * Creates a synthetic candle from basic price data
     *
     * This is a convenience method for creating a candle when you only have
     * minimal price information.
     *
     * @param price The price (used for all OHLC values)
     * @param timestamp The timestamp for this candle
     * @param volume Optional volume (defaults to 0.0)
     * @return A Candle object
     */
    fun createSyntheticCandle(
        price: Double,
        timestamp: Long,
        volume: Double = 0.0
    ): Candle {
        return Candle(
            timestamp = timestamp,
            open = price,
            high = price,
            low = price,
            close = price,
            volume = volume
        )
    }
}
