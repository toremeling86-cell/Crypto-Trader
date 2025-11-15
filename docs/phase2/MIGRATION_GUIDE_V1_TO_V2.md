# Migration Guide: V1 to V2 Indicator System

**Version**: 1.0
**Date**: November 14, 2024
**Target Audience**: Developers migrating from V1 to V2

---

## Table of Contents

1. [Migration Overview](#migration-overview)
2. [Feature Flag Strategy](#feature-flag-strategy)
3. [Step-by-Step Migration](#step-by-step-migration)
4. [Code Migration Examples](#code-migration-examples)
5. [Testing Strategy](#testing-strategy)
6. [Rollback Procedures](#rollback-procedures)
7. [Performance Monitoring](#performance-monitoring)
8. [Common Issues](#common-issues)

---

## Migration Overview

### What's Changing?

The migration from V1 to V2 involves transitioning from a simple, static indicator system to an advanced, dependency-injected calculator architecture.

### V1 vs V2 Comparison

```
┌────────────────────────────────────────────────────────────────┐
│ Feature                 │ V1 System    │ V2 System             │
├────────────────────────────────────────────────────────────────┤
│ Architecture            │ Static       │ DI with Hilt          │
│ Caching                 │ None         │ LRU Cache             │
│ Data Format             │ Price lists  │ OHLCV Candles         │
│ Testability             │ Limited      │ Full DI mocking       │
│ Performance             │ Baseline     │ 50-60% faster (cache) │
│ Indicators              │ 5 basic      │ 7 advanced            │
│ Thread Safety           │ Basic        │ Full concurrent       │
│ Extensibility           │ Hard         │ Easy (interfaces)     │
└────────────────────────────────────────────────────────────────┘
```

### Migration Timeline

**Phase 2.5**: Validation (1-2 weeks)
- Enable A/B comparison
- Run both systems in parallel
- Monitor for discrepancies

**Phase 2.6**: Beta Rollout (1 week)
- Enable V2 for production
- Monitor performance and correctness
- Keep rollback option ready

**Phase 2.7**: Full Migration (1 week)
- Remove V1 code
- Clean up feature flags
- Final documentation update

---

## Feature Flag Strategy

### The Three Feature Flags

#### 1. USE_ADVANCED_INDICATORS

**Purpose**: Switch between V1 and V2 systems

**Location**: `utils/FeatureFlags.kt`

**Default**: `false` (safe start with V1)

**Migration Stages**:
```kotlin
// Stage 1: Validation (Keep V1)
const val USE_ADVANCED_INDICATORS = false

// Stage 2: Beta Rollout (Switch to V2)
const val USE_ADVANCED_INDICATORS = true

// Stage 3: Final (V1 code removed, flag removed)
// Flag no longer needed
```

#### 2. LOG_CACHE_PERFORMANCE

**Purpose**: Monitor cache effectiveness

**Default**: `true`

**Keep enabled during**:
- Validation phase (to verify cache is working)
- Beta rollout (to monitor cache hit rates)
- First few weeks of production

**Disable when**:
- System proven stable
- Cache metrics meet expectations (>60% hit rate)

#### 3. COMPARE_INDICATOR_OUTPUTS

**Purpose**: A/B testing - run both systems and compare

**Default**: `false`

**Warning**: Doubles computation cost!

**Use during**:
- Validation phase only
- When debugging discrepancies between V1 and V2

**Example**:
```kotlin
const val COMPARE_INDICATOR_OUTPUTS = true  // During validation

// In code:
if (FeatureFlags.COMPARE_INDICATOR_OUTPUTS) {
    val v1Result = strategyEvaluator.evaluateEntryConditions(strategy, marketData)
    val v2Result = strategyEvaluatorV2.evaluateEntryConditions(strategy, marketData)

    if (v1Result != v2Result) {
        Timber.w("Discrepancy: V1=$v1Result, V2=$v2Result for ${marketData.pair}")
        // Log details for investigation
    }
}
```

---

## Step-by-Step Migration

### Phase 2.5: Validation (Week 1-2)

#### Step 1: Enable A/B Comparison

**File**: `utils/FeatureFlags.kt`

```kotlin
object FeatureFlags {
    const val USE_ADVANCED_INDICATORS = false  // Still using V1
    const val LOG_CACHE_PERFORMANCE = true     // Enable logging
    const val COMPARE_INDICATOR_OUTPUTS = true // Enable A/B testing
}
```

#### Step 2: Update TradingEngine for Comparison

**File**: `domain/trading/TradingEngine.kt`

```kotlin
class TradingEngine @Inject constructor(
    private val strategyEvaluator: StrategyEvaluator,        // V1
    private val strategyEvaluatorV2: StrategyEvaluatorV2    // V2
) {
    private fun evaluateStrategy(
        strategy: Strategy,
        marketData: MarketTicker
    ): Boolean {
        // Update history for both systems
        strategyEvaluator.updatePriceHistory(marketData.pair, marketData.last)
        strategyEvaluatorV2.updatePriceHistory(marketData.pair, marketData)

        // Primary result (V1 for now)
        val primaryResult = strategyEvaluator.evaluateEntryConditions(
            strategy, marketData
        )

        // Compare with V2 if flag enabled
        if (FeatureFlags.COMPARE_INDICATOR_OUTPUTS) {
            val v2Result = strategyEvaluatorV2.evaluateEntryConditions(
                strategy, marketData
            )

            if (primaryResult != v2Result) {
                Timber.w(
                    "[MIGRATION] Discrepancy detected:\n" +
                    "Strategy: ${strategy.name}\n" +
                    "Pair: ${marketData.pair}\n" +
                    "V1 result: $primaryResult\n" +
                    "V2 result: $v2Result\n" +
                    "Conditions: ${strategy.entryConditions}"
                )
            }
        }

        return primaryResult  // Still using V1 result
    }
}
```

#### Step 3: Monitor Logs

Look for discrepancies in the logs:

```bash
# Filter for discrepancies
adb logcat | grep "MIGRATION.*Discrepancy"

# Monitor cache performance
adb logcat | grep "IndicatorCache"
adb logcat | grep "StrategyEvaluatorV2"
```

#### Step 4: Analyze Discrepancies

**Common causes of discrepancies**:

1. **ATR and Bollinger Bands**: V2 uses full OHLCV data, V1 uses only prices
   - Expected: Some difference
   - Action: Verify V2 is more accurate

2. **RSI and MACD**: Should match closely
   - Expected: < 1% difference
   - Action: Investigate if difference > 1%

3. **Insufficient History**: V1 and V2 might have different history requirements
   - Expected: Early discrepancies until both have enough data
   - Action: Wait for history to build up

#### Step 5: Run Paper Trading

Enable paper trading mode and run for 1-2 weeks:

```kotlin
// In your trading configuration
const val PAPER_TRADING_MODE = true
```

Monitor:
- Number of signals generated by V1 vs V2
- Trade outcomes if both systems were used
- Cache hit rates
- Any crashes or errors

**Success Criteria**:
- < 5% difference in signal generation
- Cache hit rate > 60%
- No crashes
- V2 performance within 10% of V1

---

### Phase 2.6: Beta Rollout (Week 3)

#### Step 1: Switch to V2

**File**: `utils/FeatureFlags.kt`

```kotlin
object FeatureFlags {
    const val USE_ADVANCED_INDICATORS = true   // NOW USING V2!
    const val LOG_CACHE_PERFORMANCE = true     // Keep logging
    const val COMPARE_INDICATOR_OUTPUTS = false // Disable (expensive)
}
```

#### Step 2: Update TradingEngine

Simplify to use V2 primarily:

```kotlin
class TradingEngine @Inject constructor(
    private val strategyEvaluator: StrategyEvaluator,        // Keep for rollback
    private val strategyEvaluatorV2: StrategyEvaluatorV2
) {
    private fun evaluateStrategy(
        strategy: Strategy,
        marketData: MarketTicker
    ): Boolean {
        return if (FeatureFlags.USE_ADVANCED_INDICATORS) {
            // V2: Advanced system
            strategyEvaluatorV2.updatePriceHistory(marketData.pair, marketData)
            strategyEvaluatorV2.evaluateEntryConditions(strategy, marketData)
        } else {
            // V1: Fallback
            strategyEvaluator.updatePriceHistory(marketData.pair, marketData.last)
            strategyEvaluator.evaluateEntryConditions(strategy, marketData)
        }
    }
}
```

#### Step 3: Monitor Production

**Key Metrics**:
- Strategy evaluation latency (should be 50-60% faster with cache)
- Cache hit rate (target: > 60%)
- Memory usage (should be < 200 KB increase)
- Any errors or crashes

**Monitoring Code**:
```kotlin
class PerformanceMonitor @Inject constructor() {

    fun monitorEvaluation(block: () -> Boolean): Boolean {
        val startTime = System.currentTimeMillis()
        val result = block()
        val duration = System.currentTimeMillis() - startTime

        Timber.d("[PERFORMANCE] Strategy evaluation: ${duration}ms")

        if (duration > 50) {
            Timber.w("[PERFORMANCE] Slow evaluation: ${duration}ms (threshold: 50ms)")
        }

        return result
    }
}

// Usage:
val result = performanceMonitor.monitorEvaluation {
    evaluator.evaluateEntryConditions(strategy, marketData)
}
```

#### Step 4: Collect Metrics

Create a metrics dashboard:

```kotlin
data class CacheMetrics(
    val totalRequests: Long,
    val cacheHits: Long,
    val cacheMisses: Long,
    val hitRate: Double
) {
    companion object {
        fun calculate(cache: IndicatorCache): CacheMetrics {
            // Implement metrics collection
            // This would require adding counters to IndicatorCache
        }
    }
}
```

**Success Criteria**:
- No crashes for 1 week
- Cache hit rate > 60%
- Average evaluation time < 5ms
- Memory usage stable

---

### Phase 2.7: Full Migration (Week 4)

#### Step 1: Remove V1 Code

**Files to Delete**:
- `domain/trading/TechnicalIndicators.kt` (V1 static indicator methods)
- `domain/trading/StrategyEvaluator.kt` (V1 evaluator)

**Files to Update**:
- `domain/trading/TradingEngine.kt` (remove V1 references)
- `utils/FeatureFlags.kt` (remove flags)

#### Step 2: Clean Up TradingEngine

**Before**:
```kotlin
class TradingEngine @Inject constructor(
    private val strategyEvaluator: StrategyEvaluator,        // V1
    private val strategyEvaluatorV2: StrategyEvaluatorV2    // V2
) {
    private fun evaluateStrategy(...): Boolean {
        return if (FeatureFlags.USE_ADVANCED_INDICATORS) {
            strategyEvaluatorV2.evaluateEntryConditions(...)
        } else {
            strategyEvaluator.evaluateEntryConditions(...)
        }
    }
}
```

**After**:
```kotlin
class TradingEngine @Inject constructor(
    private val strategyEvaluator: StrategyEvaluatorV2  // Only V2!
) {
    private fun evaluateStrategy(...): Boolean {
        // No more feature flag checks
        strategyEvaluator.updatePriceHistory(marketData.pair, marketData)
        return strategyEvaluator.evaluateEntryConditions(strategy, marketData)
    }
}
```

#### Step 3: Remove Feature Flags

**Delete**: `utils/FeatureFlags.kt`

**Update all references**: Remove `if (FeatureFlags.xxx)` checks

#### Step 4: Rename V2 to Default

Optional but recommended:

```bash
# Rename StrategyEvaluatorV2 → StrategyEvaluator
mv StrategyEvaluatorV2.kt StrategyEvaluator.kt

# Update class name
# StrategyEvaluatorV2 → StrategyEvaluator
```

**Benefits**:
- Cleaner naming
- Indicates V2 is now the standard
- Reduces confusion for new developers

#### Step 5: Final Testing

Run full test suite:

```bash
# Unit tests
./gradlew test

# Integration tests
./gradlew connectedAndroidTest

# Paper trading verification
# Run for 24 hours in paper trading mode
```

---

## Code Migration Examples

### Example 1: Migrating Strategy Evaluation

**V1 Code**:
```kotlin
class MyViewModel @Inject constructor(
    private val strategyEvaluator: StrategyEvaluator
) {
    fun evaluateStrategy(strategy: Strategy, ticker: MarketTicker) {
        // Update history
        strategyEvaluator.updatePriceHistory(ticker.pair, ticker.last)

        // Get price history
        val prices = strategyEvaluator.priceHistory[ticker.pair]

        // Evaluate
        val shouldEnter = strategyEvaluator.evaluateEntryConditions(
            strategy, ticker
        )
    }
}
```

**V2 Code**:
```kotlin
class MyViewModel @Inject constructor(
    private val strategyEvaluator: StrategyEvaluatorV2
) {
    fun evaluateStrategy(strategy: Strategy, ticker: MarketTicker) {
        // Update history (now stores Candles, not just prices)
        strategyEvaluator.updatePriceHistory(ticker.pair, ticker)

        // Check if enough history
        if (!strategyEvaluator.hasEnoughHistory(ticker.pair)) {
            return // Not enough data yet
        }

        // Evaluate (automatically uses cache)
        val shouldEnter = strategyEvaluator.evaluateEntryConditions(
            strategy, ticker
        )
    }
}
```

### Example 2: Migrating Manual Indicator Calculation

**V1 Code**:
```kotlin
fun calculateRSI(prices: List<Double>): Double? {
    return TechnicalIndicators.calculateRSI(prices)
}

fun calculateMACD(prices: List<Double>): Triple<Double, Double, Double>? {
    return TechnicalIndicators.calculateMACD(prices)
}
```

**V2 Code**:
```kotlin
@Inject constructor(
    private val rsiCalculator: RsiCalculator,
    private val macdCalculator: MacdCalculator
) {
    fun calculateRSI(prices: List<Double>): Double? {
        val rsiValues = rsiCalculator.calculate(prices, period = 14)
        return rsiValues.lastOrNull()
    }

    fun calculateMACD(prices: List<Double>): Triple<Double, Double, Double>? {
        val result = macdCalculator.calculate(prices)

        val macdLine = result.macdLine.lastOrNull()
        val signalLine = result.signalLine.lastOrNull()
        val histogram = result.histogram.lastOrNull()

        return if (macdLine != null && signalLine != null && histogram != null) {
            Triple(macdLine, signalLine, histogram)
        } else {
            null
        }
    }
}
```

### Example 3: Migrating OHLC-Based Indicators

**V1 Code** (didn't support OHLCV):
```kotlin
// ATR was approximated from price volatility
fun calculateATR(prices: List<Double>): Double {
    val returns = prices.zipWithNext { a, b -> abs((b - a) / a) }
    return returns.average() * prices.last()
}
```

**V2 Code** (proper ATR with OHLC):
```kotlin
@Inject constructor(
    private val atrCalculator: AtrCalculator,
    private val priceHistoryManager: PriceHistoryManager
) {
    fun calculateATR(pair: String): Double? {
        val candles = priceHistoryManager.getHistory(pair)

        val highs = candles.map { it.high }
        val lows = candles.map { it.low }
        val closes = candles.map { it.close }

        val atrValues = atrCalculator.calculate(highs, lows, closes, period = 14)
        return atrValues.lastOrNull()
    }
}
```

---

## Testing Strategy

### Unit Testing

**Test V1 and V2 give equivalent results**:

```kotlin
class IndicatorMigrationTest {

    @Test
    fun `RSI V1 and V2 should match`() {
        val prices = listOf(44.0, 44.34, 44.09, /* ... */)

        // V1
        val v1Rsi = TechnicalIndicators.calculateRSI(prices)

        // V2
        val v2Calculator = RsiCalculatorImpl()
        val v2RsiValues = v2Calculator.calculate(prices, period = 14)
        val v2Rsi = v2RsiValues.lastOrNull()

        // Should match within tolerance
        assertNotNull(v1Rsi)
        assertNotNull(v2Rsi)
        assertEquals(v1Rsi!!, v2Rsi!!, 0.01)  // 0.01 tolerance
    }
}
```

### Integration Testing

**Test complete strategy evaluation**:

```kotlin
class StrategyEvaluationMigrationTest {

    @Inject lateinit var evaluatorV1: StrategyEvaluator
    @Inject lateinit var evaluatorV2: StrategyEvaluatorV2

    @Test
    fun `strategy evaluation V1 and V2 should match`() {
        val strategy = Strategy(
            name = "Test",
            entryConditions = listOf("RSI < 30", "MACD_crossover")
        )

        val marketData = createMockMarketTicker()

        // Build history
        val historicalPrices = loadHistoricalData()
        historicalPrices.forEach { price ->
            evaluatorV1.updatePriceHistory("BTC/USD", price)
        }
        historicalPrices.forEach { ticker ->
            evaluatorV2.updatePriceHistory("BTC/USD", ticker)
        }

        // Evaluate
        val v1Result = evaluatorV1.evaluateEntryConditions(strategy, marketData)
        val v2Result = evaluatorV2.evaluateEntryConditions(strategy, marketData)

        assertEquals(v1Result, v2Result)
    }
}
```

---

## Rollback Procedures

### Quick Rollback (Feature Flag)

If issues occur during beta rollout:

**Step 1**: Disable V2 immediately

```kotlin
// In FeatureFlags.kt
const val USE_ADVANCED_INDICATORS = false  // ROLLBACK TO V1
```

**Step 2**: Restart app or redeploy

**Step 3**: Verify V1 is working

**Step 4**: Investigate V2 issue before re-enabling

### Full Rollback (After V1 Removal)

If issues occur after V1 code removed:

**Step 1**: Revert git commits

```bash
git revert <commit-hash-of-v1-removal>
git push
```

**Step 2**: Redeploy previous version

**Step 3**: Investigate before reattempting migration

---

## Performance Monitoring

### Metrics to Track

```kotlin
data class MigrationMetrics(
    // Evaluation Performance
    val avgEvaluationTime: Long,        // milliseconds
    val p95EvaluationTime: Long,        // 95th percentile
    val maxEvaluationTime: Long,

    // Cache Performance
    val cacheHitRate: Double,           // percentage
    val cacheMissRate: Double,
    val cacheSize: Int,

    // Accuracy
    val totalEvaluations: Long,
    val v1v2Matches: Long,              // when both systems running
    val v1v2Mismatches: Long,
    val accuracyRate: Double,

    // Resource Usage
    val memoryUsageMB: Double,
    val cpuUsagePercent: Double
)
```

### Performance Dashboard

Create a simple dashboard screen:

```kotlin
@Composable
fun MigrationDashboard(metrics: MigrationMetrics) {
    Column {
        Text("Migration Status")

        MetricCard(
            title = "Cache Hit Rate",
            value = "${(metrics.cacheHitRate * 100).toInt()}%",
            target = "60%",
            isGood = metrics.cacheHitRate > 0.6
        )

        MetricCard(
            title = "Avg Evaluation Time",
            value = "${metrics.avgEvaluationTime}ms",
            target = "< 5ms",
            isGood = metrics.avgEvaluationTime < 5
        )

        MetricCard(
            title = "V1/V2 Accuracy",
            value = "${(metrics.accuracyRate * 100).toInt()}%",
            target = "95%",
            isGood = metrics.accuracyRate > 0.95
        )
    }
}
```

---

## Common Issues

### Issue 1: Cache Hit Rate Too Low

**Symptom**: Cache hit rate < 40%

**Cause**:
- Parameters changing on every call
- Input data changing frequently
- Cache size too small

**Solution**:
```kotlin
// Increase cache size
@Provides
@Singleton
fun provideIndicatorCache(): IndicatorCache {
    return IndicatorCache(maxSize = 200)  // Increased from 100
}

// Standardize parameters
val STANDARD_RSI_PERIOD = 14
val STANDARD_MACD_PERIODS = Triple(12, 26, 9)
```

### Issue 2: V1 and V2 Results Differ

**Symptom**: Frequent mismatches between V1 and V2

**Cause**:
- ATR/Bollinger use different data (OHLC vs prices)
- Rounding differences in calculations
- Insufficient history in one system

**Solution**:
- Accept differences for OHLC-based indicators (V2 is more accurate)
- For price-based indicators, investigate if difference > 1%

### Issue 3: Memory Usage Increase

**Symptom**: App using significantly more memory

**Cause**:
- Too many candles stored
- Cache size too large
- Memory leak in new code

**Solution**:
```kotlin
// Reduce candle history
private val maxCandlesPerPair = 100  // Reduced from 200

// Reduce cache size
return IndicatorCache(maxSize = 50)  // Reduced from 100

// Profile with Android Profiler to find leaks
```

---

## Success Checklist

### Phase 2.5 Complete When:
- [ ] A/B comparison running for 1 week
- [ ] V1/V2 match rate > 95%
- [ ] Cache hit rate > 60%
- [ ] No crashes or errors
- [ ] Paper trading results acceptable

### Phase 2.6 Complete When:
- [ ] V2 running in production for 1 week
- [ ] Performance metrics meet targets
- [ ] No user-reported issues
- [ ] Memory usage stable

### Phase 2.7 Complete When:
- [ ] V1 code removed
- [ ] Feature flags removed
- [ ] All tests passing
- [ ] Documentation updated
- [ ] Code review approved

---

## Related Documentation

- [Phase 2 Implementation Guide](./PHASE2_IMPLEMENTATION_GUIDE.md)
- [Indicator System Architecture](./INDICATOR_SYSTEM_ARCHITECTURE.md)
- [API Reference](./API_REFERENCE.md)

---

**Last Updated**: November 14, 2024
**Document Version**: 1.0
