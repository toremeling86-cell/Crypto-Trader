package com.cryptotrader.domain.backtesting

import android.content.Context
import com.cryptotrader.domain.model.Strategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import java.io.FileWriter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backtest Event Logger - NDJSON Observability (TODO 4, P1-7)
 *
 * Logs backtest execution events in NDJSON format for observability,
 * debugging, and reproducibility analysis.
 *
 * Output Location: app_data/backtests/{runId}/events.ndjson
 * Index File: app_data/backtests/index.csv
 *
 * Event Types:
 * - backtest_start: Backtest initialization
 * - bar_processed: Price bar evaluation
 * - trade: Trade execution (BUY/SELL)
 * - error: Execution errors
 * - backtest_end: Backtest completion with summary
 */
interface BacktestEventLogger {
    /**
     * Start a new backtest session
     * Returns the run ID for this backtest
     */
    fun startBacktest(strategy: Strategy, startingBalance: Double): String

    /**
     * Log a price bar being processed
     */
    fun logBarProcessed(runId: String, timestamp: Long, price: Double, barIndex: Int)

    /**
     * Log a trade execution
     */
    fun logTrade(runId: String, timestamp: Long, action: String, price: Double, size: Double, pnl: Double? = null)

    /**
     * Log an error during backtest
     */
    fun logError(runId: String, timestamp: Long, error: String)

    /**
     * End backtest session with summary
     */
    fun endBacktest(
        runId: String,
        timestamp: Long,
        totalTrades: Int,
        winRate: Double,
        totalPnL: Double,
        sharpeRatio: Double,
        maxDrawdown: Double
    )

    /**
     * Get the NDJSON file path for a run ID
     */
    fun getEventsFilePath(runId: String): String

    /**
     * Get the index CSV file path
     */
    fun getIndexFilePath(): String
}

/**
 * Default implementation of BacktestEventLogger
 * Writes events to NDJSON format in app_data/backtests/
 */
@Singleton
class BacktestEventLoggerImpl @Inject constructor(
    private val context: Context
) : BacktestEventLogger {

    private val json = Json { prettyPrint = false }
    private val backtestsDir = File(context.filesDir, "backtests")
    private val indexFile = File(backtestsDir, "index.csv")

    init {
        // Ensure backtests directory exists
        if (!backtestsDir.exists()) {
            backtestsDir.mkdirs()
            Timber.i("Created backtests directory: ${backtestsDir.absolutePath}")
        }

        // Initialize index.csv if it doesn't exist
        if (!indexFile.exists()) {
            FileWriter(indexFile).use { writer ->
                writer.write("run_id,strategy_name,start_time,end_time,total_trades,win_rate,total_pnl,sharpe_ratio,events_file\n")
            }
            Timber.i("Created backtest index: ${indexFile.absolutePath}")
        }
    }

    override fun startBacktest(strategy: Strategy, startingBalance: Double): String {
        val runId = "bt_${System.currentTimeMillis()}"
        val runDir = File(backtestsDir, runId)
        runDir.mkdirs()

        val event = BacktestStartEvent(
            type = "backtest_start",
            timestamp = System.currentTimeMillis(),
            runId = runId,
            strategyId = strategy.id,
            strategyName = strategy.name,
            startingBalance = startingBalance
        )

        writeEvent(runId, event)

        Timber.i("📝 Backtest logging started: $runId")
        Timber.i("   Events file: ${getEventsFilePath(runId)}")

        return runId
    }

    override fun logBarProcessed(runId: String, timestamp: Long, price: Double, barIndex: Int) {
        val event = BarProcessedEvent(
            type = "bar_processed",
            timestamp = timestamp,
            runId = runId,
            barIndex = barIndex,
            price = price
        )

        writeEvent(runId, event)
    }

    override fun logTrade(runId: String, timestamp: Long, action: String, price: Double, size: Double, pnl: Double?) {
        val event = TradeEvent(
            type = "trade",
            timestamp = timestamp,
            runId = runId,
            action = action,
            price = price,
            size = size,
            pnl = pnl
        )

        writeEvent(runId, event)

        Timber.d("📝 Trade logged: $action @ $price")
    }

    override fun logError(runId: String, timestamp: Long, error: String) {
        val event = ErrorEvent(
            type = "error",
            timestamp = timestamp,
            runId = runId,
            error = error
        )

        writeEvent(runId, event)

        Timber.w("📝 Error logged: $error")
    }

    override fun endBacktest(
        runId: String,
        timestamp: Long,
        totalTrades: Int,
        winRate: Double,
        totalPnL: Double,
        sharpeRatio: Double,
        maxDrawdown: Double
    ) {
        val event = BacktestEndEvent(
            type = "backtest_end",
            timestamp = timestamp,
            runId = runId,
            totalTrades = totalTrades,
            winRate = winRate,
            totalPnL = totalPnL,
            sharpeRatio = sharpeRatio,
            maxDrawdown = maxDrawdown
        )

        writeEvent(runId, event)

        // Update index.csv
        updateIndex(runId, event)

        Timber.i("📝 Backtest logging completed: $runId")
    }

    override fun getEventsFilePath(runId: String): String {
        return File(File(backtestsDir, runId), "events.ndjson").absolutePath
    }

    override fun getIndexFilePath(): String {
        return indexFile.absolutePath
    }

    /**
     * Write a single event to the NDJSON file
     */
    private fun writeEvent(runId: String, event: Any) {
        try {
            val runDir = File(backtestsDir, runId)
            val eventsFile = File(runDir, "events.ndjson")

            FileWriter(eventsFile, true).use { writer ->
                val jsonLine = json.encodeToString(event)
                writer.write("$jsonLine\n")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to write event to NDJSON")
        }
    }

    /**
     * Update the index.csv with backtest completion data
     */
    private fun updateIndex(runId: String, endEvent: BacktestEndEvent) {
        try {
            // Read existing index to get strategy name and start time
            val lines = if (indexFile.exists()) indexFile.readLines() else emptyList()
            val header = lines.firstOrNull() ?: "run_id,strategy_name,start_time,end_time,total_trades,win_rate,total_pnl,sharpe_ratio,events_file\n"

            // Extract strategy name from run directory
            val runDir = File(backtestsDir, runId)
            val eventsFile = File(runDir, "events.ndjson")
            val strategyName = if (eventsFile.exists()) {
                val firstLine = eventsFile.readLines().firstOrNull()
                if (firstLine != null) {
                    val startEvent = json.decodeFromString<BacktestStartEvent>(firstLine)
                    startEvent.strategyName
                } else {
                    "Unknown"
                }
            } else {
                "Unknown"
            }

            // Append new entry
            FileWriter(indexFile, true).use { writer ->
                val csvLine = buildString {
                    append("$runId,")
                    append("\"$strategyName\",")
                    append("${endEvent.timestamp - 3600000},")  // Approximate start (1h before end)
                    append("${endEvent.timestamp},")
                    append("${endEvent.totalTrades},")
                    append("${String.format("%.2f", endEvent.winRate)},")
                    append("${String.format("%.2f", endEvent.totalPnL)},")
                    append("${String.format("%.2f", endEvent.sharpeRatio)},")
                    append("${getEventsFilePath(runId)}")
                }
                writer.write("$csvLine\n")
            }

            Timber.i("📝 Index updated: ${indexFile.absolutePath}")
        } catch (e: Exception) {
            Timber.e(e, "Failed to update index.csv")
        }
    }
}

// Event data classes

@Serializable
data class BacktestStartEvent(
    val type: String,
    val timestamp: Long,
    val runId: String,
    val strategyId: String,
    val strategyName: String,
    val startingBalance: Double
)

@Serializable
data class BarProcessedEvent(
    val type: String,
    val timestamp: Long,
    val runId: String,
    val barIndex: Int,
    val price: Double
)

@Serializable
data class TradeEvent(
    val type: String,
    val timestamp: Long,
    val runId: String,
    val action: String,
    val price: Double,
    val size: Double,
    val pnl: Double? = null
)

@Serializable
data class ErrorEvent(
    val type: String,
    val timestamp: Long,
    val runId: String,
    val error: String
)

@Serializable
data class BacktestEndEvent(
    val type: String,
    val timestamp: Long,
    val runId: String,
    val totalTrades: Int,
    val winRate: Double,
    val totalPnL: Double,
    val sharpeRatio: Double,
    val maxDrawdown: Double
)
