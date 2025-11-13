package com.cryptotrader.domain.analytics

import com.cryptotrader.data.local.dao.TradeDao
import com.cryptotrader.domain.model.TradeStatus
import com.cryptotrader.domain.model.TradeType
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * Analytics engine for calculating strategy performance metrics
 */
@Singleton
class StrategyAnalytics @Inject constructor(
    private val tradeDao: TradeDao
) {

    /**
     * Calculate comprehensive analytics for a strategy
     * Returns: StrategyPerformance with all metrics
     */
    suspend fun calculateStrategyPerformance(strategyId: String): StrategyPerformance {
        return try {
            val trades = tradeDao.getTradesByStrategy(strategyId).first()

            if (trades.isEmpty()) {
                return StrategyPerformance(
                    strategyId = strategyId,
                    totalTrades = 0,
                    winningTrades = 0,
                    losingTrades = 0,
                    winRate = 0.0,
                    averageProfit = 0.0,
                    averageLoss = 0.0,
                    profitFactor = 0.0,
                    sharpeRatio = 0.0,
                    maxDrawdown = 0.0,
                    totalPnL = 0.0,
                    totalPnLPercent = 0.0,
                    bestTrade = 0.0,
                    worstTrade = 0.0
                )
            }

            // Convert to domain models and pair buy/sell trades (FIFO)
            val domainTrades = trades.map { it.toDomain() }
            val positions = mutableListOf<Pair<com.cryptotrader.domain.model.Trade, com.cryptotrader.domain.model.Trade>>() // (buy, sell)
            val buyPositions = mutableMapOf<String, MutableList<com.cryptotrader.domain.model.Trade>>()

            domainTrades.sortedBy { it.timestamp }.forEach { trade ->
                when (trade.type) {
                    TradeType.BUY -> {
                        buyPositions.getOrPut(trade.pair) { mutableListOf() }.add(trade)
                    }
                    TradeType.SELL -> {
                        val buyQueue = buyPositions[trade.pair]
                        if (!buyQueue.isNullOrEmpty()) {
                            val buyTrade = buyQueue.removeAt(0) // FIFO
                            positions.add(Pair(buyTrade, trade))
                        }
                    }
                }
            }

            if (positions.isEmpty()) {
                return StrategyPerformance(
                    strategyId = strategyId,
                    totalTrades = trades.size,
                    winningTrades = 0,
                    losingTrades = 0,
                    winRate = 0.0,
                    averageProfit = 0.0,
                    averageLoss = 0.0,
                    profitFactor = 0.0,
                    sharpeRatio = 0.0,
                    maxDrawdown = 0.0,
                    totalPnL = 0.0,
                    totalPnLPercent = 0.0,
                    bestTrade = 0.0,
                    worstTrade = 0.0
                )
            }

            // Calculate P&L for each position
            val pnls = positions.map { (buy, sell) ->
                (sell.price - buy.price) * sell.volume - buy.fee - sell.fee
            }

            val winningPnLs = pnls.filter { it > 0 }
            val losingPnLs = pnls.filter { it < 0 }

            val totalPnL = pnls.sum()
            val winningTrades = winningPnLs.size
            val losingTrades = losingPnLs.size
            val totalTrades = positions.size

            val winRate = if (totalTrades > 0) {
                (winningTrades.toDouble() / totalTrades.toDouble()) * 100.0
            } else 0.0

            val averageProfit = if (winningPnLs.isNotEmpty()) {
                winningPnLs.average()
            } else 0.0

            val averageLoss = if (losingPnLs.isNotEmpty()) {
                losingPnLs.average()
            } else 0.0

            val grossProfit = winningPnLs.sum()
            val grossLoss = losingPnLs.sum().let { kotlin.math.abs(it) }
            val profitFactor = if (grossLoss > 0) {
                grossProfit / grossLoss
            } else if (grossProfit > 0) {
                Double.POSITIVE_INFINITY
            } else 1.0

            // Sharpe Ratio calculation
            val returns = pnls.map { it / 10000.0 } // Normalize by starting balance
            val averageReturn = returns.average()
            val stdDev = if (returns.size > 1) {
                val variance = returns.map { (it - averageReturn) * (it - averageReturn) }.average()
                sqrt(variance)
            } else 0.0

            val sharpeRatio = if (stdDev > 0) {
                (averageReturn / stdDev) * sqrt(252.0) // Annualized (assuming ~252 trading days)
            } else 0.0

            // Max Drawdown calculation
            val cumulativePnL = mutableListOf<Double>()
            var runningPnL = 0.0
            pnls.forEach { pnl ->
                runningPnL += pnl
                cumulativePnL.add(runningPnL)
            }

            var maxDrawdown = 0.0
            var peak = cumulativePnL.firstOrNull() ?: 0.0
            cumulativePnL.forEach { value ->
                if (value > peak) {
                    peak = value
                }
                val drawdown = peak - value
                if (drawdown > maxDrawdown) {
                    maxDrawdown = drawdown
                }
            }

            val startingBalance = 10000.0
            val totalPnLPercent = (totalPnL / startingBalance) * 100.0

            val bestTrade = pnls.maxOrNull() ?: 0.0
            val worstTrade = pnls.minOrNull() ?: 0.0

            Timber.d("Strategy $strategyId performance: Win rate: ${"%.2f".format(winRate)}%, P&L: ${"%.2f".format(totalPnL)}, Sharpe: ${"%.2f".format(sharpeRatio)}")

            StrategyPerformance(
                strategyId = strategyId,
                totalTrades = totalTrades,
                winningTrades = winningTrades,
                losingTrades = losingTrades,
                winRate = winRate,
                averageProfit = averageProfit,
                averageLoss = averageLoss,
                profitFactor = profitFactor,
                sharpeRatio = sharpeRatio,
                maxDrawdown = maxDrawdown,
                totalPnL = totalPnL,
                totalPnLPercent = totalPnLPercent,
                bestTrade = bestTrade,
                worstTrade = worstTrade
            )
        } catch (e: Exception) {
            Timber.e(e, "Error calculating strategy performance for $strategyId")
            StrategyPerformance(
                strategyId = strategyId,
                totalTrades = 0,
                winningTrades = 0,
                losingTrades = 0,
                winRate = 0.0,
                averageProfit = 0.0,
                averageLoss = 0.0,
                profitFactor = 0.0,
                sharpeRatio = 0.0,
                maxDrawdown = 0.0,
                totalPnL = 0.0,
                totalPnLPercent = 0.0,
                bestTrade = 0.0,
                worstTrade = 0.0
            )
        }
    }

    /**
     * Get all strategies ranked by performance
     */
    suspend fun getAllStrategyPerformances(strategyIds: List<String>): List<StrategyPerformance> {
        return strategyIds.map { calculateStrategyPerformance(it) }
            .sortedByDescending { it.totalPnL } // Sort by profit (best first)
    }

    private fun com.cryptotrader.data.local.entities.TradeEntity.toDomain() = com.cryptotrader.domain.model.Trade(
        id = id,
        orderId = orderId,
        pair = pair,
        type = TradeType.fromString(type),
        price = price,
        volume = volume,
        cost = cost,
        fee = fee,
        timestamp = timestamp,
        strategyId = strategyId,
        status = TradeStatus.fromString(status),
        profit = profit
    )
}

/**
 * Data class holding all performance metrics for a strategy
 */
data class StrategyPerformance(
    val strategyId: String,
    val totalTrades: Int,
    val winningTrades: Int,
    val losingTrades: Int,
    val winRate: Double, // Percentage
    val averageProfit: Double, // USD per winning trade
    val averageLoss: Double, // USD per losing trade
    val profitFactor: Double, // Gross profit / Gross loss
    val sharpeRatio: Double, // Risk-adjusted return
    val maxDrawdown: Double, // Largest peak-to-trough decline (USD)
    val totalPnL: Double, // Total profit/loss (USD)
    val totalPnLPercent: Double, // Total P&L as percentage of starting balance
    val bestTrade: Double, // Largest winning trade (USD)
    val worstTrade: Double // Largest losing trade (USD)
)
