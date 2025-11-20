package com.cryptotrader.domain.indicators.rsi

import com.cryptotrader.domain.indicators.IndicatorCalculator
import com.cryptotrader.utils.toBigDecimalMoney
import com.cryptotrader.utils.safeDiv
import com.cryptotrader.utils.abs
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

    override fun calculateDecimal(closes: List<java.math.BigDecimal>, period: Int): List<java.math.BigDecimal?> {
        require(period > 0) { "Period must be positive" }

        if (closes.size <= period) {
            return List(closes.size) { null }
        }

        val rsiValues = mutableListOf<java.math.BigDecimal?>()
        val periodBD = period.toBigDecimalMoney()
        val periodMinusOneBD = (period - 1).toBigDecimalMoney()
        val hundredBD = com.cryptotrader.utils.BigDecimalConstants.HUNDRED
        val oneBD = com.cryptotrader.utils.BigDecimalConstants.ONE

        // Calculate price changes
        val changes = mutableListOf<java.math.BigDecimal>()
        for (i in 1 until closes.size) {
            changes.add(closes[i] - closes[i - 1])
        }

        // First 'period' RSI values are null
        repeat(period) {
            rsiValues.add(null)
        }

        // Calculate initial average gain and loss (simple average)
        var avgGain = java.math.BigDecimal.ZERO
        var avgLoss = java.math.BigDecimal.ZERO

        for (i in 0 until period) {
            val change = changes[i]
            if (change > java.math.BigDecimal.ZERO) {
                avgGain += change
            } else {
                avgLoss += change.abs()
            }
        }

        avgGain = avgGain safeDiv periodBD
        avgLoss = avgLoss safeDiv periodBD

        // Calculate first RSI value
        val firstRsi = if (avgLoss.compareTo(java.math.BigDecimal.ZERO) == 0) {
            hundredBD
        } else {
            val rs = avgGain safeDiv avgLoss
            hundredBD - (hundredBD safeDiv (oneBD + rs))
        }
        rsiValues.add(firstRsi)

        // Calculate subsequent RSI values using Wilder's smoothing
        for (i in period until changes.size) {
            val change = changes[i]
            val gain = if (change > java.math.BigDecimal.ZERO) change else java.math.BigDecimal.ZERO
            val loss = if (change < java.math.BigDecimal.ZERO) change.abs() else java.math.BigDecimal.ZERO

            // Wilder's smoothing: new average = (previous average * (period - 1) + current value) / period
            avgGain = ((avgGain * periodMinusOneBD) + gain) safeDiv periodBD
            avgLoss = ((avgLoss * periodMinusOneBD) + loss) safeDiv periodBD

            val rsi = if (avgLoss.compareTo(java.math.BigDecimal.ZERO) == 0) {
                hundredBD
            } else {
                val rs = avgGain safeDiv avgLoss
                hundredBD - (hundredBD safeDiv (oneBD + rs))
            }

            rsiValues.add(rsi)
        }

        return rsiValues
    }
}
