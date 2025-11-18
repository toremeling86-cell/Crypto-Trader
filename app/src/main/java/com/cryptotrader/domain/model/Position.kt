package com.cryptotrader.domain.model

/**
 * Domain model representing a trading position
 *
 * A position tracks the lifecycle of a trade from entry to exit,
 * including real-time P&L calculations and risk management.
 */
data class Position(
    val id: String,
    val strategyId: String,
    val pair: String,
    val side: PositionSide,
    val quantity: Double,

    // Entry details
    val entryPrice: Double,
    val entryTradeId: String,
    val openedAt: Long,

    // Risk management
    val stopLossPrice: Double?,
    val takeProfitPrice: Double?,
    val stopLossOrderId: String?,
    val takeProfitOrderId: String?,

    // Exit details
    val exitPrice: Double? = null,
    val exitTradeId: String? = null,
    val closedAt: Long? = null,
    val closeReason: String? = null,

    // P&L tracking
    val unrealizedPnL: Double = 0.0,
    val unrealizedPnLPercent: Double = 0.0,
    val realizedPnL: Double? = null,
    val realizedPnLPercent: Double? = null,

    // Status
    val status: PositionStatus = PositionStatus.OPEN,

    // Tracking
    val lastUpdated: Long = System.currentTimeMillis()
) {
    /**
     * Check if stop-loss is triggered
     */
    fun isStopLossTriggered(currentPrice: Double): Boolean {
        if (stopLossPrice == null) return false

        return when (side) {
            PositionSide.LONG -> currentPrice <= stopLossPrice
            PositionSide.SHORT -> currentPrice >= stopLossPrice
        }
    }

    /**
     * Check if take-profit is triggered
     */
    fun isTakeProfitTriggered(currentPrice: Double): Boolean {
        if (takeProfitPrice == null) return false

        return when (side) {
            PositionSide.LONG -> currentPrice >= takeProfitPrice
            PositionSide.SHORT -> currentPrice <= takeProfitPrice
        }
    }

    /**
     * Calculate unrealized P&L for current price
     */
    fun calculateUnrealizedPnL(currentPrice: Double): Pair<Double, Double> {
        val pnl = when (side) {
            PositionSide.LONG -> (currentPrice - entryPrice) * quantity
            PositionSide.SHORT -> (entryPrice - currentPrice) * quantity
        }
        val pnlPercent = (pnl / (entryPrice * quantity)) * 100.0
        return Pair(pnl, pnlPercent)
    }

    /**
     * Calculate realized P&L at exit price
     */
    fun calculateRealizedPnL(exitPrice: Double): Pair<Double, Double> {
        val pnl = when (side) {
            PositionSide.LONG -> (exitPrice - entryPrice) * quantity
            PositionSide.SHORT -> (entryPrice - exitPrice) * quantity
        }
        val pnlPercent = (pnl / (entryPrice * quantity)) * 100.0
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
