package com.cryptotrader.domain.trading

import android.content.Context
import com.cryptotrader.data.local.dao.TradeDao
import com.cryptotrader.domain.model.Trade
import com.cryptotrader.domain.model.TradeType
import com.cryptotrader.utils.CryptoUtils
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Calculates profit & loss (P&L) from trading history
 */
@Singleton
class ProfitCalculator @Inject constructor(
    private val tradeDao: TradeDao,
    private val context: Context
) {

    /**
     * Calculate total P&L from all trades
     * Returns: (totalPnL, totalPnLPercent, startingBalance)
     */
    suspend fun calculateTotalPnL(): Triple<Double, Double, Double> {
        return try {
            val isPaperTrading = CryptoUtils.isPaperTradingMode(context)

            val startingBalance = if (isPaperTrading) {
                10000.0 // Paper trading starts with $10k
            } else {
                // TODO: Track actual starting balance when user first deposits
                10000.0 // Placeholder
            }

            val allTrades = tradeDao.getAllTradesFlow().first()
            if (allTrades.isEmpty()) {
                return Triple(0.0, 0.0, startingBalance)
            }

            var totalPnL = 0.0
            val positionsMap = mutableMapOf<String, MutableList<Trade>>() // pair -> trades

            allTrades.sortedBy { it.timestamp }.forEach { tradeEntity ->
                val trade = tradeEntity.toDomain()
                val positions = positionsMap.getOrPut(trade.pair) { mutableListOf() }

                when (trade.type) {
                    TradeType.BUY -> {
                        // Open position
                        positions.add(trade)
                    }
                    TradeType.SELL -> {
                        // Close position - calculate P&L
                        if (positions.isNotEmpty()) {
                            val buyTrade = positions.removeAt(0) // FIFO
                            val pnl = (trade.price - buyTrade.price) * trade.volume - trade.fee - buyTrade.fee
                            totalPnL += pnl
                            Timber.d("P&L for ${trade.pair}: $${"%.2f".format(pnl)}")
                        }
                    }
                }
            }

            val totalPnLPercent = (totalPnL / startingBalance) * 100.0

            Timber.d("Total P&L: $${"%.2f".format(totalPnL)} (${"%.2f".format(totalPnLPercent)}%)")
            Triple(totalPnL, totalPnLPercent, startingBalance)
        } catch (e: Exception) {
            Timber.e(e, "Error calculating P&L")
            Triple(0.0, 0.0, 10000.0)
        }
    }

    /**
     * Calculate P&L for today only (since midnight)
     */
    suspend fun calculateDailyPnL(): Triple<Double, Double, Int> {
        return try {
            val midnightToday = getTodayMidnightTimestamp()
            val todayTrades = tradeDao.getTradesSince(midnightToday).first()

            if (todayTrades.isEmpty()) {
                return Triple(0.0, 0.0, 0)
            }

            val (totalPnL, _, startingBalance) = calculateTotalPnL()

            // Calculate P&L for today's trades only
            var dailyPnL = 0.0
            val positionsMap = mutableMapOf<String, MutableList<Trade>>()

            todayTrades.sortedBy { it.timestamp }.forEach { tradeEntity ->
                val trade = tradeEntity.toDomain()
                val positions = positionsMap.getOrPut(trade.pair) { mutableListOf() }

                when (trade.type) {
                    TradeType.BUY -> positions.add(trade)
                    TradeType.SELL -> {
                        if (positions.isNotEmpty()) {
                            val buyTrade = positions.removeAt(0)
                            val pnl = (trade.price - buyTrade.price) * trade.volume - trade.fee - buyTrade.fee
                            dailyPnL += pnl
                        }
                    }
                }
            }

            val dailyPnLPercent = (dailyPnL / startingBalance) * 100.0
            Triple(dailyPnL, dailyPnLPercent, todayTrades.size)
        } catch (e: Exception) {
            Timber.e(e, "Error calculating daily P&L")
            Triple(0.0, 0.0, 0)
        }
    }

    /**
     * Calculate P&L for last 7 days
     */
    suspend fun calculateWeeklyPnL(): Triple<Double, Double, Int> {
        return try {
            val weekAgoTimestamp = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000)
            val weekTrades = tradeDao.getTradesSince(weekAgoTimestamp).first()

            if (weekTrades.isEmpty()) {
                return Triple(0.0, 0.0, 0)
            }

            val (_, _, startingBalance) = calculateTotalPnL()

            var weeklyPnL = 0.0
            val positionsMap = mutableMapOf<String, MutableList<Trade>>()

            weekTrades.sortedBy { it.timestamp }.forEach { tradeEntity ->
                val trade = tradeEntity.toDomain()
                val positions = positionsMap.getOrPut(trade.pair) { mutableListOf() }

                when (trade.type) {
                    TradeType.BUY -> positions.add(trade)
                    TradeType.SELL -> {
                        if (positions.isNotEmpty()) {
                            val buyTrade = positions.removeAt(0)
                            val pnl = (trade.price - buyTrade.price) * trade.volume - trade.fee - buyTrade.fee
                            weeklyPnL += pnl
                        }
                    }
                }
            }

            val weeklyPnLPercent = (weeklyPnL / startingBalance) * 100.0
            Triple(weeklyPnL, weeklyPnLPercent, weekTrades.size)
        } catch (e: Exception) {
            Timber.e(e, "Error calculating weekly P&L")
            Triple(0.0, 0.0, 0)
        }
    }

    /**
     * Get midnight timestamp for today
     */
    private fun getTodayMidnightTimestamp(): Long {
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private fun com.cryptotrader.data.local.entities.TradeEntity.toDomain() = Trade(
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
        status = com.cryptotrader.domain.model.TradeStatus.fromString(status),
        profit = profit
    )
}
