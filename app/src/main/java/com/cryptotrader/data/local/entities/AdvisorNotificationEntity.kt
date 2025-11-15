package com.cryptotrader.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Advisor notification entity
 * Tracks notifications sent to users about trading opportunities
 */
@Entity(
    tableName = "advisor_notifications",
    foreignKeys = [
        ForeignKey(
            entity = TradingOpportunityEntity::class,
            parentColumns = ["id"],
            childColumns = ["opportunityId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("opportunityId"),
        Index("timestamp"),
        Index("type"),
        Index("isRead")
    ]
)
data class AdvisorNotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    // Link to opportunity (nullable for general notifications)
    val opportunityId: Long? = null,

    // Notification details
    val type: String, // "OPPORTUNITY", "ANALYSIS_COMPLETE", "RISK_ALERT", "MARKET_UPDATE"
    val title: String,
    val message: String,
    val priority: String, // "LOW", "NORMAL", "HIGH"

    // Status
    val isRead: Boolean = false,
    val isDismissed: Boolean = false,
    val wasShown: Boolean = false, // Whether the notification was shown to the user

    // Metadata
    val timestamp: Long = System.currentTimeMillis(),
    val readAt: Long? = null,
    val dismissedAt: Long? = null
)
