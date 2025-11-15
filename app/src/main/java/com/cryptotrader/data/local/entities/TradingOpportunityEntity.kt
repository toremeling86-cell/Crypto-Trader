package com.cryptotrader.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Trading opportunity entity
 * Represents specific trading opportunities identified by the AI Advisor
 */
@Entity(
    tableName = "trading_opportunities",
    foreignKeys = [
        ForeignKey(
            entity = AdvisorAnalysisEntity::class,
            parentColumns = ["id"],
            childColumns = ["analysisId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("analysisId"),
        Index("asset"),
        Index("timestamp"),
        Index("status"),
        Index("priority")
    ]
)
data class TradingOpportunityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    // Link to parent analysis
    val analysisId: Long,

    // Opportunity details
    val asset: String, // e.g., "BTC/USD", "ETH/USD"
    val direction: String, // "LONG" or "SHORT"
    val timeframe: String, // "SHORT_TERM", "MEDIUM_TERM", "LONG_TERM"

    // Trade parameters
    val entryPrice: Double,
    val targetPrice: Double,
    val stopLoss: Double,
    val potentialGainPercent: Double,
    val riskRewardRatio: Double,

    // Analysis
    val rationale: String, // Explanation of why this is a good opportunity
    val confidence: Double, // 0.0 to 1.0
    val priority: String, // "LOW", "MEDIUM", "HIGH", "URGENT"

    // Supporting data
    val technicalSignals: String, // JSON array of technical indicators supporting this trade
    val riskFactors: String, // JSON array of specific risks for this opportunity

    // Status tracking
    val status: String = "ACTIVE", // "ACTIVE", "EXPIRED", "EXECUTED", "DISMISSED"
    val expiresAt: Long? = null, // Optional expiration timestamp

    // Metadata
    val notificationSent: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
