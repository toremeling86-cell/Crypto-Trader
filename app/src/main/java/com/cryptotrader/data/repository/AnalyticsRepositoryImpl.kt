package com.cryptotrader.data.repository

import com.cryptotrader.data.local.dao.PositionDao
import com.cryptotrader.data.local.dao.StrategyDao
import com.cryptotrader.data.local.dao.TradeDao
import com.cryptotrader.data.mapper.toDomain
import com.cryptotrader.di.IoDispatcher
import com.cryptotrader.domain.model.Trade
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Implementation of AnalyticsRepository.
 *
 * Provides comprehensive trading analytics and performance metrics.
 * All monetary calculations use BigDecimal for exact precision.
 *
 * @property tradeDao Data access object for trades
 * @property positionDao Data access object for positions
 * @property strategyDao Data access object for strategies
 * @property ioDispatcher Coroutine dispatcher for IO operations
 */
@Singleton
class AnalyticsRepositoryImpl @Inject constructor(
    private val tradeDao: TradeDao,
    private val positionDao: PositionDao,
    private val strategyDao: StrategyDao,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : AnalyticsRepository {

    override fun getPerformanceMetrics(): Flow<PerformanceMetrics> = flow {
        try {
            // Get all trades from database
            val trades = tradeDao.getAllTradesFlow().map { entities ->
                entities.map { it.toDomain() }
            }.flowOn(ioDispatcher)

            trades.collect { tradeList ->
                // Calculate total P&L (sum of all trade profits)
                val totalPnL = tradeList
                    .mapNotNull { it.profitDecimal }
                    .fold(BigDecimal.ZERO) { acc, pnl -> acc + pnl }

                // Calculate win rate
                val executedTrades = tradeList.filter { it.profitDecimal != null }
                val winningTrades = executedTrades.count { it.profitDecimal!! > BigDecimal.ZERO }
                val winRate = if (executedTrades.isNotEmpty()) {
                    (winningTrades.toDouble() / executedTrades.size) * 100.0
                } else {
                    0.0
                }

                // Find best and worst trades
                val bestTrade = tradeList
                    .mapNotNull { it.profitDecimal }
                    .maxOrNull() ?: BigDecimal.ZERO

                val worstTrade = tradeList
                    .mapNotNull { it.profitDecimal }
                    .minOrNull() ?: BigDecimal.ZERO

                // Calculate profit factor
                val profitFactor = calculateProfitFactor(executedTrades)

                // Get open positions count
                val openPositions = try {
                    positionDao.getOpenPositionsSnapshot().size
                } catch (e: Exception) {
                    Timber.e(e, "Error getting open positions count")
                    0
                }

                emit(
                    PerformanceMetrics(
                        totalPnL = totalPnL,
                        winRate = winRate,
                        totalTrades = executedTrades.size,
                        openPositions = openPositions,
                        bestTrade = bestTrade,
                        worstTrade = worstTrade,
                        profitFactor = profitFactor,
                        sharpeRatio = null,  // TODO: Implement Sharpe ratio calculation
                        maxDrawdown = calculateMaxDrawdown(tradeList)
                    )
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Error calculating performance metrics")
            emit(
                PerformanceMetrics(
                    totalPnL = BigDecimal.ZERO,
                    winRate = 0.0,
                    totalTrades = 0,
                    openPositions = 0,
                    bestTrade = BigDecimal.ZERO,
                    worstTrade = BigDecimal.ZERO,
                    profitFactor = 0.0,
                    sharpeRatio = null,
                    maxDrawdown = 0.0
                )
            )
        }
    }.flowOn(ioDispatcher)

    override fun getPnLOverTime(
        startDate: Long,
        endDate: Long,
        interval: TimeInterval
    ): Flow<List<PnLDataPoint>> = flow {
        try {
            val trades = tradeDao.getTradesBetween(startDate, endDate)
                .map { entities -> entities.map { it.toDomain() } }
                .flowOn(ioDispatcher)

            trades.collect { tradeList ->
                // Sort trades by execution time
                val sortedTrades = tradeList.sortedBy { it.timestamp }

                // Calculate cumulative P&L and create data points
                var cumulativePnL = BigDecimal.ZERO
                val dataPoints = mutableListOf<PnLDataPoint>()

                sortedTrades.forEach { trade ->
                    trade.profitDecimal?.let { pnl ->
                        cumulativePnL += pnl
                        dataPoints.add(
                            PnLDataPoint(
                                timestamp = trade.timestamp,
                                cumulativePnL = cumulativePnL,
                                tradePnL = pnl
                            )
                        )
                    }
                }

                // Aggregate by specified interval
                val aggregated = when (interval) {
                    TimeInterval.HOURLY -> aggregateByInterval(dataPoints, 3600000L)  // 1 hour
                    TimeInterval.DAILY -> aggregateByInterval(dataPoints, 86400000L)   // 1 day
                    TimeInterval.WEEKLY -> aggregateByInterval(dataPoints, 604800000L) // 1 week
                    TimeInterval.MONTHLY -> aggregateByMonth(dataPoints)
                }

                emit(aggregated)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error calculating P&L over time for period $startDate-$endDate")
            emit(emptyList())
        }
    }.flowOn(ioDispatcher)

    override fun getWinLossDistribution(): Flow<WinLossStats> = flow {
        try {
            val trades = tradeDao.getAllTradesFlow()
                .map { entities -> entities.map { it.toDomain() } }
                .flowOn(ioDispatcher)

            trades.collect { tradeList ->
                val executedTrades = tradeList.filter { it.profitDecimal != null }

                val wins = executedTrades.count { it.profitDecimal!! > BigDecimal.ZERO }
                val losses = executedTrades.count { it.profitDecimal!! < BigDecimal.ZERO }
                val breakeven = executedTrades.count { it.profitDecimal!! == BigDecimal.ZERO }

                emit(WinLossStats(wins = wins, losses = losses, breakeven = breakeven))
            }
        } catch (e: Exception) {
            Timber.e(e, "Error calculating win/loss distribution")
            emit(WinLossStats(wins = 0, losses = 0, breakeven = 0))
        }
    }.flowOn(ioDispatcher)

    override fun getTradesPerPair(): Flow<Map<String, Int>> = flow {
        try {
            val trades = tradeDao.getAllTradesFlow()
                .map { entities -> entities.map { it.toDomain() } }
                .flowOn(ioDispatcher)

            trades.collect { tradeList ->
                val tradesPerPair = tradeList
                    .filter { it.profitDecimal != null }  // Only count executed trades
                    .groupingBy { it.pair }
                    .eachCount()

                emit(tradesPerPair)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error calculating trades per pair")
            emit(emptyMap())
        }
    }.flowOn(ioDispatcher)

    override fun getStrategyPerformance(): Flow<List<StrategyPerformance>> = flow {
        try {
            val strategies = strategyDao.getAllStrategies()
                .flowOn(ioDispatcher)

            val trades = tradeDao.getAllTradesFlow()
                .map { entities -> entities.map { it.toDomain() } }
                .flowOn(ioDispatcher)

            // Combine strategies and trades flows
            strategies.collect { strategyEntities ->
                trades.collect { tradeList ->
                    val performanceList = mutableListOf<StrategyPerformance>()

                    for (strategyEntity in strategyEntities) {
                        val strategyTrades = tradeList.filter {
                            it.strategyId == strategyEntity.id && it.profitDecimal != null
                        }

                        if (strategyTrades.isNotEmpty()) {
                            val totalTrades = strategyTrades.size
                            val winningTrades = strategyTrades.count { it.profitDecimal!! > BigDecimal.ZERO }
                            val winRate = (winningTrades.toDouble() / totalTrades) * 100.0

                            val totalPnL = strategyTrades
                                .mapNotNull { it.profitDecimal }
                                .fold(BigDecimal.ZERO) { acc, pnl -> acc + pnl }

                            val profitFactor = calculateProfitFactor(strategyTrades)
                            val maxDrawdown = calculateMaxDrawdown(strategyTrades)

                            performanceList.add(
                                StrategyPerformance(
                                    strategyId = strategyEntity.id,
                                    strategyName = strategyEntity.name,
                                    totalTrades = totalTrades,
                                    winRate = winRate,
                                    totalPnL = totalPnL,
                                    profitFactor = profitFactor,
                                    sharpeRatio = null,  // TODO: Implement Sharpe ratio
                                    maxDrawdown = maxDrawdown
                                )
                            )
                        }
                    }

                    // Sort by total P&L descending
                    emit(performanceList.sortedByDescending { it.totalPnL })
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error calculating strategy performance")
            emit(emptyList())
        }
    }.flowOn(ioDispatcher)

    override fun getTopTrades(limit: Int): Flow<List<Trade>> = flow {
        try {
            val trades = tradeDao.getAllTradesFlow()
                .map { entities -> entities.map { it.toDomain() } }
                .flowOn(ioDispatcher)

            trades.collect { tradeList ->
                val topTrades = tradeList
                    .filter { it.profitDecimal != null }
                    .sortedByDescending { it.profitDecimal }
                    .take(limit)

                emit(topTrades)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error getting top trades")
            emit(emptyList())
        }
    }.flowOn(ioDispatcher)

    override fun getWorstTrades(limit: Int): Flow<List<Trade>> = flow {
        try {
            val trades = tradeDao.getAllTradesFlow()
                .map { entities -> entities.map { it.toDomain() } }
                .flowOn(ioDispatcher)

            trades.collect { tradeList ->
                val worstTrades = tradeList
                    .filter { it.profitDecimal != null }
                    .sortedBy { it.profitDecimal }
                    .take(limit)

                emit(worstTrades)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error getting worst trades")
            emit(emptyList())
        }
    }.flowOn(ioDispatcher)

    // Private helper functions

    /**
     * Calculate profit factor (gross profit / gross loss).
     *
     * Uses BigDecimal for exact calculations. Returns 0.0 if no losing trades.
     *
     * @param trades List of trades with profit information
     * @return Profit factor as Double (>1.0 indicates profitable)
     */
    private fun calculateProfitFactor(trades: List<Trade>): Double {
        val grossProfit = trades
            .filter { it.profitDecimal != null && it.profitDecimal > BigDecimal.ZERO }
            .sumOf { it.profitDecimal!! }

        val grossLoss = trades
            .filter { it.profitDecimal != null && it.profitDecimal < BigDecimal.ZERO }
            .sumOf { it.profitDecimal!!.abs() }

        return if (grossLoss > BigDecimal.ZERO) {
            (grossProfit.toDouble() / grossLoss.toDouble())
        } else {
            0.0
        }
    }

    /**
     * Calculate maximum drawdown as a percentage.
     *
     * Drawdown is the peak-to-trough decline in cumulative P&L.
     * Uses BigDecimal for exact calculations.
     *
     * @param trades List of trades sorted by timestamp
     * @return Maximum drawdown as percentage (0.0 if no drawdown)
     */
    private fun calculateMaxDrawdown(trades: List<Trade>): Double {
        if (trades.isEmpty()) return 0.0

        val sortedTrades = trades.sortedBy { it.timestamp }
        var cumulativePnL = BigDecimal.ZERO
        var peak = BigDecimal.ZERO
        var maxDrawdown = BigDecimal.ZERO

        for (trade in sortedTrades) {
            trade.profitDecimal?.let { pnl ->
                cumulativePnL += pnl

                if (cumulativePnL > peak) {
                    peak = cumulativePnL
                }

                val currentDrawdown = (peak - cumulativePnL).abs()
                if (currentDrawdown > maxDrawdown) {
                    maxDrawdown = currentDrawdown
                }
            }
        }

        // Calculate drawdown percentage
        return if (peak > BigDecimal.ZERO) {
            (maxDrawdown / peak * BigDecimal("100"))
                .setScale(2, RoundingMode.HALF_UP)
                .toDouble()
        } else {
            0.0
        }
    }

    /**
     * Aggregate P&L data points by fixed time interval.
     *
     * Groups trades into buckets of specified duration and uses the last
     * cumulative P&L value in each bucket as the representative value.
     *
     * @param dataPoints Original P&L data points
     * @param intervalMs Interval duration in milliseconds
     * @return Aggregated data points
     */
    private fun aggregateByInterval(
        dataPoints: List<PnLDataPoint>,
        intervalMs: Long
    ): List<PnLDataPoint> {
        if (dataPoints.isEmpty()) return emptyList()

        val aggregated = mutableListOf<PnLDataPoint>()
        var currentBucket = mutableListOf<PnLDataPoint>()
        var bucketStartTime = dataPoints.first().timestamp

        for (point in dataPoints) {
            if (point.timestamp < bucketStartTime + intervalMs) {
                currentBucket.add(point)
            } else {
                // Save current bucket
                currentBucket.lastOrNull()?.let { aggregated.add(it) }

                // Start new bucket
                bucketStartTime = point.timestamp
                currentBucket = mutableListOf(point)
            }
        }

        // Save last bucket
        currentBucket.lastOrNull()?.let { aggregated.add(it) }

        return aggregated
    }

    /**
     * Aggregate P&L data points by calendar month.
     *
     * Groups trades into calendar months and uses the last cumulative P&L
     * value in each month as the representative value.
     *
     * @param dataPoints Original P&L data points
     * @return Aggregated data points
     */
    private fun aggregateByMonth(dataPoints: List<PnLDataPoint>): List<PnLDataPoint> {
        if (dataPoints.isEmpty()) return emptyList()

        val aggregated = mutableListOf<PnLDataPoint>()
        var currentMonth = -1
        var currentYear = -1
        var lastPointInMonth: PnLDataPoint? = null

        for (point in dataPoints) {
            val calendar = Calendar.getInstance().apply {
                timeInMillis = point.timestamp
            }
            val month = calendar.get(Calendar.MONTH)
            val year = calendar.get(Calendar.YEAR)

            if (year != currentYear || month != currentMonth) {
                // Month changed, save previous month's last point
                lastPointInMonth?.let { aggregated.add(it) }

                currentYear = year
                currentMonth = month
            }

            lastPointInMonth = point
        }

        // Save last month's last point
        lastPointInMonth?.let { aggregated.add(it) }

        return aggregated
    }
}
