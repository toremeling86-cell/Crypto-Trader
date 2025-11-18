package com.cryptotrader.domain.backtesting

interface BacktestEventLogger {
    fun log(event: BacktestEvent)
}
