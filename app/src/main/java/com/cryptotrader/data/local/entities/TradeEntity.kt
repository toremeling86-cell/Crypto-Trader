package com.cryptotrader.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

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
    val price: Double,
    val volume: Double,
    val cost: Double,
    val fee: Double,
    val timestamp: Long = System.currentTimeMillis(),
    val strategyId: String? = null,
    val status: String = "executed", // executed, pending, failed, cancelled
    val profit: Double? = null, // Calculated profit/loss
    val notes: String? = null,

    // New execution tracking fields (version 7+)
    val orderType: String = "MARKET", // MARKET, LIMIT, STOP_LOSS, TAKE_PROFIT
    val feeCurrency: String = "USD", // Currency of the fee
    val krakenOrderId: String? = null, // Kraken order reference
    val krakenTradeId: String? = null, // Kraken trade reference
    val realizedPnL: Double? = null, // Realized profit/loss
    val realizedPnLPercent: Double? = null, // Realized P&L percentage
    val executedAt: Long = timestamp, // Execution timestamp (separate from creation)
    val positionId: String? = null // Link to position if part of position management
)
