package com.cryptotrader.domain.indicators.movingaverage

import com.cryptotrader.domain.indicators.IndicatorCalculator

/**
 * Implementation of Simple and Exponential Moving Average calculators
 */
class MovingAverageCalculatorImpl : MovingAverageCalculator, IndicatorCalculator {

    override fun calculateSMA(data: List<Double>, period: Int): List<Double?> {
        require(period > 0) { "Period must be positive" }

        if (data.size < period) {
            return List(data.size) { null }
        }

        val smaValues = mutableListOf<Double?>()

        // First 'period - 1' values are null
        repeat(period - 1) {
            smaValues.add(null)
        }

        // Calculate SMA using sliding window
        var sum = 0.0
        for (i in 0 until period) {
            sum += data[i]
        }
        smaValues.add(sum / period)

        // Slide the window for remaining values
        for (i in period until data.size) {
            sum = sum - data[i - period] + data[i]
            smaValues.add(sum / period)
        }

        return smaValues
    }

    override fun calculateEMA(data: List<Double>, period: Int): List<Double?> {
        require(period > 0) { "Period must be positive" }

        if (data.isEmpty()) {
            return emptyList()
        }

        if (data.size < period) {
            return List(data.size) { null }
        }

        val emaValues = mutableListOf<Double?>()
        val multiplier = 2.0 / (period + 1.0)

        // First 'period - 1' values are null
        repeat(period - 1) {
            emaValues.add(null)
        }

        // Calculate first EMA value using SMA as seed
        var ema = 0.0
        for (i in 0 until period) {
            ema += data[i]
        }
        ema /= period
        emaValues.add(ema)

        // Calculate subsequent EMA values
        // EMA = (Close - EMA_previous) * multiplier + EMA_previous
        for (i in period until data.size) {
            ema = (data[i] - ema) * multiplier + ema
            emaValues.add(ema)
        }

        return emaValues
    }
}
