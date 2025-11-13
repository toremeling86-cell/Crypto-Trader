package com.cryptotrader.domain.trading

import com.cryptotrader.data.repository.HistoricalDataRepository
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * Correlation analysis for portfolio diversification
 *
 * Analyzes price correlations between trading pairs to:
 * - Avoid over-concentration in correlated assets
 * - Improve portfolio diversification
 * - Reduce systematic risk
 */
@Singleton
class CorrelationAnalyzer @Inject constructor(
    private val historicalDataRepository: HistoricalDataRepository
) {

    companion object {
        private const val MIN_DATA_POINTS = 30
        private const val HIGH_CORRELATION_THRESHOLD = 0.7  // |r| > 0.7 = highly correlated
        private const val LOW_CORRELATION_THRESHOLD = 0.3   // |r| < 0.3 = low correlation
    }

    /**
     * Calculate correlation between two trading pairs
     *
     * @param pair1 First trading pair
     * @param pair2 Second trading pair
     * @param interval Timeframe in minutes
     * @param period Number of bars to analyze
     * @return Correlation coefficient (-1.0 to 1.0)
     */
    suspend fun calculateCorrelation(
        pair1: String,
        pair2: String,
        interval: Int = 60,
        period: Int = 100
    ): Double? {
        try {
            // Fetch historical data for both pairs
            val bars1Result = historicalDataRepository.getRecentBars(pair1, interval, period)
            val bars2Result = historicalDataRepository.getRecentBars(pair2, interval, period)

            if (bars1Result.isFailure || bars2Result.isFailure) {
                Timber.w("Failed to fetch data for correlation analysis")
                return null
            }

            val bars1 = bars1Result.getOrNull() ?: return null
            val bars2 = bars2Result.getOrNull() ?: return null

            if (bars1.size < MIN_DATA_POINTS || bars2.size < MIN_DATA_POINTS) {
                Timber.w("Insufficient data for correlation analysis")
                return null
            }

            // Get closing prices
            val prices1 = bars1.map { it.close }
            val prices2 = bars2.map { it.close }

            // Calculate returns (percentage changes)
            val returns1 = calculateReturns(prices1)
            val returns2 = calculateReturns(prices2)

            // Ensure same length
            val minLength = minOf(returns1.size, returns2.size)
            val r1 = returns1.take(minLength)
            val r2 = returns2.take(minLength)

            // Calculate Pearson correlation coefficient
            val correlation = calculatePearsonCorrelation(r1, r2)

            Timber.d("Correlation between $pair1 and $pair2: ${String.format("%.3f", correlation)}")

            return correlation

        } catch (e: Exception) {
            Timber.e(e, "Error calculating correlation")
            return null
        }
    }

    /**
     * Calculate returns (percentage changes) from prices
     */
    private fun calculateReturns(prices: List<Double>): List<Double> {
        val returns = mutableListOf<Double>()

        for (i in 1 until prices.size) {
            val returnPct = ((prices[i] - prices[i - 1]) / prices[i - 1]) * 100.0
            returns.add(returnPct)
        }

        return returns
    }

    /**
     * Calculate Pearson correlation coefficient
     */
    private fun calculatePearsonCorrelation(x: List<Double>, y: List<Double>): Double {
        if (x.size != y.size || x.isEmpty()) return 0.0

        val n = x.size
        val meanX = x.average()
        val meanY = y.average()

        var numerator = 0.0
        var sumSqX = 0.0
        var sumSqY = 0.0

        for (i in 0 until n) {
            val dx = x[i] - meanX
            val dy = y[i] - meanY

            numerator += dx * dy
            sumSqX += dx * dx
            sumSqY += dy * dy
        }

        val denominator = sqrt(sumSqX * sumSqY)

        return if (denominator > 0.0) {
            numerator / denominator
        } else {
            0.0
        }
    }

    /**
     * Analyze correlation between a pair and all existing portfolio positions
     *
     * @param newPair New pair to analyze
     * @param existingPairs Pairs already in portfolio
     * @return Correlation analysis result
     */
    suspend fun analyzePortfolioCorrelation(
        newPair: String,
        existingPairs: List<String>
    ): PortfolioCorrelationAnalysis {
        if (existingPairs.isEmpty()) {
            return PortfolioCorrelationAnalysis(
                newPair = newPair,
                correlations = emptyMap(),
                avgCorrelation = 0.0,
                maxCorrelation = 0.0,
                isDiversified = true,
                recommendation = "No existing positions - OK to trade"
            )
        }

        val correlations = mutableMapOf<String, Double>()

        for (existingPair in existingPairs) {
            val corr = calculateCorrelation(newPair, existingPair)
            if (corr != null) {
                correlations[existingPair] = corr
            }
        }

        if (correlations.isEmpty()) {
            return PortfolioCorrelationAnalysis(
                newPair = newPair,
                correlations = emptyMap(),
                avgCorrelation = 0.0,
                maxCorrelation = 0.0,
                isDiversified = true,
                recommendation = "Unable to calculate correlation - proceed with caution"
            )
        }

        val avgCorr = correlations.values.map { kotlin.math.abs(it) }.average()
        val maxCorr = correlations.values.maxOf { kotlin.math.abs(it) }

        // Determine if adding this pair would maintain diversification
        val isDiversified = maxCorr < HIGH_CORRELATION_THRESHOLD

        val recommendation = when {
            maxCorr > HIGH_CORRELATION_THRESHOLD -> {
                val mostCorrelated = correlations.maxByOrNull { kotlin.math.abs(it.value) }
                "⚠️ Highly correlated with ${mostCorrelated?.key} (${String.format("%.2f", mostCorrelated?.value)}). Consider skipping to maintain diversification."
            }
            avgCorr > 0.5 -> {
                "⚡ Moderately correlated with portfolio (avg: ${String.format("%.2f", avgCorr)}). OK to trade but monitor exposure."
            }
            else -> {
                "✅ Well diversified from existing positions (avg corr: ${String.format("%.2f", avgCorr)}). Good trade candidate."
            }
        }

        return PortfolioCorrelationAnalysis(
            newPair = newPair,
            correlations = correlations,
            avgCorrelation = avgCorr,
            maxCorrelation = maxCorr,
            isDiversified = isDiversified,
            recommendation = recommendation
        )
    }

    /**
     * Build correlation matrix for multiple pairs
     */
    suspend fun buildCorrelationMatrix(pairs: List<String>): Map<String, Map<String, Double>> {
        val matrix = mutableMapOf<String, MutableMap<String, Double>>()

        for (pair1 in pairs) {
            matrix[pair1] = mutableMapOf()

            for (pair2 in pairs) {
                if (pair1 == pair2) {
                    matrix[pair1]!![pair2] = 1.0
                } else {
                    val corr = calculateCorrelation(pair1, pair2)
                    matrix[pair1]!![pair2] = corr ?: 0.0
                }
            }
        }

        return matrix
    }
}

/**
 * Portfolio correlation analysis result
 */
data class PortfolioCorrelationAnalysis(
    val newPair: String,
    val correlations: Map<String, Double>,
    val avgCorrelation: Double,
    val maxCorrelation: Double,
    val isDiversified: Boolean,
    val recommendation: String
)
