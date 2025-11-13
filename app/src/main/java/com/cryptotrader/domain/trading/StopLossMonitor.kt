package com.cryptotrader.domain.trading

import com.cryptotrader.data.repository.KrakenRepository
import com.cryptotrader.domain.model.Position
import com.cryptotrader.domain.model.TradeType
import com.cryptotrader.domain.usecase.OrderType
import com.cryptotrader.domain.usecase.TradeRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Monitors open positions and automatically executes stop-loss and take-profit orders
 */
@Singleton
class StopLossMonitor @Inject constructor(
    private val krakenRepository: KrakenRepository
) {
    // Thread-safe storage of open positions
    private val openPositions = ConcurrentHashMap<String, Position>()

    private val _positions = MutableStateFlow<List<Position>>(emptyList())
    val positions: StateFlow<List<Position>> = _positions.asStateFlow()

    /**
     * Add a position to monitor
     */
    fun addPosition(position: Position) {
        openPositions[position.id] = position
        updatePositionsFlow()
        Timber.d("Position added to monitor: ${position.id} ${position.pair}")
    }

    /**
     * Remove a position from monitoring (position closed)
     */
    fun removePosition(positionId: String) {
        openPositions.remove(positionId)
        updatePositionsFlow()
        Timber.d("Position removed from monitor: $positionId")
    }

    /**
     * Get all open positions
     */
    fun getOpenPositions(): List<Position> = openPositions.values.toList()

    /**
     * Get open positions for a specific strategy
     */
    fun getPositionsForStrategy(strategyId: String): List<Position> {
        return openPositions.values.filter { it.strategyId == strategyId }
    }

    /**
     * Monitor positions and execute stop-loss/take-profit
     * Should be called periodically (every 1-5 minutes)
     */
    suspend fun checkPositions(): List<TradeResult> {
        val results = mutableListOf<TradeResult>()

        openPositions.values.forEach { position ->
            try {
                // Get current market price
                val tickerResult = krakenRepository.getTicker(position.pair)
                if (tickerResult.isSuccess) {
                    val ticker = tickerResult.getOrNull()!!
                    val currentPrice = ticker.last

                    // Update position with current price
                    val updatedPosition = position.calculateUnrealizedPnL(currentPrice)
                    openPositions[position.id] = updatedPosition

                    // Check stop-loss
                    if (updatedPosition.isStopLossTriggered(currentPrice)) {
                        Timber.w("Stop-loss triggered for ${position.id} at $currentPrice (stop: ${position.stopLossPrice})")

                        val result = executeStopLoss(updatedPosition, currentPrice)
                        if (result.success) {
                            removePosition(position.id)
                        }
                        results.add(result)
                    }
                    // Check take-profit
                    else if (updatedPosition.isTakeProfitTriggered(currentPrice)) {
                        Timber.i("Take-profit triggered for ${position.id} at $currentPrice (target: ${position.takeProfitPrice})")

                        val result = executeTakeProfit(updatedPosition, currentPrice)
                        if (result.success) {
                            removePosition(position.id)
                        }
                        results.add(result)
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error checking position ${position.id}")
            }
        }

        updatePositionsFlow()
        return results
    }

    /**
     * Execute stop-loss order (market sell to exit position quickly)
     */
    private suspend fun executeStopLoss(position: Position, currentPrice: Double): TradeResult {
        return try {
            // Create market order to close position immediately
            val closeType = when (position.type) {
                TradeType.BUY -> TradeType.SELL  // Close long position
                TradeType.SELL -> TradeType.BUY  // Close short position
            }

            val tradeRequest = TradeRequest(
                strategyId = position.strategyId,
                pair = position.pair,
                type = closeType,
                orderType = OrderType.MARKET, // Market order for immediate execution
                price = currentPrice,
                volume = position.volume
            )

            val result = krakenRepository.placeOrder(tradeRequest)
            if (result.isSuccess) {
                val trade = result.getOrNull()!!
                Timber.i("Stop-loss executed successfully: ${trade.orderId}")
                TradeResult(
                    positionId = position.id,
                    success = true,
                    reason = "Stop-loss triggered at $currentPrice",
                    pnl = position.unrealizedPnL,
                    orderId = trade.orderId
                )
            } else {
                Timber.e("Stop-loss execution failed: ${result.exceptionOrNull()?.message}")
                TradeResult(
                    positionId = position.id,
                    success = false,
                    reason = "Execution failed: ${result.exceptionOrNull()?.message}",
                    pnl = null,
                    orderId = null
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Error executing stop-loss")
            TradeResult(
                positionId = position.id,
                success = false,
                reason = "Exception: ${e.message}",
                pnl = null,
                orderId = null
            )
        }
    }

    /**
     * Execute take-profit order
     */
    private suspend fun executeTakeProfit(position: Position, currentPrice: Double): TradeResult {
        return try {
            val closeType = when (position.type) {
                TradeType.BUY -> TradeType.SELL
                TradeType.SELL -> TradeType.BUY
            }

            val tradeRequest = TradeRequest(
                strategyId = position.strategyId,
                pair = position.pair,
                type = closeType,
                orderType = OrderType.LIMIT, // Limit order at target price
                price = position.takeProfitPrice ?: currentPrice,
                volume = position.volume
            )

            val result = krakenRepository.placeOrder(tradeRequest)
            if (result.isSuccess) {
                val trade = result.getOrNull()!!
                Timber.i("Take-profit executed successfully: ${trade.orderId}")
                TradeResult(
                    positionId = position.id,
                    success = true,
                    reason = "Take-profit triggered at $currentPrice",
                    pnl = position.unrealizedPnL,
                    orderId = trade.orderId
                )
            } else {
                Timber.e("Take-profit execution failed: ${result.exceptionOrNull()?.message}")
                TradeResult(
                    positionId = position.id,
                    success = false,
                    reason = "Execution failed: ${result.exceptionOrNull()?.message}",
                    pnl = null,
                    orderId = null
                )
            }
        } catch (e: Exception) {
            Timber.e(e, "Error executing take-profit")
            TradeResult(
                positionId = position.id,
                success = false,
                reason = "Exception: ${e.message}",
                pnl = null,
                orderId = null
            )
        }
    }

    private fun updatePositionsFlow() {
        _positions.value = openPositions.values.toList()
    }

    /**
     * Clear all positions (for testing or reset)
     */
    fun clearAll() {
        openPositions.clear()
        updatePositionsFlow()
        Timber.d("All positions cleared from monitor")
    }
}

/**
 * Result of stop-loss or take-profit execution
 */
data class TradeResult(
    val positionId: String,
    val success: Boolean,
    val reason: String,
    val pnl: Double?,
    val orderId: String?
)
