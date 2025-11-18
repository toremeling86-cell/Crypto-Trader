package com.cryptotrader.domain.backtesting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for TradingCostModel
 * Validates bug fixes for spread and slippage calculations
 */
class TradingCostModelTest {

    private lateinit var costModel: TradingCostModel

    @Before
    fun setup() {
        // Use default Kraken-like parameters
        costModel = TradingCostModel(
            makerFee = 0.0016,  // 0.16%
            takerFee = 0.0026,  // 0.26%
            slippagePercent = 0.05,  // 0.05%
            spreadPercent = 0.02,  // 0.02%
            useRealisticSlippage = true,
            useTieredFees = false
        )
    }

    @Test
    fun `test spread cost is half of full spread - BUG 2_2 fix validation`() {
        // Test a $10,000 order
        val orderValue = 10_000.0
        val cost = costModel.calculateTradeCost(
            orderType = OrderExecutionType.TAKER,
            orderValue = orderValue
        )

        // With 0.02% full spread, half-spread should be 0.01%
        // Spread cost = $10,000 * 0.01% = $1.00
        val expectedSpreadCost = orderValue * (0.01 / 100.0)

        assertEquals(
            "Spread cost should be half of full spread (0.01% not 0.02%)",
            expectedSpreadCost,
            cost.spreadCost,
            0.01  // Allow 1 cent tolerance
        )

        // Verify spread percent returned is the half-spread
        assertEquals(0.01, cost.spreadPercent, 0.0001)
    }

    @Test
    fun `test slippage multiplier applied to percentage not dollar amount - BUG 2_1 fix validation`() {
        // Test medium order ($15,000) - should get 1.25x slippage multiplier
        val orderValue = 15_000.0
        val cost = costModel.calculateTradeCost(
            orderType = OrderExecutionType.TAKER,
            orderValue = orderValue
        )

        // Base slippage: 0.05%
        // Multiplier for $15K order: 1.25x
        // Adjusted slippage: 0.05% * 1.25 = 0.0625%
        // Slippage cost = $15,000 * 0.0625% = $9.375
        val expectedSlippagePercent = 0.05 * 1.25
        val expectedSlippageCost = orderValue * (expectedSlippagePercent / 100.0)

        assertEquals(
            "Slippage should be 0.0625% for $15K order",
            expectedSlippagePercent,
            cost.slippagePercent,
            0.001
        )

        assertEquals(
            "Slippage cost should be based on adjusted percentage",
            expectedSlippageCost,
            cost.slippageAmount,
            0.01
        )
    }

    @Test
    fun `test realistic $10K BTC order has reasonable total costs`() {
        // Simulate a realistic $10,000 BTC purchase
        val orderValue = 10_000.0
        val cost = costModel.calculateTradeCost(
            orderType = OrderExecutionType.TAKER,
            orderValue = orderValue
        )

        // Expected breakdown:
        // - Taker fee: $10,000 * 0.26% = $26.00
        // - Spread: $10,000 * 0.01% = $1.00 (half of 0.02%)
        // - Slippage: $10,000 * 0.05% = $5.00 (no multiplier for $10K)
        // Total: ~$32.00

        assertEquals("Taker fee should be $26", 26.0, cost.feeAmount, 0.01)
        assertEquals("Spread cost should be $1", 1.0, cost.spreadCost, 0.01)
        assertEquals("Slippage should be $5", 5.0, cost.slippageAmount, 0.01)

        val expectedTotal = 32.0
        assertEquals(
            "Total cost for $10K order should be around $32",
            expectedTotal,
            cost.totalCost,
            0.5  // Allow 50 cent tolerance
        )

        // Total cost should be around 0.32%
        assertTrue(
            "Total cost percent should be reasonable (around 0.32%)",
            cost.totalCostPercent >= 0.30 && cost.totalCostPercent <= 0.35
        )
    }

    @Test
    fun `test large order $100K has higher slippage due to multiplier`() {
        val orderValue = 100_000.0
        val cost = costModel.calculateTradeCost(
            orderType = OrderExecutionType.TAKER,
            orderValue = orderValue
        )

        // For $100K order, slippage gets 2x multiplier
        // Base: 0.05% -> Adjusted: 0.10%
        // Cost: $100,000 * 0.10% = $100.00
        val expectedSlippagePercent = 0.05 * 2.0
        val expectedSlippageCost = orderValue * (expectedSlippagePercent / 100.0)

        assertEquals(
            "Large order slippage should be 0.10%",
            expectedSlippagePercent,
            cost.slippagePercent,
            0.001
        )

        assertEquals(
            "Large order slippage cost should be $100",
            expectedSlippageCost,
            cost.slippageAmount,
            0.5
        )
    }

    @Test
    fun `test maker vs taker fee difference`() {
        val orderValue = 10_000.0

        val makerCost = costModel.calculateTradeCost(
            orderType = OrderExecutionType.MAKER,
            orderValue = orderValue
        )

        val takerCost = costModel.calculateTradeCost(
            orderType = OrderExecutionType.TAKER,
            orderValue = orderValue
        )

        // Maker fee: 0.16% = $16
        // Taker fee: 0.26% = $26
        assertEquals("Maker fee should be $16", 16.0, makerCost.feeAmount, 0.01)
        assertEquals("Taker fee should be $26", 26.0, takerCost.feeAmount, 0.01)

        // Spread and slippage should be the same for both
        assertEquals(makerCost.spreadCost, takerCost.spreadCost, 0.01)
        assertEquals(makerCost.slippageAmount, takerCost.slippageAmount, 0.01)

        // Taker total should be higher by $10 (fee difference)
        assertTrue(
            "Taker cost should be higher than maker cost",
            takerCost.totalCost > makerCost.totalCost
        )
    }

    @Test
    fun `test small order has minimal slippage multiplier`() {
        val orderValue = 1_000.0  // Small order
        val cost = costModel.calculateTradeCost(
            orderType = OrderExecutionType.TAKER,
            orderValue = orderValue
        )

        // Small orders get no multiplier (1.0x)
        // Slippage: 0.05% * 1.0 = 0.05%
        val expectedSlippagePercent = 0.05
        assertEquals(
            "Small order should have base slippage rate",
            expectedSlippagePercent,
            cost.slippagePercent,
            0.001
        )
    }

    @Test
    fun `test isLargeOrder flag applies 3x slippage multiplier`() {
        val orderValue = 10_000.0
        val cost = costModel.calculateTradeCost(
            orderType = OrderExecutionType.TAKER,
            orderValue = orderValue,
            isLargeOrder = true  // Force large order treatment
        )

        // isLargeOrder flag should apply 3x multiplier
        // Base: 0.05% -> Adjusted: 0.15%
        val expectedSlippagePercent = 0.05 * 3.0
        assertEquals(
            "Large order flag should apply 3x slippage",
            expectedSlippagePercent,
            cost.slippagePercent,
            0.001
        )
    }

    @Test
    fun `test spread cost is exactly half of what it was before bug fix`() {
        val orderValue = 10_000.0

        // Current implementation (after fix): half-spread
        val cost = costModel.calculateTradeCost(
            orderType = OrderExecutionType.TAKER,
            orderValue = orderValue
        )

        // Before bug fix, spread cost was:
        // $10,000 * 0.02% = $2.00
        val oldBuggySpreadCost = orderValue * (0.02 / 100.0)

        // After bug fix, spread cost should be:
        // $10,000 * 0.01% = $1.00
        val expectedFixedSpreadCost = orderValue * (0.01 / 100.0)

        assertEquals(
            "Fixed spread cost should be half of buggy version",
            expectedFixedSpreadCost,
            cost.spreadCost,
            0.01
        )

        assertEquals(
            "Fixed spread cost should be exactly half of old buggy cost",
            oldBuggySpreadCost / 2.0,
            cost.spreadCost,
            0.01
        )
    }

    @Test
    fun `test total cost percentage is realistic for hedge fund backtesting`() {
        val testCases = listOf(
            1_000.0 to 0.37,    // $1K order: ~0.37% total
            10_000.0 to 0.32,   // $10K order: ~0.32% total
            50_000.0 to 0.285,  // $50K order: ~0.285% total (higher slippage)
            100_000.0 to 0.37   // $100K order: ~0.37% total (much higher slippage)
        )

        testCases.forEach { (orderValue, expectedPercent) ->
            val cost = costModel.calculateTradeCost(
                orderType = OrderExecutionType.TAKER,
                orderValue = orderValue
            )

            assertTrue(
                "Total cost for $$orderValue should be realistic (around $expectedPercent%): " +
                        "actual=${cost.totalCostPercent}%",
                cost.totalCostPercent >= expectedPercent - 0.05 &&
                        cost.totalCostPercent <= expectedPercent + 0.05
            )
        }
    }
}
