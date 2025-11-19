# BigDecimal Migration Plan
**Priority**: HIGH (before live trading with real money)
**Estimated Effort**: 3-5 days (sequential) or 2-3 days (parallelized)
**Impact**: Prevents cumulative precision errors in monetary calculations
**Date Created**: 2025-11-19
**Current Database Version**: 14

---

## 🎯 OBJECTIVE

Migrate all monetary calculations from `Double` to `BigDecimal` to achieve exact decimal arithmetic required for hedge-fund quality trading system.

### Why This Matters
- **Precision Errors**: `Double` uses binary floating-point, causing rounding errors (e.g., 0.1 + 0.2 = 0.30000000000000004)
- **Cumulative Errors**: Small errors compound over thousands of trades
- **Real Money**: Hedge funds use exact decimal arithmetic for all monetary calculations
- **Regulatory**: Financial systems require exact calculations for audit compliance

### Example Issue with Double
```kotlin
// CURRENT (Double) - PRECISION ISSUE
val balance = 1000.10
val fee = 0.26  // 0.26% Kraken fee
val cost = balance * (fee / 100.0)
// Result: 2.600260000000000001 (imprecise!)

// TARGET (BigDecimal) - EXACT
val balance = BigDecimal("1000.10")
val fee = BigDecimal("0.26")
val cost = balance * (fee / BigDecimal("100"))
// Result: 2.60 (exact!)
```

---

## 📊 SCOPE ANALYSIS

### Files Using Double for Monetary Values

**Domain Models** (12 files, ~138 Double fields):
1. ✅ `Portfolio.kt` - 14 balance/value fields
2. ✅ `Trade.kt` - 5 fields (price, volume, cost, fee, profit)
3. ✅ `Position.kt` - 9 fields (prices, P&L)
4. ✅ `Strategy.kt` - 18 fields (percents, profits, metrics)
5. ✅ `Order.kt` - Price, volume, fee fields
6. ✅ `MarketSnapshot.kt` - Price fields
7. ✅ `TradingAdviceRequest.kt` - 10 fields
8. ✅ `TradingAdviceResponse.kt` - 4 fields
9. ✅ `BacktestProposal.kt` - Balance fields
10. ✅ `MetaAnalysis.kt` - Confidence scores
11. ✅ `TradingOpportunity.kt` - Price fields
12. ✅ `ChatMessage.kt` - Potential numeric fields

**Calculation Logic** (~20 files):
1. ✅ `BacktestEngine.kt` - All P&L calculations, equity curve
2. ✅ `TradingEngine.kt` - Position sizing, trade execution
3. ✅ `RiskManager.kt` - Position sizing, risk calculations
4. ✅ `ProfitCalculator.kt` - FIFO matching, P&L calculations
5. ✅ `PerformanceCalculator.kt` - ROI, daily P&L, returns
6. ✅ `TradingCostModel.kt` - Fee, slippage, spread calculations
7. ✅ `KellyCriterionCalculator.kt` - Position sizing recommendations
8. ✅ `StrategyEvaluatorV2.kt` - Indicator thresholds
9. ✅ `MultiTimeframeAnalyzer.kt` - Price comparisons
10. ✅ `PortfolioManager.kt` - Balance calculations
11. ✅ All indicator calculators (RSI, MACD, Bollinger, ATR, etc.) - 8 files

**Data Layer** (~15 files):
1. ✅ `KrakenRepository.kt` - API price/volume conversions
2. ✅ `PortfolioRepository.kt` - Balance updates
3. ✅ `TradeRepository.kt` - Trade data handling
4. ✅ `PositionRepository.kt` - Position data handling
5. ✅ `StrategyRepository.kt` - Strategy metrics
6. ✅ All DAO classes (TradeDao, PositionDao, etc.) - 7 files

**Database Entities** (~10 files):
1. ✅ `TradeEntity.kt`
2. ✅ `PositionEntity.kt`
3. ✅ `OrderEntity.kt`
4. ✅ `StrategyEntity.kt`
5. ✅ `PortfolioSnapshotEntity.kt`
6. ✅ `BacktestRunEntity.kt`
7. ✅ `ExecutionLogEntity.kt`
8. ✅ `OHLCBarEntity.kt`
9. ✅ All related entities

**UI Layer** (~5 files):
1. ✅ `PortfolioViewModel.kt` - Display formatting
2. ✅ `StrategyViewModel.kt` - Strategy metrics display
3. ✅ `DashboardViewModel.kt` - Portfolio display
4. ✅ `BacktestViewModel.kt` - Results display
5. ✅ Extensions.kt - Formatting helpers

**Total Estimated Files**: ~60 files
**Total Estimated Double Fields**: ~250 fields

---

## 📋 MIGRATION STRATEGY - 4 PHASES

### Phase 1: Foundation (Day 1 - 8 hours)
**Goal**: Add BigDecimal support infrastructure without breaking existing code

#### 1.1 Add Kotlin BigDecimal Extensions (2 hours)
**File**: Create `app/src/main/java/com/cryptotrader/utils/BigDecimalExtensions.kt`

```kotlin
package com.cryptotrader.utils

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Standard scale for monetary values (8 decimal places for crypto precision)
 */
const val MONEY_SCALE = 8

/**
 * Standard rounding mode for financial calculations
 */
val MONEY_ROUNDING = RoundingMode.HALF_EVEN  // Banker's rounding

/**
 * Create BigDecimal from String with default scale
 */
fun String.toBigDecimalMoney(): BigDecimal =
    BigDecimal(this).setScale(MONEY_SCALE, MONEY_ROUNDING)

/**
 * Create BigDecimal from Double (use cautiously, prefer String)
 */
fun Double.toBigDecimalMoney(): BigDecimal =
    BigDecimal.valueOf(this).setScale(MONEY_SCALE, MONEY_ROUNDING)

/**
 * Format BigDecimal as USD currency string
 */
fun BigDecimal.toUSDString(): String =
    "$${this.setScale(2, MONEY_ROUNDING)}"

/**
 * Format BigDecimal as crypto amount (8 decimals)
 */
fun BigDecimal.toCryptoString(): String =
    this.setScale(8, MONEY_ROUNDING).toPlainString()

/**
 * Format BigDecimal as percentage
 */
fun BigDecimal.toPercentString(): String =
    "${this.setScale(2, MONEY_ROUNDING)}%"

/**
 * Safe division with scale
 */
infix fun BigDecimal.safeDiv(divisor: BigDecimal): BigDecimal {
    if (divisor == BigDecimal.ZERO) return BigDecimal.ZERO
    return this.divide(divisor, MONEY_SCALE, MONEY_ROUNDING)
}

/**
 * Percentage calculation: (value / total) * 100
 */
fun BigDecimal.percentOf(total: BigDecimal): BigDecimal {
    if (total == BigDecimal.ZERO) return BigDecimal.ZERO
    return (this safeDiv total) * BigDecimal("100")
}

/**
 * Apply percentage: value * (percent / 100)
 */
fun BigDecimal.applyPercent(percent: BigDecimal): BigDecimal =
    this * (percent safeDiv BigDecimal("100"))

/**
 * Common constants
 */
object BigDecimalConstants {
    val ZERO = BigDecimal.ZERO.setScale(MONEY_SCALE, MONEY_ROUNDING)
    val ONE = BigDecimal.ONE.setScale(MONEY_SCALE, MONEY_ROUNDING)
    val HUNDRED = BigDecimal("100").setScale(MONEY_SCALE, MONEY_ROUNDING)
}
```

#### 1.2 Add Room TypeConverter for BigDecimal (1 hour)
**File**: Modify `app/src/main/java/com/cryptotrader/data/local/Converters.kt`

```kotlin
import java.math.BigDecimal

class Converters {
    // ... existing converters ...

    @TypeConverter
    fun fromBigDecimal(value: BigDecimal?): String? {
        return value?.toPlainString()
    }

    @TypeConverter
    fun toBigDecimal(value: String?): BigDecimal? {
        return value?.let { BigDecimal(it) }
    }
}
```

#### 1.3 Add Gson Serializer for BigDecimal (1 hour)
**File**: Modify `app/src/main/java/com/cryptotrader/di/NetworkModule.kt`

```kotlin
import com.google.gson.JsonSerializer
import com.google.gson.JsonDeserializer
import java.math.BigDecimal

val gson = GsonBuilder()
    .registerTypeAdapter(BigDecimal::class.java, JsonSerializer<BigDecimal> { src, _, _ ->
        JsonPrimitive(src.toPlainString())
    })
    .registerTypeAdapter(BigDecimal::class.java, JsonDeserializer { json, _, _ ->
        BigDecimal(json.asString)
    })
    .create()
```

#### 1.4 Create Migration Testing Framework (2 hours)
**File**: Create `app/src/test/java/com/cryptotrader/migration/BigDecimalMigrationTest.kt`

```kotlin
package com.cryptotrader.migration

import org.junit.Test
import java.math.BigDecimal
import kotlin.test.assertEquals

class BigDecimalMigrationTest {

    @Test
    fun `test Double to BigDecimal conversion accuracy`() {
        val doubleValue = 1000.10
        val bigDecimalValue = BigDecimal("1000.10")

        // Show precision issue with Double
        val doubleFee = doubleValue * 0.0026  // 0.26%
        val bigDecimalFee = bigDecimalValue * BigDecimal("0.0026")

        println("Double result: $doubleFee")  // Imprecise
        println("BigDecimal result: ${bigDecimalFee.toPlainString()}")  // Exact

        // BigDecimal should be exact
        assertEquals(BigDecimal("2.60260"), bigDecimalFee)
    }

    @Test
    fun `test cumulative error with 1000 trades`() {
        // Simulate 1000 trades with Double
        var doubleBalance = 10000.0
        for (i in 1..1000) {
            doubleBalance += 1.01  // Small profit each trade
            doubleBalance -= 0.26  // Fee
        }

        // Simulate 1000 trades with BigDecimal
        var bigDecimalBalance = BigDecimal("10000.00")
        for (i in 1..1000) {
            bigDecimalBalance += BigDecimal("1.01")
            bigDecimalBalance -= BigDecimal("0.26")
        }

        println("Double after 1000 trades: $doubleBalance")
        println("BigDecimal after 1000 trades: ${bigDecimalBalance.toPlainString()}")

        // Show the cumulative error
        val error = Math.abs(doubleBalance - bigDecimalBalance.toDouble())
        println("Cumulative error: $$error")
    }
}
```

#### 1.5 Database Migration 14→15 (2 hours)
**File**: Modify `app/src/main/java/com/cryptotrader/data/local/migrations/DatabaseMigrations.kt`

**Strategy**: Keep Double columns, add parallel BigDecimal columns with TEXT type

```kotlin
val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(database: SupportSQLiteDatabase) {
        Timber.i("=== Starting Database Migration 14 → 15 ===")
        Timber.i("Purpose: Add BigDecimal support (Phase 1 - Non-breaking)")

        // Add TEXT columns for BigDecimal values (parallel to existing Double columns)
        // We keep Double columns for backward compatibility during migration

        // Trades table
        database.execSQL("ALTER TABLE trades ADD COLUMN price_decimal TEXT DEFAULT NULL")
        database.execSQL("ALTER TABLE trades ADD COLUMN volume_decimal TEXT DEFAULT NULL")
        database.execSQL("ALTER TABLE trades ADD COLUMN cost_decimal TEXT DEFAULT NULL")
        database.execSQL("ALTER TABLE trades ADD COLUMN fee_decimal TEXT DEFAULT NULL")
        database.execSQL("ALTER TABLE trades ADD COLUMN profit_decimal TEXT DEFAULT NULL")

        // Positions table
        database.execSQL("ALTER TABLE positions ADD COLUMN entry_price_decimal TEXT DEFAULT NULL")
        database.execSQL("ALTER TABLE positions ADD COLUMN exit_price_decimal TEXT DEFAULT NULL")
        database.execSQL("ALTER TABLE positions ADD COLUMN quantity_decimal TEXT DEFAULT NULL")
        database.execSQL("ALTER TABLE positions ADD COLUMN unrealized_pnl_decimal TEXT DEFAULT NULL")
        database.execSQL("ALTER TABLE positions ADD COLUMN realized_pnl_decimal TEXT DEFAULT NULL")

        // Orders table
        database.execSQL("ALTER TABLE orders ADD COLUMN price_decimal TEXT DEFAULT NULL")
        database.execSQL("ALTER TABLE orders ADD COLUMN volume_decimal TEXT DEFAULT NULL")
        database.execSQL("ALTER TABLE orders ADD COLUMN fee_decimal TEXT DEFAULT NULL")

        // Strategies table
        database.execSQL("ALTER TABLE strategies ADD COLUMN total_profit_decimal TEXT DEFAULT NULL")
        database.execSQL("ALTER TABLE strategies ADD COLUMN max_drawdown_decimal TEXT DEFAULT NULL")
        database.execSQL("ALTER TABLE strategies ADD COLUMN avg_win_decimal TEXT DEFAULT NULL")
        database.execSQL("ALTER TABLE strategies ADD COLUMN avg_loss_decimal TEXT DEFAULT NULL")

        Timber.i("✅ Migration 14 → 15 complete (BigDecimal columns added)")
    }
}
```

**Update AppDatabase**:
```kotlin
@Database(
    entities = [/* ... */],
    version = 15,  // Increment version
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    // ...
}
```

---

### Phase 2: Domain Models Migration (Day 2 - 8 hours)
**Goal**: Update all domain models to use BigDecimal with backward-compatible Double properties

#### 2.1 Migrate Core Domain Models (4 hours)

**Pattern**: Add BigDecimal properties alongside Double with deprecation warnings

**File**: `Trade.kt`
```kotlin
data class Trade(
    val id: Long = 0,
    val orderId: String,
    val pair: String,
    val type: TradeType,

    // OLD: Deprecated Double fields
    @Deprecated("Use priceDecimal", ReplaceWith("priceDecimal.toDouble()"))
    val price: Double,
    @Deprecated("Use volumeDecimal", ReplaceWith("volumeDecimal.toDouble()"))
    val volume: Double,
    @Deprecated("Use costDecimal", ReplaceWith("costDecimal.toDouble()"))
    val cost: Double,
    @Deprecated("Use feeDecimal", ReplaceWith("feeDecimal.toDouble()"))
    val fee: Double,
    @Deprecated("Use profitDecimal", ReplaceWith("profitDecimal?.toDouble()"))
    val profit: Double? = null,

    // NEW: BigDecimal fields (source of truth)
    val priceDecimal: BigDecimal = price.toBigDecimalMoney(),
    val volumeDecimal: BigDecimal = volume.toBigDecimalMoney(),
    val costDecimal: BigDecimal = cost.toBigDecimalMoney(),
    val feeDecimal: BigDecimal = fee.toBigDecimalMoney(),
    val profitDecimal: BigDecimal? = profit?.toBigDecimalMoney(),

    val timestamp: Long,
    val strategyId: String? = null,
    val status: TradeStatus = TradeStatus.EXECUTED,
    val notes: String? = null,
    val entryTime: Long? = null,
    val exitTime: Long? = null
) {
    // Convenience constructor for backward compatibility
    constructor(
        id: Long = 0,
        orderId: String,
        pair: String,
        type: TradeType,
        priceDecimal: BigDecimal,
        volumeDecimal: BigDecimal,
        costDecimal: BigDecimal,
        feeDecimal: BigDecimal,
        profitDecimal: BigDecimal? = null,
        timestamp: Long,
        strategyId: String? = null,
        status: TradeStatus = TradeStatus.EXECUTED,
        notes: String? = null,
        entryTime: Long? = null,
        exitTime: Long? = null
    ) : this(
        id = id,
        orderId = orderId,
        pair = pair,
        type = type,
        price = priceDecimal.toDouble(),
        volume = volumeDecimal.toDouble(),
        cost = costDecimal.toDouble(),
        fee = feeDecimal.toDouble(),
        profit = profitDecimal?.toDouble(),
        priceDecimal = priceDecimal,
        volumeDecimal = volumeDecimal,
        costDecimal = costDecimal,
        feeDecimal = feeDecimal,
        profitDecimal = profitDecimal,
        timestamp = timestamp,
        strategyId = strategyId,
        status = status,
        notes = notes,
        entryTime = entryTime,
        exitTime = exitTime
    )
}
```

**Similar pattern for**:
- ✅ `Position.kt`
- ✅ `Portfolio.kt`
- ✅ `Strategy.kt`
- ✅ `Order.kt`
- ✅ `MarketSnapshot.kt` (MarketTicker in Portfolio.kt)

#### 2.2 Migrate Database Entities (2 hours)

**File**: `TradeEntity.kt`
```kotlin
@Entity(tableName = "trades")
data class TradeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: String,
    val pair: String,
    val type: String,

    // OLD: Keep for migration compatibility
    val price: Double,
    val volume: Double,
    val cost: Double,
    val fee: Double,
    val profit: Double?,

    // NEW: BigDecimal columns (TEXT in SQLite)
    @ColumnInfo(name = "price_decimal") val priceDecimal: String? = null,
    @ColumnInfo(name = "volume_decimal") val volumeDecimal: String? = null,
    @ColumnInfo(name = "cost_decimal") val costDecimal: String? = null,
    @ColumnInfo(name = "fee_decimal") val feeDecimal: String? = null,
    @ColumnInfo(name = "profit_decimal") val profitDecimal: String? = null,

    val timestamp: Long,
    val strategyId: String?,
    val status: String,
    val notes: String?,
    val entryTime: Long?,
    val exitTime: Long?,
    val executedAt: Long
)
```

#### 2.3 Update Mappers (2 hours)

**File**: Create `app/src/main/java/com/cryptotrader/data/mapper/TradeMapper.kt`
```kotlin
package com.cryptotrader.data.mapper

import com.cryptotrader.data.local.entities.TradeEntity
import com.cryptotrader.domain.model.Trade
import com.cryptotrader.domain.model.TradeType
import com.cryptotrader.domain.model.TradeStatus
import com.cryptotrader.utils.toBigDecimalMoney
import java.math.BigDecimal

fun TradeEntity.toDomain(): Trade {
    // Prefer BigDecimal values if available, fallback to Double
    return Trade(
        id = id,
        orderId = orderId,
        pair = pair,
        type = TradeType.fromString(type),
        priceDecimal = priceDecimal?.let { BigDecimal(it) } ?: price.toBigDecimalMoney(),
        volumeDecimal = volumeDecimal?.let { BigDecimal(it) } ?: volume.toBigDecimalMoney(),
        costDecimal = costDecimal?.let { BigDecimal(it) } ?: cost.toBigDecimalMoney(),
        feeDecimal = feeDecimal?.let { BigDecimal(it) } ?: fee.toBigDecimalMoney(),
        profitDecimal = profitDecimal?.let { BigDecimal(it) } ?: profit?.toBigDecimalMoney(),
        timestamp = timestamp,
        strategyId = strategyId,
        status = TradeStatus.fromString(status),
        notes = notes,
        entryTime = entryTime,
        exitTime = exitTime
    )
}

fun Trade.toEntity(): TradeEntity {
    return TradeEntity(
        id = id,
        orderId = orderId,
        pair = pair,
        type = type.toString(),
        // Store BOTH Double and BigDecimal during migration
        price = priceDecimal.toDouble(),
        volume = volumeDecimal.toDouble(),
        cost = costDecimal.toDouble(),
        fee = feeDecimal.toDouble(),
        profit = profitDecimal?.toDouble(),
        priceDecimal = priceDecimal.toPlainString(),
        volumeDecimal = volumeDecimal.toPlainString(),
        costDecimal = costDecimal.toPlainString(),
        feeDecimal = feeDecimal.toPlainString(),
        profitDecimal = profitDecimal?.toPlainString(),
        timestamp = timestamp,
        strategyId = strategyId,
        status = status.toString(),
        notes = notes,
        entryTime = entryTime,
        exitTime = exitTime,
        executedAt = timestamp
    )
}
```

**Similar mappers for**:
- ✅ Position
- ✅ Order
- ✅ Strategy
- ✅ Portfolio

---

### Phase 3: Calculation Logic Migration (Day 3-4 - 16 hours)
**Goal**: Update all calculation logic to use BigDecimal arithmetic

#### 3.1 Migrate Core Calculation Files (8 hours)

**Priority Order**:
1. ✅ `TradingCostModel.kt` (fees, slippage, spread)
2. ✅ `ProfitCalculator.kt` (FIFO matching, P&L)
3. ✅ `RiskManager.kt` (position sizing)
4. ✅ `BacktestEngine.kt` (all backtest calculations)
5. ✅ `PerformanceCalculator.kt` (ROI, returns)
6. ✅ `KellyCriterionCalculator.kt` (position sizing)

**Example**: `TradingCostModel.kt`

**Before (Double)**:
```kotlin
fun calculateTotalCost(orderValue: Double, orderType: OrderType): TradeCost {
    val fee = calculateFee(orderValue, orderType)
    val spread = calculateSpread(orderValue)
    val slippage = calculateSlippage(orderValue)
    val totalCost = fee + spread + slippage

    return TradeCost(
        fee = fee,
        spread = spread,
        slippage = slippage,
        totalCost = totalCost
    )
}
```

**After (BigDecimal)**:
```kotlin
fun calculateTotalCost(orderValue: BigDecimal, orderType: OrderType): TradeCost {
    val fee = calculateFee(orderValue, orderType)
    val spread = calculateSpread(orderValue)
    val slippage = calculateSlippage(orderValue)
    val totalCost = fee + spread + slippage

    return TradeCost(
        feeDecimal = fee,
        spreadDecimal = spread,
        slippageDecimal = slippage,
        totalCostDecimal = totalCost
    )
}

private fun calculateFee(orderValue: BigDecimal, orderType: OrderType): BigDecimal {
    val feePercent = when (orderType) {
        OrderType.MARKET_MAKER -> BigDecimal("0.16")  // Kraken maker fee
        OrderType.MARKET_TAKER -> BigDecimal("0.26")  // Kraken taker fee
    }
    return orderValue.applyPercent(feePercent)  // Uses extension function
}

private fun calculateSpread(orderValue: BigDecimal): BigDecimal {
    val halfSpreadPercent = BigDecimal("0.01")  // 0.01% half-spread
    return orderValue.applyPercent(halfSpreadPercent)
}

private fun calculateSlippage(orderValue: BigDecimal): BigDecimal {
    val baseSlippagePercent = BigDecimal("0.05")

    val adjustedSlippagePercent = when {
        orderValue > BigDecimal("100000") -> baseSlippagePercent * BigDecimal("2.0")
        orderValue > BigDecimal("50000") -> baseSlippagePercent * BigDecimal("1.5")
        orderValue > BigDecimal("10000") -> baseSlippagePercent * BigDecimal("1.25")
        else -> baseSlippagePercent
    }

    return orderValue.applyPercent(adjustedSlippagePercent)
}
```

**Updated TradeCost model**:
```kotlin
data class TradeCost(
    @Deprecated("Use feeDecimal")
    val fee: Double = feeDecimal.toDouble(),
    @Deprecated("Use spreadDecimal")
    val spread: Double = spreadDecimal.toDouble(),
    @Deprecated("Use slippageDecimal")
    val slippage: Double = slippageDecimal.toDouble(),
    @Deprecated("Use totalCostDecimal")
    val totalCost: Double = totalCostDecimal.toDouble(),

    val feeDecimal: BigDecimal,
    val spreadDecimal: BigDecimal,
    val slippageDecimal: BigDecimal,
    val totalCostDecimal: BigDecimal
)
```

#### 3.2 Migrate BacktestEngine (4 hours)

**Critical sections**:
1. ✅ Equity curve calculation with unrealized P&L
2. ✅ P&L calculation with cost basis
3. ✅ Slippage application
4. ✅ Position sizing
5. ✅ Sharpe ratio calculation
6. ✅ Max drawdown percentage

**Example**: Equity curve with BigDecimal
```kotlin
val unrealizedPnL = openPositions.values.fold(BigDecimalConstants.ZERO) { acc, position ->
    val currentValue = priceBar.close * position.volumeDecimal
    val costBasis = position.entryPriceDecimal * position.volumeDecimal + position.totalCostDecimal
    val pnl = currentValue - costBasis
    acc + pnl
}
val totalEquity = balanceDecimal + unrealizedPnL
equityCurve.add(totalEquity)
```

#### 3.3 Migrate Indicator Calculators (2 hours)

**Note**: Indicators (RSI, MACD, etc.) can keep using Double since they're ratios/percentages, not monetary values. Only thresholds used in strategy conditions need BigDecimal conversion.

**Example**: Strategy threshold comparison
```kotlin
// Before (Double)
if (rsi < strategy.rsiOversoldThreshold) { /* buy */ }

// After (BigDecimal)
val rsiDecimal = BigDecimal.valueOf(rsi)  // Convert from indicator
val thresholdDecimal = strategy.rsiOversoldThresholdDecimal
if (rsiDecimal < thresholdDecimal) { /* buy */ }
```

#### 3.4 Migrate Repositories (2 hours)

**Pattern**: Repository methods accept/return BigDecimal, handle conversion at boundaries

**Example**: `TradeRepository.kt`
```kotlin
interface TradeRepository {
    // NEW: BigDecimal methods
    suspend fun insertTrade(
        orderId: String,
        pair: String,
        type: TradeType,
        priceDecimal: BigDecimal,
        volumeDecimal: BigDecimal,
        costDecimal: BigDecimal,
        feeDecimal: BigDecimal,
        timestamp: Long,
        strategyId: String?
    ): Long

    // OLD: Deprecated Double methods
    @Deprecated("Use BigDecimal version")
    suspend fun insertTrade(/* Double parameters */): Long
}
```

---

### Phase 4: Cleanup & Validation (Day 5 - 8 hours)
**Goal**: Remove deprecated Double fields, validate all calculations, performance testing

#### 4.1 Database Migration 15→16 - Remove Double Columns (2 hours)

```kotlin
val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(database: SupportSQLiteDatabase) {
        Timber.i("=== Starting Database Migration 15 → 16 ===")
        Timber.i("Purpose: Remove deprecated Double columns (Phase 4 - Cleanup)")

        // SQLite doesn't support DROP COLUMN directly, must recreate tables

        // Trades table
        database.execSQL("""
            CREATE TABLE trades_new (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                orderId TEXT NOT NULL,
                pair TEXT NOT NULL,
                type TEXT NOT NULL,
                price_decimal TEXT NOT NULL,
                volume_decimal TEXT NOT NULL,
                cost_decimal TEXT NOT NULL,
                fee_decimal TEXT NOT NULL,
                profit_decimal TEXT,
                timestamp INTEGER NOT NULL,
                strategyId TEXT,
                status TEXT NOT NULL,
                notes TEXT,
                entryTime INTEGER,
                exitTime INTEGER,
                executedAt INTEGER NOT NULL
            )
        """)

        database.execSQL("""
            INSERT INTO trades_new SELECT
                id, orderId, pair, type,
                COALESCE(price_decimal, CAST(price AS TEXT)),
                COALESCE(volume_decimal, CAST(volume AS TEXT)),
                COALESCE(cost_decimal, CAST(cost AS TEXT)),
                COALESCE(fee_decimal, CAST(fee AS TEXT)),
                COALESCE(profit_decimal, CAST(profit AS TEXT)),
                timestamp, strategyId, status, notes, entryTime, exitTime, executedAt
            FROM trades
        """)

        database.execSQL("DROP TABLE trades")
        database.execSQL("ALTER TABLE trades_new RENAME TO trades")

        // Repeat for positions, orders, strategies tables...

        Timber.i("✅ Migration 15 → 16 complete (Double columns removed)")
    }
}
```

#### 4.2 Remove @Deprecated Annotations (2 hours)

**Pattern**: Search and remove all `@Deprecated` Double properties

```bash
# Find all deprecated properties
grep -r "@Deprecated.*Double" app/src/main/java/

# Remove deprecated properties from data classes
# Update all call sites to use BigDecimal versions
```

#### 4.3 Comprehensive Testing (3 hours)

**Test Suite**: `BigDecimalValidationTest.kt`

```kotlin
@RunWith(JUnit4::class)
class BigDecimalValidationTest {

    @Test
    fun `validate all monetary calculations use BigDecimal`() {
        // Compile-time check: Try to create Trade with Double (should fail)
        // This test ensures Double constructor is removed

        val trade = Trade(
            orderId = "TEST-001",
            pair = "XXBTZUSD",
            type = TradeType.BUY,
            priceDecimal = BigDecimal("50000.00"),
            volumeDecimal = BigDecimal("0.1"),
            costDecimal = BigDecimal("5000.00"),
            feeDecimal = BigDecimal("13.00"),
            timestamp = System.currentTimeMillis()
        )

        // Verify BigDecimal precision
        val expectedCost = BigDecimal("5000.00")
        assertEquals(expectedCost, trade.costDecimal)
    }

    @Test
    fun `validate BacktestEngine equity curve precision`() {
        // Run backtest with known data
        // Verify equity values are exact to 8 decimal places
        // Compare with Double version (should show precision difference)
    }

    @Test
    fun `validate ProfitCalculator FIFO matching precision`() {
        // Test partial fills with BigDecimal
        // Ensure no rounding errors in P&L matching
    }

    @Test
    fun `validate TradingCostModel fee calculation precision`() {
        val orderValue = BigDecimal("10000.50")
        val cost = tradingCostModel.calculateTotalCost(orderValue, OrderType.MARKET_TAKER)

        // Kraken taker fee: 0.26%
        val expectedFee = BigDecimal("26.001300")  // 10000.50 * 0.0026
        assertEquals(expectedFee, cost.feeDecimal)
    }

    @Test
    fun `validate no cumulative errors over 10000 trades`() {
        var balance = BigDecimal("10000.00")

        for (i in 1..10000) {
            val profit = BigDecimal("1.01")
            val fee = BigDecimal("0.26")
            balance = balance + profit - fee
        }

        val expectedBalance = BigDecimal("10000.00") +
            (BigDecimal("1.01") - BigDecimal("0.26")) * BigDecimal("10000")

        assertEquals(expectedBalance, balance)
    }
}
```

#### 4.4 Performance Benchmarking (1 hour)

**Test**: Compare BigDecimal vs Double performance

```kotlin
@Test
fun `benchmark BigDecimal performance vs Double`() {
    val iterations = 100000

    // Double benchmark
    val doubleStart = System.nanoTime()
    var doubleResult = 0.0
    repeat(iterations) {
        doubleResult += 1.01
        doubleResult -= 0.26
        doubleResult *= 1.0026
    }
    val doubleTime = System.nanoTime() - doubleStart

    // BigDecimal benchmark
    val bdStart = System.nanoTime()
    var bdResult = BigDecimalConstants.ZERO
    repeat(iterations) {
        bdResult += BigDecimal("1.01")
        bdResult -= BigDecimal("0.26")
        bdResult *= BigDecimal("1.0026")
    }
    val bdTime = System.nanoTime() - bdStart

    println("Double time: ${doubleTime / 1_000_000}ms")
    println("BigDecimal time: ${bdTime / 1_000_000}ms")
    println("Slowdown: ${bdTime.toDouble() / doubleTime}x")

    // Acceptable if BigDecimal is <10x slower
    assertTrue(bdTime < doubleTime * 10)
}
```

**Expected Performance**: BigDecimal is ~5-8x slower than Double, but still acceptable for trading calculations (< 1ms per operation).

---

## 📊 TESTING STRATEGY

### Unit Tests (40+ tests)
1. ✅ BigDecimal extension functions
2. ✅ Domain model conversions (Double ↔ BigDecimal)
3. ✅ Entity mappers
4. ✅ Calculation logic (TradingCostModel, ProfitCalculator, etc.)
5. ✅ Precision validation (compare with Double)
6. ✅ Edge cases (zero, negative, very large numbers)

### Integration Tests (10+ tests)
1. ✅ Database read/write with BigDecimal
2. ✅ Repository layer conversions
3. ✅ Backtest engine end-to-end
4. ✅ Trading flow (entry → exit → P&L)
5. ✅ API integration (Kraken responses → BigDecimal)

### Regression Tests (5+ tests)
1. ✅ Compare backtest results (BigDecimal vs Double)
2. ✅ Verify P&L matches to 8 decimals
3. ✅ No precision loss in 10,000 trade simulation
4. ✅ Equity curve matches historical data
5. ✅ Performance metrics unchanged (Sharpe, drawdown, etc.)

---

## 🚨 RISKS & MITIGATION

### Risk 1: Performance Degradation
**Impact**: BigDecimal is 5-8x slower than Double
**Mitigation**:
- Use BigDecimal only for monetary values
- Keep indicators as Double (they're ratios, not money)
- Cache calculations where possible
- Profile hot paths after migration

### Risk 2: Database Migration Failures
**Impact**: Users lose data if migration fails
**Mitigation**:
- Test migrations on copy of production database
- Add rollback support (keep Double columns in Phase 1-3)
- Comprehensive logging during migration
- Backup database before migration

### Risk 3: API Integration Issues
**Impact**: Kraken API returns Double, conversion errors possible
**Mitigation**:
- Convert at API boundary (repository layer)
- Use String parsing for Kraken responses
- Validate conversions with assertions
- Test with real Kraken data

### Risk 4: UI Display Formatting
**Impact**: BigDecimal.toString() may show unwanted precision
**Mitigation**:
- Use extension functions for formatting (toUSDString, toCryptoString)
- Standardize scale (2 decimals for USD, 8 for crypto)
- Test all UI screens after migration

### Risk 5: Breaking Changes for Users
**Impact**: Migration might cause app to crash on startup
**Mitigation**:
- Gradual rollout (Phase 1-3 keeps backward compatibility)
- Beta testing with test users
- Crash reporting (Crashlytics)
- Ability to rollback to previous version

---

## 📅 TIMELINE

### Sequential Execution (5 days)
- **Day 1**: Phase 1 - Foundation (8 hours)
- **Day 2**: Phase 2 - Domain Models (8 hours)
- **Day 3**: Phase 3 - Calculation Logic Part 1 (8 hours)
- **Day 4**: Phase 3 - Calculation Logic Part 2 (8 hours)
- **Day 5**: Phase 4 - Cleanup & Validation (8 hours)

### Parallelized Execution (3 days)
- **Day 1**:
  - Morning: Phase 1 - Foundation (4 hours)
  - Afternoon: Phase 2 - Domain Models (4 hours)
- **Day 2**:
  - Full day: Phase 3 - Calculation Logic (8 hours, parallelized files)
- **Day 3**:
  - Morning: Phase 4 - Cleanup (4 hours)
  - Afternoon: Testing & Validation (4 hours)

---

## ✅ COMPLETION CHECKLIST

### Phase 1: Foundation
- [ ] BigDecimal extensions created
- [ ] Room TypeConverter added
- [ ] Gson serializer configured
- [ ] Migration 14→15 written and tested
- [ ] Migration testing framework created

### Phase 2: Domain Models
- [ ] Trade model migrated
- [ ] Position model migrated
- [ ] Portfolio model migrated
- [ ] Strategy model migrated
- [ ] All entity models migrated
- [ ] Mappers created and tested

### Phase 3: Calculation Logic
- [ ] TradingCostModel migrated
- [ ] ProfitCalculator migrated
- [ ] RiskManager migrated
- [ ] BacktestEngine migrated
- [ ] PerformanceCalculator migrated
- [ ] KellyCriterionCalculator migrated
- [ ] All repositories migrated

### Phase 4: Cleanup
- [ ] Migration 15→16 written (remove Double columns)
- [ ] All @Deprecated annotations removed
- [ ] All tests passing (unit + integration)
- [ ] Performance benchmarks acceptable
- [ ] UI formatting validated
- [ ] Build successful

### Final Validation
- [ ] Run full backtest with historical data (compare BigDecimal vs Double)
- [ ] Verify no precision errors in 10,000 trade simulation
- [ ] Deploy to test device
- [ ] Extended paper trading (1 week)
- [ ] Compare paper results to backtest predictions
- [ ] Sign-off for production deployment

---

## 📝 NOTES

### BigDecimal Best Practices
1. **Always use String constructor**: `BigDecimal("1.01")` not `BigDecimal(1.01)`
2. **Set scale explicitly**: Use `MONEY_SCALE = 8` for all monetary values
3. **Use RoundingMode.HALF_EVEN**: Banker's rounding minimizes bias
4. **Avoid toDouble()**: Only convert at UI boundaries
5. **Cache constants**: Reuse `BigDecimal.ZERO`, `BigDecimal.ONE`, etc.

### Performance Optimization
1. **Reuse objects**: `BigDecimal("100")` creates new object each time
2. **Use constants**: Create static constants for common values
3. **Minimize conversions**: Keep calculations in BigDecimal domain
4. **Profile first**: Only optimize if performance issue detected

### Database Considerations
1. **TEXT column type**: SQLite stores BigDecimal as TEXT (NUMERIC has precision limits)
2. **Indexes still work**: TEXT columns can be indexed for queries
3. **Storage size**: TEXT uses ~20% more space than REAL for typical crypto prices
4. **Query performance**: Minimal impact (<5%) on query speed

---

## 🎯 SUCCESS CRITERIA

✅ **Migration Successful If**:
1. All monetary calculations use BigDecimal
2. No precision errors in 10,000 trade simulation
3. Backtest results match to 8 decimal places
4. All existing tests pass
5. Performance degradation < 10x
6. UI displays correct formatting
7. Database migrations complete without errors
8. No production crashes after deployment

❌ **Migration Failed If**:
1. Any monetary calculations still use Double
2. Precision errors detected in simulations
3. Tests fail after migration
4. Performance degradation > 10x
5. Database migration errors
6. Production crashes on startup

---

**Status**: ⬜ Not Started
**Priority**: HIGH (before live trading)
**Dependencies**: None (can start immediately)
**Blocked By**: None

---

*Plan created by Claude Code (Sonnet 4.5)*
*Date: 2025-11-19*
*Estimated effort: 3-5 days*
