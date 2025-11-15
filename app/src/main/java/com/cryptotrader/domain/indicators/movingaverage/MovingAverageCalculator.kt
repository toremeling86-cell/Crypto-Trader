package com.cryptotrader.domain.indicators.movingaverage

/**
 * Calculator for Moving Averages (SMA and EMA)
 *
 * Moving averages are used to smooth price data and identify trends.
 */
interface MovingAverageCalculator {
    /**
     * Calculates Simple Moving Average (SMA)
     *
     * SMA is the arithmetic mean of the last N data points.
     *
     * @param data List of data points (typically closing prices)
     * @param period The number of periods to average
     * @return List of SMA values (first 'period - 1' values will be null)
     */
    fun calculateSMA(data: List<Double>, period: Int): List<Double?>

    /**
     * Calculates Exponential Moving Average (EMA)
     *
     * EMA gives more weight to recent prices using an exponential decay factor.
     *
     * @param data List of data points (typically closing prices)
     * @param period The number of periods for the EMA
     * @return List of EMA values (first value uses SMA as seed)
     */
    fun calculateEMA(data: List<Double>, period: Int): List<Double?>
}
