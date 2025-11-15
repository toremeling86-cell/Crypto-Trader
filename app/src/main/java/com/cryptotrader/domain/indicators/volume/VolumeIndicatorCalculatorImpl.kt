package com.cryptotrader.domain.indicators.volume

import com.cryptotrader.domain.indicators.IndicatorCalculator
import com.cryptotrader.domain.indicators.movingaverage.MovingAverageCalculator
import com.cryptotrader.domain.indicators.movingaverage.MovingAverageCalculatorImpl

/**
 * Implementation of Volume Indicators calculator
 */
class VolumeIndicatorCalculatorImpl(
    private val maCalculator: MovingAverageCalculator = MovingAverageCalculatorImpl()
) : VolumeIndicatorCalculator, IndicatorCalculator {

    override fun calculateAverageVolume(volumes: List<Double>, period: Int): List<Double?> {
        require(period > 0) { "Period must be positive" }

        // Average volume is simply the SMA of volume
        return maCalculator.calculateSMA(volumes, period)
    }

    override fun calculateVolumeRatio(volumes: List<Double>, period: Int): List<Double?> {
        require(period > 0) { "Period must be positive" }

        if (volumes.size < period) {
            return List(volumes.size) { null }
        }

        // Calculate average volume
        val avgVolumes = calculateAverageVolume(volumes, period)

        // Calculate ratio: current volume / average volume
        val ratios = mutableListOf<Double?>()

        for (i in volumes.indices) {
            val avgVolume = avgVolumes[i]
            if (avgVolume != null && avgVolume > 0) {
                ratios.add(volumes[i] / avgVolume)
            } else {
                ratios.add(null)
            }
        }

        return ratios
    }
}
