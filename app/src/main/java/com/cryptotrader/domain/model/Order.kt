package com.cryptotrader.domain.model

/**
 * Domain model representing a trading order
 */
data class Order(
    val id: String,
    val positionId: String?,
    val pair: String,
    val type: TradeType,
    val orderType: OrderType,
    val quantity: Double,
    val price: Double?,
    val stopPrice: Double?,
    val krakenOrderId: String?,
    val status: OrderStatus,
    val placedAt: Long,
    val filledAt: Long? = null,
    val cancelledAt: Long? = null,
    val filledQuantity: Double = 0.0,
    val averageFillPrice: Double? = null,
    val fee: Double? = null,
    val errorMessage: String? = null
)

/**
 * Order status enum
 */
enum class OrderStatus {
    PENDING,          // Order created locally, not yet sent to exchange
    OPEN,             // Order placed on exchange, waiting for execution
    FILLED,           // Order completely filled
    PARTIALLY_FILLED, // Order partially filled
    CANCELLED,        // Order cancelled by user or system
    REJECTED,         // Order rejected by exchange
    EXPIRED;          // Order expired (for time-limited orders)

    override fun toString(): String = name.uppercase()

    companion object {
        fun fromString(value: String): OrderStatus {
            return when (value.uppercase()) {
                "PENDING" -> PENDING
                "OPEN" -> OPEN
                "FILLED" -> FILLED
                "PARTIALLY_FILLED" -> PARTIALLY_FILLED
                "CANCELLED" -> CANCELLED
                "REJECTED" -> REJECTED
                "EXPIRED" -> EXPIRED
                else -> PENDING
            }
        }

        /**
         * Map Kraken status to our OrderStatus
         * Kraken statuses: pending, open, closed, canceled, expired
         */
        fun fromKrakenStatus(krakenStatus: String, volumeExecuted: Double, volume: Double): OrderStatus {
            return when (krakenStatus.lowercase()) {
                "pending" -> PENDING
                "open" -> {
                    // Check if partially filled
                    if (volumeExecuted > 0 && volumeExecuted < volume) {
                        PARTIALLY_FILLED
                    } else {
                        OPEN
                    }
                }
                "closed" -> FILLED
                "canceled", "cancelled" -> CANCELLED
                "expired" -> EXPIRED
                else -> PENDING
            }
        }
    }
}

/**
 * Order type enum
 */
enum class OrderType {
    MARKET,              // Execute immediately at best available price
    LIMIT,               // Execute only at specified price or better
    STOP_LOSS,           // Market order triggered when price hits stop price
    TAKE_PROFIT,         // Market order triggered when price hits take profit price
    STOP_LOSS_LIMIT,     // Limit order triggered when price hits stop price
    TAKE_PROFIT_LIMIT,   // Limit order triggered when price hits take profit price
    TRAILING_STOP,       // Stop loss that follows price movement (native Kraken)
    TRAILING_STOP_LIMIT; // Limit order with trailing stop (native Kraken)

    override fun toString(): String = name.lowercase().replace("_", "-")

    companion object {
        fun fromString(value: String): OrderType {
            return when (value.lowercase().replace("-", "_")) {
                "market" -> MARKET
                "limit" -> LIMIT
                "stop_loss", "stop-loss" -> STOP_LOSS
                "take_profit", "take-profit" -> TAKE_PROFIT
                "stop_loss_limit", "stop-loss-limit" -> STOP_LOSS_LIMIT
                "take_profit_limit", "take-profit-limit" -> TAKE_PROFIT_LIMIT
                "trailing_stop", "trailing-stop" -> TRAILING_STOP
                "trailing_stop_limit", "trailing-stop-limit" -> TRAILING_STOP_LIMIT
                else -> MARKET
            }
        }

        /**
         * Convert to Kraken API format
         */
        fun toKrakenFormat(orderType: OrderType): String {
            return when (orderType) {
                MARKET -> "market"
                LIMIT -> "limit"
                STOP_LOSS -> "stop-loss"
                TAKE_PROFIT -> "take-profit"
                STOP_LOSS_LIMIT -> "stop-loss-limit"
                TAKE_PROFIT_LIMIT -> "take-profit-limit"
                TRAILING_STOP -> "trailing-stop"
                TRAILING_STOP_LIMIT -> "trailing-stop-limit"
            }
        }
    }
}
