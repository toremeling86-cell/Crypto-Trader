package com.cryptotrader.data.repository

import com.cryptotrader.data.local.dao.StrategyDao
import com.cryptotrader.data.local.entities.StrategyEntity
import com.cryptotrader.domain.model.Strategy
import com.cryptotrader.domain.model.RiskLevel
import com.cryptotrader.domain.model.ApprovalStatus
import com.cryptotrader.domain.model.StrategySource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.util.UUID

/**
 * Unit tests for strategy validation in StrategyRepository
 * Tests the hardcoded price level detection and validation logic
 */
class StrategyValidationTest {

    private lateinit var strategyDao: StrategyDao
    private lateinit var repository: StrategyRepository

    @Before
    fun setup() {
        strategyDao = mockk(relaxed = true)
        repository = StrategyRepository(strategyDao)
    }

    // ========== VALID STRATEGIES (SHOULD PASS) ==========

    @Test
    fun `insertStrategy with RSI-based conditions should succeed`() = runTest {
        val strategy = createValidStrategy(
            entryConditions = listOf(
                "RSI(14) < 30",
                "Price below SMA(50)"
            ),
            exitConditions = listOf(
                "RSI(14) > 70",
                "MACD crossover signal"
            )
        )

        coEvery { strategyDao.insertStrategy(any()) } returns Unit

        repository.insertStrategy(strategy)

        coVerify { strategyDao.insertStrategy(any()) }
    }

    @Test
    fun `insertStrategy with percentage-based conditions should succeed`() = runTest {
        val strategy = createValidStrategy(
            entryConditions = listOf(
                "Price gain > 5%",
                "Volume increase > 50%"
            ),
            exitConditions = listOf(
                "Price drop > 3%",
                "Stop loss at 2%"
            )
        )

        coEvery { strategyDao.insertStrategy(any()) } returns Unit

        repository.insertStrategy(strategy)

        coVerify { strategyDao.insertStrategy(any()) }
    }

    @Test
    fun `insertStrategy with Bollinger Bands conditions should succeed`() = runTest {
        val strategy = createValidStrategy(
            entryConditions = listOf(
                "Price touches lower Bollinger Band",
                "RSI < 35"
            ),
            exitConditions = listOf(
                "Price reaches upper Bollinger Band",
                "RSI > 65"
            )
        )

        coEvery { strategyDao.insertStrategy(any()) } returns Unit

        repository.insertStrategy(strategy)

        coVerify { strategyDao.insertStrategy(any()) }
    }

    @Test
    fun `insertStrategy with MACD and EMA conditions should succeed`() = runTest {
        val strategy = createValidStrategy(
            entryConditions = listOf(
                "MACD line crosses above signal line",
                "Price above EMA(200)",
                "Volume > 20-day average"
            ),
            exitConditions = listOf(
                "MACD line crosses below signal line",
                "Price below EMA(50)"
            )
        )

        coEvery { strategyDao.insertStrategy(any()) } returns Unit

        repository.insertStrategy(strategy)

        coVerify { strategyDao.insertStrategy(any()) }
    }

    @Test
    fun `insertStrategy with ATR-based stops should succeed`() = runTest {
        val strategy = createValidStrategy(
            entryConditions = listOf(
                "RSI < 40",
                "Price crosses above SMA(20)"
            ),
            exitConditions = listOf(
                "Stop loss: 2x ATR below entry",
                "Take profit: 3x ATR above entry"
            )
        )

        coEvery { strategyDao.insertStrategy(any()) } returns Unit

        repository.insertStrategy(strategy)

        coVerify { strategyDao.insertStrategy(any()) }
    }

    @Test
    fun `insertStrategy with relative price comparisons should succeed`() = runTest {
        val strategy = createValidStrategy(
            entryConditions = listOf(
                "Price > SMA(20)",
                "Price < EMA(50)",
                "Price crossover EMA(10)"
            ),
            exitConditions = listOf(
                "Price below SMA(20)",
                "RSI > 75"
            )
        )

        coEvery { strategyDao.insertStrategy(any()) } returns Unit

        repository.insertStrategy(strategy)

        coVerify { strategyDao.insertStrategy(any()) }
    }

    @Test
    fun `insertStrategy with small timeframe numbers should succeed`() = runTest {
        val strategy = createValidStrategy(
            entryConditions = listOf(
                "RSI(14) < 30 on 15-minute chart",
                "SMA(50) crossover on 60-minute chart"
            ),
            exitConditions = listOf(
                "RSI(14) > 70",
                "Price 5% above entry"
            )
        )

        coEvery { strategyDao.insertStrategy(any()) } returns Unit

        repository.insertStrategy(strategy)

        coVerify { strategyDao.insertStrategy(any()) }
    }

    // ========== INVALID STRATEGIES (SHOULD FAIL) ==========

    @Test
    fun `insertStrategy with dollar amount should fail`() = runTest {
        val strategy = createInvalidStrategy(
            entryConditions = listOf(
                "Price > $42,500",
                "RSI < 30"
            )
        )

        coEvery { strategyDao.insertStrategy(any()) } returns Unit

        try {
            repository.insertStrategy(strategy)
            fail("Expected IllegalArgumentException for hardcoded dollar amount")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("hardcoded price levels") == true)
            assertTrue(e.message?.contains("\$42,500") == true)
        }

        coVerify(exactly = 0) { strategyDao.insertStrategy(any()) }
    }

    @Test
    fun `insertStrategy with large price number should fail`() = runTest {
        val strategy = createInvalidStrategy(
            entryConditions = listOf(
                "price > 42500"
            )
        )

        coEvery { strategyDao.insertStrategy(any()) } returns Unit

        try {
            repository.insertStrategy(strategy)
            fail("Expected IllegalArgumentException for hardcoded price number")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("hardcoded price levels") == true)
        }

        coVerify(exactly = 0) { strategyDao.insertStrategy(any()) }
    }

    @Test
    fun `insertStrategy with BTC dollar range should fail`() = runTest {
        val strategy = createInvalidStrategy(
            entryConditions = listOf(
                "BTC: $42,500-43,500"
            )
        )

        coEvery { strategyDao.insertStrategy(any()) } returns Unit

        try {
            repository.insertStrategy(strategy)
            fail("Expected IllegalArgumentException for BTC price range")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("hardcoded price levels") == true)
        }

        coVerify(exactly = 0) { strategyDao.insertStrategy(any()) }
    }

    @Test
    fun `insertStrategy with ETH price level should fail`() = runTest {
        val strategy = createInvalidStrategy(
            entryConditions = listOf(
                "ETH: $2,100",
                "RSI < 30"
            )
        )

        coEvery { strategyDao.insertStrategy(any()) } returns Unit

        try {
            repository.insertStrategy(strategy)
            fail("Expected IllegalArgumentException for ETH price level")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("hardcoded price levels") == true)
        }

        coVerify(exactly = 0) { strategyDao.insertStrategy(any()) }
    }

    @Test
    fun `insertStrategy with numeric price range should fail`() = runTest {
        val strategy = createInvalidStrategy(
            exitConditions = listOf(
                "Take profit at 45000-46000"
            )
        )

        coEvery { strategyDao.insertStrategy(any()) } returns Unit

        try {
            repository.insertStrategy(strategy)
            fail("Expected IllegalArgumentException for numeric price range")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("hardcoded price levels") == true)
        }

        coVerify(exactly = 0) { strategyDao.insertStrategy(any()) }
    }

    @Test
    fun `insertStrategy with specific price in exit condition should fail`() = runTest {
        val strategy = createInvalidStrategy(
            entryConditions = listOf(
                "RSI < 30"
            ),
            exitConditions = listOf(
                "Price reaches $50000"
            )
        )

        coEvery { strategyDao.insertStrategy(any()) } returns Unit

        try {
            repository.insertStrategy(strategy)
            fail("Expected IllegalArgumentException for hardcoded exit price")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("hardcoded price levels") == true)
        }

        coVerify(exactly = 0) { strategyDao.insertStrategy(any()) }
    }

    @Test
    fun `updateStrategy with hardcoded price should fail`() = runTest {
        val strategy = createInvalidStrategy(
            entryConditions = listOf(
                "Buy at $40000"
            )
        )

        coEvery { strategyDao.updateStrategy(any()) } returns Unit

        try {
            repository.updateStrategy(strategy)
            fail("Expected IllegalArgumentException for hardcoded price in update")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("hardcoded price levels") == true)
        }

        coVerify(exactly = 0) { strategyDao.updateStrategy(any()) }
    }

    @Test
    fun `insertStrategy with mixed valid and invalid conditions should fail`() = runTest {
        val strategy = createInvalidStrategy(
            entryConditions = listOf(
                "RSI < 30",  // Valid
                "Price above $43000"  // Invalid
            )
        )

        coEvery { strategyDao.insertStrategy(any()) } returns Unit

        try {
            repository.insertStrategy(strategy)
            fail("Expected IllegalArgumentException for mixed conditions")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("hardcoded price levels") == true)
        }

        coVerify(exactly = 0) { strategyDao.insertStrategy(any()) }
    }

    // ========== HELPER METHODS ==========

    private fun createValidStrategy(
        entryConditions: List<String>,
        exitConditions: List<String>
    ): Strategy {
        return Strategy(
            id = UUID.randomUUID().toString(),
            name = "Valid Test Strategy",
            description = "A test strategy with indicator-based conditions",
            entryConditions = entryConditions,
            exitConditions = exitConditions,
            positionSizePercent = 10.0,
            stopLossPercent = 2.0,
            takeProfitPercent = 5.0,
            tradingPairs = listOf("XXBTZUSD"),
            isActive = false,
            riskLevel = RiskLevel.MEDIUM,
            approvalStatus = ApprovalStatus.APPROVED,
            source = StrategySource.USER
        )
    }

    private fun createInvalidStrategy(
        entryConditions: List<String> = emptyList(),
        exitConditions: List<String> = emptyList()
    ): Strategy {
        return Strategy(
            id = UUID.randomUUID().toString(),
            name = "Invalid Test Strategy",
            description = "A test strategy with hardcoded prices (should fail)",
            entryConditions = entryConditions,
            exitConditions = exitConditions,
            positionSizePercent = 10.0,
            stopLossPercent = 2.0,
            takeProfitPercent = 5.0,
            tradingPairs = listOf("XXBTZUSD"),
            isActive = false,
            riskLevel = RiskLevel.MEDIUM,
            approvalStatus = ApprovalStatus.APPROVED,
            source = StrategySource.USER
        )
    }
}
