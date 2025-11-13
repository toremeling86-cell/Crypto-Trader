package com.cryptotrader.domain.backtesting

import com.cryptotrader.domain.model.*
import com.cryptotrader.domain.trading.RiskManager
import com.cryptotrader.domain.trading.TradingEngine
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backtesting engine for testing strategies against historical data
 *
 * This allows users to validate strategy performance before risking real money
 */
@Singleton
class BacktestEngine @Inject constructor(
    private val tradingEngine: TradingEngine,
    private val riskManager: RiskManager
) {

    /**
     * Run backtest for a strategy using historical price data
     *
     * @param strategy The trading strategy to test
     * @param historicalData List of price bars (OHLC + timestamp)
     * @param startingBalance Initial balance for the backtest
     * @return Backtest results with performance metrics
     */
    suspend fun runBacktest(
        strategy: Strategy,
        historicalData: List<PriceBar>,
        startingBalance: Double = 10000.0
    ): BacktestResult {
        Timber.d("Starting backtest for strategy: ${strategy.name}, data points: ${historicalData.size}")

        if (historicalData.isEmpty()) {
            return BacktestResult(
                strategyId = strategy.id,
                strategyName = strategy.name,
                startingBalance = startingBalance,
                endingBalance = startingBalance,
                totalTrades = 0,
                winningTrades = 0,
                losingTrades = 0,
                winRate = 0.0,
                totalPnL = 0.0,
                totalPnLPercent = 0.0,
                maxDrawdown = 0.0,
                sharpeRatio = 0.0,
                profitFactor = 0.0,
                averageProfit = 0.0,
                averageLoss = 0.0,
                bestTrade = 0.0,
                worstTrade = 0.0,
                trades = emptyList(),
                equityCurve = listOf(startingBalance)
            )
        }

        var balance = startingBalance
        val trades = mutableListOf<BacktestTrade>()
        val equityCurve = mutableListOf(startingBalance)
        val openPositions = mutableMapOf<String, BacktestPosition>() // pair -> position

        historicalData.forEachIndexed { index, priceBar ->
            // Check stop-loss and take-profit for open positions
            val positionsToClose = mutableListOf<String>()
            openPositions.forEach { (pair, position) ->
                val currentPrice = priceBar.close

                // Check stop-loss
                if (position.type == TradeType.BUY && currentPrice <= position.stopLossPrice) {
                    // Stop-loss hit on long position
                    val pnl = (currentPrice - position.entryPrice) * position.volume - position.fee
                    balance += (currentPrice * position.volume) + pnl

                    trades.add(
                        BacktestTrade(
                            timestamp = priceBar.timestamp,
                            pair = pair,
                            type = TradeType.SELL,
                            entryPrice = position.entryPrice,
                            exitPrice = currentPrice,
                            volume = position.volume,
                            pnl = pnl,
                            reason = "Stop-Loss"
                        )
                    )
                    positionsToClose.add(pair)
                    Timber.d("Stop-loss hit: $pair at $currentPrice, P&L: $pnl")
                }

                // Check take-profit
                if (position.type == TradeType.BUY && currentPrice >= position.takeProfitPrice) {
                    // Take-profit hit on long position
                    val pnl = (currentPrice - position.entryPrice) * position.volume - position.fee
                    balance += (currentPrice * position.volume) + pnl

                    trades.add(
                        BacktestTrade(
                            timestamp = priceBar.timestamp,
                            pair = pair,
                            type = TradeType.SELL,
                            entryPrice = position.entryPrice,
                            exitPrice = currentPrice,
                            volume = position.volume,
                            pnl = pnl,
                            reason = "Take-Profit"
                        )
                    )
                    positionsToClose.add(pair)
                    Timber.d("Take-profit hit: $pair at $currentPrice, P&L: $pnl")
                }
            }

            // Close positions that hit stop-loss or take-profit
            positionsToClose.forEach { openPositions.remove(it) }

            // Evaluate strategy for signal
            val change = priceBar.close - priceBar.open
            val changePercent = (change / priceBar.open) * 100.0

            val marketData = MarketTicker(
                pair = strategy.tradingPairs.firstOrNull() ?: "XXBTZUSD",
                ask = priceBar.close,
                bid = priceBar.close,
                last = priceBar.close,
                volume24h = priceBar.volume,
                high24h = priceBar.high,
                low24h = priceBar.low,
                change24h = change,
                changePercent24h = changePercent
            )

            val portfolio = Portfolio(
                totalValue = balance,
                availableBalance = balance,
                balances = emptyMap(),
                totalProfit = balance - startingBalance,
                totalProfitPercent = ((balance - startingBalance) / startingBalance) * 100.0,
                dayProfit = 0.0,
                dayProfitPercent = 0.0,
                openPositions = openPositions.size
            )

            val signal = tradingEngine.evaluateStrategy(strategy, marketData, portfolio)

            if (signal != null) {
                when (signal.action) {
                    TradeAction.BUY -> {
                        // Only buy if we don't have an open position for this pair
                        if (!openPositions.containsKey(signal.pair)) {
                            val positionSize = balance * (strategy.positionSizePercent / 100.0)
                            val volume = positionSize / priceBar.close
                            val fee = positionSize * 0.0026 // Kraken taker fee

                            if (positionSize + fee <= balance) {
                                balance -= (positionSize + fee)

                                val stopLossPrice = riskManager.calculateStopLoss(
                                    entryPrice = priceBar.close,
                                    stopLossPercent = strategy.stopLossPercent,
                                    isBuy = true
                                )

                                val takeProfitPrice = riskManager.calculateTakeProfit(
                                    entryPrice = priceBar.close,
                                    takeProfitPercent = strategy.takeProfitPercent,
                                    isBuy = true
                                )

                                openPositions[signal.pair] = BacktestPosition(
                                    pair = signal.pair,
                                    type = TradeType.BUY,
                                    entryPrice = priceBar.close,
                                    volume = volume,
                                    stopLossPrice = stopLossPrice,
                                    takeProfitPrice = takeProfitPrice,
                                    fee = fee
                                )

                                Timber.d("BUY ${signal.pair} at ${priceBar.close}, vol: $volume, SL: $stopLossPrice, TP: $takeProfitPrice")
                            }
                        }
                    }
                    TradeAction.SELL -> {
                        // Only sell if we have an open position
                        val position = openPositions[signal.pair]
                        if (position != null) {
                            val pnl = (priceBar.close - position.entryPrice) * position.volume - position.fee
                            balance += (priceBar.close * position.volume) + pnl

                            trades.add(
                                BacktestTrade(
                                    timestamp = priceBar.timestamp,
                                    pair = signal.pair,
                                    type = TradeType.SELL,
                                    entryPrice = position.entryPrice,
                                    exitPrice = priceBar.close,
                                    volume = position.volume,
                                    pnl = pnl,
                                    reason = "Strategy Signal"
                                )
                            )

                            openPositions.remove(signal.pair)
                            Timber.d("SELL ${signal.pair} at ${priceBar.close}, P&L: $pnl")
                        }
                    }
                    TradeAction.HOLD -> {
                        // Do nothing
                    }
                }
            }

            // Update equity curve
            val totalEquity = balance + openPositions.values.sumOf {
                (priceBar.close * it.volume) - it.fee
            }
            equityCurve.add(totalEquity)
        }

        // Close any remaining open positions at final price
        val finalPriceBar = historicalData.last()
        openPositions.forEach { (pair, position) ->
            val pnl = (finalPriceBar.close - position.entryPrice) * position.volume - position.fee
            balance += (finalPriceBar.close * position.volume) + pnl

            trades.add(
                BacktestTrade(
                    timestamp = finalPriceBar.timestamp,
                    pair = pair,
                    type = TradeType.SELL,
                    entryPrice = position.entryPrice,
                    exitPrice = finalPriceBar.close,
                    volume = position.volume,
                    pnl = pnl,
                    reason = "Backtest End"
                )
            )
        }

        // Calculate metrics
        val pnls = trades.map { it.pnl }
        val winningTrades = trades.filter { it.pnl > 0 }
        val losingTrades = trades.filter { it.pnl < 0 }

        val winRate = if (trades.isNotEmpty()) {
            (winningTrades.size.toDouble() / trades.size.toDouble()) * 100.0
        } else 0.0

        val averageProfit = if (winningTrades.isNotEmpty()) {
            winningTrades.map { it.pnl }.average()
        } else 0.0

        val averageLoss = if (losingTrades.isNotEmpty()) {
            losingTrades.map { it.pnl }.average()
        } else 0.0

        val grossProfit = winningTrades.sumOf { it.pnl }
        val grossLoss = kotlin.math.abs(losingTrades.sumOf { it.pnl })
        val profitFactor = if (grossLoss > 0) {
            grossProfit / grossLoss
        } else if (grossProfit > 0) {
            Double.POSITIVE_INFINITY
        } else 1.0

        // Sharpe Ratio
        val returns = pnls.map { it / startingBalance }
        val averageReturn = if (returns.isNotEmpty()) returns.average() else 0.0
        val stdDev = if (returns.size > 1) {
            val variance = returns.map { (it - averageReturn) * (it - averageReturn) }.average()
            kotlin.math.sqrt(variance)
        } else 0.0

        val sharpeRatio = if (stdDev > 0) {
            (averageReturn / stdDev) * kotlin.math.sqrt(252.0)
        } else 0.0

        // Max Drawdown
        var maxDrawdown = 0.0
        var peak = equityCurve.firstOrNull() ?: startingBalance
        equityCurve.forEach { equity ->
            if (equity > peak) peak = equity
            val drawdown = peak - equity
            if (drawdown > maxDrawdown) maxDrawdown = drawdown
        }

        val totalPnL = balance - startingBalance
        val totalPnLPercent = (totalPnL / startingBalance) * 100.0

        Timber.i("Backtest complete: ${trades.size} trades, P&L: $totalPnL (${totalPnLPercent}%), Win Rate: $winRate%")

        return BacktestResult(
            strategyId = strategy.id,
            strategyName = strategy.name,
            startingBalance = startingBalance,
            endingBalance = balance,
            totalTrades = trades.size,
            winningTrades = winningTrades.size,
            losingTrades = losingTrades.size,
            winRate = winRate,
            totalPnL = totalPnL,
            totalPnLPercent = totalPnLPercent,
            maxDrawdown = maxDrawdown,
            sharpeRatio = sharpeRatio,
            profitFactor = profitFactor,
            averageProfit = averageProfit,
            averageLoss = averageLoss,
            bestTrade = pnls.maxOrNull() ?: 0.0,
            worstTrade = pnls.minOrNull() ?: 0.0,
            trades = trades,
            equityCurve = equityCurve
        )
    }
}

/**
 * Historical price bar (OHLC)
 */
data class PriceBar(
    val timestamp: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double
)

/**
 * Backtest position (open trade during backtest)
 */
private data class BacktestPosition(
    val pair: String,
    val type: TradeType,
    val entryPrice: Double,
    val volume: Double,
    val stopLossPrice: Double,
    val takeProfitPrice: Double,
    val fee: Double
)

/**
 * Trade executed during backtest
 */
data class BacktestTrade(
    val timestamp: Long,
    val pair: String,
    val type: TradeType,
    val entryPrice: Double,
    val exitPrice: Double,
    val volume: Double,
    val pnl: Double,
    val reason: String // "Stop-Loss", "Take-Profit", "Strategy Signal", "Backtest End"
)

/**
 * Backtest results with full performance metrics
 */
data class BacktestResult(
    val strategyId: String,
    val strategyName: String,
    val startingBalance: Double,
    val endingBalance: Double,
    val totalTrades: Int,
    val winningTrades: Int,
    val losingTrades: Int,
    val winRate: Double,
    val totalPnL: Double,
    val totalPnLPercent: Double,
    val maxDrawdown: Double,
    val sharpeRatio: Double,
    val profitFactor: Double,
    val averageProfit: Double,
    val averageLoss: Double,
    val bestTrade: Double,
    val worstTrade: Double,
    val trades: List<BacktestTrade>,
    val equityCurve: List<Double> // Balance over time
)
