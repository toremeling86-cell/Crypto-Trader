package com.cryptotrader.domain.trading

import com.cryptotrader.data.repository.HistoricalDataRepository
import com.cryptotrader.domain.backtesting.PriceBar
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Volatility-adjusted stop-loss calculator using ATR (Average True Range)
 *
 * ATR measures market volatility and adjusts stop-loss dynamically:
 * - In high volatility: Wider stops to avoid being stopped out by noise
 * - In low volatility: Tighter stops to maximize risk/reward
 *
 * Formula: Stop Loss = Entry Price ± (ATR × Multiplier)
 */
@Singleton
class VolatilityStopLossCalculator @Inject constructor(
    private val historicalDataRepository: HistoricalDataRepository
) {

    companion object {
        private const val DEFAULT_ATR_PERIOD = 14
        private const val DEFAULT_ATR_MULTIPLIER = 2.0 // 2x ATR is standard
        private const val MIN_ATR_MULTIPLIER = 1.0
        private const val MAX_ATR_MULTIPLIER = 4.0
        private const val MIN_BARS_REQUIRED = 20
    }

    /**
     * Calculate volatility-adjusted stop-loss using ATR
     *
     * @param pair Trading pair
     * @param entryPrice Entry price
     * @param isBuy True for long position, false for short
     * @param atrMultiplier How many ATRs away from entry (default 2.0)
     * @param fallbackStopLossPercent Fixed stop-loss if ATR unavailable
     * @return Stop-loss price
     */
    suspend fun calculateVolatilityStopLoss(
        pair: String,
        entryPrice: Double,
        isBuy: Boolean,
        atrMultiplier: Double = DEFAULT_ATR_MULTIPLIER,
        fallbackStopLossPercent: Double = 2.0
    ): Double {
        try {
            // Validate multiplier
            val validatedMultiplier = atrMultiplier.coerceIn(MIN_ATR_MULTIPLIER, MAX_ATR_MULTIPLIER)

            // Fetch recent price data
            val barsResult = historicalDataRepository.getRecentBars(
                pair = pair,
                interval = 60, // 1-hour bars
                count = 50     // Need enough data for ATR calculation
            )

            if (barsResult.isFailure || barsResult.getOrNull().isNullOrEmpty()) {
                Timber.w("Failed to fetch price data for volatility stop-loss, using fallback")
                return calculateFallbackStopLoss(entryPrice, fallbackStopLossPercent, isBuy)
            }

            val bars = barsResult.getOrNull()!!
            if (bars.size < MIN_BARS_REQUIRED) {
                Timber.w("Insufficient data for ATR (${bars.size} bars), using fallback")
                return calculateFallbackStopLoss(entryPrice, fallbackStopLossPercent, isBuy)
            }

            // Calculate ATR
            val atr = calculateATR(bars)
            if (atr == null || atr <= 0.0) {
                Timber.w("Invalid ATR value, using fallback")
                return calculateFallbackStopLoss(entryPrice, fallbackStopLossPercent, isBuy)
            }

            // Calculate stop-loss based on ATR
            val stopLossDistance = atr * validatedMultiplier
            val stopLossPrice = if (isBuy) {
                entryPrice - stopLossDistance
            } else {
                entryPrice + stopLossDistance
            }

            // Calculate percentage for logging
            val stopLossPercent = (stopLossDistance / entryPrice) * 100.0

            Timber.i("Volatility stop-loss: pair=$pair, entry=$entryPrice, ATR=$atr, " +
                    "multiplier=$validatedMultiplier, stop=$stopLossPrice (${String.format("%.2f", stopLossPercent)}%)")

            return stopLossPrice

        } catch (e: Exception) {
            Timber.e(e, "Error calculating volatility stop-loss")
            return calculateFallbackStopLoss(entryPrice, fallbackStopLossPercent, isBuy)
        }
    }

    /**
     * Calculate ATR from price bars
     */
    private fun calculateATR(bars: List<PriceBar>): Double? {
        val highs = bars.map { it.high }
        val lows = bars.map { it.low }
        val closes = bars.map { it.close }

        return TechnicalIndicators.calculateATR(
            highs = highs,
            lows = lows,
            closes = closes,
            period = DEFAULT_ATR_PERIOD
        )
    }

    /**
     * Fallback stop-loss using fixed percentage
     */
    private fun calculateFallbackStopLoss(
        entryPrice: Double,
        stopLossPercent: Double,
        isBuy: Boolean
    ): Double {
        return if (isBuy) {
            entryPrice * (1.0 - stopLossPercent / 100.0)
        } else {
            entryPrice * (1.0 + stopLossPercent / 100.0)
        }
    }

    /**
     * Get current ATR value for a trading pair
     */
    suspend fun getCurrentATR(pair: String, interval: Int = 60): Double? {
        try {
            val barsResult = historicalDataRepository.getRecentBars(
                pair = pair,
                interval = interval,
                count = 50
            )

            if (barsResult.isSuccess) {
                val bars = barsResult.getOrNull()!!
                if (bars.size >= MIN_BARS_REQUIRED) {
                    return calculateATR(bars)
                }
            }

            return null
        } catch (e: Exception) {
            Timber.e(e, "Error getting current ATR")
            return null
        }
    }

    /**
     * Calculate optimal ATR multiplier based on market conditions
     * Returns higher multiplier in trending markets, lower in ranging markets
     */
    suspend fun calculateOptimalATRMultiplier(pair: String): Double {
        try {
            val barsResult = historicalDataRepository.getRecentBars(
                pair = pair,
                interval = 60,
                count = 100
            )

            if (barsResult.isFailure) {
                return DEFAULT_ATR_MULTIPLIER
            }

            val bars = barsResult.getOrNull()!!
            if (bars.size < 50) {
                return DEFAULT_ATR_MULTIPLIER
            }

            // Calculate price trend strength
            val prices = bars.map { it.close }
            val shortMA = TechnicalIndicators.calculateSMA(prices, 10)
            val longMA = TechnicalIndicators.calculateSMA(prices, 30)

            if (shortMA == null || longMA == null) {
                return DEFAULT_ATR_MULTIPLIER
            }

            // Measure trend strength
            val trendStrength = kotlin.math.abs((shortMA - longMA) / longMA)

            // Strong trend: Use wider stops (2.5-3x ATR)
            // Weak/ranging: Use tighter stops (1.5-2x ATR)
            val multiplier = when {
                trendStrength > 0.05 -> 2.5  // Strong trend
                trendStrength > 0.02 -> 2.0  // Moderate trend
                else -> 1.5                  // Weak/ranging
            }

            Timber.d("Optimal ATR multiplier for $pair: $multiplier (trend strength: $trendStrength)")

            return multiplier

        } catch (e: Exception) {
            Timber.e(e, "Error calculating optimal ATR multiplier")
            return DEFAULT_ATR_MULTIPLIER
        }
    }

    /**
     * Get volatility classification for a trading pair
     */
    suspend fun getVolatilityLevel(pair: String): VolatilityLevel {
        try {
            val atr = getCurrentATR(pair)
            if (atr == null) {
                return VolatilityLevel.MEDIUM
            }

            // Get current price to calculate ATR as percentage
            val barsResult = historicalDataRepository.getRecentBars(pair, 60, 10)
            if (barsResult.isFailure) {
                return VolatilityLevel.MEDIUM
            }

            val currentPrice = barsResult.getOrNull()?.lastOrNull()?.close ?: return VolatilityLevel.MEDIUM
            val atrPercent = (atr / currentPrice) * 100.0

            return when {
                atrPercent > 3.0 -> VolatilityLevel.HIGH
                atrPercent > 1.5 -> VolatilityLevel.MEDIUM
                else -> VolatilityLevel.LOW
            }

        } catch (e: Exception) {
            Timber.e(e, "Error getting volatility level")
            return VolatilityLevel.MEDIUM
        }
    }

    /**
     * Calculate volatility-adjusted stop-loss using BigDecimal (exact calculations)
     *
     * @param pair Trading pair
     * @param entryPrice Entry price
     * @param isBuy True for long position, false for short
     * @param atrMultiplier How many ATRs away from entry
     * @param fallbackStopLossPercent Fixed stop-loss if ATR unavailable
     * @return Stop-loss price
     */
    suspend fun calculateVolatilityStopLossDecimal(
        pair: String,
        entryPrice: java.math.BigDecimal,
        isBuy: Boolean,
        atrMultiplier: java.math.BigDecimal = java.math.BigDecimal.valueOf(DEFAULT_ATR_MULTIPLIER),
        fallbackStopLossPercent: java.math.BigDecimal = java.math.BigDecimal.valueOf(2.0)
    ): java.math.BigDecimal {
        // Convert to Double, calculate, then convert back
        val result = calculateVolatilityStopLoss(
            pair = pair,
            entryPrice = entryPrice.toDouble(),
            isBuy = isBuy,
            atrMultiplier = atrMultiplier.toDouble(),
            fallbackStopLossPercent = fallbackStopLossPercent.toDouble()
        )
        return java.math.BigDecimal.valueOf(result)
    }
}

/**
 * Volatility classification
 */
enum class VolatilityLevel {
    LOW,    // ATR < 1.5% - Use tighter stops
    MEDIUM, // ATR 1.5-3% - Normal stops
    HIGH    // ATR > 3% - Use wider stops
}

/**
 * Volatility analysis result
 */
data class VolatilityAnalysis(
    val atr: Double,
    val atrPercent: Double,
    val volatilityLevel: VolatilityLevel,
    val recommendedMultiplier: Double,
    val recommendedStopLoss: Double
)
