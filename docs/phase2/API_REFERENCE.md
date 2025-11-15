# API Reference - Phase 2 Indicators

**Version**: 1.0
**Date**: November 14, 2024
**Purpose**: Complete API documentation for Phase 2 indicator system

---

## Table of Contents

1. [MarketDataAdapter](#marketdataadapter)
2. [PriceHistoryManager](#pricehistorymanager)
3. [StrategyEvaluatorV2](#strategyevaluatorv2)
4. [IndicatorCache](#indicatorcache)
5. [Calculator APIs](#calculator-apis)
   - [RsiCalculator](#rsicalculator)
   - [MacdCalculator](#macdcalculator)
   - [BollingerBandsCalculator](#bollingerbandscalculator)
   - [AtrCalculator](#atrcalculator)
   - [StochasticCalculator](#stochasticcalculator)
   - [VolumeIndicatorCalculator](#volumeindicatorcalculator)
   - [MovingAverageCalculator](#movingaveragecalculator)

---

## MarketDataAdapter

**Package**: `com.cryptotrader.domain.trading`

**Purpose**: Converts MarketTicker data to Candle (OHLCV) format for indicator calculations.

### Methods

#### toCandle

Converts a MarketTicker to a Candle.

```kotlin
fun toCandle(
    ticker: MarketTicker,
    timestamp: Long = ticker.timestamp
): Candle
```

**Parameters**:
- `ticker`: The market ticker data to convert
- `timestamp`: Optional timestamp override (defaults to ticker's timestamp)

**Returns**: A Candle object with OHLCV data

**Example**:
```kotlin
val adapter = MarketDataAdapter()
val ticker = MarketTicker(
    pair = "BTC/USD",
    last = 45000.0,
    high24h = 46000.0,
    low24h = 44000.0,
    volume24h = 1234567.0,
    change24h = 500.0,
    changePercent24h = 1.12,
    timestamp = System.currentTimeMillis()
)

val candle = adapter.toCandle(ticker)
// Result: Candle(
//   timestamp = ticker.timestamp,
//   open = 44500.0,
//   high = 46000.0,
//   low = 44000.0,
//   close = 45000.0,
//   volume = 1234567.0
// )
```

#### toCandleList

Converts lists of prices and timestamps to Candles.

```kotlin
fun toCandleList(
    prices: List<Double>,
    timestamps: List<Long>
): List<Candle>
```

**Parameters**:
- `prices`: List of closing prices
- `timestamps`: List of corresponding timestamps (must match prices length)

**Returns**: List of Candle objects

**Throws**: `IllegalArgumentException` if lists have different sizes

**Example**:
```kotlin
val prices = listOf(100.0, 101.0, 102.0, 103.0)
val timestamps = listOf(1000L, 2000L, 3000L, 4000L)

val candles = adapter.toCandleList(prices, timestamps)
// Result: 4 candles where O=H=L=C for each
```

#### toCandleListWithInterval

Converts prices to candles with auto-generated timestamps.

```kotlin
fun toCandleListWithInterval(
    prices: List<Double>,
    intervalMillis: Long,
    newestFirst: Boolean = false
): List<Candle>
```

**Parameters**:
- `prices`: List of closing prices
- `intervalMillis`: Time interval between candles in milliseconds
- `newestFirst`: Whether prices are ordered newest-first (default: false)

**Returns**: List of Candle objects with generated timestamps

**Example**:
```kotlin
val prices = listOf(100.0, 101.0, 102.0, 103.0)
val candles = adapter.toCandleListWithInterval(
    prices = prices,
    intervalMillis = 5 * 60 * 1000,  // 5-minute intervals
    newestFirst = false
)
// Generates timestamps: now - 15min, now - 10min, now - 5min, now
```

#### createSyntheticCandle

Creates a single candle from basic price data.

```kotlin
fun createSyntheticCandle(
    price: Double,
    timestamp: Long,
    volume: Double = 0.0
): Candle
```

**Parameters**:
- `price`: The price (used for O, H, L, C)
- `timestamp`: Candle timestamp
- `volume`: Optional volume (default: 0.0)

**Returns**: A Candle where O=H=L=C=price

---

## PriceHistoryManager

**Package**: `com.cryptotrader.domain.trading`

**Purpose**: Thread-safe storage and management of candle history for trading pairs.

### Methods

#### updateHistory

Adds a new candle to the history for a trading pair.

```kotlin
fun updateHistory(pair: String, candle: Candle)
```

**Parameters**:
- `pair`: Trading pair identifier (e.g., "BTC/USD")
- `candle`: Candle to add to history

**Side Effects**:
- Adds candle to in-memory storage
- Trims history to max 200 candles (removes oldest)
- Persists to IndicatorCache

**Example**:
```kotlin
val manager = PriceHistoryManager(indicatorCache)
val candle = Candle(
    timestamp = System.currentTimeMillis(),
    open = 45000.0,
    high = 45500.0,
    low = 44500.0,
    close = 45200.0,
    volume = 12345.0
)

manager.updateHistory("BTC/USD", candle)
```

#### getHistory

Retrieves the complete price history for a pair.

```kotlin
fun getHistory(pair: String): List<Candle>
```

**Parameters**:
- `pair`: Trading pair identifier

**Returns**: List of candles ordered from oldest to newest (immutable copy)

**Example**:
```kotlin
val history = manager.getHistory("BTC/USD")
println("BTC/USD history: ${history.size} candles")
```

#### getHistory (with count)

Retrieves the last N candles for a pair.

```kotlin
fun getHistory(pair: String, count: Int): List<Candle>
```

**Parameters**:
- `pair`: Trading pair identifier
- `count`: Number of most recent candles to retrieve

**Returns**: List of the last N candles

**Throws**: `IllegalArgumentException` if count <= 0

**Example**:
```kotlin
val recentCandles = manager.getHistory("BTC/USD", 50)
// Returns only the 50 most recent candles
```

#### updateHistoryBatch

Adds multiple candles at once.

```kotlin
fun updateHistoryBatch(pair: String, candles: List<Candle>)
```

**Parameters**:
- `pair`: Trading pair identifier
- `candles`: List of candles to add (ordered oldest to newest)

**Example**:
```kotlin
val historicalCandles = loadHistoricalData("BTC/USD")
manager.updateHistoryBatch("BTC/USD", historicalCandles)
```

#### setHistory

Replaces the entire history for a pair.

```kotlin
fun setHistory(pair: String, candles: List<Candle>)
```

**Parameters**:
- `pair`: Trading pair identifier
- `candles`: Complete list of candles to set as history

**Example**:
```kotlin
val newHistory = fetchFromAPI("BTC/USD")
manager.setHistory("BTC/USD", newHistory)
```

#### clearHistory

Clears history for a specific pair.

```kotlin
fun clearHistory(pair: String)
```

#### clearAllHistory

Clears history for all pairs.

```kotlin
fun clearAllHistory()
```

#### getHistorySize

Gets the number of candles stored for a pair.

```kotlin
fun getHistorySize(pair: String): Int
```

#### hasHistory

Checks if any history exists for a pair.

```kotlin
fun hasHistory(pair: String): Boolean
```

#### getLatestCandle

Gets the most recent candle for a pair.

```kotlin
fun getLatestCandle(pair: String): Candle?
```

#### getStorageStats

Gets statistics about stored history.

```kotlin
fun getStorageStats(): Map<String, Int>
```

**Returns**: Map of pair to candle count

**Example**:
```kotlin
val stats = manager.getStorageStats()
// Result: {"BTC/USD" to 150, "ETH/USD" to 200, ...}
```

---

## StrategyEvaluatorV2

**Package**: `com.cryptotrader.domain.trading`

**Purpose**: Evaluates trading strategy entry and exit conditions using advanced calculators.

### Methods

#### updatePriceHistory

Updates price history for a trading pair.

```kotlin
fun updatePriceHistory(pair: String, marketData: MarketTicker)
```

**Parameters**:
- `pair`: Trading pair identifier
- `marketData`: Current market ticker data

**Side Effects**:
- Converts MarketTicker to Candle
- Updates PriceHistoryManager
- Logs history size if `LOG_CACHE_PERFORMANCE` is enabled

#### evaluateEntryConditions

Evaluates entry conditions for a strategy.

```kotlin
fun evaluateEntryConditions(
    strategy: Strategy,
    marketData: MarketTicker
): Boolean
```

**Parameters**:
- `strategy`: Strategy containing entry conditions
- `marketData`: Current market ticker data

**Returns**: `true` if ALL entry conditions are met, `false` otherwise

**Example**:
```kotlin
val strategy = Strategy(
    name = "RSI Oversold + MACD Bullish",
    entryConditions = listOf(
        "RSI < 30",
        "MACD_crossover",
        "Volume > average"
    ),
    exitConditions = listOf(...)
)

val shouldEnter = evaluator.evaluateEntryConditions(strategy, marketData)
if (shouldEnter) {
    println("All entry conditions met - entering trade")
}
```

#### evaluateExitConditions

Evaluates exit conditions for a strategy.

```kotlin
fun evaluateExitConditions(
    strategy: Strategy,
    marketData: MarketTicker
): Boolean
```

**Parameters**:
- `strategy`: Strategy containing exit conditions
- `marketData`: Current market ticker data

**Returns**: `true` if ANY exit condition is met, `false` otherwise

**Example**:
```kotlin
val strategy = Strategy(
    name = "My Strategy",
    entryConditions = listOf(...),
    exitConditions = listOf(
        "RSI > 70",
        "MACD negative",
        "Price > Bollinger_Upper"
    )
)

val shouldExit = evaluator.evaluateExitConditions(strategy, marketData)
if (shouldExit) {
    println("Exit condition triggered - closing position")
}
```

### Supported Condition Patterns

#### RSI Conditions
- `"RSI < 30"` - RSI below threshold
- `"RSI > 70"` - RSI above threshold
- `"RSI oversold"` - RSI < 30
- `"RSI overbought"` - RSI > 70

#### Moving Average Conditions
- `"SMA_20 > SMA_50"` - Short MA above long MA
- `"EMA_12 > EMA_26"` - Fast EMA above slow EMA
- `"Price > SMA_20"` - Price above MA
- `"SMA_50 cross SMA_200"` - Golden cross detection

#### MACD Conditions
- `"MACD_crossover"` - Bullish MACD crossover
- `"MACD positive"` - MACD histogram > 0
- `"MACD negative"` - MACD histogram < 0
- `"MACD > 0"` - MACD line above signal line

#### Bollinger Bands Conditions
- `"Price > Bollinger_Upper"` - Price above upper band
- `"Price < Bollinger_Lower"` - Price below lower band
- `"Bollinger_outside"` - Price outside bands

#### Volume Conditions
- `"Volume > average"` - High volume
- `"Volume < average"` - Low volume

#### ATR Conditions
- `"ATR > 2.0"` - High volatility
- `"ATR < 1.0"` - Low volatility

#### Price Position Conditions
- `"Price near high"` - Price >= high24h * 0.98
- `"Price near low"` - Price <= low24h * 1.02
- `"Price > 45000"` - Price above threshold

#### Momentum Conditions
- `"Momentum > 2"` - 24h change > 2%
- `"Change positive"` - Positive momentum

#### History Status

```kotlin
fun getPriceHistoryStatus(pair: String): Pair<Int, Int>
fun hasEnoughHistory(pair: String): Boolean
fun getAllPriceHistoryStatus(): Map<String, Pair<Int, Int>>
fun clearHistory()
fun clearHistory(pair: String)
```

---

## IndicatorCache

**Package**: `com.cryptotrader.domain.indicators.cache`

**Purpose**: LRU cache for indicator calculation results.

### Methods

#### generateKey

Generates a cache key from indicator parameters.

```kotlin
fun generateKey(
    indicatorName: String,
    parameters: Map<String, Any>,
    dataHash: Int
): String
```

**Parameters**:
- `indicatorName`: Name of the indicator (e.g., "RSI", "MACD")
- `parameters`: Map of parameter names to values
- `dataHash`: Hash of the input data

**Returns**: Cache key string

**Example**:
```kotlin
val key = cache.generateKey(
    indicatorName = "RSI",
    parameters = mapOf("period" to 14),
    dataHash = prices.hashCode()
)
// Result: "RSI|period=14|data_hash=123456789"
```

#### hashData

Generates a hash for a list of doubles.

```kotlin
fun hashData(data: List<Double>): Int
fun hashData(vararg dataLists: List<Double>): Int
```

**Example**:
```kotlin
val hash1 = cache.hashData(closes)
val hash2 = cache.hashData(highs, lows, closes)
```

#### get

Retrieves a cached value.

```kotlin
fun <T> get(key: String): T?
```

**Returns**: Cached value or null if not found

#### put

Stores a value in the cache.

```kotlin
fun put(key: String, value: Any)
```

#### contains

Checks if a key exists in cache.

```kotlin
fun contains(key: String): Boolean
```

#### clear

Clears all cached values.

```kotlin
fun clear()
```

#### remove

Removes a specific entry.

```kotlin
fun remove(key: String)
```

#### size

Gets the current number of cached entries.

```kotlin
fun size(): Int
```

### Extension Function

#### getOrPut

Gets a cached value or computes it.

```kotlin
inline fun <reified T> IndicatorCache.getOrPut(
    key: String,
    compute: () -> T
): T
```

**Example**:
```kotlin
val rsiValues = cache.getOrPut(key) {
    // This is only called if cache miss
    expensiveRsiCalculation(closes, period)
}
```

---

## Calculator APIs

### RsiCalculator

**Package**: `com.cryptotrader.domain.indicators.rsi`

**Purpose**: Calculates RSI (Relative Strength Index) values.

#### calculate

```kotlin
fun calculate(
    closes: List<Double>,
    period: Int = 14
): List<Double?>
```

**Parameters**:
- `closes`: List of closing prices
- `period`: Lookback period (default: 14)

**Returns**: List of RSI values (0-100), null for insufficient data

**Formula**: `RSI = 100 - (100 / (1 + RS))` where `RS = Average Gain / Average Loss`

**Example**:
```kotlin
val closes = listOf(44.0, 44.34, 44.09, 43.61, 44.33, /* ... more prices */)
val rsiValues = rsiCalculator.calculate(closes, period = 14)
val currentRsi = rsiValues.lastOrNull()

if (currentRsi != null && currentRsi < 30) {
    println("RSI is oversold: $currentRsi")
}
```

---

### MacdCalculator

**Package**: `com.cryptotrader.domain.indicators.macd`

**Purpose**: Calculates MACD (Moving Average Convergence Divergence).

#### calculate

```kotlin
fun calculate(
    closes: List<Double>,
    fastPeriod: Int = 12,
    slowPeriod: Int = 26,
    signalPeriod: Int = 9
): MacdResult
```

**Parameters**:
- `closes`: List of closing prices
- `fastPeriod`: Fast EMA period (default: 12)
- `slowPeriod`: Slow EMA period (default: 26)
- `signalPeriod`: Signal line EMA period (default: 9)

**Returns**: `MacdResult` object containing:
- `macdLine`: Fast EMA - Slow EMA
- `signalLine`: EMA of MACD line
- `histogram`: MACD line - Signal line

**Example**:
```kotlin
val closes = listOf(/* price data */)
val result = macdCalculator.calculate(closes)

val macdLine = result.macdLine.lastOrNull()
val signalLine = result.signalLine.lastOrNull()
val histogram = result.histogram.lastOrNull()

if (macdLine != null && signalLine != null && macdLine > signalLine) {
    println("Bullish MACD: line=$macdLine, signal=$signalLine")
}
```

---

### BollingerBandsCalculator

**Package**: `com.cryptotrader.domain.indicators.bollingerbands`

**Purpose**: Calculates Bollinger Bands (price bands based on standard deviation).

#### calculate

```kotlin
fun calculate(
    closes: List<Double>,
    period: Int = 20,
    stdDev: Double = 2.0
): BollingerBandsResult
```

**Parameters**:
- `closes`: List of closing prices
- `period`: MA period (default: 20)
- `stdDev`: Standard deviation multiplier (default: 2.0)

**Returns**: `BollingerBandsResult` object containing:
- `upperBand`: Middle band + (stdDev * std deviation)
- `middleBand`: Simple moving average
- `lowerBand`: Middle band - (stdDev * std deviation)

**Example**:
```kotlin
val result = bollingerCalculator.calculate(closes, period = 20, stdDev = 2.0)

val upper = result.upperBand.lastOrNull()
val middle = result.middleBand.lastOrNull()
val lower = result.lowerBand.lastOrNull()
val currentPrice = closes.last()

if (upper != null && currentPrice > upper) {
    println("Price broke above upper Bollinger Band")
}
```

---

### AtrCalculator

**Package**: `com.cryptotrader.domain.indicators.atr`

**Purpose**: Calculates ATR (Average True Range) for volatility measurement.

#### calculate

```kotlin
fun calculate(
    highs: List<Double>,
    lows: List<Double>,
    closes: List<Double>,
    period: Int = 14
): List<Double?>
```

**Parameters**:
- `highs`: List of high prices
- `lows`: List of low prices
- `closes`: List of closing prices
- `period`: Lookback period (default: 14)

**Returns**: List of ATR values, null for insufficient data

**Formula**: Smoothed average of True Range, where TR = max(H-L, |H-C_prev|, |L-C_prev|)

**Example**:
```kotlin
val candles = priceHistoryManager.getHistory("BTC/USD")
val highs = candles.map { it.high }
val lows = candles.map { it.low }
val closes = candles.map { it.close }

val atrValues = atrCalculator.calculate(highs, lows, closes, period = 14)
val currentAtr = atrValues.lastOrNull()

if (currentAtr != null && currentAtr > 1000.0) {
    println("High volatility detected: ATR = $currentAtr")
}
```

---

### StochasticCalculator

**Package**: `com.cryptotrader.domain.indicators.stochastic`

**Purpose**: Calculates Stochastic Oscillator (%K and %D).

#### calculate

```kotlin
fun calculate(
    highs: List<Double>,
    lows: List<Double>,
    closes: List<Double>,
    kPeriod: Int = 14,
    dPeriod: Int = 3,
    smooth: Int = 3
): StochasticResult
```

**Parameters**:
- `highs`: List of high prices
- `lows`: List of low prices
- `closes`: List of closing prices
- `kPeriod`: %K period (default: 14)
- `dPeriod`: %D period (default: 3)
- `smooth`: Smoothing period (default: 3)

**Returns**: `StochasticResult` object containing:
- `k`: %K line values (fast stochastic)
- `d`: %D line values (slow stochastic, SMA of %K)

**Example**:
```kotlin
val candles = priceHistoryManager.getHistory("ETH/USD")
val result = stochasticCalculator.calculate(
    highs = candles.map { it.high },
    lows = candles.map { it.low },
    closes = candles.map { it.close }
)

val k = result.k.lastOrNull()
val d = result.d.lastOrNull()

if (k != null && d != null && k < 20 && d < 20) {
    println("Stochastic oversold: K=$k, D=$d")
}
```

---

### VolumeIndicatorCalculator

**Package**: `com.cryptotrader.domain.indicators.volume`

**Purpose**: Calculates volume-based indicators.

#### calculateAverageVolume

```kotlin
fun calculateAverageVolume(
    volumes: List<Double>,
    period: Int = 20
): List<Double?>
```

**Parameters**:
- `volumes`: List of volume values
- `period`: Lookback period (default: 20)

**Returns**: List of average volume values

**Example**:
```kotlin
val candles = priceHistoryManager.getHistory("BTC/USD")
val volumes = candles.map { it.volume }

val avgVolumes = volumeCalculator.calculateAverageVolume(volumes, period = 20)
val currentVolume = volumes.last()
val avgVolume = avgVolumes.lastOrNull()

if (avgVolume != null && currentVolume > avgVolume * 2.0) {
    println("Volume spike detected: ${currentVolume / avgVolume}x average")
}
```

#### calculateVolumeChange

```kotlin
fun calculateVolumeChange(volumes: List<Double>): List<Double?>
```

**Returns**: List of percentage volume changes

#### calculateOnBalanceVolume

```kotlin
fun calculateOnBalanceVolume(
    closes: List<Double>,
    volumes: List<Double>
): List<Double?>
```

**Returns**: List of On-Balance Volume (OBV) values

---

### MovingAverageCalculator

**Package**: `com.cryptotrader.domain.indicators.movingaverage`

**Purpose**: Calculates various types of moving averages.

#### calculateSMA

Simple Moving Average.

```kotlin
fun calculateSMA(
    data: List<Double>,
    period: Int
): List<Double?>
```

**Parameters**:
- `data`: List of price data
- `period`: Lookback period

**Returns**: List of SMA values

**Example**:
```kotlin
val closes = candles.map { it.close }
val sma20 = maCalculator.calculateSMA(closes, 20)
val sma50 = maCalculator.calculateSMA(closes, 50)

val current20 = sma20.lastOrNull()
val current50 = sma50.lastOrNull()

if (current20 != null && current50 != null && current20 > current50) {
    println("Golden cross: SMA20 ($current20) > SMA50 ($current50)")
}
```

#### calculateEMA

Exponential Moving Average.

```kotlin
fun calculateEMA(
    data: List<Double>,
    period: Int
): List<Double?>
```

**Parameters**:
- `data`: List of price data
- `period`: Lookback period

**Returns**: List of EMA values

**Formula**: `EMA_t = Price_t * k + EMA_{t-1} * (1 - k)` where `k = 2 / (period + 1)`

#### calculateWMA

Weighted Moving Average.

```kotlin
fun calculateWMA(
    data: List<Double>,
    period: Int
): List<Double?>
```

**Parameters**:
- `data`: List of price data
- `period`: Lookback period

**Returns**: List of WMA values

---

## Usage Patterns

### Pattern 1: Single Indicator Calculation

```kotlin
@Inject constructor(
    private val rsiCalculator: RsiCalculator
) {
    fun analyze(candles: List<Candle>) {
        val closes = candles.map { it.close }
        val rsiValues = rsiCalculator.calculate(closes, period = 14)
        val currentRsi = rsiValues.lastOrNull()

        // Use RSI value
    }
}
```

### Pattern 2: Multiple Indicators

```kotlin
@Inject constructor(
    private val rsiCalculator: RsiCalculator,
    private val macdCalculator: MacdCalculator
) {
    fun analyze(candles: List<Candle>) {
        val closes = candles.map { it.close }

        val rsiValues = rsiCalculator.calculate(closes)
        val macdResult = macdCalculator.calculate(closes)

        val rsi = rsiValues.lastOrNull()
        val histogram = macdResult.histogram.lastOrNull()

        if (rsi != null && rsi < 30 && histogram != null && histogram > 0) {
            println("Strong buy signal: Oversold + Bullish MACD")
        }
    }
}
```

### Pattern 3: Strategy Evaluation

```kotlin
@Inject constructor(
    private val evaluator: StrategyEvaluatorV2
) {
    fun checkEntry(strategy: Strategy, ticker: MarketTicker): Boolean {
        // Update history
        evaluator.updatePriceHistory(ticker.pair, ticker)

        // Check if enough data
        if (!evaluator.hasEnoughHistory(ticker.pair)) {
            return false
        }

        // Evaluate
        return evaluator.evaluateEntryConditions(strategy, ticker)
    }
}
```

---

## Error Handling

All calculators validate inputs and throw `IllegalArgumentException` for:
- Invalid periods (period <= 0)
- Mismatched list sizes (for multi-input calculators)
- Empty input lists

Example error handling:

```kotlin
try {
    val rsiValues = rsiCalculator.calculate(closes, period = 14)
    // Process results
} catch (e: IllegalArgumentException) {
    Timber.e(e, "Invalid RSI calculation parameters")
    // Handle error
}
```

---

## Related Documentation

- [Phase 2 Implementation Guide](./PHASE2_IMPLEMENTATION_GUIDE.md)
- [Indicator System Architecture](./INDICATOR_SYSTEM_ARCHITECTURE.md)
- [Developer Guide: Indicators](./DEVELOPER_GUIDE_INDICATORS.md)

---

**Last Updated**: November 14, 2024
**Document Version**: 1.0
