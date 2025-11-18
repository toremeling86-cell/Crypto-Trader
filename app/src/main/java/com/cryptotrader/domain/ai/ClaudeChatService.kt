package com.cryptotrader.domain.ai

import android.content.Context
import com.cryptotrader.data.remote.claude.ClaudeApiService
import com.cryptotrader.data.remote.claude.dto.ClaudeMessage
import com.cryptotrader.data.remote.claude.dto.ClaudeRequest
import com.cryptotrader.data.repository.KrakenRepository
import com.cryptotrader.data.repository.StrategyRepository
import com.cryptotrader.domain.model.ChatMessage
import com.cryptotrader.domain.model.Portfolio
import com.cryptotrader.domain.model.Strategy
import com.cryptotrader.utils.CryptoUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Claude AI Chat Service
 *
 * Handles conversational AI trading assistant
 * Can generate, explain, and implement trading strategies
 */
@Singleton
class ClaudeChatService @Inject constructor(
    private val claudeApi: ClaudeApiService,
    private val strategyGenerator: ClaudeStrategyGenerator,
    private val aiContextBuilder: AIContextBuilder,
    private val strategyRepository: StrategyRepository,
    @ApplicationContext private val context: Context
) {

    // Conversation history for context
    private val conversationHistory = mutableListOf<ClaudeMessage>()

    /**
     * Send a message to Claude and get response
     */
    suspend fun sendMessage(
        userMessage: String,
        includePortfolioContext: Boolean = true
    ): Result<ChatMessage> {
        return try {
            val apiKey = CryptoUtils.getClaudeApiKey(context)
            if (apiKey.isNullOrBlank()) {
                return Result.failure(Exception("Claude API key not configured"))
            }

            // Build system prompt with context
            val systemPrompt = buildSystemPrompt(includePortfolioContext)

            // Add user message to history
            conversationHistory.add(ClaudeMessage(role = "user", content = userMessage))

            // Keep only last 10 messages to avoid token limits
            if (conversationHistory.size > 10) {
                conversationHistory.removeAt(0)
                conversationHistory.removeAt(0) // Remove both user and assistant
            }

            Timber.d("Sending message to Claude: $userMessage")

            // Call Claude API
            val request = ClaudeRequest(
                model = "claude-sonnet-4-5-20250929",
                maxTokens = 4096,
                messages = conversationHistory.toList(),
                temperature = 0.7,
                system = systemPrompt
            )

            val response = claudeApi.createMessage(
                apiKey = apiKey,
                request = request
            )

            if (!response.isSuccessful) {
                val errorBody = response.errorBody()?.string()
                Timber.e("Claude API error: ${response.code()} - $errorBody")
                return Result.failure(Exception("Claude API error: ${response.message()}"))
            }

            val claudeResponse = response.body()
            if (claudeResponse == null || claudeResponse.content.isEmpty()) {
                return Result.failure(Exception("Empty response from Claude"))
            }

            val assistantMessage = claudeResponse.content.first().text

            // Add assistant response to history
            conversationHistory.add(ClaudeMessage(role = "assistant", content = assistantMessage))

            Timber.d("Claude response: $assistantMessage")

            // Check if response contains a strategy
            val suggestedStrategy = tryExtractStrategy(assistantMessage)

            val chatMessage = ChatMessage(
                content = assistantMessage,
                isFromUser = false,
                suggestedStrategy = suggestedStrategy
            )

            Result.success(chatMessage)

        } catch (e: Exception) {
            Timber.e(e, "Error sending message to Claude")
            Result.failure(e)
        }
    }

    /**
     * Build system prompt with FULL context from AIContextBuilder
     * This makes Claude EXTREMELY intelligent about the user's situation
     */
    private suspend fun buildSystemPrompt(includeContext: Boolean): String {
        var prompt = """
            You are an EXPERT cryptocurrency trading assistant integrated into a LIVE mobile trading app.

            The app uses Kraken exchange API for REAL-MONEY trading.

            Your role as AI Mission Control:
            1. Analyze LIVE market data from Kraken (Bitcoin, Ethereum, Solana, etc.)
            2. Monitor the user's portfolio and provide personalized advice
            3. Generate data-driven trading strategies based on current market conditions
            4. Provide comprehensive risk analysis and actionable insights
            5. Learn from the user's trading patterns and preferences
            6. Give SPECIFIC, ACTIONABLE recommendations - not generic advice

            CRITICAL CAPABILITIES:
            - You have FULL ACCESS to: Live market prices, user's portfolio, recent trades
            - You can SEE the user's current positions, P&L, and exposure
            - You KNOW what crypto they own and their trading history
            - You can ANALYZE if they should buy/sell/hold based on THEIR situation

            When creating a strategy, you MUST:
            1. Analyze the current market data provided in the context below
            2. Explain WHY this strategy fits the CURRENT market conditions
            3. Provide a DETAILED analysis report (minimum 200 words)
            4. Return the strategy in the exact JSON format specified

            CRITICAL BACKTEST COMPATIBILITY:
            ⚠️ Strategies will be backtested on the LAST 100 HOURS of historical data.
            ⚠️ Entry conditions MUST have triggered at least once in recent 100-hour periods.
            ⚠️ DO NOT use conditions based on CURRENT market state that may not have occurred 100 hours ago.

            Example of BAD condition generation:
            - Current market: BTC $102k, MACD = +150 (positive)
            - You generate: "MACD > 0" (thinking it will trigger because it's positive NOW)
            - Reality: 100 hours ago MACD was -471, stayed negative entire period → 0 trades ❌

            Example of GOOD condition generation:
            - Use oscillating indicators that cycle through ranges: RSI, Bollinger Bands
            - Use crossovers that happen frequently: "SMA_20 > SMA_50"
            - Check the historical context provided below to see indicator ranges
            - Ensure conditions will trigger in typical 100-hour volatility

            TRADING COSTS (REALISTIC KRAKEN MODEL):
            The backtest uses REALISTIC trading costs that match real Kraken exchange fees:

            Base Costs (for ALL orders):
            - Taker Fee: 0.26% (market orders that remove liquidity)
            - Maker Fee: 0.16% (limit orders that add liquidity)
            - Bid-Ask Spread: 0.01% (half of 0.02% typical BTC spread)

            Slippage (depends on position size):
            - Small positions (<10% of balance): 0.05% slippage
            - Large positions (>10% of balance): 0.15% slippage (3x multiplier)
            - Very large positions: Up to 0.25% slippage

            TOTAL ROUND-TRIP COST EXAMPLES:
            - Small position (5% of balance): ~0.32% entry + 0.32% exit = 0.64% round-trip
            - Large position (20% of balance): ~0.42% entry + 0.42% exit = 0.84% round-trip

            WHY LARGE POSITIONS COST MORE:
            When you try to buy/sell $2000 worth of BTC in one order (20% of $10k balance),
            you move the market price and get worse fills. This is called "slippage" and is
            a REAL cost that professional traders account for.

            STRATEGY IMPLICATIONS:
            - Prefer smaller position sizes (5-10%) to minimize slippage
            - Account for ~0.6-0.8% round-trip costs when setting take-profit levels
            - A 1% market move only nets ~0.2-0.4% profit after costs
            - Stop-loss at -3% means you actually lose -3.4% after costs

            JSON format for strategies (ALL FIELDS REQUIRED):
            {
              "name": "Strategy Name (descriptive, e.g. 'BTC Mean Reversion Q4 2024')",
              "description": "Brief description of strategy logic and trading style",
              "entryConditions": ["RSI < 30", "MACD > 0", "Volume > average"],
              "exitConditions": ["RSI > 70", "StopLoss", "TakeProfit"],
              "stopLossPercent": 3.0,
              "takeProfitPercent": 6.0,
              "positionSizePercent": 5.0,
              "tradingPairs": ["XXBTZUSD"],
              "riskLevel": "LOW|MEDIUM|HIGH",
              "analysisReport": "COMPREHENSIVE analysis (minimum 200 words): Current market trends for this specific asset, technical indicators supporting the strategy, fundamental factors, why THIS strategy works NOW, risk factors, expected win rate, optimal market conditions, what could go wrong, and recommended monitoring approach. Be THOROUGH and SPECIFIC!"
            }

            CRITICAL: Entry and exit conditions MUST use ONLY these technical indicators:
            - RSI (e.g., "RSI < 30", "RSI > 70") ✅ BEST for backtesting - oscillates 0-100
            - MACD (e.g., "MACD > 0", "MACD_crossover") ⚠️ Can stay one-sided for long periods
            - Moving Averages (e.g., "SMA_20 > SMA_50", "Price > EMA_50") ✅ GOOD - frequent crosses
            - Bollinger Bands (e.g., "Price < Bollinger_lower") ✅ BEST - guaranteed touches
            - Volume (e.g., "Volume > average", "Volume_increasing") ✅ GOOD - always varies
            - Momentum (e.g., "Momentum > 2.0")
            - Special (e.g., "StopLoss", "TakeProfit", "Price_near_high")

            DO NOT use natural language conditions like "price is higher than 1 hour ago" or "take profit at +1.5%".
            ALWAYS use technical indicator syntax like "RSI < 30" or "StopLoss".

            Communication style:
            - Be direct and analytical, not vague
            - Use data and numbers to support recommendations
            - Prioritize RISK MANAGEMENT above profit
            - Respond in Norwegian if user writes in Norwegian
            - When unsure, say so - don't guess

            NOW, here is the LIVE context about the user and markets:
        """.trimIndent()

        if (includeContext) {
            try {
                // Use AI Context Builder for comprehensive, structured context
                val fullContext = aiContextBuilder.buildFullContext(
                    includeMarketData = true,
                    includePortfolio = true,
                    includeTrades = true
                )

                if (fullContext.isNotEmpty()) {
                    prompt += "\n\n$fullContext"
                }

                // Add active strategies summary
                val strategies = strategyRepository.getActiveStrategies().first()
                if (strategies.isNotEmpty()) {
                    prompt += "\n\n# ACTIVE STRATEGIES"
                    prompt += "\nCurrently running strategies:"
                    strategies.forEach { strategy ->
                        prompt += "\n- ${strategy.name} (${strategy.tradingPairs.joinToString()})"
                        prompt += "\n  Risk: ${strategy.riskLevel}, Status: ${strategy.approvalStatus}"
                    }
                }

                Timber.d("Built full AI context: ${fullContext.length} chars")

            } catch (e: Exception) {
                Timber.e(e, "Failed to build AI context")
                prompt += "\n\n# CONTEXT UNAVAILABLE\nError loading market/portfolio data."
            }
        }

        return prompt
    }

    /**
     * Try to extract a strategy from Claude's response
     */
    private fun tryExtractStrategy(response: String): Strategy? {
        return try {
            // Check if response contains JSON
            if (response.contains("```json") || response.contains("{")) {
                val result = strategyGenerator.parseStrategyFromJson(response)
                if (result.isSuccess) {
                    result.getOrNull()
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            Timber.w("Response doesn't contain a valid strategy")
            null
        }
    }

    /**
     * Clear conversation history
     */
    fun clearHistory() {
        conversationHistory.clear()
        Timber.d("Conversation history cleared")
    }
}
