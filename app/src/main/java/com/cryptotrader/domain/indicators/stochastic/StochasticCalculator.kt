package com.cryptotrader.domain.indicators.stochastic

/**
 * Calculator for Stochastic Oscillator
 *
 * The Stochastic Oscillator is a momentum indicator that shows the location
 * of the close relative to the high-low range over a set number of periods.
 */
interface StochasticCalculator {
    /**
     * Calculates Stochastic Oscillator values
     *
     * @param highs List of high prices
     * @param lows List of low prices
     * @param closes List of closing prices
     * @param kPeriod The period for %K calculation (default: 14)
     * @param dPeriod The period for %D smoothing (default: 3)
     * @return StochasticResult containing %K and %D lines
     */
    fun calculate(
        highs: List<Double>,
        lows: List<Double>,
        closes: List<Double>,
        kPeriod: Int = 14,
        dPeriod: Int = 3
    ): StochasticResult
}

/**
 * Result of Stochastic Oscillator calculation
 *
 * @property kLine The %K line (fast stochastic)
 * @property dLine The %D line (slow stochastic, SMA of %K)
 */
data class StochasticResult(
    val kLine: List<Double?>,
    val dLine: List<Double?>
)
