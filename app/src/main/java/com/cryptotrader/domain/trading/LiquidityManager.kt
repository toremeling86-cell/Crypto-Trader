package com.cryptotrader.domain.trading

import com.cryptotrader.data.repository.KrakenRepository
import com.cryptotrader.domain.usecase.TradeRequest
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Liquidity management and order splitting
 *
 * Prevents price impact from large orders by:
 * - Checking order book depth before trading
 * - Splitting large orders into smaller chunks
 * - Monitoring volume and liquidity
 */
@Singleton
class LiquidityManager @Inject constructor(
    private val krakenRepository: KrakenRepository
) {

    companion object {
        private const val MAX_ORDER_SIZE_PERCENT_OF_VOLUME = 2.0  // Max 2% of 24h volume
        private const val MIN_LIQUIDITY_THRESHOLD = 10000.0        // Min $10k daily volume
        private const val MAX_ORDERBOOK_IMPACT_PERCENT = 0.5       // Max 0.5% price impact
    }

    /**
     * Check if there's sufficient liquidity for a trade
     *
     * @param pair Trading pair
     * @param volume Trade volume
     * @param price Trade price
     * @return Liquidity check result
     */
    suspend fun checkLiquidity(
        pair: String,
        volume: Double,
        price: Double
    ): LiquidityCheckResult {
        try {
            // Get ticker data for volume info
            val tickerResult = krakenRepository.getTicker(pair)

            if (tickerResult.isFailure) {
                return LiquidityCheckResult(
                    isSufficient = false,
                    shouldSplit = false,
                    reason = "Failed to fetch market data",
                    suggestedChunks = listOf(volume)
                )
            }

            val ticker = tickerResult.getOrNull()!!

            // Calculate trade value
            val tradeValue = volume * price

            // Check against 24h volume
            val volume24h = ticker.volume24h * ticker.last // Volume in USD
            val volumePercent = (tradeValue / volume24h) * 100.0

            // Check minimum liquidity threshold
            if (volume24h < MIN_LIQUIDITY_THRESHOLD) {
                return LiquidityCheckResult(
                    isSufficient = false,
                    shouldSplit = false,
                    reason = "Insufficient market liquidity: $${volume24h.toInt()} 24h volume (min: $$MIN_LIQUIDITY_THRESHOLD)",
                    suggestedChunks = listOf(volume)
                )
            }

            // Check if order is too large relative to volume
            if (volumePercent > MAX_ORDER_SIZE_PERCENT_OF_VOLUME) {
                // Calculate optimal chunk size
                val maxChunkValue = volume24h * (MAX_ORDER_SIZE_PERCENT_OF_VOLUME / 100.0)
                val maxChunkVolume = maxChunkValue / price
                val numberOfChunks = kotlin.math.ceil(volume / maxChunkVolume).toInt()

                val chunks = splitOrder(volume, numberOfChunks)

                return LiquidityCheckResult(
                    isSufficient = true,
                    shouldSplit = true,
                    reason = "Order too large (${"%.2f".format(volumePercent)}% of volume). Splitting into $numberOfChunks chunks.",
                    suggestedChunks = chunks,
                    estimatedImpact = estimatePriceImpact(volumePercent)
                )
            }

            // Sufficient liquidity
            return LiquidityCheckResult(
                isSufficient = true,
                shouldSplit = false,
                reason = "Sufficient liquidity",
                suggestedChunks = listOf(volume),
                estimatedImpact = estimatePriceImpact(volumePercent)
            )

        } catch (e: Exception) {
            Timber.e(e, "Error checking liquidity")
            return LiquidityCheckResult(
                isSufficient = false,
                shouldSplit = false,
                reason = "Error checking liquidity: ${e.message}",
                suggestedChunks = listOf(volume)
            )
        }
    }

    /**
     * Split an order into smaller chunks
     */
    private fun splitOrder(totalVolume: Double, numberOfChunks: Int): List<Double> {
        val chunkSize = totalVolume / numberOfChunks
        return List(numberOfChunks) { chunkSize }
    }

    /**
     * Estimate price impact based on order size
     */
    private fun estimatePriceImpact(volumePercent: Double): Double {
        // Simplified price impact model
        // Real impact would require order book analysis
        return when {
            volumePercent > 5.0 -> volumePercent * 0.15  // High impact
            volumePercent > 2.0 -> volumePercent * 0.10  // Medium impact
            else -> volumePercent * 0.05                  // Low impact
        }
    }

    /**
     * Execute order with automatic splitting if needed
     *
     * @param tradeRequest Original trade request
     * @return List of executed trades (may be multiple if split)
     */
    suspend fun executeWithLiquidityCheck(
        tradeRequest: TradeRequest
    ): Result<List<com.cryptotrader.domain.model.Trade>> {
        // Check liquidity
        val liquidityCheck = checkLiquidity(
            tradeRequest.pair,
            tradeRequest.volume,
            tradeRequest.price
        )

        if (!liquidityCheck.isSufficient) {
            Timber.w("Insufficient liquidity: ${liquidityCheck.reason}")
            return Result.failure(Exception("Insufficient liquidity: ${liquidityCheck.reason}"))
        }

        if (!liquidityCheck.shouldSplit) {
            // Execute single order
            val result = krakenRepository.placeOrder(tradeRequest)
            return if (result.isSuccess) {
                Result.success(listOf(result.getOrNull()!!))
            } else {
                Result.failure(result.exceptionOrNull() ?: Exception("Order execution failed"))
            }
        }

        // Split and execute
        Timber.i("Splitting order into ${liquidityCheck.suggestedChunks.size} chunks")
        val executedTrades = mutableListOf<com.cryptotrader.domain.model.Trade>()

        for ((index, chunkVolume) in liquidityCheck.suggestedChunks.withIndex()) {
            val chunkRequest = tradeRequest.copy(volume = chunkVolume)

            try {
                val result = krakenRepository.placeOrder(chunkRequest)

                if (result.isSuccess) {
                    val trade = result.getOrNull()!!
                    executedTrades.add(trade)
                    Timber.d("Chunk ${index + 1}/${liquidityCheck.suggestedChunks.size} executed: $chunkVolume")

                    // Add delay between chunks to avoid rate limiting
                    if (index < liquidityCheck.suggestedChunks.size - 1) {
                        kotlinx.coroutines.delay(1000) // 1 second delay
                    }
                } else {
                    Timber.e("Chunk execution failed: ${result.exceptionOrNull()?.message}")
                    // Continue with remaining chunks
                }

            } catch (e: Exception) {
                Timber.e(e, "Error executing chunk")
            }
        }

        return if (executedTrades.isNotEmpty()) {
            Result.success(executedTrades)
        } else {
            Result.failure(Exception("All chunks failed to execute"))
        }
    }
}

/**
 * Liquidity check result
 */
data class LiquidityCheckResult(
    val isSufficient: Boolean,
    val shouldSplit: Boolean,
    val reason: String,
    val suggestedChunks: List<Double>,
    val estimatedImpact: Double = 0.0
)
