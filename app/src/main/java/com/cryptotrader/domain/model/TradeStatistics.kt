package com.cryptotrader.domain.model

import java.math.BigDecimal

/**
 * Aggregated trade statistics for analytics and reporting
 *
 * Contains comprehensive statistics about trades including:
 * - Trade counts and categorization
 * - Profit/loss metrics
 * - Volume and size analytics
 * - Temporal distribution
 *
 * All monetary values use BigDecimal for exact arithmetic
 */
data class TradeStatistics(
    // Count metrics
    val totalTrades: Int,
    val winningTrades: Int,
    val losingTrades: Int,

    // Volume metrics
    val totalVolume: BigDecimal,
    val averageTradeSize: BigDecimal,

    // Profit/Loss metrics
    val averagePnL: BigDecimal,
    val medianPnL: BigDecimal,
    val largestWin: BigDecimal,
    val largestLoss: BigDecimal,

    // Market analysis
    val mostTradedPair: String?,
    val totalUniquesPairs: Int = 0,

    // Temporal distribution (month -> count)
    // Format: "2024-11" -> 15 trades
    val tradesByMonth: Map<String, Int> = emptyMap(),
) {
    // Win rate calculation
    val winRate: Double
        get() = if (totalTrades > 0) {
            (winningTrades.toDouble() / totalTrades.toDouble()) * 100.0
        } else {
            0.0
        }

    // Loss rate calculation
    val lossRate: Double
        get() = if (totalTrades > 0) {
            (losingTrades.toDouble() / totalTrades.toDouble()) * 100.0
        } else {
            0.0
        }

    // Profit factor (gross wins / gross losses)
    val profitFactor: BigDecimal
        get() = if (losingTrades > 0 && largestLoss != BigDecimal.ZERO) {
            (largestWin * BigDecimal(winningTrades)) / (largestLoss.abs() * BigDecimal(losingTrades))
        } else if (losingTrades == 0 && winningTrades > 0) {
            BigDecimal.TEN // Arbitrary high number when no losses
        } else {
            BigDecimal.ZERO
        }
}
