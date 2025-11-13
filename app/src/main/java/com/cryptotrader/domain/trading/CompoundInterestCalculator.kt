package com.cryptotrader.domain.trading

import com.cryptotrader.domain.model.Portfolio
import com.cryptotrader.domain.model.Strategy
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.pow

/**
 * Compound interest calculator and reinvestment manager
 *
 * Maximizes long-term growth by reinvesting profits:
 * - Calculates position sizes based on growing equity
 * - Projects compound growth
 * - Manages profit reinvestment strategies
 */
@Singleton
class CompoundInterestCalculator @Inject constructor() {

    companion object {
        private const val MIN_PROFIT_FOR_REINVESTMENT = 100.0  // Reinvest when profit > $100
        private const val PROFIT_REINVESTMENT_PERCENT = 100.0   // Reinvest 100% of profits
    }

    /**
     * Calculate position size with compound interest
     *
     * Uses current total equity (initial + profits) for position sizing
     * instead of just initial capital
     *
     * @param portfolio Current portfolio state
     * @param strategy Strategy with position size settings
     * @return Compounded position size
     */
    fun calculateCompoundedPositionSize(
        portfolio: Portfolio,
        strategy: Strategy
    ): Double {
        // Use total portfolio value (initial + profits) instead of just initial capital
        val totalEquity = portfolio.totalValue

        // Calculate position size based on growing equity
        val positionSize = totalEquity * (strategy.positionSizePercent / 100.0)

        val reinvestedProfits = portfolio.totalProfit

        Timber.d("Compounded position sizing: Total equity=$totalEquity " +
                "(includes $reinvestedProfits profits), Position=${positionSize}")

        return positionSize
    }

    /**
     * Calculate how much profit should be reinvested vs withdrawn
     *
     * @param totalProfit Total accumulated profit
     * @param reinvestmentPercent Percentage to reinvest (0-100)
     * @return Reinvestment allocation
     */
    fun calculateReinvestmentAllocation(
        totalProfit: Double,
        reinvestmentPercent: Double = PROFIT_REINVESTMENT_PERCENT
    ): ReinvestmentAllocation {
        if (totalProfit < MIN_PROFIT_FOR_REINVESTMENT) {
            return ReinvestmentAllocation(
                totalProfit = totalProfit,
                toReinvest = totalProfit,
                toWithdraw = 0.0,
                recommendation = "Profit below minimum threshold - reinvest all"
            )
        }

        val toReinvest = totalProfit * (reinvestmentPercent / 100.0)
        val toWithdraw = totalProfit - toReinvest

        val recommendation = when {
            reinvestmentPercent >= 90.0 -> "Aggressive growth mode - compounding rapidly"
            reinvestmentPercent >= 50.0 -> "Balanced approach - growing while taking profits"
            else -> "Conservative mode - securing profits"
        }

        return ReinvestmentAllocation(
            totalProfit = totalProfit,
            toReinvest = toReinvest,
            toWithdraw = toWithdraw,
            recommendation = recommendation
        )
    }

    /**
     * Project compound growth over time
     *
     * @param initialCapital Starting capital
     * @param avgMonthlyReturn Average monthly return (as decimal, e.g., 0.05 = 5%)
     * @param months Number of months to project
     * @param reinvestmentRate Percentage of profits to reinvest
     * @return Growth projection
     */
    fun projectCompoundGrowth(
        initialCapital: Double,
        avgMonthlyReturn: Double,
        months: Int,
        reinvestmentRate: Double = 1.0 // 1.0 = 100% reinvestment
    ): GrowthProjection {
        val monthlyEquity = mutableListOf(initialCapital)
        var currentEquity = initialCapital

        for (month in 1..months) {
            // Calculate return for this month
            val monthReturn = currentEquity * avgMonthlyReturn

            // Reinvest portion of profits
            val reinvestedAmount = monthReturn * reinvestmentRate
            currentEquity += reinvestedAmount

            monthlyEquity.add(currentEquity)
        }

        val totalProfit = currentEquity - initialCapital
        val totalReturnPercent = (totalProfit / initialCapital) * 100.0

        // Calculate CAGR (Compound Annual Growth Rate)
        val years = months / 12.0
        val cagr = if (years > 0) {
            (((currentEquity / initialCapital).pow(1.0 / years)) - 1.0) * 100.0
        } else 0.0

        return GrowthProjection(
            initialCapital = initialCapital,
            finalEquity = currentEquity,
            totalProfit = totalProfit,
            totalReturnPercent = totalReturnPercent,
            cagr = cagr,
            months = months,
            monthlyEquity = monthlyEquity
        )
    }

    /**
     * Compare simple vs compound growth
     */
    fun compareSimpleVsCompound(
        initialCapital: Double,
        avgMonthlyReturn: Double,
        months: Int
    ): GrowthComparison {
        // Simple interest (no reinvestment)
        val simpleProfit = initialCapital * avgMonthlyReturn * months
        val simpleFinal = initialCapital + simpleProfit

        // Compound interest (full reinvestment)
        val compoundProjection = projectCompoundGrowth(
            initialCapital,
            avgMonthlyReturn,
            months,
            reinvestmentRate = 1.0
        )

        val compoundAdvantage = compoundProjection.finalEquity - simpleFinal
        val advantagePercent = (compoundAdvantage / simpleFinal) * 100.0

        return GrowthComparison(
            initialCapital = initialCapital,
            months = months,
            avgMonthlyReturn = avgMonthlyReturn * 100.0,
            simpleFinalEquity = simpleFinal,
            compoundFinalEquity = compoundProjection.finalEquity,
            compoundAdvantage = compoundAdvantage,
            advantagePercent = advantagePercent
        )
    }

    /**
     * Calculate effective compound rate from trade results
     */
    fun calculateEffectiveCompoundRate(
        initialCapital: Double,
        currentEquity: Double,
        daysSinceStart: Int
    ): Double {
        if (daysSinceStart <= 0 || initialCapital <= 0) return 0.0

        val totalReturn = (currentEquity / initialCapital) - 1.0
        val years = daysSinceStart / 365.0

        // Calculate annualized compound rate
        return if (years > 0 && currentEquity > 0 && initialCapital > 0) {
            ((currentEquity / initialCapital).pow(1.0 / years) - 1.0) * 100.0
        } else 0.0
    }
}

/**
 * Reinvestment allocation
 */
data class ReinvestmentAllocation(
    val totalProfit: Double,
    val toReinvest: Double,
    val toWithdraw: Double,
    val recommendation: String
)

/**
 * Growth projection
 */
data class GrowthProjection(
    val initialCapital: Double,
    val finalEquity: Double,
    val totalProfit: Double,
    val totalReturnPercent: Double,
    val cagr: Double,  // Compound Annual Growth Rate
    val months: Int,
    val monthlyEquity: List<Double>
)

/**
 * Growth comparison between simple and compound
 */
data class GrowthComparison(
    val initialCapital: Double,
    val months: Int,
    val avgMonthlyReturn: Double,  // As percentage
    val simpleFinalEquity: Double,
    val compoundFinalEquity: Double,
    val compoundAdvantage: Double,
    val advantagePercent: Double
)
