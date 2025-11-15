package com.cryptotrader.domain.indicators.macd

import com.cryptotrader.domain.indicators.IndicatorCalculator
import com.cryptotrader.domain.indicators.movingaverage.MovingAverageCalculator
import com.cryptotrader.domain.indicators.movingaverage.MovingAverageCalculatorImpl

/**
 * Implementation of MACD (Moving Average Convergence Divergence) calculator
 *
 * MACD Line = Fast EMA - Slow EMA
 * Signal Line = EMA of MACD Line
 * Histogram = MACD Line - Signal Line
 */
class MacdCalculatorImpl(
    private val maCalculator: MovingAverageCalculator = MovingAverageCalculatorImpl()
) : MacdCalculator, IndicatorCalculator {

    override fun calculate(
        closes: List<Double>,
        fastPeriod: Int,
        slowPeriod: Int,
        signalPeriod: Int
    ): MacdResult {
        require(fastPeriod > 0) { "Fast period must be positive" }
        require(slowPeriod > 0) { "Slow period must be positive" }
        require(signalPeriod > 0) { "Signal period must be positive" }
        require(fastPeriod < slowPeriod) { "Fast period must be less than slow period" }

        if (closes.size < slowPeriod) {
            return MacdResult(
                macdLine = List(closes.size) { null },
                signalLine = List(closes.size) { null },
                histogram = List(closes.size) { null }
            )
        }

        // Calculate fast and slow EMAs
        val fastEma = maCalculator.calculateEMA(closes, fastPeriod)
        val slowEma = maCalculator.calculateEMA(closes, slowPeriod)

        // Calculate MACD line (fast EMA - slow EMA)
        val macdLine = mutableListOf<Double?>()
        for (i in closes.indices) {
            val fast = fastEma[i]
            val slow = slowEma[i]
            if (fast != null && slow != null) {
                macdLine.add(fast - slow)
            } else {
                macdLine.add(null)
            }
        }

        // Extract non-null MACD values for signal line calculation
        val macdValues = macdLine.filterNotNull()
        val firstMacdIndex = macdLine.indexOfFirst { it != null }

        if (macdValues.size < signalPeriod) {
            return MacdResult(
                macdLine = macdLine,
                signalLine = List(closes.size) { null },
                histogram = List(closes.size) { null }
            )
        }

        // Calculate signal line (EMA of MACD line)
        val signalEma = maCalculator.calculateEMA(macdValues, signalPeriod)

        // Align signal line with original data
        val signalLine = mutableListOf<Double?>()
        repeat(firstMacdIndex) {
            signalLine.add(null)
        }
        signalLine.addAll(signalEma)

        // Calculate histogram (MACD line - signal line)
        val histogram = mutableListOf<Double?>()
        for (i in closes.indices) {
            val macd = macdLine[i]
            val signal = signalLine.getOrNull(i)
            if (macd != null && signal != null) {
                histogram.add(macd - signal)
            } else {
                histogram.add(null)
            }
        }

        return MacdResult(
            macdLine = macdLine,
            signalLine = signalLine,
            histogram = histogram
        )
    }
}
