package com.cryptotrader.domain.trading

import com.cryptotrader.domain.backtesting.BacktestEngine
import com.cryptotrader.domain.backtesting.BacktestResult
import com.cryptotrader.domain.backtesting.PriceBar
import com.cryptotrader.domain.model.Strategy
import com.cryptotrader.utils.toBigDecimalMoney
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Strategy parameter optimization using grid search
 *
 * Finds optimal parameters by testing combinations and selecting best performing:
 * - Stop-loss and take-profit levels
 * - Position size
 * - Indicator parameters
 * - ATR multipliers
 */
@Singleton
class StrategyOptimizer @Inject constructor(
    private val backtestEngine: BacktestEngine
) {

    /**
     * Optimize strategy parameters using grid search
     *
     * @param baseStrategy Base strategy to optimize
     * @param historicalData Historical price data for backtesting
     * @param parameterRanges Parameter ranges to test
     * @return Best strategy configuration
     */
    suspend fun optimizeStrategy(
        baseStrategy: Strategy,
        historicalData: List<PriceBar>,
        parameterRanges: ParameterRanges = ParameterRanges()
    ): OptimizationResult {
        Timber.i("Starting strategy optimization for ${baseStrategy.name}")

        val results = mutableListOf<ParameterTestResult>()

        // Grid search over parameter combinations
        for (stopLoss in parameterRanges.stopLossRange) {
            for (takeProfit in parameterRanges.takeProfitRange) {
                for (positionSize in parameterRanges.positionSizeRange) {
                    for (atrMultiplier in parameterRanges.atrMultiplierRange) {

                        // Create test strategy with these parameters
                        val testStrategy = baseStrategy.copy(
                            stopLossPercent = stopLoss,
                            takeProfitPercent = takeProfit,
                            positionSizePercent = positionSize,
                            atrMultiplier = atrMultiplier
                        )

                        // Run backtest
                        val resultDecimal = backtestEngine.runBacktestDecimal(
                            strategy = testStrategy,
                            historicalData = historicalData,
                            startingBalance = 10000.0.toBigDecimalMoney()
                        )
                        val backtestResult = resultDecimal.toBacktestResult()

                        // Calculate fitness score
                        val fitnessScore = calculateFitnessScore(backtestResult)

                        results.add(
                            ParameterTestResult(
                                stopLoss = stopLoss,
                                takeProfit = takeProfit,
                                positionSize = positionSize,
                                atrMultiplier = atrMultiplier,
                                backtestResult = backtestResult,
                                fitnessScore = fitnessScore
                            )
                        )

                        Timber.d("Tested: SL=$stopLoss%, TP=$takeProfit%, Size=$positionSize%, ATR=$atrMultiplier -> Score=$fitnessScore")
                    }
                }
            }
        }

        // Find best parameters
        val bestResult = results.maxByOrNull { it.fitnessScore }

        if (bestResult == null) {
            Timber.e("Optimization failed - no valid results")
            return OptimizationResult(
                originalStrategy = baseStrategy,
                optimizedStrategy = baseStrategy,
                improvement = 0.0,
                testedCombinations = 0,
                bestScore = 0.0
            )
        }

        // Create optimized strategy
        val optimizedStrategy = baseStrategy.copy(
            stopLossPercent = bestResult.stopLoss,
            takeProfitPercent = bestResult.takeProfit,
            positionSizePercent = bestResult.positionSize,
            atrMultiplier = bestResult.atrMultiplier
        )

        // Calculate improvement
        val originalResultDecimal = backtestEngine.runBacktestDecimal(baseStrategy, historicalData, 10000.0.toBigDecimalMoney())
        val originalBacktest = originalResultDecimal.toBacktestResult()
        val originalScore = calculateFitnessScore(originalBacktest)
        val improvement = ((bestResult.fitnessScore - originalScore) / originalScore) * 100.0

        Timber.i("Optimization complete: Best score=${bestResult.fitnessScore}, Improvement=${improvement}%")
        Timber.i("Optimal params: SL=${bestResult.stopLoss}%, TP=${bestResult.takeProfit}%, " +
                "Size=${bestResult.positionSize}%, ATR=${bestResult.atrMultiplier}")

        return OptimizationResult(
            originalStrategy = baseStrategy,
            optimizedStrategy = optimizedStrategy,
            improvement = improvement,
            testedCombinations = results.size,
            bestScore = bestResult.fitnessScore,
            allResults = results
        )
    }

    /**
     * Calculate fitness score for a backtest result
     * Balances profitability, win rate, and risk metrics
     */
    private fun calculateFitnessScore(result: BacktestResult): Double {
        // Multi-objective fitness function
        val profitWeight = 0.4
        val sharpeWeight = 0.3
        val winRateWeight = 0.2
        val drawdownWeight = 0.1

        // Normalize profit (0-100 scale)
        val profitScore = result.totalPnLPercent.coerceIn(-100.0, 100.0) + 100.0

        // Sharpe ratio (0-5 typical range, multiply by 20 to scale to 100)
        val sharpeScore = (result.sharpeRatio.coerceIn(0.0, 5.0) * 20.0)

        // Win rate (0-100)
        val winRateScore = result.winRate

        // Drawdown penalty (lower is better, invert)
        val drawdownScore = 100.0 - result.maxDrawdown.coerceAtMost(100.0)

        return (profitScore * profitWeight) +
                (sharpeScore * sharpeWeight) +
                (winRateScore * winRateWeight) +
                (drawdownScore * drawdownWeight)
    }

    /**
     * Quick optimize using common parameter sets
     */
    suspend fun quickOptimize(
        strategy: Strategy,
        historicalData: List<PriceBar>
    ): OptimizationResult {
        val quickRanges = ParameterRanges(
            stopLossRange = listOf(2.0, 3.0, 5.0),
            takeProfitRange = listOf(5.0, 8.0, 10.0),
            positionSizeRange = listOf(10.0, 15.0, 20.0),
            atrMultiplierRange = listOf(1.5, 2.0, 2.5)
        )

        return optimizeStrategy(strategy, historicalData, quickRanges)
    }
}

/**
 * Parameter ranges for optimization
 */
data class ParameterRanges(
    val stopLossRange: List<Double> = listOf(1.0, 2.0, 3.0, 5.0),
    val takeProfitRange: List<Double> = listOf(3.0, 5.0, 8.0, 10.0, 15.0),
    val positionSizeRange: List<Double> = listOf(5.0, 10.0, 15.0, 20.0),
    val atrMultiplierRange: List<Double> = listOf(1.0, 1.5, 2.0, 2.5, 3.0)
)

/**
 * Result of a single parameter test
 */
data class ParameterTestResult(
    val stopLoss: Double,
    val takeProfit: Double,
    val positionSize: Double,
    val atrMultiplier: Double,
    val backtestResult: BacktestResult,
    val fitnessScore: Double
)

/**
 * Optimization result
 */
data class OptimizationResult(
    val originalStrategy: Strategy,
    val optimizedStrategy: Strategy,
    val improvement: Double,  // Percentage improvement
    val testedCombinations: Int,
    val bestScore: Double,
    val allResults: List<ParameterTestResult> = emptyList()
)
