package com.cryptotrader.domain.model

import com.cryptotrader.utils.toBigDecimalMoney
import java.math.BigDecimal

/**
 * Domain model for a trade
 *
 * BigDecimal Migration (Phase 2.9):
 * - All monetary fields now use BigDecimal for exact calculations
 * - Double fields deprecated but kept for backward compatibility
 * - New code should use *Decimal fields exclusively
 */
data class Trade(
    val id: Long = 0,
    val orderId: String,
    val pair: String,
    val type: TradeType,

    // Price fields - exact decimal arithmetic
    @Deprecated("Use priceDecimal for exact calculations", ReplaceWith("priceDecimal"))
    val price: Double,
    val priceDecimal: BigDecimal = price.toBigDecimalMoney(),

    // Volume fields - exact decimal arithmetic
    @Deprecated("Use volumeDecimal for exact calculations", ReplaceWith("volumeDecimal"))
    val volume: Double,
    val volumeDecimal: BigDecimal = volume.toBigDecimalMoney(),

    // Cost fields - exact decimal arithmetic
    @Deprecated("Use costDecimal for exact calculations", ReplaceWith("costDecimal"))
    val cost: Double,
    val costDecimal: BigDecimal = cost.toBigDecimalMoney(),

    // Fee fields - exact decimal arithmetic
    @Deprecated("Use feeDecimal for exact calculations", ReplaceWith("feeDecimal"))
    val fee: Double,
    val feeDecimal: BigDecimal = fee.toBigDecimalMoney(),

    val timestamp: Long,
    val strategyId: String? = null,
    val status: TradeStatus = TradeStatus.EXECUTED,

    // Profit fields - exact decimal arithmetic
    @Deprecated("Use profitDecimal for exact calculations", ReplaceWith("profitDecimal"))
    val profit: Double? = null,
    val profitDecimal: BigDecimal? = profit?.toBigDecimalMoney(),

    val notes: String? = null,
    val entryTime: Long? = null,
    val exitTime: Long? = null
)

enum class TradeType {
    BUY, SELL;

    override fun toString(): String = name.lowercase()

    companion object {
        fun fromString(value: String): TradeType {
            return when (value.lowercase()) {
                "buy" -> BUY
                "sell" -> SELL
                else -> throw IllegalArgumentException("Unknown trade type: $value")
            }
        }
    }
}

enum class TradeStatus {
    PENDING, EXECUTED, FAILED, CANCELLED;

    override fun toString(): String = name.lowercase()

    companion object {
        fun fromString(value: String): TradeStatus {
            return when (value.lowercase()) {
                "pending" -> PENDING
                "executed" -> EXECUTED
                "failed" -> FAILED
                "cancelled" -> CANCELLED
                else -> PENDING
            }
        }
    }
}
