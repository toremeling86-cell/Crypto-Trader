package com.cryptotrader.domain.usecase

import com.cryptotrader.domain.ai.ClaudeStrategyGenerator
import com.cryptotrader.domain.backtesting.BacktestResult
import com.cryptotrader.domain.model.Strategy
import com.cryptotrader.domain.model.RiskLevel
import com.cryptotrader.domain.trading.RiskManager
import timber.log.Timber
import java.util.*
import javax.inject.Inject

/**
 * Use case for generating trading strategies using Claude AI
 *
 * REAL AI IMPLEMENTATION - Calls Claude API to generate strategies
 * INCLUDES AUTO-BACKTEST - Validates strategies against historical data
 */
class GenerateStrategyUseCase @Inject constructor(
    private val riskManager: RiskManager,
    private val claudeStrategyGenerator: ClaudeStrategyGenerator,
    private val autoBacktestUseCase: AutoBacktestUseCase
) {

    /**
     * Generate strategy using REAL Claude AI with automatic backtesting
     *
     * @param description Natural language strategy description
     * @param tradingPairs Trading pairs to apply strategy to
     * @param runBacktest Whether to automatically backtest the strategy (default: true)
     * @return AI-generated Strategy with backtest results or error
     */
    suspend operator fun invoke(
        description: String,
        tradingPairs: List<String>,
        runBacktest: Boolean = true
    ): Result<StrategyGenerationResult> {
        return try {
            Timber.i("🤖 Generating strategy with Claude AI...")

            // REAL AI CALL - Generate strategy using Claude
            val result = claudeStrategyGenerator.generateStrategy(description)

            if (result.isFailure) {
                // If AI fails, fall back to template-based generation
                Timber.w("Claude AI failed, using fallback: ${result.exceptionOrNull()?.message}")
                return createFallbackStrategyWithBacktest(description, tradingPairs, runBacktest)
            }

            val strategy = result.getOrThrow()

            // Override trading pairs if specified by user
            val finalStrategy = if (tradingPairs.isNotEmpty()) {
                strategy.copy(tradingPairs = tradingPairs)
            } else {
                strategy
            }

            // Validate strategy
            riskManager.validateStrategy(finalStrategy).getOrThrow()

            Timber.i("✅ Strategy successfully generated: ${finalStrategy.name}")

            // AUTO-BACKTEST: Validate strategy against historical data
            val backtestValidation = if (runBacktest) {
                Timber.i("🔬 Running automatic backtest validation...")
                val backtestResult = autoBacktestUseCase.invoke(finalStrategy)

                if (backtestResult.isFailure) {
                    Timber.w("Backtest failed: ${backtestResult.exceptionOrNull()?.message}")
                    null
                } else {
                    val validation = backtestResult.getOrThrow()
                    Timber.i("📊 Backtest Status: ${validation.status}")
                    Timber.i("   ${validation.recommendation}")
                    validation
                }
            } else {
                null
            }

            Result.success(
                StrategyGenerationResult(
                    strategy = finalStrategy,
                    backtestValidation = backtestValidation,
                    source = StrategySource.CLAUDE_AI
                )
            )

        } catch (e: Exception) {
            Timber.e(e, "Error generating strategy")
            Result.failure(e)
        }
    }

    /**
     * Fallback strategy generation if AI fails
     */
    private suspend fun createFallbackStrategyWithBacktest(
        description: String,
        tradingPairs: List<String>,
        runBacktest: Boolean
    ): Result<StrategyGenerationResult> {
        return try {
            val strategy = createSimpleStrategy(description, tradingPairs)
            riskManager.validateStrategy(strategy).getOrThrow()
            Timber.d("Fallback strategy generated: ${strategy.name}")

            // Run backtest on fallback strategy too
            val backtestValidation = if (runBacktest) {
                val backtestResult = autoBacktestUseCase.invoke(strategy)
                if (backtestResult.isSuccess) {
                    backtestResult.getOrThrow()
                } else {
                    null
                }
            } else {
                null
            }

            Result.success(
                StrategyGenerationResult(
                    strategy = strategy,
                    backtestValidation = backtestValidation,
                    source = StrategySource.TEMPLATE_FALLBACK
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun createSimpleStrategy(
        description: String,
        tradingPairs: List<String>
    ): Strategy {
        val desc = description.lowercase()

        // Detect strategy type from description
        val strategyType = when {
            desc.contains("rsi") -> StrategyType.RSI
            desc.contains("macd") -> StrategyType.MACD
            desc.contains("moving average") || desc.contains("ma") || desc.contains("sma") -> {
                StrategyType.MOVING_AVERAGE
            }
            desc.contains("bollinger") -> StrategyType.BOLLINGER
            desc.contains("momentum") || desc.contains("trend") -> StrategyType.MOMENTUM
            desc.contains("scalp") -> StrategyType.SCALPING
            else -> StrategyType.BALANCED
        }

        // Detect risk level
        val isConservative = desc.contains("safe") || desc.contains("conservative") ||
                desc.contains("low risk")
        val isAggressive = desc.contains("aggressive") || desc.contains("risky") ||
                desc.contains("high risk")

        val riskLevel = when {
            isConservative -> RiskLevel.LOW
            isAggressive -> RiskLevel.HIGH
            else -> RiskLevel.MEDIUM
        }

        // Get strategy parameters based on type
        val (name, entryConditions, exitConditions, positionSize, stopLoss, takeProfit) =
            getStrategyParameters(strategyType, riskLevel)

        return Strategy(
            id = UUID.randomUUID().toString(),
            name = name,
            description = description,
            entryConditions = entryConditions,
            exitConditions = exitConditions,
            positionSizePercent = positionSize,
            stopLossPercent = stopLoss,
            takeProfitPercent = takeProfit,
            tradingPairs = tradingPairs,
            isActive = false,
            riskLevel = riskLevel
        )
    }

    private enum class StrategyType {
        RSI, MACD, MOVING_AVERAGE, BOLLINGER, MOMENTUM, SCALPING, BALANCED
    }

    private fun getStrategyParameters(
        type: StrategyType,
        riskLevel: RiskLevel
    ): StrategyParams {
        val positionSize = when (riskLevel) {
            RiskLevel.LOW -> 2.0
            RiskLevel.MEDIUM -> 5.0
            RiskLevel.HIGH -> 10.0
        }

        val stopLoss = when (riskLevel) {
            RiskLevel.LOW -> 2.0
            RiskLevel.MEDIUM -> 3.0
            RiskLevel.HIGH -> 5.0
        }

        val takeProfit = stopLoss * 2.5

        return when (type) {
            StrategyType.RSI -> StrategyParams(
                name = "RSI Oversold/Overbought Strategy",
                entryConditions = listOf(
                    "RSI < 30", // Oversold - buy signal
                    "Volume > average"
                ),
                exitConditions = listOf(
                    "RSI > 70", // Overbought - sell signal
                    "Stop loss"
                ),
                positionSize, stopLoss, takeProfit
            )

            StrategyType.MACD -> StrategyParams(
                name = "MACD Crossover Strategy",
                entryConditions = listOf(
                    "MACD crossover", // MACD crosses above signal line
                    "MACD histogram positive"
                ),
                exitConditions = listOf(
                    "MACD < signal",
                    "Stop loss"
                ),
                positionSize, stopLoss, takeProfit
            )

            StrategyType.MOVING_AVERAGE -> StrategyParams(
                name = "Golden Cross Strategy",
                entryConditions = listOf(
                    "SMA_20 > SMA_50", // Golden cross
                    "Price > SMA_20"
                ),
                exitConditions = listOf(
                    "SMA_20 < SMA_50", // Death cross
                    "Stop loss"
                ),
                positionSize, stopLoss, takeProfit
            )

            StrategyType.BOLLINGER -> StrategyParams(
                name = "Bollinger Bounce Strategy",
                entryConditions = listOf(
                    "Price < Bollinger_lower", // Bounce from lower band
                    "RSI < 40"
                ),
                exitConditions = listOf(
                    "Price > Bollinger_upper",
                    "Stop loss"
                ),
                positionSize, stopLoss, takeProfit
            )

            StrategyType.MOMENTUM -> StrategyParams(
                name = "Momentum Trading Strategy",
                entryConditions = listOf(
                    "Momentum > 3%",
                    "Volume > average",
                    "Price near high"
                ),
                exitConditions = listOf(
                    "Momentum < -2%",
                    "Stop loss"
                ),
                positionSize, stopLoss, takeProfit
            )

            StrategyType.SCALPING -> StrategyParams(
                name = "Scalping Strategy",
                entryConditions = listOf(
                    "Momentum > 1%",
                    "Volume high"
                ),
                exitConditions = listOf(
                    "Take profit", // Quick profit target
                    "Stop loss"
                ),
                positionSize = positionSize * 1.5, // Larger positions for scalping
                stopLoss = stopLoss * 0.5, // Tighter stop loss
                takeProfit = takeProfit * 0.5 // Quick take profit
            )

            StrategyType.BALANCED -> StrategyParams(
                name = "Balanced Multi-Indicator Strategy",
                entryConditions = listOf(
                    "RSI < 40",
                    "SMA_20 > SMA_50",
                    "MACD positive"
                ),
                exitConditions = listOf(
                    "RSI > 65",
                    "Stop loss"
                ),
                positionSize, stopLoss, takeProfit
            )
        }
    }

    private data class StrategyParams(
        val name: String,
        val entryConditions: List<String>,
        val exitConditions: List<String>,
        val positionSize: Double,
        val stopLoss: Double,
        val takeProfit: Double
    )
}

/**
 * Result of strategy generation including backtest validation
 */
data class StrategyGenerationResult(
    val strategy: Strategy,
    val backtestValidation: BacktestValidation?,
    val source: StrategySource
)

/**
 * Source of generated strategy
 */
enum class StrategySource {
    CLAUDE_AI,           // Generated by Claude AI
    TEMPLATE_FALLBACK    // Generated by template fallback
}
