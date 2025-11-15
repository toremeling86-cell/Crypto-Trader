package com.cryptotrader.data.repository

import com.cryptotrader.data.local.dao.PortfolioSnapshotDao
import com.cryptotrader.data.local.dao.TradeDao
import com.cryptotrader.data.local.entities.PortfolioSnapshotEntity
import com.cryptotrader.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for portfolio operations
 * Handles portfolio data, snapshots, and historical tracking
 */
@Singleton
class PortfolioRepository @Inject constructor(
    private val krakenRepository: KrakenRepository,
    private val tradeDao: TradeDao,
    private val snapshotDao: PortfolioSnapshotDao
) {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Get current portfolio holdings from Kraken
     */
    suspend fun getCurrentHoldings(): Result<List<PortfolioHolding>> {
        return try {
            val balanceResult = krakenRepository.getBalance()
            if (balanceResult.isFailure) {
                return Result.failure(balanceResult.exceptionOrNull() ?: Exception("Failed to get balance"))
            }

            val balances = balanceResult.getOrNull() ?: return Result.success(emptyList())

            // Parse balance data and get current prices
            val holdings = mutableListOf<PortfolioHolding>()
            var totalValue = 0.0

            // Calculate total value first
            for ((asset, balanceStr) in balances) {
                if (asset == "error") continue

                val balance = balanceStr.toDoubleOrNull() ?: continue
                if (balance == 0.0) continue

                // Get current price for this asset
                val price = getCurrentPrice(asset)
                val value = balance * price
                totalValue += value
            }

            // Create holdings with percentages
            for ((asset, balanceStr) in balances) {
                if (asset == "error") continue

                val balance = balanceStr.toDoubleOrNull() ?: continue
                if (balance == 0.0) continue

                val price = getCurrentPrice(asset)
                val value = balance * price
                val percent = if (totalValue > 0) (value / totalValue) * 100.0 else 0.0

                holdings.add(
                    PortfolioHolding(
                        asset = asset,
                        assetName = getAssetName(asset),
                        amount = balance,
                        currentPrice = price,
                        currentValue = value,
                        percentOfPortfolio = percent,
                        assetType = determineAssetType(asset)
                    )
                )
            }

            Result.success(holdings.sortedByDescending { it.currentValue })
        } catch (e: Exception) {
            Timber.e(e, "Error getting current holdings")
            Result.failure(e)
        }
    }

    /**
     * Get current portfolio snapshot
     */
    suspend fun getCurrentSnapshot(): Result<PortfolioSnapshot> {
        return try {
            val holdingsResult = getCurrentHoldings()
            if (holdingsResult.isFailure) {
                return Result.failure(holdingsResult.exceptionOrNull() ?: Exception("Failed to get holdings"))
            }

            val holdings = holdingsResult.getOrNull() ?: emptyList()
            val totalValue = holdings.sumOf { it.currentValue }
            val totalPnL = holdings.sumOf { it.unrealizedPnL }
            val totalPnLPercent = if (totalValue > 0) (totalPnL / totalValue) * 100.0 else 0.0

            val snapshot = PortfolioSnapshot(
                timestamp = System.currentTimeMillis(),
                totalValue = totalValue,
                totalPnL = totalPnL,
                totalPnLPercent = totalPnLPercent,
                holdings = holdings
            )

            Result.success(snapshot)
        } catch (e: Exception) {
            Timber.e(e, "Error getting current snapshot")
            Result.failure(e)
        }
    }

    /**
     * Save portfolio snapshot to database
     */
    suspend fun saveSnapshot(snapshot: PortfolioSnapshot) {
        try {
            val entity = PortfolioSnapshotEntity(
                timestamp = snapshot.timestamp,
                totalValue = snapshot.totalValue,
                totalPnL = snapshot.totalPnL,
                totalPnLPercent = snapshot.totalPnLPercent,
                holdingsJson = json.encodeToString(snapshot.holdings)
            )
            snapshotDao.insertSnapshot(entity)
            Timber.d("Portfolio snapshot saved: ${snapshot.totalValue}")
        } catch (e: Exception) {
            Timber.e(e, "Error saving snapshot")
            throw e
        }
    }

    /**
     * Get historical snapshots for a time period
     */
    suspend fun getHistoricalSnapshots(period: TimePeriod): List<PortfolioSnapshot> {
        return try {
            val startTime = getStartTimeForPeriod(period)
            val entities = snapshotDao.getSnapshotsSince(startTime)
            entities.map { it.toDomain() }
        } catch (e: Exception) {
            Timber.e(e, "Error getting historical snapshots")
            emptyList()
        }
    }

    /**
     * Get all snapshots as Flow
     */
    fun getAllSnapshotsFlow(): Flow<List<PortfolioSnapshot>> {
        return snapshotDao.getAllSnapshots().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    /**
     * Delete old snapshots (keep last 365 days)
     */
    suspend fun cleanOldSnapshots() {
        try {
            val cutoffTime = System.currentTimeMillis() - (365L * 24 * 60 * 60 * 1000)
            snapshotDao.deleteSnapshotsBefore(cutoffTime)
            Timber.d("Old snapshots cleaned")
        } catch (e: Exception) {
            Timber.e(e, "Error cleaning old snapshots")
        }
    }

    /**
     * Get current price for an asset
     */
    private suspend fun getCurrentPrice(asset: String): Double {
        return try {
            // Map asset to trading pair (assuming USD base)
            val pair = when {
                asset.startsWith("X") && asset.endsWith("Z") -> asset // Already a pair
                asset == "ZUSD" -> return 1.0 // USD is 1:1
                asset == "XXBT" -> "XXBTZUSD"
                asset == "XETH" -> "XETHZUSD"
                else -> "${asset}USD"
            }

            val tickerResult = krakenRepository.getTicker(pair)
            if (tickerResult.isSuccess) {
                val ticker = tickerResult.getOrNull()
                ticker?.last ?: 1.0
            } else {
                1.0
            }
        } catch (e: Exception) {
            Timber.w("Could not get price for $asset, using 1.0")
            1.0
        }
    }

    /**
     * Get human-readable asset name
     */
    private fun getAssetName(asset: String): String {
        return when (asset) {
            "XXBT", "XBT" -> "Bitcoin"
            "XETH", "ETH" -> "Ethereum"
            "XLTC", "LTC" -> "Litecoin"
            "XXRP", "XRP" -> "Ripple"
            "SOL" -> "Solana"
            "ZUSD", "USD" -> "US Dollar"
            "ZEUR", "EUR" -> "Euro"
            else -> asset
        }
    }

    /**
     * Determine asset type
     */
    private fun determineAssetType(asset: String): AssetType {
        return when {
            asset.startsWith("Z") -> AssetType.FIAT
            asset.startsWith("X") -> AssetType.CRYPTO
            asset in listOf("USD", "EUR", "GBP") -> AssetType.FIAT
            asset in listOf("BTC", "ETH", "SOL", "XRP", "LTC") -> AssetType.CRYPTO
            else -> AssetType.CRYPTO
        }
    }

    /**
     * Get start time for period
     */
    private fun getStartTimeForPeriod(period: TimePeriod): Long {
        val now = System.currentTimeMillis()
        return when (period) {
            TimePeriod.ONE_DAY -> now - (24 * 60 * 60 * 1000)
            TimePeriod.ONE_WEEK -> now - (7 * 24 * 60 * 60 * 1000)
            TimePeriod.ONE_MONTH -> now - (30L * 24 * 60 * 60 * 1000)
            TimePeriod.THREE_MONTHS -> now - (90L * 24 * 60 * 60 * 1000)
            TimePeriod.SIX_MONTHS -> now - (180L * 24 * 60 * 60 * 1000)
            TimePeriod.ONE_YEAR -> now - (365L * 24 * 60 * 60 * 1000)
            TimePeriod.ALL_TIME -> 0L
        }
    }

    /**
     * Convert entity to domain model
     */
    private fun PortfolioSnapshotEntity.toDomain(): PortfolioSnapshot {
        val holdings = try {
            json.decodeFromString<List<PortfolioHolding>>(holdingsJson)
        } catch (e: Exception) {
            Timber.w("Could not parse holdings JSON")
            emptyList()
        }

        return PortfolioSnapshot(
            timestamp = timestamp,
            totalValue = totalValue,
            totalPnL = totalPnL,
            totalPnLPercent = totalPnLPercent,
            holdings = holdings
        )
    }
}
