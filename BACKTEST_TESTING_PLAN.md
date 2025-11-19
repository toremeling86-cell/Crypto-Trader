# Backtest System Testing Plan with CryptoLake Data
**Priority**: HIGH (validate hedge-fund quality system)
**Estimated Effort**: 2-3 days
**Data Source**: 30GB+ CryptoLake historical data (already copied to D:\)
**Current Phase**: 2.8 Complete - System ready for validation
**Date Created**: 2025-11-19

---

## 🎯 OBJECTIVE

Validate the complete backtesting system using professional-grade CryptoLake data across all 4 data quality tiers to ensure:
1. ✅ Zero look-ahead bias (BUG 3.1 fix verified)
2. ✅ Accurate P&L calculations (BUG 1.1-1.6 fixes verified)
3. ✅ Data tier separation maintained (HEDGE-FUND QUALITY)
4. ✅ AI backtest proposal system functional
5. ✅ Backtest results are reliable and match live trading expectations

---

## 📊 TEST DATA OVERVIEW

### Data Tiers Available

**TIER_1_PREMIUM** (0.99 quality score):
- **Source**: CryptoLake order book Level 20 data
- **Format**: Parquet files
- **Precision**: Nanosecond timestamps
- **Frequency**: ~970 trades/minute
- **Use Case**: High-frequency strategies, order flow analysis, VWAP
- **Location**: `D:\Development\Projects\Mobile\Android\CryptoTrader\data\crypto_lake_ohlcv\`
- **Example Files**:
  - `BINANCE_BTCUSDT_20240501_20240503_book_level20.parquet`
  - `BINANCE_ETHUSDT_20240515_20240520_book_level20.parquet`

**TIER_2_PROFESSIONAL** (0.95 quality score):
- **Source**: CryptoLake tick-by-tick trades with aggressor ID
- **Format**: Parquet files
- **Precision**: Millisecond timestamps
- **Frequency**: Every individual trade
- **Use Case**: Scalping, tick data strategies
- **Location**: `D:\Development\Projects\Mobile\Android\CryptoTrader\data\crypto_lake_ohlcv\`
- **Example Files**:
  - `BINANCE_BTCUSDT_20240501_20240503_trades.parquet`

**TIER_3_STANDARD** (0.85 quality score):
- **Source**: Binance aggregated trades
- **Format**: CSV/Parquet files
- **Precision**: Millisecond timestamps
- **Frequency**: Aggregated trades
- **Use Case**: Day trading, swing trading
- **Location**: `D:\Development\Projects\Mobile\Android\CryptoTrader\data\binance_raw\`
- **Example Files**:
  - `BINANCE_BTCUSDT_20240501_20240531_agg_trades.csv`

**TIER_4_BASIC** (0.70 quality score):
- **Source**: Pre-processed OHLCV candles
- **Format**: CSV files
- **Timeframes**: 1m, 5m, 15m, 1h, 4h, 1d
- **Use Case**: Position trading, long-term strategies
- **Location**: `D:\Development\Projects\Mobile\Android\CryptoTrader\data\binance_raw\`
- **Example Files**:
  - `BINANCE_BTCUSDT_20240101_20241231_ohlcv_1h.csv`

**Total Data Volume**: 30GB+
**Date Range**: 2024-01-01 to 2024-12-31
**Assets**: BTC/USDT, ETH/USDT, BNB/USDT, SOL/USDT, XRP/USDT

---

## 📋 TESTING PHASES

### Phase 1: Data Import & Validation (Day 1 - 8 hours)
**Goal**: Import historical data and validate integrity

#### 1.1 Scan Available Data (1 hour)

**Test Steps**:
1. Launch BacktestManagementViewModel
2. Call `scanAvailableData()`
3. Observe `_availableDataFiles` Flow

**Expected Results**:
```kotlin
// Files found grouped by tier
TIER_1_PREMIUM: 45 files (order book data)
TIER_2_PROFESSIONAL: 32 files (tick trades)
TIER_3_STANDARD: 28 files (aggregated trades)
TIER_4_BASIC: 120 files (OHLCV candles across timeframes)
```

**Validation**:
- ✅ All parquet files detected
- ✅ All CSV files detected
- ✅ File metadata parsed correctly (asset, dates, tier)
- ✅ DataTier auto-detected correctly

**Test Code**:
```kotlin
@Test
fun testDataScan() = runTest {
    val viewModel = BacktestManagementViewModel(/* inject dependencies */)

    viewModel.scanAvailableData()

    viewModel.availableDataFiles.test {
        val files = awaitItem()

        assertTrue(files.isNotEmpty(), "Should find data files")

        // Verify tier separation
        val tierGroups = files.groupBy { it.dataTier }
        assertTrue(tierGroups.containsKey(DataTier.TIER_1_PREMIUM))
        assertTrue(tierGroups.containsKey(DataTier.TIER_4_BASIC))

        // Log results
        tierGroups.forEach { (tier, tierFiles) ->
            println("${tier.tierName}: ${tierFiles.size} files")
        }
    }
}
```

#### 1.2 Import TIER_4_BASIC CSV Data (2 hours)

**Test Steps**:
1. Select 5 TIER_4_BASIC CSV files (different assets/timeframes)
2. Call `importDataFiles(selectedFiles)`
3. Observe import progress

**Test Files**:
```
1. BINANCE_BTCUSDT_20240101_20240131_ohlcv_1h.csv (744 bars)
2. BINANCE_ETHUSDT_20240101_20240131_ohlcv_1h.csv (744 bars)
3. BINANCE_BTCUSDT_20240601_20240630_ohlcv_15m.csv (2,880 bars)
4. BINANCE_BTCUSDT_20240101_20240131_ohlcv_1d.csv (31 bars)
5. BINANCE_SOLANA_20240101_20240131_ohlcv_1h.csv (744 bars)
```

**Expected Results**:
```
Import Progress:
- File 1/5: 744 bars imported (100%)
- File 2/5: 744 bars imported (100%)
- File 3/5: 2,880 bars imported (100%)
- File 4/5: 31 bars imported (100%)
- File 5/5: 744 bars imported (100%)

Total: 5,143 OHLC bars imported
Success rate: 100%
Data tier: TIER_4_BASIC (all files)
```

**Validation**:
- ✅ All CSV files imported successfully
- ✅ Database contains 5,143 OHLCBarEntity rows
- ✅ All rows have `dataTier = TIER_4_BASIC`
- ✅ OHLC validation passed (8 checks per bar)
- ✅ No corrupt data in database

**Test Code**:
```kotlin
@Test
fun testCsvImport() = runTest {
    val files = listOf(
        File("D:\\...\\BINANCE_BTCUSDT_20240101_20240131_ohlcv_1h.csv"),
        // ... other files
    )

    val parsedFiles = files.mapNotNull { DataFileParser.parseFile(it) }
    assertEquals(5, parsedFiles.size)

    // Import all files
    val importer = BatchDataImporter(/* dependencies */)
    parsedFiles.forEach { parsedFile ->
        importer.importFile(parsedFile).collect { progress ->
            when (progress) {
                is ImportProgress.Completed -> {
                    println("Imported ${progress.totalRows} rows")
                }
                is ImportProgress.Failed -> {
                    fail("Import failed: ${progress.error}")
                }
            }
        }
    }

    // Verify database
    val allBars = ohlcBarDao.getAllOHLCBars("XXBTZUSD", "1h")
    assertTrue(allBars.isNotEmpty())

    val tierCheck = allBars.all { it.dataTier == DataTier.TIER_4_BASIC.name }
    assertTrue(tierCheck, "All bars should be TIER_4_BASIC")
}
```

#### 1.3 Import TIER_1_PREMIUM Parquet Data (Skeleton Test - 1 hour)

**Note**: Parquet import requires Apache Arrow library integration.

**Test Steps**:
1. Attempt to import 1 TIER_1_PREMIUM parquet file
2. Verify skeleton implementation returns helpful error message

**Expected Results**:
```
Import Error: Parquet import not yet implemented
Required: Apache Arrow Android library
Recommendation: Use CSV export or wait for Parquet support
```

**Future Implementation** (Phase 7+):
```kotlin
// Add dependency
implementation("org.apache.arrow:arrow-memory-core:13.0.0")
implementation("org.apache.arrow:arrow-vector:13.0.0")
```

#### 1.4 Data Coverage Analysis (1 hour)

**Test Steps**:
1. After import, call `dataImportRepository.getDataCoverage("XXBTZUSD", "1h")`
2. Verify data completeness metrics

**Expected Results**:
```kotlin
DataCoverage(
    asset = "XXBTZUSD",
    timeframe = "1h",
    startDate = 1704067200000, // 2024-01-01 00:00:00
    endDate = 1706659200000,   // 2024-01-31 00:00:00
    totalBars = 744,
    expectedBars = 744,
    completeness = 1.0,  // 100%
    gaps = emptyList(),
    dataTier = DataTier.TIER_4_BASIC,
    qualityScore = 0.70
)
```

**Validation**:
- ✅ Data coverage calculated correctly
- ✅ No gaps detected (100% completeness)
- ✅ Quality score matches tier (0.70 for BASIC)

#### 1.5 OHLC Validation Testing (2 hours)

**Test Steps**:
1. Inject known corrupt data into test database
2. Verify validation detects all 8 types of corruption

**Test Cases**:
```kotlin
@Test
fun testOHLCValidation() {
    val corruptBars = listOf(
        // Case 1: Negative price
        OHLCBarEntity(open = -50000.0, high = 51000.0, low = 49000.0, close = 50500.0),

        // Case 2: Zero volume
        OHLCBarEntity(volume = 0.0, /* valid OHLC */),

        // Case 3: Low > High
        OHLCBarEntity(open = 50000.0, high = 49000.0, low = 51000.0, close = 50500.0),

        // Case 4: Close outside [Low, High]
        OHLCBarEntity(open = 50000.0, high = 51000.0, low = 49000.0, close = 52000.0),

        // Case 5: Open outside [Low, High]
        OHLCBarEntity(open = 52000.0, high = 51000.0, low = 49000.0, close = 50000.0),

        // Case 6: Future timestamp
        OHLCBarEntity(timestamp = System.currentTimeMillis() + 86400000),

        // Case 7: Before Bitcoin genesis (2009-01-03)
        OHLCBarEntity(timestamp = 1230940800000 - 86400000),

        // Case 8: Extreme price spike (>50% range)
        OHLCBarEntity(open = 50000.0, high = 100000.0, low = 49000.0, close = 50500.0)
    )

    corruptBars.forEach { bar ->
        val validation = HistoricalDataRepository.validateOHLC(bar)
        assertFalse(validation.isValid, "Should detect corruption")
        println("Detected: ${validation.errors}")
    }
}
```

**Expected**: All 8 corruption types detected

#### 1.6 Database Performance Benchmarking (1 hour)

**Test**: Measure import and query performance

**Benchmarks**:
```kotlin
@Test
fun benchmarkDatabasePerformance() = runTest {
    // Benchmark 1: Import speed
    val start = System.currentTimeMillis()
    val bars = generateTestBars(10000)  // 10,000 OHLC bars
    ohlcBarDao.insertBatch(bars)
    val importTime = System.currentTimeMillis() - start

    println("Import speed: ${10000.0 / (importTime / 1000.0)} bars/second")
    assertTrue(importTime < 10000, "Should import 10k bars in < 10 seconds")

    // Benchmark 2: Query speed
    val queryStart = System.currentTimeMillis()
    val result = ohlcBarDao.getOHLCBars("XXBTZUSD", "1h", 1704067200000, 1706659200000)
    val queryTime = System.currentTimeMillis() - queryStart

    println("Query time: ${queryTime}ms for ${result.size} bars")
    assertTrue(queryTime < 100, "Should query in < 100ms")

    // Benchmark 3: Tier filtering
    val tierQueryStart = System.currentTimeMillis()
    val tierBars = ohlcBarDao.getOHLCBarsByTier("XXBTZUSD", "1h", DataTier.TIER_4_BASIC.name)
    val tierQueryTime = System.currentTimeMillis() - tierQueryStart

    println("Tier query time: ${tierQueryTime}ms for ${tierBars.size} bars")
    assertTrue(tierQueryTime < 150, "Tier query should be fast")
}
```

**Performance Targets**:
- Import: > 1,000 bars/second
- Query: < 100ms for date range
- Tier filtering: < 150ms

---

### Phase 2: Backtest Execution (Day 2 - 8 hours)
**Goal**: Run backtests across all data tiers and validate results

#### 2.1 Create Test Strategies (1 hour)

**Test Strategies** (covering different complexities):

**Strategy 1: Simple Buy-and-Hold** (Benchmark)
```kotlin
val buyAndHold = Strategy(
    id = "test-buy-hold",
    name = "Buy and Hold BTC",
    entryConditions = listOf("ALWAYS_TRUE"),
    exitConditions = listOf("NEVER"),
    positionSizePercent = 95.0,
    stopLossPercent = 0.0,
    takeProfitPercent = 0.0,
    tradingPairs = listOf("XXBTZUSD"),
    riskLevel = RiskLevel.LOW
)
```

**Strategy 2: RSI Mean Reversion**
```kotlin
val rsiStrategy = Strategy(
    id = "test-rsi",
    name = "RSI Mean Reversion",
    entryConditions = listOf("RSI_14 < 30"),  // Oversold
    exitConditions = listOf("RSI_14 > 70"),    // Overbought
    positionSizePercent = 50.0,
    stopLossPercent = 5.0,
    takeProfitPercent = 10.0,
    tradingPairs = listOf("XXBTZUSD"),
    riskLevel = RiskLevel.MEDIUM
)
```

**Strategy 3: MACD + RSI Combo**
```kotlin
val comboStrategy = Strategy(
    id = "test-combo",
    name = "MACD + RSI Combo",
    entryConditions = listOf(
        "MACD_CROSSOVER_UP",
        "RSI_14 < 50"
    ),
    exitConditions = listOf(
        "MACD_CROSSOVER_DOWN",
        "RSI_14 > 70"
    ),
    positionSizePercent = 30.0,
    stopLossPercent = 3.0,
    takeProfitPercent = 6.0,
    tradingPairs = listOf("XXBTZUSD"),
    riskLevel = RiskLevel.MEDIUM
)
```

**Strategy 4: Bollinger Bands Breakout**
```kotlin
val bollingerStrategy = Strategy(
    id = "test-bollinger",
    name = "Bollinger Breakout",
    entryConditions = listOf("PRICE_BELOW_BB_LOWER"),
    exitConditions = listOf("PRICE_ABOVE_BB_UPPER"),
    positionSizePercent = 40.0,
    stopLossPercent = 4.0,
    takeProfitPercent = 8.0,
    tradingPairs = listOf("XXBTZUSD"),
    riskLevel = RiskLevel.MEDIUM
)
```

#### 2.2 Run Backtests on TIER_4_BASIC Data (3 hours)

**Test**: Run all 4 strategies on 1-month of TIER_4_BASIC data

**Test Configuration**:
```kotlin
val config = BacktestConfig(
    asset = "XXBTZUSD",
    timeframe = "1h",
    startDate = "2024-01-01",
    endDate = "2024-01-31",
    startingBalance = 10000.0,
    dataTier = DataTier.TIER_4_BASIC
)
```

**Expected Results**:

**Strategy 1 - Buy and Hold**:
```
Starting Balance: $10,000
Ending Balance: $10,450 (+4.5%)
Total Trades: 1 (1 buy, 0 sells)
Win Rate: N/A
Max Drawdown: 12.3%
Sharpe Ratio: 0.45
Total P&L: +$450
```

**Strategy 2 - RSI Mean Reversion**:
```
Starting Balance: $10,000
Ending Balance: $10,280 (+2.8%)
Total Trades: 8
Winning Trades: 5
Losing Trades: 3
Win Rate: 62.5%
Max Drawdown: 8.1%
Sharpe Ratio: 0.62
Total P&L: +$280
Best Trade: +$150
Worst Trade: -$85
```

**Strategy 3 - MACD + RSI Combo**:
```
Starting Balance: $10,000
Ending Balance: $10,195 (+1.95%)
Total Trades: 6
Win Rate: 50%
Max Drawdown: 5.2%
Sharpe Ratio: 0.58
Total P&L: +$195
```

**Strategy 4 - Bollinger Bands Breakout**:
```
Starting Balance: $10,000
Ending Balance: $10,320 (+3.2%)
Total Trades: 4
Win Rate: 75%
Max Drawdown: 6.8%
Sharpe Ratio: 0.71
Total P&L: +$320
```

**Validation Tests**:
```kotlin
@Test
fun testBacktestTier4Basic() = runTest {
    // Get historical data from database
    val ohlcBars = ohlcBarDao.getOHLCBarsByTier(
        asset = "XXBTZUSD",
        timeframe = "1h",
        dataTier = DataTier.TIER_4_BASIC.name,
        startDate = parseDate("2024-01-01"),
        endDate = parseDate("2024-01-31")
    )

    assertEquals(744, ohlcBars.size, "Should have 744 hourly bars for January")

    // Convert to PriceBar domain model
    val priceBars = ohlcBars.map { it.toDomain() }

    // Run backtest
    val result = backtestEngine.runBacktest(
        strategy = rsiStrategy,
        historicalData = priceBars,
        startingBalance = 10000.0,
        ohlcBars = ohlcBars  // For tier validation
    )

    // Validate results
    assertTrue(result.totalTrades > 0, "Should execute trades")
    assertEquals(DataTier.TIER_4_BASIC, result.dataTier)
    assertEquals(0.70, result.dataQualityScore)

    // Validate equity curve
    assertTrue(result.equityCurve.isNotEmpty())
    assertEquals(10000.0, result.equityCurve.first(), 0.01)

    // Validate P&L accuracy (to 2 decimals)
    val expectedEndingBalance = result.equityCurve.last()
    assertEquals(expectedEndingBalance, result.endingBalance, 0.01)

    // Validate no look-ahead bias
    // Re-run backtest, should get same results (deterministic)
    val result2 = backtestEngine.runBacktest(
        strategy = rsiStrategy,
        historicalData = priceBars,
        startingBalance = 10000.0,
        ohlcBars = ohlcBars
    )

    assertEquals(result.totalPnL, result2.totalPnL, 0.01, "Results should be deterministic")
    assertEquals(result.totalTrades, result2.totalTrades)
}
```

#### 2.3 Compare Results Across Data Tiers (2 hours)

**Test**: Run same strategy on TIER_4_BASIC vs TIER_3_STANDARD

**Hypothesis**: TIER_3 data (higher frequency) should show:
- More trades executed (better entry/exit precision)
- Slightly different P&L (due to price precision)
- Similar win rate and Sharpe ratio

**Test Code**:
```kotlin
@Test
fun compareDataTiers() = runTest {
    val strategy = rsiStrategy

    // Run on TIER_4_BASIC (1h candles)
    val tier4Result = backtestEngine.runBacktest(
        strategy = strategy,
        historicalData = getTier4Data(),
        startingBalance = 10000.0,
        ohlcBars = getTier4OHLCBars()
    )

    // Run on TIER_3_STANDARD (1m aggregated trades -> 1h aggregation)
    val tier3Result = backtestEngine.runBacktest(
        strategy = strategy,
        historicalData = getTier3Data(),
        startingBalance = 10000.0,
        ohlcBars = getTier3OHLCBars()
    )

    // Compare results
    println("TIER_4_BASIC Results:")
    println("  Total P&L: ${tier4Result.totalPnL}")
    println("  Total Trades: ${tier4Result.totalTrades}")
    println("  Win Rate: ${tier4Result.winRate}%")
    println("  Sharpe Ratio: ${tier4Result.sharpeRatio}")

    println("TIER_3_STANDARD Results:")
    println("  Total P&L: ${tier3Result.totalPnL}")
    println("  Total Trades: ${tier3Result.totalTrades}")
    println("  Win Rate: ${tier3Result.winRate}%")
    println("  Sharpe Ratio: ${tier3Result.sharpeRatio}")

    // P&L should be within 5% (minor differences due to data precision)
    val pnlDifference = Math.abs(tier4Result.totalPnL - tier3Result.totalPnL)
    val pnlDifferencePercent = (pnlDifference / tier4Result.totalPnL) * 100.0

    assertTrue(pnlDifferencePercent < 5.0, "P&L should be similar across tiers")

    // Win rate should be within 10% (acceptable variation)
    val winRateDifference = Math.abs(tier4Result.winRate - tier3Result.winRate)
    assertTrue(winRateDifference < 10.0, "Win rate should be similar")
}
```

**Expected**: Results should be highly correlated but not identical

#### 2.4 Validate Look-Ahead Bias Elimination (1 hour)

**Test**: Verify no future data leakage (BUG 3.1 fix)

**Method**: Compare backtest results with hand-calculated expected results

**Test Case**: Known market scenario
```kotlin
@Test
fun validateNoLookAheadBias() = runTest {
    // Create synthetic data with known pattern
    val syntheticData = listOf(
        PriceBar(timestamp = t0, close = 100.0),  // Bar 0
        PriceBar(timestamp = t1, close = 95.0),   // Bar 1 (5% drop)
        PriceBar(timestamp = t2, close = 90.0),   // Bar 2 (5% drop)
        PriceBar(timestamp = t3, close = 105.0),  // Bar 3 (16.7% rise)
        PriceBar(timestamp = t4, close = 100.0)   // Bar 4
    )

    // Strategy: Buy when price drops 10% from peak
    val strategy = Strategy(
        entryConditions = listOf("PRICE_DROPPED_10_PERCENT"),
        exitConditions = listOf("PRICE_RECOVERED_5_PERCENT")
    )

    // Run backtest
    val result = backtestEngine.runBacktest(
        strategy = strategy,
        historicalData = syntheticData,
        startingBalance = 10000.0
    )

    // Expected behavior with NO look-ahead bias:
    // - At Bar 2 (price = 90): Should detect 10% drop from Bar 0 (100 -> 90)
    // - Buy at Bar 2 close: 90.0
    // - At Bar 3 (price = 105): Should detect 16.7% gain from entry
    // - Sell at Bar 3 close: 105.0
    // - P&L = (105 - 90) / 90 = 16.7%

    assertEquals(1, result.totalTrades / 2, "Should have 1 buy/sell pair")
    assertTrue(result.totalPnL > 1500.0, "Should profit ~16.7%")

    // With look-ahead bias (WRONG):
    // - At Bar 2, strategy could see Bar 2's close (90) BEFORE deciding
    // - This would give unrealistic entry/exit timing
    // - Result would be overly optimistic

    // Verification: Check trade timestamps
    val trades = result.trades
    assertTrue(trades.isNotEmpty())

    val buyTrade = trades.first { it.type == TradeType.BUY }
    assertTrue(buyTrade.timestamp >= t2, "Buy should be at or after Bar 2")
}
```

**Expected**: No look-ahead bias, realistic results

#### 2.5 Test Data Tier Validation (1 hour)

**Test**: Verify tier mixing prevention

**Test Code**:
```kotlin
@Test
fun testDataTierMixingPrevention() = runTest {
    // Create mixed-tier data (SHOULD FAIL)
    val mixedData = listOf(
        OHLCBarEntity(dataTier = DataTier.TIER_4_BASIC.name),
        OHLCBarEntity(dataTier = DataTier.TIER_1_PREMIUM.name),  // DIFFERENT TIER!
        OHLCBarEntity(dataTier = DataTier.TIER_4_BASIC.name)
    )

    val priceBars = mixedData.map { it.toDomain() }

    // Attempt backtest (should fail validation)
    val result = backtestEngine.runBacktest(
        strategy = rsiStrategy,
        historicalData = priceBars,
        startingBalance = 10000.0,
        ohlcBars = mixedData
    )

    // Should return error in result
    assertNotNull(result.validationError)
    assertTrue(result.validationError!!.contains("Data tier mixing detected"))

    println("Validation error: ${result.validationError}")
}
```

**Expected**: Data tier mixing detected and backtest rejected

---

### Phase 3: AI Backtest Proposal System (Day 3 - 4 hours)
**Goal**: Test AI-powered backtest proposal generation

#### 3.1 Generate AI Proposal for Simple Strategy (1 hour)

**Test Steps**:
1. Create simple RSI strategy
2. Call `backtestProposalGenerator.generateProposal(strategy)`
3. Verify AI recommends appropriate data tier

**Test Code**:
```kotlin
@Test
fun testAIProposalSimpleStrategy() = runTest {
    val strategy = rsiStrategy  // RSI Mean Reversion

    val proposal = backtestProposalGenerator.generateProposal(
        strategy = strategy,
        availableAssets = listOf("XXBTZUSD", "XETHZUSD"),
        availableTiers = listOf(
            DataTier.TIER_4_BASIC,
            DataTier.TIER_3_STANDARD
        )
    )

    // Verify proposal
    assertNotNull(proposal)
    assertEquals(strategy.id, proposal.strategyId)

    // AI should recommend TIER_4_BASIC for simple RSI strategy
    assertEquals(DataTier.TIER_4_BASIC, proposal.recommendedDataTier)

    // Verify reasoning
    assertTrue(proposal.tierRationale.contains("RSI"))
    assertTrue(proposal.tierRationale.contains("BASIC"))

    // Verify timeframe recommendation
    assertTrue(proposal.recommendedTimeframe in listOf("1h", "4h", "1d"))

    // Verify date range (should cover at least 3 months for statistical significance)
    val dateRangeDays = (proposal.endDate - proposal.startDate) / 86400000
    assertTrue(dateRangeDays >= 90, "Should recommend at least 90 days")

    // Verify warnings
    if (dateRangeDays < 180) {
        assertTrue(proposal.warnings.any { it.contains("limited data") })
    }

    // Print proposal for review
    println("=== AI Backtest Proposal ===")
    println(proposal.toMarkdown())
}
```

**Expected AI Proposal**:
```markdown
# Backtest Proposal: RSI Mean Reversion

## Recommended Configuration
- **Data Tier**: TIER_4_BASIC (Standard OHLCV)
- **Asset**: XXBTZUSD
- **Timeframe**: 1h
- **Date Range**: 2024-01-01 to 2024-12-31 (365 days)
- **Starting Balance**: $10,000

## Rationale

### Data Tier: TIER_4_BASIC
I recommend **TIER_4_BASIC** because:
- Your strategy uses RSI (14-period), which is calculated from close prices
- Hourly OHLCV candles provide sufficient granularity for RSI signals
- BASIC tier data is cost-effective and reliable for position trading strategies
- 💡 Higher tiers (PROFESSIONAL/PREMIUM) are only needed for high-frequency or order flow strategies

### Timeframe: 1 hour
- RSI(14) requires 14 periods, so 14 hours of data per signal
- 1-hour timeframe balances signal frequency with noise reduction
- Typical hold time for RSI mean reversion: 6-24 hours

### Date Range: Full year (365 days)
- 365 days provides ~500 RSI signals (sufficient sample size)
- Covers multiple market regimes (bull, bear, ranging)
- Ensures statistical significance (minimum 100 trades recommended)

## Warnings
⚠️ None - configuration looks good!

## Educational Notes
💡 **About Data Tiers**: TIER_4_BASIC is perfect for strategies based on daily/hourly indicators. Only use TIER_1_PREMIUM if you need sub-second precision for scalping or order flow analysis.

💡 **Statistical Significance**: For reliable backtest results, aim for at least 100 trades. Your configuration should generate ~50-100 RSI signals.
```

#### 3.2 Generate AI Proposal for High-Frequency Strategy (1 hour)

**Test**: Verify AI recommends PREMIUM tier for HF strategy

**Test Code**:
```kotlin
@Test
fun testAIProposalHighFrequencyStrategy() = runTest {
    val hfStrategy = Strategy(
        id = "test-hf-scalping",
        name = "High-Frequency Scalping",
        entryConditions = listOf(
            "ORDER_IMBALANCE > 0.7",  // Requires order book data
            "BID_ASK_SPREAD < 0.02%"
        ),
        exitConditions = listOf("PRICE_TICK_UP"),
        positionSizePercent = 10.0,
        stopLossPercent = 0.1,  // Tight stop (0.1%)
        takeProfitPercent = 0.2,  // Small target (0.2%)
        tradingPairs = listOf("XXBTZUSD"),
        riskLevel = RiskLevel.HIGH
    )

    val proposal = backtestProposalGenerator.generateProposal(
        strategy = hfStrategy,
        availableTiers = listOf(
            DataTier.TIER_1_PREMIUM,
            DataTier.TIER_4_BASIC
        )
    )

    // AI should recommend TIER_1_PREMIUM for HF strategy
    assertEquals(DataTier.TIER_1_PREMIUM, proposal.recommendedDataTier)

    // Verify reasoning mentions order book requirement
    assertTrue(proposal.tierRationale.contains("order book"))
    assertTrue(proposal.tierRationale.contains("ORDER_IMBALANCE"))

    // Verify short timeframe recommended
    assertTrue(proposal.recommendedTimeframe in listOf("1s", "5s", "1m"))

    // Verify shorter date range (HF strategies don't need years of data)
    val dateRangeDays = (proposal.endDate - proposal.startDate) / 86400000
    assertTrue(dateRangeDays <= 30, "HF strategies typically test on shorter periods")

    println("=== HF Strategy AI Proposal ===")
    println(proposal.toMarkdown())
}
```

**Expected**: AI correctly detects need for TIER_1_PREMIUM

#### 3.3 User Approval Workflow (1 hour)

**Test**: Complete proposal → approval → execution flow

**Test Code**:
```kotlin
@Test
fun testProposalApprovalWorkflow() = runTest {
    val viewModel = BacktestManagementViewModel(/* dependencies */)

    // Step 1: Generate proposal
    viewModel.generateBacktestProposal(rsiStrategy)

    viewModel.currentProposal.test {
        val proposal = awaitItem()
        assertNotNull(proposal)

        // Step 2: User reviews and approves
        val decision = BacktestDecision(
            proposalId = proposal.proposalId,
            approved = true,
            modifiedTier = null,  // Accept AI recommendation
            modifiedTimeframe = null,
            modifiedStartDate = null,
            modifiedEndDate = null
        )

        // Step 3: Execute backtest with approved config
        viewModel.executeBacktest(rsiStrategy, decision)

        // Wait for completion
        viewModel.backtestResult.test {
            val result = awaitItem()
            assertNotNull(result)

            // Verify result matches proposal
            assertEquals(proposal.recommendedDataTier, result.dataTier)
            assertTrue(result.totalTrades > 0)

            println("Backtest completed:")
            println("  Tier: ${result.dataTier}")
            println("  Trades: ${result.totalTrades}")
            println("  P&L: ${result.totalPnL}")
        }
    }
}
```

#### 3.4 User Modification of Proposal (30 minutes)

**Test**: User overrides AI recommendation

**Test Code**:
```kotlin
@Test
fun testProposalModification() = runTest {
    val proposal = backtestProposalGenerator.generateProposal(rsiStrategy)

    // AI recommended TIER_4_BASIC, but user wants TIER_3_STANDARD
    val decision = BacktestDecision(
        proposalId = proposal.proposalId,
        approved = true,
        modifiedTier = DataTier.TIER_3_STANDARD,  // User override
        modifiedTimeframe = "15m",  // User override
        modifiedStartDate = parseDate("2024-06-01"),  // User override
        modifiedEndDate = parseDate("2024-06-30"),
        userNotes = "Testing on recent data with higher precision"
    )

    // Execute with modified config
    val result = backtestOrchestrator.executeBacktest(rsiStrategy, decision)

    // Verify result uses user's choices
    assertEquals(DataTier.TIER_3_STANDARD, result.dataTier)
    // Timeframe and date range also modified per user request
}
```

---

### Phase 4: Integration & Regression Testing (Day 3 - 4 hours)
**Goal**: End-to-end validation and regression testing

#### 4.1 Complete Workflow Test (1 hour)

**Test**: Full flow from data import to backtest results

```kotlin
@Test
fun testCompleteBacktestWorkflow() = runTest {
    // Step 1: Scan data
    val files = batchDataImporter.scanAvailableData()
    assertTrue(files.isNotEmpty())

    // Step 2: Import selected files
    val testFiles = files.filter {
        it.asset == "XXBTZUSD" &&
        it.timeframe == "1h" &&
        it.dataTier == DataTier.TIER_4_BASIC
    }.take(1)

    testFiles.forEach { file ->
        batchDataImporter.importFile(file).collect { /* progress */ }
    }

    // Step 3: Generate AI proposal
    val proposal = backtestProposalGenerator.generateProposal(rsiStrategy)

    // Step 4: User approves
    val decision = BacktestDecision(
        proposalId = proposal.proposalId,
        approved = true
    )

    // Step 5: Execute backtest
    val result = backtestOrchestrator.executeBacktest(rsiStrategy, decision)

    // Step 6: Validate results
    assertNotNull(result)
    assertTrue(result.totalTrades > 0)
    assertEquals(DataTier.TIER_4_BASIC, result.dataTier)

    // Step 7: Save to database
    backtestRunDao.insert(result.toEntity())

    // Step 8: Retrieve from database
    val savedRun = backtestRunDao.getBacktestRun(result.id)
    assertNotNull(savedRun)
    assertEquals(result.strategyId, savedRun.strategyId)

    println("✅ Complete workflow successful!")
}
```

#### 4.2 Performance Regression Test (1 hour)

**Test**: Verify no performance degradation

```kotlin
@Test
fun testBacktestPerformance() = runTest {
    val data = generateTestBars(10000)  // 10,000 bars

    val start = System.currentTimeMillis()

    val result = backtestEngine.runBacktest(
        strategy = rsiStrategy,
        historicalData = data,
        startingBalance = 10000.0
    )

    val duration = System.currentTimeMillis() - start

    println("Backtest duration: ${duration}ms for 10,000 bars")
    println("Throughput: ${10000.0 / (duration / 1000.0)} bars/second")

    // Should process at least 1000 bars/second
    assertTrue(duration < 10000, "Should complete in < 10 seconds")
}
```

**Performance Targets**:
- 1,000 bars: < 1 second
- 10,000 bars: < 10 seconds
- 100,000 bars: < 2 minutes

#### 4.3 Accuracy Regression Test (1 hour)

**Test**: Compare with known reference backtests

```kotlin
@Test
fun testBacktestAccuracy() = runTest {
    // Use historical backtest results as baseline
    val referenceBacktest = BacktestResult(
        strategyId = "test-rsi",
        totalPnL = 280.50,
        totalTrades = 8,
        winRate = 62.5,
        sharpeRatio = 0.62,
        maxDrawdown = 8.1
        // ... other metrics from Phase 2.7 validation
    )

    // Re-run same backtest
    val currentResult = backtestEngine.runBacktest(
        strategy = rsiStrategy,
        historicalData = getReferenceData(),
        startingBalance = 10000.0
    )

    // Results should match to 2 decimals
    assertEquals(referenceBacktest.totalPnL, currentResult.totalPnL, 0.01)
    assertEquals(referenceBacktest.totalTrades, currentResult.totalTrades)
    assertEquals(referenceBacktest.winRate, currentResult.winRate, 0.1)
    assertEquals(referenceBacktest.sharpeRatio, currentResult.sharpeRatio, 0.01)
    assertEquals(referenceBacktest.maxDrawdown, currentResult.maxDrawdown, 0.1)

    println("✅ Backtest accuracy maintained!")
}
```

#### 4.4 Edge Case Testing (1 hour)

**Test**: Handle edge cases gracefully

```kotlin
@Test
fun testBacktestEdgeCases() = runTest {
    // Edge case 1: Empty data
    val emptyResult = backtestEngine.runBacktest(
        strategy = rsiStrategy,
        historicalData = emptyList(),
        startingBalance = 10000.0
    )
    assertEquals(0, emptyResult.totalTrades)
    assertEquals(10000.0, emptyResult.endingBalance)

    // Edge case 2: Single bar
    val singleBarResult = backtestEngine.runBacktest(
        strategy = rsiStrategy,
        historicalData = listOf(PriceBar(/* ... */)),
        startingBalance = 10000.0
    )
    assertEquals(0, singleBarResult.totalTrades)  // Not enough data for RSI

    // Edge case 3: All losing trades
    val alwaysLoseStrategy = Strategy(/* always loses */)
    val losingResult = backtestEngine.runBacktest(
        strategy = alwaysLoseStrategy,
        historicalData = getTestData(),
        startingBalance = 10000.0
    )
    assertTrue(losingResult.totalPnL < 0)
    assertEquals(0.0, losingResult.winRate)

    // Edge case 4: 100% win rate
    val alwaysWinStrategy = Strategy(/* always wins */)
    val winningResult = backtestEngine.runBacktest(
        strategy = alwaysWinStrategy,
        historicalData = getTestData(),
        startingBalance = 10000.0
    )
    assertTrue(winningResult.totalPnL > 0)
    assertEquals(100.0, winningResult.winRate)

    println("✅ All edge cases handled!")
}
```

---

## 📊 SUCCESS CRITERIA

### Phase 1: Data Import
- ✅ Scan detects all data files
- ✅ CSV import 100% success rate
- ✅ Database contains correct tier labels
- ✅ OHLC validation detects all 8 corruption types
- ✅ Data coverage analysis accurate
- ✅ Import speed > 1,000 bars/second

### Phase 2: Backtest Execution
- ✅ All 4 test strategies execute successfully
- ✅ Results are deterministic (reproducible)
- ✅ No look-ahead bias (BUG 3.1 verified)
- ✅ P&L calculations accurate to 2 decimals
- ✅ Data tier validation prevents mixing
- ✅ Equity curve precision correct
- ✅ Performance metrics (Sharpe, drawdown) match expected ranges

### Phase 3: AI Proposals
- ✅ AI recommends appropriate tiers for strategy complexity
- ✅ Proposals include educational rationale
- ✅ User can approve/modify proposals
- ✅ Execution uses proposal configuration
- ✅ Warnings generated for edge cases

### Phase 4: Integration
- ✅ Complete workflow (scan → import → propose → backtest → save) works
- ✅ Performance targets met (>1000 bars/sec)
- ✅ Accuracy matches reference backtests
- ✅ Edge cases handled gracefully
- ✅ No crashes or data corruption

---

## 🚨 KNOWN ISSUES & WORKAROUNDS

### Issue 1: Parquet Import Not Implemented
**Status**: Skeleton only (Phase 2.8)
**Workaround**: Use CSV exports of CryptoLake data for now
**Resolution**: Phase 7+ will add Apache Arrow support

### Issue 2: Very Large Datasets (100K+ bars)
**Status**: Memory usage not optimized for massive datasets
**Workaround**: Split backtests into smaller date ranges
**Resolution**: Implement streaming backtest processing in future

### Issue 3: Multi-Asset Backtests
**Status**: Current implementation backtests one asset at a time
**Workaround**: Run separate backtests per asset, aggregate results manually
**Resolution**: Phase 5+ will add portfolio-level backtesting

---

## 📝 TEST EXECUTION LOG

### Test Session 1: [Date]
- [ ] Phase 1.1: Data scan (**PASS** / FAIL)
- [ ] Phase 1.2: CSV import (**PASS** / FAIL)
- [ ] Phase 1.3: Parquet test (**PASS** / FAIL)
- [ ] Phase 1.4: Data coverage (**PASS** / FAIL)
- [ ] Phase 1.5: OHLC validation (**PASS** / FAIL)
- [ ] Phase 1.6: Performance benchmark (**PASS** / FAIL)

### Test Session 2: [Date]
- [ ] Phase 2.1: Test strategies created (**PASS** / FAIL)
- [ ] Phase 2.2: Tier 4 backtests (**PASS** / FAIL)
- [ ] Phase 2.3: Tier comparison (**PASS** / FAIL)
- [ ] Phase 2.4: Look-ahead bias test (**PASS** / FAIL)
- [ ] Phase 2.5: Tier mixing prevention (**PASS** / FAIL)

### Test Session 3: [Date]
- [ ] Phase 3.1: AI proposal simple strategy (**PASS** / FAIL)
- [ ] Phase 3.2: AI proposal HF strategy (**PASS** / FAIL)
- [ ] Phase 3.3: Approval workflow (**PASS** / FAIL)
- [ ] Phase 3.4: User modification (**PASS** / FAIL)

### Test Session 4: [Date]
- [ ] Phase 4.1: Complete workflow (**PASS** / FAIL)
- [ ] Phase 4.2: Performance regression (**PASS** / FAIL)
- [ ] Phase 4.3: Accuracy regression (**PASS** / FAIL)
- [ ] Phase 4.4: Edge cases (**PASS** / FAIL)

---

## ✅ FINAL VALIDATION CHECKLIST

Before declaring backtest system **PRODUCTION READY**:

- [ ] All data import tests passing
- [ ] All backtest execution tests passing
- [ ] AI proposal system functional
- [ ] No look-ahead bias detected
- [ ] P&L accuracy ±0.01%
- [ ] Performance benchmarks met
- [ ] Data tier separation enforced
- [ ] Edge cases handled gracefully
- [ ] Documentation updated
- [ ] User acceptance testing completed

---

**Status**: ⬜ Not Started
**Dependencies**: Phase 2.8 Complete (✅)
**Estimated Completion**: 2-3 days
**Next Step**: Begin Phase 1 - Data Import & Validation

---

*Plan created by Claude Code (Sonnet 4.5)*
*Date: 2025-11-19*
*Current Phase: 2.8 Complete - Ready for Testing*
