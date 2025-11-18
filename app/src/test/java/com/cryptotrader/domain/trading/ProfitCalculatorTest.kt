package com.cryptotrader.domain.trading

import android.content.Context
import com.cryptotrader.data.local.dao.TradeDao
import com.cryptotrader.data.local.entities.TradeEntity
import com.cryptotrader.domain.model.TradeStatus
import com.cryptotrader.domain.model.TradeType
import com.cryptotrader.utils.CryptoUtils
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for ProfitCalculator - specifically testing the FIFO position matching fix
 *
 * BUG 10.1 - Broken FIFO matching for partial volumes
 *
 * Test Scenarios:
 * 1. Full volume matching (original behavior should still work)
 * 2. Partial volume matching - single buy, multiple sells
 * 3. Multiple buy positions matched by single sell
 * 4. Complex scenario - multiple buys and sells with various volumes
 * 5. Edge case - very small volumes (floating point precision)
 * 6. Edge case - sell exceeds available buy positions
 * 7. Fee proration across partial fills
 */
class ProfitCalculatorTest {

    private lateinit var profitCalculator: ProfitCalculator
    private lateinit var mockTradeDao: TradeDao
    private lateinit var mockContext: Context

    @Before
    fun setup() {
        // Mock dependencies
        mockTradeDao = mockk(relaxed = true)
        mockContext = mockk(relaxed = true)

        // Mock CryptoUtils for paper trading
        mockkObject(CryptoUtils)
        every { CryptoUtils.isPaperTradingMode(any()) } returns true

        // Initialize calculator
        profitCalculator = ProfitCalculator(mockTradeDao, mockContext)
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // ==================== Test 1: Full Volume Matching ====================

    @Test
    fun test_fullVolumeMatching_originalBehavior() = runBlocking {
        println("\n=== TEST 1: Full Volume Matching (Original Behavior) ===")

        // Scenario: Buy 1 BTC, Sell 1 BTC
        val trades = listOf(
            createTradeEntity(
                id = 1,
                type = "buy",
                price = 50000.0,
                volume = 1.0,
                fee = 25.0,
                timestamp = 1000L
            ),
            createTradeEntity(
                id = 2,
                type = "sell",
                price = 60000.0,
                volume = 1.0,
                fee = 30.0,
                timestamp = 2000L
            )
        )

        coEvery { mockTradeDao.getAllTradesFlow() } returns flowOf(trades)

        val (totalPnL, _, _) = profitCalculator.calculateTotalPnL()

        // Expected P&L: (60000 - 50000) * 1.0 - 25 - 30 = 9945
        val expectedPnL = 9945.0
        assertEquals(expectedPnL, totalPnL, 0.01, "Full volume matching P&L should be $expectedPnL")

        println("Total P&L: $$totalPnL (expected: $$expectedPnL)")
        println("=== TEST 1 PASSED ===\n")
    }

    // ==================== Test 2: Partial Volume - Single Buy, Multiple Sells ====================

    @Test
    fun test_partialVolume_singleBuy_multipleSells() = runBlocking {
        println("\n=== TEST 2: Partial Volume - Single Buy, Multiple Sells ===")

        // Scenario: Buy 1 BTC @ $50K, Sell 0.5 BTC @ $60K, Sell 0.5 BTC @ $65K
        val trades = listOf(
            createTradeEntity(
                id = 1,
                type = "buy",
                price = 50000.0,
                volume = 1.0,
                fee = 25.0,
                timestamp = 1000L
            ),
            createTradeEntity(
                id = 2,
                type = "sell",
                price = 60000.0,
                volume = 0.5,
                fee = 15.0,
                timestamp = 2000L
            ),
            createTradeEntity(
                id = 3,
                type = "sell",
                price = 65000.0,
                volume = 0.5,
                fee = 16.25,
                timestamp = 3000L
            )
        )

        coEvery { mockTradeDao.getAllTradesFlow() } returns flowOf(trades)

        val (totalPnL, _, _) = profitCalculator.calculateTotalPnL()

        // First sell (0.5 BTC @ $60K):
        // Proceeds: 60000 * 0.5 = 30000
        // Cost: 50000 * 0.5 = 25000
        // Sell fee (prorated): 15.0 * (0.5/0.5) = 15.0
        // Buy fee (prorated): 25.0 * (0.5/1.0) = 12.5
        // P&L: 30000 - 25000 - 15.0 - 12.5 = 4972.5

        // Second sell (0.5 BTC @ $65K):
        // Proceeds: 65000 * 0.5 = 32500
        // Cost: 50000 * 0.5 = 25000
        // Sell fee (prorated): 16.25 * (0.5/0.5) = 16.25
        // Buy fee (prorated): 25.0 * (0.5/1.0) = 12.5
        // P&L: 32500 - 25000 - 16.25 - 12.5 = 7471.25

        // Total P&L: 4972.5 + 7471.25 = 12443.75
        val expectedPnL = 12443.75
        assertEquals(expectedPnL, totalPnL, 0.01, "Partial volume matching P&L should be $expectedPnL")

        println("Total P&L: $$totalPnL (expected: $$expectedPnL)")
        println("=== TEST 2 PASSED ===\n")
    }

    // ==================== Test 3: Multiple Buys Matched by Single Sell ====================

    @Test
    fun test_multipleBuys_singleSell() = runBlocking {
        println("\n=== TEST 3: Multiple Buys Matched by Single Sell ===")

        // Scenario: Buy 0.5 BTC @ $50K, Buy 0.5 BTC @ $52K, Sell 1.0 BTC @ $60K
        val trades = listOf(
            createTradeEntity(
                id = 1,
                type = "buy",
                price = 50000.0,
                volume = 0.5,
                fee = 12.5,
                timestamp = 1000L
            ),
            createTradeEntity(
                id = 2,
                type = "buy",
                price = 52000.0,
                volume = 0.5,
                fee = 13.0,
                timestamp = 2000L
            ),
            createTradeEntity(
                id = 3,
                type = "sell",
                price = 60000.0,
                volume = 1.0,
                fee = 30.0,
                timestamp = 3000L
            )
        )

        coEvery { mockTradeDao.getAllTradesFlow() } returns flowOf(trades)

        val (totalPnL, _, _) = profitCalculator.calculateTotalPnL()

        // First match (0.5 BTC from first buy @ $50K):
        // Proceeds: 60000 * 0.5 = 30000
        // Cost: 50000 * 0.5 = 25000
        // Sell fee (prorated): 30.0 * (0.5/1.0) = 15.0
        // Buy fee (prorated): 12.5 * (0.5/0.5) = 12.5
        // P&L: 30000 - 25000 - 15.0 - 12.5 = 4972.5

        // Second match (0.5 BTC from second buy @ $52K):
        // Proceeds: 60000 * 0.5 = 30000
        // Cost: 52000 * 0.5 = 26000
        // Sell fee (prorated): 30.0 * (0.5/1.0) = 15.0
        // Buy fee (prorated): 13.0 * (0.5/0.5) = 13.0
        // P&L: 30000 - 26000 - 15.0 - 13.0 = 3972.0

        // Total P&L: 4972.5 + 3972.0 = 8944.5
        val expectedPnL = 8944.5
        assertEquals(expectedPnL, totalPnL, 0.01, "Multiple buys matched by single sell P&L should be $expectedPnL")

        println("Total P&L: $$totalPnL (expected: $$expectedPnL)")
        println("=== TEST 3 PASSED ===\n")
    }

    // ==================== Test 4: Complex Scenario - Multiple Buys and Sells ====================

    @Test
    fun test_complexScenario_multipleBuysAndSells() = runBlocking {
        println("\n=== TEST 4: Complex Scenario - Multiple Buys and Sells ===")

        // Scenario:
        // Buy 1.0 BTC @ $50K
        // Sell 0.3 BTC @ $55K
        // Buy 0.5 BTC @ $52K
        // Sell 0.8 BTC @ $60K
        // Sell 0.4 BTC @ $62K
        val trades = listOf(
            createTradeEntity(id = 1, type = "buy", price = 50000.0, volume = 1.0, fee = 25.0, timestamp = 1000L),
            createTradeEntity(id = 2, type = "sell", price = 55000.0, volume = 0.3, fee = 8.25, timestamp = 2000L),
            createTradeEntity(id = 3, type = "buy", price = 52000.0, volume = 0.5, fee = 13.0, timestamp = 3000L),
            createTradeEntity(id = 4, type = "sell", price = 60000.0, volume = 0.8, fee = 24.0, timestamp = 4000L),
            createTradeEntity(id = 5, type = "sell", price = 62000.0, volume = 0.4, fee = 12.4, timestamp = 5000L)
        )

        coEvery { mockTradeDao.getAllTradesFlow() } returns flowOf(trades)

        val (totalPnL, _, _) = profitCalculator.calculateTotalPnL()

        // Manual calculation:
        // Sell 1 (0.3 BTC @ $55K) matches 0.3 from Buy 1:
        //   P&L: (55000 - 50000) * 0.3 - 8.25 * (0.3/0.3) - 25.0 * (0.3/1.0) = 1500 - 8.25 - 7.5 = 1484.25
        //   Buy 1 remaining: 0.7 BTC

        // Sell 2 (0.8 BTC @ $60K) matches 0.7 from Buy 1, then 0.1 from Buy 2:
        //   Match 1 (0.7): (60000 - 50000) * 0.7 - 24.0 * (0.7/0.8) - 25.0 * (0.7/1.0) = 7000 - 21.0 - 17.5 = 6961.5
        //   Match 2 (0.1): (60000 - 52000) * 0.1 - 24.0 * (0.1/0.8) - 13.0 * (0.1/0.5) = 800 - 3.0 - 2.6 = 794.4
        //   Buy 2 remaining: 0.4 BTC

        // Sell 3 (0.4 BTC @ $62K) matches 0.4 from Buy 2:
        //   P&L: (62000 - 52000) * 0.4 - 12.4 * (0.4/0.4) - 13.0 * (0.4/0.5) = 4000 - 12.4 - 10.4 = 3977.2

        // Total P&L: 1484.25 + 6961.5 + 794.4 + 3977.2 = 13217.35
        val expectedPnL = 13217.35
        assertEquals(expectedPnL, totalPnL, 0.02, "Complex scenario P&L should be approximately $expectedPnL")

        println("Total P&L: $$totalPnL (expected: $$expectedPnL)")
        println("=== TEST 4 PASSED ===\n")
    }

    // ==================== Test 5: Floating Point Precision ====================

    @Test
    fun test_floatingPointPrecision_verySmallVolumes() = runBlocking {
        println("\n=== TEST 5: Floating Point Precision - Very Small Volumes ===")

        // Scenario: Buy 0.00001 BTC, Sell 0.000005 BTC twice
        val trades = listOf(
            createTradeEntity(id = 1, type = "buy", price = 50000.0, volume = 0.00001, fee = 0.0005, timestamp = 1000L),
            createTradeEntity(id = 2, type = "sell", price = 60000.0, volume = 0.000005, fee = 0.0003, timestamp = 2000L),
            createTradeEntity(id = 3, type = "sell", price = 65000.0, volume = 0.000005, fee = 0.000325, timestamp = 3000L)
        )

        coEvery { mockTradeDao.getAllTradesFlow() } returns flowOf(trades)

        val (totalPnL, _, _) = profitCalculator.calculateTotalPnL()

        // First sell: (60000 - 50000) * 0.000005 - 0.0003 - 0.00025 = 0.05 - 0.00055 = 0.04945
        // Second sell: (65000 - 50000) * 0.000005 - 0.000325 - 0.00025 = 0.075 - 0.000575 = 0.074425
        // Total: 0.04945 + 0.074425 = 0.123875
        val expectedPnL = 0.123875

        // Due to floating point precision, we use a slightly larger tolerance
        assertEquals(expectedPnL, totalPnL, 0.0001, "Small volume P&L should handle floating point precision")

        println("Total P&L: $$totalPnL (expected: $$expectedPnL)")
        println("=== TEST 5 PASSED ===\n")
    }

    // ==================== Test 6: Sell Exceeds Buy Positions ====================

    @Test
    fun test_sellExceedsBuyPositions_warningLogged() = runBlocking {
        println("\n=== TEST 6: Sell Exceeds Buy Positions ===")

        // Scenario: Buy 1.0 BTC, Sell 1.5 BTC (0.5 BTC has no matching buy)
        val trades = listOf(
            createTradeEntity(id = 1, type = "buy", price = 50000.0, volume = 1.0, fee = 25.0, timestamp = 1000L),
            createTradeEntity(id = 2, type = "sell", price = 60000.0, volume = 1.5, fee = 45.0, timestamp = 2000L)
        )

        coEvery { mockTradeDao.getAllTradesFlow() } returns flowOf(trades)

        val (totalPnL, _, _) = profitCalculator.calculateTotalPnL()

        // Only the matched 1.0 BTC should contribute to P&L:
        // P&L: (60000 - 50000) * 1.0 - 45.0 * (1.0/1.5) - 25.0 = 10000 - 30.0 - 25.0 = 9945.0
        val expectedPnL = 9945.0
        assertEquals(expectedPnL, totalPnL, 0.01, "P&L should only account for matched volume")

        println("Total P&L: $$totalPnL (expected: $$expectedPnL)")
        println("Note: A warning should be logged for 0.5 BTC unmatched volume")
        println("=== TEST 6 PASSED ===\n")
    }

    // ==================== Test 7: Fee Proration Validation ====================

    @Test
    fun test_feeProration_correctCalculation() = runBlocking {
        println("\n=== TEST 7: Fee Proration Validation ===")

        // Scenario: Buy 1.0 BTC with $100 fee, sell in 4 parts (0.25 each)
        // Each sell should get $25 of the buy fee prorated
        val trades = listOf(
            createTradeEntity(id = 1, type = "buy", price = 50000.0, volume = 1.0, fee = 100.0, timestamp = 1000L),
            createTradeEntity(id = 2, type = "sell", price = 51000.0, volume = 0.25, fee = 12.75, timestamp = 2000L),
            createTradeEntity(id = 3, type = "sell", price = 52000.0, volume = 0.25, fee = 13.0, timestamp = 3000L),
            createTradeEntity(id = 4, type = "sell", price = 53000.0, volume = 0.25, fee = 13.25, timestamp = 4000L),
            createTradeEntity(id = 5, type = "sell", price = 54000.0, volume = 0.25, fee = 13.5, timestamp = 5000L)
        )

        coEvery { mockTradeDao.getAllTradesFlow() } returns flowOf(trades)

        val (totalPnL, _, _) = profitCalculator.calculateTotalPnL()

        // Each sell should have buy fee prorated as: 100.0 * (0.25/1.0) = 25.0
        // Sell 1: (51000 - 50000) * 0.25 - 12.75 - 25.0 = 250 - 37.75 = 212.25
        // Sell 2: (52000 - 50000) * 0.25 - 13.0 - 25.0 = 500 - 38.0 = 462.0
        // Sell 3: (53000 - 50000) * 0.25 - 13.25 - 25.0 = 750 - 38.25 = 711.75
        // Sell 4: (54000 - 50000) * 0.25 - 13.5 - 25.0 = 1000 - 38.5 = 961.5
        // Total: 212.25 + 462.0 + 711.75 + 961.5 = 2347.5
        val expectedPnL = 2347.5
        assertEquals(expectedPnL, totalPnL, 0.01, "Fee proration should be accurate")

        println("Total P&L: $$totalPnL (expected: $$expectedPnL)")
        println("=== TEST 7 PASSED ===\n")
    }

    // ==================== Test 8: Multiple Trading Pairs ====================

    @Test
    fun test_multipleTradingPairs_independentFIFO() = runBlocking {
        println("\n=== TEST 8: Multiple Trading Pairs - Independent FIFO ===")

        // Scenario: Trade both BTC and ETH, ensure FIFO is independent per pair
        val trades = listOf(
            // BTC trades
            createTradeEntity(id = 1, type = "buy", price = 50000.0, volume = 1.0, fee = 25.0, pair = "BTCUSD", timestamp = 1000L),
            // ETH trades
            createTradeEntity(id = 2, type = "buy", price = 3000.0, volume = 5.0, fee = 7.5, pair = "ETHUSD", timestamp = 2000L),
            // BTC sell
            createTradeEntity(id = 3, type = "sell", price = 60000.0, volume = 0.5, fee = 15.0, pair = "BTCUSD", timestamp = 3000L),
            // ETH sell
            createTradeEntity(id = 4, type = "sell", price = 3500.0, volume = 2.0, fee = 3.5, pair = "ETHUSD", timestamp = 4000L)
        )

        coEvery { mockTradeDao.getAllTradesFlow() } returns flowOf(trades)

        val (totalPnL, _, _) = profitCalculator.calculateTotalPnL()

        // BTC P&L: (60000 - 50000) * 0.5 - 15.0 - 12.5 = 5000 - 27.5 = 4972.5
        // ETH P&L: (3500 - 3000) * 2.0 - 3.5 - 3.0 = 1000 - 6.5 = 993.5
        // Total: 4972.5 + 993.5 = 5966.0
        val expectedPnL = 5966.0
        assertEquals(expectedPnL, totalPnL, 0.01, "FIFO should be independent per trading pair")

        println("Total P&L: $$totalPnL (expected: $$expectedPnL)")
        println("=== TEST 8 PASSED ===\n")
    }

    // ==================== Helper Methods ====================

    private fun createTradeEntity(
        id: Long,
        type: String,
        price: Double,
        volume: Double,
        fee: Double,
        pair: String = "BTCUSD",
        timestamp: Long
    ): TradeEntity {
        return TradeEntity(
            id = id,
            orderId = "ORDER-$id",
            pair = pair,
            type = type,
            price = price,
            volume = volume,
            cost = price * volume,
            fee = fee,
            timestamp = timestamp,
            strategyId = "test-strategy",
            status = TradeStatus.EXECUTED.toString(),
            profit = null
        )
    }
}
