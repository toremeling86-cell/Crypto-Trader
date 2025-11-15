package com.cryptotrader.domain.indicators.bollingerbands

/**
 * Calculator for Bollinger Bands
 *
 * Bollinger Bands consist of a middle band (SMA) with upper and lower bands
 * that are standard deviations away from the middle band.
 */
interface BollingerBandsCalculator {
    /**
     * Calculates Bollinger Bands
     *
     * @param closes List of closing prices
     * @param period The period for SMA calculation (default: 20)
     * @param stdDev Number of standard deviations for bands (default: 2.0)
     * @return BollingerBandsResult containing upper, middle, and lower bands
     */
    fun calculate(
        closes: List<Double>,
        period: Int = 20,
        stdDev: Double = 2.0
    ): BollingerBandsResult
}

/**
 * Result of Bollinger Bands calculation
 *
 * @property upperBand Upper Bollinger Band (SMA + stdDev * standard deviation)
 * @property middleBand Middle Bollinger Band (SMA)
 * @property lowerBand Lower Bollinger Band (SMA - stdDev * standard deviation)
 */
data class BollingerBandsResult(
    val upperBand: List<Double?>,
    val middleBand: List<Double?>,
    val lowerBand: List<Double?>
)
