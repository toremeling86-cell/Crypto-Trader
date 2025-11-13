package com.cryptotrader.domain.model

/**
 * Represents an open trading position with stop-loss and take-profit targets
 */
data class Position(
    val id: String, // Same as trade orderId
    val strategyId: String,
    val pair: String,
    val type: TradeType,
    val entryPrice: Double,
    val volume: Double,
    val stopLossPrice: Double,
    val takeProfitPrice: Double?,
    val entryTimestamp: Long,
    val currentPrice: Double = entryPrice,
    val unrealizedPnL: Double = 0.0,
    val unrealizedPnLPercent: Double = 0.0,
    // Trailing stop-loss tracking
    val useTrailingStop: Boolean = false,
    val trailingStopPercent: Double = 5.0,
    val highestPrice: Double = entryPrice, // Track highest price for trailing stop
    val initialStopLoss: Double = stopLossPrice // Keep initial stop-loss as fallback
) {
    /**
     * Check if stop-loss is triggered
     */
    fun isStopLossTriggered(currentPrice: Double): Boolean {
        return when (type) {
            TradeType.BUY -> currentPrice <= stopLossPrice  // Price dropped below stop-loss
            TradeType.SELL -> currentPrice >= stopLossPrice // Price rose above stop-loss
        }
    }

    /**
     * Check if take-profit is triggered
     */
    fun isTakeProfitTriggered(currentPrice: Double): Boolean {
        if (takeProfitPrice == null) return false
        return when (type) {
            TradeType.BUY -> currentPrice >= takeProfitPrice  // Price rose to target
            TradeType.SELL -> currentPrice <= takeProfitPrice // Price dropped to target
        }
    }

    /**
     * Calculate current unrealized P&L
     */
    fun calculateUnrealizedPnL(currentPrice: Double): Position {
        val pnl = when (type) {
            TradeType.BUY -> (currentPrice - entryPrice) * volume
            TradeType.SELL -> (entryPrice - currentPrice) * volume
        }
        val pnlPercent = ((currentPrice - entryPrice) / entryPrice) * 100.0

        return copy(
            currentPrice = currentPrice,
            unrealizedPnL = pnl,
            unrealizedPnLPercent = pnlPercent
        )
    }

    /**
     * Update trailing stop-loss based on current price
     * Returns updated Position with new stop-loss if trailing stop is triggered
     */
    fun updateTrailingStop(currentPrice: Double): Position {
        if (!useTrailingStop) return this

        when (type) {
            TradeType.BUY -> {
                // For long positions, trail stop-loss upward
                if (currentPrice > highestPrice) {
                    // New high price - update trailing stop
                    val newStopLoss = currentPrice * (1.0 - trailingStopPercent / 100.0)

                    // Only move stop-loss up, never down
                    if (newStopLoss > stopLossPrice) {
                        return copy(
                            highestPrice = currentPrice,
                            stopLossPrice = newStopLoss
                        )
                    }
                }
            }
            TradeType.SELL -> {
                // For short positions, trail stop-loss downward
                if (currentPrice < highestPrice) { // Note: for shorts, "highest" means lowest price
                    val newStopLoss = currentPrice * (1.0 + trailingStopPercent / 100.0)

                    // Only move stop-loss down, never up
                    if (newStopLoss < stopLossPrice) {
                        return copy(
                            highestPrice = currentPrice,
                            stopLossPrice = newStopLoss
                        )
                    }
                }
            }
        }

        return this
    }
}
