package com.cryptotrader.domain.backtesting

import com.cryptotrader.data.local.dao.OHLCBarDao
import com.cryptotrader.data.local.entities.OHLCBarEntity
import com.cryptotrader.domain.model.*
import com.cryptotrader.domain.trading.RiskManager
import com.cryptotrader.domain.trading.TradingEngine
import com.cryptotrader.domain.validation.DataTierValidator
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Backtesting engine for testing strategies against historical data
 *
 * HEDGE FUND QUALITY BACKTESTING:
 * - Data tier validation (NEVER mix quality levels)
 * - Look-ahead bias prevention
 * - Realistic cost modeling
 * - Proper equity curve calculation
 *
 * This allows users to validate strategy performance before risking real money
 */
@Singleton
class BacktestEngine @Inject constructor(
    private val tradingEngine: TradingEngine,
    private val riskManager: RiskManager,
    private val ohlcBarDao: OHLCBarDao
) {

    /**
     * Run backtest for a strategy using historical price data
     *
     * @param strategy The trading strategy to test
     * @param historicalData List of price bars (OHLC + timestamp)
     * @param startingBalance Initial balance for the backtest
     * @param costModel Trading cost model (fees, slippage, spread)
     * @return Backtest results with performance metrics
     */
    suspend fun runBacktest(
        strategy: Strategy,
        historicalData: List<PriceBar>,
        startingBalance: Double = 10000.0,
        costModel: TradingCostModel = TradingCostModel(),
        ohlcBars: List<OHLCBarEntity>? = null  // Optional: for data tier validation
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
                equityCurve = listOf(startingBalance),
                dataTier = null,
                dataQualityScore = null
            )
        }

        // HEDGE FUND QUALITY: Data tier validation (if OHLC entities provided)
        var dataTier: DataTier? = null
        var dataQualityScore: Double? = null

        if (ohlcBars != null && ohlcBars.isNotEmpty()) {
            try {
                Timber.i("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
                Timber.i("🔒 DATA TIER VALIDATION STARTING")
                Timber.i("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

                // Calculate expected bars
                val expectedBars = historicalData.size.toLong()

                // Validate data tier consistency and quality
                val validation = DataTierValidator.validateBacktestData(ohlcBars, expectedBars)

                dataTier = validation.tier
                dataQualityScore = validation.qualityScore

                Timber.i("✅ Data tier validation PASSED")
                Timber.i("   Tier: ${validation.tier.tierName}")
                Timber.i("   Quality Score: ${String.format("%.2f", validation.qualityScore)}")
                Timber.i("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

            } catch (e: Exception) {
                Timber.e(e, "❌ Data tier validation FAILED")
                Timber.e("   Error: ${e.message}")
                Timber.e("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

                // Return failure result with validation error
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
                    equityCurve = listOf(startingBalance),
                    validationError = e.message,
                    dataTier = null,
                    dataQualityScore = null
                )
            }
        } else {
            Timber.w("⚠️ No OHLC bars provided - skipping data tier validation")
        }

        // HEDGE-FUND QUALITY ACCOUNTING:
        // We track three separate values for accurate P&L calculation:
        // 1. balance: Available cash (starts at startingBalance, reduced when buying, increased when selling)
        // 2. realizedPnL: Cumulative profit/loss from closed positions
        // 3. unrealizedPnL: Current profit/loss from open positions
        // Total equity = balance + unrealizedPnL (balance already includes realized P&L)
        var balance = startingBalance
        var realizedPnL = 0.0  // Track realized P&L separately from balance
        val trades = mutableListOf<BacktestTrade>()
        val equityCurve = mutableListOf(startingBalance)
        val openPositions = mutableMapOf<String, BacktestPosition>() // pair -> position

        // CRITICAL: Clear price history before backtest to prevent contamination from previous runs
        val pair = strategy.tradingPairs.firstOrNull() ?: "XXBTZUSD"
        tradingEngine.clearPriceHistory(pair)
        Timber.i("Cleared price history for $pair before backtest")

        // Log strategy details at start
        Timber.i("=== BACKTEST STRATEGY DETAILS ===")
        Timber.i("Strategy: ${strategy.name}")
        Timber.i("Entry Conditions: ${strategy.entryConditions.joinToString(" AND ")}")
        Timber.i("Exit Conditions: ${strategy.exitConditions.joinToString(" OR ")}")
        Timber.i("Position Size: ${strategy.positionSizePercent}%")
        Timber.i("Stop Loss: ${strategy.stopLossPercent}%")
        Timber.i("Take Profit: ${strategy.takeProfitPercent}%")
        Timber.i("BACKTEST MODE: Look-ahead bias prevention ENABLED")
        Timber.i("=================================")

        historicalData.forEachIndexed { index, priceBar ->
            // CRITICAL FIX FOR LOOK-AHEAD BIAS:
            // Before evaluating the current candle, we add all PREVIOUS candles to history.
            // This ensures the strategy only sees completed candles, never the current one.
            if (index > 0) {
                val previousBar = historicalData[index - 1]
                val previousMarketData = MarketTicker(
                    pair = pair,
                    ask = previousBar.close,
                    bid = previousBar.close,
                    last = previousBar.close,
                    volume24h = previousBar.volume,
                    high24h = previousBar.high,
                    low24h = previousBar.low,
                    change24h = previousBar.close - previousBar.open,
                    changePercent24h = ((previousBar.close - previousBar.open) / previousBar.open) * 100.0
                )
                tradingEngine.updatePriceHistory(pair, previousMarketData)

                if (index % 50 == 0) {
                    Timber.d("Added previous candle to history (index ${index-1}), now evaluating current candle (index $index)")
                }
            }

            // Log every 10th candle to track progress
            if (index % 10 == 0) {
                Timber.d("Backtest iteration $index/${historicalData.size}: Price ${priceBar.close}, Balance: $balance")
            }

            // Check stop-loss and take-profit for open positions
            val positionsToClose = mutableListOf<String>()
            openPositions.forEach { (pair, position) ->
                val currentPrice = priceBar.close

                // Check stop-loss
                if (position.type == TradeType.BUY && currentPrice <= position.stopLossPrice) {
                    // FIX Bug 1.2 + 1.3: Correct P&L and balance calculation
                    // Stop-loss hit on long position - calculate exit costs
                    val exitValue = currentPrice * position.volume
                    val exitExecutionType = OrderExecutionType.TAKER // Stop-loss is usually market order
                    val exitCost = costModel.calculateTradeCost(
                        orderType = exitExecutionType,
                        orderValue = exitValue,
                        isLargeOrder = exitValue > balance * 0.1
                    )

                    // Apply slippage to exit price (negative for selling)
                    val exitSlippagePercent = exitCost.slippagePercent / 100.0
                    val exitPrice = currentPrice * (1 - exitSlippagePercent) // Get less when selling

                    // HEDGE-FUND QUALITY P&L CALCULATION:
                    // proceeds: What we receive from selling the position
                    // exitFees: Costs to exit the position
                    // netProceeds: What we actually get back (proceeds - exit fees)
                    // costBasis: What we originally paid (entry price * volume + entry fees)
                    // pnl: Actual profit or loss (netProceeds - costBasis)
                    val proceeds = exitPrice * position.volume
                    val netProceeds = proceeds - exitCost.totalCost
                    val costBasis = position.entryPrice * position.volume + position.totalCost
                    val pnl = netProceeds - costBasis

                    // Add all proceeds back to balance
                    balance += netProceeds
                    realizedPnL += pnl

                    Timber.d("STOP-LOSS EXIT: $pair")
                    Timber.d("  Entry: ${position.entryPrice} x ${position.volume} = ${position.entryPrice * position.volume}")
                    Timber.d("  Entry Costs: ${position.totalCost}")
                    Timber.d("  Cost Basis: $costBasis")
                    Timber.d("  Exit: $exitPrice x ${position.volume} = $proceeds")
                    Timber.d("  Exit Costs: ${exitCost.totalCost}")
                    Timber.d("  Net Proceeds: $netProceeds")
                    Timber.d("  P&L: $pnl")
                    Timber.d("  New Balance: $balance, Realized P&L: $realizedPnL")

                    trades.add(
                        BacktestTrade(
                            timestamp = priceBar.timestamp,
                            pair = pair,
                            type = TradeType.SELL,
                            entryPrice = position.entryPrice,
                            exitPrice = exitPrice,
                            volume = position.volume,
                            pnl = pnl,
                            entryCost = position.totalCost,
                            exitCost = exitCost.totalCost,
                            reason = "Stop-Loss"
                        )
                    )
                    positionsToClose.add(pair)
                }

                // Check take-profit
                if (position.type == TradeType.BUY && currentPrice >= position.takeProfitPrice) {
                    // FIX Bug 1.2 + 1.3: Correct P&L and balance calculation
                    // Take-profit hit on long position - calculate exit costs
                    val exitValue = currentPrice * position.volume
                    val exitExecutionType = if (strategy.postOnly) OrderExecutionType.MAKER else OrderExecutionType.TAKER
                    val exitCost = costModel.calculateTradeCost(
                        orderType = exitExecutionType,
                        orderValue = exitValue,
                        isLargeOrder = exitValue > balance * 0.1
                    )

                    // Apply slippage to exit price (negative for selling)
                    val exitSlippagePercent = exitCost.slippagePercent / 100.0
                    val exitPrice = currentPrice * (1 - exitSlippagePercent) // Get less when selling

                    // HEDGE-FUND QUALITY P&L CALCULATION
                    val proceeds = exitPrice * position.volume
                    val netProceeds = proceeds - exitCost.totalCost
                    val costBasis = position.entryPrice * position.volume + position.totalCost
                    val pnl = netProceeds - costBasis

                    balance += netProceeds
                    realizedPnL += pnl

                    Timber.d("TAKE-PROFIT EXIT: $pair")
                    Timber.d("  Entry: ${position.entryPrice} x ${position.volume} = ${position.entryPrice * position.volume}")
                    Timber.d("  Entry Costs: ${position.totalCost}")
                    Timber.d("  Cost Basis: $costBasis")
                    Timber.d("  Exit: $exitPrice x ${position.volume} = $proceeds")
                    Timber.d("  Exit Costs: ${exitCost.totalCost}")
                    Timber.d("  Net Proceeds: $netProceeds")
                    Timber.d("  P&L: $pnl")
                    Timber.d("  New Balance: $balance, Realized P&L: $realizedPnL")

                    trades.add(
                        BacktestTrade(
                            timestamp = priceBar.timestamp,
                            pair = pair,
                            type = TradeType.SELL,
                            entryPrice = position.entryPrice,
                            exitPrice = exitPrice,
                            volume = position.volume,
                            pnl = pnl,
                            entryCost = position.totalCost,
                            exitCost = exitCost.totalCost,
                            reason = "Take-Profit"
                        )
                    )
                    positionsToClose.add(pair)
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

            // CRITICAL: Pass isBacktesting=true to prevent look-ahead bias
            val signal = tradingEngine.evaluateStrategy(
                strategy = strategy,
                marketData = marketData,
                portfolio = portfolio,
                isBacktesting = true  // Ensures only completed candles are used
            )

            // Log signal evaluation results (especially for early iterations)
            if (index < 35 || signal != null) {
                Timber.d("Iteration $index: Signal = ${signal?.action ?: "NULL"}, Price = ${priceBar.close}, Balance = $balance")
            }

            if (signal != null) {
                Timber.i("🎯 SIGNAL GENERATED at iteration $index: ${signal.action} ${signal.pair} @ ${priceBar.close}, Reason: ${signal.reason}")
                when (signal.action) {
                    TradeAction.BUY -> {
                        // Only buy if we don't have an open position for this pair
                        if (!openPositions.containsKey(signal.pair)) {
                            // FIX Bug 1.4: Correct slippage handling on entry
                            // Target position size in dollars
                            val targetPositionSize = balance * (strategy.positionSizePercent / 100.0)

                            // Calculate trading costs using cost model (estimated on target size)
                            val executionType = if (strategy.postOnly) OrderExecutionType.MAKER else OrderExecutionType.TAKER
                            val tradeCost = costModel.calculateTradeCost(
                                orderType = executionType,
                                orderValue = targetPositionSize,
                                isLargeOrder = targetPositionSize > balance * 0.1 // >10% of balance = large order
                            )

                            // HEDGE-FUND QUALITY ENTRY CALCULATION:
                            // 1. Apply slippage to get the actual entry price we'll pay
                            // 2. Calculate volume at the slipped price (not the close price)
                            // 3. Calculate actual cost including fees
                            // 4. Deduct actual cost from balance (not just positionSize)
                            val slippagePercent = tradeCost.slippagePercent / 100.0
                            val entryPrice = priceBar.close * (1 + slippagePercent) // Pay more when buying
                            val volume = targetPositionSize / entryPrice  // Volume at slipped price
                            val actualPositionValue = entryPrice * volume
                            val totalEntryCost = actualPositionValue + tradeCost.totalCost

                            if (totalEntryCost <= balance) {
                                // Deduct actual cost from balance
                                balance -= totalEntryCost

                                val stopLossPrice = riskManager.calculateStopLoss(
                                    entryPrice = entryPrice,
                                    stopLossPercent = strategy.stopLossPercent,
                                    isBuy = true
                                )

                                val takeProfitPrice = riskManager.calculateTakeProfit(
                                    entryPrice = entryPrice,
                                    takeProfitPercent = strategy.takeProfitPercent,
                                    isBuy = true
                                )

                                openPositions[signal.pair] = BacktestPosition(
                                    pair = signal.pair,
                                    type = TradeType.BUY,
                                    entryPrice = entryPrice,
                                    volume = volume,
                                    stopLossPrice = stopLossPrice,
                                    takeProfitPrice = takeProfitPrice,
                                    totalCost = tradeCost.totalCost
                                )

                                Timber.d("BUY ENTRY: ${signal.pair}")
                                Timber.d("  Target Size: $targetPositionSize")
                                Timber.d("  Entry Price (with slippage): $entryPrice")
                                Timber.d("  Volume: $volume")
                                Timber.d("  Position Value: $actualPositionValue")
                                Timber.d("  Entry Costs: ${tradeCost.totalCost}")
                                Timber.d("  Total Cost: $totalEntryCost")
                                Timber.d("  New Balance: $balance")
                                Timber.d("  Stop Loss: $stopLossPrice, Take Profit: $takeProfitPrice")
                            } else {
                                Timber.w("Insufficient balance for BUY: need $totalEntryCost, have $balance")
                            }
                        }
                    }
                    TradeAction.SELL -> {
                        // FIX Bug 1.2 + 1.3: Correct P&L and balance calculation
                        // Only sell if we have an open position
                        val position = openPositions[signal.pair]
                        if (position != null) {
                            val exitValue = priceBar.close * position.volume
                            val exitExecutionType = if (strategy.postOnly) OrderExecutionType.MAKER else OrderExecutionType.TAKER
                            val exitCost = costModel.calculateTradeCost(
                                orderType = exitExecutionType,
                                orderValue = exitValue,
                                isLargeOrder = exitValue > balance * 0.1
                            )

                            // Apply slippage to exit price
                            val exitSlippagePercent = exitCost.slippagePercent / 100.0
                            val exitPrice = priceBar.close * (1 - exitSlippagePercent)

                            // HEDGE-FUND QUALITY P&L CALCULATION
                            val proceeds = exitPrice * position.volume
                            val netProceeds = proceeds - exitCost.totalCost
                            val costBasis = position.entryPrice * position.volume + position.totalCost
                            val pnl = netProceeds - costBasis

                            balance += netProceeds
                            realizedPnL += pnl

                            Timber.d("STRATEGY SELL EXIT: ${signal.pair}")
                            Timber.d("  Entry: ${position.entryPrice} x ${position.volume} = ${position.entryPrice * position.volume}")
                            Timber.d("  Entry Costs: ${position.totalCost}")
                            Timber.d("  Cost Basis: $costBasis")
                            Timber.d("  Exit: $exitPrice x ${position.volume} = $proceeds")
                            Timber.d("  Exit Costs: ${exitCost.totalCost}")
                            Timber.d("  Net Proceeds: $netProceeds")
                            Timber.d("  P&L: $pnl")
                            Timber.d("  New Balance: $balance, Realized P&L: $realizedPnL")

                            trades.add(
                                BacktestTrade(
                                    timestamp = priceBar.timestamp,
                                    pair = signal.pair,
                                    type = TradeType.SELL,
                                    entryPrice = position.entryPrice,
                                    exitPrice = exitPrice,
                                    volume = position.volume,
                                    pnl = pnl,
                                    entryCost = position.totalCost,
                                    exitCost = exitCost.totalCost,
                                    reason = "Strategy Signal"
                                )
                            )

                            openPositions.remove(signal.pair)
                        }
                    }
                    TradeAction.HOLD -> {
                        // Do nothing
                    }
                }
            }

            // FIX Bug 1.1: Correct equity curve calculation using unrealized P&L
            // HEDGE-FUND QUALITY EQUITY CALCULATION:
            // Total equity = balance + unrealizedPnL
            // Where unrealizedPnL = sum of all open positions' (currentPrice - entryPrice) * volume - fees already paid
            // Note: balance was already reduced when positions were opened, so we only add unrealized gains/losses
            val unrealizedPnL = openPositions.values.sumOf { position ->
                val currentValue = priceBar.close * position.volume
                val costBasis = position.entryPrice * position.volume + position.totalCost
                currentValue - costBasis  // Unrealized P&L = current value - what we paid
            }
            val totalEquity = balance + unrealizedPnL
            equityCurve.add(totalEquity)

            // Log equity details every 50 iterations or when there are open positions
            if (index % 50 == 0 || openPositions.isNotEmpty()) {
                Timber.d("EQUITY UPDATE (iteration $index):")
                Timber.d("  Balance (cash): $balance")
                Timber.d("  Unrealized P&L: $unrealizedPnL")
                Timber.d("  Total Equity: $totalEquity")
                Timber.d("  Open Positions: ${openPositions.size}")
            }
        }

        // FIX Bug 1.2 + 1.3: Close any remaining open positions at final price with correct P&L
        val finalPriceBar = historicalData.last()
        openPositions.forEach { (pair, position) ->
            val exitValue = finalPriceBar.close * position.volume
            val exitCost = costModel.calculateTradeCost(
                orderType = OrderExecutionType.TAKER, // Force close at market
                orderValue = exitValue,
                isLargeOrder = exitValue > balance * 0.1
            )

            val exitSlippagePercent = exitCost.slippagePercent / 100.0
            val exitPrice = finalPriceBar.close * (1 - exitSlippagePercent)

            // HEDGE-FUND QUALITY P&L CALCULATION
            val proceeds = exitPrice * position.volume
            val netProceeds = proceeds - exitCost.totalCost
            val costBasis = position.entryPrice * position.volume + position.totalCost
            val pnl = netProceeds - costBasis

            balance += netProceeds
            realizedPnL += pnl

            Timber.d("BACKTEST END - FORCE CLOSE: $pair")
            Timber.d("  Entry: ${position.entryPrice} x ${position.volume} = ${position.entryPrice * position.volume}")
            Timber.d("  Entry Costs: ${position.totalCost}")
            Timber.d("  Cost Basis: $costBasis")
            Timber.d("  Exit: $exitPrice x ${position.volume} = $proceeds")
            Timber.d("  Exit Costs: ${exitCost.totalCost}")
            Timber.d("  Net Proceeds: $netProceeds")
            Timber.d("  P&L: $pnl")

            trades.add(
                BacktestTrade(
                    timestamp = finalPriceBar.timestamp,
                    pair = pair,
                    type = TradeType.SELL,
                    entryPrice = position.entryPrice,
                    exitPrice = exitPrice,
                    volume = position.volume,
                    pnl = pnl,
                    entryCost = position.totalCost,
                    exitCost = exitCost.totalCost,
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

        // FIX Bug 1.5: HEDGE-FUND QUALITY SHARPE RATIO CALCULATION
        // Use equity curve returns (not per-trade returns) for proper risk-adjusted performance
        // Sharpe ratio measures risk-adjusted returns: (avg return - risk-free rate) / std deviation
        // We annualize using the appropriate factor based on data frequency
        val sharpeRatio = if (equityCurve.size > 2) {
            // Calculate period-to-period returns from equity curve
            val equityReturns = equityCurve.zipWithNext { current, next ->
                if (current > 0) (next - current) / current else 0.0
            }

            if (equityReturns.isNotEmpty()) {
                val avgReturn = equityReturns.average()
                val variance = equityReturns.map { (it - avgReturn).let { diff -> diff * diff } }.average()
                val stdDev = kotlin.math.sqrt(variance)

                // P1-3: TIMEFRAME-AWARE SHARPE RATIO ANNUALIZATION
                // Determine annualization factor based on actual data frequency
                // Crypto markets trade 24/7, not just 252 days like traditional markets
                val timeframe = ohlcBars?.firstOrNull()?.timeframe ?: detectTimeframeFromBars(historicalData)
                val periodsPerYear = calculatePeriodsPerYear(timeframe)
                val annualizationFactor = kotlin.math.sqrt(periodsPerYear)

                Timber.d("Sharpe ratio calculation: timeframe=$timeframe, periodsPerYear=$periodsPerYear")

                if (stdDev > 0) {
                    // Sharpe ratio = (average return / std dev) * sqrt(periods per year)
                    // Assuming risk-free rate ≈ 0 for simplicity (can be adjusted)
                    (avgReturn / stdDev) * annualizationFactor
                } else {
                    0.0
                }
            } else {
                0.0
            }
        } else {
            0.0
        }

        // FIX Bug 1.6: HEDGE-FUND QUALITY MAX DRAWDOWN AS PERCENTAGE
        // Max drawdown should be expressed as percentage from peak for proper risk assessment
        var maxDrawdownPercent = 0.0
        var peak = equityCurve.firstOrNull() ?: startingBalance
        equityCurve.forEach { equity ->
            if (equity > peak) peak = equity
            val drawdownPercent = if (peak > 0) {
                ((peak - equity) / peak) * 100.0
            } else {
                0.0
            }
            if (drawdownPercent > maxDrawdownPercent) {
                maxDrawdownPercent = drawdownPercent
            }
        }

        Timber.i("RISK METRICS:")
        Timber.i("  Sharpe Ratio (annualized): $sharpeRatio")
        Timber.i("  Max Drawdown: ${maxDrawdownPercent}%")

        val totalPnL = balance - startingBalance
        val totalPnLPercent = (totalPnL / startingBalance) * 100.0

        // Calculate total costs from all trades
        val totalEntryCosts = trades.sumOf { it.entryCost }
        val totalExitCosts = trades.sumOf { it.exitCost }
        val totalCosts = totalEntryCosts + totalExitCosts

        Timber.i("=== BACKTEST COMPLETE ===")
        Timber.i("Trades: ${trades.size} (${winningTrades.size} wins, ${losingTrades.size} losses)")
        Timber.i("Win Rate: $winRate%")
        Timber.i("Total P&L: $totalPnL (${totalPnLPercent}%)")
        Timber.i("Profit Factor: $profitFactor")
        Timber.i("Sharpe Ratio: $sharpeRatio")
        Timber.i("Max Drawdown: ${maxDrawdownPercent}%")
        Timber.i("Total Costs: $totalCosts")
        Timber.i("Final Balance: $balance (started with $startingBalance)")
        if (dataTier != null) {
            Timber.i("Data Tier: ${dataTier.tierName} (Quality: ${String.format("%.2f", dataQualityScore ?: 0.0)})")
        }
        Timber.i("========================")

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
            maxDrawdown = maxDrawdownPercent,  // NOW RETURNS PERCENTAGE, NOT DOLLAR AMOUNT
            sharpeRatio = sharpeRatio,         // NOW PROPERLY CALCULATED FROM EQUITY CURVE
            profitFactor = profitFactor,
            averageProfit = averageProfit,
            averageLoss = averageLoss,
            bestTrade = pnls.maxOrNull() ?: 0.0,
            worstTrade = pnls.minOrNull() ?: 0.0,
            trades = trades,
            equityCurve = equityCurve,
            totalFees = totalCosts, // Total of all costs (fees + slippage + spread combined)
            totalSlippage = 0.0, // Could break down further if needed
            totalSpreadCost = 0.0, // Could break down further if needed
            dataTier = dataTier, // Data quality tier used
            dataQualityScore = dataQualityScore // Measured quality score
        )
    }

    /**
     * P1-3: Calculate periods per year based on timeframe
     * Crypto markets trade 24/7, unlike traditional 252-day markets
     */
    private fun calculatePeriodsPerYear(timeframe: String): Double {
        return when (timeframe.lowercase()) {
            "1m" -> 365.25 * 24 * 60.0      // Minutes per year (24/7)
            "5m" -> 365.25 * 24 * 12.0      // 5-minute periods per year
            "15m" -> 365.25 * 24 * 4.0      // 15-minute periods
            "30m" -> 365.25 * 24 * 2.0      // 30-minute periods
            "1h" -> 365.25 * 24.0           // Hours per year (24/7)
            "4h" -> 365.25 * 6.0            // 4-hour periods per year
            "1d" -> 365.25                  // Days per year (crypto trades every day)
            "1w" -> 52.0                    // Weeks per year
            else -> {
                Timber.w("Unknown timeframe: $timeframe, defaulting to daily (365.25)")
                365.25
            }
        }
    }

    /**
     * P1-3: Detect timeframe from bar intervals
     * Analyzes time differences between bars to infer timeframe
     */
    private fun detectTimeframeFromBars(bars: List<PriceBar>): String {
        if (bars.size < 2) {
            Timber.w("Not enough bars to detect timeframe, defaulting to 1d")
            return "1d"
        }

        // Calculate median time difference between bars
        val timeDiffs = bars.zipWithNext { current, next ->
            next.timestamp - current.timestamp
        }

        val medianDiff = timeDiffs.sorted()[timeDiffs.size / 2]
        val minutes = medianDiff / (1000 * 60)

        // Map time difference to timeframe
        val detectedTimeframe = when {
            minutes <= 1 -> "1m"
            minutes <= 5 -> "5m"
            minutes <= 15 -> "15m"
            minutes <= 30 -> "30m"
            minutes <= 60 -> "1h"
            minutes <= 240 -> "4h"
            minutes <= 1440 -> "1d"
            else -> "1w"
        }

        Timber.i("Detected timeframe: $detectedTimeframe (median interval: $minutes minutes)")
        return detectedTimeframe
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
    val totalCost: Double // Total entry cost (fees + slippage + spread)
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
    val entryCost: Double = 0.0, // Total entry costs (fees + slippage + spread)
    val exitCost: Double = 0.0,  // Total exit costs (fees + slippage + spread)
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
    val equityCurve: List<Double>, // Balance over time
    val totalFees: Double = 0.0, // Total fees paid
    val totalSlippage: Double = 0.0, // Total slippage cost
    val totalSpreadCost: Double = 0.0, // Total bid-ask spread cost
    val validationError: String? = null, // Data validation error (if any)
    val dataTier: DataTier? = null, // Data quality tier used (PREMIUM/PROFESSIONAL/STANDARD/BASIC)
    val dataQualityScore: Double? = null // Measured data quality score (0.0 - 1.0)
)
