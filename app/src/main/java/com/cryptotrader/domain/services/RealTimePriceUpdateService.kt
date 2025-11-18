package com.cryptotrader.domain.services

import com.cryptotrader.data.remote.kraken.KrakenWebSocketClient
import com.cryptotrader.data.remote.kraken.TickerUpdate
import com.cryptotrader.data.repository.PositionRepository
import com.cryptotrader.utils.FeatureFlags
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service for real-time position price updates via WebSocket
 *
 * Features:
 * - Subscribes to ticker updates for all open positions
 * - Updates position prices in real-time
 * - Checks stop-loss and take-profit triggers
 * - Automatic lifecycle management (activates when needed, cleans up when idle)
 */
@Singleton
class RealTimePriceUpdateService @Inject constructor(
    private val webSocketClient: KrakenWebSocketClient,
    private val positionRepository: PositionRepository
) {

    private var subscriptionJob: Job? = null
    private var isActive = false

    /**
     * Start real-time price updates for open positions
     *
     * @param scope Coroutine scope (typically ViewModelScope or similar)
     */
    fun startPriceUpdates(scope: CoroutineScope) {
        if (!FeatureFlags.ENABLE_KRAKEN_WEBSOCKET) {
            Timber.d("WebSocket disabled via feature flag - skipping real-time price updates")
            return
        }

        if (isActive) {
            Timber.d("Real-time price updates already active")
            return
        }

        Timber.i("Starting real-time price updates via WebSocket")
        isActive = true

        // Launch coroutine to set up subscription
        scope.launch {
            // Get open positions
            val openPositions = positionRepository.getOpenPositionsSync()
            val pairs = openPositions.map { it.pair }.distinct()

            if (pairs.isEmpty()) {
                Timber.d("No open positions to subscribe to")
                stopPriceUpdates()
                return@launch
            }

            Timber.d("Subscribing to ticker updates for ${pairs.size} pairs: $pairs")

            // Subscribe to ticker updates
            subscriptionJob = webSocketClient.subscribeToTicker(pairs)
                .onEach { tickerUpdate ->
                    handleTickerUpdate(tickerUpdate)
                }
                .catch { e ->
                    Timber.e(e, "WebSocket ticker subscription error")
                    stopPriceUpdates()
                }
                .launchIn(scope)
        }
    }

    /**
     * Handle incoming ticker update
     */
    private suspend fun handleTickerUpdate(tickerUpdate: TickerUpdate) {
        try {
            Timber.d("Price update: ${tickerUpdate.pair} = ${tickerUpdate.last}")

            // Update all positions for this pair
            val openPositions = positionRepository.getOpenPositionsSync()
            val affectedPositions = openPositions.filter { it.pair == tickerUpdate.pair }

            affectedPositions.forEach { position ->
                // Update position price
                positionRepository.updatePositionPrice(position.id.toString(), tickerUpdate.last)

                // Check stop-loss and take-profit
                val triggered = positionRepository.checkStopLossTakeProfit(position.id.toString())
                if (triggered.isSuccess && triggered.getOrNull() == true) {
                    Timber.i("SL/TP triggered for position ${position.id} (${position.pair})")
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error handling ticker update")
        }
    }

    /**
     * Stop real-time price updates
     */
    fun stopPriceUpdates() {
        if (!isActive) return

        Timber.i("Stopping real-time price updates")
        subscriptionJob?.cancel()
        subscriptionJob = null
        webSocketClient.markAsInactive()
        isActive = false
    }

    /**
     * Check if real-time updates are active
     */
    fun isActive(): Boolean = isActive

    /**
     * Restart price updates (useful when new positions are opened)
     */
    fun restartPriceUpdates(scope: CoroutineScope) {
        stopPriceUpdates()
        startPriceUpdates(scope)
    }
}
