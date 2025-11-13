package com.cryptotrader.domain.trading

import android.content.Context
import com.cryptotrader.domain.model.*
import com.cryptotrader.domain.usecase.TradeRequest
import com.cryptotrader.utils.CryptoUtils
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages paper trading (simulated trading with fake money)
 * Mocks Kraken API responses for safe testing
 */
@Singleton
class PaperTradingManager @Inject constructor(
    private val context: Context
) {

    /**
     * Simulate placing an order in paper trading mode
     * Returns mocked Trade object
     */
    fun simulatePlaceOrder(request: TradeRequest, currentPrice: Double): Result<Trade> {
        return try {
            val currentBalance = CryptoUtils.getPaperTradingBalance(context)
            val tradeCost = request.volume * request.price

            // Check if user has enough paper balance
            if (request.type == TradeType.BUY) {
                if (tradeCost > currentBalance) {
                    return Result.failure(
                        Exception("Insufficient paper trading balance. Available: $${"%.2f".format(currentBalance)}, Required: $${"%.2f".format(tradeCost)}")
                    )
                }

                // Deduct from paper balance
                CryptoUtils.setPaperTradingBalance(context, currentBalance - tradeCost)
            } else {
                // SELL - add to paper balance
                CryptoUtils.setPaperTradingBalance(context, currentBalance + tradeCost)
            }

            // Create simulated trade
            val trade = Trade(
                orderId = "PAPER-${UUID.randomUUID()}",
                pair = request.pair,
                type = request.type,
                price = currentPrice, // Use current market price
                volume = request.volume,
                cost = tradeCost,
                fee = tradeCost * 0.0026, // Kraken fee ~0.26%
                timestamp = System.currentTimeMillis(),
                strategyId = request.strategyId,
                status = TradeStatus.EXECUTED
            )

            // Increment paper trading counter
            CryptoUtils.incrementPaperTradingCount(context)

            Timber.d("📄 Paper Trade Executed: ${trade.type} ${trade.volume} ${trade.pair} @ ${trade.price}")
            Result.success(trade)
        } catch (e: Exception) {
            Timber.e(e, "Error simulating paper trade")
            Result.failure(e)
        }
    }

    /**
     * Simulate getting account balance
     * Returns paper trading balance as USD
     */
    fun simulateGetBalance(): Result<Map<String, String>> {
        return try {
            val paperBalance = CryptoUtils.getPaperTradingBalance(context)
            val balance = mapOf(
                "ZUSD" to "%.4f".format(paperBalance)
            )

            Timber.d("📄 Paper Balance: $${"%.2f".format(paperBalance)}")
            Result.success(balance)
        } catch (e: Exception) {
            Timber.e(e, "Error getting paper balance")
            Result.failure(e)
        }
    }

    /**
     * Check if user can enable live trading
     * Requirements:
     * - Completed 30 days or 50 trades in paper mode
     * - Positive paper trading P&L
     */
    fun canEnableLiveTrading(): Pair<Boolean, String> {
        val hasCompleted = CryptoUtils.hasCompletedPaperTrading(context)
        val (trades, days, _) = CryptoUtils.getPaperTradingStats(context)
        val currentBalance = CryptoUtils.getPaperTradingBalance(context)
        val pnl = currentBalance - 10000.0 // Starting balance was $10k

        return when {
            !hasCompleted -> {
                val tradesNeeded = maxOf(0, 50 - trades)
                val daysNeeded = maxOf(0, 30 - days.toInt())

                val message = buildString {
                    append("⚠️ Paper trading requirements not met:\n")
                    if (tradesNeeded > 0) append("• Need $tradesNeeded more trades (${trades}/50)\n")
                    if (daysNeeded > 0) append("• Need $daysNeeded more days (${days}/30)\n")
                }

                Pair(false, message)
            }
            pnl < 0 -> {
                Pair(false, "⚠️ Paper trading shows losses: $${"%.2f".format(pnl)}\nPractice more before using real money!")
            }
            else -> {
                Pair(true, "✅ Requirements met! Paper trading P&L: +$${"%.2f".format(pnl)}")
            }
        }
    }

    /**
     * Get paper trading performance summary
     */
    fun getPerformanceSummary(): String {
        val (trades, days, completed) = CryptoUtils.getPaperTradingStats(context)
        val currentBalance = CryptoUtils.getPaperTradingBalance(context)
        val pnl = currentBalance - 10000.0
        val pnlPercent = (pnl / 10000.0) * 100.0

        return buildString {
            append("📄 PAPER TRADING MODE\n\n")
            append("Starting Balance: $10,000.00\n")
            append("Current Balance: $${"%.2f".format(currentBalance)}\n")
            append("P&L: $${"%.2f".format(pnl)} (${"%.2f".format(pnlPercent)}%)\n\n")
            append("Trades: $trades / 50\n")
            append("Days: $days / 30\n")
            append("Status: ${if (completed) "✅ Completed" else "🔄 In Progress"}\n")
        }
    }
}
