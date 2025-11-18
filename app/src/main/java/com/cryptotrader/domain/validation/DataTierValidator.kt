package com.cryptotrader.domain.validation

import com.cryptotrader.data.local.entities.OHLCBarEntity
import com.cryptotrader.domain.model.DataTier
import com.cryptotrader.domain.model.DataTierMixingException
import com.cryptotrader.domain.model.InsufficientDataQualityException
import timber.log.Timber

/**
 * Data Tier Validator - Hedge Fund Quality Control
 *
 * Enforces strict data quality separation:
 * - NEVER mix data from different tiers in a single backtest
 * - Verify all data meets minimum quality thresholds
 * - Track tier usage for audit trail
 *
 * CRITICAL: This prevents the catastrophic mistake of mixing
 * high-quality institutional data with low-quality retail data.
 */
object DataTierValidator {

    /**
     * Validate that all OHLC bars are from the same data tier
     *
     * @param bars List of OHLC bars to validate
     * @throws DataTierMixingException if bars contain mixed tiers
     * @return The validated DataTier (all bars are confirmed to be from this tier)
     */
    fun validateSingleTier(bars: List<OHLCBarEntity>): DataTier {
        if (bars.isEmpty()) {
            throw IllegalArgumentException("Cannot validate empty bar list")
        }

        // Get all unique tiers in the dataset
        val uniqueTiers = bars.map { it.dataTier }.toSet()

        if (uniqueTiers.size > 1) {
            val tierCounts = bars.groupingBy { it.dataTier }.eachCount()
            val detailMessage = tierCounts.entries.joinToString(", ") { (tier, count) ->
                "$tier: $count bars"
            }

            Timber.e("❌ CRITICAL: Data tier mixing detected! $detailMessage")
            throw DataTierMixingException(
                "CRITICAL: Mixed data tiers detected in backtest data!\n" +
                "Found ${uniqueTiers.size} different tiers: ${uniqueTiers.joinToString(", ")}\n" +
                "Distribution: $detailMessage\n\n" +
                "Hedge funds NEVER mix data quality levels.\n" +
                "Please use data from a single tier only."
            )
        }

        val tierString = uniqueTiers.first()
        val tier = DataTier.fromString(tierString)

        Timber.i("✅ Data tier validation passed: All ${bars.size} bars are ${tier.tierName} quality")
        return tier
    }

    /**
     * Validate that data quality meets minimum threshold for the tier
     *
     * @param bars List of OHLC bars
     * @param tier The data tier being used
     * @param actualQualityScore Measured quality score (0.0 - 1.0)
     * @throws InsufficientDataQualityException if quality is below threshold
     */
    fun validateQualityThreshold(
        bars: List<OHLCBarEntity>,
        tier: DataTier,
        actualQualityScore: Double
    ) {
        val requiredQuality = tier.getMinimumQualityThreshold()

        if (actualQualityScore < requiredQuality) {
            Timber.e(
                "❌ CRITICAL: Data quality below threshold for ${tier.tierName}! " +
                "Actual: ${actualQualityScore}, Required: ${requiredQuality}"
            )
            throw InsufficientDataQualityException(
                tier = tier,
                actualQuality = actualQualityScore,
                requiredQuality = requiredQuality
            )
        }

        Timber.i(
            "✅ Quality validation passed: ${tier.tierName} quality score " +
            "${actualQualityScore} meets threshold ${requiredQuality}"
        )
    }

    /**
     * Validate complete dataset for backtesting
     *
     * Performs comprehensive validation:
     * 1. Ensures all bars are from single tier
     * 2. Checks data quality meets tier requirements
     * 3. Validates data completeness
     *
     * @param bars List of OHLC bars for backtest
     * @param expectedBarCount Expected number of bars (based on timeframe and date range)
     * @return ValidationResult with tier information and quality score
     */
    fun validateBacktestData(
        bars: List<OHLCBarEntity>,
        expectedBarCount: Long
    ): ValidationResult {
        // Step 1: Validate single tier
        val tier = validateSingleTier(bars)

        // Step 2: Calculate quality score
        val actualBarCount = bars.size.toLong()
        val completenessScore = (actualBarCount.toDouble() / expectedBarCount.toDouble()).coerceIn(0.0, 1.0)

        // Step 3: Check for data anomalies
        val zeroVolumeBars = bars.count { it.volume == 0.0 }
        val invalidBars = bars.count {
            it.high < it.low || it.open < 0 || it.close < 0 || it.high < 0 || it.low < 0
        }

        // Calculate overall quality score
        val anomalyPenalty = ((zeroVolumeBars + invalidBars).toDouble() / actualBarCount.toDouble()) * 0.1
        val overallQuality = (completenessScore - anomalyPenalty).coerceIn(0.0, 1.0)

        // Step 4: Validate against tier threshold
        validateQualityThreshold(bars, tier, overallQuality)

        // Step 5: Generate warnings if needed
        val warnings = mutableListOf<String>()
        if (completenessScore < 0.95) {
            warnings.add("Data completeness: ${String.format("%.1f%%", completenessScore * 100)} (${actualBarCount}/${expectedBarCount} bars)")
        }
        if (zeroVolumeBars > 0) {
            warnings.add("Found $zeroVolumeBars bars with zero volume")
        }
        if (invalidBars > 0) {
            warnings.add("Found $invalidBars bars with invalid OHLC values")
        }

        Timber.i(
            """
            ✅ Backtest data validation PASSED
            ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            Data Tier: ${tier.tierName}
            Quality Score: ${String.format("%.2f", overallQuality)} (required: ${tier.getMinimumQualityThreshold()})
            Completeness: ${String.format("%.1f%%", completenessScore * 100)} (${actualBarCount}/${expectedBarCount} bars)
            Zero Volume Bars: $zeroVolumeBars
            Invalid Bars: $invalidBars
            Warnings: ${if (warnings.isEmpty()) "None" else warnings.size}
            ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
            """.trimIndent()
        )

        return ValidationResult(
            tier = tier,
            qualityScore = overallQuality,
            completenessScore = completenessScore,
            actualBarCount = actualBarCount,
            expectedBarCount = expectedBarCount,
            zeroVolumeBars = zeroVolumeBars,
            invalidBars = invalidBars,
            warnings = warnings,
            passed = true
        )
    }

    /**
     * Check if tier mixing is present (without throwing exception)
     *
     * Useful for UI warnings before running backtest
     */
    fun checkForTierMixing(bars: List<OHLCBarEntity>): TierMixingCheck {
        if (bars.isEmpty()) {
            return TierMixingCheck(
                hasMixing = false,
                uniqueTiers = emptySet(),
                distribution = emptyMap()
            )
        }

        val uniqueTiers = bars.map { it.dataTier }.toSet()
        val distribution = bars.groupingBy { it.dataTier }.eachCount()

        return TierMixingCheck(
            hasMixing = uniqueTiers.size > 1,
            uniqueTiers = uniqueTiers,
            distribution = distribution
        )
    }
}

/**
 * Result of backtest data validation
 */
data class ValidationResult(
    val tier: DataTier,
    val qualityScore: Double,
    val completenessScore: Double,
    val actualBarCount: Long,
    val expectedBarCount: Long,
    val zeroVolumeBars: Int,
    val invalidBars: Int,
    val warnings: List<String>,
    val passed: Boolean
)

/**
 * Result of tier mixing check
 */
data class TierMixingCheck(
    val hasMixing: Boolean,
    val uniqueTiers: Set<String>,
    val distribution: Map<String, Int>
)
