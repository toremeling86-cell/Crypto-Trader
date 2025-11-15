package com.cryptotrader.domain.indicators.atr

/**
 * Calculator for ATR (Average True Range)
 *
 * ATR measures market volatility by calculating the average of true ranges
 * over a specified period. True Range is the greatest of:
 * - Current High - Current Low
 * - Absolute value of Current High - Previous Close
 * - Absolute value of Current Low - Previous Close
 */
interface AtrCalculator {
    /**
     * Calculates ATR values
     *
     * @param highs List of high prices
     * @param lows List of low prices
     * @param closes List of closing prices
     * @param period The lookback period for ATR calculation (default: 14)
     * @return List of ATR values (first value will be null due to requiring previous close)
     */
    fun calculate(
        highs: List<Double>,
        lows: List<Double>,
        closes: List<Double>,
        period: Int = 14
    ): List<Double?>
}
