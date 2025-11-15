package com.cryptotrader.domain.ai

import android.content.Context
import com.cryptotrader.data.local.dao.AIMarketAnalysisDao
import com.cryptotrader.data.local.entities.AIMarketAnalysisEntity
import com.cryptotrader.data.remote.claude.ClaudeApiService
import com.cryptotrader.data.remote.claude.dto.ClaudeMessage
import com.cryptotrader.data.remote.claude.dto.ClaudeRequest
import com.cryptotrader.utils.CryptoUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Claude Market Analyzer
 *
 * Performs REAL market analysis using Claude API
 * - Analyzes current crypto market conditions
 * - Provides sentiment (BULLISH/BEARISH/NEUTRAL)
 * - Identifies risks and opportunities
 * - Generates actionable insights
 *
 * Used for:
 * 1. Scheduled hourly analysis (via WorkManager)
 * 2. Manual "Analyze Now" triggers
 */
@Singleton
class ClaudeMarketAnalyzer @Inject constructor(
    private val claudeApi: ClaudeApiService,
    private val aiContextBuilder: AIContextBuilder,
    private val aiMarketAnalysisDao: AIMarketAnalysisDao,
    @ApplicationContext private val context: Context
) {

    /**
     * Perform comprehensive market analysis
     * Returns analysis ID for tracking
     */
    suspend fun analyzeMarket(
        triggerType: String = "MANUAL", // "MANUAL" or "SCHEDULED"
        includeExpertReports: Boolean = false
    ): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val apiKey = CryptoUtils.getClaudeApiKey(context)
            if (apiKey.isNullOrBlank()) {
                return@withContext Result.failure(Exception("Claude API key not configured"))
            }

            Timber.d("Starting market analysis (trigger: $triggerType)")

            // Build market context
            val marketContext = aiContextBuilder.buildMarketAnalysisContext()

            // Build analysis prompt
            val systemPrompt = buildAnalysisPrompt(marketContext, includeExpertReports)

            // Call Claude API
            val request = ClaudeRequest(
                model = "claude-sonnet-4-5-20250929",
                maxTokens = 4096,
                messages = listOf(
                    ClaudeMessage(
                        role = "user",
                        content = "Analyze the current cryptocurrency market and provide comprehensive insights."
                    )
                ),
                temperature = 0.7,
                system = systemPrompt
            )

            val response = claudeApi.createMessage(apiKey = apiKey, request = request)

            if (!response.isSuccessful) {
                val errorBody = response.errorBody()?.string()
                Timber.e("Claude API error: ${response.code()} - $errorBody")
                return@withContext Result.failure(Exception("Claude API error: ${response.message()}"))
            }

            val claudeResponse = response.body()
            if (claudeResponse == null || claudeResponse.content.isEmpty()) {
                return@withContext Result.failure(Exception("Empty response from Claude"))
            }

            val analysisText = claudeResponse.content.first().text

            // Parse Claude's response to extract structured data
            val parsedAnalysis = parseAnalysisResponse(analysisText)

            // Save to database
            val entity = AIMarketAnalysisEntity(
                analysisText = analysisText,
                sentiment = parsedAnalysis.sentiment,
                marketCondition = parsedAnalysis.marketCondition,
                confidence = parsedAnalysis.confidence,
                keyInsights = parsedAnalysis.keyInsights,
                riskFactors = parsedAnalysis.riskFactors,
                opportunities = parsedAnalysis.opportunities,
                triggerType = triggerType,
                symbolsAnalyzed = parsedAnalysis.symbolsAnalyzed,
                timestamp = System.currentTimeMillis()
            )

            val analysisId = aiMarketAnalysisDao.insertAnalysis(entity)

            Timber.i("Market analysis completed successfully (ID: $analysisId, sentiment: ${parsedAnalysis.sentiment})")

            Result.success(analysisId)

        } catch (e: Exception) {
            Timber.e(e, "Error performing market analysis")
            Result.failure(e)
        }
    }

    /**
     * Build comprehensive analysis prompt
     */
    private fun buildAnalysisPrompt(marketContext: String, includeReports: Boolean): String {
        return """
            You are an EXPERT cryptocurrency market analyst.

            Your task: Perform a COMPREHENSIVE market analysis of current crypto markets.

            CRITICAL: You MUST respond with a JSON object containing your analysis.

            JSON format (REQUIRED):
            {
              "sentiment": "BULLISH|BEARISH|NEUTRAL",
              "marketCondition": "VOLATILE|STABLE|TRENDING_UP|TRENDING_DOWN|CONSOLIDATING",
              "confidence": 0.85,
              "keyInsights": [
                "Bitcoin showing strong bullish momentum with RSI at 65",
                "Ethereum lagging behind with decreasing volume",
                "Solana outperforming with 15% gain in 24h"
              ],
              "riskFactors": [
                "High volatility could trigger stop losses",
                "Overbought conditions on short timeframes",
                "Potential resistance at $70,000 for BTC"
              ],
              "opportunities": [
                "Potential entry on ETH if it breaks above $3,500",
                "SOL showing strong momentum - consider trailing stop",
                "BTC approaching key resistance - potential breakout"
              ],
              "symbolsAnalyzed": ["BTC", "ETH", "SOL", "ADA", "DOT", "MATIC", "AVAX", "LINK"],
              "summary": "Brief 2-3 sentence market summary"
            }

            Analysis requirements:
            1. Be SPECIFIC with numbers and levels
            2. Identify CONCRETE risks and opportunities
            3. Confidence should reflect market clarity (0.0-1.0)
            4. keyInsights should be actionable, not generic
            5. Consider timeframes: short-term (1-7 days) focus

            MARKET DATA:
            $marketContext

            ${if (includeReports) "\nEXPERT REPORTS:\n[Expert reports would be included here]" else ""}

            Respond ONLY with the JSON object, no additional text.
        """.trimIndent()
    }

    /**
     * Parse Claude's analysis response
     * Extracts structured data from JSON or text
     */
    private fun parseAnalysisResponse(response: String): ParsedAnalysis {
        return try {
            // Try to extract JSON from response
            val jsonStart = response.indexOf("{")
            val jsonEnd = response.lastIndexOf("}") + 1

            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                val jsonString = response.substring(jsonStart, jsonEnd)
                val json = JSONObject(jsonString)

                ParsedAnalysis(
                    sentiment = json.optString("sentiment", "NEUTRAL"),
                    marketCondition = json.optString("marketCondition", "STABLE"),
                    confidence = json.optDouble("confidence", 0.5),
                    keyInsights = parseJsonArray(json, "keyInsights"),
                    riskFactors = parseJsonArray(json, "riskFactors"),
                    opportunities = parseJsonArray(json, "opportunities"),
                    symbolsAnalyzed = parseJsonArray(json, "symbolsAnalyzed")
                )
            } else {
                // Fallback: Parse text heuristically
                Timber.w("No JSON found in response, using fallback parsing")
                fallbackParseAnalysis(response)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error parsing analysis response")
            fallbackParseAnalysis(response)
        }
    }

    /**
     * Parse JSON array to comma-separated string
     */
    private fun parseJsonArray(json: JSONObject, key: String): String {
        return try {
            val array = json.optJSONArray(key)
            if (array != null) {
                val items = mutableListOf<String>()
                for (i in 0 until array.length()) {
                    items.add(array.getString(i))
                }
                items.joinToString(",")
            } else {
                ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    /**
     * Fallback parsing when JSON extraction fails
     */
    private fun fallbackParseAnalysis(text: String): ParsedAnalysis {
        val sentiment = when {
            text.contains("bullish", ignoreCase = true) -> "BULLISH"
            text.contains("bearish", ignoreCase = true) -> "BEARISH"
            else -> "NEUTRAL"
        }

        val marketCondition = when {
            text.contains("volatile", ignoreCase = true) -> "VOLATILE"
            text.contains("trending up", ignoreCase = true) || text.contains("uptrend", ignoreCase = true) -> "TRENDING_UP"
            text.contains("trending down", ignoreCase = true) || text.contains("downtrend", ignoreCase = true) -> "TRENDING_DOWN"
            else -> "STABLE"
        }

        return ParsedAnalysis(
            sentiment = sentiment,
            marketCondition = marketCondition,
            confidence = 0.5, // Default to medium confidence
            keyInsights = "Analysis available in full text",
            riskFactors = "See analysis for details",
            opportunities = "See analysis for details",
            symbolsAnalyzed = "BTC,ETH,SOL"
        )
    }

    /**
     * Parsed analysis data
     */
    data class ParsedAnalysis(
        val sentiment: String,
        val marketCondition: String,
        val confidence: Double,
        val keyInsights: String,
        val riskFactors: String,
        val opportunities: String,
        val symbolsAnalyzed: String
    )
}
