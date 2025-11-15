package com.cryptotrader.domain.indicators

import java.util.Random

/**
 * Test data fixtures for indicator testing
 *
 * Provides reusable test data generation methods for consistent testing across
 * different test classes.
 */
object TestDataFixtures {

    /**
     * Generates realistic price data using a random walk with drift
     *
     * This simulates actual market behavior with:
     * - Random price movements (Gaussian distribution)
     * - Slight upward drift (optional)
     * - Volatility control
     * - Reproducible results (seeded random)
     *
     * @param startPrice Initial price
     * @param count Number of data points to generate
     * @param volatility Price volatility (as percentage, e.g., 0.02 = 2%)
     * @param seed Random seed for reproducibility (default: 42)
     * @return List of prices
     */
    fun generateRealisticPriceData(
        startPrice: Double = 50000.0,
        count: Int = 150,
        volatility: Double = 0.02,
        seed: Long = 42
    ): List<Double> {
        val prices = mutableListOf(startPrice)
        var currentPrice = startPrice
        val random = Random(seed)

        for (i in 1 until count) {
            // Random walk with drift
            val change = random.nextGaussian() * volatility * currentPrice
            val drift = 0.0001 * currentPrice // 0.01% upward bias

            currentPrice += change + drift
            // Keep price positive (at least 50% of start price)
            currentPrice = currentPrice.coerceAtLeast(startPrice * 0.5)

            prices.add(currentPrice)
        }

        return prices
    }

    /**
     * Generates constant price data (no movement)
     *
     * Useful for testing edge cases where there's no volatility.
     *
     * @param price The constant price
     * @param count Number of data points
     * @return List of identical prices
     */
    fun generateConstantPriceData(
        price: Double = 100.0,
        count: Int = 100
    ): List<Double> {
        return List(count) { price }
    }

    /**
     * Generates trending price data (upward or downward)
     *
     * Creates a linear trend in price data.
     *
     * @param startPrice Initial price
     * @param count Number of data points
     * @param trendPercent Total trend percentage (positive = up, negative = down)
     * @return List of prices following the trend
     */
    fun generateTrendingPriceData(
        startPrice: Double = 100.0,
        count: Int = 100,
        trendPercent: Double = 0.20 // 20% change over the period
    ): List<Double> {
        val prices = mutableListOf<Double>()
        for (i in 0 until count) {
            val price = startPrice * (1.0 + (trendPercent * i / count))
            prices.add(price)
        }
        return prices
    }

    /**
     * Generates extreme volatility price data
     *
     * Creates highly volatile price movements for stress testing.
     *
     * @param startPrice Initial price
     * @param count Number of data points
     * @param seed Random seed for reproducibility
     * @return List of highly volatile prices
     */
    fun generateVolatilePriceData(
        startPrice: Double = 100.0,
        count: Int = 20,
        seed: Long = 42
    ): List<Double> {
        val prices = mutableListOf<Double>()
        val random = Random(seed)

        for (i in 0 until count) {
            // Extreme swings between -25% and +50%
            val change = (random.nextDouble() * 0.75 - 0.25)
            val price = startPrice * (1.0 + change)
            prices.add(price.coerceAtLeast(startPrice * 0.5))
        }

        return prices
    }

    /**
     * Generates OHLC data from closing prices
     *
     * Creates High, Low, Open data based on closing prices with realistic variations.
     *
     * @param closes List of closing prices
     * @param highLowSpread Percentage spread for high/low (default: 0.5% = 0.005)
     * @return Triple of (highs, lows, opens)
     */
    fun generateOHLCFromCloses(
        closes: List<Double>,
        highLowSpread: Double = 0.005
    ): Triple<List<Double>, List<Double>, List<Double>> {
        val highs = closes.mapIndexed { index, close ->
            close * (1.0 + highLowSpread * (1 + index % 3) / 2.0)
        }

        val lows = closes.mapIndexed { index, close ->
            close * (1.0 - highLowSpread * (1 + index % 3) / 2.0)
        }

        val opens = closes.mapIndexed { index, close ->
            if (index == 0) close
            else closes[index - 1] // Open is previous close
        }

        return Triple(highs, lows, opens)
    }

    /**
     * Generates volume data
     *
     * Creates realistic volume data with some variation.
     *
     * @param count Number of data points
     * @param baseVolume Base volume amount
     * @param variationPercent Percentage variation in volume (default: 20% = 0.20)
     * @param seed Random seed for reproducibility
     * @return List of volumes
     */
    fun generateVolumeData(
        count: Int = 100,
        baseVolume: Double = 1000000.0,
        variationPercent: Double = 0.20,
        seed: Long = 42
    ): List<Double> {
        val random = Random(seed)
        return List(count) {
            baseVolume * (1.0 + (random.nextDouble() * 2 - 1) * variationPercent)
        }
    }

    /**
     * Known good RSI calculation values for validation
     *
     * These are pre-calculated RSI values for a specific dataset,
     * useful for regression testing.
     */
    object KnownGoodValues {
        // Price data for known values
        val knownPrices = listOf(
            44.34, 44.09, 44.15, 43.61, 44.33, 44.83, 45.10, 45.42,
            45.84, 46.08, 45.89, 46.03, 45.61, 46.28, 46.28, 46.00,
            46.03, 46.41, 46.22, 45.64
        )

        // Expected RSI value for the above data (period=14)
        // Calculated using standard RSI formula
        val expectedRSI14 = 66.32 // Approximately
    }

    /**
     * Creates a dataset with a known indicator pattern
     *
     * Useful for validating that indicators detect known conditions
     * (e.g., overbought, oversold, trend changes)
     */
    object PatternData {
        // Overbought pattern (strong uptrend)
        val overboughtPrices = generateTrendingPriceData(
            startPrice = 100.0,
            count = 30,
            trendPercent = 0.30 // 30% gain
        )

        // Oversold pattern (strong downtrend)
        val oversoldPrices = generateTrendingPriceData(
            startPrice = 100.0,
            count = 30,
            trendPercent = -0.30 // 30% loss
        )

        // Ranging market (oscillating)
        fun generateRangingPrices(count: Int = 30): List<Double> {
            val prices = mutableListOf<Double>()
            val basePrice = 100.0
            for (i in 0 until count) {
                // Oscillate between 95 and 105
                val price = basePrice + 5.0 * kotlin.math.sin(i * kotlin.math.PI / 5)
                prices.add(price)
            }
            return prices
        }
    }
}
