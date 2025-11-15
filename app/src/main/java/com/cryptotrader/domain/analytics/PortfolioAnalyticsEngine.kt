package com.cryptotrader.domain.analytics

import com.cryptotrader.domain.model.PortfolioAnalytics
import com.cryptotrader.domain.model.Trade
import timber.log.Timber
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Portfolio Analytics Engine
 * Calculates advanced trading metrics and performance indicators
 */
@Singleton
class PortfolioAnalyticsEngine @Inject constructor() {

    /**
     * Calculate complete portfolio analytics
     */
    suspend fun calculateAnalytics(
        trades: List<Trade>,
        portfolioValues: List<Double>
    ): PortfolioAnalytics {
        return try {
            val returns = calculateReturns(portfolioValues)
            val winningTrades = trades.filter { (it.profit ?: 0.0) > 0 }
            val losingTrades = trades.filter { (it.profit ?: 0.0) < 0 }

            PortfolioAnalytics(
                sharpeRatio = calculateSharpeRatio(returns),
                maxDrawdown = calculateMaxDrawdownValue(portfolioValues),
                maxDrawdownPercent = calculateMaxDrawdown(portfolioValues),
                winRate = calculateWinRate(trades),
                profitFactor = calculateProfitFactor(trades),
                bestTrade = findBestTrade(trades),
                worstTrade = findWorstTrade(trades),
                avgHoldTime = calculateAvgHoldTime(trades),
                monthlyReturns = calculateMonthlyReturns(trades),
                totalTrades = trades.size,
                winningTrades = winningTrades.size,
                losingTrades = losingTrades.size,
                avgWin = if (winningTrades.isNotEmpty()) winningTrades.mapNotNull { it.profit }.average() else 0.0,
                avgLoss = if (losingTrades.isNotEmpty()) losingTrades.mapNotNull { it.profit }.average() else 0.0,
                largestWin = winningTrades.mapNotNull { it.profit }.maxOrNull() ?: 0.0,
                largestLoss = losingTrades.mapNotNull { it.profit }.minOrNull() ?: 0.0
            )
        } catch (e: Exception) {
            Timber.e(e, "Error calculating analytics")
            // Return default analytics
            PortfolioAnalytics(
                sharpeRatio = 0.0,
                maxDrawdown = 0.0,
                maxDrawdownPercent = 0.0,
                winRate = 0.0,
                profitFactor = 0.0,
                bestTrade = null,
                worstTrade = null,
                avgHoldTime = 0L,
                monthlyReturns = emptyMap(),
                totalTrades = 0,
                winningTrades = 0,
                losingTrades = 0,
                avgWin = 0.0,
                avgLoss = 0.0,
                largestWin = 0.0,
                largestLoss = 0.0
            )
        }
    }

    /**
     * Calculate Sharpe Ratio
     * (Average Return - Risk Free Rate) / Standard Deviation of Returns
     * Risk-free rate assumed to be 0.02 (2% annual)
     */
    fun calculateSharpeRatio(returns: List<Double>, riskFreeRate: Double = 0.02): Double {
        if (returns.isEmpty() || returns.size < 2) return 0.0

        val avgReturn = returns.average()
        val stdDev = calculateStandardDeviation(returns)

        if (stdDev == 0.0) return 0.0

        // Annualize the Sharpe ratio (assuming daily returns)
        val sharpe = (avgReturn - riskFreeRate / 252) / stdDev
        return sharpe * sqrt(252.0) // Annualized Sharpe
    }

    /**
     * Calculate Maximum Drawdown (percentage)
     * Largest peak-to-trough decline
     */
    fun calculateMaxDrawdown(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0

        var maxDrawdown = 0.0
        var peak = values[0]

        for (value in values) {
            if (value > peak) {
                peak = value
            }
            val drawdown = ((peak - value) / peak) * 100.0
            if (drawdown > maxDrawdown) {
                maxDrawdown = drawdown
            }
        }

        return maxDrawdown
    }

    /**
     * Calculate Maximum Drawdown (absolute value)
     */
    fun calculateMaxDrawdownValue(values: List<Double>): Double {
        if (values.isEmpty()) return 0.0

        var maxDrawdown = 0.0
        var peak = values[0]

        for (value in values) {
            if (value > peak) {
                peak = value
            }
            val drawdown = peak - value
            if (drawdown > maxDrawdown) {
                maxDrawdown = drawdown
            }
        }

        return maxDrawdown
    }

    /**
     * Calculate Win Rate
     * (Winning Trades / Total Trades) * 100
     */
    fun calculateWinRate(trades: List<Trade>): Double {
        if (trades.isEmpty()) return 0.0

        val winningTrades = trades.count { (it.profit ?: 0.0) > 0 }
        return (winningTrades.toDouble() / trades.size) * 100.0
    }

    /**
     * Calculate Profit Factor
     * Total Wins / Total Losses
     */
    fun calculateProfitFactor(trades: List<Trade>): Double {
        val totalWins = trades.filter { (it.profit ?: 0.0) > 0 }.sumOf { it.profit ?: 0.0 }
        val totalLosses = trades.filter { (it.profit ?: 0.0) < 0 }.sumOf { kotlin.math.abs(it.profit ?: 0.0) }

        if (totalLosses == 0.0) {
            return if (totalWins > 0) Double.POSITIVE_INFINITY else 0.0
        }

        return totalWins / totalLosses
    }

    /**
     * Find best trade
     */
    fun findBestTrade(trades: List<Trade>): Trade? {
        return trades.maxByOrNull { it.profit ?: Double.MIN_VALUE }
    }

    /**
     * Find worst trade
     */
    fun findWorstTrade(trades: List<Trade>): Trade? {
        return trades.minByOrNull { it.profit ?: Double.MAX_VALUE }
    }

    /**
     * Calculate average hold time
     */
    fun calculateAvgHoldTime(trades: List<Trade>): Long {
        if (trades.isEmpty()) return 0L

        val totalHoldTime = trades.sumOf { trade ->
            if (trade.exitTime != null && trade.entryTime != null) {
                trade.exitTime - trade.entryTime
            } else {
                0L
            }
        }

        return totalHoldTime / trades.size
    }

    /**
     * Calculate monthly returns
     * Returns map of "YYYY-MM" -> return%
     */
    fun calculateMonthlyReturns(trades: List<Trade>): Map<String, Double> {
        if (trades.isEmpty()) return emptyMap()

        val monthlyProfits = mutableMapOf<String, MutableList<Double>>()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM")

        for (trade in trades) {
            val exitTime = trade.exitTime ?: continue
            val profit = trade.profit ?: continue
            val month = Instant.ofEpochMilli(exitTime)
                .atZone(ZoneId.systemDefault())
                .format(formatter)

            monthlyProfits.getOrPut(month) { mutableListOf() }.add(profit)
        }

        return monthlyProfits.mapValues { (_, profits) ->
            profits.sum()
        }.toSortedMap()
    }

    /**
     * Calculate returns from portfolio values
     */
    private fun calculateReturns(values: List<Double>): List<Double> {
        if (values.size < 2) return emptyList()

        val returns = mutableListOf<Double>()
        for (i in 1 until values.size) {
            if (values[i - 1] > 0) {
                val returnVal = (values[i] - values[i - 1]) / values[i - 1]
                returns.add(returnVal)
            }
        }

        return returns
    }

    /**
     * Calculate standard deviation
     */
    private fun calculateStandardDeviation(values: List<Double>): Double {
        if (values.isEmpty() || values.size < 2) return 0.0

        val mean = values.average()
        val variance = values.map { (it - mean).pow(2) }.average()
        return sqrt(variance)
    }
}
