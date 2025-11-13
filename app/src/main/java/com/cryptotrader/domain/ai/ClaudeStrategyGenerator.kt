package com.cryptotrader.domain.ai

import android.content.Context
import com.cryptotrader.data.remote.claude.ClaudeApiService
import com.cryptotrader.data.remote.claude.dto.ClaudeMessage
import com.cryptotrader.data.remote.claude.dto.ClaudeRequest
import com.cryptotrader.domain.model.ApprovalStatus
import com.cryptotrader.domain.model.RiskLevel
import com.cryptotrader.domain.model.Strategy
import com.cryptotrader.domain.model.StrategySource
import com.cryptotrader.utils.CryptoUtils
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real Claude AI Strategy Generator
 *
 * Converts natural language descriptions into executable trading strategies using Claude AI
 */
@Singleton
class ClaudeStrategyGenerator @Inject constructor(
    private val claudeApi: ClaudeApiService,
    @ApplicationContext private val context: Context
) {

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    companion object {
        private const val PROMPT_TEMPLATE_FILE = "ai_strategy_prompt.txt"
    }

    /**
     * Generate a trading strategy from natural language using Claude AI
     *
     * @param userPrompt User's strategy description
     * @return Generated Strategy or error
     */
    suspend fun generateStrategy(userPrompt: String): Result<Strategy> {
        return try {
            // Get Claude API key
            val apiKey = CryptoUtils.getClaudeApiKey(context)
            if (apiKey.isNullOrBlank()) {
                return Result.failure(Exception("Claude API key not configured. Please add it in Settings."))
            }

            // Load prompt template
            val promptTemplate = loadPromptTemplate()

            // Build complete prompt
            val completePrompt = promptTemplate.replace("{{USER_PROMPT}}", userPrompt)

            Timber.i("Generating strategy with Claude AI for prompt: $userPrompt")

            // Call Claude API
            val request = ClaudeRequest(
                model = "claude-sonnet-4-5-20250929",
                maxTokens = 4096,
                messages = listOf(
                    ClaudeMessage(
                        role = "user",
                        content = completePrompt
                    )
                ),
                temperature = 0.7
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
                return Result.failure(Exception("Empty response from Claude AI"))
            }

            // Extract JSON from response
            val jsonText = claudeResponse.content.first().text

            Timber.d("Claude response: $jsonText")

            // Parse JSON to Strategy
            parseStrategyFromJson(jsonText)

        } catch (e: Exception) {
            Timber.e(e, "Error generating strategy with Claude")
            Result.failure(e)
        }
    }

    /**
     * Load prompt template from assets
     */
    private fun loadPromptTemplate(): String {
        return try {
            context.assets.open(PROMPT_TEMPLATE_FILE).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Timber.e(e, "Failed to load prompt template")
            // Fallback basic template
            """
            Generate a trading strategy for Kraken exchange in JSON format with these fields:
            name, description, entryConditions (list), exitConditions (list),
            stopLossPercent, takeProfitPercent, positionSizePercent, tradingPairs (list), riskLevel.

            User request: {{USER_PROMPT}}

            Return ONLY valid JSON.
            """.trimIndent()
        }
    }

    /**
     * Parse Claude's JSON response into Strategy domain model
     * Public so it can be used by ClaudeChatService
     */
    fun parseStrategyFromJson(jsonText: String): Result<Strategy> {
        return try {
            // Extract JSON from markdown code blocks if present
            val cleanJson = extractJsonFromMarkdown(jsonText)

            // Parse to intermediate format
            val jsonAdapter = moshi.adapter(AIStrategyResponse::class.java)
            val aiStrategy = jsonAdapter.fromJson(cleanJson)
                ?: return Result.failure(Exception("Failed to parse strategy JSON"))

            // Validate
            validateAIStrategy(aiStrategy)

            // Convert to domain model
            val strategy = Strategy(
                id = UUID.randomUUID().toString(),
                name = aiStrategy.name,
                description = aiStrategy.description,
                entryConditions = aiStrategy.entryConditions,
                exitConditions = aiStrategy.exitConditions,
                positionSizePercent = aiStrategy.positionSizePercent,
                stopLossPercent = aiStrategy.stopLossPercent,
                takeProfitPercent = aiStrategy.takeProfitPercent,
                tradingPairs = aiStrategy.tradingPairs,
                isActive = false, // Not active by default - requires approval first
                riskLevel = RiskLevel.fromString(aiStrategy.riskLevel),
                // AI-specific fields
                analysisReport = aiStrategy.analysisReport,
                approvalStatus = ApprovalStatus.PENDING, // Always pending for AI strategies
                source = StrategySource.AI_CLAUDE,
                // Advanced settings
                useTrailingStop = aiStrategy.useTrailingStop ?: false,
                trailingStopPercent = aiStrategy.trailingStopPercent ?: 5.0,
                useMultiTimeframe = aiStrategy.useMultiTimeframe ?: false,
                primaryTimeframe = aiStrategy.primaryTimeframe ?: 60,
                confirmatoryTimeframes = aiStrategy.confirmatoryTimeframes ?: listOf(15, 240),
                useVolatilityStops = aiStrategy.useVolatilityStops ?: false,
                atrMultiplier = aiStrategy.atrMultiplier ?: 2.0,
                useRegimeFilter = aiStrategy.useRegimeFilter ?: false,
                allowedRegimes = aiStrategy.allowedRegimes ?: listOf("TRENDING_BULLISH")
            )

            Timber.i("Successfully generated strategy: ${strategy.name}")
            Result.success(strategy)

        } catch (e: Exception) {
            Timber.e(e, "Error parsing strategy JSON")
            Result.failure(Exception("Failed to parse strategy: ${e.message}"))
        }
    }

    /**
     * Extract JSON from markdown code blocks
     */
    private fun extractJsonFromMarkdown(text: String): String {
        // Remove markdown code blocks
        val jsonPattern = Regex("```json\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)
        val match = jsonPattern.find(text)

        return if (match != null) {
            match.groupValues[1].trim()
        } else {
            // Try to find raw JSON object
            val jsonObjectPattern = Regex("\\{[\\s\\S]*}")
            jsonObjectPattern.find(text)?.value?.trim() ?: text.trim()
        }
    }

    /**
     * Validate AI-generated strategy parameters
     */
    private fun validateAIStrategy(strategy: AIStrategyResponse) {
        require(strategy.name.isNotBlank()) { "Strategy name cannot be empty" }
        require(strategy.entryConditions.isNotEmpty()) { "Must have at least one entry condition" }
        require(strategy.exitConditions.isNotEmpty()) { "Must have at least one exit condition" }

        require(strategy.stopLossPercent in 0.5..15.0) {
            "Stop loss must be between 0.5% and 15%, got ${strategy.stopLossPercent}%"
        }
        require(strategy.takeProfitPercent in 1.0..50.0) {
            "Take profit must be between 1% and 50%, got ${strategy.takeProfitPercent}%"
        }
        require(strategy.positionSizePercent in 1.0..25.0) {
            "Position size must be between 1% and 25%, got ${strategy.positionSizePercent}%"
        }

        // Risk/reward ratio check
        require(strategy.takeProfitPercent >= strategy.stopLossPercent) {
            "Take profit should be >= stop loss for positive expected value"
        }

        require(strategy.tradingPairs.isNotEmpty()) { "Must specify at least one trading pair" }
    }
}

/**
 * Intermediate AI response format
 */
data class AIStrategyResponse(
    val name: String,
    val description: String,
    val entryConditions: List<String>,
    val exitConditions: List<String>,
    val stopLossPercent: Double,
    val takeProfitPercent: Double,
    val positionSizePercent: Double,
    val tradingPairs: List<String>,
    val riskLevel: String = "MEDIUM",
    val analysisReport: String? = null, // Claude's market analysis and strategy justification
    val useTrailingStop: Boolean? = null,
    val trailingStopPercent: Double? = null,
    val useMultiTimeframe: Boolean? = null,
    val primaryTimeframe: Int? = null,
    val confirmatoryTimeframes: List<Int>? = null,
    val useVolatilityStops: Boolean? = null,
    val atrMultiplier: Double? = null,
    val useRegimeFilter: Boolean? = null,
    val allowedRegimes: List<String>? = null
)
