package com.cryptotrader.domain.model

/**
 * Trading Opportunity Domain Model
 *
 * Represents a high-confidence trading opportunity identified by AI Trading Advisor
 * This is the single source of truth for opportunity data across the app
 */
data class TradingOpportunity(
    val id: Long = 0,
    val asset: String,                    // e.g., "XXBTZUSD", "XETHZUSD"
    val direction: String,                // "BUY" or "SELL"
    val confidence: Double,               // 0.0-1.0 (will be displayed as percentage)
    val entryPrice: Double,
    val entryPriceMax: Double,           // Maximum acceptable entry price
    val targetPrice: Double,              // Take profit target
    val stopPrice: Double,                // Stop loss price
    val targetProfitPercent: Double,      // Expected profit %
    val stopLossPercent: Double,          // Risk %
    val riskRewardRatio: Double,          // e.g., 2.5 means 2.5:1 reward:risk
    val positionSizePercent: Double,      // Recommended position size as % of portfolio
    val timeHorizon: String,              // e.g., "24-48 hours", "1-2 days", "IMMEDIATE"
    val reasoning: List<String>,          // Key insights explaining the opportunity
    val riskFactors: List<String>,        // Identified risks
    val timestamp: Long = System.currentTimeMillis(),

    // Claude AI analysis (optional, added by deep validation)
    val claudeReasoning: String? = null,
    val claudeInsights: List<String>? = null,
    val claudeRisks: List<String>? = null,
    val positionSizeModifier: Double = 1.0,  // Adjustment from Claude (0.5-1.5)

    // Status tracking
    val status: String = "ACTIVE"         // ACTIVE, EXPIRED, EXECUTED, DISMISSED
) {
    /**
     * Calculate actual risk amount in dollars
     */
    fun calculateRisk(portfolioValue: Double): Double {
        return portfolioValue * (positionSizePercent / 100.0) * (stopLossPercent / 100.0)
    }

    /**
     * Calculate potential reward in dollars
     */
    fun calculateReward(portfolioValue: Double): Double {
        return portfolioValue * (positionSizePercent / 100.0) * (targetProfitPercent / 100.0)
    }

    /**
     * Get confidence as percentage string
     */
    fun confidencePercent(): Int = (confidence * 100).toInt()

    /**
     * Check if opportunity is still valid based on time
     */
    fun isStillValid(maxAgeHours: Int = 24): Boolean {
        val ageMs = System.currentTimeMillis() - timestamp
        val ageHours = ageMs / (1000 * 60 * 60)
        return ageHours < maxAgeHours && status == "ACTIVE"
    }

    /**
     * Validate stop loss is on correct side of entry
     */
    fun hasValidStopLoss(): Boolean {
        return when (direction) {
            "BUY", "LONG" -> stopPrice < entryPrice
            "SELL", "SHORT" -> stopPrice > entryPrice
            else -> false
        }
    }

    /**
     * Validate risk/reward ratio matches price levels
     */
    fun validateRiskRewardRatio(): Boolean {
        val risk = kotlin.math.abs(entryPrice - stopPrice)
        val reward = kotlin.math.abs(targetPrice - entryPrice)
        val calculatedRR = reward / risk

        // Allow 5% tolerance for rounding
        return kotlin.math.abs(calculatedRR - riskRewardRatio) < 0.1
    }
}

/**
 * DTO for parsing opportunities from Claude API responses
 */
data class OpportunityDTO(
    val pair: String,
    val direction: String,
    val confidence: Double,
    val entryPrice: Double,
    val entryPriceMax: Double,
    val targetProfit: Double,
    val stopLoss: Double,
    val timeHorizon: String,
    val reasoning: List<String>,
    val riskFactors: List<String>
) {
    /**
     * Convert DTO to domain model
     */
    fun toDomain(): TradingOpportunity {
        val risk = kotlin.math.abs(entryPrice - (entryPrice * (1 - stopLoss / 100.0)))
        val reward = kotlin.math.abs((entryPrice * (1 + targetProfit / 100.0)) - entryPrice)
        val riskRewardRatio = if (risk > 0) reward / risk else 0.0

        val targetPrice = when (direction.uppercase()) {
            "BUY", "LONG" -> entryPrice * (1 + targetProfit / 100.0)
            "SELL", "SHORT" -> entryPrice * (1 - targetProfit / 100.0)
            else -> entryPrice
        }

        val stopPrice = when (direction.uppercase()) {
            "BUY", "LONG" -> entryPrice * (1 - stopLoss / 100.0)
            "SELL", "SHORT" -> entryPrice * (1 + stopLoss / 100.0)
            else -> entryPrice
        }

        return TradingOpportunity(
            asset = pair,
            direction = direction.uppercase(),
            confidence = confidence,
            entryPrice = entryPrice,
            entryPriceMax = entryPriceMax,
            targetPrice = targetPrice,
            stopPrice = stopPrice,
            targetProfitPercent = targetProfit,
            stopLossPercent = stopLoss,
            riskRewardRatio = riskRewardRatio,
            positionSizePercent = 5.0, // Default 5%, will be adjusted
            timeHorizon = timeHorizon,
            reasoning = reasoning,
            riskFactors = riskFactors
        )
    }
}
