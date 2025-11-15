# Phase 2 Migration Test Infrastructure

## Overview

This test suite validates functional parity between the old and new indicator calculation systems:

- **OLD System**: `TechnicalIndicators` (legacy object with single-value calculations)
- **NEW System**: Advanced calculator implementations (list-based calculations with full history)

## Test Coverage

### Indicators Tested

1. **RSI (Relative Strength Index)**
   - Standard calculation (period=14)
   - Insufficient data handling
   - Empty list handling
   - Extreme volatility scenarios

2. **MACD (Moving Average Convergence Divergence)**
   - MACD Line
   - Signal Line
   - Histogram
   - Insufficient data handling

3. **Moving Averages**
   - Simple Moving Average (SMA)
   - Exponential Moving Average (EMA)
   - Single data point edge cases

4. **Bollinger Bands**
   - Upper Band
   - Middle Band (SMA)
   - Lower Band
   - Insufficient data handling

5. **ATR (Average True Range)**
   - Standard calculation (period=14)
   - Insufficient data handling

6. **Stochastic Oscillator**
   - %K Line
   - %D Line
   - Extreme volatility scenarios

## Tolerance

All tests use a **0.0001%** tolerance for comparing old vs new values. This extremely tight tolerance ensures:
- No rounding errors accumulate
- Trading decisions remain identical
- Safe migration with zero behavioral changes

## Running the Tests

### From Command Line

```bash
# Run all tests
./gradlew :app:testDebugUnitTest

# Run only migration tests
./gradlew :app:testDebugUnitTest --tests "com.cryptotrader.domain.indicators.IndicatorMigrationTest"

# Run specific indicator test
./gradlew :app:testDebugUnitTest --tests "com.cryptotrader.domain.indicators.IndicatorMigrationTest.test_RSI_migration_produces_identical_results"
```

### From Android Studio

1. Right-click on `IndicatorMigrationTest.kt`
2. Select "Run 'IndicatorMigrationTest'"

## Test Data

The test suite uses three types of data:

1. **Realistic Price Data** (150 points)
   - Generated using random walk with drift
   - Simulates real market movements
   - Reproducible (seeded random)

2. **Edge Case Data**
   - Empty lists
   - Single data points
   - Insufficient data for period

3. **Extreme Volatility Data**
   - High percentage price swings
   - Tests numerical stability

## Success Criteria

All tests must pass with:
- ✓ Zero tolerance violations
- ✓ Correct null handling
- ✓ Edge cases handled identically
- ✓ Fast execution (< 1 second per test)

## Adding New Tests

When adding new indicator tests:

1. Add calculator instance to test class
2. Initialize in `@Before` method
3. Create test method following naming convention:
   ```kotlin
   @Test
   fun test_IndicatorName_migration_produces_identical_results() {
       // Test implementation
   }
   ```
4. Use `assertIndicatorParity()` helper for validation
5. Add edge case tests for the indicator

## Common Patterns

### Single Value Comparison
```kotlin
val oldValue = TechnicalIndicators.calculateIndicator(data, period)
val newValueList = calculator.calculate(data, period)
val newValue = newValueList.lastOrNull()

assertIndicatorParity(oldValue, newValue, "Indicator Name")
```

### Multi-Value Comparison (e.g., MACD)
```kotlin
val oldResult = TechnicalIndicators.calculateMACD(...)
val newResult = calculator.calculate(...)

assertIndicatorParity(oldResult?.first, newResult.macdLine.lastOrNull(), "MACD Line")
assertIndicatorParity(oldResult?.second, newResult.signalLine.lastOrNull(), "Signal Line")
assertIndicatorParity(oldResult?.third, newResult.histogram.lastOrNull(), "Histogram")
```

## Troubleshooting

### Test Fails with Tolerance Violation

1. Check if algorithm implementations match exactly
2. Verify multipliers and constants are identical
3. Check for floating-point precision differences
4. Review order of operations in calculations

### Test Fails with Null Mismatch

1. Verify minimum data requirements are identical
2. Check boundary conditions (period, list size)
3. Ensure null handling logic matches

### Test Takes Too Long

1. Reduce sample data size (but keep > period requirements)
2. Check for O(n²) or worse complexity
3. Profile the calculator implementation

## Next Steps

After all tests pass:

1. Update integration points to use new calculators
2. Add feature flags for gradual rollout
3. Monitor production metrics
4. Deprecate old TechnicalIndicators
5. Remove old code after validation period
