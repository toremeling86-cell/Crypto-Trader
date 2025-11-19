package com.cryptotrader.domain.trading

import android.content.Context
import com.cryptotrader.data.local.dao.TradeDao
import com.cryptotrader.domain.model.Trade
import com.cryptotrader.domain.model.TradeType
import com.cryptotrader.utils.CryptoUtils
import com.cryptotrader.utils.toBigDecimalMoney
import com.cryptotrader.utils.safeDiv
import kotlinx.coroutines.flow.first
import timber.log.Timber
import java.math.BigDecimal
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Calculates profit & loss (P&L) from trading history
 *
 * BigDecimal Migration (Phase 2.9):
 * - All new calculation methods use BigDecimal for exact arithmetic
 * - Double methods deprecated but maintained for backward compatibility
 * - FIFO matching logic uses exact decimal precision
 */
@Singleton
class ProfitCalculator @Inject constructor(
    private val tradeDao: TradeDao,
    private val context: Context
) {

    /**
     * Mutable wrapper for Trade to track remaining volume in FIFO matching
     * @deprecated Use MutablePositionDecimal for exact calculations
     */
    @Deprecated("Use MutablePositionDecimal for exact calculations", ReplaceWith("MutablePositionDecimal"))
    private data class MutablePosition(
        val trade: Trade,
        var remainingVolume: Double
    )

    /**
     * Mutable wrapper for Trade to track remaining volume in FIFO matching (BigDecimal version)
     */
    private data class MutablePositionDecimal(
        val trade: Trade,
        var remainingVolume: BigDecimal
    )

    /**
     * Calculate total P&L from all trades (BigDecimal version - exact arithmetic)
     * Returns: (totalPnL, totalPnLPercent, startingBalance)
     */
    suspend fun calculateTotalPnLDecimal(): Triple<BigDecimal, BigDecimal, BigDecimal> {
        return try {
            val isPaperTrading = CryptoUtils.isPaperTradingMode(context)

            val startingBalance = if (isPaperTrading) {
                BigDecimal("10000.0") // Paper trading starts with $10k
            } else {
                // TODO: Track actual starting balance when user first deposits
                BigDecimal("10000.0") // Placeholder
            }

            val allTrades = tradeDao.getAllTradesFlow().first()
            if (allTrades.isEmpty()) {
                return Triple(BigDecimal.ZERO, BigDecimal.ZERO, startingBalance)
            }

            var totalPnL = BigDecimal.ZERO
            val positionsMap = mutableMapOf<String, MutableList<MutablePositionDecimal>>() // pair -> positions

            allTrades.sortedBy { it.timestamp }.forEach { tradeEntity ->
                val trade = tradeEntity.toDomain()
                val positions = positionsMap.getOrPut(trade.pair) { mutableListOf() }

                when (trade.type) {
                    TradeType.BUY -> {
                        // Open position - wrap trade in mutable position
                        positions.add(MutablePositionDecimal(trade, trade.volumeDecimal))
                        Timber.d("FIFO: Added BUY position for ${trade.pair}: ${trade.volumeDecimal.toPlainString()} @ $${trade.priceDecimal.toPlainString()}")
                    }
                    TradeType.SELL -> {
                        // Close position(s) - calculate P&L using FIFO matching
                        var remainingVolume = trade.volumeDecimal
                        var matchedPositions = 0

                        Timber.d("FIFO: Processing SELL for ${trade.pair}: ${trade.volumeDecimal.toPlainString()} @ $${trade.priceDecimal.toPlainString()}")

                        while (remainingVolume > EPSILON_DECIMAL && positions.isNotEmpty()) {
                            val buyPosition = positions.first()
                            val matchVolume = remainingVolume.min(buyPosition.remainingVolume)

                            // Calculate P&L for this matched portion
                            val proceeds = trade.priceDecimal * matchVolume
                            val cost = buyPosition.trade.priceDecimal * matchVolume

                            // Prorate fees based on matched volume
                            val sellFeeForMatch = trade.feeDecimal * (matchVolume safeDiv trade.volumeDecimal)
                            val buyFeeForMatch = buyPosition.trade.feeDecimal * (matchVolume safeDiv buyPosition.trade.volumeDecimal)

                            val pnl = proceeds - cost - sellFeeForMatch - buyFeeForMatch
                            totalPnL += pnl
                            matchedPositions++

                            Timber.d("FIFO: Matched ${matchVolume.toPlainString()} against BUY @ $${buyPosition.trade.priceDecimal.toPlainString()}, P&L: $${pnl.setScale(2, java.math.RoundingMode.HALF_EVEN).toPlainString()}")

                            // Update buy position volume
                            buyPosition.remainingVolume -= matchVolume
                            if (buyPosition.remainingVolume <= EPSILON_DECIMAL) {
                                positions.removeAt(0)
                                Timber.d("FIFO: Fully consumed BUY position")
                            } else {
                                Timber.d("FIFO: Partial fill, ${buyPosition.remainingVolume.toPlainString()} remaining")
                            }

                            remainingVolume -= matchVolume
                        }

                        // Warn if sell volume exceeds available buy positions
                        if (remainingVolume > EPSILON_DECIMAL) {
                            Timber.w("FIFO: SELL volume exceeds available BUY positions for ${trade.pair}. " +
                                    "Unmatched volume: ${remainingVolume.toPlainString()}")
                        }

                        Timber.d("FIFO: Completed SELL matching. Matched ${matchedPositions} position(s), Total P&L: $${totalPnL.setScale(2, java.math.RoundingMode.HALF_EVEN).toPlainString()}")
                    }
                }
            }

            val totalPnLPercent = (totalPnL safeDiv startingBalance) * BigDecimal("100")

            Timber.d("Total P&L: $${totalPnL.setScale(2, java.math.RoundingMode.HALF_EVEN).toPlainString()} (${totalPnLPercent.setScale(2, java.math.RoundingMode.HALF_EVEN).toPlainString()}%)")
            Triple(totalPnL, totalPnLPercent, startingBalance)
        } catch (e: Exception) {
            Timber.e(e, "Error calculating P&L")
            Triple(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal("10000.0"))
        }
    }

    /**
     * Calculate P&L for today only (since midnight) - BigDecimal version
     */
    suspend fun calculateDailyPnLDecimal(): Triple<BigDecimal, BigDecimal, Int> {
        return try {
            val midnightToday = getTodayMidnightTimestamp()
            val todayTrades = tradeDao.getTradesSince(midnightToday).first()

            if (todayTrades.isEmpty()) {
                return Triple(BigDecimal.ZERO, BigDecimal.ZERO, 0)
            }

            val (_, _, startingBalance) = calculateTotalPnLDecimal()

            // Calculate P&L for today's trades only
            var dailyPnL = BigDecimal.ZERO
            val positionsMap = mutableMapOf<String, MutableList<MutablePositionDecimal>>()

            todayTrades.sortedBy { it.timestamp }.forEach { tradeEntity ->
                val trade = tradeEntity.toDomain()
                val positions = positionsMap.getOrPut(trade.pair) { mutableListOf() }

                when (trade.type) {
                    TradeType.BUY -> {
                        positions.add(MutablePositionDecimal(trade, trade.volumeDecimal))
                    }
                    TradeType.SELL -> {
                        var remainingVolume = trade.volumeDecimal

                        while (remainingVolume > EPSILON_DECIMAL && positions.isNotEmpty()) {
                            val buyPosition = positions.first()
                            val matchVolume = remainingVolume.min(buyPosition.remainingVolume)

                            // Calculate P&L for this matched portion
                            val proceeds = trade.priceDecimal * matchVolume
                            val cost = buyPosition.trade.priceDecimal * matchVolume

                            // Prorate fees based on matched volume
                            val sellFeeForMatch = trade.feeDecimal * (matchVolume safeDiv trade.volumeDecimal)
                            val buyFeeForMatch = buyPosition.trade.feeDecimal * (matchVolume safeDiv buyPosition.trade.volumeDecimal)

                            val pnl = proceeds - cost - sellFeeForMatch - buyFeeForMatch
                            dailyPnL += pnl

                            // Update buy position volume
                            buyPosition.remainingVolume -= matchVolume
                            if (buyPosition.remainingVolume <= EPSILON_DECIMAL) {
                                positions.removeAt(0)
                            }

                            remainingVolume -= matchVolume
                        }

                        // Warn if sell volume exceeds available buy positions
                        if (remainingVolume > EPSILON_DECIMAL) {
                            Timber.w("FIFO (Daily): SELL volume exceeds available BUY positions for ${trade.pair}. " +
                                    "Unmatched volume: ${remainingVolume.toPlainString()}")
                        }
                    }
                }
            }

            val dailyPnLPercent = (dailyPnL safeDiv startingBalance) * BigDecimal("100")
            Triple(dailyPnL, dailyPnLPercent, todayTrades.size)
        } catch (e: Exception) {
            Timber.e(e, "Error calculating daily P&L")
            Triple(BigDecimal.ZERO, BigDecimal.ZERO, 0)
        }
    }

    /**
     * Calculate P&L for last 7 days - BigDecimal version
     */
    suspend fun calculateWeeklyPnLDecimal(): Triple<BigDecimal, BigDecimal, Int> {
        return try {
            val weekAgoTimestamp = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000)
            val weekTrades = tradeDao.getTradesSince(weekAgoTimestamp).first()

            if (weekTrades.isEmpty()) {
                return Triple(BigDecimal.ZERO, BigDecimal.ZERO, 0)
            }

            val (_, _, startingBalance) = calculateTotalPnLDecimal()

            var weeklyPnL = BigDecimal.ZERO
            val positionsMap = mutableMapOf<String, MutableList<MutablePositionDecimal>>()

            weekTrades.sortedBy { it.timestamp }.forEach { tradeEntity ->
                val trade = tradeEntity.toDomain()
                val positions = positionsMap.getOrPut(trade.pair) { mutableListOf() }

                when (trade.type) {
                    TradeType.BUY -> {
                        positions.add(MutablePositionDecimal(trade, trade.volumeDecimal))
                    }
                    TradeType.SELL -> {
                        var remainingVolume = trade.volumeDecimal

                        while (remainingVolume > EPSILON_DECIMAL && positions.isNotEmpty()) {
                            val buyPosition = positions.first()
                            val matchVolume = remainingVolume.min(buyPosition.remainingVolume)

                            // Calculate P&L for this matched portion
                            val proceeds = trade.priceDecimal * matchVolume
                            val cost = buyPosition.trade.priceDecimal * matchVolume

                            // Prorate fees based on matched volume
                            val sellFeeForMatch = trade.feeDecimal * (matchVolume safeDiv trade.volumeDecimal)
                            val buyFeeForMatch = buyPosition.trade.feeDecimal * (matchVolume safeDiv buyPosition.trade.volumeDecimal)

                            val pnl = proceeds - cost - sellFeeForMatch - buyFeeForMatch
                            weeklyPnL += pnl

                            // Update buy position volume
                            buyPosition.remainingVolume -= matchVolume
                            if (buyPosition.remainingVolume <= EPSILON_DECIMAL) {
                                positions.removeAt(0)
                            }

                            remainingVolume -= matchVolume
                        }

                        // Warn if sell volume exceeds available buy positions
                        if (remainingVolume > EPSILON_DECIMAL) {
                            Timber.w("FIFO (Weekly): SELL volume exceeds available BUY positions for ${trade.pair}. " +
                                    "Unmatched volume: ${remainingVolume.toPlainString()}")
                        }
                    }
                }
            }

            val weeklyPnLPercent = (weeklyPnL safeDiv startingBalance) * BigDecimal("100")
            Triple(weeklyPnL, weeklyPnLPercent, weekTrades.size)
        } catch (e: Exception) {
            Timber.e(e, "Error calculating weekly P&L")
            Triple(BigDecimal.ZERO, BigDecimal.ZERO, 0)
        }
    }

    // ==================== DEPRECATED DOUBLE METHODS (Backward Compatibility) ====================

    /**
     * Calculate total P&L from all trades
     * Returns: (totalPnL, totalPnLPercent, startingBalance)
     * @deprecated Use calculateTotalPnLDecimal() for exact calculations
     */
    @Deprecated("Use calculateTotalPnLDecimal() for exact calculations", ReplaceWith("calculateTotalPnLDecimal()"))
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

    /**
     * Calculate P&L for today only (since midnight)
     * @deprecated Use calculateDailyPnLDecimal() for exact calculations
     */
    @Deprecated("Use calculateDailyPnLDecimal() for exact calculations", ReplaceWith("calculateDailyPnLDecimal()"))
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
     * @deprecated Use calculateWeeklyPnLDecimal() for exact calculations
     */
    @Deprecated("Use calculateWeeklyPnLDecimal() for exact calculations", ReplaceWith("calculateWeeklyPnLDecimal()"))
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

    // ==================== PRIVATE HELPER METHODS ====================

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

    /**
     * Map TradeEntity to Trade domain model
     * Uses BigDecimal fields from Trade model for exact calculations
     */
    private fun com.cryptotrader.data.local.entities.TradeEntity.toDomain() = Trade(
        id = id,
        orderId = orderId,
        pair = pair,
        type = TradeType.fromString(type),
        price = price,
        priceDecimal = price.toBigDecimalMoney(),
        volume = volume,
        volumeDecimal = volume.toBigDecimalMoney(),
        cost = cost,
        costDecimal = cost.toBigDecimalMoney(),
        fee = fee,
        feeDecimal = fee.toBigDecimalMoney(),
        timestamp = timestamp,
        strategyId = strategyId,
        status = com.cryptotrader.domain.model.TradeStatus.fromString(status),
        profit = profit,
        profitDecimal = profit?.toBigDecimalMoney()
    )

    companion object {
        // Epsilon for floating point comparisons (Double)
        private const val EPSILON = 0.00001

        // Epsilon for BigDecimal comparisons (exact arithmetic)
        private val EPSILON_DECIMAL = BigDecimal("0.00001")
    }
}
