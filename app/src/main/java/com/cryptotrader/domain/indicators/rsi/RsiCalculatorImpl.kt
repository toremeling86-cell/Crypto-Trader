package com.cryptotrader.domain.indicators.rsi

import com.cryptotrader.domain.indicators.IndicatorCalculator
import kotlin.math.abs

/**
 * Implementation of RSI (Relative Strength Index) calculator
 *
 * Formula: RSI = 100 - (100 / (1 + RS))
 * Where RS = Average Gain / Average Loss
 *
 * Uses Wilder's smoothing method (similar to EMA) for calculating average gain/loss
 */
class RsiCalculatorImpl : RsiCalculator, IndicatorCalculator {

    override fun calculate(closes: List<Double>, period: Int): List<Double?> {
        require(period > 0) { "Period must be positive" }

        if (closes.size <= period) {
            return List(closes.size) { null }
        }

        val rsiValues = mutableListOf<Double?>()

        // Calculate price changes
        val changes = mutableListOf<Double>()
        for (i in 1 until closes.size) {
            changes.add(closes[i] - closes[i - 1])
        }

        // First 'period' RSI values are null
        repeat(period) {
            rsiValues.add(null)
        }

        // Calculate initial average gain and loss (simple average)
        var avgGain = 0.0
        var avgLoss = 0.0

        for (i in 0 until period) {
            val change = changes[i]
            if (change > 0) {
                avgGain += change
            } else {
                avgLoss += abs(change)
            }
        }

        avgGain /= period
        avgLoss /= period

        // Calculate first RSI value
        val firstRsi = if (avgLoss == 0.0) {
            100.0
        } else {
            val rs = avgGain / avgLoss
            100.0 - (100.0 / (1.0 + rs))
        }
        rsiValues.add(firstRsi)

        // Calculate subsequent RSI values using Wilder's smoothing
        for (i in period until changes.size) {
            val change = changes[i]
            val gain = if (change > 0) change else 0.0
            val loss = if (change < 0) abs(change) else 0.0

            // Wilder's smoothing: new average = (previous average * (period - 1) + current value) / period
            avgGain = (avgGain * (period - 1) + gain) / period
            avgLoss = (avgLoss * (period - 1) + loss) / period

            val rsi = if (avgLoss == 0.0) {
                100.0
            } else {
                val rs = avgGain / avgLoss
                100.0 - (100.0 / (1.0 + rs))
            }

            rsiValues.add(rsi)
        }

        return rsiValues
    }
}
