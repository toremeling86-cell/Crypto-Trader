package com.cryptotrader.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal

@Entity(
    tableName = "trades",
    foreignKeys = [
        ForeignKey(
            entity = StrategyEntity::class,
            parentColumns = ["id"],
            childColumns = ["strategyId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("strategyId"), Index("timestamp"), Index("pair"), Index("status"), Index("orderType")]
)
data class TradeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val orderId: String,
    val pair: String,
    val type: String, // "buy" or "sell" (kept for backwards compatibility, use BUY/SELL for new trades)

    // Legacy Double fields (deprecated, kept for backward compatibility)
    val price: Double,
    val volume: Double,
    val cost: Double,
    val fee: Double,

    // BigDecimal fields (version 20+) - exact calculations
    val priceDecimal: BigDecimal? = null,
    val volumeDecimal: BigDecimal? = null,
    val costDecimal: BigDecimal? = null,
    val feeDecimal: BigDecimal? = null,

    val timestamp: Long = System.currentTimeMillis(),
    val strategyId: String? = null,
    val status: String = "executed", // executed, pending, failed, cancelled
    val profit: Double? = null, // Calculated profit/loss (deprecated)
    val notes: String? = null,

    // New execution tracking fields (version 7+)
    val orderType: String? = null, // MARKET, LIMIT, STOP_LOSS, TAKE_PROFIT
    val feeCurrency: String? = null, // Currency of the fee
    val krakenOrderId: String? = null, // Kraken order reference
    val krakenTradeId: String? = null, // Kraken trade reference
    val realizedPnL: Double? = null, // Realized profit/loss (deprecated)
    val realizedPnLPercent: Double? = null, // Realized P&L percentage (deprecated)
    val executedAt: Long? = null, // Execution timestamp (separate from creation)
    val positionId: String? = null // Link to position if part of position management
)
