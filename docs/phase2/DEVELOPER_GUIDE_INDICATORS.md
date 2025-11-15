# Developer Guide: Indicators

**Version**: 1.0
**Date**: November 14, 2024
**Target Audience**: Developers adding or modifying indicators

---

## Table of Contents

1. [Introduction](#introduction)
2. [Indicator Architecture](#indicator-architecture)
3. [Adding a New Indicator](#adding-a-new-indicator)
4. [Modifying Existing Calculators](#modifying-existing-calculators)
5. [Testing Guidelines](#testing-guidelines)
6. [Code Examples](#code-examples)
7. [Common Pitfalls](#common-pitfalls)
8. [Best Practices](#best-practices)

---

## Introduction

This guide explains how to work with the indicator system in the CryptoTrader application. Whether you're adding a new technical indicator or modifying an existing one, this document provides the patterns and practices to follow.

### Prerequisites

Before working with indicators, you should understand:
- Kotlin programming language
- Technical analysis concepts
- Dependency injection with Hilt
- Unit testing with JUnit

---

## Indicator Architecture

### Core Concepts

All indicators follow a consistent pattern:

1. **Interface Definition**: Defines the calculator's public API
2. **Implementation**: Contains the calculation logic
3. **Hilt Module**: Provides the calculator instance
4. **Integration**: Used by StrategyEvaluatorV2

### File Structure

```
domain/indicators/
├── IndicatorCalculator.kt          # Base interface
├── Candle.kt                        # OHLCV data model
├── cache/
│   └── IndicatorCache.kt           # LRU cache implementation
├── di/
│   └── IndicatorModule.kt          # Hilt DI configuration
├── rsi/
│   ├── RsiCalculator.kt            # Interface
│   └── RsiCalculatorImpl.kt        # Implementation
├── macd/
│   ├── MacdCalculator.kt
│   └── MacdCalculatorImpl.kt
├── bollingerbands/
│   ├── BollingerBandsCalculator.kt
│   └── BollingerBandsCalculatorImpl.kt
├── atr/
│   ├── AtrCalculator.kt
│   └── AtrCalculatorImpl.kt
├── stochastic/
│   ├── StochasticCalculator.kt
│   └── StochasticCalculatorImpl.kt
├── volume/
│   ├── VolumeIndicatorCalculator.kt
│   └── VolumeIndicatorCalculatorImpl.kt
└── movingaverage/
    ├── MovingAverageCalculator.kt
    └── MovingAverageCalculatorImpl.kt
```

---

## Adding a New Indicator

### Step-by-Step Guide

Let's add a new indicator: **CCI (Commodity Channel Index)**

#### Step 1: Create Package Structure

```
domain/indicators/cci/
├── CciCalculator.kt
└── CciCalculatorImpl.kt
```

#### Step 2: Define the Interface

**File**: `domain/indicators/cci/CciCalculator.kt`

```kotlin
package com.cryptotrader.domain.indicators.cci

import com.cryptotrader.domain.indicators.Candle

/**
 * Calculator for CCI (Commodity Channel Index)
 *
 * CCI measures the deviation from the statistical mean.
 * Used to identify cyclical trends in commodities and stocks.
 *
 * Formula: CCI = (Typical Price - SMA) / (0.015 * Mean Deviation)
 * Where Typical Price = (High + Low + Close) / 3
 */
interface CciCalculator {
    /**
     * Calculates CCI values for a series of candles
     *
     * @param candles List of OHLC candles (requires high, low, close)
     * @param period Lookback period for calculation (default: 20)
     * @return List of CCI values (null for insufficient data points)
     */
    fun calculate(candles: List<Candle>, period: Int = 20): List<Double?>
}
```

#### Step 3: Implement the Calculator

**File**: `domain/indicators/cci/CciCalculatorImpl.kt`

```kotlin
package com.cryptotrader.domain.indicators.cci

import com.cryptotrader.domain.indicators.Candle
import com.cryptotrader.domain.indicators.IndicatorCalculator
import com.cryptotrader.domain.indicators.cache.IndicatorCache
import com.cryptotrader.domain.indicators.cache.getOrPut
import com.cryptotrader.domain.indicators.movingaverage.MovingAverageCalculator
import javax.inject.Inject
import kotlin.math.abs

/**
 * Implementation of CCI (Commodity Channel Index) calculator
 */
class CciCalculatorImpl @Inject constructor(
    private val maCalculator: MovingAverageCalculator,
    private val cache: IndicatorCache? = null
) : CciCalculator, IndicatorCalculator {

    override fun calculate(candles: List<Candle>, period: Int): List<Double?> {
        require(period > 0) { "Period must be positive, got $period" }

        // Check cache first
        if (cache != null) {
            val cacheKey = cache.generateKey(
                indicatorName = "CCI",
                parameters = mapOf("period" to period),
                dataHash = candles.hashCode()
            )

            val cached = cache.get<List<Double?>>(cacheKey)
            if (cached != null) {
                return cached
            }
        }

        // Not enough data
        if (candles.size < period) {
            return List(candles.size) { null }
        }

        // Calculate typical prices
        val typicalPrices = candles.map { candle ->
            (candle.high + candle.low + candle.close) / 3.0
        }

        // Calculate SMA of typical prices
        val smaValues = maCalculator.calculateSMA(typicalPrices, period)

        // Calculate CCI
        val cciValues = mutableListOf<Double?>()

        for (i in candles.indices) {
            if (i < period - 1) {
                cciValues.add(null)
                continue
            }

            val sma = smaValues[i] ?: continue

            // Calculate mean deviation
            val recentPrices = typicalPrices.subList(i - period + 1, i + 1)
            val meanDeviation = recentPrices.map { abs(it - sma) }.average()

            // CCI formula
            val cci = if (meanDeviation == 0.0) {
                0.0
            } else {
                (typicalPrices[i] - sma) / (0.015 * meanDeviation)
            }

            cciValues.add(cci)
        }

        // Store in cache
        if (cache != null) {
            val cacheKey = cache.generateKey(
                indicatorName = "CCI",
                parameters = mapOf("period" to period),
                dataHash = candles.hashCode()
            )
            cache.put(cacheKey, cciValues)
        }

        return cciValues
    }
}
```

#### Step 4: Add to Hilt Module

**File**: `domain/indicators/di/IndicatorModule.kt`

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object IndicatorModule {

    // ... existing providers ...

    /**
     * Provides CCI calculator instance
     */
    @Provides
    @Singleton
    fun provideCciCalculator(
        maCalculator: MovingAverageCalculator,
        cache: IndicatorCache
    ): CciCalculator {
        return CciCalculatorImpl(maCalculator, cache)
    }
}
```

#### Step 5: Integrate with StrategyEvaluatorV2

**File**: `domain/trading/StrategyEvaluatorV2.kt`

```kotlin
@Singleton
class StrategyEvaluatorV2 @Inject constructor(
    // ... existing calculators ...
    private val cciCalculator: CciCalculator,  // Add this
    // ... other dependencies ...
) {

    private fun evaluateCondition(
        condition: String,
        marketData: MarketTicker,
        candles: List<Candle>
    ): Boolean {
        val normalizedCondition = condition.trim().lowercase()

        return try {
            val result = when {
                // ... existing conditions ...

                // CCI conditions
                normalizedCondition.contains("cci") -> {
                    evaluateCCI(normalizedCondition, candles)
                }

                // ... rest of conditions ...
            }

            result
        } catch (e: Exception) {
            Timber.e(e, "Error evaluating condition: $condition")
            false
        }
    }

    /**
     * Evaluate CCI (Commodity Channel Index) conditions
     *
     * @param condition Condition string (e.g., "CCI > 100", "CCI < -100")
     * @param candles Historical candle data
     * @return True if CCI condition is met
     */
    private fun evaluateCCI(condition: String, candles: List<Candle>): Boolean {
        val cciValues = cciCalculator.calculate(candles, period = 20)
        val cci = cciValues.lastOrNull() ?: return false

        if (FeatureFlags.LOG_CACHE_PERFORMANCE) {
            Timber.d("[$TAG] CCI calculated: $cci")
        }

        return when {
            condition.contains(">") -> {
                val threshold = extractNumber(condition) ?: 100.0
                cci > threshold
            }
            condition.contains("<") -> {
                val threshold = extractNumber(condition) ?: -100.0
                cci < threshold
            }
            condition.contains("overbought") -> cci > 100.0
            condition.contains("oversold") -> cci < -100.0
            else -> false
        }
    }
}
```

#### Step 6: Write Unit Tests

**File**: `domain/indicators/cci/CciCalculatorImplTest.kt`

```kotlin
package com.cryptotrader.domain.indicators.cci

import com.cryptotrader.domain.indicators.Candle
import com.cryptotrader.domain.indicators.movingaverage.MovingAverageCalculatorImpl
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class CciCalculatorImplTest {

    private lateinit var calculator: CciCalculator

    @Before
    fun setup() {
        val maCalculator = MovingAverageCalculatorImpl()
        calculator = CciCalculatorImpl(maCalculator, cache = null)
    }

    @Test
    fun `calculate should return null for insufficient data`() {
        val candles = listOf(
            Candle(1, 100.0, 105.0, 95.0, 102.0, 1000.0)
        )

        val result = calculator.calculate(candles, period = 20)

        assertEquals(1, result.size)
        assertNull(result[0])
    }

    @Test
    fun `calculate should return CCI values for sufficient data`() {
        // Create 30 candles with realistic OHLC data
        val candles = (0 until 30).map { i ->
            val base = 100.0 + i
            Candle(
                timestamp = i.toLong(),
                open = base,
                high = base + 2,
                low = base - 2,
                close = base + 1,
                volume = 1000.0
            )
        }

        val result = calculator.calculate(candles, period = 20)

        assertEquals(30, result.size)

        // First 19 values should be null
        for (i in 0 until 19) {
            assertNull(result[i])
        }

        // Values from index 19 onwards should be calculated
        for (i in 19 until 30) {
            assertNotNull(result[i])
            assertTrue(result[i]!! in -300.0..300.0)  // Typical CCI range
        }
    }

    @Test
    fun `calculate should handle period edge case`() {
        val candles = (0 until 20).map { i ->
            Candle(i.toLong(), 100.0, 105.0, 95.0, 102.0, 1000.0)
        }

        val result = calculator.calculate(candles, period = 20)

        assertEquals(20, result.size)
        assertNotNull(result.last())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `calculate should throw exception for invalid period`() {
        calculator.calculate(emptyList(), period = -1)
    }
}
```

#### Step 7: Document the Indicator

Add documentation to the API reference (see API_REFERENCE.md).

---

## Modifying Existing Calculators

### When to Modify

Modify an existing calculator when:
- Fixing a bug in the calculation
- Optimizing performance
- Adding optional parameters
- Improving error handling

### Modification Process

1. **Read the Current Implementation**: Understand the existing logic
2. **Write Tests First**: Add tests for the new behavior
3. **Make Changes**: Modify the implementation
4. **Run All Tests**: Ensure no regressions
5. **Update Documentation**: Reflect changes in KDoc comments

### Example: Adding Caching to an Existing Calculator

**Before** (no caching):

```kotlin
class RsiCalculatorImpl : RsiCalculator {
    override fun calculate(closes: List<Double>, period: Int): List<Double?> {
        // Calculation logic
        return rsiValues
    }
}
```

**After** (with caching):

```kotlin
class RsiCalculatorImpl @Inject constructor(
    private val cache: IndicatorCache? = null
) : RsiCalculator {
    override fun calculate(closes: List<Double>, period: Int): List<Double?> {
        // Check cache
        if (cache != null) {
            val key = cache.generateKey(
                "RSI",
                mapOf("period" to period),
                cache.hashData(closes)
            )
            val cached = cache.get<List<Double?>>(key)
            if (cached != null) return cached
        }

        // Calculation logic
        val rsiValues = // ... actual calculation ...

        // Store in cache
        cache?.put(key, rsiValues)

        return rsiValues
    }
}
```

---

## Testing Guidelines

### Unit Testing Checklist

When testing an indicator calculator:

- [ ] Test with insufficient data (should return nulls)
- [ ] Test with exact minimum data (edge case)
- [ ] Test with sufficient data (normal case)
- [ ] Test with invalid parameters (should throw exception)
- [ ] Test calculation correctness against known values
- [ ] Test caching behavior (if applicable)
- [ ] Test thread safety (if applicable)

### Example Test Structure

```kotlin
@Test
fun `calculator name - should behavior when condition`() {
    // ARRANGE: Setup test data
    val input = listOf(/* test data */)
    val expectedOutput = listOf(/* expected result */)

    // ACT: Call the calculator
    val result = calculator.calculate(input, period = 14)

    // ASSERT: Verify the output
    assertEquals(expectedOutput.size, result.size)
    for (i in result.indices) {
        if (expectedOutput[i] == null) {
            assertNull(result[i])
        } else {
            assertEquals(expectedOutput[i]!!, result[i]!!, 0.01)  // Delta for double comparison
        }
    }
}
```

### Testing with Real Market Data

For more realistic tests, use actual historical price data:

```kotlin
@Test
fun `RSI should match known values from TradingView`() {
    // Data from TradingView for BTC/USD on 2024-11-01 to 2024-11-14
    val closes = listOf(
        67891.23, 68123.45, 67456.78, 68901.23, 69234.56,
        // ... more data points
    )

    val rsiValues = calculator.calculate(closes, period = 14)
    val actualRsi = rsiValues.last()

    // Value from TradingView for same period
    val expectedRsi = 58.42

    assertNotNull(actualRsi)
    assertEquals(expectedRsi, actualRsi!!, 1.0)  // Allow 1 point tolerance
}
```

---

## Code Examples

### Example 1: Simple Price-Based Indicator (RSI)

```kotlin
interface RsiCalculator {
    fun calculate(closes: List<Double>, period: Int = 14): List<Double?>
}

class RsiCalculatorImpl : RsiCalculator, IndicatorCalculator {
    override fun calculate(closes: List<Double>, period: Int): List<Double?> {
        require(period > 0) { "Period must be positive" }

        if (closes.size <= period) {
            return List(closes.size) { null }
        }

        // Calculate price changes
        val changes = closes.zipWithNext { a, b -> b - a }

        // Calculate average gains and losses
        var avgGain = 0.0
        var avgLoss = 0.0

        for (i in 0 until period) {
            if (changes[i] > 0) {
                avgGain += changes[i]
            } else {
                avgLoss += abs(changes[i])
            }
        }

        avgGain /= period
        avgLoss /= period

        // Calculate RSI using smoothing
        val rsiValues = mutableListOf<Double?>()
        repeat(period) { rsiValues.add(null) }

        val rs = avgGain / avgLoss
        rsiValues.add(100.0 - (100.0 / (1.0 + rs)))

        // Continue with Wilder's smoothing...
        return rsiValues
    }
}
```

### Example 2: OHLC-Based Indicator (ATR)

```kotlin
interface AtrCalculator {
    fun calculate(
        highs: List<Double>,
        lows: List<Double>,
        closes: List<Double>,
        period: Int = 14
    ): List<Double?>
}

class AtrCalculatorImpl : AtrCalculator {
    override fun calculate(
        highs: List<Double>,
        lows: List<Double>,
        closes: List<Double>,
        period: Int
    ): List<Double?> {
        require(highs.size == lows.size && lows.size == closes.size) {
            "All input lists must have the same size"
        }

        // Calculate True Range for each period
        val trueRanges = mutableListOf<Double>()

        for (i in 1 until closes.size) {
            val tr = maxOf(
                highs[i] - lows[i],                   // Current high - low
                abs(highs[i] - closes[i - 1]),        // Current high - prev close
                abs(lows[i] - closes[i - 1])          // Current low - prev close
            )
            trueRanges.add(tr)
        }

        // Calculate ATR using moving average of True Range
        val atrValues = mutableListOf<Double?>(null)  // First value is null

        if (trueRanges.size < period) {
            return List(closes.size) { null }
        }

        // Initial ATR (simple average)
        var atr = trueRanges.take(period).average()
        atrValues.add(atr)

        // Subsequent ATR (Wilder's smoothing)
        for (i in period until trueRanges.size) {
            atr = (atr * (period - 1) + trueRanges[i]) / period
            atrValues.add(atr)
        }

        return atrValues
    }
}
```

### Example 3: Composite Indicator (MACD depends on MA)

```kotlin
class MacdCalculatorImpl @Inject constructor(
    private val maCalculator: MovingAverageCalculator
) : MacdCalculator {

    override fun calculate(
        closes: List<Double>,
        fastPeriod: Int,
        slowPeriod: Int,
        signalPeriod: Int
    ): MacdResult {
        // Use MovingAverageCalculator for EMA calculations
        val fastEma = maCalculator.calculateEMA(closes, fastPeriod)
        val slowEma = maCalculator.calculateEMA(closes, slowPeriod)

        // Calculate MACD line
        val macdLine = fastEma.zip(slowEma).map { (fast, slow) ->
            if (fast != null && slow != null) fast - slow else null
        }

        // Calculate signal line (EMA of MACD line)
        val macdValues = macdLine.filterNotNull()
        val signalLine = maCalculator.calculateEMA(macdValues, signalPeriod)

        // Calculate histogram
        val histogram = macdLine.zip(signalLine).map { (macd, signal) ->
            if (macd != null && signal != null) macd - signal else null
        }

        return MacdResult(macdLine, signalLine, histogram)
    }
}
```

---

## Common Pitfalls

### 1. Not Handling Insufficient Data

**Problem**: Crash when not enough data points

```kotlin
// BAD: Will crash if closes.size < period
fun calculate(closes: List<Double>, period: Int): List<Double> {
    val sma = closes.takeLast(period).average()
    return listOf(sma)
}
```

**Solution**: Return nulls for invalid indices

```kotlin
// GOOD: Returns null for insufficient data
fun calculate(closes: List<Double>, period: Int): List<Double?> {
    if (closes.size < period) {
        return List(closes.size) { null }
    }

    val result = mutableListOf<Double?>()
    repeat(period - 1) { result.add(null) }

    for (i in (period - 1) until closes.size) {
        val sma = closes.subList(i - period + 1, i + 1).average()
        result.add(sma)
    }

    return result
}
```

### 2. Ignoring Cache

**Problem**: Slow performance due to repeated calculations

```kotlin
// BAD: No caching
class RsiCalculatorImpl : RsiCalculator {
    override fun calculate(closes: List<Double>, period: Int): List<Double?> {
        // Expensive calculation every time
        return expensiveCalculation(closes, period)
    }
}
```

**Solution**: Check and use cache

```kotlin
// GOOD: Uses cache
class RsiCalculatorImpl @Inject constructor(
    private val cache: IndicatorCache? = null
) : RsiCalculator {
    override fun calculate(closes: List<Double>, period: Int): List<Double?> {
        if (cache != null) {
            val key = cache.generateKey("RSI", mapOf("period" to period), cache.hashData(closes))
            cache.get<List<Double?>>(key)?.let { return it }
        }

        val result = expensiveCalculation(closes, period)

        cache?.put(key, result)
        return result
    }
}
```

### 3. Incorrect Index Alignment

**Problem**: Result indices don't match input indices

```kotlin
// BAD: Result size != input size
fun calculate(closes: List<Double>, period: Int): List<Double?> {
    return closes.drop(period).map { calculateValue(it) }
    // Result is shorter than input!
}
```

**Solution**: Keep result size equal to input size

```kotlin
// GOOD: Result size == input size
fun calculate(closes: List<Double>, period: Int): List<Double?> {
    val result = mutableListOf<Double?>()

    // First (period - 1) values are null
    repeat(period - 1) { result.add(null) }

    // Calculate for remaining values
    for (i in (period - 1) until closes.size) {
        result.add(calculateValue(closes, i, period))
    }

    return result  // Same size as closes
}
```

### 4. Not Validating Inputs

**Problem**: Crashes with invalid inputs

```kotlin
// BAD: No validation
fun calculate(highs: List<Double>, lows: List<Double>): List<Double?> {
    return highs.zip(lows).map { (h, l) -> h - l }  // Will crash if sizes differ
}
```

**Solution**: Validate all inputs

```kotlin
// GOOD: Validates inputs
fun calculate(highs: List<Double>, lows: List<Double>): List<Double?> {
    require(highs.size == lows.size) {
        "Highs and lows must have the same size. " +
        "Got ${highs.size} highs and ${lows.size} lows."
    }
    require(highs.isNotEmpty()) { "Input lists must not be empty" }

    return highs.zip(lows).map { (h, l) -> h - l }
}
```

### 5. Floating Point Comparison

**Problem**: Exact comparison of doubles

```kotlin
// BAD: Exact comparison
if (rsi == 30.0) {  // May never be true due to floating point precision
    // ...
}
```

**Solution**: Use ranges or thresholds

```kotlin
// GOOD: Use range
if (rsi in 29.9..30.1) {
    // ...
}

// Or use epsilon comparison
fun isApproximately(a: Double, b: Double, epsilon: Double = 0.001): Boolean {
    return abs(a - b) < epsilon
}
```

---

## Best Practices

### 1. Follow Naming Conventions

```kotlin
// Calculators: <Indicator>Calculator
interface RsiCalculator { }
interface MacdCalculator { }

// Implementations: <Indicator>CalculatorImpl
class RsiCalculatorImpl : RsiCalculator { }
class MacdCalculatorImpl : MacdCalculator { }

// Result classes: <Indicator>Result
data class MacdResult(val macdLine: List<Double?>, ...)
data class BollingerBandsResult(val upperBand: List<Double?>, ...)
```

### 2. Use KDoc Comments

```kotlin
/**
 * Calculates RSI (Relative Strength Index) values
 *
 * RSI is a momentum oscillator that measures the speed and magnitude
 * of recent price changes to evaluate overbought or oversold conditions.
 *
 * @param closes List of closing prices
 * @param period Lookback period for calculation (default: 14)
 * @return List of RSI values in range 0-100, null for insufficient data
 * @throws IllegalArgumentException if period is not positive
 *
 * Example:
 * ```kotlin
 * val closes = listOf(44.0, 44.5, 45.0, 45.5, 46.0)
 * val rsi = calculator.calculate(closes, period = 14)
 * ```
 */
fun calculate(closes: List<Double>, period: Int = 14): List<Double?>
```

### 3. Make Calculators Stateless

```kotlin
// GOOD: Stateless (can be safely shared)
class RsiCalculatorImpl : RsiCalculator {
    override fun calculate(closes: List<Double>, period: Int): List<Double?> {
        // No instance variables
        val result = // ... calculate based only on inputs
        return result
    }
}

// BAD: Stateful (not thread-safe)
class RsiCalculatorImpl : RsiCalculator {
    private var lastResult: List<Double?>? = null  // Mutable state!

    override fun calculate(closes: List<Double>, period: Int): List<Double?> {
        lastResult = // ... calculate
        return lastResult!!
    }
}
```

### 4. Use Nullable Return Types for Indicators

```kotlin
// GOOD: Nullable list elements
fun calculate(closes: List<Double>, period: Int): List<Double?> {
    // Can represent "not enough data" as null
    return listOf(null, null, 42.5, 45.2, ...)
}

// BAD: Non-null with magic values
fun calculate(closes: List<Double>, period: Int): List<Double> {
    // Using -1 as "no data" is confusing
    return listOf(-1.0, -1.0, 42.5, 45.2, ...)
}
```

### 5. Consistent Parameter Defaults

```kotlin
// GOOD: Standard defaults across all calculators
interface RsiCalculator {
    fun calculate(closes: List<Double>, period: Int = 14): List<Double?>
}

interface MacdCalculator {
    fun calculate(
        closes: List<Double>,
        fastPeriod: Int = 12,
        slowPeriod: Int = 26,
        signalPeriod: Int = 9
    ): MacdResult
}

// Use industry-standard default values
```

### 6. Optimize Cache Keys

```kotlin
// GOOD: Include all relevant parameters
fun generateCacheKey(
    indicatorName: String,
    period: Int,
    multiplier: Double,
    dataHash: Int
): String {
    return "$indicatorName|period=$period,multiplier=$multiplier|$dataHash"
}

// BAD: Missing parameters leads to incorrect cache hits
fun generateCacheKey(indicatorName: String, dataHash: Int): String {
    return "$indicatorName|$dataHash"  // Period not included!
}
```

---

## Summary

When working with indicators:

1. **Follow the pattern**: Interface + Implementation + Hilt Module + Tests
2. **Handle edge cases**: Insufficient data, invalid parameters, null inputs
3. **Use caching**: Leverage IndicatorCache for performance
4. **Write tests**: Cover all edge cases and validate calculations
5. **Document thoroughly**: KDoc comments for all public APIs
6. **Keep it stateless**: Calculators should be thread-safe singletons

---

## Related Documentation

- [Phase 2 Implementation Guide](./PHASE2_IMPLEMENTATION_GUIDE.md)
- [Indicator System Architecture](./INDICATOR_SYSTEM_ARCHITECTURE.md)
- [API Reference](./API_REFERENCE.md)

---

**Last Updated**: November 14, 2024
**Document Version**: 1.0
