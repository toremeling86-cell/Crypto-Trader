package com.cryptotrader.domain.advisor

import com.cryptotrader.domain.model.MarketSnapshot
import timber.log.Timber

/**
 * Claude Prompt Templates for AI Trading Advisor
 *
 * Provides specialized prompts for:
 * 1. Fast Synthesis (Haiku) - Quick signal aggregation from multiple agents
 * 2. Deep Validation (Sonnet) - Thorough opportunity validation with risk analysis
 */
object ClaudePrompts {

    /**
     * Build fast synthesis prompt for Claude Haiku
     *
     * Purpose: Quick aggregation of signals from multiple trading agents
     * Expected response time: 5-10 seconds
     * Model: Claude Haiku (fast, cost-effective)
     *
     * @param agentSignals Map of agent names to their signals and confidence scores
     * @param marketData Current market snapshot data
     * @param portfolioContext User's current holdings and exposure
     * @return Prompt string for Claude API
     */
    fun buildFastSynthesisPrompt(
        agentSignals: Map<String, AgentSignal>,
        marketData: List<MarketSnapshot>,
        portfolioContext: String?
    ): String {
        val prompt = StringBuilder()

        prompt.appendLine("""
            You are an EXPERT cryptocurrency trading signal synthesizer.

            Your task: Rapidly analyze and synthesize signals from multiple trading agents into a coherent trading recommendation.

            CRITICAL: Respond ONLY with a JSON object in this EXACT format:
            {
              "synthesizedConfidence": 0.75,
              "primarySignal": "BUY|SELL|HOLD",
              "keyInsight": "Brief actionable insight (1-2 sentences)",
              "riskFactor": 0.45,
              "sentimentAdjustment": 0.10
            }

            Field requirements:
            - synthesizedConfidence: 0.0-1.0, weighted average of agent signals
            - primarySignal: Must be exactly "BUY", "SELL", or "HOLD"
            - keyInsight: The most important takeaway for the trader (max 150 chars)
            - riskFactor: 0.0-1.0, current market risk level
            - sentimentAdjustment: -1.0 to +1.0, sentiment impact on confidence

        """.trimIndent())

        // Add agent signals
        if (agentSignals.isNotEmpty()) {
            prompt.appendLine("\n## AGENT SIGNALS")
            agentSignals.entries.sortedByDescending { it.value.confidence }.forEach { (agentName, signal) ->
                prompt.appendLine("""
                    Agent: $agentName
                    Signal: ${signal.signal}
                    Confidence: ${String.format("%.2f", signal.confidence)}
                    Reasoning: ${signal.reasoning}

                """.trimIndent())
            }
        } else {
            prompt.appendLine("\n## AGENT SIGNALS\nNo agent signals available")
        }

        // Add market data
        if (marketData.isNotEmpty()) {
            prompt.appendLine("\n## LIVE MARKET DATA")
            marketData.take(5).forEach { snapshot ->
                val trend = if (snapshot.changePercent24h >= 0) "↑" else "↓"
                val sign = if (snapshot.changePercent24h >= 0) "+" else ""
                prompt.appendLine(
                    "${snapshot.getBaseCurrency()}: $${String.format("%.2f", snapshot.price)} " +
                    "($sign${String.format("%.2f", snapshot.changePercent24h)}% 24h) $trend " +
                    "Vol: $${String.format("%.0f", snapshot.volume24h)}"
                )
            }
        }

        // Add portfolio context if available
        if (!portfolioContext.isNullOrBlank()) {
            prompt.appendLine("\n## PORTFOLIO CONTEXT")
            prompt.appendLine(portfolioContext)
        }

        prompt.appendLine("""

            Analysis priorities:
            1. Weight signals by agent confidence and track record
            2. Consider market volatility and liquidity
            3. Account for portfolio exposure and risk limits
            4. Detect conflicting signals and identify consensus
            5. Be conservative when signals are unclear

            Respond ONLY with the JSON object, no markdown formatting or additional text.
        """.trimIndent())

        return prompt.toString()
    }

    /**
     * Build deep validation prompt for Claude Sonnet
     *
     * Purpose: Thorough analysis and validation of trading opportunities
     * Expected response time: 15-20 seconds
     * Model: Claude Sonnet 4.5 (advanced reasoning)
     *
     * @param opportunity The trading opportunity to validate
     * @param fullMarketContext Comprehensive market analysis
     * @param historicalPerformance Past performance of similar setups
     * @param riskConstraints User's risk limits and preferences
     * @return Prompt string for Claude API
     */
    fun buildDeepValidationPrompt(
        opportunity: TradingOpportunity,
        fullMarketContext: String,
        historicalPerformance: String?,
        riskConstraints: RiskConstraints
    ): String {
        val prompt = StringBuilder()

        prompt.appendLine("""
            You are an EXPERT cryptocurrency trading analyst with deep knowledge of technical analysis, risk management, and market psychology.

            Your task: Perform a COMPREHENSIVE validation of a trading opportunity, providing detailed analysis and risk assessment.

            CRITICAL: Respond ONLY with a JSON object in this EXACT format:
            {
              "validationResult": "APPROVED|REJECTED|CONDITIONAL",
              "finalConfidence": 0.82,
              "reasoning": "Detailed multi-paragraph analysis explaining your decision",
              "keyInsights": [
                "First critical insight about the opportunity",
                "Second key observation about market conditions",
                "Third important risk or opportunity factor"
              ],
              "risks": [
                "Primary risk factor with specific impact",
                "Secondary risk with mitigation approach",
                "Tertiary concern and monitoring strategy"
              ],
              "recommendedAdjustments": {
                "positionSize": "Reduce to 3% due to volatility",
                "stopLoss": "Tighten to 2.5% below entry",
                "takeProfit": "Set at +8% with trailing stop",
                "timing": "Wait for volume confirmation before entry"
              }
            }

            Field requirements:
            - validationResult: "APPROVED" (execute as planned), "REJECTED" (do not trade), or "CONDITIONAL" (trade with adjustments)
            - finalConfidence: 0.0-1.0, your confidence in the opportunity after deep analysis
            - reasoning: Minimum 200 words, thorough analysis covering technical, fundamental, and risk factors
            - keyInsights: 3-5 actionable insights (each 10-50 words)
            - risks: 3-5 specific risk factors with impact assessment
            - recommendedAdjustments: Specific modifications to improve risk/reward (null if REJECTED)

        """.trimIndent())

        // Add opportunity details
        prompt.appendLine("\n## TRADING OPPORTUNITY TO VALIDATE")
        prompt.appendLine("Symbol: ${opportunity.symbol}")
        prompt.appendLine("Signal: ${opportunity.signal}")
        prompt.appendLine("Entry Price: $${String.format("%.2f", opportunity.entryPrice)}")
        prompt.appendLine("Proposed Position Size: ${String.format("%.1f", opportunity.positionSizePercent)}% of portfolio")
        prompt.appendLine("Stop Loss: ${String.format("%.2f", opportunity.stopLossPercent)}%")
        prompt.appendLine("Take Profit: ${String.format("%.2f", opportunity.takeProfitPercent)}%")
        prompt.appendLine("Synthesis Confidence: ${String.format("%.2f", opportunity.synthesisConfidence)}")
        prompt.appendLine("Risk/Reward Ratio: ${String.format("%.2f", opportunity.riskRewardRatio)}")

        if (opportunity.technicalFactors.isNotEmpty()) {
            prompt.appendLine("\nTechnical Factors:")
            opportunity.technicalFactors.forEach { factor ->
                prompt.appendLine("- $factor")
            }
        }

        // Add market context
        prompt.appendLine("\n## COMPREHENSIVE MARKET CONTEXT")
        prompt.appendLine(fullMarketContext)

        // Add historical performance if available
        if (!historicalPerformance.isNullOrBlank()) {
            prompt.appendLine("\n## HISTORICAL PERFORMANCE")
            prompt.appendLine(historicalPerformance)
        }

        // Add risk constraints
        prompt.appendLine("\n## RISK CONSTRAINTS")
        prompt.appendLine("Maximum Position Size: ${String.format("%.1f", riskConstraints.maxPositionSizePercent)}%")
        prompt.appendLine("Maximum Portfolio Risk: ${String.format("%.1f", riskConstraints.maxPortfolioRiskPercent)}%")
        prompt.appendLine("Maximum Drawdown Tolerance: ${String.format("%.1f", riskConstraints.maxDrawdownPercent)}%")
        prompt.appendLine("Risk Tolerance Level: ${riskConstraints.riskToleranceLevel}")
        prompt.appendLine("Current Portfolio Exposure: ${String.format("%.1f", riskConstraints.currentExposurePercent)}%")

        prompt.appendLine("""

            Validation criteria:
            1. TECHNICAL SETUP: Is the technical pattern valid and high-probability?
            2. MARKET ENVIRONMENT: Do current market conditions favor this trade?
            3. RISK MANAGEMENT: Are stop loss and position sizing appropriate?
            4. RISK/REWARD: Is the potential reward worth the risk?
            5. TIMING: Is this the optimal entry point, or should we wait?
            6. PORTFOLIO FIT: Does this trade fit the user's risk profile and constraints?
            7. EDGE DETECTION: Is there a genuine edge, or is this random noise?

            Be thorough and analytical. Your analysis should be:
            - Specific and data-driven (cite actual numbers from the context)
            - Balanced (consider both bullish and bearish scenarios)
            - Actionable (provide clear guidance on execution)
            - Risk-focused (prioritize capital preservation)

            If you recommend CONDITIONAL approval, your adjustments must be specific and implementable.
            If you recommend REJECTION, explain clearly why the opportunity is not worth the risk.

            Respond ONLY with the JSON object, no markdown formatting or additional text.
        """.trimIndent())

        return prompt.toString()
    }

    /**
     * Build prompt for emergency market analysis during high volatility
     */
    fun buildEmergencyAnalysisPrompt(
        currentPositions: String,
        marketSnapshot: String,
        volatilityMetrics: String
    ): String {
        return """
            URGENT: Market volatility spike detected. Rapid risk assessment required.

            Respond with JSON:
            {
              "severity": "LOW|MEDIUM|HIGH|CRITICAL",
              "recommendedAction": "HOLD_ALL|CLOSE_RISKY|CLOSE_ALL|REDUCE_EXPOSURE",
              "affectedPositions": ["BTC", "ETH"],
              "reasoning": "Brief explanation (2-3 sentences)",
              "timeframe": "Act within X minutes/hours"
            }

            ## CURRENT POSITIONS
            $currentPositions

            ## MARKET SNAPSHOT
            $marketSnapshot

            ## VOLATILITY METRICS
            $volatilityMetrics

            Prioritize capital preservation. Be decisive.
        """.trimIndent()
    }
}

/**
 * Agent signal from individual trading agent
 */
data class AgentSignal(
    val signal: String, // "BUY", "SELL", "HOLD"
    val confidence: Double, // 0.0-1.0
    val reasoning: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Trading opportunity requiring validation
 */
data class TradingOpportunity(
    val symbol: String,
    val signal: String,
    val entryPrice: Double,
    val positionSizePercent: Double,
    val stopLossPercent: Double,
    val takeProfitPercent: Double,
    val synthesisConfidence: Double,
    val riskRewardRatio: Double,
    val technicalFactors: List<String> = emptyList(),
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * User's risk constraints and preferences
 */
data class RiskConstraints(
    val maxPositionSizePercent: Double = 10.0,
    val maxPortfolioRiskPercent: Double = 20.0,
    val maxDrawdownPercent: Double = 15.0,
    val riskToleranceLevel: String = "MEDIUM", // "LOW", "MEDIUM", "HIGH"
    val currentExposurePercent: Double = 0.0
)
