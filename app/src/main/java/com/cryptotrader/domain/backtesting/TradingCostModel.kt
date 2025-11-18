package com.cryptotrader.domain.backtesting

import timber.log.Timber

/**
 * Trading cost model for realistic backtesting
 * Includes fees, slippage, and spread costs
 *
 * Kraken Spot Trading Fees (as of 2024):
 * - Maker: 0.16% for < $50K volume
 * - Taker: 0.26% for < $50K volume
 *
 * Spread (typical):
 * - BTC/USD: ~0.02% (1 basis point each side = 0.01% impact per trade)
 * - ETH/USD: ~0.03%
 * - Altcoins: 0.05-0.10%
 *
 * Slippage (typical for market orders):
 * - Small orders (<$1K): ~0.01-0.02%
 * - Medium orders ($1K-$10K): ~0.02-0.05%
 * - Large orders (>$10K): ~0.05-0.15%
 * - Very large orders (>$100K): Can be 0.2-0.5% or more
 */
data class TradingCostModel(
    val makerFee: Double = 0.0016,  // 0.16% - Kraken maker fee (0-100k volume tier)
    val takerFee: Double = 0.0026,  // 0.26% - Kraken taker fee (0-100k volume tier)
    val slippagePercent: Double = 0.05,  // 0.05% average slippage
    val spreadPercent: Double = 0.02,  // 0.02% bid-ask spread cost
    val useRealisticSlippage: Boolean = true,  // Enable dynamic slippage based on volume
    val useTieredFees: Boolean = false  // Use Kraken's tiered fee structure
) {
    /**
     * Calculate total cost for a trade
     *
     * @param orderType Whether this is a market order (taker) or limit order (maker)
     * @param orderValue Total value of the order in quote currency
     * @param volume30Day 30-day trading volume for tiered fee calculation
     * @param isLargeOrder Whether this order is large relative to market depth
     * @return TradeCost breakdown
     */
    fun calculateTradeCost(
        orderType: OrderExecutionType,
        orderValue: Double,
        volume30Day: Double = 0.0,
        isLargeOrder: Boolean = false
    ): TradeCost {
        // 1. Fee calculation
        val baseFee = when (orderType) {
            OrderExecutionType.MAKER -> if (useTieredFees) getTieredMakerFee(volume30Day) else makerFee
            OrderExecutionType.TAKER -> if (useTieredFees) getTieredTakerFee(volume30Day) else takerFee
        }
        val feeAmount = orderValue * baseFee

        // 2. Slippage calculation
        val slippageAmount = if (useRealisticSlippage) {
            calculateRealisticSlippage(orderValue, isLargeOrder)
        } else {
            orderValue * (slippagePercent / 100.0)
        }

        // 3. Spread cost (always applies)
        // FIX BUG 2.2: Spread cost is only HALF the spread
        // When BUYING: pay at ASK (mid + half spread)
        // When SELLING: receive at BID (mid - half spread)
        // We only pay ONE side of the spread, not both
        val halfSpreadPercent = spreadPercent / 2.0
        val spreadCost = orderValue * (halfSpreadPercent / 100.0)

        Timber.d("Spread cost calculation: orderValue=${"%.2f".format(orderValue)}, " +
                "fullSpread=${"%.4f".format(spreadPercent)}%, " +
                "halfSpread=${"%.4f".format(halfSpreadPercent)}%, " +
                "cost=${"%.2f".format(spreadCost)}")

        val totalCost = feeAmount + slippageAmount + spreadCost
        val totalCostPercent = (totalCost / orderValue) * 100.0

        // Comprehensive cost breakdown logging
        Timber.i("=== TRADING COST BREAKDOWN ===")
        Timber.i("Order Value: $${"%.2f".format(orderValue)}")
        Timber.i("Exchange Fee (${"%.4f".format(baseFee * 100.0)}%): $${"%.2f".format(feeAmount)}")
        Timber.i("Spread Cost (${"%.4f".format(halfSpreadPercent)}% half-spread): $${"%.2f".format(spreadCost)}")
        Timber.i("Slippage (${"%.4f".format((slippageAmount / orderValue) * 100.0)}%): $${"%.2f".format(slippageAmount)}")
        Timber.i("Total Cost: $${"%.2f".format(totalCost)} (${"%.4f".format(totalCostPercent)}% of order)")
        Timber.i("==============================")

        return TradeCost(
            feeAmount = feeAmount,
            feePercent = baseFee * 100.0,
            slippageAmount = slippageAmount,
            slippagePercent = (slippageAmount / orderValue) * 100.0,
            spreadCost = spreadCost,
            spreadPercent = halfSpreadPercent,  // FIX: Return the actual half-spread used, not full spread
            totalCost = totalCost,
            totalCostPercent = totalCostPercent
        )
    }

    /**
     * Get Kraken tiered maker fee based on 30-day volume
     * Source: https://www.kraken.com/features/fee-schedule
     */
    private fun getTieredMakerFee(volume30Day: Double): Double {
        return when {
            volume30Day < 50_000 -> 0.0016      // 0.16%
            volume30Day < 100_000 -> 0.0014     // 0.14%
            volume30Day < 250_000 -> 0.0012     // 0.12%
            volume30Day < 500_000 -> 0.0010     // 0.10%
            volume30Day < 1_000_000 -> 0.0008   // 0.08%
            volume30Day < 2_500_000 -> 0.0006   // 0.06%
            volume30Day < 5_000_000 -> 0.0004   // 0.04%
            volume30Day < 10_000_000 -> 0.0002  // 0.02%
            else -> 0.0000                       // 0.00%
        }
    }

    /**
     * Get Kraken tiered taker fee based on 30-day volume
     */
    private fun getTieredTakerFee(volume30Day: Double): Double {
        return when {
            volume30Day < 50_000 -> 0.0026      // 0.26%
            volume30Day < 100_000 -> 0.0024     // 0.24%
            volume30Day < 250_000 -> 0.0022     // 0.22%
            volume30Day < 500_000 -> 0.0020     // 0.20%
            volume30Day < 1_000_000 -> 0.0018   // 0.18%
            volume30Day < 2_500_000 -> 0.0016   // 0.16%
            volume30Day < 5_000_000 -> 0.0014   // 0.14%
            volume30Day < 10_000_000 -> 0.0012  // 0.12%
            else -> 0.0010                       // 0.10%
        }
    }

    /**
     * Calculate realistic slippage based on order size
     * Larger orders experience more slippage due to market depth
     *
     * FIX BUG 2.1: Apply multiplier to PERCENTAGE, not dollar amount
     * This ensures slippage scales appropriately with order size
     */
    private fun calculateRealisticSlippage(orderValue: Double, isLargeOrder: Boolean): Double {
        // Apply multiplier to the PERCENTAGE, not the dollar amount
        val adjustedSlippagePercent = when {
            isLargeOrder -> slippagePercent * 3.0           // Very large orders: 3x slippage %
            orderValue > 100_000 -> slippagePercent * 2.0   // >$100K: 2x slippage %
            orderValue > 50_000 -> slippagePercent * 1.5    // >$50K: 1.5x slippage %
            orderValue > 10_000 -> slippagePercent * 1.25   // >$10K: 1.25x slippage %
            else -> slippagePercent                         // Small orders: normal slippage %
        }

        val slippageCost = orderValue * (adjustedSlippagePercent / 100.0)

        // Add logging to verify slippage calculation
        Timber.d("Slippage calculation: orderValue=${"%.2f".format(orderValue)}, " +
                "baseSlippage=${"%.4f".format(slippagePercent)}%, " +
                "adjusted=${"%.4f".format(adjustedSlippagePercent)}%, " +
                "cost=${"%.2f".format(slippageCost)}")

        return slippageCost
    }
}

/**
 * Order execution type for fee calculation
 */
enum class OrderExecutionType {
    MAKER,  // Limit order that adds liquidity (lower fee)
    TAKER   // Market order that removes liquidity (higher fee)
}

/**
 * Detailed breakdown of trade costs
 */
data class TradeCost(
    val feeAmount: Double,
    val feePercent: Double,
    val slippageAmount: Double,
    val slippagePercent: Double,
    val spreadCost: Double,
    val spreadPercent: Double,
    val totalCost: Double,
    val totalCostPercent: Double
)
