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

            // Calculate daily P&L with proper 24-hour time period validation
            val dailyPnL = calculateDailyPnL(snapshots)
            val dailyPnLPercent = calculateDailyPnLPercent(snapshots)

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
     * Calculate daily P&L with proper 24-hour time period validation
     *
     * BUG FIX 9.1: This now validates that snapshots are actually 24 hours apart
     * instead of just taking the last 2 snapshots.
     *
     * @param snapshots List of portfolio snapshots ordered by timestamp
     * @return Daily P&L amount
     */
    fun calculateDailyPnL(snapshots: List<PortfolioSnapshot>): Double {
        if (snapshots.isEmpty()) {
            Timber.d("No snapshots available for daily P&L calculation")
            return 0.0
        }

        val now = System.currentTimeMillis()
        val oneDayAgo = now - (24 * 60 * 60 * 1000)

        // Get snapshot from 24 hours ago (or closest before that)
        val startSnapshot = snapshots.lastOrNull { it.timestamp <= oneDayAgo }
        val endSnapshot = snapshots.lastOrNull()

        return if (startSnapshot != null && endSnapshot != null) {
            val pnl = endSnapshot.totalValue - startSnapshot.totalValue
            val hoursDiff = (endSnapshot.timestamp - startSnapshot.timestamp) / (60 * 60 * 1000.0)

            Timber.d("Daily P&L: Start=${startSnapshot.totalValue} (${java.util.Date(startSnapshot.timestamp)}), " +
                    "End=${endSnapshot.totalValue} (${java.util.Date(endSnapshot.timestamp)}), " +
                    "Hours=${"%.1f".format(hoursDiff)}, P&L=${"%.2f".format(pnl)}")

            // Validate the time period is reasonable (at least 20 hours to count as "daily")
            if (hoursDiff < 20.0) {
                Timber.w("Insufficient time period for daily P&L: ${"%.1f".format(hoursDiff)} hours")
                return 0.0
            }

            pnl
        } else {
            Timber.w("Not enough snapshots for 24-hour P&L calculation (start=${startSnapshot != null}, end=${endSnapshot != null})")
            0.0
        }
    }

    /**
     * Calculate daily P&L percentage with proper 24-hour time period validation
     *
     * @param snapshots List of portfolio snapshots ordered by timestamp
     * @return Daily P&L percentage
     */
    fun calculateDailyPnLPercent(snapshots: List<PortfolioSnapshot>): Double {
        if (snapshots.isEmpty()) return 0.0

        val now = System.currentTimeMillis()
        val oneDayAgo = now - (24 * 60 * 60 * 1000)

        // Get snapshot from 24 hours ago (or closest before that)
        val startSnapshot = snapshots.lastOrNull { it.timestamp <= oneDayAgo }
        val endSnapshot = snapshots.lastOrNull()

        return if (startSnapshot != null && endSnapshot != null && startSnapshot.totalValue > 0) {
            val pnl = endSnapshot.totalValue - startSnapshot.totalValue
            val pnlPercent = (pnl / startSnapshot.totalValue) * 100.0

            val hoursDiff = (endSnapshot.timestamp - startSnapshot.timestamp) / (60 * 60 * 1000.0)

            // Validate the time period is reasonable
            if (hoursDiff < 20.0) {
                Timber.w("Insufficient time period for daily P&L %: ${"%.1f".format(hoursDiff)} hours")
                return 0.0
            }

            Timber.d("Daily P&L %: ${"%.2f".format(pnlPercent)}% over ${"%.1f".format(hoursDiff)} hours")
            pnlPercent
        } else {
            0.0
        }
    }

    /**
     * Calculate Return on Investment (ROI)
     *
     * BUG FIX 9.2: Renamed parameters for clarity - finalValue and initialValue
     * instead of confusing gains and costs.
     *
     * @param finalValue The final portfolio value
     * @param initialValue The initial investment amount
     * @return ROI as a percentage
     */
    fun calculateROI(finalValue: Double, initialValue: Double): Double {
        if (initialValue == 0.0) {
            Timber.w("ROI calculation: Initial value is 0, returning 0%")
            return 0.0
        }

        val roi = ((finalValue - initialValue) / initialValue) * 100.0

        Timber.d("ROI: Initial=${"%.2f".format(initialValue)}, Final=${"%.2f".format(finalValue)}, ROI=${"%.2f".format(roi)}%")

        // Validate ROI is within reasonable bounds (-100% to +10000%)
        if (roi < -100.0 || roi > 10000.0) {
            Timber.w("ROI value seems unreasonable: ${"%.2f".format(roi)}%")
        }

        return roi
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
