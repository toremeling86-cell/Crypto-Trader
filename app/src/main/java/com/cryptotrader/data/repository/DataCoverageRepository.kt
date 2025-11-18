package com.cryptotrader.data.repository

import com.cryptotrader.data.local.dao.BacktestRunDao
import com.cryptotrader.data.local.dao.DataCoverageDao
import com.cryptotrader.data.local.dao.DataQualityDao
import com.cryptotrader.data.local.entities.BacktestRunEntity
import com.cryptotrader.data.local.entities.DataCoverageEntity
import com.cryptotrader.data.local.entities.DataQualityEntity
import kotlinx.coroutines.flow.Flow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data Coverage Repository - Metadata queries for AI chat and UI
 *
 * Provides information about:
 * - Which assets/timeframes are available
 * - Data quality scores
 * - Date ranges covered
 * - Which data was used for backtesting
 *
 * Used by:
 * - AI chat to answer "What data do we have?"
 * - UI to show available data
 * - BacktestEngine to verify data coverage before running tests
 */
@Singleton
class DataCoverageRepository @Inject constructor(
    private val dataCoverageDao: DataCoverageDao,
    private val dataQualityDao: DataQualityDao,
    private val backtestRunDao: BacktestRunDao
) {

    /**
     * Get all available data coverage (for AI chat)
     *
     * Example response:
     * - XXBTZUSD: 1m (2023-01-01 to 2024-12-31), 5m (2023-06-01 to 2024-12-31)
     * - SOLUSD: 1h (2023-06-15 to 2024-12-31)
     */
    suspend fun getAllCoverage(): List<DataCoverageEntity> {
        return try {
            dataCoverageDao.getAllCoverage()
        } catch (e: Exception) {
            Timber.e(e, "Failed to get all coverage")
            emptyList()
        }
    }

    /**
     * Get coverage as Flow (for reactive UI)
     */
    fun getAllCoverageFlow(): Flow<List<DataCoverageEntity>> {
        return dataCoverageDao.getAllCoverageFlow()
    }

    /**
     * Get coverage for specific asset/timeframe
     */
    suspend fun getCoverage(asset: String, timeframe: String): DataCoverageEntity? {
        return try {
            dataCoverageDao.getCoverage(asset, timeframe)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get coverage for $asset $timeframe")
            null
        }
    }

    /**
     * Get all available assets
     */
    suspend fun getAvailableAssets(): List<String> {
        return try {
            dataCoverageDao.getAllAssets()
        } catch (e: Exception) {
            Timber.e(e, "Failed to get available assets")
            emptyList()
        }
    }

    /**
     * Get coverage for asset (all timeframes)
     */
    suspend fun getCoverageForAsset(asset: String): List<DataCoverageEntity> {
        return try {
            dataCoverageDao.getCoverageForAsset(asset)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get coverage for asset")
            emptyList()
        }
    }

    /**
     * Get high-quality data only (for backtesting recommendations)
     */
    suspend fun getHighQualityCoverage(minScore: Double = 0.8): List<DataCoverageEntity> {
        return try {
            dataCoverageDao.getHighQualityCoverage(minScore)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get high quality coverage")
            emptyList()
        }
    }

    /**
     * Get data quality report
     */
    suspend fun getDataQuality(asset: String, timeframe: String): DataQualityEntity? {
        return try {
            dataQualityDao.getLatestQuality(asset, timeframe)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get data quality")
            null
        }
    }

    /**
     * Get backtest-suitable data (good quality, no major gaps)
     */
    suspend fun getBacktestSuitableData(minScore: Double = 0.7): List<DataQualityEntity> {
        return try {
            dataQualityDao.getBacktestSuitableData(minScore)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get backtest suitable data")
            emptyList()
        }
    }

    /**
     * Get backtest history for strategy
     *
     * Shows which data was used for each backtest run
     * Used by AI chat to answer: "Which data was this strategy tested on?"
     */
    suspend fun getBacktestHistory(strategyId: String): List<BacktestRunEntity> {
        return try {
            backtestRunDao.getRunsForStrategy(strategyId)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get backtest history")
            emptyList()
        }
    }

    /**
     * Get backtest history as Flow
     */
    fun getBacktestHistoryFlow(strategyId: String): Flow<List<BacktestRunEntity>> {
        return backtestRunDao.getRunsForStrategyFlow(strategyId)
    }

    /**
     * Get latest backtest run for strategy
     */
    suspend fun getLatestBacktestRun(strategyId: String): BacktestRunEntity? {
        return try {
            backtestRunDao.getLatestRun(strategyId)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get latest backtest run")
            null
        }
    }

    /**
     * Get successful backtest runs (good performance)
     */
    suspend fun getSuccessfulBacktests(
        minWinRate: Double = 60.0,
        minReturn: Double = 10.0
    ): List<BacktestRunEntity> {
        return try {
            backtestRunDao.getSuccessfulRuns(minWinRate, minReturn)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get successful backtests")
            emptyList()
        }
    }

    /**
     * Insert backtest run (called by BacktestEngine)
     */
    suspend fun insertBacktestRun(run: BacktestRunEntity): Result<Long> {
        return try {
            val id = backtestRunDao.insert(run)
            Timber.i("📊 Backtest run saved: Strategy ${run.strategyId}, ${run.asset} ${run.timeframe}")
            Result.success(id)
        } catch (e: Exception) {
            Timber.e(e, "Failed to insert backtest run")
            Result.failure(e)
        }
    }

    /**
     * Generate summary for AI chat
     *
     * Example output:
     * "Available data:
     * - XXBTZUSD: 1m (Jan 2023 - Dec 2024, 98.5% quality), 5m (Jun 2023 - Dec 2024, 99.2% quality)
     * - SOLUSD: 1h (Jun 2023 - Dec 2024, 95.3% quality)"
     */
    suspend fun generateDataSummaryForAI(): String {
        return try {
            val coverage = getAllCoverage()
            if (coverage.isEmpty()) {
                return "No historical data available in local storage. Import data from CryptoLake to enable offline backtesting."
            }

            buildString {
                appendLine("Available historical data:")
                coverage.groupBy { it.asset }.forEach { (asset, coverages) ->
                    append("- $asset: ")
                    val timeframeInfo = coverages.joinToString(", ") { c ->
                        val startDate = formatTimestamp(c.earliestTimestamp)
                        val endDate = formatTimestamp(c.latestTimestamp)
                        val quality = "%.1f%%".format(c.dataQualityScore * 100)
                        "${c.timeframe} ($startDate - $endDate, $quality quality)"
                    }
                    appendLine(timeframeInfo)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to generate data summary")
            "Error generating data summary"
        }
    }

    /**
     * Format timestamp for display
     */
    private fun formatTimestamp(timestamp: Long): String {
        val date = java.util.Date(timestamp)
        val format = java.text.SimpleDateFormat("MMM yyyy", java.util.Locale.US)
        return format.format(date)
    }
}
