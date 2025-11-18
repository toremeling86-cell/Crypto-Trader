package com.cryptotrader.presentation.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cryptotrader.data.repository.CryptoReportRepository
// TEMPORARILY DISABLED - Learning section
// import com.cryptotrader.data.repository.MetaAnalysisRepository
import com.cryptotrader.data.repository.StrategyRepository
// TEMPORARILY DISABLED - Learning section
// import com.cryptotrader.domain.advisor.MetaAnalysisAgent
import com.cryptotrader.domain.ai.ClaudeChatService
import com.cryptotrader.domain.model.ChatMessage
import com.cryptotrader.domain.model.MetaAnalysis
import com.cryptotrader.domain.model.Strategy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val claudeChatService: ClaudeChatService,
    private val strategyRepository: StrategyRepository,
    private val cryptoReportRepository: CryptoReportRepository,
    // TEMPORARILY DISABLED - Learning section
    // private val metaAnalysisRepository: MetaAnalysisRepository,
    // private val metaAnalysisAgent: MetaAnalysisAgent,
    private val backtestEngine: com.cryptotrader.domain.backtesting.BacktestEngine,
    private val marketDataRepository: com.cryptotrader.data.repository.MarketDataRepository,
    private val savedStateHandle: androidx.lifecycle.SavedStateHandle
) : ViewModel() {

    companion object {
        private const val KEY_MESSAGES = "chat_messages"
        private const val KEY_HAS_WELCOMED = "has_welcomed"
    }

    private val _uiState = MutableStateFlow(ChatState())
    val uiState: StateFlow<ChatState> = _uiState.asStateFlow()

    init {
        // Restore saved messages if they exist
        val savedMessages = savedStateHandle.get<List<ChatMessage>>(KEY_MESSAGES)
        val hasWelcomed = savedStateHandle.get<Boolean>(KEY_HAS_WELCOMED) ?: false

        if (savedMessages != null && savedMessages.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(messages = savedMessages)
        } else if (!hasWelcomed) {
            addWelcomeMessage()
            savedStateHandle[KEY_HAS_WELCOMED] = true
        }

        // Start file monitoring for expert reports
        viewModelScope.launch {
            cryptoReportRepository.startFileMonitoring()
        }

        // Observe unanalyzed reports count for badge
        viewModelScope.launch {
            cryptoReportRepository.getUnanalyzedReportCount().collect { count ->
                _uiState.value = _uiState.value.copy(unanalyzedReportCount = count)
            }
        }
    }

    private fun addWelcomeMessage() {
        // Add welcome message
        val welcomeMessage = ChatMessage(
            content = """
                Hei! Jeg er din AI trading assistent.

                Jeg kan hjelpe deg med:
                • Lage trading strategier
                • Analysere markedet
                • Forklare strategier
                • Gi råd om risikostyring

                Hva kan jeg hjelpe deg med?
            """.trimIndent(),
            isFromUser = false
        )
        _uiState.value = _uiState.value.copy(messages = listOf(welcomeMessage))
    }

    /**
     * Send a message to Claude
     */
    fun sendMessage(message: String) {
        if (message.isBlank()) return

        viewModelScope.launch {
            try {
                // Add user message to UI
                val userMessage = ChatMessage(content = message, isFromUser = true)
                addMessage(userMessage)

                // Show typing indicator
                val typingMessage = ChatMessage(
                    content = "Tenker...",
                    isFromUser = false,
                    isTyping = true
                )
                addMessage(typingMessage)

                // Send to Claude
                val result = claudeChatService.sendMessage(
                    userMessage = message,
                    includePortfolioContext = true
                )

                // Remove typing indicator
                removeTypingIndicator()

                if (result.isSuccess) {
                    val response = result.getOrNull()!!
                    addMessage(response)

                    Timber.i("Claude response received: ${response.content.take(100)}")
                } else {
                    val errorMessage = ChatMessage(
                        content = "Beklager, jeg fikk ikke svar fra AI: ${result.exceptionOrNull()?.message}",
                        isFromUser = false
                    )
                    addMessage(errorMessage)
                }

            } catch (e: Exception) {
                Timber.e(e, "Error sending message")
                removeTypingIndicator()
                val errorMessage = ChatMessage(
                    content = "En feil oppstod: ${e.message}",
                    isFromUser = false
                )
                addMessage(errorMessage)
            }
        }
    }

    /**
     * Test and save strategy suggested by Claude (with automatic backtesting and AI feedback loop)
     */
    fun implementStrategy(strategy: Strategy) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isImplementingStrategy = true)

                // Add message about starting backtest
                val backtestStartMessage = ChatMessage(
                    content = """
                        🧪 Starter backtest av strategien "${strategy.name}"...

                        Henter EKTE historisk data fra Kraken...
                    """.trimIndent(),
                    isFromUser = false
                )
                addMessage(backtestStartMessage)

                // Fetch REAL historical data from Kraken
                val pair = strategy.tradingPairs.firstOrNull() ?: "XXBTZUSD"
                val ohlcResult = marketDataRepository.getHistoricalOHLC(
                    pair = pair,
                    intervalMinutes = 60, // 1 hour candles
                    numCandles = 100
                )

                if (ohlcResult.isFailure) {
                    throw Exception("Kunne ikke hente historisk data: ${ohlcResult.exceptionOrNull()?.message}")
                }

                val ohlcCandles = ohlcResult.getOrNull()!!
                val historicalData = marketDataRepository.convertToPriceBars(ohlcCandles)

                Timber.i("Fetched ${historicalData.size} REAL candles from Kraken for backtest")

                // Create cost model with realistic fees
                val costModel = com.cryptotrader.domain.backtesting.TradingCostModel(
                    makerFee = 0.0016,  // 0.16%
                    takerFee = 0.0026,  // 0.26%
                    slippagePercent = 0.05,
                    spreadPercent = 0.02,
                    useRealisticSlippage = true
                )

                // Run backtest (temporarily set strategy as active for testing)
                val testStrategy = strategy.copy(isActive = true)
                val backtestResult = backtestEngine.runBacktest(
                    strategy = testStrategy,
                    historicalData = historicalData,
                    startingBalance = 10000.0,
                    costModel = costModel
                )

                Timber.i("Backtest complete: P&L ${backtestResult.totalPnL}, Win Rate: ${backtestResult.winRate}%")

                // Calculate detailed cost breakdown
                val avgCostPerTrade = if (backtestResult.totalTrades > 0) {
                    backtestResult.totalFees / backtestResult.totalTrades
                } else 0.0
                val costAsPercentOfPnL = if (backtestResult.totalPnL != 0.0) {
                    (backtestResult.totalFees / kotlin.math.abs(backtestResult.totalPnL)) * 100.0
                } else 0.0

                // Estimate breakdown (based on TradingCostModel defaults)
                val estimatedFees = backtestResult.totalFees * 0.31  // ~31% is fees (0.26% of total cost)
                val estimatedSlippage = backtestResult.totalFees * 0.59  // ~59% is slippage (varies by position size)
                val estimatedSpread = backtestResult.totalFees * 0.10  // ~10% is spread (0.01%)

                // Format backtest results for Claude
                val backtestSummary = """
                    📊 BACKTEST RESULTATER for "${strategy.name}":
                    🎯 Testet på ${historicalData.size} EKTE 1-times candler fra Kraken ($pair)

                    💰 Total P&L: ${if (backtestResult.totalPnL >= 0) "+" else ""}${"%.2f".format(backtestResult.totalPnL)} (${if (backtestResult.totalPnLPercent >= 0) "+" else ""}${"%.2f".format(backtestResult.totalPnLPercent)}%)
                    📈 Trades: ${backtestResult.totalTrades} (${backtestResult.winningTrades} wins, ${backtestResult.losingTrades} losses)
                    🎯 Win Rate: ${"%.1f".format(backtestResult.winRate)}%
                    📊 Profit Factor: ${"%.2f".format(backtestResult.profitFactor)}
                    📈 Sharpe Ratio: ${"%.2f".format(backtestResult.sharpeRatio)}
                    📉 Max Drawdown: ${"%.2f".format(backtestResult.maxDrawdown)}%

                    💵 TRADING COSTS (realistisk Kraken-modell):
                    • Total Costs: $${"%.2f".format(backtestResult.totalFees)}
                    • Avg per trade: $${"%.2f".format(avgCostPerTrade)}
                    • Breakdown (est.):
                      - Exchange fees (0.26%): $${"%.2f".format(estimatedFees)}
                      - Slippage (0.05-0.15%): $${"%.2f".format(estimatedSlippage)}
                      - Bid-ask spread (0.01%): $${"%.2f".format(estimatedSpread)}

                    🔍 Best Trade: $${"%.2f".format(backtestResult.bestTrade)}
                    ⚠️ Worst Trade: $${"%.2f".format(backtestResult.worstTrade)}

                    💡 TRANSPARENS:
                    Costs representerer ${String.format("%.0f", costAsPercentOfPnL)}% av absolutt P&L.
                    Strategien brukte ${strategy.positionSizePercent}% posisjonsstørrelse, som påvirker slippage.

                    Dette er EKTE resultater basert på historisk markedsdata fra Kraken.

                    Basert på disse resultatene, anbefaler du fortsatt denne strategien?
                    Eller bør noen parametere justeres for bedre ytelse?
                """.trimIndent()

                // Show backtest results to user
                val backtestMessage = ChatMessage(
                    content = backtestSummary,
                    isFromUser = false
                )
                addMessage(backtestMessage)

                // Send backtest results back to Claude for evaluation
                Timber.i("Sending backtest results to Claude for evaluation...")
                val evaluationResult = claudeChatService.sendMessage(
                    userMessage = backtestSummary,
                    includePortfolioContext = false
                )

                if (evaluationResult.isSuccess) {
                    val claudeEvaluation = evaluationResult.getOrNull()!!
                    addMessage(claudeEvaluation)

                    Timber.i("Claude evaluation received: ${claudeEvaluation.content.take(100)}")

                    // Check if Claude suggests improvements or approves
                    val shouldSave = backtestResult.totalPnLPercent > 0 ||
                                    backtestResult.winRate > 50.0

                    if (shouldSave) {
                        // Save strategy to database with backtest results
                        strategyRepository.insertStrategy(strategy)

                        val successMessage = ChatMessage(
                            content = """
                                ✅ Strategien "${strategy.name}" er lagret med backtest-resultater!

                                Gå til **Strategies**-fanen for å se detaljer og godkjenne strategien.
                                Den vil ikke handle automatisk før du godkjenner den.
                            """.trimIndent(),
                            isFromUser = false
                        )
                        addMessage(successMessage)

                        _uiState.value = _uiState.value.copy(
                            isImplementingStrategy = false,
                            successMessage = "Strategi testet og lagret!"
                        )
                    } else {
                        _uiState.value = _uiState.value.copy(
                            isImplementingStrategy = false
                        )
                    }
                } else {
                    // Even if Claude evaluation fails, show results to user
                    _uiState.value = _uiState.value.copy(
                        isImplementingStrategy = false
                    )
                }

            } catch (e: Exception) {
                Timber.e(e, "Error testing/saving strategy")
                val errorMessage = ChatMessage(
                    content = "❌ Feil under backtesting: ${e.message}",
                    isFromUser = false
                )
                addMessage(errorMessage)

                _uiState.value = _uiState.value.copy(
                    isImplementingStrategy = false,
                    errorMessage = "Kunne ikke teste strategi: ${e.message}"
                )
            }
        }
    }

    /**
     * Clear chat history
     */
    fun clearChat() {
        claudeChatService.clearHistory()
        _uiState.value = ChatState()
        addWelcomeMessage() // Re-add welcome message
    }

    /**
     * Clear error/success messages
     */
    fun clearMessages() {
        _uiState.value = _uiState.value.copy(
            errorMessage = null,
            successMessage = null
        )
    }

    /**
     * Set analysis timeframe for meta-analysis (Phase 3B)
     */
    fun setTimeframe(timeframe: com.cryptotrader.domain.model.AnalysisTimeframe) {
        _uiState.value = _uiState.value.copy(selectedTimeframe = timeframe)
        Timber.d("Analysis timeframe changed to: ${timeframe.displayName}")
    }

    // TEMPORARILY DISABLED - Learning section
    /*
    /**
     * Trigger meta-analysis with rolling window approach (Phase 3B)
     */
    fun triggerMetaAnalysis() {
        viewModelScope.launch {
            try {
                val timeframe = _uiState.value.selectedTimeframe

                _uiState.value = _uiState.value.copy(
                    isAnalyzing = true,
                    analysisProgress = "Henter rapporter fra ${timeframe.displayName} tidsramme..."
                )

                // Phase 3B: Get ALL reports for rolling window filtering
                val allReports = cryptoReportRepository.getAllReports().first()
                if (allReports.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isAnalyzing = false,
                        errorMessage = "Ingen rapporter funnet"
                    )
                    return@launch
                }

                Timber.i("Starting meta-analysis with ${timeframe.displayName} timeframe from ${allReports.size} total reports")
                _uiState.value = _uiState.value.copy(
                    analysisProgress = "Analyserer rapporter med Opus 4.1 (${timeframe.displayName})..."
                )

                // Perform meta-analysis with temporal weighting and rolling window
                val result = metaAnalysisAgent.analyzeReports(allReports, timeframe)

                if (result.isFailure) {
                    val error = result.exceptionOrNull()
                    Timber.e(error, "Meta-analysis failed")
                    _uiState.value = _uiState.value.copy(
                        isAnalyzing = false,
                        errorMessage = "Analyse feilet: ${error?.message}"
                    )
                    return@launch
                }

                val metaAnalysis = result.getOrNull()!!

                _uiState.value = _uiState.value.copy(
                    analysisProgress = "Lagrer analyse..."
                )

                // Save meta-analysis to database
                val analysisId = metaAnalysisRepository.insertAnalysis(metaAnalysis)

                // Phase 3B: With rolling window, we track which reports were used but don't mark as "analyzed"
                // Reports can be reused in multiple analyses with different timeframes
                // The metaAnalysis.reportIds already tracks which reports were included

                Timber.i("Meta-analysis completed: ${metaAnalysis.strategyName} (confidence: ${metaAnalysis.confidence})")

                // Update state with result
                _uiState.value = _uiState.value.copy(
                    isAnalyzing = false,
                    completedAnalysis = metaAnalysis.copy(id = analysisId),
                    showAnalysisResult = true,
                    successMessage = "Analyse fullført!"
                )

            } catch (e: Exception) {
                Timber.e(e, "Error during meta-analysis")
                _uiState.value = _uiState.value.copy(
                    isAnalyzing = false,
                    errorMessage = "En feil oppstod under analyse: ${e.message}"
                )
            }
        }
    }
    */

    /**
     * Dismiss analysis result dialog
     */
    fun dismissAnalysisResult() {
        _uiState.value = _uiState.value.copy(
            showAnalysisResult = false,
            completedAnalysis = null
        )
    }

    // TEMPORARILY DISABLED - Learning section
    /*
    /**
     * Approve meta-analysis and create strategy
     */
    fun approveAnalysis(analysis: MetaAnalysis) {
        viewModelScope.launch {
            try {
                // Create strategy from recommended strategy
                val strategy = analysis.recommendedStrategy.toStrategy()

                // Save strategy
                strategyRepository.insertStrategy(strategy)

                // Approve meta-analysis and link to strategy
                metaAnalysisRepository.approveAnalysis(
                    analysisId = analysis.id,
                    strategyId = strategy.id.toLongOrNull()
                )

                Timber.i("Approved meta-analysis and created strategy: ${strategy.name}")

                _uiState.value = _uiState.value.copy(
                    showAnalysisResult = false,
                    completedAnalysis = null,
                    successMessage = "Strategi opprettet! Gå til Strategies for å aktivere."
                )

            } catch (e: Exception) {
                Timber.e(e, "Error approving analysis")
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Kunne ikke opprette strategi: ${e.message}"
                )
            }
        }
    }

    /**
     * Reject meta-analysis
     */
    fun rejectAnalysis(analysis: MetaAnalysis, reason: String) {
        viewModelScope.launch {
            try {
                metaAnalysisRepository.rejectAnalysis(analysis.id, reason)

                Timber.i("Rejected meta-analysis: ${analysis.strategyName}")

                _uiState.value = _uiState.value.copy(
                    showAnalysisResult = false,
                    completedAnalysis = null,
                    successMessage = "Analyse avvist"
                )

            } catch (e: Exception) {
                Timber.e(e, "Error rejecting analysis")
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Kunne ikke avvise analyse: ${e.message}"
                )
            }
        }
    }
    */

    private fun addMessage(message: ChatMessage) {
        val currentMessages = _uiState.value.messages.toMutableList()
        currentMessages.add(message)
        _uiState.value = _uiState.value.copy(messages = currentMessages)

        // Save to SavedStateHandle for persistence
        savedStateHandle[KEY_MESSAGES] = currentMessages
    }

    private fun removeTypingIndicator() {
        val currentMessages = _uiState.value.messages.toMutableList()
        currentMessages.removeAll { it.isTyping }
        _uiState.value = _uiState.value.copy(messages = currentMessages)

        // Save to SavedStateHandle for persistence
        savedStateHandle[KEY_MESSAGES] = currentMessages
    }
}

/**
 * Extension function to convert RecommendedStrategy to Strategy
 */
private fun com.cryptotrader.domain.model.RecommendedStrategy.toStrategy(): Strategy {
    return Strategy(
        id = java.util.UUID.randomUUID().toString(),
        name = name,
        description = description,
        entryConditions = entryConditions,
        exitConditions = exitConditions,
        positionSizePercent = positionSizePercent,
        stopLossPercent = stopLossPercent,
        takeProfitPercent = takeProfitPercent,
        tradingPairs = tradingPairs,
        isActive = false,
        riskLevel = riskLevel,
        analysisReport = rationale,
        approvalStatus = com.cryptotrader.domain.model.ApprovalStatus.PENDING,
        source = com.cryptotrader.domain.model.StrategySource.AI_CLAUDE
    )
}

data class ChatState(
    val messages: List<ChatMessage> = emptyList(),
    val isImplementingStrategy: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null,
    // Meta-analysis fields
    val unanalyzedReportCount: Int = 0,
    val isAnalyzing: Boolean = false,
    val analysisProgress: String? = null,
    val completedAnalysis: MetaAnalysis? = null,
    val showAnalysisResult: Boolean = false,
    // Phase 3B: Temporal analysis
    val selectedTimeframe: com.cryptotrader.domain.model.AnalysisTimeframe = com.cryptotrader.domain.model.AnalysisTimeframe.WEEKLY
)
