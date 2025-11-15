# Indicator System Architecture

**Version**: 1.0
**Date**: November 14, 2024
**Purpose**: Technical architecture documentation for the Phase 2 indicator system

---

## Table of Contents

1. [System Overview](#system-overview)
2. [Component Architecture](#component-architecture)
3. [Data Flow](#data-flow)
4. [Caching Strategy](#caching-strategy)
5. [Dependency Injection](#dependency-injection)
6. [Thread Safety](#thread-safety)
7. [Performance Characteristics](#performance-characteristics)
8. [Storage Architecture](#storage-architecture)

---

## System Overview

The Phase 2 indicator system is built on four key architectural principles:

1. **Separation of Concerns**: Each component has a single, well-defined responsibility
2. **Dependency Injection**: All dependencies are injected via Hilt for testability
3. **Caching First**: LRU cache reduces redundant calculations
4. **Thread Safety**: Concurrent data structures for safe multi-threaded access

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                         CryptoTrader Application                     │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                                    v
┌─────────────────────────────────────────────────────────────────────┐
│                         Hilt DI Container                            │
│  ┌───────────────────────────────────────────────────────────────┐  │
│  │                      IndicatorModule                           │  │
│  │  - Provides all calculator instances                          │  │
│  │  - Wires calculator dependencies                              │  │
│  │  - Configures IndicatorCache                                  │  │
│  └───────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────┘
                                    │
                    ┌───────────────┼───────────────┐
                    v               v               v
        ┌──────────────┐  ┌─────────────┐  ┌──────────────┐
        │   Trading    │  │   Domain    │  │ Presentation │
        │   Engine     │  │   Services  │  │    Layer     │
        └──────────────┘  └─────────────┘  └──────────────┘
                    │               │               │
                    └───────────────┼───────────────┘
                                    v
                    ┌───────────────────────────────┐
                    │   StrategyEvaluatorV2         │
                    │   - Entry/exit evaluation     │
                    │   - Condition parsing         │
                    │   - Indicator coordination    │
                    └───────────────────────────────┘
                                    │
        ┌───────────────────────────┼───────────────────────────┐
        v                           v                           v
┌───────────────┐      ┌────────────────────┐      ┌──────────────────┐
│ Market Data   │      │ Price History      │      │ Indicator Cache  │
│ Adapter       │      │ Manager            │      │ (LRU)            │
│               │      │                    │      │                  │
│ - Ticker→     │      │ - Candle storage   │      │ - Result caching │
│   Candle      │      │ - Thread-safe      │      │ - Auto-eviction  │
│ - Batch       │      │ - Max 200/pair     │      │ - Max 100 items  │
│   conversion  │      │ - Cache persist    │      │ - Thread-safe    │
└───────────────┘      └────────────────────┘      └──────────────────┘
                                    │
                    ┌───────────────┼───────────────┐
                    v               v               v
        ┌──────────────────┐  ┌─────────────────┐  ┌───────────────┐
        │  Indicator       │  │  Indicator      │  │  Indicator    │
        │  Calculators     │  │  Calculators    │  │  Calculators  │
        │                  │  │                 │  │               │
        │  - RSI           │  │  - MACD         │  │  - Bollinger  │
        │  - ATR           │  │  - Stochastic   │  │  - Volume     │
        │  - MovingAverage │  │                 │  │               │
        └──────────────────┘  └─────────────────┘  └───────────────┘
```

---

## Component Architecture

### Layer Structure

```
┌─────────────────────────────────────────────────────────────────┐
│                      PRESENTATION LAYER                          │
│  - ViewModels                                                    │
│  - UI State                                                      │
│  - Compose Screens                                               │
└─────────────────────────────────────────────────────────────────┘
                              │
                              v
┌─────────────────────────────────────────────────────────────────┐
│                       DOMAIN LAYER                               │
│                                                                  │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │              Trading Domain                                │ │
│  │  ┌──────────────────────────────────────────────┐          │ │
│  │  │        StrategyEvaluatorV2                   │          │ │
│  │  │  - Entry/exit condition evaluation           │          │ │
│  │  │  - Condition parsing                         │          │ │
│  │  │  - Indicator coordination                    │          │ │
│  │  └──────────────────────────────────────────────┘          │ │
│  │  ┌──────────────────────────────────────────────┐          │ │
│  │  │        MarketDataAdapter                     │          │ │
│  │  │  - MarketTicker → Candle conversion          │          │ │
│  │  │  - OHLCV normalization                       │          │ │
│  │  └──────────────────────────────────────────────┘          │ │
│  │  ┌──────────────────────────────────────────────┐          │ │
│  │  │        PriceHistoryManager                   │          │ │
│  │  │  - Candle storage (ConcurrentHashMap)        │          │ │
│  │  │  - Max 200 candles per pair                  │          │ │
│  │  │  - Thread-safe operations                    │          │ │
│  │  └──────────────────────────────────────────────┘          │ │
│  └────────────────────────────────────────────────────────────┘ │
│                                                                  │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │              Indicators Domain                             │ │
│  │  ┌────────────────┐  ┌────────────────┐  ┌──────────────┐ │ │
│  │  │ RsiCalculator  │  │ MacdCalculator │  │ AtrCalculator│ │ │
│  │  └────────────────┘  └────────────────┘  └──────────────┘ │ │
│  │  ┌────────────────┐  ┌────────────────┐  ┌──────────────┐ │ │
│  │  │BollingerBands  │  │  Stochastic    │  │   Volume     │ │ │
│  │  │  Calculator    │  │  Calculator    │  │  Calculator  │ │ │
│  │  └────────────────┘  └────────────────┘  └──────────────┘ │ │
│  │  ┌────────────────┐  ┌────────────────┐                   │ │
│  │  │ MovingAverage  │  │ IndicatorCache │                   │ │
│  │  │  Calculator    │  │   (LRU)        │                   │ │
│  │  └────────────────┘  └────────────────┘                   │ │
│  └────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
                              │
                              v
┌─────────────────────────────────────────────────────────────────┐
│                       DATA LAYER                                 │
│  - Repositories                                                  │
│  - Data Sources (API, Database)                                  │
│  - DAOs                                                          │
└─────────────────────────────────────────────────────────────────┘
```

### Component Relationships

```
StrategyEvaluatorV2
    ├── Depends on: MarketDataAdapter
    ├── Depends on: PriceHistoryManager
    ├── Depends on: RsiCalculator
    ├── Depends on: MacdCalculator
    ├── Depends on: BollingerBandsCalculator
    ├── Depends on: AtrCalculator
    ├── Depends on: StochasticCalculator
    ├── Depends on: VolumeIndicatorCalculator
    └── Depends on: IndicatorCache

PriceHistoryManager
    └── Depends on: IndicatorCache

MacdCalculator
    └── Depends on: MovingAverageCalculator

BollingerBandsCalculator
    └── Depends on: MovingAverageCalculator

StochasticCalculator
    └── Depends on: MovingAverageCalculator

VolumeIndicatorCalculator
    └── Depends on: MovingAverageCalculator

All Calculators (optional)
    └── May use: IndicatorCache
```

---

## Data Flow

### Strategy Evaluation Flow

```
┌──────────────────────────────────────────────────────────────────────┐
│ Step 1: Market Data Arrives                                          │
└──────────────────────────────────────────────────────────────────────┘
                              │
                              v
                    [MarketTicker Object]
                    - pair: "BTC/USD"
                    - last: 45000.0
                    - high24h: 46000.0
                    - low24h: 44000.0
                    - volume24h: 1234567
                    - timestamp: 1699999999
                              │
                              v
┌──────────────────────────────────────────────────────────────────────┐
│ Step 2: Convert to Candle Format                                     │
│ [MarketDataAdapter.toCandle()]                                       │
└──────────────────────────────────────────────────────────────────────┘
                              │
                              v
                        [Candle Object]
                        - timestamp: 1699999999
                        - open: 44500.0
                        - high: 46000.0
                        - low: 44000.0
                        - close: 45000.0
                        - volume: 1234567
                              │
                              v
┌──────────────────────────────────────────────────────────────────────┐
│ Step 3: Store in History                                             │
│ [PriceHistoryManager.updateHistory()]                                │
│ - Add to in-memory ConcurrentHashMap                                 │
│ - Trim to max 200 candles                                            │
│ - Persist to IndicatorCache                                          │
└──────────────────────────────────────────────────────────────────────┘
                              │
                              v
┌──────────────────────────────────────────────────────────────────────┐
│ Step 4: Evaluate Strategy                                            │
│ [StrategyEvaluatorV2.evaluateEntryConditions()]                      │
└──────────────────────────────────────────────────────────────────────┘
                              │
                ┌─────────────┴─────────────┐
                v                           v
    Check History Size              Parse Conditions
    (min 30 candles)                (e.g., "RSI < 30")
                │                           │
                └─────────────┬─────────────┘
                              v
┌──────────────────────────────────────────────────────────────────────┐
│ Step 5: Evaluate Each Condition                                      │
│ [evaluateCondition()]                                                │
└──────────────────────────────────────────────────────────────────────┘
                              │
                              v
              Identify Indicator Type
              (RSI, MACD, Bollinger, etc.)
                              │
                              v
┌──────────────────────────────────────────────────────────────────────┐
│ Step 6: Calculate Indicator                                          │
│ [RsiCalculator.calculate() / MacdCalculator.calculate() / etc.]      │
└──────────────────────────────────────────────────────────────────────┘
                              │
                ┌─────────────┴─────────────┐
                v                           v
    Generate Cache Key            Check IndicatorCache
    (indicator + params           for existing result
     + data hash)                           │
                │                           │
                └─────────────┬─────────────┘
                              v
                    ┌──────────────────┐
                    │  Cache Hit?      │
                    └──────────────────┘
                         │       │
                    Yes  │       │  No
                         v       v
                 Return cached  Perform calculation
                    result           │
                         │           v
                         │      Store in cache
                         │           │
                         └─────┬─────┘
                               v
                    [Indicator Result]
                    (e.g., RSI = 42.5)
                               │
                               v
┌──────────────────────────────────────────────────────────────────────┐
│ Step 7: Compare with Threshold                                       │
│ [Condition evaluation: RSI < 30 → false]                             │
└──────────────────────────────────────────────────────────────────────┘
                               │
                               v
┌──────────────────────────────────────────────────────────────────────┐
│ Step 8: Combine All Conditions                                       │
│ [All entry conditions must be true]                                  │
└──────────────────────────────────────────────────────────────────────┘
                               │
                               v
                    [Final Decision: Boolean]
                    - true: Enter trade
                    - false: Wait
```

### Candle Storage Flow

```
MarketTicker (from WebSocket/API)
        │
        v
MarketDataAdapter.toCandle()
        │
        v
Candle Object (OHLCV)
        │
        v
PriceHistoryManager.updateHistory(pair, candle)
        │
        v
    ┌───────────────────────────────────┐
    │ ConcurrentHashMap<String, List>   │
    │                                   │
    │ "BTC/USD" → [Candle1, Candle2,   │
    │              Candle3, ...,        │
    │              Candle200]           │
    │                                   │
    │ "ETH/USD" → [Candle1, Candle2,   │
    │              ...]                 │
    └───────────────────────────────────┘
        │
        v (also persist)
    ┌───────────────────────────────────┐
    │ IndicatorCache                    │
    │                                   │
    │ "price_history_BTC/USD" →        │
    │     [serialized candles]          │
    └───────────────────────────────────┘
```

---

## Caching Strategy

### LRU Cache Implementation

```
┌─────────────────────────────────────────────────────────────────┐
│                     IndicatorCache                               │
│                 (Least Recently Used - LRU)                      │
│                                                                  │
│  Internal Structure: LinkedHashMap                               │
│  - Access Order: true (LRU behavior)                             │
│  - Max Size: 100 entries (configurable)                          │
│  - Thread Safe: synchronized operations                          │
│                                                                  │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │  Cache Entry                                               │ │
│  │                                                            │ │
│  │  Key: "RSI|period=14|data_hash=1234567"                   │ │
│  │  Value: [45.2, 42.1, 39.8, ..., 52.3]                     │ │
│  │  Last Accessed: 2024-11-14 10:30:15                       │ │
│  └────────────────────────────────────────────────────────────┘ │
│                                                                  │
│  Eviction Policy:                                                │
│  - When size > maxSize, remove least recently used entry         │
│  - Automatic via LinkedHashMap.removeEldestEntry()               │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

### Cache Key Generation

```kotlin
// Formula: indicatorName|parameter1=value1,parameter2=value2|dataHash

// Example 1: RSI
"RSI|period=14|data_hash=-1234567890"

// Example 2: MACD
"MACD|fastPeriod=12,slowPeriod=26,signalPeriod=9|data_hash=987654321"

// Example 3: Bollinger Bands
"BollingerBands|period=20,stdDev=2.0|data_hash=1122334455"

// Data hash is computed from the input price list
// Same prices → same hash → cache hit
// Different prices → different hash → cache miss
```

### Cache Decision Flow

```
Calculator.calculate() called
        │
        v
    ┌───────────────────────┐
    │ Generate cache key    │
    │ (indicator + params   │
    │  + data hash)         │
    └───────────────────────┘
        │
        v
    ┌───────────────────────┐
    │ Check cache           │
    │ cache.get(key)        │
    └───────────────────────┘
        │
        ├─────────────┬─────────────┐
        │             │             │
        v             v             v
   Cache HIT     Cache MISS    Cache disabled
        │             │           (null)
        v             v             │
   Return cached  Compute result    v
   result             │          Compute result
        │             v             │
        │         Store in cache    │
        │             │             │
        └─────────────┴─────────────┘
                      │
                      v
              Return result
```

### Cache Performance Metrics

Expected performance with typical usage:

```
┌─────────────────────────────────────────────────────────────┐
│ Scenario: Evaluating 5 strategies on same market data       │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│ Without Cache:                                               │
│  - RSI calculated: 5 times                                   │
│  - MACD calculated: 5 times                                  │
│  - Bollinger calculated: 5 times                             │
│  - Total time: ~25-50ms                                      │
│                                                              │
│ With Cache (60% hit rate):                                   │
│  - RSI: 1 calculation + 4 cache hits                         │
│  - MACD: 1 calculation + 4 cache hits                        │
│  - Bollinger: 1 calculation + 4 cache hits                   │
│  - Total time: ~10-20ms (50-60% faster)                      │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

---

## Dependency Injection

### Hilt Component Hierarchy

```
┌─────────────────────────────────────────────────────────────┐
│              Application                                     │
│              @HiltAndroidApp                                 │
└─────────────────────────────────────────────────────────────┘
                      │
                      v
┌─────────────────────────────────────────────────────────────┐
│         SingletonComponent                                   │
│         (Application Lifetime)                               │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │         IndicatorModule                              │   │
│  │         @InstallIn(SingletonComponent::class)        │   │
│  │                                                      │   │
│  │  @Provides @Singleton                                │   │
│  │  - RsiCalculator                                     │   │
│  │  - MacdCalculator                                    │   │
│  │  - BollingerBandsCalculator                          │   │
│  │  - AtrCalculator                                     │   │
│  │  - StochasticCalculator                              │   │
│  │  - VolumeIndicatorCalculator                         │   │
│  │  - MovingAverageCalculator                           │   │
│  │  - IndicatorCache                                    │   │
│  │  - MarketDataAdapter                                 │   │
│  │  - PriceHistoryManager                               │   │
│  │  - StrategyEvaluatorV2                               │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                      │
                      v
┌─────────────────────────────────────────────────────────────┐
│         ActivityRetainedComponent                            │
│         (Activity Lifetime)                                  │
│                                                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │         ViewModelComponent                           │   │
│  │                                                      │   │
│  │  Injects into:                                       │   │
│  │  - TradingViewModel                                  │   │
│  │  - StrategyViewModel                                 │   │
│  │  - PortfolioViewModel                                │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

### Dependency Graph

```
IndicatorModule
    │
    ├─> provideIndicatorCache()
    │       │
    │       └─> new IndicatorCache(maxSize = 100)
    │
    ├─> provideMovingAverageCalculator()
    │       │
    │       └─> new MovingAverageCalculatorImpl()
    │
    ├─> provideRsiCalculator()
    │       │
    │       └─> new RsiCalculatorImpl()
    │
    ├─> provideMacdCalculator(maCalculator)
    │       │
    │       └─> new MacdCalculatorImpl(maCalculator)
    │               │
    │               └─> uses MovingAverageCalculator
    │
    ├─> provideBollingerBandsCalculator(maCalculator)
    │       │
    │       └─> new BollingerBandsCalculatorImpl(maCalculator)
    │
    ├─> provideAtrCalculator()
    │       │
    │       └─> new AtrCalculatorImpl()
    │
    ├─> provideStochasticCalculator(maCalculator)
    │       │
    │       └─> new StochasticCalculatorImpl(maCalculator)
    │
    ├─> provideVolumeIndicatorCalculator(maCalculator)
    │       │
    │       └─> new VolumeIndicatorCalculatorImpl(maCalculator)
    │
    ├─> provideMarketDataAdapter()
    │       │
    │       └─> new MarketDataAdapter()
    │
    ├─> providePriceHistoryManager(indicatorCache)
    │       │
    │       └─> new PriceHistoryManager(indicatorCache)
    │
    └─> provideStrategyEvaluatorV2(
            rsiCalculator,
            maCalculator,
            macdCalculator,
            bollingerCalculator,
            atrCalculator,
            stochasticCalculator,
            volumeCalculator,
            indicatorCache,
            priceHistoryManager,
            marketDataAdapter
        )
```

---

## Thread Safety

### Concurrent Data Structures

```
┌─────────────────────────────────────────────────────────────┐
│ PriceHistoryManager                                          │
│                                                              │
│  Data Structure:                                             │
│  private val historyMap =                                    │
│      ConcurrentHashMap<String, MutableList<Candle>>()        │
│                                                              │
│  Thread Safety:                                              │
│  - ConcurrentHashMap: Thread-safe reads/writes               │
│  - MutableList: Protected by synchronized blocks             │
│                                                              │
│  synchronized(historyMap) {                                  │
│      val history = historyMap.getOrPut(pair) {              │
│          mutableListOf()                                     │
│      }                                                       │
│      history.add(candle)                                     │
│  }                                                           │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│ IndicatorCache                                               │
│                                                              │
│  Data Structure:                                             │
│  private val cache = LinkedHashMap<String, Any>(...)         │
│                                                              │
│  Thread Safety:                                              │
│  - All operations wrapped in synchronized(cache)             │
│                                                              │
│  fun put(key: String, value: Any) {                          │
│      synchronized(cache) {                                   │
│          cache[key] = value                                  │
│      }                                                       │
│  }                                                           │
└─────────────────────────────────────────────────────────────┘
```

### Concurrency Patterns

```
Multiple Threads Accessing StrategyEvaluatorV2
    │
    ├─> Thread 1: Evaluate Strategy A on BTC/USD
    │       │
    │       ├─> PriceHistoryManager.getHistory("BTC/USD")
    │       │       └─> synchronized read (safe)
    │       │
    │       ├─> RsiCalculator.calculate(prices)
    │       │       └─> IndicatorCache.get(key)
    │       │               └─> synchronized read (safe)
    │       │
    │       └─> Return result
    │
    ├─> Thread 2: Evaluate Strategy B on BTC/USD
    │       │
    │       ├─> PriceHistoryManager.getHistory("BTC/USD")
    │       │       └─> synchronized read (safe, same data as Thread 1)
    │       │
    │       ├─> MacdCalculator.calculate(prices)
    │       │       └─> IndicatorCache.get(key)
    │       │               └─> synchronized read (safe)
    │       │
    │       └─> Return result
    │
    └─> Thread 3: Update history for ETH/USD
            │
            ├─> PriceHistoryManager.updateHistory("ETH/USD", candle)
            │       └─> synchronized write (safe, different key)
            │
            └─> IndicatorCache.put(key, history)
                    └─> synchronized write (safe)

No deadlocks: All synchronized blocks are small and non-nested
No race conditions: All mutable state protected by synchronization
```

---

## Performance Characteristics

### Time Complexity

```
┌─────────────────────────────────────────────────────────────────┐
│ Operation                          │ Time Complexity            │
├────────────────────────────────────┼────────────────────────────┤
│ PriceHistoryManager.updateHistory  │ O(1) amortized             │
│ PriceHistoryManager.getHistory     │ O(n) where n = candles     │
│ IndicatorCache.get                 │ O(1)                       │
│ IndicatorCache.put                 │ O(1)                       │
│ RsiCalculator.calculate            │ O(n) where n = period      │
│ MacdCalculator.calculate           │ O(n) where n = data points │
│ MovingAverageCalculator.calculate  │ O(n) where n = data points │
│ StrategyEvaluatorV2.evaluate       │ O(c * i) where:            │
│                                    │   c = # conditions         │
│                                    │   i = indicator calc time  │
└─────────────────────────────────────────────────────────────────┘
```

### Space Complexity

```
┌─────────────────────────────────────────────────────────────────┐
│ Component                 │ Space Usage                         │
├───────────────────────────┼─────────────────────────────────────┤
│ PriceHistoryManager       │ O(p * c) where:                     │
│                           │   p = # pairs (e.g., 10)            │
│                           │   c = max candles (200)             │
│                           │ = 2000 candles * 48 bytes           │
│                           │ ≈ 96 KB                             │
├───────────────────────────┼─────────────────────────────────────┤
│ IndicatorCache            │ O(e * s) where:                     │
│                           │   e = max entries (100)             │
│                           │   s = avg entry size (1 KB)         │
│                           │ ≈ 100 KB                            │
├───────────────────────────┼─────────────────────────────────────┤
│ Calculator Instances      │ O(1) - Stateless singletons         │
│                           │ ≈ 1 KB total                        │
├───────────────────────────┼─────────────────────────────────────┤
│ TOTAL                     │ ≈ 200 KB                            │
└─────────────────────────────────────────────────────────────────┘
```

### Benchmark Results (Estimated)

```
┌─────────────────────────────────────────────────────────────────┐
│ Scenario: Evaluate 1 strategy with 3 indicator conditions       │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│ V1 System (No Caching):                                          │
│  - RSI calculation: 0.8ms                                        │
│  - MACD calculation: 1.2ms                                       │
│  - Bollinger calculation: 0.7ms                                  │
│  - Total: 2.7ms per evaluation                                   │
│                                                                  │
│ V2 System (First Call - Cache Miss):                             │
│  - RSI calculation: 0.8ms                                        │
│  - MACD calculation: 1.2ms                                       │
│  - Bollinger calculation: 0.7ms                                  │
│  - Cache overhead: 0.1ms                                         │
│  - Total: 2.8ms (3% slower)                                      │
│                                                                  │
│ V2 System (Subsequent Call - Cache Hit):                         │
│  - RSI cache lookup: 0.01ms                                      │
│  - MACD cache lookup: 0.01ms                                     │
│  - Bollinger cache lookup: 0.01ms                                │
│  - Total: 0.03ms (99% faster)                                    │
│                                                                  │
│ V2 System (Average with 60% cache hit rate):                     │
│  - Total: ~1.1ms per evaluation (59% faster than V1)             │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘
```

---

## Storage Architecture

### In-Memory Storage

```
┌─────────────────────────────────────────────────────────────────┐
│                    Application Memory                            │
│                                                                  │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │ PriceHistoryManager                                        │ │
│  │                                                            │ │
│  │ ConcurrentHashMap<String, MutableList<Candle>>             │ │
│  │                                                            │ │
│  │ ┌──────────────────────────────────────────────────────┐  │ │
│  │ │ "BTC/USD" → [                                        │  │ │
│  │ │   Candle(ts=1, o=44000, h=45000, l=43500, c=44500),  │  │ │
│  │ │   Candle(ts=2, o=44500, h=45500, l=44000, c=45000),  │  │ │
│  │ │   ...                                                 │  │ │
│  │ │   (max 200 candles)                                   │  │ │
│  │ │ ]                                                     │  │ │
│  │ └──────────────────────────────────────────────────────┘  │ │
│  │                                                            │ │
│  │ ┌──────────────────────────────────────────────────────┐  │ │
│  │ │ "ETH/USD" → [...]                                    │  │ │
│  │ └──────────────────────────────────────────────────────┘  │ │
│  └────────────────────────────────────────────────────────────┘ │
│                                                                  │
│  ┌────────────────────────────────────────────────────────────┐ │
│  │ IndicatorCache                                             │ │
│  │                                                            │ │
│  │ LinkedHashMap<String, Any> (LRU)                           │ │
│  │                                                            │ │
│  │ ┌──────────────────────────────────────────────────────┐  │ │
│  │ │ "RSI|period=14|hash=123" → [45.2, 42.1, ...]        │  │ │
│  │ └──────────────────────────────────────────────────────┘  │ │
│  │ ┌──────────────────────────────────────────────────────┐  │ │
│  │ │ "MACD|12,26,9|hash=456" → MacdResult(...)           │  │ │
│  │ └──────────────────────────────────────────────────────┘  │ │
│  │ ┌──────────────────────────────────────────────────────┐  │ │
│  │ │ "price_history_BTC/USD" → [Candle, ...]             │  │ │
│  │ └──────────────────────────────────────────────────────┘  │ │
│  │                                                            │ │
│  │ (max 100 entries, auto-eviction)                           │ │
│  └────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

### Data Lifecycle

```
1. MarketTicker arrives
        │
        v
2. Convert to Candle (MarketDataAdapter)
        │
        v
3. Store in PriceHistoryManager
        │
        ├─> In-memory: ConcurrentHashMap
        │       - Fast access
        │       - Lost on app restart
        │
        └─> Cache: IndicatorCache
                - Persisted across calls
                - Survives during app session
                - Lost on app restart
                │
                v
4. Calculate indicators (on demand)
        │
        ├─> Check IndicatorCache
        │       - If hit: Return cached result
        │       - If miss: Calculate and cache
        │
        v
5. Indicator results used in strategy evaluation
        │
        v
6. Candle history auto-trimmed to 200 max per pair
        │
        v
7. Cache auto-evicts LRU entries when > 100 items
```

---

## Summary

The Phase 2 indicator system provides:

1. **Scalability**: Clean architecture supports easy addition of new indicators
2. **Performance**: LRU caching reduces redundant calculations by 50-60%
3. **Testability**: Dependency injection enables comprehensive unit testing
4. **Thread Safety**: Concurrent data structures prevent race conditions
5. **Memory Efficiency**: Bounded storage (200 candles/pair, 100 cache entries)
6. **Maintainability**: Clear separation of concerns and well-defined interfaces

---

## Related Documentation

- [Phase 2 Implementation Guide](./PHASE2_IMPLEMENTATION_GUIDE.md)
- [Developer Guide: Indicators](./DEVELOPER_GUIDE_INDICATORS.md)
- [API Reference](./API_REFERENCE.md)
- [Migration Guide V1 to V2](./MIGRATION_GUIDE_V1_TO_V2.md)

---

**Last Updated**: November 14, 2024
**Document Version**: 1.0
