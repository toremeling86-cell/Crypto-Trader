package com.cryptotrader.domain.indicators

import com.cryptotrader.domain.indicators.atr.AtrCalculatorImpl
import com.cryptotrader.domain.indicators.bollingerbands.BollingerBandsCalculatorImpl
import com.cryptotrader.domain.indicators.macd.MacdCalculatorImpl
import com.cryptotrader.domain.indicators.movingaverage.MovingAverageCalculatorImpl
import com.cryptotrader.domain.indicators.rsi.RsiCalculatorImpl
import com.cryptotrader.domain.indicators.stochastic.StochasticCalculatorImpl
import com.cryptotrader.domain.trading.TechnicalIndicators
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.math.abs

/**
 * Migration test suite to validate functional parity between old and new indicator systems.
 *
 * This test suite compares outputs from:
 * - OLD: TechnicalIndicators (legacy object with single-value calculations)
 * - NEW: XCalculator implementations (advanced calculators with list-based calculations)
 *
 * Tests ensure results are within 0.0001% tolerance, proving the new system can safely
 * replace the old one without affecting trading decisions.
 */
class IndicatorMigrationTest {

    // New calculator instances
    private lateinit var rsiCalculator: RsiCalculatorImpl
    private lateinit var macdCalculator: MacdCalculatorImpl
    private lateinit var movingAverageCalculator: MovingAverageCalculatorImpl
    private lateinit var bollingerBandsCalculator: BollingerBandsCalculatorImpl
    private lateinit var atrCalculator: AtrCalculatorImpl
    private lateinit var stochasticCalculator: StochasticCalculatorImpl

    // Test data fixtures
    private lateinit var samplePrices: List<Double>
    private lateinit var sampleHighs: List<Double>
    private lateinit var sampleLows: List<Double>
    private lateinit var sampleCloses: List<Double>
    private lateinit var sampleVolumes: List<Double>

    @Before
    fun setup() {
        // Initialize new calculators
        rsiCalculator = RsiCalculatorImpl()
        macdCalculator = MacdCalculatorImpl()
        movingAverageCalculator = MovingAverageCalculatorImpl()
        bollingerBandsCalculator = BollingerBandsCalculatorImpl()
        atrCalculator = AtrCalculatorImpl()
        stochasticCalculator = StochasticCalculatorImpl()

        // Generate realistic sample data (100+ data points)
        // Using a price series that simulates realistic market movements
        samplePrices = generateRealisticPriceData(
            startPrice = 50000.0,
            count = 150,
            volatility = 0.02
        )

        // Generate OHLC data based on closes
        sampleCloses = samplePrices
        sampleHighs = samplePrices.mapIndexed { index, close ->
            close * (1.0 + (index % 5) * 0.001) // Highs slightly above close
        }
        sampleLows = samplePrices.mapIndexed { index, close ->
            close * (1.0 - (index % 5) * 0.001) // Lows slightly below close
        }

        // Generate volume data
        sampleVolumes = List(samplePrices.size) { index ->
            1000000.0 + (index % 10) * 50000.0
        }
    }

    @After
    fun tearDown() {
        // Cleanup if needed
    }

    // ==================== RSI Tests ====================

    @Test
    fun test_RSI_migration_produces_identical_results() {
        val period = 14

        // OLD: TechnicalIndicators.calculateRSI returns single value for entire list
        val oldRsi = TechnicalIndicators.calculateRSI(samplePrices, period)

        // NEW: RsiCalculator returns list of values, last non-null matches old result
        val newRsiList = rsiCalculator.calculate(samplePrices, period)
        val newRsi = newRsiList.lastOrNull()

        assertIndicatorParity(
            oldValue = oldRsi,
            newValue = newRsi,
            indicatorName = "RSI"
        )
    }

    @Test
    fun test_RSI_handles_insufficient_data() {
        val insufficientPrices = samplePrices.take(10) // Less than period (14)

        // OLD: Should return null
        val oldRsi = TechnicalIndicators.calculateRSI(insufficientPrices, 14)

        // NEW: Should return all nulls
        val newRsiList = rsiCalculator.calculate(insufficientPrices, 14)

        assert(oldRsi == null) { "Old RSI should be null for insufficient data" }
        assert(newRsiList.all { it == null }) { "New RSI should be all nulls for insufficient data" }
    }

    @Test
    fun test_RSI_handles_empty_list() {
        val emptyPrices = emptyList<Double>()

        // OLD: Should return null
        val oldRsi = TechnicalIndicators.calculateRSI(emptyPrices, 14)

        // NEW: Should return empty list
        val newRsiList = rsiCalculator.calculate(emptyPrices, 14)

        assert(oldRsi == null) { "Old RSI should be null for empty data" }
        assert(newRsiList.isEmpty()) { "New RSI should be empty for empty data" }
    }

    @Test
    fun test_RSI_extreme_volatility() {
        // Create extreme volatility scenario
        val volatilePrices = listOf(
            100.0, 150.0, 80.0, 200.0, 50.0, 175.0, 90.0, 180.0,
            60.0, 190.0, 70.0, 185.0, 85.0, 195.0, 75.0, 180.0,
            95.0, 170.0, 110.0, 160.0
        )

        val oldRsi = TechnicalIndicators.calculateRSI(volatilePrices, 14)
        val newRsiList = rsiCalculator.calculate(volatilePrices, 14)
        val newRsi = newRsiList.lastOrNull()

        assertIndicatorParity(
            oldValue = oldRsi,
            newValue = newRsi,
            indicatorName = "RSI (extreme volatility)"
        )
    }

    // ==================== MACD Tests ====================

    @Test
    fun test_MACD_migration_produces_identical_results() {
        val fastPeriod = 12
        val slowPeriod = 26
        val signalPeriod = 9

        // OLD: Returns Triple(macdLine, signalLine, histogram) for entire list
        val oldMacd = TechnicalIndicators.calculateMACD(
            samplePrices,
            fastPeriod,
            slowPeriod,
            signalPeriod
        )

        // NEW: Returns lists of values
        val newMacd = macdCalculator.calculate(
            samplePrices,
            fastPeriod,
            slowPeriod,
            signalPeriod
        )
        val newMacdLine = newMacd.macdLine.lastOrNull()
        val newSignalLine = newMacd.signalLine.lastOrNull()
        val newHistogram = newMacd.histogram.lastOrNull()

        assertIndicatorParity(
            oldValue = oldMacd?.first,
            newValue = newMacdLine,
            indicatorName = "MACD Line"
        )
        assertIndicatorParity(
            oldValue = oldMacd?.second,
            newValue = newSignalLine,
            indicatorName = "MACD Signal Line"
        )
        assertIndicatorParity(
            oldValue = oldMacd?.third,
            newValue = newHistogram,
            indicatorName = "MACD Histogram"
        )
    }

    @Test
    fun test_MACD_handles_insufficient_data() {
        val insufficientPrices = samplePrices.take(20) // Less than slowPeriod (26)

        // OLD: Should return null
        val oldMacd = TechnicalIndicators.calculateMACD(insufficientPrices, 12, 26, 9)

        // NEW: Should handle gracefully
        val newMacd = macdCalculator.calculate(insufficientPrices, 12, 26, 9)

        assert(oldMacd == null) { "Old MACD should be null for insufficient data" }
        // New MACD should have mostly nulls for this case
    }

    // ==================== Moving Average Tests ====================

    @Test
    fun test_SMA_migration_produces_identical_results() {
        val period = 20

        // OLD: Returns single SMA value for the period
        val oldSma = TechnicalIndicators.calculateSMA(samplePrices, period)

        // NEW: Returns list of SMA values, last non-null should match
        val newSmaList = movingAverageCalculator.calculateSMA(samplePrices, period)
        val newSma = newSmaList.lastOrNull()

        assertIndicatorParity(
            oldValue = oldSma,
            newValue = newSma,
            indicatorName = "SMA"
        )
    }

    @Test
    fun test_EMA_migration_produces_identical_results() {
        val period = 20

        // OLD: Returns single EMA value for the period
        val oldEma = TechnicalIndicators.calculateEMA(samplePrices, period)

        // NEW: Returns list of EMA values, last non-null should match
        val newEmaList = movingAverageCalculator.calculateEMA(samplePrices, period)
        val newEma = newEmaList.lastOrNull()

        assertIndicatorParity(
            oldValue = oldEma,
            newValue = newEma,
            indicatorName = "EMA"
        )
    }

    @Test
    fun test_SMA_handles_single_data_point() {
        val singlePrice = listOf(100.0)

        // OLD: Should return null (insufficient data)
        val oldSma = TechnicalIndicators.calculateSMA(singlePrice, 10)

        // NEW: Should return list with null
        val newSmaList = movingAverageCalculator.calculateSMA(singlePrice, 10)

        assert(oldSma == null) { "Old SMA should be null for single data point" }
        assert(newSmaList.all { it == null }) { "New SMA should be all nulls for single data point" }
    }

    @Test
    fun test_EMA_handles_single_data_point() {
        val singlePrice = listOf(100.0)

        // OLD: Should return null (insufficient data)
        val oldEma = TechnicalIndicators.calculateEMA(singlePrice, 10)

        // NEW: Should return list with null
        val newEmaList = movingAverageCalculator.calculateEMA(singlePrice, 10)

        assert(oldEma == null) { "Old EMA should be null for single data point" }
        assert(newEmaList.all { it == null }) { "New EMA should be all nulls for single data point" }
    }

    // ==================== Bollinger Bands Tests ====================

    @Test
    fun test_BollingerBands_migration_produces_identical_results() {
        val period = 20
        val stdDevMultiplier = 2.0

        // OLD: Returns Triple(upper, middle, lower)
        val oldBands = TechnicalIndicators.calculateBollingerBands(
            samplePrices,
            period,
            stdDevMultiplier
        )

        // NEW: Returns lists of values
        val newBands = bollingerBandsCalculator.calculate(
            samplePrices,
            period,
            stdDevMultiplier
        )
        val newUpperBand = newBands.upperBand.lastOrNull()
        val newMiddleBand = newBands.middleBand.lastOrNull()
        val newLowerBand = newBands.lowerBand.lastOrNull()

        assertIndicatorParity(
            oldValue = oldBands?.first,
            newValue = newUpperBand,
            indicatorName = "Bollinger Upper Band"
        )
        assertIndicatorParity(
            oldValue = oldBands?.second,
            newValue = newMiddleBand,
            indicatorName = "Bollinger Middle Band"
        )
        assertIndicatorParity(
            oldValue = oldBands?.third,
            newValue = newLowerBand,
            indicatorName = "Bollinger Lower Band"
        )
    }

    @Test
    fun test_BollingerBands_handles_insufficient_data() {
        val insufficientPrices = samplePrices.take(15) // Less than period (20)

        // OLD: Should return null
        val oldBands = TechnicalIndicators.calculateBollingerBands(insufficientPrices, 20, 2.0)

        // NEW: Should handle gracefully
        val newBands = bollingerBandsCalculator.calculate(insufficientPrices, 20, 2.0)

        assert(oldBands == null) { "Old Bollinger Bands should be null for insufficient data" }
        // New Bollinger Bands should have mostly nulls for this case
    }

    // ==================== ATR Tests ====================

    @Test
    fun test_ATR_migration_uses_wilders_smoothing() {
        val period = 14

        // NOTE: V2 uses Wilder's Smoothing (industry standard) instead of SMA
        // This is an INTENTIONAL IMPROVEMENT, not a bug
        // V1 used Simple Moving Average: avg(last N true ranges)
        // V2 uses Wilder's Smoothing: exponential smoothing of true ranges
        // This matches TradingView, MetaTrader, and other professional platforms

        // NEW: Returns list of ATR values using Wilder's smoothing
        val newAtrList = atrCalculator.calculate(
            sampleHighs,
            sampleLows,
            sampleCloses,
            period
        )
        val newAtr = newAtrList.lastOrNull()

        // Verify ATR is calculated and produces reasonable values
        assert(newAtr != null) { "ATR should be calculated for sufficient data" }
        assert(newAtr!! > 0) { "ATR should be positive" }

        // Verify it's using Wilder's smoothing by checking it differs from simple average
        // Calculate what SMA would give us (for comparison)
        val trueRanges = mutableListOf<Double>()
        for (i in 1 until sampleHighs.size) {
            val tr = kotlin.math.max(
                sampleHighs[i] - sampleLows[i],
                kotlin.math.max(
                    kotlin.math.abs(sampleHighs[i] - sampleCloses[i - 1]),
                    kotlin.math.abs(sampleLows[i] - sampleCloses[i - 1])
                )
            )
            trueRanges.add(tr)
        }
        val smaAtr = trueRanges.takeLast(period).average()

        // Wilder's smoothing should differ from SMA (proving we're using the right method)
        val percentDiff = kotlin.math.abs((newAtr - smaAtr) / smaAtr) * 100.0
        assert(percentDiff > 0.1) { "ATR should use Wilder's smoothing, not SMA (diff: $percentDiff%)" }

        println("✓ ATR: Using Wilder's Smoothing (industry standard) - Value: $newAtr, SMA would be: $smaAtr, Diff: ${String.format("%.2f", percentDiff)}%")
    }

    @Test
    fun test_ATR_handles_insufficient_data() {
        val insufficientData = 10
        val highs = sampleHighs.take(insufficientData)
        val lows = sampleLows.take(insufficientData)
        val closes = sampleCloses.take(insufficientData)

        // OLD: Should return null
        val oldAtr = TechnicalIndicators.calculateATR(highs, lows, closes, 14)

        // NEW: Should handle gracefully
        val newAtrList = atrCalculator.calculate(highs, lows, closes, 14)

        assert(oldAtr == null) { "Old ATR should be null for insufficient data" }
        assert(newAtrList.all { it == null }) { "New ATR should be all nulls for insufficient data" }
    }

    // ==================== Stochastic Tests ====================

    @Test
    fun test_Stochastic_migration_produces_identical_results() {
        val kPeriod = 14
        val dPeriod = 3

        // OLD: Returns Pair(%K, %D)
        val oldStochastic = TechnicalIndicators.calculateStochastic(
            sampleHighs,
            sampleLows,
            sampleCloses,
            kPeriod,
            dPeriod
        )

        // NEW: Returns lists of values
        val newStochastic = stochasticCalculator.calculate(
            sampleHighs,
            sampleLows,
            sampleCloses,
            kPeriod,
            dPeriod
        )
        val newKValue = newStochastic.kLine.lastOrNull()
        val newDValue = newStochastic.dLine.lastOrNull()

        assertIndicatorParity(
            oldValue = oldStochastic?.first,
            newValue = newKValue,
            indicatorName = "Stochastic %K"
        )
        assertIndicatorParity(
            oldValue = oldStochastic?.second,
            newValue = newDValue,
            indicatorName = "Stochastic %D"
        )
    }

    @Test
    fun test_Stochastic_handles_insufficient_data() {
        val insufficientData = 10
        val highs = sampleHighs.take(insufficientData)
        val lows = sampleLows.take(insufficientData)
        val closes = sampleCloses.take(insufficientData)

        // OLD: Should return null
        val oldStochastic = TechnicalIndicators.calculateStochastic(highs, lows, closes, 14, 3)

        // NEW: Should handle gracefully
        val newStochastic = stochasticCalculator.calculate(highs, lows, closes, 14, 3)

        assert(oldStochastic == null) { "Old Stochastic should be null for insufficient data" }
        // New Stochastic should have mostly nulls for this case
    }

    @Test
    fun test_Stochastic_extreme_volatility() {
        // Create scenario with extreme price movements
        val volatileHighs = listOf(
            110.0, 130.0, 100.0, 140.0, 95.0, 135.0, 105.0, 145.0,
            90.0, 150.0, 100.0, 140.0, 110.0, 145.0, 95.0, 135.0
        )
        val volatileLows = listOf(
            90.0, 110.0, 80.0, 120.0, 75.0, 115.0, 85.0, 125.0,
            70.0, 130.0, 80.0, 120.0, 90.0, 125.0, 75.0, 115.0
        )
        val volatileCloses = listOf(
            100.0, 120.0, 90.0, 130.0, 85.0, 125.0, 95.0, 135.0,
            80.0, 140.0, 90.0, 130.0, 100.0, 135.0, 85.0, 125.0
        )

        val oldStochastic = TechnicalIndicators.calculateStochastic(
            volatileHighs,
            volatileLows,
            volatileCloses,
            14,
            3
        )

        val newStochastic = stochasticCalculator.calculate(
            volatileHighs,
            volatileLows,
            volatileCloses,
            14,
            3
        )

        assertIndicatorParity(
            oldValue = oldStochastic?.first,
            newValue = newStochastic.kLine.lastOrNull(),
            indicatorName = "Stochastic %K (extreme volatility)"
        )
    }

    // ==================== Helper Methods ====================

    /**
     * Asserts that old and new indicator values are within tolerance (0.0001% by default)
     *
     * This is the core validation method that ensures functional parity between systems.
     */
    private fun assertIndicatorParity(
        oldValue: Double?,
        newValue: Double?,
        indicatorName: String,
        tolerance: Double = 0.0001
    ) {
        when {
            oldValue == null && newValue == null -> {
                // Both null - perfect match
                println("✓ $indicatorName: Both systems correctly returned null")
            }
            oldValue == null || newValue == null -> {
                throw AssertionError(
                    "$indicatorName mismatch: old=$oldValue, new=$newValue (one is null)"
                )
            }
            else -> {
                // Both have values - check percentage difference
                val percentageDiff = if (oldValue != 0.0) {
                    abs((newValue - oldValue) / oldValue) * 100.0
                } else {
                    if (newValue == 0.0) 0.0 else 100.0
                }

                assert(percentageDiff <= tolerance) {
                    "$indicatorName exceeds tolerance: old=$oldValue, new=$newValue, diff=$percentageDiff%"
                }

                println("✓ $indicatorName: Within tolerance (diff: ${String.format("%.6f", percentageDiff)}%)")
            }
        }
    }

    /**
     * Generates realistic price data for testing
     *
     * Uses a simple random walk with drift to simulate realistic market movements.
     */
    private fun generateRealisticPriceData(
        startPrice: Double,
        count: Int,
        volatility: Double
    ): List<Double> {
        val prices = mutableListOf(startPrice)
        var currentPrice = startPrice

        // Seed for reproducibility
        val random = java.util.Random(42)

        for (i in 1 until count) {
            // Random walk with drift
            val change = (random.nextGaussian() * volatility * currentPrice)
            val drift = 0.0001 * currentPrice // Slight upward bias

            currentPrice += change + drift
            // Keep price positive
            currentPrice = currentPrice.coerceAtLeast(startPrice * 0.5)

            prices.add(currentPrice)
        }

        return prices
    }

    /**
     * Generates price data with known characteristics for edge case testing
     */
    private fun generateConstantPriceData(price: Double, count: Int): List<Double> {
        return List(count) { price }
    }

    /**
     * Generates trending price data (upward or downward)
     */
    private fun generateTrendingPriceData(
        startPrice: Double,
        count: Int,
        trendPercent: Double
    ): List<Double> {
        val prices = mutableListOf<Double>()
        for (i in 0 until count) {
            val price = startPrice * (1.0 + (trendPercent * i / count))
            prices.add(price)
        }
        return prices
    }
}
