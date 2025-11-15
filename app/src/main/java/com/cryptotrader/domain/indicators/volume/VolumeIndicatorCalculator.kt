package com.cryptotrader.domain.indicators.volume

/**
 * Calculator for Volume-based indicators
 *
 * Volume indicators help analyze trading volume patterns and trends.
 */
interface VolumeIndicatorCalculator {
    /**
     * Calculates average volume over a period
     *
     * @param volumes List of volume values
     * @param period The lookback period (default: 20)
     * @return List of average volume values
     */
    fun calculateAverageVolume(volumes: List<Double>, period: Int = 20): List<Double?>

    /**
     * Calculates volume ratio (current volume / average volume)
     *
     * Values above 1.0 indicate higher than average volume
     * Values below 1.0 indicate lower than average volume
     *
     * @param volumes List of volume values
     * @param period The lookback period for average calculation (default: 20)
     * @return List of volume ratio values
     */
    fun calculateVolumeRatio(volumes: List<Double>, period: Int = 20): List<Double?>
}
