package com.cryptotrader.domain.trading

import com.cryptotrader.domain.model.Portfolio
import com.cryptotrader.domain.model.Strategy
import com.cryptotrader.utils.toBigDecimalMoney
import com.cryptotrader.utils.safeDiv
import com.cryptotrader.utils.percentOf
import com.cryptotrader.utils.applyPercent
import com.cryptotrader.utils.MONEY_SCALE
import com.cryptotrader.utils.MONEY_ROUNDING
import timber.log.Timber
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * Risk management system to prevent excessive losses
 *
 * MIGRATION STATUS: Phase 2.9 - BigDecimal Support Added
 * - All calculation methods now have BigDecimal versions with "Decimal" suffix
 * - Legacy Double methods kept for backward compatibility but marked @Deprecated
 * - New code should use BigDecimal methods for exact arithmetic
 */
@Singleton
class RiskManager @Inject constructor(
    private val kellyCriterionCalculator: KellyCriterionCalculator,
    private val volatilityStopLossCalculator: VolatilityStopLossCalculator
) {

    companion object {
        // Legacy Double constants - kept for backward compatibility
        private const val MAX_POSITION_SIZE_PERCENT = 20.0 // Max 20% of portfolio per trade
        private const val MAX_TOTAL_EXPOSURE_PERCENT = 80.0 // Max 80% of portfolio invested
        private const val MIN_TRADE_VALUE_USD = 10.0 // Minimum trade value
        private const val MAX_DAILY_LOSS_PERCENT = 5.0 // Stop trading if daily loss exceeds 5%

        // BigDecimal constants for exact calculations
        private val MAX_POSITION_SIZE_PERCENT_DECIMAL = "20".toBigDecimalMoney()
        private val MAX_TOTAL_EXPOSURE_PERCENT_DECIMAL = "80".toBigDecimalMoney()
        private val MIN_TRADE_VALUE_USD_DECIMAL = "10".toBigDecimalMoney()
        private val MAX_DAILY_LOSS_PERCENT_DECIMAL = "5".toBigDecimalMoney()
        private val HUNDRED = "100".toBigDecimalMoney()
    }

    // =============================================================================================
    // BIGDECIMAL METHODS (PHASE 2.9) - Use these for exact calculations
    // =============================================================================================

    /**
     * Adjust position size based on risk parameters and Kelly Criterion (BigDecimal version)
     *
     * This method provides exact decimal arithmetic for position sizing calculations.
     *
     * @param requestedVolume Requested volume in crypto units
     * @param price Current price per unit
     * @param availableBalance Available balance in USD
     * @param strategy Trading strategy with risk parameters
     * @return Adjusted volume in crypto units (exact BigDecimal)
     */
    suspend fun adjustPositionSizeDecimal(
        requestedVolume: BigDecimal,
        price: BigDecimal,
        availableBalance: BigDecimal,
        strategy: Strategy
    ): BigDecimal {
        // Use Kelly Criterion if strategy has enough trade history
        val optimalValue = if (strategy.totalTrades >= 10) {
            // Use Kelly Criterion for optimal sizing (now uses actual trade history)
            kellyCriterionCalculator.calculatePositionSizeForStrategyDecimal(strategy, availableBalance)
        } else {
            // Fall back to strategy's position size percentage
            availableBalance.applyPercent(strategy.positionSizePercentDecimal)
        }

        // Apply maximum position size constraint (20% of available balance)
        val maxPositionValue = availableBalance.applyPercent(MAX_POSITION_SIZE_PERCENT_DECIMAL)
        val adjustedValue = optimalValue.min(maxPositionValue)

        // Ensure minimum trade value
        if (adjustedValue < MIN_TRADE_VALUE_USD_DECIMAL) {
            Timber.w("Trade value too small: $adjustedValue USD")
            return BigDecimal.ZERO
        }

        // Calculate volume from value
        val adjustedVolume = adjustedValue safeDiv price

        Timber.d(
            "Position size adjusted: requested=$requestedVolume, " +
                    "kelly_optimal=${optimalValue safeDiv price}, adjusted=$adjustedVolume, value=$adjustedValue USD"
        )

        return adjustedVolume
    }

    /**
     * Check if new trade is allowed based on current portfolio state (BigDecimal version)
     *
     * Validates:
     * - Portfolio has positive value
     * - Total exposure stays below 80% limit
     * - Daily loss hasn't exceeded 5% threshold
     * - Sufficient balance available
     *
     * @param tradeValue Value of the proposed trade in USD
     * @param portfolio Current portfolio state
     * @return true if trade is allowed, false otherwise
     */
    fun canExecuteTradeDecimal(
        tradeValue: BigDecimal,
        portfolio: Portfolio
    ): Boolean {
        val totalValue = portfolio.totalValueDecimal
        val availableBalance = portfolio.availableBalanceDecimal

        // Guard against zero or negative portfolio value
        if (totalValue <= BigDecimal.ZERO) {
            Timber.w("Trade rejected: Portfolio value is zero or negative")
            return false
        }

        // Calculate current exposure percentage
        val currentInvestedValue = totalValue - availableBalance
        val currentExposurePercent = currentInvestedValue.percentOf(totalValue)

        // Calculate new exposure if trade executes
        val tradeExposurePercent = tradeValue.percentOf(totalValue)
        val newTotalExposurePercent = currentExposurePercent + tradeExposurePercent

        if (newTotalExposurePercent > MAX_TOTAL_EXPOSURE_PERCENT_DECIMAL) {
            Timber.w(
                "Trade rejected: Would exceed maximum exposure limit " +
                        "(current: $currentExposurePercent%, trade: $tradeExposurePercent%, max: $MAX_TOTAL_EXPOSURE_PERCENT_DECIMAL%)"
            )
            return false
        }

        // Check daily loss limit
        val dayProfitPercent = portfolio.dayProfitPercent.toBigDecimalMoney()
        if (dayProfitPercent < -MAX_DAILY_LOSS_PERCENT_DECIMAL) {
            Timber.w("Trading halted: Daily loss limit exceeded ($dayProfitPercent%)")
            return false
        }

        // Check if sufficient balance
        if (tradeValue > availableBalance) {
            Timber.w("Trade rejected: Insufficient balance (need: $tradeValue, available: $availableBalance)")
            return false
        }

        return true
    }

    /**
     * Calculate stop loss price - fixed percentage (BigDecimal version)
     *
     * For long positions (buy): stopLoss = entryPrice * (1 - stopLossPercent/100)
     * For short positions (sell): stopLoss = entryPrice * (1 + stopLossPercent/100)
     *
     * @param entryPrice Entry price of the position
     * @param stopLossPercent Stop loss percentage (e.g., 2.0 for 2%)
     * @param isBuy true for long position, false for short position
     * @return Stop loss price (exact BigDecimal)
     */
    fun calculateStopLossDecimal(
        entryPrice: BigDecimal,
        stopLossPercent: BigDecimal,
        isBuy: Boolean
    ): BigDecimal {
        return if (isBuy) {
            // Long: stop below entry price
            entryPrice - entryPrice.applyPercent(stopLossPercent)
        } else {
            // Short: stop above entry price
            entryPrice + entryPrice.applyPercent(stopLossPercent)
        }
    }

    /**
     * Calculate stop loss with optional volatility adjustment (BigDecimal version)
     *
     * Uses either:
     * - ATR-based volatility stop (if strategy.useVolatilityStops = true)
     * - Fixed percentage stop (if strategy.useVolatilityStops = false)
     *
     * @param pair Trading pair (e.g., "BTC/USD")
     * @param entryPrice Entry price
     * @param strategy Strategy with stop-loss settings
     * @param isBuy True for long, false for short
     * @return Stop-loss price (exact BigDecimal)
     */
    suspend fun calculateStopLossWithVolatilityDecimal(
        pair: String,
        entryPrice: BigDecimal,
        strategy: Strategy,
        isBuy: Boolean
    ): BigDecimal {
        return if (strategy.useVolatilityStops) {
            // Use ATR-based volatility stop
            volatilityStopLossCalculator.calculateVolatilityStopLossDecimal(
                pair = pair,
                entryPrice = entryPrice,
                isBuy = isBuy,
                atrMultiplier = strategy.atrMultiplier.toBigDecimalMoney(),
                fallbackStopLossPercent = strategy.stopLossPercentDecimal
            )
        } else {
            // Use fixed percentage stop
            calculateStopLossDecimal(entryPrice, strategy.stopLossPercentDecimal, isBuy)
        }
    }

    /**
     * Calculate take profit price (BigDecimal version)
     *
     * For long positions (buy): takeProfit = entryPrice * (1 + takeProfitPercent/100)
     * For short positions (sell): takeProfit = entryPrice * (1 - takeProfitPercent/100)
     *
     * @param entryPrice Entry price of the position
     * @param takeProfitPercent Take profit percentage (e.g., 5.0 for 5%)
     * @param isBuy true for long position, false for short position
     * @return Take profit price (exact BigDecimal)
     */
    fun calculateTakeProfitDecimal(
        entryPrice: BigDecimal,
        takeProfitPercent: BigDecimal,
        isBuy: Boolean
    ): BigDecimal {
        return if (isBuy) {
            // Long: profit above entry price
            entryPrice + entryPrice.applyPercent(takeProfitPercent)
        } else {
            // Short: profit below entry price
            entryPrice - entryPrice.applyPercent(takeProfitPercent)
        }
    }

    /**
     * Validate strategy risk parameters (BigDecimal version)
     *
     * Checks:
     * - Position size: 0% < x <= 100%
     * - Stop loss: 0% < x <= 50%
     * - Take profit: 0% < x <= 200%
     * - At least one trading pair specified
     *
     * @param strategy Strategy to validate
     * @return Result.success if valid, Result.failure with error message if invalid
     */
    fun validateStrategyDecimal(strategy: Strategy): Result<Unit> {
        val positionSize = strategy.positionSizePercentDecimal
        val stopLoss = strategy.stopLossPercentDecimal
        val takeProfit = strategy.takeProfitPercentDecimal

        if (positionSize <= BigDecimal.ZERO || positionSize > HUNDRED) {
            return Result.failure(
                IllegalArgumentException("Position size must be between 0 and 100% (got: $positionSize%)")
            )
        }

        if (stopLoss <= BigDecimal.ZERO || stopLoss > "50".toBigDecimalMoney()) {
            return Result.failure(
                IllegalArgumentException("Stop loss must be between 0 and 50% (got: $stopLoss%)")
            )
        }

        if (takeProfit <= BigDecimal.ZERO || takeProfit > "200".toBigDecimalMoney()) {
            return Result.failure(
                IllegalArgumentException("Take profit must be between 0 and 200% (got: $takeProfit%)")
            )
        }

        if (strategy.tradingPairs.isEmpty()) {
            return Result.failure(
                IllegalArgumentException("At least one trading pair must be specified")
            )
        }

        return Result.success(Unit)
    }

    // =============================================================================================
    // LEGACY DOUBLE METHODS - Deprecated, kept for backward compatibility
    // =============================================================================================

    /**
     * Adjust position size based on risk parameters and Kelly Criterion (if applicable)
     *
     * Note: This is now a suspend function because Kelly Criterion uses actual trade history
     *
     * @deprecated Use adjustPositionSizeDecimal for exact calculations
     */
    @Deprecated(
        message = "Use adjustPositionSizeDecimal for exact calculations",
        replaceWith = ReplaceWith("adjustPositionSizeDecimal(requestedVolume.toBigDecimalMoney(), price.toBigDecimalMoney(), availableBalance.toBigDecimalMoney(), strategy)")
    )
    suspend fun adjustPositionSize(
        requestedVolume: Double,
        price: Double,
        availableBalance: Double,
        strategy: Strategy
    ): Double {
        // Use Kelly Criterion if strategy has enough trade history
        val optimalValue = if (strategy.totalTrades >= 10) {
            // Use Kelly Criterion for optimal sizing (now uses actual trade history)
            kellyCriterionCalculator.calculatePositionSizeForStrategy(strategy, availableBalance)
        } else {
            // Fall back to strategy's position size percentage
            availableBalance * (strategy.positionSizePercent / 100.0)
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
     *
     * @deprecated Use canExecuteTradeDecimal for exact calculations
     */
    @Deprecated(
        message = "Use canExecuteTradeDecimal for exact calculations",
        replaceWith = ReplaceWith("canExecuteTradeDecimal(tradeValue.toBigDecimalMoney(), portfolio)")
    )
    fun canExecuteTrade(
        tradeValue: Double,
        portfolio: Portfolio
    ): Boolean {
        // Guard against zero portfolio value
        if (portfolio.totalValue <= 0.0) {
            Timber.w("Trade rejected: Portfolio value is zero or negative")
            return false
        }

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
     *
     * @deprecated Use calculateStopLossDecimal for exact calculations
     */
    @Deprecated(
        message = "Use calculateStopLossDecimal for exact calculations",
        replaceWith = ReplaceWith("calculateStopLossDecimal(entryPrice.toBigDecimalMoney(), stopLossPercent.toBigDecimalMoney(), isBuy)")
    )
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
     *
     * @deprecated Use calculateStopLossWithVolatilityDecimal for exact calculations
     */
    @Deprecated(
        message = "Use calculateStopLossWithVolatilityDecimal for exact calculations",
        replaceWith = ReplaceWith("calculateStopLossWithVolatilityDecimal(pair, entryPrice.toBigDecimalMoney(), strategy, isBuy)")
    )
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
     *
     * @deprecated Use calculateTakeProfitDecimal for exact calculations
     */
    @Deprecated(
        message = "Use calculateTakeProfitDecimal for exact calculations",
        replaceWith = ReplaceWith("calculateTakeProfitDecimal(entryPrice.toBigDecimalMoney(), takeProfitPercent.toBigDecimalMoney(), isBuy)")
    )
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
     *
     * @deprecated Use validateStrategyDecimal for exact calculations
     */
    @Deprecated(
        message = "Use validateStrategyDecimal for exact calculations",
        replaceWith = ReplaceWith("validateStrategyDecimal(strategy)")
    )
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
