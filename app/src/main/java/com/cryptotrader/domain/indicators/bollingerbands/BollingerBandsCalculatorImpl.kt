package com.cryptotrader.domain.indicators.bollingerbands

import com.cryptotrader.domain.indicators.IndicatorCalculator
import com.cryptotrader.domain.indicators.movingaverage.MovingAverageCalculator
import com.cryptotrader.domain.indicators.movingaverage.MovingAverageCalculatorImpl
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Implementation of Bollinger Bands calculator
 *
 * Upper Band = SMA + (stdDev * standard deviation)
 * Middle Band = SMA
 * Lower Band = SMA - (stdDev * standard deviation)
 */
class BollingerBandsCalculatorImpl(
    private val maCalculator: MovingAverageCalculator = MovingAverageCalculatorImpl()
) : BollingerBandsCalculator, IndicatorCalculator {

    override fun calculate(
        closes: List<Double>,
        period: Int,
        stdDev: Double
    ): BollingerBandsResult {
        require(period > 0) { "Period must be positive" }
        require(stdDev > 0) { "Standard deviation multiplier must be positive" }

        if (closes.size < period) {
            return BollingerBandsResult(
                upperBand = List(closes.size) { null },
                middleBand = List(closes.size) { null },
                lowerBand = List(closes.size) { null }
            )
        }

        // Calculate middle band (SMA)
        val middleBand = maCalculator.calculateSMA(closes, period)

        val upperBand = mutableListOf<Double?>()
        val lowerBand = mutableListOf<Double?>()

        // First 'period - 1' values are null
        repeat(period - 1) {
            upperBand.add(null)
            lowerBand.add(null)
        }

        // Calculate standard deviation and bands for each valid window
        for (i in period - 1 until closes.size) {
            val windowStart = i - period + 1
            val window = closes.subList(windowStart, i + 1)
            val sma = middleBand[i]!!

            // Calculate standard deviation
            val variance = window.map { (it - sma).pow(2) }.average()
            val standardDeviation = sqrt(variance)

            // Calculate bands
            upperBand.add(sma + stdDev * standardDeviation)
            lowerBand.add(sma - stdDev * standardDeviation)
        }

        return BollingerBandsResult(
            upperBand = upperBand,
            middleBand = middleBand,
            lowerBand = lowerBand
        )
    }
}
