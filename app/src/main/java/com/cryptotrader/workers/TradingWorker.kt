package com.cryptotrader.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.cryptotrader.data.repository.KrakenRepository
import com.cryptotrader.data.repository.StrategyRepository
import com.cryptotrader.domain.model.Portfolio
import com.cryptotrader.domain.model.Position
import com.cryptotrader.domain.model.TradeAction
import com.cryptotrader.domain.trading.RiskManager
import com.cryptotrader.domain.trading.StopLossMonitor
import com.cryptotrader.domain.trading.TradeResult
import com.cryptotrader.domain.trading.TradingEngine
import com.cryptotrader.domain.usecase.ExecuteTradeUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import timber.log.Timber

/**
 * Background worker that periodically evaluates strategies and executes trades
 */
@HiltWorker
class TradingWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val strategyRepository: StrategyRepository,
    private val krakenRepository: KrakenRepository,
    private val tradingEngine: TradingEngine,
    private val executeTradeUseCase: ExecuteTradeUseCase,
    private val stopLossMonitor: StopLossMonitor,
    private val riskManager: RiskManager,
    private val profitCalculator: com.cryptotrader.domain.trading.ProfitCalculator,
    private val notificationManager: com.cryptotrader.notifications.NotificationManager
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        return try {
            Timber.d("TradingWorker started")

            // FIRST: Check if emergency stop is active
            if (com.cryptotrader.utils.CryptoUtils.isEmergencyStopActive(applicationContext)) {
                Timber.w("🚨 Emergency stop is active - Trading halted")
                return Result.success()
            }

            // Get active strategies
            val activeStrategies = strategyRepository.getActiveStrategies().first()

            if (activeStrategies.isEmpty()) {
                Timber.d("No active strategies, skipping")
                return Result.success()
            }

            // Get current balance and construct portfolio
            val balanceResult = krakenRepository.getBalance()
            if (balanceResult.isFailure) {
                Timber.e("Failed to get balance: ${balanceResult.exceptionOrNull()?.message}")
                return Result.retry()
            }

            val krakenBalances = balanceResult.getOrNull() ?: emptyMap()

            // Parse Kraken balance response
            // Format: {"ZUSD": "1000.0000", "XXBT": "0.5000", ...}
            val assetBalances = mutableMapOf<String, com.cryptotrader.domain.model.AssetBalance>()
            var totalValueUSD = 0.0
            var availableFunds = 0.0

            krakenBalances.forEach { (asset, balanceStr) ->
                val balance = balanceStr.toDoubleOrNull() ?: 0.0
                if (balance > 0) {
                    // Normalize Kraken asset names (ZUSD -> USD, XXBT -> XBT)
                    val normalizedAsset = when {
                        asset.startsWith("Z") && asset.length == 4 -> asset.substring(1)
                        asset.startsWith("X") && asset.length == 4 -> asset.substring(1)
                        else -> asset
                    }

                    // Calculate value in USD
                    val valueInUSD = when (normalizedAsset) {
                        "USD" -> balance // Already in USD
                        "XBT", "BTC" -> {
                            // Get BTC price to calculate USD value
                            val btcTickerResult = krakenRepository.getTicker("XXBTZUSD")
                            if (btcTickerResult.isSuccess) {
                                balance * (btcTickerResult.getOrNull()?.last ?: 0.0)
                            } else {
                                balance * 40000.0 // Fallback estimate
                            }
                        }
                        else -> balance * 1.0 // Fallback: assume 1:1 with USD for unknowns
                    }

                    assetBalances[normalizedAsset] = com.cryptotrader.domain.model.AssetBalance(
                        asset = normalizedAsset,
                        balance = balance,
                        valueInUSD = valueInUSD,
                        percentOfPortfolio = 0.0 // Will calculate after total
                    )

                    totalValueUSD += valueInUSD

                    // Available funds = USD balance (not locked in positions)
                    if (normalizedAsset == "USD") {
                        availableFunds = balance
                    }
                }
            }

            // Update percentages
            val assetBalancesWithPercent = assetBalances.mapValues { (_, assetBalance) ->
                assetBalance.copy(
                    percentOfPortfolio = if (totalValueUSD > 0) {
                        (assetBalance.valueInUSD / totalValueUSD) * 100.0
                    } else 0.0
                )
            }

            // Calculate P&L
            val (totalPnL, totalPnLPercent, _) = profitCalculator.calculateTotalPnL()
            val (dayPnL, dayPnLPercent, _) = profitCalculator.calculateDailyPnL()

            val portfolio = Portfolio(
                totalValue = totalValueUSD,
                availableBalance = availableFunds,
                balances = assetBalancesWithPercent,
                totalProfit = totalPnL,
                totalProfitPercent = totalPnLPercent,
                dayProfit = dayPnL,
                dayProfitPercent = dayPnLPercent,
                openPositions = assetBalances.size - 1 // Exclude USD
            )

            // FIRST: Check stop-loss and take-profit for existing positions
            Timber.d("Checking stop-loss for ${stopLossMonitor.getOpenPositions().size} open positions")

            // Get positions before checking to access them after removal
            val positionsBeforeCheck = stopLossMonitor.getOpenPositions().toList()

            val stopLossResults = stopLossMonitor.checkPositions()
            stopLossResults.forEach { result ->
                if (result.success && result.pnl != null) {
                    Timber.i("Stop-loss/Take-profit executed: ${result.positionId}, P&L: ${result.pnl}")

                    // Find position in the snapshot taken before checking
                    val position = positionsBeforeCheck.find { it.id == result.positionId }
                    if (position != null) {
                        // Get current price for exit price
                        val exitPrice = try {
                            val tickerResult = krakenRepository.getTicker(position.pair)
                            tickerResult.getOrNull()?.last ?: position.entryPrice
                        } catch (e: Exception) {
                            position.entryPrice
                        }

                        if (result.pnl >= 0) {
                            notificationManager.notifyTakeProfitHit(
                                pair = position.pair,
                                entryPrice = position.entryPrice,
                                exitPrice = exitPrice,
                                pnl = result.pnl
                            )
                        } else {
                            notificationManager.notifyStopLossHit(
                                pair = position.pair,
                                entryPrice = position.entryPrice,
                                exitPrice = exitPrice,
                                pnl = result.pnl
                            )
                        }
                    }
                } else if (!result.success) {
                    Timber.w("Stop-loss/Take-profit failed: ${result.positionId}, reason: ${result.reason}")
                }
            }

            // THEN: Evaluate each strategy for new signals
            activeStrategies.forEach { strategy ->
                strategy.tradingPairs.forEach { pair ->
                    try {
                        // Get market data
                        val tickerResult = krakenRepository.getTicker(pair)
                        if (tickerResult.isSuccess) {
                            val ticker = tickerResult.getOrNull()!!

                            // Evaluate strategy (with multi-timeframe analysis if enabled)
                            val signal = tradingEngine.evaluateStrategy(strategy, ticker, portfolio)

                            if (signal != null) {
                                Timber.d("Trade signal generated: ${signal.action} ${signal.pair}")

                                // Execute trade
                                val tradeRequestResult = executeTradeUseCase(signal, portfolio)
                                if (tradeRequestResult.isSuccess) {
                                    val tradeRequest = tradeRequestResult.getOrNull()!!
                                    val tradeResult = krakenRepository.placeOrder(tradeRequest)

                                    if (tradeResult.isSuccess) {
                                        val trade = tradeResult.getOrNull()!!
                                        notificationManager.notifyTradeExecuted(trade)
                                        Timber.d("Trade executed successfully: ${trade.orderId}")

                                        // If BUY signal, add position to stop-loss monitor
                                        if (signal.action == TradeAction.BUY) {
                                            val isBuy = (trade.type == com.cryptotrader.domain.model.TradeType.BUY)

                                            // Use volatility-adjusted stop-loss if enabled
                                            val stopLoss = riskManager.calculateStopLossWithVolatility(
                                                pair = trade.pair,
                                                entryPrice = trade.price,
                                                strategy = strategy,
                                                isBuy = isBuy
                                            )

                                            val takeProfit = riskManager.calculateTakeProfit(
                                                entryPrice = trade.price,
                                                takeProfitPercent = strategy.takeProfitPercent,
                                                isBuy = isBuy
                                            )

                                            val position = Position(
                                                id = trade.orderId,
                                                strategyId = strategy.id,
                                                pair = trade.pair,
                                                type = trade.type,
                                                entryPrice = trade.price,
                                                volume = trade.volume,
                                                stopLossPrice = stopLoss,
                                                takeProfitPrice = takeProfit,
                                                entryTimestamp = trade.timestamp,
                                                useTrailingStop = strategy.useTrailingStop,
                                                trailingStopPercent = strategy.trailingStopPercent,
                                                initialStopLoss = stopLoss
                                            )
                                            stopLossMonitor.addPosition(position)
                                            Timber.d("Position added to stop-loss monitor: ${position.id}, SL: $stopLoss, TP: $takeProfit")
                                        }
                                    } else {
                                        Timber.e("Trade execution failed: ${tradeResult.exceptionOrNull()?.message}")
                                    }
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Error processing pair $pair for strategy ${strategy.name}")
                    }
                }
            }

            Timber.d("TradingWorker completed successfully")
            Result.success()
        } catch (e: Exception) {
            Timber.e(e, "TradingWorker failed")
            Result.retry()
        }
    }
}
