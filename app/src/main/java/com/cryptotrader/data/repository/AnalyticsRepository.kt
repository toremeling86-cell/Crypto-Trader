package com.cryptotrader.data.repository

import com.cryptotrader.domain.model.Trade
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal

/**
 * Repository interface for trading analytics and performance metrics.
 *
 * Provides comprehensive performance analytics for the UI Analytics Dashboard,
 * including P&L calculations, win/loss statistics, and strategy comparisons.
 *
 * All monetary calculations use BigDecimal for exact precision.
 */
interface AnalyticsRepository {
    /**
     * Get comprehensive performance metrics for all trades.
     *
     * Calculates key performance indicators including total P&L, win rate,
     * profit factor, and Sharpe ratio.
     *
     * @return Flow emitting the current PerformanceMetrics
     */
    fun getPerformanceMetrics(): Flow<PerformanceMetrics>

    /**
     * Get P&L over time for charting and visualization.
     *
     * Aggregates cumulative P&L at specified time intervals, useful for
     * drawing equity curves and tracking performance over time.
     *
     * @param startDate Start timestamp in milliseconds
     * @param endDate End timestamp in milliseconds
     * @param interval Time aggregation interval (HOURLY, DAILY, WEEKLY, MONTHLY)
     * @return Flow emitting list of P&L data points
     */
    fun getPnLOverTime(
        startDate: Long,
        endDate: Long,
        interval: TimeInterval
    ): Flow<List<PnLDataPoint>>

    /**
     * Get win/loss distribution statistics.
     *
     * Provides counts of winning, losing, and break-even trades for
     * performance analysis and risk assessment.
     *
     * @return Flow emitting WinLossStats with win/loss/breakeven counts
     */
    fun getWinLossDistribution(): Flow<WinLossStats>

    /**
     * Get trade count per trading pair.
     *
     * Useful for analyzing which pairs are being traded most frequently
     * and their relative performance contribution.
     *
     * @return Flow emitting map of pair symbols to trade counts
     */
    fun getTradesPerPair(): Flow<Map<String, Int>>

    /**
     * Get performance metrics for each strategy.
     *
     * Compares strategy performance including win rate, profit factor,
     * and total P&L for strategy ranking and comparison.
     *
     * @return Flow emitting list of StrategyPerformance data
     */
    fun getStrategyPerformance(): Flow<List<StrategyPerformance>>

    /**
     * Get best performing trades (by profit).
     *
     * Retrieves top N trades sorted by profit in descending order.
     * Useful for identifying successful trade patterns.
     *
     * @param limit Maximum number of trades to return (default: 10)
     * @return Flow emitting list of top trades
     */
    fun getTopTrades(limit: Int = 10): Flow<List<Trade>>

    /**
     * Get worst performing trades (by loss).
     *
     * Retrieves top N trades sorted by profit in ascending order.
     * Useful for analyzing failure patterns and risk management.
     *
     * @param limit Maximum number of trades to return (default: 10)
     * @return Flow emitting list of worst trades
     */
    fun getWorstTrades(limit: Int = 10): Flow<List<Trade>>
}

/**
 * Comprehensive performance metrics for trading performance analysis.
 *
 * All monetary values use BigDecimal for exact calculations.
 *
 * @property totalPnL Total profit/loss across all trades
 * @property winRate Percentage of winning trades (0-100)
 * @property totalTrades Total number of executed trades
 * @property openPositions Number of currently open positions
 * @property bestTrade Best single trade profit
 * @property worstTrade Worst single trade loss (negative value)
 * @property profitFactor Ratio of gross profit to gross loss (>1.0 is profitable)
 * @property sharpeRatio Risk-adjusted return metric (null if insufficient data)
 * @property maxDrawdown Maximum peak-to-trough decline in equity curve (percentage)
 */
data class PerformanceMetrics(
    val totalPnL: BigDecimal,
    val winRate: Double,  // 0-100
    val totalTrades: Int,
    val openPositions: Int,
    val bestTrade: BigDecimal,
    val worstTrade: BigDecimal,
    val profitFactor: Double,  // gross_profit / gross_loss
    val sharpeRatio: Double? = null,
    val maxDrawdown: Double  // percentage
)

/**
 * Single point in P&L time series data.
 *
 * Represents cumulative P&L at a specific timestamp, optionally including
 * the P&L from a trade executed at that time.
 *
 * @property timestamp Time in milliseconds
 * @property cumulativePnL Total P&L up to this point
 * @property tradePnL P&L from trade at this timestamp (null if no trade executed)
 */
data class PnLDataPoint(
    val timestamp: Long,
    val cumulativePnL: BigDecimal,
    val tradePnL: BigDecimal? = null  // null if no trade at this timestamp
)

/**
 * Win/loss distribution statistics.
 *
 * @property wins Number of profitable trades
 * @property losses Number of losing trades
 * @property breakeven Number of break-even trades
 */
data class WinLossStats(
    val wins: Int,
    val losses: Int,
    val breakeven: Int
)

/**
 * Strategy performance metrics for comparison and ranking.
 *
 * @property strategyId Unique strategy identifier
 * @property strategyName Human-readable strategy name
 * @property totalTrades Number of trades executed by this strategy
 * @property winRate Percentage of winning trades (0-100)
 * @property totalPnL Total profit/loss for this strategy
 * @property profitFactor Ratio of gross profit to gross loss
 * @property sharpeRatio Risk-adjusted return metric
 * @property maxDrawdown Maximum drawdown percentage
 */
data class StrategyPerformance(
    val strategyId: String,
    val strategyName: String,
    val totalTrades: Int,
    val winRate: Double,  // 0-100
    val totalPnL: BigDecimal,
    val profitFactor: Double,
    val sharpeRatio: Double? = null,
    val maxDrawdown: Double
)

/**
 * Time interval enumeration for P&L aggregation.
 *
 * Used to specify the granularity at which P&L data is aggregated
 * for charting and analysis.
 */
enum class TimeInterval {
    HOURLY,   // 1 hour intervals
    DAILY,    // 1 day intervals
    WEEKLY,   // 1 week intervals
    MONTHLY   // 1 month intervals
}
