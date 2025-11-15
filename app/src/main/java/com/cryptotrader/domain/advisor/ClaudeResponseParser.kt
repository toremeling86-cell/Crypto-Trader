package com.cryptotrader.domain.advisor

import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber

/**
 * Claude Response Parser
 *
 * Parses JSON responses from Claude API, handling:
 * - JSON extraction from markdown code blocks
 * - Malformed JSON recovery
 * - Field validation and type conversion
 * - Error handling with meaningful messages
 */
class ClaudeResponseParser {

    /**
     * Parse fast synthesis response from Claude Haiku
     *
     * @param response Raw text response from Claude
     * @return Parsed synthesis data or null if parsing fails
     */
    fun parseFastSynthesis(response: String): ClaudeFastSynthesis? {
        return try {
            val json = extractJson(response)
            if (json == null) {
                Timber.w("No JSON found in fast synthesis response")
                return null
            }

            // Validate required fields
            val requiredFields = listOf(
                "synthesizedConfidence",
                "primarySignal",
                "keyInsight",
                "riskFactor",
                "sentimentAdjustment"
            )

            val missingFields = requiredFields.filter { !json.has(it) }
            if (missingFields.isNotEmpty()) {
                Timber.e("Missing required fields in fast synthesis: $missingFields")
                return null
            }

            val primarySignal = json.getString("primarySignal").uppercase()
            if (primarySignal !in listOf("BUY", "SELL", "HOLD")) {
                Timber.e("Invalid primarySignal: $primarySignal")
                return null
            }

            ClaudeFastSynthesis(
                synthesizedConfidence = json.getDouble("synthesizedConfidence").coerceIn(0.0, 1.0),
                primarySignal = primarySignal,
                keyInsight = json.getString("keyInsight"),
                riskFactor = json.getDouble("riskFactor").coerceIn(0.0, 1.0),
                sentimentAdjustment = json.getDouble("sentimentAdjustment").coerceIn(-1.0, 1.0),
                timestamp = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Timber.e(e, "Error parsing fast synthesis response")
            null
        }
    }

    /**
     * Parse deep validation response from Claude Sonnet
     *
     * @param response Raw text response from Claude
     * @return Parsed validation data or null if parsing fails
     */
    fun parseDeepValidation(response: String): ClaudeDeepValidation? {
        return try {
            val json = extractJson(response)
            if (json == null) {
                Timber.w("No JSON found in deep validation response")
                return null
            }

            // Validate required fields
            val requiredFields = listOf(
                "validationResult",
                "finalConfidence",
                "reasoning",
                "keyInsights",
                "risks"
            )

            val missingFields = requiredFields.filter { !json.has(it) }
            if (missingFields.isNotEmpty()) {
                Timber.e("Missing required fields in deep validation: $missingFields")
                return null
            }

            val validationResult = json.getString("validationResult").uppercase()
            if (validationResult !in listOf("APPROVED", "REJECTED", "CONDITIONAL")) {
                Timber.e("Invalid validationResult: $validationResult")
                return null
            }

            // Parse arrays
            val keyInsights = parseStringArray(json.getJSONArray("keyInsights"))
            val risks = parseStringArray(json.getJSONArray("risks"))

            // Parse recommended adjustments (may be null for REJECTED)
            val adjustments = if (json.has("recommendedAdjustments") && !json.isNull("recommendedAdjustments")) {
                parseRecommendedAdjustments(json.getJSONObject("recommendedAdjustments"))
            } else {
                null
            }

            ClaudeDeepValidation(
                validationResult = validationResult,
                finalConfidence = json.getDouble("finalConfidence").coerceIn(0.0, 1.0),
                reasoning = json.getString("reasoning"),
                keyInsights = keyInsights,
                risks = risks,
                recommendedAdjustments = adjustments,
                timestamp = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Timber.e(e, "Error parsing deep validation response")
            null
        }
    }

    /**
     * Parse emergency analysis response
     */
    fun parseEmergencyAnalysis(response: String): EmergencyAnalysis? {
        return try {
            val json = extractJson(response)
            if (json == null) {
                Timber.w("No JSON found in emergency analysis response")
                return null
            }

            val severity = json.getString("severity").uppercase()
            val recommendedAction = json.getString("recommendedAction").uppercase()

            val affectedPositions = if (json.has("affectedPositions")) {
                parseStringArray(json.getJSONArray("affectedPositions"))
            } else {
                emptyList()
            }

            EmergencyAnalysis(
                severity = severity,
                recommendedAction = recommendedAction,
                affectedPositions = affectedPositions,
                reasoning = json.getString("reasoning"),
                timeframe = json.getString("timeframe"),
                timestamp = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Timber.e(e, "Error parsing emergency analysis response")
            null
        }
    }

    /**
     * Extract JSON object from response text
     * Handles cases where JSON is wrapped in markdown code blocks
     */
    private fun extractJson(response: String): JSONObject? {
        return try {
            // Try parsing entire response first
            try {
                return JSONObject(response.trim())
            } catch (e: Exception) {
                // Response might be wrapped in markdown
            }

            // Look for JSON in markdown code blocks
            val codeBlockRegex = """```(?:json)?\s*(\{[\s\S]*?\})\s*```""".toRegex()
            val match = codeBlockRegex.find(response)
            if (match != null) {
                val jsonString = match.groupValues[1]
                return JSONObject(jsonString)
            }

            // Look for raw JSON object in text
            val jsonStart = response.indexOf("{")
            val jsonEnd = response.lastIndexOf("}") + 1

            if (jsonStart >= 0 && jsonEnd > jsonStart) {
                val jsonString = response.substring(jsonStart, jsonEnd)
                return JSONObject(jsonString)
            }

            Timber.w("Could not find valid JSON in response")
            null
        } catch (e: Exception) {
            Timber.e(e, "Error extracting JSON from response")
            null
        }
    }

    /**
     * Parse JSON array to List<String>
     */
    private fun parseStringArray(jsonArray: JSONArray): List<String> {
        val list = mutableListOf<String>()
        for (i in 0 until jsonArray.length()) {
            list.add(jsonArray.getString(i))
        }
        return list
    }

    /**
     * Parse recommended adjustments object
     */
    private fun parseRecommendedAdjustments(json: JSONObject): RecommendedAdjustments {
        return RecommendedAdjustments(
            positionSize = json.optString("positionSize", null),
            stopLoss = json.optString("stopLoss", null),
            takeProfit = json.optString("takeProfit", null),
            timing = json.optString("timing", null)
        )
    }

    /**
     * Validate that a synthesis response meets quality thresholds
     */
    fun validateSynthesisQuality(synthesis: ClaudeFastSynthesis): ValidationResult {
        val issues = mutableListOf<String>()

        // Check confidence is reasonable
        if (synthesis.synthesizedConfidence < 0.2) {
            issues.add("Confidence too low (${synthesis.synthesizedConfidence})")
        }

        // Check key insight is not empty or too short
        if (synthesis.keyInsight.length < 10) {
            issues.add("Key insight too brief")
        }

        // Check risk factor is reasonable
        if (synthesis.riskFactor > 0.8 && synthesis.primarySignal != "HOLD") {
            issues.add("Risk factor very high (${synthesis.riskFactor}) for ${synthesis.primarySignal} signal")
        }

        return if (issues.isEmpty()) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid(issues)
        }
    }

    /**
     * Validate that a validation response meets quality thresholds
     */
    fun validateValidationQuality(validation: ClaudeDeepValidation): ValidationResult {
        val issues = mutableListOf<String>()

        // Check reasoning is sufficiently detailed
        if (validation.reasoning.length < 100) {
            issues.add("Reasoning too brief (${validation.reasoning.length} chars, minimum 100)")
        }

        // Check we have enough insights
        if (validation.keyInsights.size < 2) {
            issues.add("Insufficient key insights (${validation.keyInsights.size}, minimum 2)")
        }

        // Check we have enough risk factors
        if (validation.risks.size < 2) {
            issues.add("Insufficient risk factors (${validation.risks.size}, minimum 2)")
        }

        // Check adjustments are provided for CONDITIONAL
        if (validation.validationResult == "CONDITIONAL" && validation.recommendedAdjustments == null) {
            issues.add("CONDITIONAL result requires recommended adjustments")
        }

        // Check confidence is reasonable for result
        when (validation.validationResult) {
            "APPROVED" -> {
                if (validation.finalConfidence < 0.6) {
                    issues.add("Confidence too low for APPROVED (${validation.finalConfidence})")
                }
            }
            "REJECTED" -> {
                if (validation.finalConfidence > 0.4) {
                    issues.add("Confidence too high for REJECTED (${validation.finalConfidence})")
                }
            }
        }

        return if (issues.isEmpty()) {
            ValidationResult.Valid
        } else {
            ValidationResult.Invalid(issues)
        }
    }
}

/**
 * Fast synthesis result from Claude Haiku
 */
data class ClaudeFastSynthesis(
    val synthesizedConfidence: Double,
    val primarySignal: String, // "BUY", "SELL", "HOLD"
    val keyInsight: String,
    val riskFactor: Double,
    val sentimentAdjustment: Double,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Deep validation result from Claude Sonnet
 */
data class ClaudeDeepValidation(
    val validationResult: String, // "APPROVED", "REJECTED", "CONDITIONAL"
    val finalConfidence: Double,
    val reasoning: String,
    val keyInsights: List<String>,
    val risks: List<String>,
    val recommendedAdjustments: RecommendedAdjustments?,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Recommended adjustments for conditional approval
 */
data class RecommendedAdjustments(
    val positionSize: String?,
    val stopLoss: String?,
    val takeProfit: String?,
    val timing: String?
)

/**
 * Emergency analysis for high volatility situations
 */
data class EmergencyAnalysis(
    val severity: String, // "LOW", "MEDIUM", "HIGH", "CRITICAL"
    val recommendedAction: String, // "HOLD_ALL", "CLOSE_RISKY", "CLOSE_ALL", "REDUCE_EXPOSURE"
    val affectedPositions: List<String>,
    val reasoning: String,
    val timeframe: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Validation result for quality checking
 */
sealed class ValidationResult {
    object Valid : ValidationResult()
    data class Invalid(val issues: List<String>) : ValidationResult()

    fun isValid(): Boolean = this is Valid
}
