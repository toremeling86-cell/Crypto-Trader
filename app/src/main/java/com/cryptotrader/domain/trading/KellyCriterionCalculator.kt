package com.cryptotrader.domain.trading

import com.cryptotrader.domain.model.Strategy
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

/**
 * Kelly Criterion calculator for optimal position sizing
 *
 * The Kelly Criterion maximizes long-term wealth by calculating the optimal bet size based on:
 * - Win rate (probability of winning)
 * - Average win vs average loss ratio
 *
 * Formula: Kelly% = (p * b - q) / b
 * Where:
 * - p = probability of winning (win rate)
 * - q = probability of losing (1 - p)
 * - b = odds received (avg win / avg loss)
 *
 * We use fractional Kelly (default: 0.25 = Quarter Kelly) to reduce volatility
 */
@Singleton
class KellyCriterionCalculator @Inject constructor() {

    // Use fractional Kelly to reduce volatility
    private val kellyFraction = 0.25 // Quarter Kelly (conservative)

    // Safety limits
    private val maxPositionSize = 0.20 // Never risk more than 20% per trade
    private val minPositionSize = 0.01 // Minimum 1% position size

    /**
     * Calculate optimal position size using Kelly Criterion
     *
     * @param winRate Win rate as decimal (0.0 to 1.0)
     * @param avgWinPercent Average win as percentage
     * @param avgLossPercent Average loss as percentage (positive number)
     * @param availableBalance Available trading balance
     * @return Optimal position size in currency units
     */
    fun calculateOptimalPositionSize(
        winRate: Double,
        avgWinPercent: Double,
        avgLossPercent: Double,
        availableBalance: Double
    ): Double {
        try {
            // Validate inputs
            if (winRate <= 0.0 || winRate >= 1.0) {
                Timber.w("Invalid win rate: $winRate, using default")
                return availableBalance * minPositionSize
            }

            if (avgWinPercent <= 0.0 || avgLossPercent <= 0.0) {
                Timber.w("Invalid win/loss percentages, using default")
                return availableBalance * minPositionSize
            }

            // Calculate Kelly percentage
            val p = winRate
            val q = 1.0 - winRate
            val b = avgWinPercent / avgLossPercent // Win/Loss ratio

            val kellyPercent = (p * b - q) / b

            Timber.d("Kelly calculation: p=$p, q=$q, b=$b, kelly=$kellyPercent")

            // Apply fractional Kelly for safety
            var adjustedKelly = kellyPercent * kellyFraction

            // Apply safety limits
            adjustedKelly = max(minPositionSize, min(maxPositionSize, adjustedKelly))

            // Handle negative Kelly (unfavorable odds - don't trade)
            if (adjustedKelly <= 0.0) {
                Timber.w("Negative Kelly criterion: ${kellyPercent}, position size = 0")
                return 0.0
            }

            val positionSize = availableBalance * adjustedKelly

            Timber.i("Kelly Criterion: win_rate=$winRate, win/loss_ratio=$b, kelly%=$kellyPercent, " +
                    "fractional_kelly%=$adjustedKelly, position_size=$positionSize")

            return positionSize

        } catch (e: Exception) {
            Timber.e(e, "Error calculating Kelly position size")
            return availableBalance * minPositionSize
        }
    }

    /**
     * Calculate position size for a strategy based on its historical performance
     *
     * @param strategy Strategy with performance history
     * @param availableBalance Available balance for trading
     * @return Optimal position size in currency units
     */
    fun calculatePositionSizeForStrategy(
        strategy: Strategy,
        availableBalance: Double
    ): Double {
        // Check if we have enough trade history
        if (strategy.totalTrades < 10) {
            // Not enough history, use conservative fixed percentage
            Timber.d("Insufficient trade history (${strategy.totalTrades}), using default position size")
            return availableBalance * (strategy.positionSizePercent / 100.0)
        }

        // Calculate average win and loss from strategy stats
        val winRate = strategy.winRate / 100.0 // Convert to decimal

        // Estimate average win/loss from win rate and total profit
        // This is a simplified calculation - in production you'd track actual avg win/loss
        val avgWin = estimateAvgWin(strategy)
        val avgLoss = estimateAvgLoss(strategy)

        if (avgWin <= 0.0 || avgLoss <= 0.0) {
            // Can't calculate Kelly without win/loss data
            Timber.w("Cannot calculate avg win/loss for strategy ${strategy.name}")
            return availableBalance * (strategy.positionSizePercent / 100.0)
        }

        // Calculate optimal position using Kelly
        val kellyPosition = calculateOptimalPositionSize(
            winRate = winRate,
            avgWinPercent = avgWin,
            avgLossPercent = avgLoss,
            availableBalance = availableBalance
        )

        // Don't exceed strategy's configured max position size
        val maxStrategyPosition = availableBalance * (strategy.positionSizePercent / 100.0)
        val finalPosition = min(kellyPosition, maxStrategyPosition)

        Timber.i("Strategy ${strategy.name}: Kelly=$kellyPosition, Max=$maxStrategyPosition, Final=$finalPosition")

        return finalPosition
    }

    /**
     * Estimate average win percentage from strategy statistics
     */
    private fun estimateAvgWin(strategy: Strategy): Double {
        if (strategy.successfulTrades == 0) return 0.0

        // Simplified estimation based on profit and take-profit setting
        // In reality, you'd track actual average wins
        return strategy.takeProfitPercent
    }

    /**
     * Estimate average loss percentage from strategy statistics
     */
    private fun estimateAvgLoss(strategy: Strategy): Double {
        if (strategy.failedTrades == 0) return 0.0

        // Simplified estimation based on stop-loss setting
        // In reality, you'd track actual average losses
        return strategy.stopLossPercent
    }

    /**
     * Calculate recommended Kelly fraction based on risk tolerance
     *
     * @param riskLevel Risk level from strategy
     * @return Kelly fraction (0.0 to 1.0)
     */
    fun getKellyFractionForRisk(riskLevel: com.cryptotrader.domain.model.RiskLevel): Double {
        return when (riskLevel) {
            com.cryptotrader.domain.model.RiskLevel.LOW -> 0.1    // 1/10 Kelly (very conservative)
            com.cryptotrader.domain.model.RiskLevel.MEDIUM -> 0.25 // 1/4 Kelly (conservative)
            com.cryptotrader.domain.model.RiskLevel.HIGH -> 0.5   // 1/2 Kelly (aggressive)
        }
    }

    /**
     * Determine if a trade should be taken based on Kelly Criterion
     *
     * @return true if Kelly% > 0 (positive expectancy)
     */
    fun shouldTakeTrade(
        winRate: Double,
        avgWinPercent: Double,
        avgLossPercent: Double
    ): Boolean {
        if (avgLossPercent == 0.0) return false

        val p = winRate
        val q = 1.0 - winRate
        val b = avgWinPercent / avgLossPercent

        val kelly = (p * b - q) / b

        return kelly > 0.0
    }

    /**
     * Get diagnostic information about Kelly calculation
     */
    fun getKellyDiagnostics(
        winRate: Double,
        avgWinPercent: Double,
        avgLossPercent: Double
    ): KellyDiagnostics {
        val p = winRate
        val q = 1.0 - winRate
        val b = if (avgLossPercent > 0.0) avgWinPercent / avgLossPercent else 0.0
        val kelly = if (b > 0.0) (p * b - q) / b else 0.0
        val fractionalKelly = kelly * kellyFraction

        return KellyDiagnostics(
            winRate = winRate,
            lossRate = q,
            winLossRatio = b,
            kellyPercent = kelly,
            fractionalKellyPercent = fractionalKelly,
            shouldTrade = kelly > 0.0,
            expectedValue = (winRate * avgWinPercent) - (q * avgLossPercent)
        )
    }
}

/**
 * Diagnostic information about Kelly Criterion calculation
 */
data class KellyDiagnostics(
    val winRate: Double,
    val lossRate: Double,
    val winLossRatio: Double,
    val kellyPercent: Double,
    val fractionalKellyPercent: Double,
    val shouldTrade: Boolean,
    val expectedValue: Double // Expected value per trade (positive = profitable)
)
