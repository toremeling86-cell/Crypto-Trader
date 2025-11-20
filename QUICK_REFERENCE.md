# Real-Time P&L Tracking - Quick Reference

## New Methods in PositionRepository

### 1. getPositionWithCurrentPrice()
**Purpose:** Get single position with live price and P&L

**Signature:**
```kotlin
fun getPositionWithCurrentPrice(positionId: String): Flow<PositionWithPrice>
```

**Returns:** Flow emitting PositionWithPrice updates

**Example:**
```kotlin
viewModelScope.launch {
    positionRepository.getPositionWithCurrentPrice("pos-123")
        .collect { posWithPrice ->
            println("Price: ${posWithPrice.currentPrice}")
            println("P&L: ${posWithPrice.unrealizedPnL}")
        }
}
```

---

### 2. updateUnrealizedPnL()
**Purpose:** Update P&L for multiple positions with new prices

**Signature:**
```kotlin
suspend fun updateUnrealizedPnL(priceUpdates: Map<String, BigDecimal>)
```

**Parameters:** Map of pair -> price

**Example:**
```kotlin
val prices = mapOf(
    "XBTUSD" to BigDecimal("45000.00"),
    "ETHUSD" to BigDecimal("2500.00")
)
positionRepository.updateUnrealizedPnL(prices)
```

---

### 3. getOpenPositionsWithPrices()
**Purpose:** Get all open positions with current prices (live dashboard)

**Signature:**
```kotlin
fun getOpenPositionsWithPrices(): Flow<List<PositionWithPrice>>
```

**Returns:** Flow emitting list of PositionWithPrice

**Example:**
```kotlin
viewModelScope.launch {
    positionRepository.getOpenPositionsWithPrices()
        .collect { positions ->
            _state.value = positions
        }
}
```

---

### 4. observePositionPnL()
**Purpose:** Real-time P&L stream for high-frequency updates

**Signature:**
```kotlin
fun observePositionPnL(positionId: String): Flow<PositionPnL>
```

**Returns:** Flow emitting PositionPnL (optimized P&L snapshots)

**Example:**
```kotlin
viewModelScope.launch {
    positionRepository.observePositionPnL("pos-123")
        .collect { pnl ->
            if (pnl.isGain()) showProfit(pnl.unrealizedPnL)
            else if (pnl.isLoss()) showLoss(pnl.unrealizedPnL)
        }
}
```

---

### 5. updatePrice()
**Purpose:** Push new price into cache and notify subscribers

**Signature:**
```kotlin
fun updatePrice(pair: String, price: BigDecimal)
```

**Example:**
```kotlin
// From WebSocket handler
onWebSocketMessage = { pair, price ->
    positionRepository.updatePrice(pair, BigDecimal(price))
}
```

---

### 6. syncOpenPositionsWithPrices()
**Purpose:** Fetch current prices and update all positions

**Signature:**
```kotlin
suspend fun syncOpenPositionsWithPrices(): Result<List<PositionWithPrice>>
```

**Returns:** Result with list of synced positions

**Example:**
```kotlin
viewModelScope.launch {
    positionRepository.syncOpenPositionsWithPrices()
        .onSuccess { positions ->
            Timber.d("Synced ${positions.size} positions")
        }
        .onFailure { error ->
            Timber.e(error, "Sync failed")
        }
}
```

---

## Data Classes

### PositionWithPrice
```kotlin
data class PositionWithPrice(
    val position: Position,
    val currentPrice: BigDecimal?,
    val unrealizedPnL: BigDecimal?,
    val unrealizedPnLPercent: Double?,
    val lastPriceUpdate: Long?
)
```

**Utility Methods:**
- `isProfit()` - P&L > 0
- `isLoss()` - P&L < 0
- `isNeutral()` - P&L == 0
- `getPair()` - Get trading pair
- `getSide()` - Get LONG/SHORT
- `isAtRisk(price)` - Stop-loss triggered
- `isProfitTargetReached(price)` - Take-profit triggered

---

### PositionPnL
```kotlin
data class PositionPnL(
    val positionId: String,
    val unrealizedPnL: BigDecimal,
    val unrealizedPnLPercent: Double,
    val currentPrice: BigDecimal,
    val timestamp: Long
)
```

**Utility Methods:**
- `isGain()` - P&L > 0
- `isLoss()` - P&L < 0
- `hasImproved(prev)` - P&L > previous
- `hasWorsened(prev)` - P&L < previous

---

## Common Patterns

### Real-Time Dashboard
```kotlin
fun collectPositions() {
    viewModelScope.launch {
        positionRepository.getOpenPositionsWithPrices()
            .collect { positions ->
                _dashboardState.value = positions.map { pos ->
                    PositionUI(
                        pair = pos.getPair(),
                        side = pos.getSide().toString(),
                        pnl = pos.unrealizedPnL?.toPlainString() ?: "N/A",
                        color = when {
                            pos.isProfit() -> Green
                            pos.isLoss() -> Red
                            else -> Gray
                        }
                    )
                }
            }
    }
}
```

### Position Alert
```kotlin
fun monitorPosition(positionId: String) {
    viewModelScope.launch {
        positionRepository.observePositionPnL(positionId)
            .filter { it.unrealizedPnL < BigDecimal("-1000") }
            .collect { pnl ->
                showAlert("Large loss: ${pnl.unrealizedPnL}")
            }
    }
}
```

### WebSocket Integration
```kotlin
private fun setupWebSocket() {
    webSocketClient.onMessage = { pair, price ->
        positionRepository.updatePrice(pair, BigDecimal(price))
    }
}
```

### Periodic Sync
```kotlin
fun startPeriodicSync() {
    viewModelScope.launch {
        while (isActive) {
            positionRepository.syncOpenPositionsWithPrices()
            delay(30_000) // 30 seconds
        }
    }
}
```

---

## Error Handling

### With Try-Catch
```kotlin
viewModelScope.launch {
    try {
        val result = positionRepository.syncOpenPositionsWithPrices()
        result.onSuccess { positions ->
            // Handle success
        }
    } catch (e: Exception) {
        Timber.e(e, "Error syncing positions")
        showError("Failed to sync positions")
    }
}
```

### With Flow Error Handling
```kotlin
viewModelScope.launch {
    positionRepository.getOpenPositionsWithPrices()
        .catch { e ->
            Timber.e(e, "Error in position flow")
            showError("Failed to get positions")
        }
        .collect { positions ->
            // Handle success
        }
}
```

---

## Testing

### Mock Setup
```kotlin
@Test
fun testPositionWithPrice() = runTest {
    val position = createTestPosition("XBTUSD")
    val price = BigDecimal("45000.00")

    val result = positionRepository.getPositionWithCurrentPrice("pos-123")
        .first()

    assertEquals(price, result.currentPrice)
}
```

---

## DAO New Methods

```kotlin
// Get position as Flow
fun getPositionByIdFlow(id: String): Flow<PositionEntity?>

// Update P&L with BigDecimal
suspend fun updateUnrealizedPnLDecimal(
    positionId: String,
    unrealizedPnLDecimal: BigDecimal,
    unrealizedPnLPercentDecimal: BigDecimal,
    currentPrice: BigDecimal,
    lastUpdated: Long
)

// Get open positions count
fun getOpenPositionsCount(): Flow<Int>

// Get positions by pair
fun getPositionsByPairFlow(pair: String): Flow<List<PositionEntity>>
```

---

## Performance Tips

1. **Use getOpenPositionsWithPrices()** for dashboards (combines all prices)
2. **Use observePositionPnL()** for alerts (single position, high frequency)
3. **Use updatePrice()** for WebSocket updates (non-blocking)
4. **Use syncOpenPositionsWithPrices()** for periodic updates (batch operation)

---

## File Locations

- Repository: `data/repository/PositionRepository.kt`
- Models: `domain/model/PositionWithPrice.kt`
- DAO: `data/local/dao/PositionDao.kt`
