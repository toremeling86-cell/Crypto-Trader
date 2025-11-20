package com.cryptotrader.data.repository

import com.cryptotrader.domain.model.Trade
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

/**
 * Repository interface for trading performance analytics and metrics
 */
interface AnalyticsRepository {
    fun getPerformanceMetrics(): Flow<PerformanceMetrics>
    fun getPnLOverTime(startDate: Long, endDate: Long, interval: TimeInterval): Flow<List<PnLDataPoint>>
    fun getWinLossDistribution(): Flow<WinLossStats>
    fun getTradesPerPair(): Flow<Map<String, Int>>
    fun getStrategyPerformance(): Flow<List<StrategyPerformance>>
    fun getTopTrades(limit: Int): Flow<List<Trade>>
    fun getWorstTrades(limit: Int): Flow<List<Trade>>
}

// Data models
data class PerformanceMetrics(
    val totalPnL: BigDecimal,
    val winRate: Double,
    val profitFactor: Double,
    val sharpeRatio: Double?,
    val maxDrawdown: Double,
    val totalTrades: Int,
    val openPositions: Int,
    val bestTrade: BigDecimal,
    val worstTrade: BigDecimal
)

data class StrategyPerformance(
    val strategyId: String,
    val strategyName: String,
    val totalPnL: BigDecimal,
    val winRate: Double,
    val totalTrades: Int,
    val profitFactor: Double,
    val sharpeRatio: Double?,
    val maxDrawdown: Double
)

data class PnLDataPoint(
    val timestamp: Long,
    val cumulativePnL: BigDecimal,
    val tradePnL: BigDecimal
)

data class WinLossStats(
    val wins: Int,
    val losses: Int,
    val breakeven: Int
)

enum class TimeInterval {
    HOURLY, DAILY, WEEKLY, MONTHLY
}
