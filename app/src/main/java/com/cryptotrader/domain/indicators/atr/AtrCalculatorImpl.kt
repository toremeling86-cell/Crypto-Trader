package com.cryptotrader.domain.indicators.atr

import com.cryptotrader.domain.indicators.IndicatorCalculator
import kotlin.math.abs
import kotlin.math.max

/**
 * Implementation of ATR (Average True Range) calculator
 *
 * True Range = max(high - low, abs(high - previous close), abs(low - previous close))
 * ATR = Wilder's smoothed average of True Range
 */
class AtrCalculatorImpl : AtrCalculator, IndicatorCalculator {

    override fun calculate(
        highs: List<Double>,
        lows: List<Double>,
        closes: List<Double>,
        period: Int
    ): List<Double?> {
        require(period > 0) { "Period must be positive" }
        require(highs.size == lows.size && lows.size == closes.size) {
            "Highs, lows, and closes must have the same size"
        }

        if (closes.size <= period) {
            return List(closes.size) { null }
        }

        val atrValues = mutableListOf<Double?>()

        // First value is null (no previous close)
        atrValues.add(null)

        // Calculate True Range for each period
        val trueRanges = mutableListOf<Double>()
        for (i in 1 until closes.size) {
            val tr = calculateTrueRange(
                high = highs[i],
                low = lows[i],
                previousClose = closes[i - 1]
            )
            trueRanges.add(tr)
        }

        // Need at least 'period' true ranges to calculate first ATR
        if (trueRanges.size < period) {
            repeat(closes.size - 1) {
                atrValues.add(null)
            }
            return atrValues
        }

        // Calculate first ATR using simple average
        var atr = 0.0
        for (i in 0 until period) {
            atr += trueRanges[i]
        }
        atr /= period

        // Add nulls for the period before first ATR value
        repeat(period - 1) {
            atrValues.add(null)
        }
        atrValues.add(atr)

        // Calculate subsequent ATR values using Wilder's smoothing
        // ATR = ((previous ATR * (period - 1)) + current TR) / period
        for (i in period until trueRanges.size) {
            atr = ((atr * (period - 1)) + trueRanges[i]) / period
            atrValues.add(atr)
        }

        return atrValues
    }

    /**
     * Calculates True Range for a single period
     *
     * @param high Current high price
     * @param low Current low price
     * @param previousClose Previous closing price
     * @return True Range value
     */
    private fun calculateTrueRange(high: Double, low: Double, previousClose: Double): Double {
        val range1 = high - low
        val range2 = abs(high - previousClose)
        val range3 = abs(low - previousClose)

        return max(range1, max(range2, range3))
    }
}
