package com.cryptotrader.data.repository

import com.cryptotrader.data.local.dao.TradeDao
import com.cryptotrader.data.mapper.toDomain
import com.cryptotrader.domain.model.Trade
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing trade data
 *
 * Responsibilities:
 * - Provide access to trade history
 * - Calculate trade statistics
 * - Support Kelly Criterion and performance calculations
 */
@Singleton
class TradeRepository @Inject constructor(
    private val tradeDao: TradeDao
) {

    /**
     * Get all trades as a list (synchronous)
     *
     * @return List of all trades ordered by timestamp ascending
     */
    suspend fun getAllTrades(): List<Trade> {
        return try {
            tradeDao.getAllTrades().map { it.toDomain() }
        } catch (e: Exception) {
            Timber.e(e, "Error getting all trades")
            emptyList()
        }
    }

    /**
     * Get all trades as Flow for reactive updates
     *
     * @return Flow emitting list of all trades
     */
    fun getAllTradesFlow(): Flow<List<Trade>> {
        return tradeDao.getAllTradesFlow().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /**
     * Get recent trades with limit
     *
     * @param limit Maximum number of trades to return
     * @return Flow emitting list of recent trades
     */
    fun getRecentTrades(limit: Int = 50): Flow<List<Trade>> {
        return tradeDao.getRecentTrades(limit).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /**
     * Get trades for a specific strategy
     *
     * @param strategyId Strategy ID
     * @return Flow emitting list of trades for the strategy
     */
    fun getTradesByStrategy(strategyId: String): Flow<List<Trade>> {
        return tradeDao.getTradesByStrategy(strategyId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /**
     * Get trades for a specific strategy as a list (synchronous)
     *
     * @param strategyId Strategy ID
     * @return List of trades for the strategy
     */
    suspend fun getTradesByStrategySync(strategyId: String): List<Trade> {
        return try {
            tradeDao.getAllTrades()
                .filter { it.strategyId == strategyId }
                .map { it.toDomain() }
        } catch (e: Exception) {
            Timber.e(e, "Error getting trades for strategy $strategyId")
            emptyList()
        }
    }

    /**
     * Get trades for a specific trading pair
     *
     * @param pair Trading pair
     * @param limit Maximum number of trades to return
     * @return Flow emitting list of trades for the pair
     */
    fun getTradesByPair(pair: String, limit: Int = 100): Flow<List<Trade>> {
        return tradeDao.getTradesByPair(pair, limit).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /**
     * Get trades since a specific timestamp
     *
     * @param since Timestamp in milliseconds
     * @return Flow emitting list of trades since the timestamp
     */
    fun getTradesSince(since: Long): Flow<List<Trade>> {
        return tradeDao.getTradesSince(since).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /**
     * Get trades between two timestamps
     *
     * @param startTime Start timestamp in milliseconds
     * @param endTime End timestamp in milliseconds
     * @return Flow emitting list of trades in the time range
     */
    fun getTradesBetween(startTime: Long, endTime: Long): Flow<List<Trade>> {
        return tradeDao.getTradesBetween(startTime, endTime).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /**
     * Get total realized P&L for a strategy
     *
     * @param strategyId Strategy ID
     * @return Total realized P&L
     */
    suspend fun getTotalRealizedPnL(strategyId: String): Double {
        return try {
            tradeDao.getTotalRealizedPnL(strategyId) ?: 0.0
        } catch (e: Exception) {
            Timber.e(e, "Error getting total realized P&L for strategy $strategyId")
            0.0
        }
    }

    /**
     * Get average realized P&L percentage for a strategy
     *
     * @param strategyId Strategy ID
     * @return Average realized P&L percentage
     */
    suspend fun getAverageRealizedPnLPercent(strategyId: String): Double {
        return try {
            tradeDao.getAverageRealizedPnLPercent(strategyId) ?: 0.0
        } catch (e: Exception) {
            Timber.e(e, "Error getting average realized P&L percent for strategy $strategyId")
            0.0
        }
    }

    /**
     * Get winning trade count for a strategy
     *
     * @param strategyId Strategy ID
     * @return Number of winning trades
     */
    suspend fun getWinningTradeCount(strategyId: String): Int {
        return try {
            tradeDao.getWinningTradeCount(strategyId)
        } catch (e: Exception) {
            Timber.e(e, "Error getting winning trade count for strategy $strategyId")
            0
        }
    }

    /**
     * Get executed trade count for a strategy
     *
     * @param strategyId Strategy ID
     * @return Number of executed trades
     */
    suspend fun getExecutedTradeCount(strategyId: String): Int {
        return try {
            tradeDao.getExecutedTradeCount(strategyId)
        } catch (e: Exception) {
            Timber.e(e, "Error getting executed trade count for strategy $strategyId")
            0
        }
    }

    /**
     * Get total fees paid for a strategy
     *
     * @param strategyId Strategy ID
     * @return Total fees
     */
    suspend fun getTotalFees(strategyId: String): Double {
        return try {
            tradeDao.getTotalFees(strategyId) ?: 0.0
        } catch (e: Exception) {
            Timber.e(e, "Error getting total fees for strategy $strategyId")
            0.0
        }
    }

    /**
     * Calculate actual average win percentage from trade history
     * Used by Kelly Criterion for accurate position sizing
     *
     * @param strategyId Strategy ID
     * @return Average win percentage or null if no winning trades
     */
    suspend fun calculateActualAvgWinPercent(strategyId: String): Double? {
        return try {
            val allTrades = getAllTrades()
            val winningTrades = allTrades.filter {
                it.strategyId == strategyId && it.profit != null && it.profit > 0
            }

            if (winningTrades.isEmpty()) {
                Timber.d("No winning trades found for strategy $strategyId")
                return null
            }

            val avgWinPercent = winningTrades.mapNotNull { trade ->
                trade.profit?.let { profit ->
                    (profit / trade.cost) * 100.0
                }
            }.average()

            Timber.d("Strategy $strategyId - Actual avg win: ${"%.2f".format(avgWinPercent)}% from ${winningTrades.size} trades")
            avgWinPercent
        } catch (e: Exception) {
            Timber.e(e, "Error calculating avg win percent for strategy $strategyId")
            null
        }
    }

    /**
     * Calculate actual average loss percentage from trade history
     * Used by Kelly Criterion for accurate position sizing
     *
     * @param strategyId Strategy ID
     * @return Average loss percentage (as positive number) or null if no losing trades
     */
    suspend fun calculateActualAvgLossPercent(strategyId: String): Double? {
        return try {
            val allTrades = getAllTrades()
            val losingTrades = allTrades.filter {
                it.strategyId == strategyId && it.profit != null && it.profit < 0
            }

            if (losingTrades.isEmpty()) {
                Timber.d("No losing trades found for strategy $strategyId")
                return null
            }

            val avgLossPercent = losingTrades.mapNotNull { trade ->
                trade.profit?.let { profit ->
                    kotlin.math.abs((profit / trade.cost) * 100.0)
                }
            }.average()

            Timber.d("Strategy $strategyId - Actual avg loss: ${"%.2f".format(avgLossPercent)}% from ${losingTrades.size} trades")
            avgLossPercent
        } catch (e: Exception) {
            Timber.e(e, "Error calculating avg loss percent for strategy $strategyId")
            null
        }
    }
}
