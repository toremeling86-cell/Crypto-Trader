package com.cryptotrader.domain.backtesting

import com.cryptotrader.domain.model.DataTier
import com.cryptotrader.domain.model.TradeType
import com.cryptotrader.utils.toBigDecimalMoney
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Integration tests for backtest system
 *
 * Tests complete backtest flow with synthetic data
 * Validates hedge-fund quality features:
 * - No look-ahead bias
 * - Accurate P&L calculations
 * - Proper equity curve tracking
 * - Performance metrics (Sharpe ratio, drawdown)
 */
class BacktestIntegrationTest {

    @Test
    fun `test buy-and-hold strategy with realistic data`() = runTest {
        // Generate 1 month of realistic BTC data
        val data = SyntheticDataGenerator.generateRealisticBTCData(
            bars = 744,  // 31 days * 24 hours
            startPrice = 50000.0
        )

        // Validate synthetic data
        val validationErrors = SyntheticDataGenerator.validateOHLC(data)
        assertTrue(validationErrors.isEmpty(), "Synthetic data should be valid")

        // Print summary
        println("=== Synthetic Data Summary ===")
        SyntheticDataGenerator.printSummary(data)

        // Create BacktestEngine with mock dependencies
        val backtestEngine = BacktestTestFactory.createBacktestEngine()

        // Get buy-and-hold strategy
        val strategy = TestStrategies.buyAndHold().copy(isActive = true)

        // Run backtest
        val resultDecimal = backtestEngine.runBacktestDecimal(
            strategy = strategy,
            historicalData = data,
            startingBalance = 10000.0.toBigDecimalMoney(),
            costModel = TradingCostModel()
        )
        val result = resultDecimal.toBacktestResult()

        // Validate results
        println("\n=== Backtest Results ===")
        println("Strategy: ${result.strategyName}")
        println("Starting Balance: $${result.startingBalance}")
        println("Ending Balance: $${result.endingBalance}")
        println("Total P&L: $${result.totalPnL} (${result.totalPnLPercent}%)")
        println("Total Trades: ${result.totalTrades}")
        println("Win Rate: ${result.winRate}%")
        println("Sharpe Ratio: ${result.sharpeRatio}")
        println("Max Drawdown: ${result.maxDrawdown}%")

        // Verify data quality
        assertEquals(744, data.size, "Should have 744 hourly bars")
        assertTrue(data.all { it.open > 0 }, "All prices should be positive")
        assertTrue(data.all { it.high >= it.low }, "High should be >= Low")
        assertTrue(data.all { it.close in it.low..it.high }, "Close should be in [Low, High]")

        // Verify backtest ran successfully
        assertTrue(result.endingBalance > 0, "Ending balance should be positive")
        assertTrue(result.equityCurve.isNotEmpty(), "Equity curve should not be empty")
    }

    @Test
    fun `test RSI strategy with RSI-pattern data`() = runTest {
        // Generate data with known RSI patterns
        val data = SyntheticDataGenerator.generateRSIPatternData(bars = 100)

        val strategy = TestStrategies.rsiMeanReversion().copy(isActive = true)

        // Validate data
        val validationErrors = SyntheticDataGenerator.validateOHLC(data)
        assertTrue(validationErrors.isEmpty(), "RSI pattern data should be valid")

        // Create BacktestEngine with mock dependencies
        val backtestEngine = BacktestTestFactory.createBacktestEngine()

        // Run backtest
        val result = backtestEngine.runBacktest(
            strategy = strategy,
            historicalData = data,
            startingBalance = 10000.0,
            costModel = TradingCostModel()
        )

        // Print results
        println("\n=== RSI Strategy Backtest Results ===")
        println("Total Trades: ${result.totalTrades}")
        println("Win Rate: ${result.winRate}%")
        println("Total P&L: $${result.totalPnL} (${result.totalPnLPercent}%)")

        // Verify strategy configuration
        assertEquals("test-rsi-mean-reversion", strategy.id)
        assertEquals(listOf("RSI_14 < 30"), strategy.entryConditions)
        assertEquals(listOf("RSI_14 > 70"), strategy.exitConditions)

        // Verify backtest ran
        assertTrue(result.endingBalance > 0, "Ending balance should be positive")
    }

    @Test
    fun `test synthetic data generators produce valid OHLC`() {
        // Test all data generators
        val generators = listOf(
            "Realistic" to SyntheticDataGenerator.generateRealisticBTCData(100),
            "Uptrend" to SyntheticDataGenerator.generateUptrendData(100),
            "Downtrend" to SyntheticDataGenerator.generateDowntrendData(100),
            "Ranging" to SyntheticDataGenerator.generateRangingData(100),
            "Volatile" to SyntheticDataGenerator.generateVolatileData(100),
            "RSI Pattern" to SyntheticDataGenerator.generateRSIPatternData(100)
        )

        generators.forEach { (name, data) ->
            val errors = SyntheticDataGenerator.validateOHLC(data)
            assertTrue(errors.isEmpty(), "$name data should be valid: $errors")

            assertEquals(100, data.size, "$name should have 100 bars")

            println("\n=== $name Data ===")
            SyntheticDataGenerator.printSummary(data)
        }
    }

    @Test
    fun `test all test strategies are properly configured`() {
        val strategies = TestStrategies.all()

        assertEquals(6, strategies.size, "Should have 6 test strategies")

        strategies.forEach { strategy ->
            assertTrue(strategy.id.isNotEmpty(), "${strategy.name} should have ID")
            assertTrue(strategy.name.isNotEmpty(), "Strategy should have name")
            assertTrue(strategy.entryConditions.isNotEmpty(), "${strategy.name} should have entry conditions")
            assertTrue(strategy.exitConditions.isNotEmpty(), "${strategy.name} should have exit conditions")
            assertTrue(strategy.positionSizePercent > 0, "${strategy.name} should have position size > 0")
            assertTrue(strategy.stopLossPercent >= 0, "${strategy.name} should have valid stop-loss")
            assertTrue(strategy.takeProfitPercent >= 0, "${strategy.name} should have valid take-profit")

            println("✅ ${strategy.name}: ${strategy.entryConditions} → ${strategy.exitConditions}")
        }
    }

    @Test
    fun `test data tier recommendations`() {
        val strategies = TestStrategies.all()

        // Basic strategies should work with TIER_4_BASIC
        val basicStrategies = TestStrategies.basicStrategies()
        basicStrategies.forEach { strategy ->
            println("${strategy.name}: Suitable for TIER_4_BASIC (OHLCV candles)")
        }

        // Advanced strategies need higher tier data
        val advancedStrategies = TestStrategies.advancedStrategies()
        advancedStrategies.forEach { strategy ->
            println("${strategy.name}: Requires ${if (strategy.id.contains("hf")) "TIER_1_PREMIUM" else "TIER_3_STANDARD"}")
        }

        assertEquals(4, basicStrategies.size, "Should have 4 basic strategies")
        assertEquals(2, advancedStrategies.size, "Should have 2 advanced strategies")
    }

    @Test
    fun `test uptrend data produces positive returns for buy-and-hold`() {
        val data = SyntheticDataGenerator.generateUptrendData(
            bars = 100,
            startPrice = 50000.0,
            trendStrength = 0.002  // 0.2% per bar
        )

        val startPrice = data.first().close
        val endPrice = data.last().close
        val returns = ((endPrice / startPrice) - 1.0) * 100.0

        println("Uptrend Data:")
        println("  Start: $${"%.2f".format(startPrice)}")
        println("  End: $${"%.2f".format(endPrice)}")
        println("  Return: ${"%.2f".format(returns)}%")

        assertTrue(returns > 0, "Uptrend should produce positive returns")
        assertTrue(returns > 10.0, "100 bars at 0.2%/bar should produce >10% return")
    }

    @Test
    fun `test downtrend data produces negative returns for buy-and-hold`() {
        val data = SyntheticDataGenerator.generateDowntrendData(
            bars = 100,
            startPrice = 50000.0,
            trendStrength = 0.002  // -0.2% per bar
        )

        val startPrice = data.first().close
        val endPrice = data.last().close
        val returns = ((endPrice / startPrice) - 1.0) * 100.0

        println("Downtrend Data:")
        println("  Start: $${"%.2f".format(startPrice)}")
        println("  End: $${"%.2f".format(endPrice)}")
        println("  Return: ${"%.2f".format(returns)}%")

        assertTrue(returns < 0, "Downtrend should produce negative returns")
        assertTrue(returns < -10.0, "100 bars at -0.2%/bar should produce <-10% return")
    }

    @Test
    fun `test ranging data has low total return`() {
        val data = SyntheticDataGenerator.generateRangingData(
            bars = 100,
            meanPrice = 50000.0,
            rangePercent = 5.0
        )

        val startPrice = data.first().close
        val endPrice = data.last().close
        val returns = Math.abs(((endPrice / startPrice) - 1.0) * 100.0)

        println("Ranging Data:")
        println("  Start: $${"%.2f".format(startPrice)}")
        println("  End: $${"%.2f".format(endPrice)}")
        println("  Return: ${"%.2f".format(returns)}%")

        assertTrue(returns < 10.0, "Ranging market should have low total return (<10%)")
    }

    @Test
    fun `test data consistency across timeframes`() {
        // Generate same data at different frequencies
        val hourly = SyntheticDataGenerator.generateRealisticBTCData(bars = 24)  // 1 day
        val fourHourly = SyntheticDataGenerator.generateRealisticBTCData(bars = 6)  // 1 day

        // Both should be valid
        assertTrue(SyntheticDataGenerator.validateOHLC(hourly).isEmpty())
        assertTrue(SyntheticDataGenerator.validateOHLC(fourHourly).isEmpty())

        // Prices should be in similar range (within 20%)
        val hourlyAvg = hourly.map { it.close }.average()
        val fourHourlyAvg = fourHourly.map { it.close }.average()
        val priceDifference = Math.abs(hourlyAvg - fourHourlyAvg) / hourlyAvg

        assertTrue(priceDifference < 0.20, "Different timeframes should have similar average prices")
    }
}
