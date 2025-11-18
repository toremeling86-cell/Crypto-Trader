package com.cryptotrader.domain.trading

import com.cryptotrader.domain.model.TradingMode
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks open trading positions
 *
 * Keeps track of active trades so we don't open multiple positions for same pair
 * Monitors stop-loss and take-profit levels
 */
@Singleton
class PositionTracker @Inject constructor() {

    // Map: strategyId+pair -> Position
    private val openPositions = ConcurrentHashMap<String, Position>()

    /**
     * Check if strategy has open position for pair
     */
    fun hasOpenPosition(strategyId: String, pair: String): Boolean {
        val key = "$strategyId:$pair"
        return openPositions.containsKey(key)
    }

    /**
     * Get open position for strategy and pair
     */
    fun getOpenPosition(strategyId: String, pair: String): Position? {
        val key = "$strategyId:$pair"
        return openPositions[key]
    }

    /**
     * Open a new position
     */
    fun openPosition(
        strategyId: String,
        pair: String,
        entryPrice: Double,
        volume: Double,
        stopLossPrice: Double,
        takeProfitPrice: Double,
        mode: TradingMode
    ) {
        val key = "$strategyId:$pair"
        val position = Position(
            strategyId = strategyId,
            pair = pair,
            entryPrice = entryPrice,
            volume = volume,
            stopLossPrice = stopLossPrice,
            takeProfitPrice = takeProfitPrice,
            openedAt = System.currentTimeMillis(),
            mode = mode
        )

        openPositions[key] = position
        Timber.i("📍 Position opened: $pair @ $entryPrice (${mode.name})")
    }

    /**
     * Close a position
     */
    fun closePosition(strategyId: String, pair: String, pnl: Double) {
        val key = "$strategyId:$pair"
        val position = openPositions.remove(key)

        if (position != null) {
            Timber.i("📍 Position closed: $pair, P&L: $${"%.2f".format(pnl)} (${position.mode.name})")
        }
    }

    /**
     * Get all open positions for strategy
     */
    fun getOpenPositions(strategyId: String): List<Position> {
        return openPositions.values.filter { it.strategyId == strategyId }
    }

    /**
     * Get all open positions
     */
    fun getAllOpenPositions(): List<Position> {
        return openPositions.values.toList()
    }

    /**
     * Check if current price has hit stop-loss or take-profit
     */
    fun checkPriceTargets(strategyId: String, pair: String, currentPrice: Double): PriceTarget? {
        val position = getOpenPosition(strategyId, pair) ?: return null

        return when {
            currentPrice <= position.stopLossPrice -> PriceTarget.STOP_LOSS
            currentPrice >= position.takeProfitPrice -> PriceTarget.TAKE_PROFIT
            else -> null
        }
    }

    /**
     * Clear all positions (for reset/testing)
     */
    fun clearAll() {
        openPositions.clear()
        Timber.i("📍 All positions cleared")
    }
}

/**
 * Represents an open trading position
 */
data class Position(
    val strategyId: String,
    val pair: String,
    val entryPrice: Double,
    val volume: Double,
    val stopLossPrice: Double,
    val takeProfitPrice: Double,
    val openedAt: Long,
    val mode: TradingMode
) {
    /**
     * Calculate current unrealized P&L
     */
    fun calculateUnrealizedPnL(currentPrice: Double): Double {
        return (currentPrice - entryPrice) * volume
    }

    /**
     * Calculate P&L percentage
     */
    fun calculatePnLPercent(currentPrice: Double): Double {
        return ((currentPrice - entryPrice) / entryPrice) * 100.0
    }
}

enum class PriceTarget {
    STOP_LOSS,
    TAKE_PROFIT
}
