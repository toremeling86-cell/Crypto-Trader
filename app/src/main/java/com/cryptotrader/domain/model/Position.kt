package com.cryptotrader.domain.model

import com.cryptotrader.utils.toBigDecimalMoney
import java.math.BigDecimal

/**
 * Domain model representing a trading position
 *
 * A position tracks the lifecycle of a trade from entry to exit,
 * including real-time P&L calculations and risk management.
 *
 * BigDecimal Migration (Phase 2.9):
 * - All monetary fields now use BigDecimal for exact calculations
 * - Double fields deprecated but kept for backward compatibility
 * - New code should use *Decimal fields exclusively
 */
data class Position(
    val id: String,
    val strategyId: String,
    val pair: String,
    val side: PositionSide,

    // Quantity - exact decimal arithmetic
    @Deprecated("Use quantityDecimal for exact calculations", ReplaceWith("quantityDecimal"))
    val quantity: Double,
    val quantityDecimal: BigDecimal = quantity.toBigDecimalMoney(),

    // Entry price - exact decimal arithmetic
    @Deprecated("Use entryPriceDecimal for exact calculations", ReplaceWith("entryPriceDecimal"))
    val entryPrice: Double,
    val entryPriceDecimal: BigDecimal = entryPrice.toBigDecimalMoney(),

    val entryTradeId: String,
    val openedAt: Long,

    // Risk management - exact decimal arithmetic
    @Deprecated("Use stopLossPriceDecimal for exact calculations", ReplaceWith("stopLossPriceDecimal"))
    val stopLossPrice: Double?,
    val stopLossPriceDecimal: BigDecimal? = stopLossPrice?.toBigDecimalMoney(),

    @Deprecated("Use takeProfitPriceDecimal for exact calculations", ReplaceWith("takeProfitPriceDecimal"))
    val takeProfitPrice: Double?,
    val takeProfitPriceDecimal: BigDecimal? = takeProfitPrice?.toBigDecimalMoney(),

    val stopLossOrderId: String?,
    val takeProfitOrderId: String?,

    // Exit price - exact decimal arithmetic
    @Deprecated("Use exitPriceDecimal for exact calculations", ReplaceWith("exitPriceDecimal"))
    val exitPrice: Double? = null,
    val exitPriceDecimal: BigDecimal? = exitPrice?.toBigDecimalMoney(),

    val exitTradeId: String? = null,
    val closedAt: Long? = null,
    val closeReason: String? = null,

    // P&L tracking - exact decimal arithmetic
    @Deprecated("Use unrealizedPnLDecimal for exact calculations", ReplaceWith("unrealizedPnLDecimal"))
    val unrealizedPnL: Double = 0.0,
    val unrealizedPnLDecimal: BigDecimal = unrealizedPnL.toBigDecimalMoney(),

    @Deprecated("Use unrealizedPnLPercentDecimal for exact calculations", ReplaceWith("unrealizedPnLPercentDecimal"))
    val unrealizedPnLPercent: Double = 0.0,
    val unrealizedPnLPercentDecimal: BigDecimal = unrealizedPnLPercent.toBigDecimalMoney(),

    @Deprecated("Use realizedPnLDecimal for exact calculations", ReplaceWith("realizedPnLDecimal"))
    val realizedPnL: Double? = null,
    val realizedPnLDecimal: BigDecimal? = realizedPnL?.toBigDecimalMoney(),

    @Deprecated("Use realizedPnLPercentDecimal for exact calculations", ReplaceWith("realizedPnLPercentDecimal"))
    val realizedPnLPercent: Double? = null,
    val realizedPnLPercentDecimal: BigDecimal? = realizedPnLPercent?.toBigDecimalMoney(),

    // Status
    val status: PositionStatus = PositionStatus.OPEN,

    // Tracking
    val lastUpdated: Long = System.currentTimeMillis()
) {
    /**
     * Check if stop-loss is triggered (using Double for backward compatibility)
     */
    @Deprecated("Use isStopLossTriggeredDecimal for exact calculations", ReplaceWith("isStopLossTriggeredDecimal(currentPrice.toBigDecimalMoney())"))
    fun isStopLossTriggered(currentPrice: Double): Boolean {
        if (stopLossPrice == null) return false

        return when (side) {
            PositionSide.LONG -> currentPrice <= stopLossPrice
            PositionSide.SHORT -> currentPrice >= stopLossPrice
        }
    }

    /**
     * Check if stop-loss is triggered (BigDecimal - exact calculations)
     */
    fun isStopLossTriggeredDecimal(currentPrice: BigDecimal): Boolean {
        if (stopLossPriceDecimal == null) return false

        return when (side) {
            PositionSide.LONG -> currentPrice <= stopLossPriceDecimal
            PositionSide.SHORT -> currentPrice >= stopLossPriceDecimal
        }
    }

    /**
     * Check if take-profit is triggered (using Double for backward compatibility)
     */
    @Deprecated("Use isTakeProfitTriggeredDecimal for exact calculations", ReplaceWith("isTakeProfitTriggeredDecimal(currentPrice.toBigDecimalMoney())"))
    fun isTakeProfitTriggered(currentPrice: Double): Boolean {
        if (takeProfitPrice == null) return false

        return when (side) {
            PositionSide.LONG -> currentPrice >= takeProfitPrice
            PositionSide.SHORT -> currentPrice <= takeProfitPrice
        }
    }

    /**
     * Check if take-profit is triggered (BigDecimal - exact calculations)
     */
    fun isTakeProfitTriggeredDecimal(currentPrice: BigDecimal): Boolean {
        if (takeProfitPriceDecimal == null) return false

        return when (side) {
            PositionSide.LONG -> currentPrice >= takeProfitPriceDecimal
            PositionSide.SHORT -> currentPrice <= takeProfitPriceDecimal
        }
    }

    /**
     * Calculate unrealized P&L for current price (using Double for backward compatibility)
     */
    @Deprecated("Use calculateUnrealizedPnLDecimal for exact calculations", ReplaceWith("calculateUnrealizedPnLDecimal(currentPrice.toBigDecimalMoney())"))
    fun calculateUnrealizedPnL(currentPrice: Double): Pair<Double, Double> {
        val pnl = when (side) {
            PositionSide.LONG -> (currentPrice - entryPrice) * quantity
            PositionSide.SHORT -> (entryPrice - currentPrice) * quantity
        }
        val pnlPercent = (pnl / (entryPrice * quantity)) * 100.0
        return Pair(pnl, pnlPercent)
    }

    /**
     * Calculate unrealized P&L for current price (BigDecimal - exact calculations)
     */
    fun calculateUnrealizedPnLDecimal(currentPrice: BigDecimal): Pair<BigDecimal, BigDecimal> {
        val pnl = when (side) {
            PositionSide.LONG -> (currentPrice - entryPriceDecimal) * quantityDecimal
            PositionSide.SHORT -> (entryPriceDecimal - currentPrice) * quantityDecimal
        }
        val pnlPercent = (pnl / (entryPriceDecimal * quantityDecimal)) * BigDecimal("100")
        return Pair(pnl, pnlPercent)
    }

    /**
     * Calculate realized P&L at exit price (using Double for backward compatibility)
     */
    @Deprecated("Use calculateRealizedPnLDecimal for exact calculations", ReplaceWith("calculateRealizedPnLDecimal(exitPrice.toBigDecimalMoney())"))
    fun calculateRealizedPnL(exitPrice: Double): Pair<Double, Double> {
        val pnl = when (side) {
            PositionSide.LONG -> (exitPrice - entryPrice) * quantity
            PositionSide.SHORT -> (entryPrice - exitPrice) * quantity
        }
        val pnlPercent = (pnl / (entryPrice * quantity)) * 100.0
        return Pair(pnl, pnlPercent)
    }

    /**
     * Calculate realized P&L at exit price (BigDecimal - exact calculations)
     */
    fun calculateRealizedPnLDecimal(exitPrice: BigDecimal): Pair<BigDecimal, BigDecimal> {
        val pnl = when (side) {
            PositionSide.LONG -> (exitPrice - entryPriceDecimal) * quantityDecimal
            PositionSide.SHORT -> (entryPriceDecimal - exitPrice) * quantityDecimal
        }
        val pnlPercent = (pnl / (entryPriceDecimal * quantityDecimal)) * BigDecimal("100")
        return Pair(pnl, pnlPercent)
    }
}

/**
 * Position side enumeration
 */
enum class PositionSide {
    LONG,
    SHORT;

    override fun toString(): String = name

    companion object {
        fun fromString(value: String): PositionSide {
            return when (value.uppercase()) {
                "LONG" -> LONG
                "SHORT" -> SHORT
                else -> throw IllegalArgumentException("Unknown position side: $value")
            }
        }
    }
}

/**
 * Position status enumeration
 */
enum class PositionStatus {
    OPEN,
    CLOSED,
    LIQUIDATED;

    override fun toString(): String = name

    companion object {
        fun fromString(value: String): PositionStatus {
            return when (value.uppercase()) {
                "OPEN" -> OPEN
                "CLOSED" -> CLOSED
                "LIQUIDATED" -> LIQUIDATED
                else -> OPEN
            }
        }
    }
}
