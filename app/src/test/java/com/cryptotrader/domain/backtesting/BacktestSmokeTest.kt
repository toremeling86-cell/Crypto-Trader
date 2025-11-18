package com.cryptotrader.domain.backtesting

import com.cryptotrader.data.local.dao.OHLCBarDao
import com.cryptotrader.diagnostics.diagnosticRsiStrategy
import com.cryptotrader.domain.model.MarketTicker
import com.cryptotrader.domain.model.Portfolio
import com.cryptotrader.domain.model.TradeAction
import com.cryptotrader.domain.model.TradeSignal
import com.cryptotrader.domain.trading.RiskManager
import com.cryptotrader.domain.trading.TradingEngine
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.io.File
import kotlin.test.assertTrue

class BacktestSmokeTest {

    private val tradingEngine: TradingEngine = mockk(relaxed = true)
    private val riskManager: RiskManager = mockk(relaxed = true)
    private val ohlcBarDao: OHLCBarDao = mockk(relaxed = true)

    @Test
    fun `backtest produces trades using diagnostic RSI strategy`() = runBlocking {
        val priceBars = loadSampleBars()
        val strategy = diagnosticRsiStrategy()

        mockRiskManager()
        mockTradingEngine(priceBars)

        val backtestEngine = BacktestEngine(tradingEngine, riskManager, ohlcBarDao)
        val result = backtestEngine.runBacktest(strategy, priceBars)

        assertTrue(result.trades.isNotEmpty(), "Expected diagnostic backtest to produce trades")
    }

    private fun mockRiskManager() {
        every { riskManager.calculateStopLoss(any(), any(), any()) } answers {
            val entry = firstArg<Double>()
            val percent = secondArg<Double>() / 100.0
            val isBuy = thirdArg<Boolean>()
            if (isBuy) entry * (1 - percent) else entry * (1 + percent)
        }

        every { riskManager.calculateTakeProfit(any(), any(), any()) } answers {
            val entry = firstArg<Double>()
            val percent = secondArg<Double>() / 100.0
            val isBuy = thirdArg<Boolean>()
            if (isBuy) entry * (1 + percent) else entry * (1 - percent)
        }
    }

    private fun mockTradingEngine(priceBars: List<PriceBar>) {
        var hasPosition = false
        val entryPrice = priceBars.first().close

        coEvery { tradingEngine.clearPriceHistory(any()) } returns Unit
        coEvery { tradingEngine.updatePriceHistory(any(), any()) } returns Unit
        coEvery { tradingEngine.evaluateStrategy(any(), any(), any(), any()) } answers {
            val marketData = secondArg<MarketTicker>()
            val portfolio = thirdArg<Portfolio>()

            if (!hasPosition && marketData.last <= entryPrice) {
                hasPosition = true
                TradeSignal(
                    strategyId = "diagnostic_rsi_strategy",
                    pair = marketData.pair,
                    action = TradeAction.BUY,
                    confidence = 0.9,
                    targetPrice = marketData.last,
                    suggestedVolume = portfolio.totalValue / marketData.last,
                    reason = "RSI diagnostic entry"
                )
            } else if (hasPosition && marketData.last >= entryPrice * 1.01) {
                hasPosition = false
                TradeSignal(
                    strategyId = "diagnostic_rsi_strategy",
                    pair = marketData.pair,
                    action = TradeAction.SELL,
                    confidence = 0.9,
                    targetPrice = marketData.last,
                    suggestedVolume = portfolio.totalValue / marketData.last,
                    reason = "RSI diagnostic exit"
                )
            } else {
                null
            }
        }
    }

    private fun loadSampleBars(): List<PriceBar> {
        val file = File("sample_data/XXBTZUSD_1h_sample.csv")
        return file.readLines()
            .drop(1)
            .filter { it.isNotBlank() }
            .mapNotNull { line ->
                val columns = line.split(",")
                if (columns.size < 6) return@mapNotNull null
                PriceBar(
                    timestamp = columns[0].toLong(),
                    open = columns[1].toDouble(),
                    high = columns[2].toDouble(),
                    low = columns[3].toDouble(),
                    close = columns[4].toDouble(),
                    volume = columns[5].toDouble()
                )
            }
    }
}
