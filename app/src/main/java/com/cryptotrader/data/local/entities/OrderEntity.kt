package com.cryptotrader.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "orders",
    foreignKeys = [
        ForeignKey(
            entity = PositionEntity::class,
            parentColumns = ["id"],
            childColumns = ["positionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("positionId"), Index("krakenOrderId"), Index("status"), Index("placedAt")]
)
data class OrderEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),

    // Position relationship
    val positionId: String?,

    // Order details
    val pair: String,
    val type: String, // "BUY", "SELL"
    val orderType: String, // "MARKET", "LIMIT", "STOP_LOSS", "TAKE_PROFIT"
    val quantity: Double,
    val price: Double?,
    val stopPrice: Double?,

    // Kraken
    val krakenOrderId: String?,

    // Status
    val status: String, // "PENDING", "OPEN", "FILLED", "CANCELLED", "REJECTED"

    // Timing
    val placedAt: Long,
    val filledAt: Long? = null,
    val cancelledAt: Long? = null,

    // Execution
    val filledQuantity: Double = 0.0,
    val averageFillPrice: Double? = null,
    val fee: Double? = null,

    // Metadata
    val errorMessage: String? = null
)
