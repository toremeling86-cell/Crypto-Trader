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
    private val krakenRepository: KrakenRepository,
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
     * Build system prompt with portfolio and LIVE market context
     */
    private suspend fun buildSystemPrompt(includeContext: Boolean): String {
        var prompt = """
            You are an expert cryptocurrency trading assistant integrated into a mobile trading app.

            The app uses Kraken exchange API for LIVE trading with real money.

            Your capabilities:
            1. Analyze LIVE market data from Kraken
            2. Generate data-driven trading strategies based on current market conditions
            3. Provide detailed risk analysis and market insights
            4. Answer questions about trading and technical analysis
            5. Give actionable recommendations

            IMPORTANT: When creating a strategy, you MUST:
            1. Analyze the current market data provided below
            2. Explain WHY this strategy fits the current market
            3. Provide a detailed analysis report
            4. Return the strategy in JSON format

            JSON format for strategies (REQUIRED FIELDS):
            {
              "name": "Strategy Name",
              "description": "Brief description of strategy logic",
              "entryConditions": ["Specific entry rules like 'RSI < 30'", "Price crosses above 50 MA"],
              "exitConditions": ["Exit rules like 'RSI > 70'", "Take profit hit"],
              "stopLossPercent": 3.0,
              "takeProfitPercent": 6.0,
              "positionSizePercent": 5.0,
              "tradingPairs": ["XXBTZUSD"],
              "riskLevel": "LOW|MEDIUM|HIGH",
              "analysisReport": "DETAILED analysis: Current market trends, why this strategy works now, risk factors, expected outcomes. Be thorough!"
            }

            Be analytical, data-driven, and prioritize risk management.
            Use Norwegian when user writes in Norwegian, English otherwise.
        """.trimIndent()

        if (includeContext) {
            try {
                // Add portfolio context
                val balanceResult = krakenRepository.getBalance()
                if (balanceResult.isSuccess) {
                    val balances = balanceResult.getOrNull()
                    prompt += "\n\n📊 CURRENT PORTFOLIO:\n$balances"
                }

                // Add LIVE market data from Kraken
                prompt += "\n\n📈 LIVE MARKET DATA (fra Kraken):"
                val majorPairs = listOf("XXBTZUSD", "XETHZUSD", "SOLUSD")

                for (pair in majorPairs) {
                    try {
                        val ticker = krakenRepository.getTicker(pair)
                        if (ticker.isSuccess) {
                            val data = ticker.getOrNull()
                            if (data != null) {
                                prompt += "\n\n$pair:"
                                prompt += "\n  • Last Price: $${data.last}"
                                prompt += "\n  • 24h High: $${data.high24h}"
                                prompt += "\n  • 24h Low: $${data.low24h}"
                                prompt += "\n  • 24h Volume: ${data.volume24h}"
                                prompt += "\n  • Bid: $${data.bid} / Ask: $${data.ask}"
                            }
                        }
                    } catch (e: Exception) {
                        Timber.w("Failed to get ticker for $pair")
                    }
                }

                // Add active strategies context
                val strategies = strategyRepository.getActiveStrategies().first()
                if (strategies.isNotEmpty()) {
                    prompt += "\n\n⚙️ ACTIVE STRATEGIES:\n${strategies.map { "• ${it.name} (${it.tradingPairs.joinToString()})" }.joinToString("\n")}"
                }
            } catch (e: Exception) {
                Timber.w(e, "Failed to add context to prompt")
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
