package com.cryptotrader.domain.analytics

import com.cryptotrader.domain.model.ChartPoint
import com.cryptotrader.domain.model.PerformanceMetrics
import com.cryptotrader.domain.model.PortfolioSnapshot
import com.cryptotrader.domain.model.TimePeriod
import com.cryptotrader.utils.toBigDecimalMoney
import com.cryptotrader.utils.safeDiv
import com.cryptotrader.utils.percentOf
import timber.log.Timber
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Performance Calculator
 * Calculates portfolio performance metrics and ROI
 *
 * BigDecimal Migration (Phase 3):
 * - All new calculation methods use BigDecimal for exact arithmetic
 * - Double methods deprecated but maintained for backward compatibility
 * - Uses MathContext(8, RoundingMode.HALF_EVEN) for precise calculations
 */
@Singleton
class PerformanceCalculator @Inject constructor() {

    companion object {
        // MathContext for BigDecimal calculations (8 digits precision, HALF_EVEN rounding)
        private val MC = MathContext(8, RoundingMode.HALF_EVEN)
        private val EPSILON = BigDecimal("0.00001")
        private const val HOURS_IN_DAY = 24L
        private const val MILLIS_IN_HOUR = 60 * 60 * 1000L
        private const val MIN_HOURS_FOR_DAILY = 20.0
    }

    // ==================== BIGDECIMAL METHODS (Exact Calculations) ====================

    /**
     * Calculate performance metrics using BigDecimal for exact arithmetic
     */
    suspend fun calculatePerformanceDecimal(
        snapshots: List<PortfolioSnapshot>
    ): PerformanceMetrics {
        return try {
            if (snapshots.isEmpty()) {
                return getDefaultMetricsDecimal()
            }

            val currentValue = snapshots.lastOrNull()?.totalValueDecimal ?: BigDecimal.ZERO
            val initialValue = snapshots.firstOrNull()?.totalValueDecimal ?: currentValue

            // Calculate daily P&L with proper 24-hour time period validation
            val dailyPnL = calculateDailyPnLDecimal(snapshots)
            val dailyPnLPercent = calculateDailyPnLPercentDecimal(snapshots)

            PerformanceMetrics(
                totalReturn = (currentValue - initialValue).toDouble(),
                totalReturnPercent = calculateReturnDecimal(currentValue, initialValue).toDouble(),
                roi = calculateROIDecimal(currentValue, initialValue).toDouble(),
                dailyPnL = dailyPnL.toDouble(),
                dailyPnLPercent = dailyPnLPercent.toDouble(),
                weeklyReturn = calculatePeriodReturnDecimal(snapshots, TimePeriod.ONE_WEEK).toDouble(),
                monthlyReturn = calculatePeriodReturnDecimal(snapshots, TimePeriod.ONE_MONTH).toDouble(),
                yearlyReturn = calculatePeriodReturnDecimal(snapshots, TimePeriod.ONE_YEAR).toDouble(),
                allTimeReturn = calculateReturnDecimal(currentValue, initialValue).toDouble()
            )
        } catch (e: Exception) {
            Timber.e(e, "Error calculating performance")
            getDefaultMetricsDecimal()
        }
    }

    /**
     * Calculate total return percentage using BigDecimal
     */
    fun calculateReturnDecimal(currentValue: BigDecimal, initialValue: BigDecimal): BigDecimal {
        if (initialValue == BigDecimal.ZERO) return BigDecimal.ZERO
        return ((currentValue - initialValue) safeDiv initialValue) * BigDecimal("100")
    }

    /**
     * Calculate daily P&L with proper 24-hour time period validation (BigDecimal version)
     *
     * BUG FIX 9.1: This validates that snapshots are actually 24 hours apart
     * instead of just taking the last 2 snapshots.
     *
     * @param snapshots List of portfolio snapshots ordered by timestamp
     * @return Daily P&L amount (BigDecimal)
     */
    fun calculateDailyPnLDecimal(snapshots: List<PortfolioSnapshot>): BigDecimal {
        if (snapshots.isEmpty()) {
            Timber.d("No snapshots available for daily P&L calculation")
            return BigDecimal.ZERO
        }

        val now = System.currentTimeMillis()
        val oneDayAgo = now - (HOURS_IN_DAY * MILLIS_IN_HOUR)

        // Get snapshot from 24 hours ago (or closest before that)
        val startSnapshot = snapshots.lastOrNull { it.timestamp <= oneDayAgo }
        val endSnapshot = snapshots.lastOrNull()

        return if (startSnapshot != null && endSnapshot != null) {
            val pnl = endSnapshot.totalValueDecimal - startSnapshot.totalValueDecimal
            val hoursDiff = (endSnapshot.timestamp - startSnapshot.timestamp) / (MILLIS_IN_HOUR.toDouble())

            Timber.d("Daily P&L: Start=${startSnapshot.totalValueDecimal.toPlainString()} (${java.util.Date(startSnapshot.timestamp)}), " +
                    "End=${endSnapshot.totalValueDecimal.toPlainString()} (${java.util.Date(endSnapshot.timestamp)}), " +
                    "Hours=${"%.1f".format(hoursDiff)}, P&L=${pnl.setScale(2, RoundingMode.HALF_EVEN).toPlainString()}")

            // Validate the time period is reasonable (at least 20 hours to count as "daily")
            if (hoursDiff < MIN_HOURS_FOR_DAILY) {
                Timber.w("Insufficient time period for daily P&L: ${"%.1f".format(hoursDiff)} hours")
                return BigDecimal.ZERO
            }

            pnl
        } else {
            Timber.w("Not enough snapshots for 24-hour P&L calculation (start=${startSnapshot != null}, end=${endSnapshot != null})")
            BigDecimal.ZERO
        }
    }

    /**
     * Calculate daily P&L percentage with proper 24-hour time period validation (BigDecimal version)
     *
     * @param snapshots List of portfolio snapshots ordered by timestamp
     * @return Daily P&L percentage (BigDecimal)
     */
    fun calculateDailyPnLPercentDecimal(snapshots: List<PortfolioSnapshot>): BigDecimal {
        if (snapshots.isEmpty()) return BigDecimal.ZERO

        val now = System.currentTimeMillis()
        val oneDayAgo = now - (HOURS_IN_DAY * MILLIS_IN_HOUR)

        // Get snapshot from 24 hours ago (or closest before that)
        val startSnapshot = snapshots.lastOrNull { it.timestamp <= oneDayAgo }
        val endSnapshot = snapshots.lastOrNull()

        return if (startSnapshot != null && endSnapshot != null && startSnapshot.totalValueDecimal > BigDecimal.ZERO) {
            val pnl = endSnapshot.totalValueDecimal - startSnapshot.totalValueDecimal
            val pnlPercent = pnl.percentOf(startSnapshot.totalValueDecimal)

            val hoursDiff = (endSnapshot.timestamp - startSnapshot.timestamp) / (MILLIS_IN_HOUR.toDouble())

            // Validate the time period is reasonable
            if (hoursDiff < MIN_HOURS_FOR_DAILY) {
                Timber.w("Insufficient time period for daily P&L %: ${"%.1f".format(hoursDiff)} hours")
                return BigDecimal.ZERO
            }

            Timber.d("Daily P&L %: ${pnlPercent.setScale(2, RoundingMode.HALF_EVEN).toPlainString()}% over ${"%.1f".format(hoursDiff)} hours")
            pnlPercent
        } else {
            BigDecimal.ZERO
        }
    }

    /**
     * Calculate Return on Investment (ROI) using BigDecimal
     *
     * BUG FIX 9.2: Renamed parameters for clarity - finalValue and initialValue
     * instead of confusing gains and costs.
     *
     * @param finalValue The final portfolio value
     * @param initialValue The initial investment amount
     * @return ROI as a percentage (BigDecimal)
     */
    fun calculateROIDecimal(finalValue: BigDecimal, initialValue: BigDecimal): BigDecimal {
        if (initialValue == BigDecimal.ZERO) {
            Timber.w("ROI calculation: Initial value is 0, returning 0%")
            return BigDecimal.ZERO
        }

        val roi = ((finalValue - initialValue) safeDiv initialValue) * BigDecimal("100")

        Timber.d("ROI: Initial=${initialValue.setScale(2, RoundingMode.HALF_EVEN).toPlainString()}, " +
                "Final=${finalValue.setScale(2, RoundingMode.HALF_EVEN).toPlainString()}, " +
                "ROI=${roi.setScale(2, RoundingMode.HALF_EVEN).toPlainString()}%")

        // Validate ROI is within reasonable bounds (-100% to +10000%)
        val roiDouble = roi.toDouble()
        if (roiDouble < -100.0 || roiDouble > 10000.0) {
            Timber.w("ROI value seems unreasonable: ${roi.setScale(2, RoundingMode.HALF_EVEN).toPlainString()}%")
        }

        return roi
    }

    /**
     * Calculate period return using BigDecimal
     */
    fun calculatePeriodReturnDecimal(snapshots: List<PortfolioSnapshot>, period: TimePeriod): BigDecimal {
        if (snapshots.isEmpty()) return BigDecimal.ZERO

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
        if (periodSnapshots.isEmpty()) return BigDecimal.ZERO

        val startValue = periodSnapshots.first().totalValueDecimal
        val endValue = periodSnapshots.last().totalValueDecimal

        return calculateReturnDecimal(endValue, startValue)
    }

    /**
     * Get default metrics (BigDecimal version)
     */
    private fun getDefaultMetricsDecimal(): PerformanceMetrics {
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

    // ==================== DEPRECATED DOUBLE METHODS (Backward Compatibility) ====================

    /**
     * Calculate performance metrics
     * @deprecated Use calculatePerformanceDecimal() for exact calculations
     */
    @Deprecated("Use calculatePerformanceDecimal() for exact calculations", ReplaceWith("calculatePerformanceDecimal(snapshots)"))
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
     * @deprecated Use calculateReturnDecimal() for exact calculations
     */
    @Deprecated("Use calculateReturnDecimal() for exact calculations", ReplaceWith("calculateReturnDecimal(currentValue.toBigDecimalMoney(), initialValue.toBigDecimalMoney())"))
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
     * @deprecated Use calculateDailyPnLDecimal() for exact calculations
     */
    @Deprecated("Use calculateDailyPnLDecimal() for exact calculations", ReplaceWith("calculateDailyPnLDecimal(snapshots)"))
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
     * @deprecated Use calculateDailyPnLPercentDecimal() for exact calculations
     */
    @Deprecated("Use calculateDailyPnLPercentDecimal() for exact calculations", ReplaceWith("calculateDailyPnLPercentDecimal(snapshots)"))
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
     * @deprecated Use calculateROIDecimal() for exact calculations
     */
    @Deprecated("Use calculateROIDecimal() for exact calculations", ReplaceWith("calculateROIDecimal(finalValue.toBigDecimalMoney(), initialValue.toBigDecimalMoney())"))
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
     * @deprecated Use calculatePeriodReturnDecimal() for exact calculations
     */
    @Deprecated("Use calculatePeriodReturnDecimal() for exact calculations", ReplaceWith("calculatePeriodReturnDecimal(snapshots, period)"))
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
