package com.cryptotrader.domain.analytics

import com.cryptotrader.domain.model.PortfolioHolding
import com.cryptotrader.domain.model.RiskMetrics
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Risk Analytics Engine
 * Calculates portfolio risk metrics and diversification scores
 */
@Singleton
class RiskAnalyticsEngine @Inject constructor() {

    /**
     * Calculate complete risk metrics
     */
    suspend fun calculateRiskMetrics(
        holdings: List<PortfolioHolding>,
        returns: List<Double>
    ): RiskMetrics {
        return try {
            val exposureMap = calculateExposure(holdings)
            val positionSizes = holdings.associate { it.asset to it.percentOfPortfolio }
            val largestPosition = holdings.maxOfOrNull { it.percentOfPortfolio } ?: 0.0

            RiskMetrics(
                diversificationScore = calculateDiversificationScore(holdings),
                exposureByAsset = exposureMap,
                correlationMatrix = emptyMap(), // Simplified for now
                valueAtRisk95 = calculateVaR(returns, 0.95),
                valueAtRisk99 = calculateVaR(returns, 0.99),
                positionSizes = positionSizes,
                volatilityScore = calculateVolatilityScore(returns),
                concentrationRisk = calculateConcentrationRisk(holdings),
                largestPositionPercent = largestPosition
            )
        } catch (e: Exception) {
            Timber.e(e, "Error calculating risk metrics")
            RiskMetrics(
                diversificationScore = 0.0,
                exposureByAsset = emptyMap(),
                correlationMatrix = emptyMap(),
                valueAtRisk95 = 0.0,
                valueAtRisk99 = 0.0,
                positionSizes = emptyMap(),
                volatilityScore = 0.0,
                concentrationRisk = 0.0,
                largestPositionPercent = 0.0
            )
        }
    }

    /**
     * Calculate diversification score using Herfindahl-Hirschman Index
     * Score: 0-100, higher is better diversified
     */
    fun calculateDiversificationScore(holdings: List<PortfolioHolding>): Double {
        if (holdings.isEmpty()) return 0.0

        // Calculate HHI
        val hhi = holdings.sumOf { (it.percentOfPortfolio / 100.0).pow(2) }

        // Convert to 0-100 scale (inverted - lower HHI = better diversification)
        // HHI ranges from 1/n to 1, we normalize to 0-100
        val maxHHI = 1.0 // Perfectly concentrated
        val minHHI = 1.0 / holdings.size // Perfectly diversified

        val score = ((maxHHI - hhi) / (maxHHI - minHHI)) * 100.0
        return score.coerceIn(0.0, 100.0)
    }

    /**
     * Calculate exposure by asset type
     */
    fun calculateExposure(holdings: List<PortfolioHolding>): Map<String, Double> {
        val exposureMap = mutableMapOf<String, Double>()

        for (holding in holdings) {
            val assetType = holding.assetType.name
            exposureMap[assetType] = exposureMap.getOrDefault(assetType, 0.0) + holding.percentOfPortfolio
        }

        return exposureMap
    }

    /**
     * Calculate Value at Risk (VaR)
     * Using historical method with specified confidence level
     */
    fun calculateVaR(returns: List<Double>, confidenceLevel: Double): Double {
        if (returns.isEmpty()) return 0.0

        val sortedReturns = returns.sorted()
        val index = ((1 - confidenceLevel) * sortedReturns.size).toInt()
        val varValue = if (index < sortedReturns.size) sortedReturns[index] else sortedReturns.last()

        return kotlin.math.abs(varValue * 100.0) // Return as percentage
    }

    /**
     * Calculate volatility score (annualized standard deviation)
     */
    fun calculateVolatilityScore(returns: List<Double>): Double {
        if (returns.isEmpty() || returns.size < 2) return 0.0

        val mean = returns.average()
        val variance = returns.map { (it - mean).pow(2) }.average()
        val stdDev = sqrt(variance)

        // Annualize (assuming daily returns)
        return stdDev * sqrt(252.0) * 100.0 // Return as percentage
    }

    /**
     * Calculate concentration risk
     * Higher score = more concentrated (riskier)
     * 0-100 scale
     */
    fun calculateConcentrationRisk(holdings: List<PortfolioHolding>): Double {
        if (holdings.isEmpty()) return 0.0

        // Use Herfindahl index directly
        val hhi = holdings.sumOf { (it.percentOfPortfolio / 100.0).pow(2) }

        // Convert to 0-100 scale (higher HHI = higher concentration risk)
        return (hhi * 100.0).coerceIn(0.0, 100.0)
    }

    /**
     * Analyze position sizing
     * Returns warning flags for positions that are too large
     */
    fun analyzePositionSizing(holdings: List<PortfolioHolding>): Map<String, String> {
        val warnings = mutableMapOf<String, String>()

        for (holding in holdings) {
            when {
                holding.percentOfPortfolio > 50.0 -> {
                    warnings[holding.asset] = "CRITICAL: Position exceeds 50% of portfolio"
                }
                holding.percentOfPortfolio > 30.0 -> {
                    warnings[holding.asset] = "HIGH: Position exceeds 30% of portfolio"
                }
                holding.percentOfPortfolio > 20.0 -> {
                    warnings[holding.asset] = "MEDIUM: Position exceeds 20% of portfolio"
                }
            }
        }

        return warnings
    }
}
