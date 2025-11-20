# CryptoTrader Integration Testing Guide

This document provides guidance on extending, maintaining, and working with the comprehensive trading workflow integration test suite.

## File Organization

```
app/src/test/java/com/cryptotrader/integration/
├── TradingWorkflowTest.kt       # Main test suite (21 tests)
├── TradingTestHelpers.kt        # Builders, fixtures, utilities
└── README.md                     # This file

app/src/test/java/com/cryptotrader/
└── domain/
    ├── trading/                 # Trading logic unit tests
    ├── indicators/              # Indicator calculation tests
    └── backtesting/             # Backtest scenario tests
```

## Running Tests

### Run all integration tests
```bash
./gradlew test --tests "com.cryptotrader.integration.*"
```

### Run only trading workflow tests
```bash
./gradlew test --tests "com.cryptotrader.integration.TradingWorkflowTest"
```

### Run specific test
```bash
./gradlew test --tests "com.cryptotrader.integration.TradingWorkflowTest.complete*"
```

### Run with verbose output
```bash
./gradlew test --tests "com.cryptotrader.integration.TradingWorkflowTest" -i
```

### Generate coverage report
```bash
./gradlew testDebugUnitTestCoverage
# Report: app/build/reports/coverage/androidTest/debug/index.html
```

### Run in CI/CD
```bash
./gradlew test --offline --stacktrace
```

---

## Adding New Tests

### Pattern 1: Simple Workflow Test

```kotlin
@Test
fun `new feature - expected behavior`() = runTest {
    // ARRANGE: Setup test data
    val builder = OrderBuilder()
        .withPair("XXBTZUSD")
        .asBuyOrder()
    val order = builder.build()

    // ACT: Execute business logic
    val result = orderRepository.placeOrder(
        pair = order.pair,
        type = order.type,
        orderType = order.orderType,
        volume = order.quantity,
        price = order.price
    )

    // ASSERT: Verify results
    assertThat(result.isSuccess).isTrue()
    val savedOrder = orderDao.getOrderById(result.getOrNull()!!.id)
    assertThat(savedOrder).isNotNull()
    assertThat(savedOrder!!.status).isEqualTo("PENDING")
}
```

### Pattern 2: Error Handling Test

```kotlin
@Test
fun `error handling - specific error scenario`() = runTest {
    // ARRANGE: Setup error condition
    mockKrakenApi.setNetworkError(true)

    // ACT: Try operation that should fail
    val result = orderRepository.placeOrder(
        pair = "XXBTZUSD",
        type = TradeType.BUY,
        orderType = OrderType.MARKET,
        volume = 0.1,
        price = 40000.0
    )

    // ASSERT: Verify error handling
    assertThat(result.isFailure).isTrue()
    assertThat(result.exceptionOrNull()).isNotNull()
}
```

### Pattern 3: Property-Based Test

```kotlin
@Test
fun `property - mathematical invariant`() = runTest {
    // Test 100 random combinations
    repeat(100) {
        val buyPrice = BigDecimal(Random.nextDouble(100.0, 50000.0))
        val sellPrice = BigDecimal(Random.nextDouble(100.0, 50000.0))
        val quantity = BigDecimal(Random.nextDouble(0.001, 10.0))

        // Verify property always holds
        val pnl = (sellPrice - buyPrice) * quantity
        assertThat(pnl.toDouble()).isFinite()
    }
}
```

### Pattern 4: Integration Test with Multiple Steps

```kotlin
@Test
fun `multi-step workflow - complex scenario`() = runTest {
    // Step 1: Place first order
    val order1 = orderRepository.placeOrder(...)
    assertThat(order1.isSuccess).isTrue()

    // Step 2: Verify database state
    val savedOrder1 = orderDao.getOrderById(order1.getOrNull()!!.id)
    assertThat(savedOrder1).isNotNull()

    // Step 3: Take action based on step 1
    val order2 = orderRepository.placeOrder(...)
    assertThat(order2.isSuccess).isTrue()

    // Step 4: Verify final state
    val allOrders = orderDao.getActiveOrders()
    // ... verify combined state
}
```

---

## Using Test Builders

### OrderBuilder Examples

```kotlin
// Buy order (market)
OrderBuilder()
    .withPair("XXBTZUSD")
    .asBuyOrder()
    .asMarketOrder()
    .withPrice(40000.0)
    .withQuantity(0.1)
    .filled() // Shorthand for filled state
    .build()

// Sell order (limit, rejected)
OrderBuilder()
    .withPair("XETHZUSD")
    .asSellOrder()
    .asLimitOrder()
    .withPrice(3000.0)
    .withQuantity(1.0)
    .rejected("Insufficient liquidity")
    .build()

// Stop loss order
OrderBuilder()
    .withPair("XXBTZUSD")
    .asSellOrder()
    .asStopLoss()
    .withStopPrice(38000.0)
    .withQuantity(0.5)
    .open()
    .build()
```

### PositionBuilder Examples

```kotlin
// Long position with stop loss and take profit
PositionBuilder()
    .withPair("XXBTZUSD")
    .asLong()
    .withQuantity(0.5)
    .withEntryPrice(40000.0)
    .withStopLoss(0.95) // 5% below entry
    .withTakeProfit(1.10) // 10% above entry
    .open()
    .build()

// Closed short position with profit
PositionBuilder()
    .withPair("XETHZUSD")
    .asShort()
    .withQuantity(2.0)
    .withEntryPrice(3000.0)
    .withExitPrice(2900.0) // Profit
    .closed()
    .withCloseReason("TAKE_PROFIT")
    .withRealizedPnL(200.0) // 2.0 * (3000 - 2900)
    .build()
```

---

## Using Test Helpers

### Calculation Helpers

```kotlin
// Calculate P&L
val pnl = TradingCalculations.calculatePnL(
    entryPrice = BigDecimal("40000"),
    exitPrice = BigDecimal("45000"),
    quantity = BigDecimal("0.1"),
    fees = BigDecimal("115"),
    isLong = true
)
// Result: 500 - 115 = 385

// Calculate average fill price (FIFO)
val avgPrice = TradingCalculations.calculateAverageFillPrice(
    listOf(
        BigDecimal("40000") to BigDecimal("0.1"),
        BigDecimal("41000") to BigDecimal("0.2")
    )
)
// Result: (40000 * 0.1 + 41000 * 0.2) / 0.3 = 40666.67

// Calculate max position size
val maxSize = TradingCalculations.calculateMaxPositionSize(
    balance = BigDecimal("10000"),
    riskPercent = BigDecimal("2") // 2% risk
)
// Result: 200 USD
```

### Assertion Helpers

```kotlin
// Assert prices are close
TradingAssertions.assertPriceEquals(
    expected = BigDecimal("40000.00"),
    actual = BigDecimal("40000.01"),
    tolerance = BigDecimal("0.01")
)

// Assert P&L is correct
TradingAssertions.assertPnLEquals(
    position = myPosition,
    expectedPnL = BigDecimal("385"),
    tolerance = BigDecimal("0.01")
)

// Assert order was filled correctly
TradingAssertions.assertOrderFilled(
    order = myOrder,
    expectedQuantity = 0.1,
    expectedPrice = 40000.0
)
```

### Test Data Generation

```kotlin
// Generate realistic prices
val prices = TestDataGenerator.generatePriceSeries(
    startPrice = 40000.0,
    trend = 500.0, // Upward trend
    steps = 20,
    volatility = 2.0 // 2% per step
)

// Generate random price
val price = TestDataGenerator.generatePrice("XXBTZUSD")

// Generate random quantity
val qty = TestDataGenerator.generateQuantity()

// Generate candle data
val candle = TestDataGenerator.generateCandle(
    basePrice = 40000.0,
    volatilityPercent = 3.0
)
```

---

## Common Test Scenarios

### Scenario 1: Complete Trade Lifecycle

```kotlin
@Test
fun `complete trade - from signal to closed position`() = runTest {
    // 1. Place buy order
    val buyResult = orderRepository.placeOrder(
        pair = "XXBTZUSD",
        type = TradeType.BUY,
        orderType = OrderType.MARKET,
        volume = 0.1,
        price = 40000.0
    )
    assertThat(buyResult.isSuccess).isTrue()

    // 2. Simulate order fill
    val buyOrder = orderDao.getOrderById(buyResult.getOrNull()!!.id)!!
    orderDao.markOrderFilled(
        id = buyOrder.id,
        filledAt = System.currentTimeMillis(),
        filledQuantity = 0.1,
        averageFillPrice = 40000.0,
        fee = 50.0
    )

    // 3. Open position
    val posResult = positionRepository.openPosition(
        pair = "XXBTZUSD",
        side = PositionSide.LONG,
        entryPrice = 40000.0,
        quantity = 0.1,
        strategyId = "test-strategy"
    )
    val position = posResult.getOrNull()!!

    // 4. Update price
    positionRepository.updatePositionPrice(position.id, 41000.0)

    // 5. Close position
    val closeResult = positionRepository.closePosition(
        positionId = position.id,
        exitPrice = 45000.0
    )
    val closed = closeResult.getOrNull()!!

    // 6. Verify final state
    assertThat(closed.status).isEqualTo(PositionStatus.CLOSED)
    assertThat(closed.realizedPnLDecimal).isGreaterThan(BigDecimal.ZERO)
}
```

### Scenario 2: Error Recovery

```kotlin
@Test
fun `error recovery - order rejected then retry`() = runTest {
    // 1. Configure API to reject first order
    mockKrakenApi.setRejectNextOrder(true, "Insufficient balance")

    // 2. Try to place order
    val result1 = orderRepository.placeOrder(
        pair = "XXBTZUSD",
        type = TradeType.BUY,
        orderType = OrderType.MARKET,
        volume = 0.1,
        price = 40000.0
    )
    assertThat(result1.isFailure).isTrue()

    // 3. Reset mock for retry
    mockKrakenApi.setRejectNextOrder(false)

    // 4. Retry order
    val result2 = orderRepository.placeOrder(
        pair = "XXBTZUSD",
        type = TradeType.BUY,
        orderType = OrderType.MARKET,
        volume = 0.1,
        price = 40000.0
    )
    assertThat(result2.isSuccess).isTrue()
}
```

### Scenario 3: Multi-Position Management

```kotlin
@Test
fun `multi-position management - track independent P&L`() = runTest {
    // Open position on BTC
    val btcPos = positionRepository.openPosition(
        pair = "XXBTZUSD",
        side = PositionSide.LONG,
        entryPrice = 40000.0,
        quantity = 0.1,
        strategyId = "test"
    ).getOrNull()!!

    // Open position on ETH
    val ethPos = positionRepository.openPosition(
        pair = "XETHZUSD",
        side = PositionSide.LONG,
        entryPrice = 2500.0,
        quantity = 1.0,
        strategyId = "test"
    ).getOrNull()!!

    // Update BTC price
    positionRepository.updatePositionPrice(btcPos.id, 42000.0)

    // Update ETH price
    positionRepository.updatePositionPrice(ethPos.id, 2600.0)

    // Verify independent P&L
    val btcUpdated = positionDao.getPositionById(btcPos.id)!!
    val ethUpdated = positionDao.getPositionById(ethPos.id)!!

    assertThat(btcUpdated.unrealizedPnL).isGreaterThan(0.0)
    assertThat(ethUpdated.unrealizedPnL).isGreaterThan(0.0)
    assertThat(btcUpdated.unrealizedPnL).isNotEqualTo(ethUpdated.unrealizedPnL)
}
```

---

## Debugging Failed Tests

### Enable Logging

```kotlin
@Before
fun setup() {
    ShadowLog.stream = System.out // Timber logs to stdout
}
```

### Inspect Database State

```kotlin
@Test
fun `debug test`() = runTest {
    // ... test code ...

    // Inspect database state
    val allOrders = orderDao.getActiveOrders()
    allOrders.collect { orders ->
        orders.forEach { order ->
            println("Order: ${order.id}, Status: ${order.status}, Price: ${order.price}")
        }
    }

    val allPositions = positionDao.getOpenPositions()
    allPositions.collect { positions ->
        positions.forEach { pos ->
            println("Position: ${pos.id}, Side: ${pos.type}, PnL: ${pos.unrealizedPnL}")
        }
    }
}
```

### Use Debugger Breakpoints

```kotlin
@Test
fun `debug with breakpoint`() = runTest {
    val result = orderRepository.placeOrder(
        pair = "XXBTZUSD",
        type = TradeType.BUY,
        orderType = OrderType.MARKET,
        volume = 0.1,
        price = 40000.0
    )

    // Set breakpoint here to inspect result
    val order = result.getOrNull()
    println(order) // Inspect in debugger
}
```

---

## Performance Testing

### Measure Test Execution Time

```kotlin
@Test
fun `performance - order placement`() = runTest {
    val startTime = System.currentTimeMillis()

    repeat(100) {
        orderRepository.placeOrder(
            pair = "XXBTZUSD",
            type = TradeType.BUY,
            orderType = OrderType.MARKET,
            volume = 0.01,
            price = 40000.0
        )
    }

    val elapsed = System.currentTimeMillis() - startTime
    println("100 orders in $elapsed ms (${elapsed / 100.0} ms/order)")

    assertThat(elapsed).isLessThan(1000) // Should be < 1 second
}
```

### Profile with Benchmarking

```kotlin
@Test
fun `benchmark - position P&L calculation`() = runTest {
    val position = PositionBuilder().build()

    val startTime = System.nanoTime()

    repeat(10000) {
        position.calculateUnrealizedPnL(41000.0)
    }

    val nanos = System.nanoTime() - startTime
    val micros = nanos / 1000.0
    println("10k P&L calculations: ${micros / 1000.0} ms (${micros / 10000.0} µs per calc)")
}
```

---

## Continuous Integration

### GitHub Actions Example

```yaml
name: Run Integration Tests

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3

      - name: Set up JDK
        uses: actions/setup-java@v3
        with:
          java-version: '17'

      - name: Run trading workflow tests
        run: ./gradlew test --tests "com.cryptotrader.integration.TradingWorkflowTest" --stacktrace

      - name: Generate coverage report
        run: ./gradlew testDebugUnitTestCoverage

      - name: Upload coverage to Codecov
        uses: codecov/codecov-action@v3
        with:
          files: ./app/build/outputs/unit_test_code_coverage/debug/report.xml
```

### Jenkins Pipeline Example

```groovy
pipeline {
    agent any

    stages {
        stage('Test') {
            steps {
                sh './gradlew test --tests "com.cryptotrader.integration.TradingWorkflowTest" --stacktrace'
            }
        }

        stage('Coverage') {
            steps {
                sh './gradlew testDebugUnitTestCoverage'
                publishHTML(target: [
                    reportDir: 'app/build/reports/coverage',
                    reportFiles: 'index.html',
                    reportName: 'Test Coverage'
                ])
            }
        }
    }

    post {
        always {
            junit 'app/build/test-results/**/*.xml'
        }
    }
}
```

---

## Extending the Test Suite

### Adding Tests for New Features

1. **Identify business logic flow**
   ```
   User Action → Data Validation → Repository Call → Database Update → Result
   ```

2. **Create test following pattern**
   ```kotlin
   @Test
   fun `new feature - expected behavior`() = runTest {
       // ARRANGE
       // ACT
       // ASSERT
   }
   ```

3. **Use builders for test data**
   ```kotlin
   val order = OrderBuilder().withPair(...).build()
   ```

4. **Verify database state**
   ```kotlin
   val saved = orderDao.getOrderById(id)
   assertThat(saved).isNotNull()
   ```

5. **Test error cases**
   ```kotlin
   mockKrakenApi.setNetworkError(true)
   val result = orderRepository.placeOrder(...)
   assertThat(result.isFailure).isTrue()
   ```

### Adding Property-Based Tests

1. **Identify mathematical property**
   ```
   Property: For any price pair and quantity, P&L = (sell - buy) * qty
   ```

2. **Generate random test cases**
   ```kotlin
   repeat(100) {
       val buy = BigDecimal(Random.nextDouble(...))
       val sell = BigDecimal(Random.nextDouble(...))
       // Test property
   }
   ```

3. **Verify invariant holds**
   ```kotlin
   val pnl = calculatePnL(buy, sell, qty)
   assertThat(pnl).isEqualTo((sell - buy) * qty)
   ```

---

## Test Maintenance Checklist

- [ ] All tests pass locally
- [ ] No test interdependencies (order independent)
- [ ] Database cleaned between tests
- [ ] Mocks reset after each test
- [ ] Realistic test data (prices, quantities)
- [ ] Error cases covered
- [ ] Edge cases handled
- [ ] BigDecimal used for money
- [ ] Clear test names
- [ ] Documentation updated
- [ ] Performance acceptable (<3 seconds)
- [ ] Coverage maintained (>90%)

---

## Common Issues and Solutions

### Issue: Test Fails Intermittently

**Cause:** Race condition, timing dependency, or shared state

**Solution:**
```kotlin
// Don't use random timestamps
val now = System.currentTimeMillis()
val order = OrderBuilder().withPlacedAt(now).build()

// Reset mocks between tests
@After
fun teardown() {
    unmockkAll()
}

// Use latch for synchronization
val latch = CountDownLatch(1)
// ... code ...
latch.await(5, TimeUnit.SECONDS)
```

### Issue: Database State Corrupted

**Cause:** Test didn't clean up properly, or used wrong isolation level

**Solution:**
```kotlin
@After
fun teardown() {
    database.close() // Fresh database each test
    unmockkAll()
}
```

### Issue: Mock Not Being Called

**Cause:** Wrong mock setup, or not imported correctly

**Solution:**
```kotlin
// Verify mock was called
coVerify { mockKrakenApi.addOrder(...) }

// Check mock configuration
mockRiskManager.apply {
    coEvery { canExecuteTrade(any(), any()) } returns true
}
```

### Issue: BigDecimal Precision Lost

**Cause:** Converting to/from Double

**Solution:**
```kotlin
// Wrong
val price = order.price.toDouble() // Loses precision

// Right
val priceDecimal = order.price.toBigDecimalMoney()
```

---

## Resources

- [Google Truth Documentation](https://truth.dev/)
- [MockK Documentation](https://mockk.io/)
- [Room Testing Guide](https://developer.android.com/training/data-storage/room/testing-db)
- [Kotlin Coroutines Testing](https://kotlin.github.io/kotlinx.coroutines/kotlinx-coroutines-test/)
- [Robolectric](http://robolectric.org/)

---

## Contact & Support

For questions about the test suite:
1. Check this guide first
2. Review existing tests for examples
3. Use builders and helpers for common patterns
4. Debug using breakpoints and logging

Happy testing! ✓
