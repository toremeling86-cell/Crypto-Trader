package com.cryptotrader.domain.backtesting

import kotlin.math.sin
import kotlin.random.Random

/**
 * Synthetic OHLC Data Generator for Testing
 *
 * Generates realistic price data with controllable patterns:
 * - Trending (bullish/bearish)
 * - Ranging (sideways)
 * - Volatile (high price swings)
 * - Realistic (random walk with trends)
 */
object SyntheticDataGenerator {

    /**
     * Generate realistic BTC price data (Jan 2024 style)
     *
     * - Starting price: ~$50,000
     * - Price range: $45,000 - $55,000
     * - Realistic volatility: 2-5% daily
     * - Volume: 100-500 BTC per candle
     */
    fun generateRealisticBTCData(
        bars: Int = 744,  // 31 days * 24 hours
        startPrice: Double = 50000.0,
        startTimestamp: Long = 1704067200000  // 2024-01-01 00:00:00
    ): List<PriceBar> {
        val data = mutableListOf<PriceBar>()
        var currentPrice = startPrice
        val random = Random(42)  // Fixed seed for reproducibility

        for (i in 0 until bars) {
            val timestamp = startTimestamp + (i * 3600000)  // +1 hour per bar

            // Random walk with mean reversion
            val drift = (startPrice - currentPrice) * 0.001  // Pull toward start price
            val volatility = currentPrice * 0.015  // 1.5% volatility
            val priceChange = (random.nextDouble() - 0.5) * 2 * volatility + drift
            currentPrice += priceChange

            // Ensure price stays in reasonable range
            currentPrice = currentPrice.coerceIn(startPrice * 0.85, startPrice * 1.15)

            // Generate OHLC
            val high = currentPrice + random.nextDouble() * currentPrice * 0.01  // +0-1%
            val low = currentPrice - random.nextDouble() * currentPrice * 0.01   // -0-1%
            val open = low + random.nextDouble() * (high - low)
            val close = low + random.nextDouble() * (high - low)
            val volume = 100.0 + random.nextDouble() * 400.0  // 100-500 BTC

            data.add(
                PriceBar(
                    timestamp = timestamp,
                    open = open,
                    high = high,
                    low = low,
                    close = close,
                    volume = volume,
                )
            )
        }

        return data
    }

    /**
     * Generate uptrend data (bullish market)
     *
     * - Price increases over time
     * - Good for testing long strategies
     */
    fun generateUptrendData(
        bars: Int = 100,
        startPrice: Double = 50000.0,
        trendStrength: Double = 0.001  // 0.1% per bar
    ): List<PriceBar> {
        val data = mutableListOf<PriceBar>()
        var price = startPrice
        val random = Random(42)

        for (i in 0 until bars) {
            // Upward drift
            price *= (1.0 + trendStrength + (random.nextDouble() - 0.5) * 0.005)

            val high = price * (1.0 + random.nextDouble() * 0.01)
            val low = price * (1.0 - random.nextDouble() * 0.01)
            val open = low + random.nextDouble() * (high - low)
            val close = low + random.nextDouble() * (high - low)

            data.add(
                PriceBar(
                    timestamp = 1704067200000 + (i * 3600000),
                    open = open,
                    high = high,
                    low = low,
                    close = close,
                    volume = 100.0 + random.nextDouble() * 400.0,
                )
            )
        }

        return data
    }

    /**
     * Generate downtrend data (bearish market)
     *
     * - Price decreases over time
     * - Good for testing short strategies
     */
    fun generateDowntrendData(
        bars: Int = 100,
        startPrice: Double = 50000.0,
        trendStrength: Double = 0.001  // -0.1% per bar
    ): List<PriceBar> {
        val data = mutableListOf<PriceBar>()
        var price = startPrice
        val random = Random(42)

        for (i in 0 until bars) {
            // Downward drift
            price *= (1.0 - trendStrength + (random.nextDouble() - 0.5) * 0.005)

            val high = price * (1.0 + random.nextDouble() * 0.01)
            val low = price * (1.0 - random.nextDouble() * 0.01)
            val open = low + random.nextDouble() * (high - low)
            val close = low + random.nextDouble() * (high - low)

            data.add(
                PriceBar(
                    timestamp = 1704067200000 + (i * 3600000),
                    open = open,
                    high = high,
                    low = low,
                    close = close,
                    volume = 100.0 + random.nextDouble() * 400.0,
                )
            )
        }

        return data
    }

    /**
     * Generate ranging data (sideways market)
     *
     * - Price oscillates around a mean
     * - Good for testing mean-reversion strategies
     */
    fun generateRangingData(
        bars: Int = 100,
        meanPrice: Double = 50000.0,
        rangePercent: Double = 5.0  // +/- 5% range
    ): List<PriceBar> {
        val data = mutableListOf<PriceBar>()
        val random = Random(42)

        for (i in 0 until bars) {
            // Oscillate around mean using sine wave + noise
            val cycle = sin(i * 0.1) * (meanPrice * rangePercent / 100.0)
            val noise = (random.nextDouble() - 0.5) * (meanPrice * 0.01)
            val price = meanPrice + cycle + noise

            val high = price * (1.0 + random.nextDouble() * 0.005)
            val low = price * (1.0 - random.nextDouble() * 0.005)
            val open = low + random.nextDouble() * (high - low)
            val close = low + random.nextDouble() * (high - low)

            data.add(
                PriceBar(
                    timestamp = 1704067200000 + (i * 3600000),
                    open = open,
                    high = high,
                    low = low,
                    close = close,
                    volume = 100.0 + random.nextDouble() * 400.0,
                )
            )
        }

        return data
    }

    /**
     * Generate volatile data (high price swings)
     *
     * - Large intraday price movements
     * - Good for testing stop-loss/take-profit logic
     */
    fun generateVolatileData(
        bars: Int = 100,
        startPrice: Double = 50000.0,
        volatilityMultiplier: Double = 3.0  // 3x normal volatility
    ): List<PriceBar> {
        val data = mutableListOf<PriceBar>()
        var price = startPrice
        val random = Random(42)

        for (i in 0 until bars) {
            // High volatility random walk
            val change = (random.nextDouble() - 0.5) * 2 * price * 0.05 * volatilityMultiplier
            price += change
            price = price.coerceIn(startPrice * 0.5, startPrice * 1.5)

            val high = price * (1.0 + random.nextDouble() * 0.03 * volatilityMultiplier)
            val low = price * (1.0 - random.nextDouble() * 0.03 * volatilityMultiplier)
            val open = low + random.nextDouble() * (high - low)
            val close = low + random.nextDouble() * (high - low)

            data.add(
                PriceBar(
                    timestamp = 1704067200000 + (i * 3600000),
                    open = open,
                    high = high,
                    low = low,
                    close = close,
                    volume = 200.0 + random.nextDouble() * 800.0,  // Higher volume
                )
            )
        }

        return data
    }

    /**
     * Generate data with known RSI pattern
     *
     * - Oversold conditions (RSI < 30) at specific times
     * - Overbought conditions (RSI > 70) at specific times
     * - Good for testing RSI strategies
     */
    fun generateRSIPatternData(
        bars: Int = 100,
        basePrice: Double = 50000.0
    ): List<PriceBar> {
        val data = mutableListOf<PriceBar>()
        val random = Random(42)

        for (i in 0 until bars) {
            // Create alternating oversold/overbought conditions
            val phase = (i / 20) % 2  // Switch every 20 bars
            val targetPrice = when (phase) {
                0 -> basePrice * 0.95  // Oversold phase (RSI < 30)
                else -> basePrice * 1.05  // Overbought phase (RSI > 70)
            }

            // Gradual movement toward target
            val currentTargetRatio = (i % 20) / 20.0
            val price = basePrice + (targetPrice - basePrice) * currentTargetRatio +
                    (random.nextDouble() - 0.5) * basePrice * 0.005

            val high = price * (1.0 + random.nextDouble() * 0.005)
            val low = price * (1.0 - random.nextDouble() * 0.005)
            val open = low + random.nextDouble() * (high - low)
            val close = low + random.nextDouble() * (high - low)

            data.add(
                PriceBar(
                    timestamp = 1704067200000 + (i * 3600000),
                    open = open,
                    high = high,
                    low = low,
                    close = close,
                    volume = 100.0 + random.nextDouble() * 400.0,
                )
            )
        }

        return data
    }

    /**
     * Validate OHLC data integrity
     *
     * Returns list of validation errors (empty if valid)
     */
    fun validateOHLC(data: List<PriceBar>): List<String> {
        val errors = mutableListOf<String>()

        data.forEachIndexed { index, bar ->
            // Check 1: All prices positive
            if (bar.open <= 0 || bar.high <= 0 || bar.low <= 0 || bar.close <= 0) {
                errors.add("Bar $index: Negative or zero price detected")
            }

            // Check 2: High >= Low
            if (bar.high < bar.low) {
                errors.add("Bar $index: High (${ bar.high}) < Low (${bar.low})")
            }

            // Check 3: Open in [Low, High]
            if (bar.open < bar.low || bar.open > bar.high) {
                errors.add("Bar $index: Open (${bar.open}) outside [Low, High] range")
            }

            // Check 4: Close in [Low, High]
            if (bar.close < bar.low || bar.close > bar.high) {
                errors.add("Bar $index: Close (${bar.close}) outside [Low, High] range")
            }

            // Check 5: Volume non-negative
            if (bar.volume < 0) {
                errors.add("Bar $index: Negative volume (${bar.volume})")
            }
        }

        return errors
    }

    /**
     * Print summary statistics of generated data
     */
    fun printSummary(data: List<PriceBar>) {
        val prices = data.map { it.close }
        val volumes = data.map { it.volume }

        println("=== Data Summary ===")
        println("Bars: ${data.size}")
        println("Price Range: ${"%.2f".format(prices.minOrNull())} - ${"%.2f".format(prices.maxOrNull())}")
        println("Avg Price: ${"%.2f".format(prices.average())}")
        println("Avg Volume: ${"%.2f".format(volumes.average())}")
        println("Total Return: ${"%.2f".format(((prices.last() / prices.first()) - 1) * 100)}%")

        val validationErrors = validateOHLC(data)
        if (validationErrors.isEmpty()) {
            println("Validation: ✅ PASSED")
        } else {
            println("Validation: ❌ FAILED")
            validationErrors.forEach { println("  - $it") }
        }
    }
}
