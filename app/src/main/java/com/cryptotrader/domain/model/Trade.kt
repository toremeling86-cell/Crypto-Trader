package com.cryptotrader.domain.model

/**
 * Domain model for a trade
 */
data class Trade(
    val id: Long = 0,
    val orderId: String,
    val pair: String,
    val type: TradeType,
    val price: Double,
    val volume: Double,
    val cost: Double,
    val fee: Double,
    val timestamp: Long,
    val strategyId: String? = null,
    val status: TradeStatus = TradeStatus.EXECUTED,
    val profit: Double? = null,
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
