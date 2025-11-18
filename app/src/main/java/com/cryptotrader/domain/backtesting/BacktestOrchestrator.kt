package com.cryptotrader.domain.backtesting

import com.cryptotrader.data.local.dao.BacktestRunDao
import com.cryptotrader.data.local.entities.BacktestRunEntity
import com.cryptotrader.domain.model.DataTier
import com.cryptotrader.domain.model.Strategy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backtest Orchestrator - Complete Backtest Workflow
 *
 * Coordinates the entire backtesting process:
 * 1. Data Selection (via BacktestDataProvider)
 * 2. Data Validation (via DataTierValidator)
 * 3. Backtest Execution (via BacktestEngine)
 * 4. Results Persistence (to database)
 *
 * This is the main entry point for running backtests.
 */
@Singleton
class BacktestOrchestrator @Inject constructor(
    private val backtestDataProvider: BacktestDataProvider,
    private val backtestEngine: BacktestEngine,
    private val backtestRunDao: BacktestRunDao
) {

    /**
     * Run complete backtest for a strategy
     *
     * AUTO MODE: Automatically selects best data tier and timeframe
     *
     * @param strategy Strategy to backtest
     * @param startingBalance Initial balance for backtest
     * @return Backtest results with performance metrics
     */
    suspend fun runBacktest(
        strategy: Strategy,
        startingBalance: Double = 10000.0
    ): BacktestResult = withContext(Dispatchers.Default) {
        try {
            Timber.i("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Timber.i("🚀 BACKTEST ORCHESTRATION STARTING (AUTO MODE)")
            Timber.i("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Timber.i("Strategy: ${strategy.name}")
            Timber.i("Starting Balance: $$startingBalance")
            Timber.i("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

            // Step 1: Get backtest data (automatic selection)
            val dataSet = backtestDataProvider.getBacktestData(strategy)

            if (!dataSet.isValid) {
                Timber.e("❌ Failed to get backtest data: ${dataSet.error}")
                return@withContext BacktestResult(
                    strategyId = strategy.id,
                    strategyName = strategy.name,
                    startingBalance = startingBalance,
                    endingBalance = startingBalance,
                    totalTrades = 0,
                    winningTrades = 0,
                    losingTrades = 0,
                    winRate = 0.0,
                    totalPnL = 0.0,
                    totalPnLPercent = 0.0,
                    maxDrawdown = 0.0,
                    sharpeRatio = 0.0,
                    profitFactor = 0.0,
                    averageProfit = 0.0,
                    averageLoss = 0.0,
                    bestTrade = 0.0,
                    worstTrade = 0.0,
                    trades = emptyList(),
                    equityCurve = listOf(startingBalance),
                    validationError = dataSet.error
                )
            }

            Timber.i("✅ Data loaded successfully:")
            Timber.i("   Asset: ${dataSet.asset}")
            Timber.i("   Timeframe: ${dataSet.timeframe}")
            Timber.i("   Data Tier: ${dataSet.dataTier.tierName}")
            Timber.i("   Bars: ${dataSet.priceBars.size}")
            Timber.i("   Quality Score: ${String.format("%.2f", dataSet.dataQualityScore)}")

            // Step 2: Run backtest
            val result = backtestEngine.runBacktest(
                strategy = strategy,
                historicalData = dataSet.priceBars,
                startingBalance = startingBalance,
                ohlcBars = dataSet.ohlcBars  // For data tier validation
            )

            // Step 3: Persist results to database
            if (result.validationError == null && result.totalTrades > 0) {
                persistBacktestRun(
                    result = result,
                    dataSet = dataSet
                )
            }

            Timber.i("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Timber.i("✅ BACKTEST ORCHESTRATION COMPLETE")
            Timber.i("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

            result

        } catch (e: Exception) {
            Timber.e(e, "❌ Backtest orchestration failed")
            BacktestResult(
                strategyId = strategy.id,
                strategyName = strategy.name,
                startingBalance = startingBalance,
                endingBalance = startingBalance,
                totalTrades = 0,
                winningTrades = 0,
                losingTrades = 0,
                winRate = 0.0,
                totalPnL = 0.0,
                totalPnLPercent = 0.0,
                maxDrawdown = 0.0,
                sharpeRatio = 0.0,
                profitFactor = 0.0,
                averageProfit = 0.0,
                averageLoss = 0.0,
                bestTrade = 0.0,
                worstTrade = 0.0,
                trades = emptyList(),
                equityCurve = listOf(startingBalance),
                validationError = "Orchestration failed: ${e.message}"
            )
        }
    }

    /**
     * Run backtest with manual data selection
     *
     * MANUAL MODE: User specifies data tier, asset, timeframe, and date range
     *
     * @param strategy Strategy to backtest
     * @param dataTier Specific data tier to use
     * @param asset Trading pair to test
     * @param timeframe Timeframe to use
     * @param startDate Backtest start date
     * @param endDate Backtest end date
     * @param startingBalance Initial balance
     * @return Backtest results
     */
    suspend fun runBacktestManual(
        strategy: Strategy,
        dataTier: DataTier,
        asset: String? = null,
        timeframe: String? = null,
        startDate: Long? = null,
        endDate: Long? = null,
        startingBalance: Double = 10000.0
    ): BacktestResult = withContext(Dispatchers.Default) {
        try {
            Timber.i("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Timber.i("🎯 BACKTEST ORCHESTRATION STARTING (MANUAL MODE)")
            Timber.i("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Timber.i("Strategy: ${strategy.name}")
            Timber.i("Data Tier: ${dataTier.tierName}")
            Timber.i("Asset: ${asset ?: "auto"}")
            Timber.i("Timeframe: ${timeframe ?: "auto"}")
            Timber.i("Starting Balance: $$startingBalance")
            Timber.i("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

            // Get data with manual parameters
            val dataSet = backtestDataProvider.getBacktestData(
                strategy = strategy,
                preferredTier = dataTier,
                startDate = startDate,
                endDate = endDate
            )

            if (!dataSet.isValid) {
                Timber.e("❌ Failed to get backtest data: ${dataSet.error}")
                return@withContext BacktestResult(
                    strategyId = strategy.id,
                    strategyName = strategy.name,
                    startingBalance = startingBalance,
                    endingBalance = startingBalance,
                    totalTrades = 0,
                    winningTrades = 0,
                    losingTrades = 0,
                    winRate = 0.0,
                    totalPnL = 0.0,
                    totalPnLPercent = 0.0,
                    maxDrawdown = 0.0,
                    sharpeRatio = 0.0,
                    profitFactor = 0.0,
                    averageProfit = 0.0,
                    averageLoss = 0.0,
                    bestTrade = 0.0,
                    worstTrade = 0.0,
                    trades = emptyList(),
                    equityCurve = listOf(startingBalance),
                    validationError = dataSet.error
                )
            }

            // Run backtest
            val result = backtestEngine.runBacktest(
                strategy = strategy,
                historicalData = dataSet.priceBars,
                startingBalance = startingBalance,
                ohlcBars = dataSet.ohlcBars
            )

            // Persist results
            if (result.validationError == null && result.totalTrades > 0) {
                persistBacktestRun(result, dataSet)
            }

            Timber.i("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
            Timber.i("✅ BACKTEST ORCHESTRATION COMPLETE")
            Timber.i("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

            result

        } catch (e: Exception) {
            Timber.e(e, "❌ Backtest orchestration failed")
            BacktestResult(
                strategyId = strategy.id,
                strategyName = strategy.name,
                startingBalance = startingBalance,
                endingBalance = startingBalance,
                totalTrades = 0,
                winningTrades = 0,
                losingTrades = 0,
                winRate = 0.0,
                totalPnL = 0.0,
                totalPnLPercent = 0.0,
                maxDrawdown = 0.0,
                sharpeRatio = 0.0,
                profitFactor = 0.0,
                averageProfit = 0.0,
                averageLoss = 0.0,
                bestTrade = 0.0,
                worstTrade = 0.0,
                trades = emptyList(),
                equityCurve = listOf(startingBalance),
                validationError = "Orchestration failed: ${e.message}"
            )
        }
    }

    /**
     * Persist backtest run to database for historical tracking
     *
     * Version 17+: Includes data provenance tracking for 100% reproducibility
     */
    private suspend fun persistBacktestRun(
        result: BacktestResult,
        dataSet: BacktestDataSet
    ) {
        try {
            // Calculate data provenance
            val dataFileHashes = generateDataFileHashes(dataSet)

            Timber.i("📊 Data Provenance:")
            Timber.i("   Hash: ${dataFileHashes.take(50)}...")
            Timber.i("   Parser Version: $PARSER_VERSION")
            Timber.i("   Engine Version: $ENGINE_VERSION")

            val backtestRun = BacktestRunEntity(
                strategyId = result.strategyId,
                asset = dataSet.asset,
                timeframe = dataSet.timeframe,
                startTimestamp = dataSet.startTimestamp,
                endTimestamp = dataSet.endTimestamp,
                totalBarsUsed = dataSet.priceBars.size.toLong(),
                totalTrades = result.totalTrades,
                winningTrades = result.winningTrades,
                losingTrades = result.losingTrades,
                winRate = result.winRate,
                totalPnL = result.totalPnL,
                totalPnLPercent = result.totalPnLPercent,
                sharpeRatio = result.sharpeRatio,
                maxDrawdown = result.maxDrawdown,
                profitFactor = result.profitFactor,
                status = determineBacktestStatus(result),
                dataQualityScore = dataSet.dataQualityScore,
                dataTier = dataSet.dataTier.name,  // Track which tier was used
                tierValidated = true,               // Validation passed
                dataSource = "DATABASE",
                executedAt = System.currentTimeMillis(),
                durationMs = 0, // TODO: Track actual duration
                // Data provenance (v17+)
                dataFileHashes = dataFileHashes,
                parserVersion = PARSER_VERSION,
                engineVersion = ENGINE_VERSION
            )

            backtestRunDao.insert(backtestRun)
            Timber.i("✅ Backtest run persisted to database (ID: ${backtestRun.id})")
            Timber.i("✅ Provenance tracking: 100% reproducible")

        } catch (e: Exception) {
            Timber.e(e, "Failed to persist backtest run")
        }
    }

    /**
     * Determine backtest status based on results
     */
    private fun determineBacktestStatus(result: BacktestResult): String {
        return when {
            result.winRate >= 70.0 && result.profitFactor >= 2.0 -> "EXCELLENT"
            result.winRate >= 60.0 && result.profitFactor >= 1.5 -> "GOOD"
            result.winRate >= 50.0 && result.profitFactor >= 1.0 -> "ACCEPTABLE"
            else -> "FAILED"
        }
    }

    /**
     * Calculate SHA-256 hash of dataset for provenance tracking
     *
     * Creates a deterministic hash of the OHLC data used in the backtest.
     * This enables 100% reproducible backtest verification.
     */
    private fun calculateDatasetHash(dataSet: BacktestDataSet): String {
        try {
            val digest = MessageDigest.getInstance("SHA-256")

            // Create deterministic string from OHLC bars
            val dataString = buildString {
                append("${dataSet.asset}|${dataSet.timeframe}|${dataSet.dataTier.name}|")
                dataSet.ohlcBars.forEach { bar ->
                    append("${bar.timestamp}|${bar.open}|${bar.high}|${bar.low}|${bar.close}|${bar.volume}|")
                }
            }

            val hashBytes = digest.digest(dataString.toByteArray())
            return hashBytes.joinToString("") { "%02x".format(it) }

        } catch (e: Exception) {
            Timber.e(e, "Failed to calculate dataset hash")
            return "HASH_ERROR"
        }
    }

    /**
     * Generate JSON array of data file hashes for provenance
     *
     * For database-sourced data, creates single hash of the dataset.
     * For file-sourced data (future), will include individual file hashes.
     */
    private fun generateDataFileHashes(dataSet: BacktestDataSet): String {
        val hash = calculateDatasetHash(dataSet)
        return """["sha256:$hash"]"""
    }

    companion object {
        // Semantic versioning for data parser and backtest engine
        // Update these when parser or engine logic changes
        private const val PARSER_VERSION = "1.0.0"
        private const val ENGINE_VERSION = "1.0.0"
    }
}
