package com.cryptotrader.domain.indicators

import com.cryptotrader.domain.indicators.atr.AtrCalculatorImpl
import com.cryptotrader.domain.indicators.bollingerbands.BollingerBandsCalculatorImpl
import com.cryptotrader.domain.indicators.cache.IndicatorCache
import com.cryptotrader.domain.indicators.cache.getOrPut
import com.cryptotrader.domain.indicators.macd.MacdCalculatorImpl
import com.cryptotrader.domain.indicators.movingaverage.MovingAverageCalculatorImpl
import com.cryptotrader.domain.indicators.rsi.RsiCalculatorImpl
import com.cryptotrader.domain.indicators.stochastic.StochasticCalculatorImpl
import com.cryptotrader.domain.indicators.volume.VolumeIndicatorCalculatorImpl

/**
 * Example usage of all technical indicator calculators
 *
 * This file demonstrates how to use each indicator calculator
 * with sample data and caching.
 */
class IndicatorUsageExample {

    private val cache = IndicatorCache(maxSize = 100)

    fun exampleRsiCalculation() {
        val rsiCalculator = RsiCalculatorImpl()

        // Sample closing prices
        val closes = listOf(
            44.0, 44.25, 44.5, 43.75, 44.0, 44.25, 44.75, 45.0,
            45.25, 45.5, 45.75, 46.0, 45.5, 45.25, 45.0, 44.75,
            44.5, 44.25, 44.0, 43.75
        )

        // Calculate RSI with period 14
        val rsiValues = rsiCalculator.calculate(closes, period = 14)

        // First 14 values will be null, then RSI values start
        println("RSI values: ${rsiValues.takeLast(5)}")
        // Expected: values between 0-100, typically 30-70 range
    }

    fun exampleMovingAverageCalculation() {
        val maCalculator = MovingAverageCalculatorImpl()

        val closes = listOf(
            10.0, 11.0, 12.0, 13.0, 14.0, 15.0, 16.0, 17.0,
            18.0, 19.0, 20.0, 21.0, 22.0, 23.0, 24.0, 25.0
        )

        // Calculate SMA with period 5
        val sma = maCalculator.calculateSMA(closes, period = 5)
        println("SMA values: ${sma.takeLast(5)}")
        // Expected: [20.0, 21.0, 22.0, 23.0, 24.0]

        // Calculate EMA with period 5
        val ema = maCalculator.calculateEMA(closes, period = 5)
        println("EMA values: ${ema.takeLast(5)}")
        // Expected: EMA gives more weight to recent prices
    }

    fun exampleMacdCalculation() {
        val maCalculator = MovingAverageCalculatorImpl()
        val macdCalculator = MacdCalculatorImpl(maCalculator)

        val closes = List(50) { i -> 100.0 + i * 0.5 } // Uptrend

        // Calculate MACD with default parameters (12, 26, 9)
        val macdResult = macdCalculator.calculate(
            closes = closes,
            fastPeriod = 12,
            slowPeriod = 26,
            signalPeriod = 9
        )

        println("MACD Line: ${macdResult.macdLine.takeLast(3)}")
        println("Signal Line: ${macdResult.signalLine.takeLast(3)}")
        println("Histogram: ${macdResult.histogram.takeLast(3)}")
        // Positive histogram indicates bullish momentum
    }

    fun exampleBollingerBandsCalculation() {
        val maCalculator = MovingAverageCalculatorImpl()
        val bbCalculator = BollingerBandsCalculatorImpl(maCalculator)

        val closes = List(30) { i -> 100.0 + (i % 10) - 5 } // Oscillating

        // Calculate Bollinger Bands with period 20, stdDev 2.0
        val bbResult = bbCalculator.calculate(
            closes = closes,
            period = 20,
            stdDev = 2.0
        )

        println("Upper Band: ${bbResult.upperBand.takeLast(3)}")
        println("Middle Band: ${bbResult.middleBand.takeLast(3)}")
        println("Lower Band: ${bbResult.lowerBand.takeLast(3)}")
        // Price touching upper band suggests overbought
        // Price touching lower band suggests oversold
    }

    fun exampleAtrCalculation() {
        val atrCalculator = AtrCalculatorImpl()

        val highs = List(20) { i -> 100.0 + i + 2.0 }
        val lows = List(20) { i -> 100.0 + i - 2.0 }
        val closes = List(20) { i -> 100.0 + i }

        // Calculate ATR with period 14
        val atrValues = atrCalculator.calculate(
            highs = highs,
            lows = lows,
            closes = closes,
            period = 14
        )

        println("ATR values: ${atrValues.takeLast(3)}")
        // Higher ATR indicates higher volatility
    }

    fun exampleStochasticCalculation() {
        val maCalculator = MovingAverageCalculatorImpl()
        val stochCalculator = StochasticCalculatorImpl(maCalculator)

        val highs = List(20) { i -> 105.0 + (i % 5) }
        val lows = List(20) { i -> 95.0 + (i % 5) }
        val closes = List(20) { i -> 100.0 + (i % 5) }

        // Calculate Stochastic with kPeriod 14, dPeriod 3
        val stochResult = stochCalculator.calculate(
            highs = highs,
            lows = lows,
            closes = closes,
            kPeriod = 14,
            dPeriod = 3
        )

        println("%K Line: ${stochResult.kLine.takeLast(3)}")
        println("%D Line: ${stochResult.dLine.takeLast(3)}")
        // Values above 80 indicate overbought
        // Values below 20 indicate oversold
    }

    fun exampleVolumeIndicatorCalculation() {
        val maCalculator = MovingAverageCalculatorImpl()
        val volumeCalculator = VolumeIndicatorCalculatorImpl(maCalculator)

        val volumes = listOf(
            1000.0, 1200.0, 1100.0, 1300.0, 1500.0, 1400.0, 1600.0,
            1800.0, 2000.0, 2200.0, 2100.0, 1900.0, 1700.0, 1500.0
        )

        // Calculate average volume with period 10
        val avgVolume = volumeCalculator.calculateAverageVolume(volumes, period = 10)
        println("Average Volume: ${avgVolume.takeLast(3)}")

        // Calculate volume ratio
        val volumeRatio = volumeCalculator.calculateVolumeRatio(volumes, period = 10)
        println("Volume Ratio: ${volumeRatio.takeLast(3)}")
        // Ratio > 1.0 means current volume is above average
    }

    fun exampleWithCaching() {
        val rsiCalculator = RsiCalculatorImpl()

        val closes = List(100) { i -> 100.0 + i * 0.1 }
        val period = 14

        // Generate cache key
        val cacheKey = cache.generateKey(
            indicatorName = "RSI",
            parameters = mapOf("period" to period),
            dataHash = cache.hashData(closes)
        )

        // Get or calculate RSI with caching
        val rsiValues = cache.getOrPut(cacheKey) {
            println("Calculating RSI (not cached)")
            rsiCalculator.calculate(closes, period)
        }

        // Second call will use cached value
        val cachedRsiValues = cache.getOrPut(cacheKey) {
            println("This won't be printed - using cache")
            rsiCalculator.calculate(closes, period)
        }

        println("Cache hit: ${rsiValues === cachedRsiValues}")
        println("Cache size: ${cache.size()}")
    }

    fun exampleWithCandles() {
        // Create candle data
        val candles = listOf(
            Candle(
                timestamp = 1699564800000,
                open = 100.0,
                high = 105.0,
                low = 99.0,
                close = 103.0,
                volume = 1000.0
            ),
            Candle(
                timestamp = 1699568400000,
                open = 103.0,
                high = 108.0,
                low = 102.0,
                close = 107.0,
                volume = 1200.0
            )
            // ... more candles
        )

        // Extract data for calculations
        val closes = candles.map { it.close }
        val highs = candles.map { it.high }
        val lows = candles.map { it.low }
        val volumes = candles.map { it.volume }

        // Now use with any calculator
        val rsiCalculator = RsiCalculatorImpl()
        val rsiValues = rsiCalculator.calculate(closes, period = 14)

        println("RSI from candles: ${rsiValues.lastOrNull()}")
    }
}
