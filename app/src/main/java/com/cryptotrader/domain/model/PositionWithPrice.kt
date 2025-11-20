package com.cryptotrader.domain.model

import java.math.BigDecimal

/**
 * Position data combined with current market price and real-time P&L
 *
 * Represents a snapshot of a trading position at a specific point in time
 * with current market conditions. All monetary values use BigDecimal for
 * exact precision in financial calculations.
 *
 * @property position The underlying position domain model
 * @property currentPrice Current market price for the position's trading pair
 * @property unrealizedPnL Unrealized profit/loss at current price (BigDecimal)
 * @property unrealizedPnLPercent Unrealized P&L as percentage (%)
 * @property lastPriceUpdate Timestamp of the last price update (milliseconds)
 */
data class PositionWithPrice(
    val position: Position,
    val currentPrice: BigDecimal?,
    val unrealizedPnL: BigDecimal?,
    val unrealizedPnLPercent: Double?,
    val lastPriceUpdate: Long?
) {
    /**
     * Check if P&L is positive (profit)
     */
    fun isProfit(): Boolean = unrealizedPnL?.let { it > BigDecimal.ZERO } ?: false

    /**
     * Check if P&L is negative (loss)
     */
    fun isLoss(): Boolean = unrealizedPnL?.let { it < BigDecimal.ZERO } ?: false

    /**
     * Check if P&L is neutral
     */
    fun isNeutral(): Boolean = unrealizedPnL?.let { it == BigDecimal.ZERO } ?: false

    /**
     * Get the trading pair
     */
    fun getPair(): String = position.pair

    /**
     * Get the position side (LONG or SHORT)
     */
    fun getSide(): PositionSide = position.side

    /**
     * Check if position is at risk based on stop-loss
     */
    fun isAtRisk(currentPrice: BigDecimal): Boolean {
        return position.isStopLossTriggeredDecimal(currentPrice)
    }

    /**
     * Check if position reached profit target
     */
    fun isProfitTargetReached(currentPrice: BigDecimal): Boolean {
        return position.isTakeProfitTriggeredDecimal(currentPrice)
    }
}

/**
 * Real-time P&L update for a single position
 *
 * Optimized data class for high-frequency P&L updates. Contains only
 * the essential information for monitoring P&L changes. Useful for
 * alert systems and real-time dashboards.
 *
 * @property positionId Unique identifier of the position being tracked
 * @property unrealizedPnL Current unrealized profit/loss (BigDecimal)
 * @property unrealizedPnLPercent Current P&L percentage (%)
 * @property currentPrice Current market price (BigDecimal)
 * @property timestamp Time of this P&L update (milliseconds since epoch)
 */
data class PositionPnL(
    val positionId: String,
    val unrealizedPnL: BigDecimal,
    val unrealizedPnLPercent: Double,
    val currentPrice: BigDecimal,
    val timestamp: Long
) {
    /**
     * Check if this update represents a gain
     */
    fun isGain(): Boolean = unrealizedPnL > BigDecimal.ZERO

    /**
     * Check if this update represents a loss
     */
    fun isLoss(): Boolean = unrealizedPnL < BigDecimal.ZERO

    /**
     * Check if P&L has improved since this update
     *
     * @param previousPnL Previous P&L to compare against
     * @return true if current P&L is greater than previous
     */
    fun hasImproved(previousPnL: BigDecimal): Boolean = unrealizedPnL > previousPnL

    /**
     * Check if P&L has worsened since this update
     *
     * @param previousPnL Previous P&L to compare against
     * @return true if current P&L is less than previous
     */
    fun hasWorsened(previousPnL: BigDecimal): Boolean = unrealizedPnL < previousPnL
}
