package com.cryptotrader.domain.trading

import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Technical indicators for trading strategies
 * All calculations follow industry-standard formulas
 */
object TechnicalIndicators {

    /**
     * Simple Moving Average (SMA)
     * @param prices List of prices (most recent last)
     * @param period Number of periods
     */
    fun calculateSMA(prices: List<Double>, period: Int): Double? {
        if (prices.size < period) return null
        return prices.takeLast(period).average()
    }

    /**
     * Exponential Moving Average (EMA)
     * @param prices List of prices (most recent last)
     * @param period Number of periods
     */
    fun calculateEMA(prices: List<Double>, period: Int): Double? {
        if (prices.size < period) return null

        val multiplier = 2.0 / (period + 1)
        var ema = prices.take(period).average()

        prices.drop(period).forEach { price ->
            ema = (price - ema) * multiplier + ema
        }

        return ema
    }

    /**
     * Relative Strength Index (RSI)
     * @param prices List of closing prices
     * @param period Typically 14
     * @return RSI value (0-100)
     */
    fun calculateRSI(prices: List<Double>, period: Int = 14): Double? {
        if (prices.size < period + 1) return null

        var gains = 0.0
        var losses = 0.0

        // Calculate initial average gains and losses
        for (i in 1 until period + 1) {
            val change = prices[i] - prices[i - 1]
            if (change > 0) gains += change else losses -= change
        }

        var avgGain = gains / period
        var avgLoss = losses / period

        // Continue with smoothed averages
        for (i in period + 1 until prices.size) {
            val change = prices[i] - prices[i - 1]
            if (change > 0) {
                avgGain = (avgGain * (period - 1) + change) / period
                avgLoss = (avgLoss * (period - 1)) / period
            } else {
                avgGain = (avgGain * (period - 1)) / period
                avgLoss = (avgLoss * (period - 1) - change) / period
            }
        }

        if (avgLoss == 0.0) return 100.0

        val rs = avgGain / avgLoss
        return 100.0 - (100.0 / (1.0 + rs))
    }

    /**
     * Moving Average Convergence Divergence (MACD)
     * @return Triple of (MACD line, Signal line, Histogram)
     */
    fun calculateMACD(
        prices: List<Double>,
        fastPeriod: Int = 12,
        slowPeriod: Int = 26,
        signalPeriod: Int = 9
    ): Triple<Double, Double, Double>? {
        if (prices.size < slowPeriod) return null

        val fastEMA = calculateEMA(prices, fastPeriod) ?: return null
        val slowEMA = calculateEMA(prices, slowPeriod) ?: return null

        val macdLine = fastEMA - slowEMA

        // Calculate signal line (EMA of MACD)
        val macdHistory = mutableListOf<Double>()
        for (i in slowPeriod until prices.size) {
            val fast = calculateEMA(prices.take(i + 1), fastPeriod) ?: continue
            val slow = calculateEMA(prices.take(i + 1), slowPeriod) ?: continue
            macdHistory.add(fast - slow)
        }

        val signalLine = calculateEMA(macdHistory, signalPeriod) ?: return null
        val histogram = macdLine - signalLine

        return Triple(macdLine, signalLine, histogram)
    }

    /**
     * Bollinger Bands
     * @return Triple of (Upper band, Middle band, Lower band)
     */
    fun calculateBollingerBands(
        prices: List<Double>,
        period: Int = 20,
        stdDevMultiplier: Double = 2.0
    ): Triple<Double, Double, Double>? {
        if (prices.size < period) return null

        val sma = calculateSMA(prices, period) ?: return null
        val recentPrices = prices.takeLast(period)

        // Calculate standard deviation
        val variance = recentPrices.map { (it - sma).pow(2) }.average()
        val stdDev = sqrt(variance)

        val upperBand = sma + (stdDevMultiplier * stdDev)
        val lowerBand = sma - (stdDevMultiplier * stdDev)

        return Triple(upperBand, sma, lowerBand)
    }

    /**
     * Stochastic Oscillator
     * @return Pair of (%K, %D)
     */
    fun calculateStochastic(
        highs: List<Double>,
        lows: List<Double>,
        closes: List<Double>,
        kPeriod: Int = 14,
        dPeriod: Int = 3
    ): Pair<Double, Double>? {
        if (highs.size < kPeriod || lows.size < kPeriod || closes.size < kPeriod) return null

        val recentHighs = highs.takeLast(kPeriod)
        val recentLows = lows.takeLast(kPeriod)
        val currentClose = closes.last()

        val highestHigh = recentHighs.maxOrNull() ?: return null
        val lowestLow = recentLows.minOrNull() ?: return null

        val kValue = if (highestHigh == lowestLow) {
            50.0
        } else {
            ((currentClose - lowestLow) / (highestHigh - lowestLow)) * 100.0
        }

        // %D is SMA of %K
        val kValues = mutableListOf<Double>()
        for (i in kPeriod - 1 until closes.size) {
            val h = highs.slice(i - kPeriod + 1..i).maxOrNull() ?: continue
            val l = lows.slice(i - kPeriod + 1..i).minOrNull() ?: continue
            val c = closes[i]
            if (h != l) {
                kValues.add(((c - l) / (h - l)) * 100.0)
            }
        }

        val dValue = if (kValues.size >= dPeriod) {
            kValues.takeLast(dPeriod).average()
        } else {
            kValue
        }

        return Pair(kValue, dValue)
    }

    /**
     * Average True Range (ATR) - Volatility indicator
     */
    fun calculateATR(
        highs: List<Double>,
        lows: List<Double>,
        closes: List<Double>,
        period: Int = 14
    ): Double? {
        if (highs.size < period + 1 || lows.size < period + 1 || closes.size < period + 1) {
            return null
        }

        val trueRanges = mutableListOf<Double>()

        for (i in 1 until highs.size) {
            val high = highs[i]
            val low = lows[i]
            val previousClose = closes[i - 1]

            val tr = maxOf(
                high - low,
                kotlin.math.abs(high - previousClose),
                kotlin.math.abs(low - previousClose)
            )
            trueRanges.add(tr)
        }

        return if (trueRanges.size >= period) {
            trueRanges.takeLast(period).average()
        } else {
            null
        }
    }

    /**
     * Volume Weighted Average Price (VWAP)
     */
    fun calculateVWAP(
        prices: List<Double>,
        volumes: List<Double>
    ): Double? {
        if (prices.size != volumes.size || prices.isEmpty()) return null

        var totalPV = 0.0
        var totalVolume = 0.0

        for (i in prices.indices) {
            totalPV += prices[i] * volumes[i]
            totalVolume += volumes[i]
        }

        return if (totalVolume > 0) totalPV / totalVolume else null
    }
}
