package com.cryptotrader.domain.trading

import com.cryptotrader.data.repository.StrategyRepository
import com.cryptotrader.domain.model.Strategy
import com.cryptotrader.domain.model.Trade
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real-time strategy health monitoring
 *
 * Monitors strategy performance and automatically disables degrading strategies to prevent losses:
 * - Tracks recent win rate
 * - Monitors drawdown
 * - Detects consecutive losses
 * - Checks deviation from expected performance
 */
@Singleton
class StrategyHealthMonitor @Inject constructor(
    private val strategyRepository: StrategyRepository
) {

    companion object {
        private const val MIN_TRADES_FOR_HEALTH_CHECK = 5
        private const val RECENT_TRADES_WINDOW = 20
        private const val MIN_WIN_RATE_THRESHOLD = 0.35       // Disable if win rate < 35%
        private const val MAX_CONSECUTIVE_LOSSES = 5           // Disable after 5 losses in a row
        private const val MAX_DRAWDOWN_PERCENT = 15.0         // Disable if drawdown > 15%
        private const val PERFORMANCE_DEVIATION_THRESHOLD = 0.3 // 30% worse than expected
    }

    // Track recent trade results per strategy
    private val recentTrades = mutableMapOf<String, MutableList<StrategyTradeResult>>()
    private val consecutiveLosses = mutableMapOf<String, Int>()
    private val peakEquity = mutableMapOf<String, Double>()

    /**
     * Record a trade result and check strategy health
     *
     * @param strategyId Strategy ID
     * @param trade Executed trade
     * @param pnl Profit/Loss from the trade
     * @return Health check result (may recommend disabling strategy)
     */
    suspend fun recordTradeAndCheckHealth(
        strategyId: String,
        trade: Trade,
        pnl: Double
    ): HealthCheckResult {
        try {
            // Record trade result
            val tradeResult = StrategyTradeResult(
                timestamp = trade.timestamp,
                pnl = pnl,
                isWin = pnl > 0.0
            )

            val trades = recentTrades.getOrPut(strategyId) { mutableListOf() }
            trades.add(tradeResult)

            // Keep only recent trades
            if (trades.size > RECENT_TRADES_WINDOW) {
                trades.removeAt(0)
            }

            // Update consecutive losses
            if (pnl > 0.0) {
                consecutiveLosses[strategyId] = 0
            } else {
                consecutiveLosses[strategyId] = (consecutiveLosses[strategyId] ?: 0) + 1
            }

            // Update peak equity for drawdown calculation
            val currentEquity = calculateStrategyEquity(trades)
            val currentPeak = peakEquity[strategyId] ?: currentEquity
            if (currentEquity > currentPeak) {
                peakEquity[strategyId] = currentEquity
            }

            // Perform health check
            if (trades.size >= MIN_TRADES_FOR_HEALTH_CHECK) {
                val healthCheck = performHealthCheck(strategyId, trades)

                // Auto-disable if health check fails
                if (!healthCheck.isHealthy && healthCheck.shouldDisable) {
                    disableStrategy(strategyId, healthCheck.reason)
                }

                return healthCheck
            }

            return HealthCheckResult(
                isHealthy = true,
                shouldDisable = false,
                reason = "Insufficient trades for health check (${trades.size}/$MIN_TRADES_FOR_HEALTH_CHECK)"
            )

        } catch (e: Exception) {
            Timber.e(e, "Error recording trade and checking health")
            return HealthCheckResult(
                isHealthy = true,
                shouldDisable = false,
                reason = "Error in health check: ${e.message}"
            )
        }
    }

    /**
     * Perform comprehensive health check on a strategy
     */
    private fun performHealthCheck(
        strategyId: String,
        trades: List<StrategyTradeResult>
    ): HealthCheckResult {
        // 1. Check win rate
        val winRate = trades.count { it.isWin }.toDouble() / trades.size
        if (winRate < MIN_WIN_RATE_THRESHOLD) {
            return HealthCheckResult(
                isHealthy = false,
                shouldDisable = true,
                reason = "Win rate degraded to ${String.format("%.1f", winRate * 100)}% (threshold: ${MIN_WIN_RATE_THRESHOLD * 100}%)",
                metrics = HealthMetrics(
                    recentWinRate = winRate,
                    consecutiveLosses = consecutiveLosses[strategyId] ?: 0,
                    currentDrawdown = calculateDrawdown(strategyId, trades),
                    tradeCount = trades.size
                )
            )
        }

        // 2. Check consecutive losses
        val consLosses = consecutiveLosses[strategyId] ?: 0
        if (consLosses >= MAX_CONSECUTIVE_LOSSES) {
            return HealthCheckResult(
                isHealthy = false,
                shouldDisable = true,
                reason = "Too many consecutive losses: $consLosses",
                metrics = HealthMetrics(
                    recentWinRate = winRate,
                    consecutiveLosses = consLosses,
                    currentDrawdown = calculateDrawdown(strategyId, trades),
                    tradeCount = trades.size
                )
            )
        }

        // 3. Check drawdown
        val drawdown = calculateDrawdown(strategyId, trades)
        if (drawdown > MAX_DRAWDOWN_PERCENT) {
            return HealthCheckResult(
                isHealthy = false,
                shouldDisable = true,
                reason = "Excessive drawdown: ${String.format("%.1f", drawdown)}%",
                metrics = HealthMetrics(
                    recentWinRate = winRate,
                    consecutiveLosses = consLosses,
                    currentDrawdown = drawdown,
                    tradeCount = trades.size
                )
            )
        }

        // 4. Check performance deviation (optional - needs historical expected performance)
        // Skipped for now - would compare recent performance to backtested performance

        // Strategy is healthy
        return HealthCheckResult(
            isHealthy = true,
            shouldDisable = false,
            reason = "Strategy performing normally",
            metrics = HealthMetrics(
                recentWinRate = winRate,
                consecutiveLosses = consLosses,
                currentDrawdown = drawdown,
                tradeCount = trades.size
            )
        )
    }

    /**
     * Calculate current equity from trade results
     */
    private fun calculateStrategyEquity(trades: List<StrategyTradeResult>): Double {
        return trades.sumOf { it.pnl }
    }

    /**
     * Calculate current drawdown percentage
     */
    private fun calculateDrawdown(strategyId: String, trades: List<StrategyTradeResult>): Double {
        val currentEquity = calculateStrategyEquity(trades)
        val peak = peakEquity[strategyId] ?: currentEquity

        return if (peak > 0.0) {
            ((peak - currentEquity) / peak) * 100.0
        } else {
            0.0
        }
    }

    /**
     * Disable a strategy and log the reason
     */
    private suspend fun disableStrategy(strategyId: String, reason: String) {
        try {
            Timber.w("🚨 AUTO-DISABLING strategy $strategyId: $reason")

            // Get strategy and disable it
            val strategies = strategyRepository.getAllStrategies().first()
            val strategy = strategies.find { it.id == strategyId }

            if (strategy != null) {
                // Update strategy to inactive
                val updatedStrategy = strategy.copy(isActive = false)
                strategyRepository.updateStrategy(updatedStrategy)

                Timber.i("Strategy ${strategy.name} has been automatically disabled")
            }

        } catch (e: Exception) {
            Timber.e(e, "Error disabling strategy")
        }
    }

    /**
     * Get current health status for a strategy
     */
    fun getHealthStatus(strategyId: String): HealthStatus {
        val trades = recentTrades[strategyId] ?: emptyList()

        if (trades.isEmpty()) {
            return HealthStatus(
                status = HealthLevel.UNKNOWN,
                message = "No recent trades",
                recentWinRate = 0.0,
                consecutiveLosses = 0,
                currentDrawdown = 0.0
            )
        }

        val winRate = trades.count { it.isWin }.toDouble() / trades.size
        val consLosses = consecutiveLosses[strategyId] ?: 0
        val drawdown = calculateDrawdown(strategyId, trades)

        val level = when {
            consLosses >= MAX_CONSECUTIVE_LOSSES -> HealthLevel.CRITICAL
            winRate < MIN_WIN_RATE_THRESHOLD -> HealthLevel.CRITICAL
            drawdown > MAX_DRAWDOWN_PERCENT -> HealthLevel.CRITICAL
            winRate < 0.45 || consLosses >= 3 -> HealthLevel.WARNING
            winRate > 0.60 && drawdown < 5.0 -> HealthLevel.EXCELLENT
            else -> HealthLevel.GOOD
        }

        val message = when (level) {
            HealthLevel.EXCELLENT -> "Strategy performing excellently"
            HealthLevel.GOOD -> "Strategy performing normally"
            HealthLevel.WARNING -> "Strategy showing warning signs"
            HealthLevel.CRITICAL -> "Strategy performance critical - may auto-disable"
            HealthLevel.UNKNOWN -> "Unknown status"
        }

        return HealthStatus(
            status = level,
            message = message,
            recentWinRate = winRate,
            consecutiveLosses = consLosses,
            currentDrawdown = drawdown
        )
    }

    /**
     * Clear health data for a strategy (e.g., when resetting)
     */
    fun clearHealthData(strategyId: String) {
        recentTrades.remove(strategyId)
        consecutiveLosses.remove(strategyId)
        peakEquity.remove(strategyId)
    }

    /**
     * Get all strategy health statuses
     */
    fun getAllHealthStatuses(): Map<String, HealthStatus> {
        return recentTrades.keys.associateWith { getHealthStatus(it) }
    }
}

/**
 * Trade result for health tracking
 */
data class StrategyTradeResult(
    val timestamp: Long,
    val pnl: Double,
    val isWin: Boolean
)

/**
 * Health check result
 */
data class HealthCheckResult(
    val isHealthy: Boolean,
    val shouldDisable: Boolean,
    val reason: String,
    val metrics: HealthMetrics? = null
)

/**
 * Health metrics
 */
data class HealthMetrics(
    val recentWinRate: Double,
    val consecutiveLosses: Int,
    val currentDrawdown: Double,
    val tradeCount: Int
)

/**
 * Health status
 */
data class HealthStatus(
    val status: HealthLevel,
    val message: String,
    val recentWinRate: Double,
    val consecutiveLosses: Int,
    val currentDrawdown: Double
)

/**
 * Health level classification
 */
enum class HealthLevel {
    EXCELLENT,  // Performing above expectations
    GOOD,       // Normal performance
    WARNING,    // Showing concerning signs
    CRITICAL,   // May auto-disable soon
    UNKNOWN     // Insufficient data
}
