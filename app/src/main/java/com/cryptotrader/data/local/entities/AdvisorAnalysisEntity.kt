package com.cryptotrader.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * AI Advisor analysis entity
 * Stores comprehensive trading advice and market analysis from Claude AI
 */
@Entity(
    tableName = "advisor_analyses",
    indices = [
        Index("timestamp"),
        Index("triggerType"),
        Index("overallSentiment")
    ]
)
data class AdvisorAnalysisEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    // Analysis content
    val analysisText: String, // Full markdown-formatted analysis from Claude
    val overallSentiment: String, // "BULLISH", "BEARISH", "NEUTRAL", "MIXED"
    val confidenceLevel: Double, // 0.0 to 1.0

    // Market conditions
    val marketCondition: String, // "VOLATILE", "STABLE", "TRENDING_UP", "TRENDING_DOWN", "RANGING"
    val volatilityLevel: String, // "LOW", "MEDIUM", "HIGH", "EXTREME"

    // Structured data (stored as JSON strings)
    val keyInsights: String, // JSON array of key insights
    val riskFactors: String, // JSON array of identified risks
    val recommendations: String, // JSON array of actionable recommendations
    val technicalIndicators: String, // JSON object with technical analysis data

    // Trading opportunities count
    val opportunitiesCount: Int = 0, // Number of opportunities identified in this analysis

    // Metadata
    val triggerType: String, // "SCHEDULED", "MANUAL", "EVENT_TRIGGERED"
    val symbolsAnalyzed: String, // JSON array of symbols analyzed (e.g., ["BTC/USD", "ETH/USD"])
    val timestamp: Long = System.currentTimeMillis()
)
