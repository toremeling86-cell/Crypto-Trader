package com.cryptotrader.data.repository

import android.content.Context
import com.cryptotrader.data.local.dao.PositionDao
import com.cryptotrader.data.local.entities.PositionEntity
import com.cryptotrader.domain.model.*
import com.cryptotrader.utils.CryptoUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing trading positions
 *
 * Responsibilities:
 * - Open and close positions
 * - Track P&L in real-time
 * - Monitor stop-loss and take-profit levels
 * - Sync position prices with market data
 * - Handle paper trading mode
 */
@Singleton
class PositionRepository @Inject constructor(
    private val positionDao: PositionDao,
    private val krakenRepository: KrakenRepository,
    private val orderRepository: OrderRepository,
    private val context: Context
) {

    /**
     * Open a new position
     *
     * @param pair Trading pair (e.g., "XBTUSD")
     * @param side LONG or SHORT
     * @param entryPrice Entry price for the position
     * @param quantity Position size
     * @param strategyId Strategy that opened this position
     * @param stopLoss Optional stop-loss price
     * @param takeProfit Optional take-profit price
     * @param entryTradeId Trade ID that opened this position
     * @return Result containing the created position
     */
    suspend fun openPosition(
        pair: String,
        side: PositionSide,
        entryPrice: Double,
        quantity: Double,
        strategyId: String,
        stopLoss: Double? = null,
        takeProfit: Double? = null,
        entryTradeId: String = UUID.randomUUID().toString()
    ): Result<Position> {
        return try {
            val positionId = UUID.randomUUID().toString()
            val currentTime = System.currentTimeMillis()

            // Create position entity
            val positionEntity = PositionEntity(
                id = positionId,
                strategyId = strategyId,
                pair = pair,
                type = side.toString(),
                quantity = quantity,
                entryPrice = entryPrice,
                entryTradeId = entryTradeId,
                openedAt = currentTime,
                stopLossPrice = stopLoss,
                takeProfitPrice = takeProfit,
                stopLossOrderId = null,
                takeProfitOrderId = null,
                status = PositionStatus.OPEN.toString(),
                unrealizedPnL = 0.0,
                unrealizedPnLPercent = 0.0,
                lastUpdated = currentTime
            )

            // Insert into database
            positionDao.insertPosition(positionEntity)

            // Place stop-loss order if specified
            var stopLossOrderId: String? = null
            if (stopLoss != null) {
                val stopLossResult = placeStopLossOrder(positionId, pair, side, quantity, stopLoss)
                if (stopLossResult.isSuccess) {
                    stopLossOrderId = stopLossResult.getOrNull()?.id
                    positionDao.updateStopLossOrder(positionId, stopLossOrderId)
                    Timber.d("Stop-loss order placed for position $positionId: $stopLossOrderId")
                } else {
                    Timber.w("Failed to place stop-loss order: ${stopLossResult.exceptionOrNull()?.message}")
                }
            }

            // Place take-profit order if specified
            var takeProfitOrderId: String? = null
            if (takeProfit != null) {
                val takeProfitResult = placeTakeProfitOrder(positionId, pair, side, quantity, takeProfit)
                if (takeProfitResult.isSuccess) {
                    takeProfitOrderId = takeProfitResult.getOrNull()?.id
                    positionDao.updateTakeProfitOrder(positionId, takeProfitOrderId)
                    Timber.d("Take-profit order placed for position $positionId: $takeProfitOrderId")
                } else {
                    Timber.w("Failed to place take-profit order: ${takeProfitResult.exceptionOrNull()?.message}")
                }
            }

            val position = positionEntity.copy(
                stopLossOrderId = stopLossOrderId,
                takeProfitOrderId = takeProfitOrderId
            ).toDomain()

            Timber.d("Position opened: $positionId - ${side} ${quantity} ${pair} @ ${entryPrice}")
            Result.success(position)
        } catch (e: Exception) {
            Timber.e(e, "Error opening position")
            Result.failure(e)
        }
    }

    /**
     * Close a position at the specified exit price
     *
     * @param positionId Position to close
     * @param exitPrice Price at which position is being closed
     * @param closeReason Reason for closing (e.g., "MANUAL", "STOP_LOSS", "TAKE_PROFIT")
     * @param exitTradeId Optional trade ID for the exit
     * @return Result containing the closed position
     */
    suspend fun closePosition(
        positionId: String,
        exitPrice: Double,
        closeReason: String = "MANUAL",
        exitTradeId: String? = null
    ): Result<Position> {
        return try {
            // Get position from database
            val positionEntity = positionDao.getPositionById(positionId)
                ?: return Result.failure(Exception("Position not found: $positionId"))

            if (positionEntity.status != PositionStatus.OPEN.toString()) {
                return Result.failure(Exception("Position is not open: ${positionEntity.status}"))
            }

            // Convert to domain model for calculations
            val position = positionEntity.toDomain()

            // Calculate realized P&L
            val (realizedPnL, realizedPnLPercent) = position.calculateRealizedPnL(exitPrice)

            val closedAt = System.currentTimeMillis()

            // Update position in database
            positionDao.closePosition(
                id = positionId,
                exitPrice = exitPrice,
                closedAt = closedAt,
                reason = closeReason,
                pnl = realizedPnL,
                pnlPercent = realizedPnLPercent,
                exitTradeId = exitTradeId
            )

            // Cancel any pending stop-loss or take-profit orders
            positionEntity.stopLossOrderId?.let { orderId ->
                orderRepository.cancelOrder(orderId)
                Timber.d("Cancelled stop-loss order: $orderId")
            }

            positionEntity.takeProfitOrderId?.let { orderId ->
                orderRepository.cancelOrder(orderId)
                Timber.d("Cancelled take-profit order: $orderId")
            }

            // Get updated position
            val closedPosition = positionDao.getPositionById(positionId)?.toDomain()
                ?: return Result.failure(Exception("Failed to retrieve closed position"))

            Timber.d("Position closed: $positionId - P&L: $realizedPnL (${realizedPnLPercent}%)")
            Result.success(closedPosition)
        } catch (e: Exception) {
            Timber.e(e, "Error closing position")
            Result.failure(e)
        }
    }

    /**
     * Update current price and recalculate unrealized P&L for a position
     *
     * @param positionId Position to update
     * @param currentPrice Current market price
     * @return Result containing updated position
     */
    suspend fun updatePositionPrice(
        positionId: String,
        currentPrice: Double
    ): Result<Position> {
        return try {
            // Get position from database
            val positionEntity = positionDao.getPositionById(positionId)
                ?: return Result.failure(Exception("Position not found: $positionId"))

            if (positionEntity.status != PositionStatus.OPEN.toString()) {
                return Result.failure(Exception("Position is not open"))
            }

            // Convert to domain model for calculations
            val position = positionEntity.toDomain()

            // Calculate unrealized P&L
            val (unrealizedPnL, unrealizedPnLPercent) = position.calculateUnrealizedPnL(currentPrice)

            // Update in database
            positionDao.updateUnrealizedPnL(
                id = positionId,
                pnl = unrealizedPnL,
                pnlPercent = unrealizedPnLPercent,
                time = System.currentTimeMillis()
            )

            // Check if stop-loss or take-profit is triggered
            checkStopLossTakeProfit(positionId)

            // Get updated position
            val updatedPosition = positionDao.getPositionById(positionId)?.toDomain()
                ?: return Result.failure(Exception("Failed to retrieve updated position"))

            Result.success(updatedPosition)
        } catch (e: Exception) {
            Timber.e(e, "Error updating position price")
            Result.failure(e)
        }
    }

    /**
     * Sync all open positions with current market prices
     *
     * @return Result containing list of updated positions
     */
    suspend fun syncPositionPrices(): Result<List<Position>> {
        return try {
            // Get all open positions
            val openPositions = positionDao.getOpenPositions().first()

            if (openPositions.isEmpty()) {
                Timber.d("No open positions to sync")
                return Result.success(emptyList())
            }

            val updatedPositions = mutableListOf<Position>()

            // Group positions by trading pair for efficient price fetching
            val positionsByPair = openPositions.groupBy { it.pair }

            positionsByPair.forEach { (pair, positions) ->
                // Fetch current price for this pair
                val tickerResult = krakenRepository.getTicker(pair)

                if (tickerResult.isSuccess) {
                    val ticker = tickerResult.getOrNull()!!
                    val currentPrice = ticker.last

                    // Update each position with this pair
                    positions.forEach { positionEntity ->
                        val updateResult = updatePositionPrice(positionEntity.id, currentPrice)
                        if (updateResult.isSuccess) {
                            updatedPositions.add(updateResult.getOrNull()!!)
                        } else {
                            Timber.w("Failed to update position ${positionEntity.id}: ${updateResult.exceptionOrNull()?.message}")
                        }
                    }
                } else {
                    Timber.w("Failed to fetch ticker for $pair: ${tickerResult.exceptionOrNull()?.message}")
                }
            }

            Timber.d("Synced prices for ${updatedPositions.size} positions")
            Result.success(updatedPositions)
        } catch (e: Exception) {
            Timber.e(e, "Error syncing position prices")
            Result.failure(e)
        }
    }

    /**
     * Get position by ID
     *
     * @param id Position ID
     * @return Position or null if not found
     */
    suspend fun getPositionById(id: String): Position? {
        return try {
            positionDao.getPositionById(id)?.toDomain()
        } catch (e: Exception) {
            Timber.e(e, "Error getting position by ID")
            null
        }
    }

    /**
     * Get flow of all open positions
     *
     * @return Flow emitting list of open positions
     */
    fun getOpenPositionsFlow(): Flow<List<Position>> {
        return positionDao.getOpenPositions().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /**
     * Get current snapshot of open positions (non-Flow, for synchronous access)
     *
     * @return List of open positions
     */
    suspend fun getOpenPositionsSync(): List<Position> {
        return positionDao.getOpenPositionsSnapshot().map { it.toDomain() }
    }

    /**
     * Get flow of closed positions with limit
     *
     * @param limit Maximum number of positions to return
     * @return Flow emitting list of closed positions
     */
    fun getClosedPositionsFlow(limit: Int = 50): Flow<List<Position>> {
        return positionDao.getRecentClosedPositions(limit).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /**
     * Get flow of positions for a specific strategy
     *
     * @param strategyId Strategy ID
     * @return Flow emitting list of positions for the strategy
     */
    fun getPositionsByStrategyFlow(strategyId: String): Flow<List<Position>> {
        return positionDao.getPositionsByStrategy(strategyId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /**
     * Calculate total unrealized P&L across all open positions
     *
     * @return Sum of unrealized P&L
     */
    suspend fun calculateTotalUnrealizedPnL(): Double {
        return try {
            val openPositions = positionDao.getOpenPositions().first()
            openPositions.sumOf { it.unrealizedPnL }
        } catch (e: Exception) {
            Timber.e(e, "Error calculating total unrealized P&L")
            0.0
        }
    }

    /**
     * Check if stop-loss or take-profit is triggered for a position
     *
     * @param positionId Position to check
     * @return Result<Boolean> - true if triggered and position was closed
     */
    suspend fun checkStopLossTakeProfit(positionId: String): Result<Boolean> {
        return try {
            // Get position from database
            val positionEntity = positionDao.getPositionById(positionId)
                ?: return Result.failure(Exception("Position not found: $positionId"))

            if (positionEntity.status != PositionStatus.OPEN.toString()) {
                return Result.success(false)
            }

            // Convert to domain model
            val position = positionEntity.toDomain()

            // Get current market price
            val tickerResult = krakenRepository.getTicker(position.pair)
            if (tickerResult.isFailure) {
                Timber.w("Failed to get ticker for ${position.pair}")
                return Result.success(false)
            }

            val currentPrice = tickerResult.getOrNull()!!.last

            // Check stop-loss
            if (position.isStopLossTriggered(currentPrice)) {
                Timber.i("Stop-loss triggered for position $positionId at price $currentPrice")

                // Close position via order placement
                val closeResult = closePositionViaOrder(position, currentPrice, "STOP_LOSS")
                if (closeResult.isSuccess) {
                    Timber.d("Position closed via stop-loss: $positionId")
                    return Result.success(true)
                } else {
                    Timber.e("Failed to close position via stop-loss: ${closeResult.exceptionOrNull()?.message}")
                }
            }

            // Check take-profit
            if (position.isTakeProfitTriggered(currentPrice)) {
                Timber.i("Take-profit triggered for position $positionId at price $currentPrice")

                // Close position via order placement
                val closeResult = closePositionViaOrder(position, currentPrice, "TAKE_PROFIT")
                if (closeResult.isSuccess) {
                    Timber.d("Position closed via take-profit: $positionId")
                    return Result.success(true)
                } else {
                    Timber.e("Failed to close position via take-profit: ${closeResult.exceptionOrNull()?.message}")
                }
            }

            Result.success(false)
        } catch (e: Exception) {
            Timber.e(e, "Error checking stop-loss/take-profit")
            Result.failure(e)
        }
    }

    /**
     * Get open position for a trading pair
     *
     * @param pair Trading pair
     * @return Position or null if no open position exists
     */
    suspend fun getOpenPositionForPair(pair: String): Position? {
        return try {
            positionDao.getOpenPositionForPair(pair)?.toDomain()
        } catch (e: Exception) {
            Timber.e(e, "Error getting open position for pair")
            null
        }
    }

    /**
     * Get statistics for a strategy
     *
     * @param strategyId Strategy ID
     * @return Triple of (total P&L, win rate, average return %)
     */
    suspend fun getStrategyStats(strategyId: String): Triple<Double, Double, Double> {
        return try {
            val totalPnL = positionDao.getTotalRealizedPnL(strategyId) ?: 0.0
            val winCount = positionDao.getWinningPositionCount(strategyId)
            val totalCount = positionDao.getTotalClosedPositionCount(strategyId)
            val winRate = if (totalCount > 0) (winCount.toDouble() / totalCount) * 100 else 0.0
            val avgReturn = positionDao.getAverageReturnPercent(strategyId) ?: 0.0

            Triple(totalPnL, winRate, avgReturn)
        } catch (e: Exception) {
            Timber.e(e, "Error getting strategy stats")
            Triple(0.0, 0.0, 0.0)
        }
    }

    // Private helper methods

    /**
     * Place stop-loss order for a position
     */
    private suspend fun placeStopLossOrder(
        positionId: String,
        pair: String,
        side: PositionSide,
        quantity: Double,
        stopPrice: Double
    ): Result<Order> {
        // Stop-loss order is opposite of position side
        val orderType = when (side) {
            PositionSide.LONG -> TradeType.SELL
            PositionSide.SHORT -> TradeType.BUY
        }

        return orderRepository.placeOrder(
            pair = pair,
            type = orderType,
            orderType = OrderType.STOP_LOSS,
            volume = quantity,
            stopPrice = stopPrice,
            positionId = positionId
        )
    }

    /**
     * Place take-profit order for a position
     */
    private suspend fun placeTakeProfitOrder(
        positionId: String,
        pair: String,
        side: PositionSide,
        quantity: Double,
        targetPrice: Double
    ): Result<Order> {
        // Take-profit order is opposite of position side
        val orderType = when (side) {
            PositionSide.LONG -> TradeType.SELL
            PositionSide.SHORT -> TradeType.BUY
        }

        return orderRepository.placeOrder(
            pair = pair,
            type = orderType,
            orderType = OrderType.TAKE_PROFIT,
            volume = quantity,
            price = targetPrice,
            positionId = positionId
        )
    }

    /**
     * Close position by placing a market order
     */
    private suspend fun closePositionViaOrder(
        position: Position,
        exitPrice: Double,
        closeReason: String
    ): Result<Position> {
        // Determine order type (opposite of position side)
        val orderType = when (position.side) {
            PositionSide.LONG -> TradeType.SELL
            PositionSide.SHORT -> TradeType.BUY
        }

        // Place market order to close position
        val orderResult = orderRepository.placeOrder(
            pair = position.pair,
            type = orderType,
            orderType = OrderType.MARKET,
            volume = position.quantity,
            positionId = position.id
        )

        if (orderResult.isFailure) {
            return Result.failure(orderResult.exceptionOrNull() ?: Exception("Failed to place close order"))
        }

        val order = orderResult.getOrNull()!!

        // Close position in database
        return closePosition(
            positionId = position.id,
            exitPrice = exitPrice,
            closeReason = closeReason,
            exitTradeId = order.krakenOrderId
        )
    }

    /**
     * Convert PositionEntity to domain Position
     */
    private fun PositionEntity.toDomain(): Position {
        return Position(
            id = id,
            strategyId = strategyId,
            pair = pair,
            side = PositionSide.fromString(type),
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
            status = PositionStatus.fromString(status),
            lastUpdated = lastUpdated
        )
    }

    /**
     * Convert domain Position to PositionEntity
     */
    private fun Position.toEntity(): PositionEntity {
        return PositionEntity(
            id = id,
            strategyId = strategyId,
            pair = pair,
            type = side.toString(),
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
            status = status.toString(),
            lastUpdated = lastUpdated
        )
    }
}
