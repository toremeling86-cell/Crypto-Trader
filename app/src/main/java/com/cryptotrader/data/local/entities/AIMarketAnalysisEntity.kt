package com.cryptotrader.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * AI market analysis entity for Claude's crypto market insights
 * Stores Claude AI's analysis of market conditions
 */
@Entity(
    tableName = "ai_market_analyses",
    indices = [Index("timestamp"), Index("triggerType")]
)
data class AIMarketAnalysisEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val analysisText: String, // Claude's full markdown analysis
    val sentiment: String, // "BULLISH", "BEARISH", "NEUTRAL"
    val marketCondition: String, // "VOLATILE", "STABLE", "TRENDING_UP", "TRENDING_DOWN"
    val confidence: Double, // 0.0 to 1.0
    val keyInsights: String, // JSON array of key points
    val riskFactors: String, // JSON array of identified risks
    val opportunities: String, // JSON array of opportunities
    val triggerType: String, // "SCHEDULED" or "MANUAL"
    val symbolsAnalyzed: String, // JSON array of symbols (e.g., ["BTC/USD", "ETH/USD"])
    val timestamp: Long = System.currentTimeMillis()
)
