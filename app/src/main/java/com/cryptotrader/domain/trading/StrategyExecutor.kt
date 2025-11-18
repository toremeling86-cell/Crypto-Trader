package com.cryptotrader.domain.trading

import android.content.Context
import com.cryptotrader.data.repository.KrakenRepository
import com.cryptotrader.data.repository.PortfolioRepository
import com.cryptotrader.data.repository.StrategyRepository
import com.cryptotrader.domain.model.*
import com.cryptotrader.domain.usecase.OrderType
import com.cryptotrader.domain.usecase.TradeRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Executes trading strategies
 *
 * Evaluates strategy conditions and places orders when signals are generated
 * Handles both Paper Trading and Live Trading modes
 */
@Singleton
class StrategyExecutor @Inject constructor(
    private val tradingEngine: TradingEngine,
    private val krakenRepository: KrakenRepository,
    private val portfolioRepository: PortfolioRepository,
    private val strategyRepository: StrategyRepository,
    private val positionTracker: PositionTracker,
    @ApplicationContext private val context: Context
) {

    /**
     * Evaluate strategy and execute trades if signal is generated
     */
    suspend fun evaluateAndExecute(strategy: Strategy, mode: TradingMode) {
        try {
            Timber.d("🔍 Evaluating ${mode.name} strategy: ${strategy.name}")

            // Set paper trading mode based on strategy mode
            com.cryptotrader.utils.CryptoUtils.setPaperTradingMode(context, mode == TradingMode.PAPER)

            // Get market data for all trading pairs
            val pair = strategy.tradingPairs.firstOrNull() ?: return
            val tickerResult = krakenRepository.getTicker(pair)

            if (tickerResult.isFailure) {
                Timber.w("Failed to get market data for $pair: ${tickerResult.exceptionOrNull()?.message}")
                return
            }

            val marketData = tickerResult.getOrNull() ?: return

            // Get portfolio data
            val portfolio = getPortfolio(mode)

            // Evaluate strategy conditions
            val signal = tradingEngine.evaluateStrategy(
                strategy = strategy,
                marketData = marketData,
                portfolio = portfolio,
                isBacktesting = false // Live evaluation
            )

            if (signal == null) {
                Timber.d("No signal generated for ${strategy.name}")
                return
            }

            Timber.i("🎯 Signal generated: ${signal.action} ${signal.pair} @ ${marketData.last}")

            // Execute trade based on signal
            when (signal.action) {
                TradeAction.BUY -> handleBuySignal(strategy, signal, marketData, portfolio, mode)
                TradeAction.SELL -> handleSellSignal(strategy, signal, marketData, portfolio, mode)
                TradeAction.HOLD -> {
                    // Do nothing
                }
            }

        } catch (e: Exception) {
            Timber.e(e, "Error executing strategy: ${strategy.name}")
        }
    }

    private suspend fun handleBuySignal(
        strategy: Strategy,
        signal: TradeSignal,
        marketData: MarketTicker,
        portfolio: Portfolio,
        mode: TradingMode
    ) {
        // Check if we already have an open position for this pair
        val hasPosition = positionTracker.hasOpenPosition(strategy.id, signal.pair)
        if (hasPosition) {
            Timber.d("Already have open position for ${signal.pair}. Skipping BUY.")
            return
        }

        // Calculate position size
        val availableBalance = portfolio.availableBalance
        val positionSize = availableBalance * (strategy.positionSizePercent / 100.0)

        if (positionSize < 10.0) { // Minimum $10 order on Kraken
            Timber.w("Position size too small: $$positionSize. Skipping BUY.")
            return
        }

        // Calculate volume (BTC amount to buy)
        val volume = positionSize / marketData.last

        // Create trade request
        val tradeRequest = TradeRequest(
            pair = signal.pair,
            type = TradeType.BUY,
            orderType = OrderType.MARKET,
            volume = volume,
            price = marketData.last,
            strategyId = strategy.id
        )

        // Place order (KrakenRepository handles Paper vs Live based on context)
        val result = krakenRepository.placeOrder(tradeRequest)

        if (result.isSuccess) {
            val trade = result.getOrNull()!!
            Timber.i("✅ BUY order placed: ${trade.volume} ${trade.pair} @ ${trade.price} (${mode.name})")

            // Track position
            positionTracker.openPosition(
                strategyId = strategy.id,
                pair = signal.pair,
                entryPrice = trade.price,
                volume = trade.volume,
                stopLossPrice = calculateStopLoss(trade.price, strategy.stopLossPercent, isBuy = true),
                takeProfitPrice = calculateTakeProfit(trade.price, strategy.takeProfitPercent, isBuy = true),
                mode = mode
            )

            // Update strategy stats
            updateStrategyStats(strategy.id, success = true)

        } else {
            Timber.e("❌ BUY order failed: ${result.exceptionOrNull()?.message}")
            updateStrategyStats(strategy.id, success = false)
        }
    }

    private suspend fun handleSellSignal(
        strategy: Strategy,
        signal: TradeSignal,
        marketData: MarketTicker,
        portfolio: Portfolio,
        mode: TradingMode
    ) {
        // Check if we have an open position to sell
        val position = positionTracker.getOpenPosition(strategy.id, signal.pair)
        if (position == null) {
            Timber.d("No open position for ${signal.pair}. Skipping SELL.")
            return
        }

        // Create trade request
        val tradeRequest = TradeRequest(
            pair = signal.pair,
            type = TradeType.SELL,
            orderType = OrderType.MARKET,
            volume = position.volume,
            price = marketData.last,
            strategyId = strategy.id
        )

        // Place order (KrakenRepository handles Paper vs Live based on context)
        val result = krakenRepository.placeOrder(tradeRequest)

        if (result.isSuccess) {
            val trade = result.getOrNull()!!
            Timber.i("✅ SELL order placed: ${trade.volume} ${trade.pair} @ ${trade.price} (${mode.name})")

            // Calculate P&L
            val pnl = (trade.price - position.entryPrice) * position.volume - trade.fee

            // Close position
            positionTracker.closePosition(strategy.id, signal.pair, pnl)

            // Update strategy stats
            updateStrategyStats(strategy.id, success = pnl > 0, pnl = pnl)

        } else {
            Timber.e("❌ SELL order failed: ${result.exceptionOrNull()?.message}")
            updateStrategyStats(strategy.id, success = false)
        }
    }

    private suspend fun getPortfolio(mode: TradingMode): Portfolio {
        return when (mode) {
            TradingMode.PAPER -> {
                // Get paper trading balance from SharedPreferences
                val paperBalance = com.cryptotrader.utils.CryptoUtils.getPaperTradingBalance(context)
                Portfolio(
                    totalValue = paperBalance,
                    availableBalance = paperBalance,
                    balances = emptyMap(),
                    totalProfit = 0.0,
                    totalProfitPercent = 0.0,
                    dayProfit = 0.0,
                    dayProfitPercent = 0.0,
                    openPositions = 0
                )
            }
            TradingMode.LIVE -> {
                // Get real portfolio snapshot from Kraken
                val snapshot = portfolioRepository.getCurrentSnapshot().getOrNull()
                if (snapshot != null) {
                    Portfolio(
                        totalValue = snapshot.totalValue,
                        availableBalance = snapshot.totalValue, // Use total value as available balance
                        balances = emptyMap(),
                        totalProfit = 0.0,
                        totalProfitPercent = 0.0,
                        dayProfit = 0.0,
                        dayProfitPercent = 0.0,
                        openPositions = 0
                    )
                } else {
                    Portfolio(
                        totalValue = 0.0,
                        availableBalance = 0.0,
                        balances = emptyMap(),
                        totalProfit = 0.0,
                        totalProfitPercent = 0.0,
                        dayProfit = 0.0,
                        dayProfitPercent = 0.0,
                        openPositions = 0
                    )
                }
            }
            TradingMode.INACTIVE -> {
                Portfolio(
                    totalValue = 0.0,
                    availableBalance = 0.0,
                    balances = emptyMap(),
                    totalProfit = 0.0,
                    totalProfitPercent = 0.0,
                    dayProfit = 0.0,
                    dayProfitPercent = 0.0,
                    openPositions = 0
                )
            }
        }
    }

    private fun calculateStopLoss(entryPrice: Double, stopLossPercent: Double, isBuy: Boolean): Double {
        return if (isBuy) {
            entryPrice * (1 - stopLossPercent / 100.0)
        } else {
            entryPrice * (1 + stopLossPercent / 100.0)
        }
    }

    private fun calculateTakeProfit(entryPrice: Double, takeProfitPercent: Double, isBuy: Boolean): Double {
        return if (isBuy) {
            entryPrice * (1 + takeProfitPercent / 100.0)
        } else {
            entryPrice * (1 - takeProfitPercent / 100.0)
        }
    }

    private suspend fun updateStrategyStats(strategyId: String, success: Boolean, pnl: Double = 0.0) {
        try {
            val strategy = strategyRepository.getStrategyById(strategyId) ?: return

            val updatedStrategy = strategy.copy(
                lastExecuted = System.currentTimeMillis(),
                totalTrades = strategy.totalTrades + 1,
                successfulTrades = if (success) strategy.successfulTrades + 1 else strategy.successfulTrades,
                failedTrades = if (!success) strategy.failedTrades + 1 else strategy.failedTrades,
                winRate = calculateWinRate(strategy.successfulTrades + if (success) 1 else 0, strategy.totalTrades + 1),
                totalProfit = strategy.totalProfit + pnl
            )

            strategyRepository.updateStrategy(updatedStrategy)
        } catch (e: Exception) {
            Timber.e(e, "Failed to update strategy stats")
        }
    }

    private fun calculateWinRate(wins: Int, total: Int): Double {
        return if (total > 0) (wins.toDouble() / total.toDouble()) * 100.0 else 0.0
    }
}
