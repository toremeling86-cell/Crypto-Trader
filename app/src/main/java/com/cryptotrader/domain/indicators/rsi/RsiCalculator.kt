package com.cryptotrader.domain.indicators.rsi

/**
 * Calculator for Relative Strength Index (RSI)
 *
 * RSI is a momentum oscillator that measures the speed and magnitude of price changes.
 * Values range from 0 to 100, with readings above 70 indicating overbought conditions
 * and readings below 30 indicating oversold conditions.
 */
interface RsiCalculator {
    /**
     * Calculates RSI values for a series of closing prices
     *
     * @param closes List of closing prices (must have at least period + 1 values)
     * @param period The lookback period for RSI calculation (default: 14)
     * @return List of RSI values (first 'period' values will be null due to insufficient data)
     */
    fun calculate(closes: List<Double>, period: Int = 14): List<Double?>
}
