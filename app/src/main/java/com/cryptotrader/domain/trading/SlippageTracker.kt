package com.cryptotrader.domain.trading

import com.cryptotrader.domain.model.Trade
import com.cryptotrader.domain.model.TradeSignal
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Slippage and fee tracking system
 *
 * Measures real execution costs to improve profitability estimates:
 * - Tracks slippage (difference between expected and actual price)
 * - Monitors trading fees
 * - Calculates true cost of execution
 * - Provides analytics on execution quality
 */
@Singleton
class SlippageTracker @Inject constructor() {

    // Historical slippage data
    private val slippageHistory = mutableListOf<SlippageRecord>()
    private val maxHistorySize = 1000

    /**
     * Record slippage for an executed trade
     *
     * @param signal Original trade signal with target price
     * @param trade Actual executed trade
     * @return Slippage record
     */
    fun recordSlippage(signal: TradeSignal, trade: Trade): SlippageRecord {
        val expectedPrice = signal.targetPrice ?: trade.price
        val actualPrice = trade.price

        // Calculate slippage
        val slippageAmount = actualPrice - expectedPrice
        val slippagePercent = if (expectedPrice > 0) {
            (slippageAmount / expectedPrice) * 100.0
        } else 0.0

        // Calculate fee amount (Kraken typically charges 0.16-0.26%)
        val feePercent = trade.fee / (trade.price * trade.volume) * 100.0
        val feeAmount = trade.fee

        // Calculate total cost
        val totalCostPercent = abs(slippagePercent) + feePercent
        val totalCostAmount = abs(slippageAmount * trade.volume) + feeAmount

        val record = SlippageRecord(
            timestamp = trade.timestamp,
            pair = trade.pair,
            expectedPrice = expectedPrice,
            actualPrice = actualPrice,
            slippageAmount = slippageAmount,
            slippagePercent = slippagePercent,
            feeAmount = feeAmount,
            feePercent = feePercent,
            totalCostPercent = totalCostPercent,
            totalCostAmount = totalCostAmount,
            volume = trade.volume
        )

        // Add to history
        slippageHistory.add(record)
        if (slippageHistory.size > maxHistorySize) {
            slippageHistory.removeAt(0)
        }

        Timber.i("Slippage recorded: ${trade.pair} - Slippage: ${String.format("%.4f", slippagePercent)}%, " +
                "Fee: ${String.format("%.4f", feePercent)}%, Total cost: ${String.format("%.4f", totalCostPercent)}%")

        return record
    }

    /**
     * Get slippage statistics
     */
    fun getSlippageStatistics(): SlippageStatistics {
        if (slippageHistory.isEmpty()) {
            return SlippageStatistics(
                avgSlippagePercent = 0.0,
                avgFeePercent = 0.0,
                avgTotalCostPercent = 0.0,
                maxSlippagePercent = 0.0,
                minSlippagePercent = 0.0,
                tradeCount = 0
            )
        }

        val avgSlippage = slippageHistory.map { it.slippagePercent }.average()
        val avgFee = slippageHistory.map { it.feePercent }.average()
        val avgTotalCost = slippageHistory.map { it.totalCostPercent }.average()
        val maxSlippage = slippageHistory.maxOf { it.slippagePercent }
        val minSlippage = slippageHistory.minOf { it.slippagePercent }

        return SlippageStatistics(
            avgSlippagePercent = avgSlippage,
            avgFeePercent = avgFee,
            avgTotalCostPercent = avgTotalCost,
            maxSlippagePercent = maxSlippage,
            minSlippagePercent = minSlippage,
            tradeCount = slippageHistory.size
        )
    }

    /**
     * Get slippage statistics for a specific pair
     */
    fun getSlippageStatisticsForPair(pair: String): SlippageStatistics {
        val pairHistory = slippageHistory.filter { it.pair == pair }

        if (pairHistory.isEmpty()) {
            return SlippageStatistics(
                avgSlippagePercent = 0.0,
                avgFeePercent = 0.0,
                avgTotalCostPercent = 0.0,
                maxSlippagePercent = 0.0,
                minSlippagePercent = 0.0,
                tradeCount = 0
            )
        }

        return SlippageStatistics(
            avgSlippagePercent = pairHistory.map { it.slippagePercent }.average(),
            avgFeePercent = pairHistory.map { it.feePercent }.average(),
            avgTotalCostPercent = pairHistory.map { it.totalCostPercent }.average(),
            maxSlippagePercent = pairHistory.maxOf { it.slippagePercent },
            minSlippagePercent = pairHistory.minOf { it.slippagePercent },
            tradeCount = pairHistory.size
        )
    }

    /**
     * Get recent slippage records
     */
    fun getRecentSlippage(count: Int = 20): List<SlippageRecord> {
        return slippageHistory.takeLast(count)
    }

    /**
     * Estimate execution cost for a potential trade
     */
    fun estimateExecutionCost(pair: String, expectedPrice: Double, volume: Double): ExecutionCostEstimate {
        val stats = getSlippageStatisticsForPair(pair)

        // Use historical averages or defaults
        val estimatedSlippagePercent = if (stats.tradeCount > 0) {
            stats.avgSlippagePercent
        } else {
            0.05 // Default: 0.05% slippage
        }

        val estimatedFeePercent = if (stats.tradeCount > 0) {
            stats.avgFeePercent
        } else {
            0.16 // Kraken standard fee
        }

        val estimatedSlippageAmount = expectedPrice * volume * (estimatedSlippagePercent / 100.0)
        val estimatedFeeAmount = expectedPrice * volume * (estimatedFeePercent / 100.0)
        val totalCost = abs(estimatedSlippageAmount) + estimatedFeeAmount

        return ExecutionCostEstimate(
            pair = pair,
            expectedPrice = expectedPrice,
            volume = volume,
            estimatedSlippagePercent = estimatedSlippagePercent,
            estimatedFeePercent = estimatedFeePercent,
            estimatedSlippageAmount = estimatedSlippageAmount,
            estimatedFeeAmount = estimatedFeeAmount,
            totalEstimatedCost = totalCost,
            basedOnTrades = stats.tradeCount
        )
    }

    /**
     * Clear slippage history
     */
    fun clearHistory() {
        slippageHistory.clear()
    }
}

/**
 * Slippage record for a single trade
 */
data class SlippageRecord(
    val timestamp: Long,
    val pair: String,
    val expectedPrice: Double,
    val actualPrice: Double,
    val slippageAmount: Double,
    val slippagePercent: Double,
    val feeAmount: Double,
    val feePercent: Double,
    val totalCostPercent: Double,
    val totalCostAmount: Double,
    val volume: Double
)

/**
 * Slippage statistics
 */
data class SlippageStatistics(
    val avgSlippagePercent: Double,
    val avgFeePercent: Double,
    val avgTotalCostPercent: Double,
    val maxSlippagePercent: Double,
    val minSlippagePercent: Double,
    val tradeCount: Int
)

/**
 * Execution cost estimate
 */
data class ExecutionCostEstimate(
    val pair: String,
    val expectedPrice: Double,
    val volume: Double,
    val estimatedSlippagePercent: Double,
    val estimatedFeePercent: Double,
    val estimatedSlippageAmount: Double,
    val estimatedFeeAmount: Double,
    val totalEstimatedCost: Double,
    val basedOnTrades: Int
)
