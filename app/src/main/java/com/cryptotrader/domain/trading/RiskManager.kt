package com.cryptotrader.domain.trading

import com.cryptotrader.domain.model.Portfolio
import com.cryptotrader.domain.model.Strategy
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * Risk management system to prevent excessive losses
 */
@Singleton
class RiskManager @Inject constructor(
    private val kellyCriterionCalculator: KellyCriterionCalculator,
    private val volatilityStopLossCalculator: VolatilityStopLossCalculator
) {

    companion object {
        private const val MAX_POSITION_SIZE_PERCENT = 20.0 // Max 20% of portfolio per trade
        private const val MAX_TOTAL_EXPOSURE_PERCENT = 80.0 // Max 80% of portfolio invested
        private const val MIN_TRADE_VALUE_USD = 10.0 // Minimum trade value
        private const val MAX_DAILY_LOSS_PERCENT = 5.0 // Stop trading if daily loss exceeds 5%
    }

    /**
     * Adjust position size based on risk parameters and Kelly Criterion (if applicable)
     */
    fun adjustPositionSize(
        requestedVolume: Double,
        price: Double,
        availableBalance: Double,
        strategy: Strategy
    ): Double {
        // Use Kelly Criterion if strategy has enough trade history
        val optimalValue = if (strategy.totalTrades >= 10) {
            // Use Kelly Criterion for optimal sizing
            kellyCriterionCalculator.calculatePositionSizeForStrategy(strategy, availableBalance)
        } else {
            // Fall back to requested volume
            requestedVolume * price
        }

        // Apply maximum position size constraint
        val maxPositionValue = availableBalance * (MAX_POSITION_SIZE_PERCENT / 100.0)
        val adjustedValue = min(optimalValue, maxPositionValue)

        // Ensure minimum trade value
        if (adjustedValue < MIN_TRADE_VALUE_USD) {
            Timber.w("Trade value too small: $adjustedValue USD")
            return 0.0
        }

        val adjustedVolume = adjustedValue / price

        Timber.d(
            "Position size adjusted: requested=$requestedVolume, " +
                    "kelly_optimal=${optimalValue / price}, adjusted=$adjustedVolume, value=$adjustedValue USD"
        )

        return adjustedVolume
    }

    /**
     * Check if new trade is allowed based on current portfolio state
     */
    fun canExecuteTrade(
        tradeValue: Double,
        portfolio: Portfolio
    ): Boolean {
        // Check if we're within exposure limits
        val currentExposurePercent =
            ((portfolio.totalValue - portfolio.availableBalance) / portfolio.totalValue) * 100.0

        if (currentExposurePercent + (tradeValue / portfolio.totalValue * 100.0) > MAX_TOTAL_EXPOSURE_PERCENT) {
            Timber.w("Trade rejected: Would exceed maximum exposure limit")
            return false
        }

        // Check daily loss limit
        if (portfolio.dayProfitPercent < -MAX_DAILY_LOSS_PERCENT) {
            Timber.w("Trading halted: Daily loss limit exceeded (${portfolio.dayProfitPercent}%)")
            return false
        }

        // Check if sufficient balance
        if (tradeValue > portfolio.availableBalance) {
            Timber.w("Trade rejected: Insufficient balance")
            return false
        }

        return true
    }

    /**
     * Calculate stop loss price (fixed percentage)
     */
    fun calculateStopLoss(
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
     * Calculate stop loss with optional volatility adjustment
     *
     * @param pair Trading pair
     * @param entryPrice Entry price
     * @param strategy Strategy with stop-loss settings
     * @param isBuy True for long, false for short
     * @return Stop-loss price
     */
    suspend fun calculateStopLossWithVolatility(
        pair: String,
        entryPrice: Double,
        strategy: Strategy,
        isBuy: Boolean
    ): Double {
        return if (strategy.useVolatilityStops) {
            // Use ATR-based volatility stop
            volatilityStopLossCalculator.calculateVolatilityStopLoss(
                pair = pair,
                entryPrice = entryPrice,
                isBuy = isBuy,
                atrMultiplier = strategy.atrMultiplier,
                fallbackStopLossPercent = strategy.stopLossPercent
            )
        } else {
            // Use fixed percentage stop
            calculateStopLoss(entryPrice, strategy.stopLossPercent, isBuy)
        }
    }

    /**
     * Calculate take profit price
     */
    fun calculateTakeProfit(
        entryPrice: Double,
        takeProfitPercent: Double,
        isBuy: Boolean
    ): Double {
        return if (isBuy) {
            entryPrice * (1.0 + takeProfitPercent / 100.0)
        } else {
            entryPrice * (1.0 - takeProfitPercent / 100.0)
        }
    }

    /**
     * Validate strategy risk parameters
     */
    fun validateStrategy(strategy: Strategy): Result<Unit> {
        if (strategy.positionSizePercent <= 0 || strategy.positionSizePercent > 100) {
            return Result.failure(
                IllegalArgumentException("Position size must be between 0 and 100%")
            )
        }

        if (strategy.stopLossPercent <= 0 || strategy.stopLossPercent > 50) {
            return Result.failure(
                IllegalArgumentException("Stop loss must be between 0 and 50%")
            )
        }

        if (strategy.takeProfitPercent <= 0 || strategy.takeProfitPercent > 200) {
            return Result.failure(
                IllegalArgumentException("Take profit must be between 0 and 200%")
            )
        }

        if (strategy.tradingPairs.isEmpty()) {
            return Result.failure(
                IllegalArgumentException("At least one trading pair must be specified")
            )
        }

        return Result.success(Unit)
    }
}
