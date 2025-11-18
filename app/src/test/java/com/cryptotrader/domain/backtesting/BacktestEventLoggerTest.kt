package com.cryptotrader.domain.backtesting

import android.content.Context
import com.cryptotrader.domain.model.Strategy
import com.cryptotrader.domain.model.TradingMode
import com.cryptotrader.domain.model.RiskLevel
import com.cryptotrader.domain.model.ApprovalStatus
import com.cryptotrader.domain.model.StrategySource
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.*
import java.io.File

/**
 * Tests for BacktestEventLogger - NDJSON observability (TODO 4)
 *
 * Verifies:
 * - Event logging to NDJSON format
 * - Index.csv creation and updates
 * - Event file structure
 * - All event types (start, trade, error, end)
 */
class BacktestEventLoggerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var mockContext: Context
    private lateinit var logger: BacktestEventLoggerImpl

    @Before
    fun setup() {
        // Mock Android Context
        mockContext = mock(Context::class.java)
        `when`(mockContext.filesDir).thenReturn(tempFolder.root)

        logger = BacktestEventLoggerImpl(mockContext)
    }

    @Test
    fun `logger creates backtests directory on initialization`() {
        // GIVEN: Logger is initialized

        // THEN: Backtests directory should exist
        val backtestsDir = File(tempFolder.root, "backtests")
        assertTrue("Backtests directory should be created", backtestsDir.exists())
        assertTrue("Should be a directory", backtestsDir.isDirectory)
    }

    @Test
    fun `logger creates index csv on initialization`() {
        // GIVEN: Logger is initialized

        // THEN: Index.csv should exist with header
        val indexFile = File(File(tempFolder.root, "backtests"), "index.csv")
        assertTrue("Index.csv should be created", indexFile.exists())

        val lines = indexFile.readLines()
        assertTrue("Index should have header", lines.isNotEmpty())
        assertEquals(
            "run_id,strategy_name,start_time,end_time,total_trades,win_rate,total_pnl,sharpe_ratio,events_file",
            lines[0]
        )
    }

    @Test
    fun `startBacktest creates events file and logs start event`() {
        // GIVEN: A test strategy
        val strategy = createTestStrategy("RSI Diagnostics")

        // WHEN: Start backtest
        val runId = logger.startBacktest(strategy, 10000.0)

        // THEN: Run ID should be generated
        assertTrue("Run ID should start with bt_", runId.startsWith("bt_"))

        // AND: Events file should be created
        val eventsFile = File(logger.getEventsFilePath(runId))
        assertTrue("Events file should exist", eventsFile.exists())

        // AND: First line should be backtest_start event
        val lines = eventsFile.readLines()
        assertTrue("Events file should have at least 1 line", lines.isNotEmpty())

        val firstLine = lines[0]
        assertTrue("First event should be backtest_start", firstLine.contains("\"type\":\"backtest_start\""))
        assertTrue("Event should contain runId", firstLine.contains("\"runId\":\"$runId\""))
        assertTrue("Event should contain strategy name", firstLine.contains("\"strategyName\":\"RSI Diagnostics\""))
        assertTrue("Event should contain starting balance", firstLine.contains("\"startingBalance\":10000.0"))

        println("✅ Start event logged: $firstLine")
    }

    @Test
    fun `logTrade writes trade event to NDJSON file`() {
        // GIVEN: Started backtest
        val strategy = createTestStrategy("Test Strategy")
        val runId = logger.startBacktest(strategy, 10000.0)

        // WHEN: Log a BUY trade
        logger.logTrade(runId, 1700000000, "BUY", 42500.0, 0.1, null)

        // AND: Log a SELL trade with PnL
        logger.logTrade(runId, 1700001000, "SELL", 43000.0, 0.1, 50.0)

        // THEN: Events file should have 3 lines (start + 2 trades)
        val eventsFile = File(logger.getEventsFilePath(runId))
        val lines = eventsFile.readLines()

        assertEquals("Should have 3 events", 3, lines.size)

        // Verify BUY trade
        val buyLine = lines[1]
        assertTrue("Should contain trade type", buyLine.contains("\"type\":\"trade\""))
        assertTrue("Should contain BUY action", buyLine.contains("\"action\":\"BUY\""))
        assertTrue("Should contain price", buyLine.contains("\"price\":42500.0"))
        assertTrue("Should contain size", buyLine.contains("\"size\":0.1"))

        // Verify SELL trade with PnL
        val sellLine = lines[2]
        assertTrue("Should contain SELL action", sellLine.contains("\"action\":\"SELL\""))
        assertTrue("Should contain PnL", sellLine.contains("\"pnl\":50.0"))

        println("✅ Trade events logged:")
        println("   BUY:  $buyLine")
        println("   SELL: $sellLine")
    }

    @Test
    fun `logError writes error event to NDJSON file`() {
        // GIVEN: Started backtest
        val strategy = createTestStrategy("Error Test")
        val runId = logger.startBacktest(strategy, 10000.0)

        // WHEN: Log an error
        logger.logError(runId, 1700000000, "Insufficient funds for trade")

        // THEN: Events file should have error event
        val eventsFile = File(logger.getEventsFilePath(runId))
        val lines = eventsFile.readLines()

        assertEquals("Should have 2 events (start + error)", 2, lines.size)

        val errorLine = lines[1]
        assertTrue("Should contain error type", errorLine.contains("\"type\":\"error\""))
        assertTrue("Should contain error message", errorLine.contains("\"error\":\"Insufficient funds for trade\""))

        println("✅ Error event logged: $errorLine")
    }

    @Test
    fun `endBacktest logs end event and updates index csv`() {
        // GIVEN: Started backtest with some trades
        val strategy = createTestStrategy("Complete Test")
        val runId = logger.startBacktest(strategy, 10000.0)
        logger.logTrade(runId, 1700000000, "BUY", 42500.0, 0.1)
        logger.logTrade(runId, 1700001000, "SELL", 43000.0, 0.1, 50.0)

        // WHEN: End backtest
        logger.endBacktest(
            runId = runId,
            timestamp = 1700010000,
            totalTrades = 2,
            winRate = 1.0,
            totalPnL = 50.0,
            sharpeRatio = 1.5,
            maxDrawdown = 0.0
        )

        // THEN: Events file should have end event
        val eventsFile = File(logger.getEventsFilePath(runId))
        val lines = eventsFile.readLines()

        assertEquals("Should have 4 events (start + 2 trades + end)", 4, lines.size)

        val endLine = lines[3]
        assertTrue("Should contain backtest_end type", endLine.contains("\"type\":\"backtest_end\""))
        assertTrue("Should contain totalTrades", endLine.contains("\"totalTrades\":2"))
        assertTrue("Should contain winRate", endLine.contains("\"winRate\":1.0"))
        assertTrue("Should contain sharpeRatio", endLine.contains("\"sharpeRatio\":1.5"))

        // AND: Index.csv should be updated
        val indexFile = File(logger.getIndexFilePath())
        val indexLines = indexFile.readLines()

        assertTrue("Index should have at least 2 lines (header + entry)", indexLines.size >= 2)

        val indexEntry = indexLines.last()
        assertTrue("Index entry should contain runId", indexEntry.contains(runId))
        assertTrue("Index entry should contain strategy name", indexEntry.contains("Complete Test"))

        println("✅ End event logged: $endLine")
        println("✅ Index updated: $indexEntry")
    }

    @Test
    fun `multiple backtests create separate event files`() {
        // GIVEN: Two different strategies
        val strategy1 = createTestStrategy("Strategy 1")
        val strategy2 = createTestStrategy("Strategy 2")

        // WHEN: Run two backtests
        val runId1 = logger.startBacktest(strategy1, 10000.0)
        logger.logTrade(runId1, 1700000000, "BUY", 42500.0, 0.1)
        logger.endBacktest(runId1, 1700001000, 1, 1.0, 50.0, 1.5, 0.0)

        Thread.sleep(10) // Ensure different timestamps

        val runId2 = logger.startBacktest(strategy2, 15000.0)
        logger.logTrade(runId2, 1700002000, "SELL", 43000.0, 0.2)
        logger.endBacktest(runId2, 1700003000, 1, 1.0, 100.0, 2.0, 0.0)

        // THEN: Two separate directories should exist
        val backtestsDir = File(tempFolder.root, "backtests")
        val runDirs = backtestsDir.listFiles { file -> file.isDirectory }

        assertTrue("Should have at least 2 run directories", runDirs != null && runDirs.size >= 2)

        // AND: Each should have its own events file
        val eventsFile1 = File(logger.getEventsFilePath(runId1))
        val eventsFile2 = File(logger.getEventsFilePath(runId2))

        assertTrue("Run 1 events file should exist", eventsFile1.exists())
        assertTrue("Run 2 events file should exist", eventsFile2.exists())

        assertNotEquals("Event files should be different", eventsFile1.absolutePath, eventsFile2.absolutePath)

        println("✅ Multiple backtests logged separately:")
        println("   Run 1: ${eventsFile1.absolutePath}")
        println("   Run 2: ${eventsFile2.absolutePath}")
    }

    @Test
    fun `getEventsFilePath returns correct path format`() {
        // GIVEN: A run ID
        val runId = "bt_1234567890"

        // WHEN: Get events file path
        val path = logger.getEventsFilePath(runId)

        // THEN: Path should follow expected format
        assertTrue("Path should contain backtests directory", path.contains("backtests"))
        assertTrue("Path should contain runId directory", path.contains(runId))
        assertTrue("Path should end with events.ndjson", path.endsWith("events.ndjson"))

        println("✅ Events file path: $path")
    }

    @Test
    fun `getIndexFilePath returns correct path`() {
        // WHEN: Get index file path
        val path = logger.getIndexFilePath()

        // THEN: Path should point to index.csv
        assertTrue("Path should contain backtests directory", path.contains("backtests"))
        assertTrue("Path should end with index.csv", path.endsWith("index.csv"))

        println("✅ Index file path: $path")
    }

    // Helper: Create test strategy
    private fun createTestStrategy(name: String): Strategy {
        return Strategy(
            id = "test_${System.currentTimeMillis()}",
            name = name,
            description = "Test strategy for NDJSON logging",
            entryConditions = listOf("RSI < 30"),
            exitConditions = listOf("RSI > 70"),
            positionSizePercent = 10.0,
            stopLossPercent = 2.0,
            takeProfitPercent = 5.0,
            tradingPairs = listOf("XBTUSD"),
            isActive = false,
            tradingMode = TradingMode.INACTIVE,
            riskLevel = RiskLevel.MEDIUM,
            approvalStatus = ApprovalStatus.APPROVED,
            source = StrategySource.USER
        )
    }
}
