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
     * Mutable wrapper for Trade to track remaining volume in FIFO matching
     */
    private data class MutablePosition(
        val trade: Trade,
        var remainingVolume: Double
    )

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
            val positionsMap = mutableMapOf<String, MutableList<MutablePosition>>() // pair -> positions

            allTrades.sortedBy { it.timestamp }.forEach { tradeEntity ->
                val trade = tradeEntity.toDomain()
                val positions = positionsMap.getOrPut(trade.pair) { mutableListOf() }

                when (trade.type) {
                    TradeType.BUY -> {
                        // Open position - wrap trade in mutable position
                        positions.add(MutablePosition(trade, trade.volume))
                        Timber.d("FIFO: Added BUY position for ${trade.pair}: ${trade.volume} @ $${trade.price}")
                    }
                    TradeType.SELL -> {
                        // Close position(s) - calculate P&L using FIFO matching
                        var remainingVolume = trade.volume
                        var matchedPositions = 0

                        Timber.d("FIFO: Processing SELL for ${trade.pair}: ${trade.volume} @ $${trade.price}")

                        while (remainingVolume > EPSILON && positions.isNotEmpty()) {
                            val buyPosition = positions.first()
                            val matchVolume = minOf(remainingVolume, buyPosition.remainingVolume)

                            // Calculate P&L for this matched portion
                            val proceeds = trade.price * matchVolume
                            val cost = buyPosition.trade.price * matchVolume

                            // Prorate fees based on matched volume
                            val sellFeeForMatch = trade.fee * (matchVolume / trade.volume)
                            val buyFeeForMatch = buyPosition.trade.fee * (matchVolume / buyPosition.trade.volume)

                            val pnl = proceeds - cost - sellFeeForMatch - buyFeeForMatch
                            totalPnL += pnl
                            matchedPositions++

                            Timber.d("FIFO: Matched ${matchVolume} against BUY @ $${buyPosition.trade.price}, P&L: $${"%.2f".format(pnl)}")

                            // Update buy position volume
                            buyPosition.remainingVolume -= matchVolume
                            if (buyPosition.remainingVolume <= EPSILON) {
                                positions.removeAt(0)
                                Timber.d("FIFO: Fully consumed BUY position")
                            } else {
                                Timber.d("FIFO: Partial fill, ${buyPosition.remainingVolume} remaining")
                            }

                            remainingVolume -= matchVolume
                        }

                        // Warn if sell volume exceeds available buy positions
                        if (remainingVolume > EPSILON) {
                            Timber.w("FIFO: SELL volume exceeds available BUY positions for ${trade.pair}. " +
                                    "Unmatched volume: ${remainingVolume}")
                        }

                        Timber.d("FIFO: Completed SELL matching. Matched ${matchedPositions} position(s), Total P&L: $${"%.2f".format(totalPnL)}")
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

    companion object {
        // Epsilon for floating point comparisons
        private const val EPSILON = 0.00001
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
            val positionsMap = mutableMapOf<String, MutableList<MutablePosition>>()

            todayTrades.sortedBy { it.timestamp }.forEach { tradeEntity ->
                val trade = tradeEntity.toDomain()
                val positions = positionsMap.getOrPut(trade.pair) { mutableListOf() }

                when (trade.type) {
                    TradeType.BUY -> {
                        positions.add(MutablePosition(trade, trade.volume))
                    }
                    TradeType.SELL -> {
                        var remainingVolume = trade.volume

                        while (remainingVolume > EPSILON && positions.isNotEmpty()) {
                            val buyPosition = positions.first()
                            val matchVolume = minOf(remainingVolume, buyPosition.remainingVolume)

                            // Calculate P&L for this matched portion
                            val proceeds = trade.price * matchVolume
                            val cost = buyPosition.trade.price * matchVolume

                            // Prorate fees based on matched volume
                            val sellFeeForMatch = trade.fee * (matchVolume / trade.volume)
                            val buyFeeForMatch = buyPosition.trade.fee * (matchVolume / buyPosition.trade.volume)

                            val pnl = proceeds - cost - sellFeeForMatch - buyFeeForMatch
                            dailyPnL += pnl

                            // Update buy position volume
                            buyPosition.remainingVolume -= matchVolume
                            if (buyPosition.remainingVolume <= EPSILON) {
                                positions.removeAt(0)
                            }

                            remainingVolume -= matchVolume
                        }

                        // Warn if sell volume exceeds available buy positions
                        if (remainingVolume > EPSILON) {
                            Timber.w("FIFO (Daily): SELL volume exceeds available BUY positions for ${trade.pair}. " +
                                    "Unmatched volume: ${remainingVolume}")
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
            val positionsMap = mutableMapOf<String, MutableList<MutablePosition>>()

            weekTrades.sortedBy { it.timestamp }.forEach { tradeEntity ->
                val trade = tradeEntity.toDomain()
                val positions = positionsMap.getOrPut(trade.pair) { mutableListOf() }

                when (trade.type) {
                    TradeType.BUY -> {
                        positions.add(MutablePosition(trade, trade.volume))
                    }
                    TradeType.SELL -> {
                        var remainingVolume = trade.volume

                        while (remainingVolume > EPSILON && positions.isNotEmpty()) {
                            val buyPosition = positions.first()
                            val matchVolume = minOf(remainingVolume, buyPosition.remainingVolume)

                            // Calculate P&L for this matched portion
                            val proceeds = trade.price * matchVolume
                            val cost = buyPosition.trade.price * matchVolume

                            // Prorate fees based on matched volume
                            val sellFeeForMatch = trade.fee * (matchVolume / trade.volume)
                            val buyFeeForMatch = buyPosition.trade.fee * (matchVolume / buyPosition.trade.volume)

                            val pnl = proceeds - cost - sellFeeForMatch - buyFeeForMatch
                            weeklyPnL += pnl

                            // Update buy position volume
                            buyPosition.remainingVolume -= matchVolume
                            if (buyPosition.remainingVolume <= EPSILON) {
                                positions.removeAt(0)
                            }

                            remainingVolume -= matchVolume
                        }

                        // Warn if sell volume exceeds available buy positions
                        if (remainingVolume > EPSILON) {
                            Timber.w("FIFO (Weekly): SELL volume exceeds available BUY positions for ${trade.pair}. " +
                                    "Unmatched volume: ${remainingVolume}")
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
