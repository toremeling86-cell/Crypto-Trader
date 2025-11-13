package com.cryptotrader.domain.usecase

import com.cryptotrader.domain.model.*
import com.cryptotrader.domain.trading.RiskManager
import timber.log.Timber
import javax.inject.Inject

/**
 * Use case for executing trades with proper validation and risk management
 */
class ExecuteTradeUseCase @Inject constructor(
    private val riskManager: RiskManager
) {

    suspend operator fun invoke(
        signal: TradeSignal,
        portfolio: Portfolio
    ): Result<TradeRequest> {
        return try {
            // Validate signal
            if (signal.suggestedVolume <= 0) {
                return Result.failure(IllegalArgumentException("Invalid volume: ${signal.suggestedVolume}"))
            }

            val targetPrice = signal.targetPrice
                ?: return Result.failure(IllegalArgumentException("Target price is null"))

            val tradeValue = signal.suggestedVolume * targetPrice

            // Check risk management constraints
            if (!riskManager.canExecuteTrade(tradeValue, portfolio)) {
                return Result.failure(
                    IllegalStateException("Trade rejected by risk manager")
                )
            }

            // Create trade request
            val tradeRequest = TradeRequest(
                pair = signal.pair,
                type = if (signal.action == TradeAction.BUY) TradeType.BUY else TradeType.SELL,
                orderType = OrderType.MARKET,
                volume = signal.suggestedVolume,
                price = targetPrice,
                strategyId = signal.strategyId
            )

            Timber.d("Trade request created: $tradeRequest")
            Result.success(tradeRequest)
        } catch (e: Exception) {
            Timber.e(e, "Error creating trade request")
            Result.failure(e)
        }
    }
}

/**
 * Trade request to be sent to exchange
 */
data class TradeRequest(
    val pair: String,
    val type: TradeType,
    val orderType: OrderType,
    val volume: Double,
    val price: Double,
    val strategyId: String? = null,
    val stopLoss: Double? = null,
    val takeProfit: Double? = null
)

enum class OrderType {
    MARKET, LIMIT, STOP_LOSS, TAKE_PROFIT
}
