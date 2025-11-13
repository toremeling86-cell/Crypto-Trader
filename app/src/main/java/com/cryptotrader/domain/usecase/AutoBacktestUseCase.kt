package com.cryptotrader.domain.usecase

import com.cryptotrader.data.repository.HistoricalDataRepository
import com.cryptotrader.domain.backtesting.BacktestEngine
import com.cryptotrader.domain.backtesting.BacktestResult
import com.cryptotrader.domain.model.Strategy
import timber.log.Timber
import javax.inject.Inject

/**
 * Automatically backtest AI-generated strategies before activation
 *
 * This ensures strategies are validated against historical data to prevent
 * unprofitable or dangerous strategies from trading with real money
 */
class AutoBacktestUseCase @Inject constructor(
    private val backtestEngine: BacktestEngine,
    private val historicalDataRepository: HistoricalDataRepository
) {

    companion object {
        private const val DEFAULT_BACKTEST_DAYS = 30 // Test on last 30 days of data
        private const val DEFAULT_STARTING_BALANCE = 10000.0
        private const val MIN_WIN_RATE = 50.0 // Minimum 50% win rate
        private const val MIN_PROFIT_FACTOR = 1.2 // Minimum 1.2:1 profit factor
        private const val MAX_DRAWDOWN = 20.0 // Maximum 20% drawdown
    }

    /**
     * Automatically backtest a strategy
     *
     * @param strategy Strategy to backtest
     * @param startingBalance Initial balance for backtest (default: $10,000)
     * @param daysToTest Number of days of historical data to test (default: 30)
     * @return BacktestResult with validation status
     */
    suspend operator fun invoke(
        strategy: Strategy,
        startingBalance: Double = DEFAULT_STARTING_BALANCE,
        daysToTest: Int = DEFAULT_BACKTEST_DAYS
    ): Result<BacktestValidation> {
        return try {
            Timber.i("🔬 Auto-backtesting strategy: ${strategy.name}")

            // Get primary trading pair (use first pair from strategy)
            val primaryPair = strategy.tradingPairs.firstOrNull()
            if (primaryPair == null) {
                return Result.failure(Exception("Strategy has no trading pairs configured"))
            }

            // Determine interval based on primary timeframe
            val interval = strategy.primaryTimeframe.coerceIn(1, 1440)

            Timber.d("Fetching historical data for $primaryPair, interval: ${interval}m, days: $daysToTest")

            // Fetch historical data
            val historicalDataResult = historicalDataRepository.fetchHistoricalData(
                pair = primaryPair,
                interval = interval
            )

            if (historicalDataResult.isFailure) {
                val error = historicalDataResult.exceptionOrNull()
                Timber.e(error, "Failed to fetch historical data")
                return Result.failure(Exception("Failed to fetch historical data: ${error?.message}"))
            }

            val allData = historicalDataResult.getOrThrow()

            // Filter to last N days
            val cutoffTime = System.currentTimeMillis() - (daysToTest * 24 * 60 * 60 * 1000L)
            val recentData = allData.filter { it.timestamp >= cutoffTime }

            if (recentData.isEmpty()) {
                return Result.failure(Exception("No historical data available for backtesting"))
            }

            if (recentData.size < 20) {
                Timber.w("Only ${recentData.size} data points available, backtest may be unreliable")
            }

            Timber.d("Running backtest with ${recentData.size} data points")

            // Run backtest
            val backtestResult = backtestEngine.runBacktest(
                strategy = strategy,
                historicalData = recentData,
                startingBalance = startingBalance
            )

            // Validate results
            val validation = validateBacktestResults(backtestResult)

            Timber.i("✅ Backtest complete: ${validation.status}")
            Timber.i("   Win Rate: ${backtestResult.winRate}%")
            Timber.i("   Profit Factor: ${backtestResult.profitFactor}")
            Timber.i("   Total P&L: ${backtestResult.totalPnL} (${backtestResult.totalPnLPercent}%)")
            Timber.i("   Max Drawdown: ${backtestResult.maxDrawdown}")

            Result.success(validation)

        } catch (e: Exception) {
            Timber.e(e, "Error during auto-backtest")
            Result.failure(e)
        }
    }

    /**
     * Validate backtest results against quality thresholds
     */
    private fun validateBacktestResults(result: BacktestResult): BacktestValidation {
        val issues = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        // Check minimum number of trades
        if (result.totalTrades < 10) {
            warnings.add("Only ${result.totalTrades} trades executed. More trades would give better confidence.")
        }

        // Check win rate
        if (result.winRate < MIN_WIN_RATE) {
            issues.add("Win rate ${result.winRate}% is below minimum threshold of ${MIN_WIN_RATE}%")
        } else if (result.winRate < 55.0) {
            warnings.add("Win rate ${result.winRate}% is acceptable but could be higher")
        }

        // Check profit factor
        if (result.profitFactor < MIN_PROFIT_FACTOR) {
            issues.add("Profit factor ${result.profitFactor} is below minimum threshold of $MIN_PROFIT_FACTOR")
        } else if (result.profitFactor < 1.5) {
            warnings.add("Profit factor ${result.profitFactor} is acceptable but could be higher")
        }

        // Check overall profitability
        if (result.totalPnL < 0) {
            issues.add("Strategy lost money: \$${result.totalPnL} (${result.totalPnLPercent}%)")
        } else if (result.totalPnLPercent < 5.0) {
            warnings.add("Strategy profit ${result.totalPnLPercent}% is low for the test period")
        }

        // Check drawdown
        val drawdownPercent = (result.maxDrawdown / result.startingBalance) * 100.0
        if (drawdownPercent > MAX_DRAWDOWN) {
            issues.add("Max drawdown ${drawdownPercent}% exceeds threshold of ${MAX_DRAWDOWN}%")
        } else if (drawdownPercent > 15.0) {
            warnings.add("Max drawdown ${drawdownPercent}% is relatively high")
        }

        // Check Sharpe ratio
        if (result.sharpeRatio < 1.0) {
            warnings.add("Sharpe ratio ${result.sharpeRatio} indicates risk-adjusted returns could be better")
        }

        // Determine overall status
        val status = when {
            issues.isNotEmpty() -> BacktestStatus.FAILED
            warnings.isEmpty() -> BacktestStatus.EXCELLENT
            warnings.size <= 2 -> BacktestStatus.GOOD
            else -> BacktestStatus.ACCEPTABLE
        }

        return BacktestValidation(
            result = result,
            status = status,
            passed = issues.isEmpty(),
            issues = issues,
            warnings = warnings,
            recommendation = getRecommendation(status, issues, warnings)
        )
    }

    /**
     * Generate recommendation based on validation results
     */
    private fun getRecommendation(
        status: BacktestStatus,
        issues: List<String>,
        warnings: List<String>
    ): String {
        return when (status) {
            BacktestStatus.EXCELLENT -> {
                "This strategy passed all quality checks with excellent metrics. Safe to activate for live trading."
            }
            BacktestStatus.GOOD -> {
                "This strategy passed validation with good metrics. ${warnings.firstOrNull() ?: "Consider monitoring performance closely."}"
            }
            BacktestStatus.ACCEPTABLE -> {
                "This strategy passed minimum requirements but has several warnings:\n" +
                        warnings.joinToString("\n") +
                        "\nConsider activating with smaller position sizes initially."
            }
            BacktestStatus.FAILED -> {
                "⚠️ This strategy FAILED validation:\n" +
                        issues.joinToString("\n") +
                        "\nDO NOT activate for live trading. Consider modifying the strategy parameters."
            }
        }
    }
}

/**
 * Backtest validation result with quality assessment
 */
data class BacktestValidation(
    val result: BacktestResult,
    val status: BacktestStatus,
    val passed: Boolean,
    val issues: List<String>,
    val warnings: List<String>,
    val recommendation: String
)

/**
 * Backtest quality status
 */
enum class BacktestStatus {
    EXCELLENT,  // All metrics exceed thresholds significantly
    GOOD,       // All metrics pass with minor warnings
    ACCEPTABLE, // Passes minimum thresholds with warnings
    FAILED      // Does not meet minimum quality requirements
}
