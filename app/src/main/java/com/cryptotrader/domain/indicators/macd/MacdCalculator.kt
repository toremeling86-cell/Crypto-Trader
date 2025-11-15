package com.cryptotrader.domain.indicators.macd

/**
 * Calculator for MACD (Moving Average Convergence Divergence)
 *
 * MACD is a trend-following momentum indicator that shows the relationship
 * between two moving averages of a security's price.
 */
interface MacdCalculator {
    /**
     * Calculates MACD indicator components
     *
     * @param closes List of closing prices
     * @param fastPeriod Period for fast EMA (default: 12)
     * @param slowPeriod Period for slow EMA (default: 26)
     * @param signalPeriod Period for signal line EMA (default: 9)
     * @return MacdResult containing MACD line, signal line, and histogram
     */
    fun calculate(
        closes: List<Double>,
        fastPeriod: Int = 12,
        slowPeriod: Int = 26,
        signalPeriod: Int = 9
    ): MacdResult
}

/**
 * Result of MACD calculation
 *
 * @property macdLine The MACD line (fast EMA - slow EMA)
 * @property signalLine The signal line (EMA of MACD line)
 * @property histogram The MACD histogram (MACD line - signal line)
 */
data class MacdResult(
    val macdLine: List<Double?>,
    val signalLine: List<Double?>,
    val histogram: List<Double?>
)
