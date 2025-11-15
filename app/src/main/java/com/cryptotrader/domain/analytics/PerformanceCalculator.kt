package com.cryptotrader.domain.analytics

import com.cryptotrader.domain.model.ChartPoint
import com.cryptotrader.domain.model.PerformanceMetrics
import com.cryptotrader.domain.model.PortfolioSnapshot
import com.cryptotrader.domain.model.TimePeriod
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Performance Calculator
 * Calculates portfolio performance metrics and ROI
 */
@Singleton
class PerformanceCalculator @Inject constructor() {

    /**
     * Calculate performance metrics
     */
    suspend fun calculatePerformance(
        snapshots: List<PortfolioSnapshot>
    ): PerformanceMetrics {
        return try {
            if (snapshots.isEmpty()) {
                return getDefaultMetrics()
            }

            val currentValue = snapshots.lastOrNull()?.totalValue ?: 0.0
            val initialValue = snapshots.firstOrNull()?.totalValue ?: currentValue

            val dailyValues = snapshots.takeLast(2)
            val dailyPnL = if (dailyValues.size == 2) {
                dailyValues[1].totalValue - dailyValues[0].totalValue
            } else 0.0

            val dailyPnLPercent = if (dailyValues.size == 2 && dailyValues[0].totalValue > 0) {
                (dailyPnL / dailyValues[0].totalValue) * 100.0
            } else 0.0

            PerformanceMetrics(
                totalReturn = currentValue - initialValue,
                totalReturnPercent = calculateReturn(currentValue, initialValue),
                roi = calculateROI(currentValue, initialValue),
                dailyPnL = dailyPnL,
                dailyPnLPercent = dailyPnLPercent,
                weeklyReturn = calculatePeriodReturn(snapshots, TimePeriod.ONE_WEEK),
                monthlyReturn = calculatePeriodReturn(snapshots, TimePeriod.ONE_MONTH),
                yearlyReturn = calculatePeriodReturn(snapshots, TimePeriod.ONE_YEAR),
                allTimeReturn = calculateReturn(currentValue, initialValue)
            )
        } catch (e: Exception) {
            Timber.e(e, "Error calculating performance")
            getDefaultMetrics()
        }
    }

    /**
     * Calculate total return percentage
     */
    fun calculateReturn(currentValue: Double, initialValue: Double): Double {
        if (initialValue == 0.0) return 0.0
        return ((currentValue - initialValue) / initialValue) * 100.0
    }

    /**
     * Calculate ROI
     */
    fun calculateROI(gains: Double, costs: Double): Double {
        if (costs == 0.0) return 0.0
        return ((gains - costs) / costs) * 100.0
    }

    /**
     * Calculate period return
     */
    fun calculatePeriodReturn(snapshots: List<PortfolioSnapshot>, period: TimePeriod): Double {
        if (snapshots.isEmpty()) return 0.0

        val now = System.currentTimeMillis()
        val startTime = when (period) {
            TimePeriod.ONE_DAY -> now - (24 * 60 * 60 * 1000)
            TimePeriod.ONE_WEEK -> now - (7 * 24 * 60 * 60 * 1000)
            TimePeriod.ONE_MONTH -> now - (30L * 24 * 60 * 60 * 1000)
            TimePeriod.THREE_MONTHS -> now - (90L * 24 * 60 * 60 * 1000)
            TimePeriod.SIX_MONTHS -> now - (180L * 24 * 60 * 60 * 1000)
            TimePeriod.ONE_YEAR -> now - (365L * 24 * 60 * 60 * 1000)
            TimePeriod.ALL_TIME -> 0L
        }

        val periodSnapshots = snapshots.filter { it.timestamp >= startTime }
        if (periodSnapshots.isEmpty()) return 0.0

        val startValue = periodSnapshots.first().totalValue
        val endValue = periodSnapshots.last().totalValue

        return calculateReturn(endValue, startValue)
    }

    /**
     * Convert snapshots to chart points
     */
    fun snapshotsToChartPoints(snapshots: List<PortfolioSnapshot>): List<ChartPoint> {
        return snapshots.map { snapshot ->
            ChartPoint(
                timestamp = snapshot.timestamp,
                value = snapshot.totalValue
            )
        }
    }

    /**
     * Get default metrics
     */
    private fun getDefaultMetrics(): PerformanceMetrics {
        return PerformanceMetrics(
            totalReturn = 0.0,
            totalReturnPercent = 0.0,
            roi = 0.0,
            dailyPnL = 0.0,
            dailyPnLPercent = 0.0,
            weeklyReturn = 0.0,
            monthlyReturn = 0.0,
            yearlyReturn = 0.0,
            allTimeReturn = 0.0
        )
    }
}
