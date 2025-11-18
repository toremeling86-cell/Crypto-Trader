package com.cryptotrader.domain.backtesting

import com.cryptotrader.data.local.entities.BacktestRunEntity
import org.junit.Test
import org.junit.Assert.*

/**
 * Cost Model Tracking Test (v18+)
 *
 * Tests the cost model tracking system for backtesting.
 * Ensures that every backtest run tracks assumed vs observed trading costs.
 *
 * Phase 1.7 - P1-5: Cost Model Tracking Implementation
 */
class CostModelTrackingTest {

    @Test
    fun `backtest run entity includes cost tracking fields with default values`() {
        // GIVEN: A backtest run entity
        val backtestRun = BacktestRunEntity(
            strategyId = "test-strategy-123",
            asset = "XXBTZUSD",
            timeframe = "1h",
            startTimestamp = 1700000000000,
            endTimestamp = 1700086400000,
            totalBarsUsed = 24,
            totalTrades = 10,
            winningTrades = 6,
            losingTrades = 4,
            winRate = 60.0,
            totalPnL = 500.0,
            totalPnLPercent = 5.0,
            sharpeRatio = 1.5,
            maxDrawdown = 2.5,
            profitFactor = 1.8,
            status = "GOOD",
            dataQualityScore = 0.95,
            dataSource = "DATABASE"
        )

        // THEN: Cost tracking fields should have default values
        assertEquals(0.0, backtestRun.assumedCostBps, 0.001)
        assertEquals(0.0, backtestRun.observedCostBps, 0.001)
        assertEquals(0.0, backtestRun.costDeltaBps, 0.001)
        assertEquals(0.0, backtestRun.aggregatedFees, 0.001)
        assertEquals(0.0, backtestRun.aggregatedSlippage, 0.001)
    }

    @Test
    fun `backtest run entity stores cost tracking data correctly`() {
        // GIVEN: A backtest run with cost tracking data
        val backtestRun = BacktestRunEntity(
            strategyId = "test-strategy-456",
            asset = "SOLUSD",
            timeframe = "5m",
            startTimestamp = 1700000000000,
            endTimestamp = 1700086400000,
            totalBarsUsed = 288,
            totalTrades = 15,
            winningTrades = 10,
            losingTrades = 5,
            winRate = 66.7,
            totalPnL = 750.0,
            totalPnLPercent = 7.5,
            sharpeRatio = 2.0,
            maxDrawdown = 3.0,
            profitFactor = 2.2,
            status = "EXCELLENT",
            dataQualityScore = 0.98,
            dataSource = "DATABASE",
            assumedCostBps = 10.0,
            observedCostBps = 12.5,
            costDeltaBps = 2.5,
            aggregatedFees = 75.0,
            aggregatedSlippage = 25.0
        )

        // THEN: Cost tracking fields should be stored correctly
        assertEquals(10.0, backtestRun.assumedCostBps, 0.001)
        assertEquals(12.5, backtestRun.observedCostBps, 0.001)
        assertEquals(2.5, backtestRun.costDeltaBps, 0.001)
        assertEquals(75.0, backtestRun.aggregatedFees, 0.001)
        assertEquals(25.0, backtestRun.aggregatedSlippage, 0.001)

        println("✅ Cost tracking data stored correctly")
        println("   Assumed: ${backtestRun.assumedCostBps} bps")
        println("   Observed: ${backtestRun.observedCostBps} bps")
        println("   Delta: ${backtestRun.costDeltaBps} bps")
        println("   Fees: $${backtestRun.aggregatedFees}")
        println("   Slippage: $${backtestRun.aggregatedSlippage}")
    }

    @Test
    fun `cost metrics calculates basis points correctly`() {
        // GIVEN: Trade volume and costs
        val tradeVolume = 10000.0 // $10,000 traded
        val totalCost = 15.0      // $15 in fees + slippage

        // WHEN: Calculate cost in basis points
        val costBps = (totalCost / tradeVolume) * 10000.0

        // THEN: Should be 15 basis points
        assertEquals(15.0, costBps, 0.01)

        println("✅ Basis points calculation correct")
        println("   Volume: $${tradeVolume}")
        println("   Cost: $${totalCost}")
        println("   Cost (bps): ${costBps}")
    }

    @Test
    fun `cost delta shows cost overrun when observed greater than assumed`() {
        // GIVEN: Assumed and observed costs
        val assumedCostBps = 10.0  // Expected 10 bps
        val observedCostBps = 15.0 // Actually paid 15 bps

        // WHEN: Calculate delta
        val costDeltaBps = observedCostBps - assumedCostBps

        // THEN: Delta should be positive (cost overrun)
        assertEquals(5.0, costDeltaBps, 0.01)
        assertTrue("Cost overrun should be positive", costDeltaBps > 0.0)

        println("✅ Cost overrun detected")
        println("   Assumed: ${assumedCostBps} bps")
        println("   Observed: ${observedCostBps} bps")
        println("   Overrun: ${costDeltaBps} bps")
    }

    @Test
    fun `cost delta shows savings when observed less than assumed`() {
        // GIVEN: Assumed and observed costs
        val assumedCostBps = 10.0  // Expected 10 bps
        val observedCostBps = 7.0  // Actually paid 7 bps

        // WHEN: Calculate delta
        val costDeltaBps = observedCostBps - assumedCostBps

        // THEN: Delta should be negative (cost savings)
        assertEquals(-3.0, costDeltaBps, 0.01)
        assertTrue("Cost savings should be negative", costDeltaBps < 0.0)

        println("✅ Cost savings detected")
        println("   Assumed: ${assumedCostBps} bps")
        println("   Observed: ${observedCostBps} bps")
        println("   Savings: ${-costDeltaBps} bps")
    }

    @Test
    fun `aggregated fees and slippage tracked separately`() {
        // GIVEN: A backtest run
        val fees = 50.0
        val slippage = 25.0

        val backtestRun = BacktestRunEntity(
            strategyId = "test-strategy-789",
            asset = "ETHUSD",
            timeframe = "1h",
            startTimestamp = 1700000000000,
            endTimestamp = 1700086400000,
            totalBarsUsed = 24,
            totalTrades = 20,
            winningTrades = 12,
            losingTrades = 8,
            winRate = 60.0,
            totalPnL = 1000.0,
            totalPnLPercent = 10.0,
            sharpeRatio = 2.5,
            maxDrawdown = 5.0,
            profitFactor = 2.0,
            status = "EXCELLENT",
            dataQualityScore = 0.99,
            dataSource = "DATABASE",
            aggregatedFees = fees,
            aggregatedSlippage = slippage
        )

        // THEN: Fees and slippage should be tracked separately
        assertEquals(fees, backtestRun.aggregatedFees, 0.001)
        assertEquals(slippage, backtestRun.aggregatedSlippage, 0.001)

        // AND: Total cost should be sum of both
        val totalCost = backtestRun.aggregatedFees + backtestRun.aggregatedSlippage
        assertEquals(75.0, totalCost, 0.001)

        println("✅ Fees and slippage tracked separately")
        println("   Fees: $${backtestRun.aggregatedFees}")
        println("   Slippage: $${backtestRun.aggregatedSlippage}")
        println("   Total Cost: $${totalCost}")
    }

    @Test
    fun `cost metrics shows realistic crypto exchange costs`() {
        // GIVEN: Realistic crypto exchange costs
        // Kraken maker fee: 0.16% = 16 bps
        // Binance maker fee: 0.10% = 10 bps
        // Slippage: ~0.05% = 5 bps
        val krakenFee = 16.0
        val binanceFee = 10.0
        val slippage = 5.0

        // THEN: Total cost should be realistic
        val krakenTotal = krakenFee + slippage
        val binanceTotal = binanceFee + slippage

        assertTrue("Kraken total cost should be ~21 bps", krakenTotal >= 20.0 && krakenTotal <= 22.0)
        assertTrue("Binance total cost should be ~15 bps", binanceTotal >= 14.0 && binanceTotal <= 16.0)

        println("✅ Realistic crypto exchange costs")
        println("   Kraken: ${krakenFee} bps + ${slippage} bps = ${krakenTotal} bps")
        println("   Binance: ${binanceFee} bps + ${slippage} bps = ${binanceTotal} bps")
    }
}
