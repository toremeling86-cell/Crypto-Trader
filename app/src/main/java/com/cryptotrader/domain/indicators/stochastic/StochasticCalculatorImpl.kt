package com.cryptotrader.domain.indicators.stochastic

import com.cryptotrader.domain.indicators.IndicatorCalculator
import com.cryptotrader.domain.indicators.movingaverage.MovingAverageCalculator
import com.cryptotrader.domain.indicators.movingaverage.MovingAverageCalculatorImpl

/**
 * Implementation of Stochastic Oscillator calculator
 *
 * %K = ((Current Close - Lowest Low) / (Highest High - Lowest Low)) * 100
 * %D = SMA of %K
 */
class StochasticCalculatorImpl(
    private val maCalculator: MovingAverageCalculator = MovingAverageCalculatorImpl()
) : StochasticCalculator, IndicatorCalculator {

    override fun calculate(
        highs: List<Double>,
        lows: List<Double>,
        closes: List<Double>,
        kPeriod: Int,
        dPeriod: Int
    ): StochasticResult {
        require(kPeriod > 0) { "K period must be positive" }
        require(dPeriod > 0) { "D period must be positive" }
        require(highs.size == lows.size && lows.size == closes.size) {
            "Highs, lows, and closes must have the same size"
        }

        if (closes.size < kPeriod) {
            return StochasticResult(
                kLine = List(closes.size) { null },
                dLine = List(closes.size) { null }
            )
        }

        val kLine = mutableListOf<Double?>()

        // First 'kPeriod - 1' values are null
        repeat(kPeriod - 1) {
            kLine.add(null)
        }

        // Calculate %K for each valid window
        for (i in kPeriod - 1 until closes.size) {
            val windowStart = i - kPeriod + 1
            val highestHigh = highs.subList(windowStart, i + 1).maxOrNull()!!
            val lowestLow = lows.subList(windowStart, i + 1).minOrNull()!!
            val currentClose = closes[i]

            val k = if (highestHigh == lowestLow) {
                // Avoid division by zero
                50.0
            } else {
                ((currentClose - lowestLow) / (highestHigh - lowestLow)) * 100.0
            }

            kLine.add(k)
        }

        // Calculate %D (SMA of %K)
        val kValues = kLine.filterNotNull()
        val dValues = maCalculator.calculateSMA(kValues, dPeriod)

        // Align %D line with original data
        val dLine = mutableListOf<Double?>()
        val firstKIndex = kLine.indexOfFirst { it != null }

        repeat(firstKIndex) {
            dLine.add(null)
        }
        dLine.addAll(dValues)

        return StochasticResult(
            kLine = kLine,
            dLine = dLine
        )
    }
}
