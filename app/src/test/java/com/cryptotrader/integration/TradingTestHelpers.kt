package com.cryptotrader.integration

import com.cryptotrader.domain.model.*
import com.cryptotrader.utils.toBigDecimalMoney
import java.math.BigDecimal
import java.util.UUID

/**
 * Helper utilities and builders for trading workflow tests
 *
 * Provides:
 * - Test data builders (fluent API)
 * - Common test fixtures
 * - Assertion helpers
 * - Calculation helpers
 */

// ==================== Test Data Builders ====================

/**
 * Fluent builder for creating test orders
 *
 * Usage:
 * ```
 * OrderBuilder()
 *     .withPair("XXBTZUSD")
 *     .withType(TradeType.BUY)
 *     .withPrice(40000.0)
 *     .build()
 * ```
 */
class OrderBuilder {
    private var id: String = UUID.randomUUID().toString()
    private var positionId: String? = null
    private var pair: String = "XXBTZUSD"
    private var type: TradeType = TradeType.BUY
    private var orderType: OrderType = OrderType.MARKET
    private var quantity: Double = 1.0
    private var price: Double? = null
    private var stopPrice: Double? = null
    private var krakenOrderId: String? = UUID.randomUUID().toString()
    private var status: OrderStatus = OrderStatus.PENDING
    private var placedAt: Long = System.currentTimeMillis()
    private var filledAt: Long? = null
    private var cancelledAt: Long? = null
    private var filledQuantity: Double = 0.0
    private var averageFillPrice: Double? = null
    private var fee: Double? = null
    private var errorMessage: String? = null

    fun withId(id: String) = apply { this.id = id }
    fun withPositionId(id: String?) = apply { this.positionId = id }
    fun withPair(pair: String) = apply { this.pair = pair }
    fun withType(type: TradeType) = apply { this.type = type }
    fun withOrderType(orderType: OrderType) = apply { this.orderType = orderType }
    fun withQuantity(quantity: Double) = apply { this.quantity = quantity }
    fun withPrice(price: Double?) = apply { this.price = price }
    fun withStopPrice(stopPrice: Double?) = apply { this.stopPrice = stopPrice }
    fun withKrakenOrderId(id: String?) = apply { this.krakenOrderId = id }
    fun withStatus(status: OrderStatus) = apply { this.status = status }
    fun withPlacedAt(time: Long) = apply { this.placedAt = time }
    fun withFilledAt(time: Long?) = apply { this.filledAt = time }
    fun withCancelledAt(time: Long?) = apply { this.cancelledAt = time }
    fun withFilledQuantity(qty: Double) = apply { this.filledQuantity = qty }
    fun withAverageFillPrice(price: Double?) = apply { this.averageFillPrice = price }
    fun withFee(fee: Double?) = apply { this.fee = fee }
    fun withErrorMessage(message: String?) = apply { this.errorMessage = message }

    fun asBuyOrder() = apply { this.type = TradeType.BUY }
    fun asSellOrder() = apply { this.type = TradeType.SELL }
    fun asMarketOrder() = apply { this.orderType = OrderType.MARKET }
    fun asLimitOrder() = apply { this.orderType = OrderType.LIMIT }
    fun asStopLoss() = apply { this.orderType = OrderType.STOP_LOSS }
    fun asTakeProfit() = apply { this.orderType = OrderType.TAKE_PROFIT }

    fun pending() = apply { this.status = OrderStatus.PENDING }
    fun open() = apply { this.status = OrderStatus.OPEN }
    fun filled() = apply {
        this.status = OrderStatus.FILLED
        this.filledAt = System.currentTimeMillis()
        this.filledQuantity = this.quantity
        if (this.averageFillPrice == null) {
            this.averageFillPrice = this.price
        }
    }
    fun cancelled() = apply {
        this.status = OrderStatus.CANCELLED
        this.cancelledAt = System.currentTimeMillis()
    }
    fun rejected(reason: String = "Order rejected") = apply {
        this.status = OrderStatus.REJECTED
        this.errorMessage = reason
    }

    fun build(): Order = Order(
        id = id,
        positionId = positionId,
        pair = pair,
        type = type,
        orderType = orderType,
        quantity = quantity,
        price = price,
        stopPrice = stopPrice,
        krakenOrderId = krakenOrderId,
        status = status,
        placedAt = placedAt,
        filledAt = filledAt,
        cancelledAt = cancelledAt,
        filledQuantity = filledQuantity,
        averageFillPrice = averageFillPrice,
        fee = fee,
        errorMessage = errorMessage
    )
}

/**
 * Fluent builder for creating test positions
 *
 * Usage:
 * ```
 * PositionBuilder()
 *     .withPair("XXBTZUSD")
 *     .withSide(PositionSide.LONG)
 *     .withQuantity(0.5)
 *     .withEntryPrice(40000.0)
 *     .build()
 * ```
 */
class PositionBuilder {
    private var id: String = UUID.randomUUID().toString()
    private var strategyId: String = "test-strategy"
    private var pair: String = "XXBTZUSD"
    private var side: PositionSide = PositionSide.LONG
    private var quantity: Double = 1.0
    private var entryPrice: Double = 40000.0
    private var entryTradeId: String = UUID.randomUUID().toString()
    private var openedAt: Long = System.currentTimeMillis()
    private var stopLossPrice: Double? = null
    private var takeProfitPrice: Double? = null
    private var stopLossOrderId: String? = null
    private var takeProfitOrderId: String? = null
    private var exitPrice: Double? = null
    private var exitTradeId: String? = null
    private var closedAt: Long? = null
    private var closeReason: String? = null
    private var unrealizedPnL: Double = 0.0
    private var unrealizedPnLPercent: Double = 0.0
    private var realizedPnL: Double? = null
    private var realizedPnLPercent: Double? = null
    private var status: PositionStatus = PositionStatus.OPEN
    private var lastUpdated: Long = System.currentTimeMillis()

    fun withId(id: String) = apply { this.id = id }
    fun withStrategyId(id: String) = apply { this.strategyId = id }
    fun withPair(pair: String) = apply { this.pair = pair }
    fun withSide(side: PositionSide) = apply { this.side = side }
    fun withQuantity(qty: Double) = apply { this.quantity = qty }
    fun withEntryPrice(price: Double) = apply { this.entryPrice = price }
    fun withEntryTradeId(id: String) = apply { this.entryTradeId = id }
    fun withOpenedAt(time: Long) = apply { this.openedAt = time }
    fun withStopLossPrice(price: Double?) = apply { this.stopLossPrice = price }
    fun withTakeProfitPrice(price: Double?) = apply { this.takeProfitPrice = price }
    fun withStopLossOrderId(id: String?) = apply { this.stopLossOrderId = id }
    fun withTakeProfitOrderId(id: String?) = apply { this.takeProfitOrderId = id }
    fun withExitPrice(price: Double?) = apply { this.exitPrice = price }
    fun withExitTradeId(id: String?) = apply { this.exitTradeId = id }
    fun withClosedAt(time: Long?) = apply { this.closedAt = time }
    fun withCloseReason(reason: String?) = apply { this.closeReason = reason }
    fun withUnrealizedPnL(pnl: Double) = apply { this.unrealizedPnL = pnl }
    fun withUnrealizedPnLPercent(pnlPercent: Double) = apply { this.unrealizedPnLPercent = pnlPercent }
    fun withRealizedPnL(pnl: Double?) = apply { this.realizedPnL = pnl }
    fun withRealizedPnLPercent(pnlPercent: Double?) = apply { this.realizedPnLPercent = pnlPercent }
    fun withStatus(status: PositionStatus) = apply { this.status = status }
    fun withLastUpdated(time: Long) = apply { this.lastUpdated = time }

    fun asLong() = apply { this.side = PositionSide.LONG }
    fun asShort() = apply { this.side = PositionSide.SHORT }
    fun open() = apply { this.status = PositionStatus.OPEN }
    fun closed() = apply {
        this.status = PositionStatus.CLOSED
        this.closedAt = System.currentTimeMillis()
    }

    fun withStopLoss(priceFactor: Double) = apply {
        this.stopLossPrice = entryPrice * priceFactor
    }

    fun withTakeProfit(priceFactor: Double) = apply {
        this.takeProfitPrice = entryPrice * priceFactor
    }

    fun build(): Position = Position(
        id = id,
        strategyId = strategyId,
        pair = pair,
        side = side,
        quantity = quantity,
        entryPrice = entryPrice,
        entryTradeId = entryTradeId,
        openedAt = openedAt,
        stopLossPrice = stopLossPrice,
        takeProfitPrice = takeProfitPrice,
        stopLossOrderId = stopLossOrderId,
        takeProfitOrderId = takeProfitOrderId,
        exitPrice = exitPrice,
        exitTradeId = exitTradeId,
        closedAt = closedAt,
        closeReason = closeReason,
        unrealizedPnL = unrealizedPnL,
        unrealizedPnLPercent = unrealizedPnLPercent,
        realizedPnL = realizedPnL,
        realizedPnLPercent = realizedPnLPercent,
        status = status,
        lastUpdated = lastUpdated
    )
}

/**
 * Fluent builder for creating test trades
 */
class TradeBuilder {
    private var id: Long = 0L
    private var orderId: String = UUID.randomUUID().toString()
    private var pair: String = "XXBTZUSD"
    private var type: TradeType = TradeType.BUY
    private var price: Double = 40000.0
    private var volume: Double = 1.0
    private var cost: Double = price * volume
    private var fee: Double = 0.0
    private var profit: Double? = null
    private var timestamp: Long = System.currentTimeMillis()
    private var strategyId: String? = null
    private var status: TradeStatus = TradeStatus.EXECUTED

    fun withId(id: Long) = apply { this.id = id }
    fun withOrderId(id: String) = apply { this.orderId = id }
    fun withPair(pair: String) = apply { this.pair = pair }
    fun withType(type: TradeType) = apply { this.type = type }
    fun withPrice(price: Double) = apply {
        this.price = price
        this.cost = price * volume
    }
    fun withVolume(volume: Double) = apply {
        this.volume = volume
        this.cost = price * volume
    }
    fun withCost(cost: Double) = apply { this.cost = cost }
    fun withFee(fee: Double) = apply { this.fee = fee }
    fun withProfit(profit: Double?) = apply { this.profit = profit }
    fun withTimestamp(timestamp: Long) = apply { this.timestamp = timestamp }
    fun withStrategyId(id: String?) = apply { this.strategyId = id }
    fun withStatus(status: TradeStatus) = apply { this.status = status }

    fun asBuy() = apply { this.type = TradeType.BUY }
    fun asSell() = apply { this.type = TradeType.SELL }

    fun build(): Trade = Trade(
        id = id,
        orderId = orderId,
        pair = pair,
        type = type,
        price = price,
        volume = volume,
        cost = cost,
        fee = fee,
        profit = profit,
        timestamp = timestamp,
        strategyId = strategyId,
        status = status
    )
}

// ==================== Test Fixtures ====================

/**
 * Common test fixtures used across multiple tests
 */
object TestFixtures {
    // Trading pairs
    const val BTC_USD = "XXBTZUSD"
    const val ETH_USD = "XETHZUSD"
    const val XRP_USD = "XXRPZUSD"

    // Realistic prices (2024 ranges)
    const val BTC_PRICE_LOW = 40000.0
    const val BTC_PRICE_MID = 42500.0
    const val BTC_PRICE_HIGH = 45000.0

    const val ETH_PRICE_LOW = 2500.0
    const val ETH_PRICE_MID = 2750.0
    const val ETH_PRICE_HIGH = 3000.0

    // Fee rates
    const val MAKER_FEE_RATE = 0.0016 // 0.16%
    const val TAKER_FEE_RATE = 0.0026 // 0.26%

    // Account balance
    val STARTING_BALANCE = BigDecimal("10000")

    // Risk management
    const val DEFAULT_STOP_LOSS_PERCENT = 0.95 // 5% below entry
    const val DEFAULT_TAKE_PROFIT_PERCENT = 1.10 // 10% above entry

    // Quantities
    const val SMALL_QUANTITY = 0.01
    const val MEDIUM_QUANTITY = 0.1
    const val LARGE_QUANTITY = 1.0

    // Strategy IDs
    const val TEST_STRATEGY_ID = "test-strategy-1"
    const val BACKUP_STRATEGY_ID = "test-strategy-2"

    // Time
    fun now(): Long = System.currentTimeMillis()
    fun before(ms: Long): Long = System.currentTimeMillis() - ms
    fun after(ms: Long): Long = System.currentTimeMillis() + ms
}

// ==================== Calculation Helpers ====================

/**
 * Common trading calculations for test verification
 */
object TradingCalculations {

    /**
     * Calculate P&L for a position
     *
     * @param entryPrice Entry price
     * @param exitPrice Exit price (or current price)
     * @param quantity Position size
     * @param fees Total fees paid
     * @param isLong True for long, false for short
     * @return P&L in absolute dollars
     */
    fun calculatePnL(
        entryPrice: BigDecimal,
        exitPrice: BigDecimal,
        quantity: BigDecimal,
        fees: BigDecimal = BigDecimal.ZERO,
        isLong: Boolean = true
    ): BigDecimal {
        val priceDiff = if (isLong) {
            exitPrice - entryPrice
        } else {
            entryPrice - exitPrice
        }
        return (priceDiff * quantity) - fees
    }

    /**
     * Calculate P&L percentage
     *
     * @param entryPrice Entry price
     * @param exitPrice Exit price (or current price)
     * @param isLong True for long, false for short
     * @return P&L percentage
     */
    fun calculatePnLPercent(
        entryPrice: BigDecimal,
        exitPrice: BigDecimal,
        isLong: Boolean = true
    ): BigDecimal {
        val priceDiff = if (isLong) {
            exitPrice - entryPrice
        } else {
            entryPrice - exitPrice
        }
        return (priceDiff / entryPrice) * BigDecimal("100")
    }

    /**
     * Calculate trading fee
     *
     * @param cost Total cost of trade (price × quantity)
     * @param isMaker True for maker fee, false for taker fee
     * @return Fee amount
     */
    fun calculateFee(
        cost: BigDecimal,
        isMaker: Boolean = true
    ): BigDecimal {
        val feeRate = if (isMaker) {
            BigDecimal("0.0016") // 0.16%
        } else {
            BigDecimal("0.0026") // 0.26%
        }
        return cost * feeRate
    }

    /**
     * Calculate position value
     *
     * @param price Current price
     * @param quantity Position size
     * @return Position value
     */
    fun calculatePositionValue(
        price: BigDecimal,
        quantity: BigDecimal
    ): BigDecimal {
        return price * quantity
    }

    /**
     * Verify position fits in account balance
     *
     * @param positionValue Total value of position
     * @param balance Account balance
     * @return True if position fits
     */
    fun positionFitsInBalance(
        positionValue: BigDecimal,
        balance: BigDecimal
    ): Boolean {
        return positionValue <= balance
    }

    /**
     * Calculate max position size for given risk parameters
     * Kelly Criterion: f = (bp - q) / b
     * where f = fraction of capital, b = odds, p = win probability, q = loss probability
     *
     * @param balance Account balance
     * @param riskPercent Maximum risk as percentage of balance
     * @return Maximum position size
     */
    fun calculateMaxPositionSize(
        balance: BigDecimal,
        riskPercent: BigDecimal = BigDecimal("2") // 2% risk
    ): BigDecimal {
        return balance * (riskPercent / BigDecimal("100"))
    }

    /**
     * Calculate average fill price for partial fills
     *
     * @param fills List of (price, quantity) pairs
     * @return Weighted average price
     */
    fun calculateAverageFillPrice(
        fills: List<Pair<BigDecimal, BigDecimal>>
    ): BigDecimal {
        if (fills.isEmpty()) return BigDecimal.ZERO

        var totalCost = BigDecimal.ZERO
        var totalQuantity = BigDecimal.ZERO

        fills.forEach { (price, quantity) ->
            totalCost += price * quantity
            totalQuantity += quantity
        }

        return if (totalQuantity > BigDecimal.ZERO) {
            totalCost / totalQuantity
        } else {
            BigDecimal.ZERO
        }
    }
}

// ==================== Assertion Helpers ====================

/**
 * Custom assertion helpers for trading tests
 */
object TradingAssertions {

    /**
     * Assert that two prices are close (within cent tolerance)
     *
     * @param expected Expected price
     * @param actual Actual price
     * @param tolerance Allowed difference (default 0.01 = 1 cent)
     */
    fun assertPriceEquals(
        expected: BigDecimal,
        actual: BigDecimal,
        tolerance: BigDecimal = BigDecimal("0.01")
    ) {
        val diff = (expected - actual).abs()
        require(diff <= tolerance) {
            "Expected $expected but got $actual (difference: $diff)"
        }
    }

    /**
     * Assert that P&L calculation is correct
     *
     * @param position Position to verify
     * @param expectedPnL Expected P&L
     * @param tolerance Allowed difference
     */
    fun assertPnLEquals(
        position: Position,
        expectedPnL: BigDecimal,
        tolerance: BigDecimal = BigDecimal("0.01")
    ) {
        val actualPnL = position.realizedPnLDecimal ?: position.unrealizedPnLDecimal
        val diff = (expectedPnL - actualPnL).abs()
        require(diff <= tolerance) {
            "Expected P&L $expectedPnL but got $actualPnL (difference: $diff)"
        }
    }

    /**
     * Assert that order was filled correctly
     *
     * @param order Order to verify
     * @param expectedQuantity Expected filled quantity
     * @param expectedPrice Expected fill price
     */
    fun assertOrderFilled(
        order: Order,
        expectedQuantity: Double,
        expectedPrice: Double
    ) {
        require(order.status == OrderStatus.FILLED) {
            "Order status must be FILLED but is ${order.status}"
        }
        require(order.filledQuantity == expectedQuantity) {
            "Expected quantity $expectedQuantity but got ${order.filledQuantity}"
        }
        require(order.averageFillPrice == expectedPrice) {
            "Expected price $expectedPrice but got ${order.averageFillPrice}"
        }
    }

    /**
     * Assert that position side is correct for profit direction
     *
     * @param position Position to verify
     * @param expectedProfit Expected profit direction (positive or negative)
     */
    fun assertPositionProfitDirection(
        position: Position,
        expectedProfit: Double
    ) {
        val actualProfit = (position.realizedPnLDecimal ?: position.unrealizedPnLDecimal).toDouble()
        val sameSign = (actualProfit > 0) == (expectedProfit > 0)
        require(sameSign) {
            "Expected profit direction ${if (expectedProfit > 0) "positive" else "negative"} " +
                    "but got ${if (actualProfit > 0) "positive" else "negative"}"
        }
    }
}

// ==================== Test Data Generation ====================

/**
 * Generate realistic test data
 */
object TestDataGenerator {

    /**
     * Generate random price in realistic range
     *
     * @param pair Trading pair
     * @return Random price for pair
     */
    fun generatePrice(pair: String = TestFixtures.BTC_USD): Double {
        return when (pair) {
            TestFixtures.BTC_USD -> {
                kotlin.random.Random.nextDouble(
                    TestFixtures.BTC_PRICE_LOW,
                    TestFixtures.BTC_PRICE_HIGH
                )
            }
            TestFixtures.ETH_USD -> {
                kotlin.random.Random.nextDouble(
                    TestFixtures.ETH_PRICE_LOW,
                    TestFixtures.ETH_PRICE_HIGH
                )
            }
            else -> kotlin.random.Random.nextDouble(1000.0, 50000.0)
        }
    }

    /**
     * Generate random quantity
     *
     * @return Random quantity 0.01 - 10 BTC
     */
    fun generateQuantity(): Double {
        return kotlin.random.Random.nextDouble(0.01, 10.0)
    }

    /**
     * Generate sequence of prices with trend
     *
     * @param startPrice Starting price
     * @param trend Direction of trend (positive = up, negative = down)
     * @param steps Number of price points
     * @param volatility Percentage volatility each step
     * @return List of prices
     */
    fun generatePriceSeries(
        startPrice: Double,
        trend: Double = 0.0,
        steps: Int = 20,
        volatility: Double = 2.0
    ): List<Double> {
        val prices = mutableListOf(startPrice)
        var currentPrice = startPrice

        repeat(steps - 1) {
            val randomChange = kotlin.random.Random.nextDouble(-volatility, volatility)
            val trendComponent = trend / steps
            val changePercent = (randomChange + trendComponent) / 100.0
            currentPrice = currentPrice * (1.0 + changePercent)
            prices.add(currentPrice)
        }

        return prices
    }

    /**
     * Generate realistic OHLC candle data
     *
     * @param basePrice Starting price
     * @param volatilityPercent Volatility percentage
     * @return OHLC data
     */
    data class Candle(
        val open: Double,
        val high: Double,
        val low: Double,
        val close: Double,
        val volume: Double
    )

    fun generateCandle(
        basePrice: Double,
        volatilityPercent: Double = 2.0
    ): Candle {
        val volatility = basePrice * (volatilityPercent / 100.0)
        val open = basePrice + kotlin.random.Random.nextDouble(-volatility, volatility)
        val close = basePrice + kotlin.random.Random.nextDouble(-volatility, volatility)
        val high = maxOf(open, close) + kotlin.random.Random.nextDouble(0.0, volatility)
        val low = minOf(open, close) - kotlin.random.Random.nextDouble(0.0, volatility)
        val volume = kotlin.random.Random.nextDouble(100.0, 10000.0)

        return Candle(open, high, low, close, volume)
    }
}

// ==================== Test Extensions ====================

/**
 * Extension functions for test convenience
 */

fun BigDecimal.assertCloseTo(
    other: BigDecimal,
    tolerance: BigDecimal = BigDecimal("0.01")
) {
    val diff = (this - other).abs()
    require(diff <= tolerance) {
        "Expected $other but got $this (difference: $diff)"
    }
}

fun Double.assertCloseTo(
    other: Double,
    tolerance: Double = 0.01
) {
    val diff = kotlin.math.abs(this - other)
    require(diff <= tolerance) {
        "Expected $other but got $this (difference: $diff)"
    }
}

fun <T> List<T>.assertSize(expectedSize: Int) {
    require(this.size == expectedSize) {
        "Expected size $expectedSize but got ${this.size}"
    }
}

fun Order.isFilled(): Boolean = this.status == OrderStatus.FILLED
fun Order.isPending(): Boolean = this.status == OrderStatus.PENDING
fun Order.isOpen(): Boolean = this.status == OrderStatus.OPEN
fun Order.isCancelled(): Boolean = this.status == OrderStatus.CANCELLED
fun Order.isRejected(): Boolean = this.status == OrderStatus.REJECTED

fun Position.isOpen(): Boolean = this.status == PositionStatus.OPEN
fun Position.isClosed(): Boolean = this.status == PositionStatus.CLOSED
fun Position.isLong(): Boolean = this.side == PositionSide.LONG
fun Position.isShort(): Boolean = this.side == PositionSide.SHORT

fun Position.calculateCurrentPnL(currentPrice: BigDecimal): BigDecimal {
    return when {
        isClosed() -> realizedPnLDecimal ?: BigDecimal.ZERO
        isOpen() -> {
            val (pnl, _) = calculateUnrealizedPnLDecimal(currentPrice)
            pnl
        }
        else -> BigDecimal.ZERO
    }
}
