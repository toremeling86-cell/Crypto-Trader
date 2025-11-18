package com.cryptotrader.data.repository

import com.cryptotrader.data.local.dao.OHLCBarDao
import com.cryptotrader.data.local.dao.TechnicalIndicatorDao
import com.cryptotrader.data.local.entities.OHLCBarEntity
import com.cryptotrader.data.local.entities.TechnicalIndicatorEntity
import com.cryptotrader.domain.backtesting.PriceBar
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local Historical Data Repository - Queries OHLC data from Room database
 *
 * Replaces in-memory cache in HistoricalDataRepository with persistent Room storage
 * Used by BacktestEngine for offline backtesting with local data
 */
@Singleton
class LocalHistoricalDataRepository @Inject constructor(
    private val ohlcBarDao: OHLCBarDao,
    private val technicalIndicatorDao: TechnicalIndicatorDao
) {

    /**
     * Get OHLC bars for backtesting
     *
     * Returns PriceBar domain model (same format as Kraken API)
     */
    suspend fun getHistoricalBars(
        asset: String,
        timeframe: String,
        startTimestamp: Long,
        endTimestamp: Long
    ): Result<List<PriceBar>> {
        return try {
            val entities = ohlcBarDao.getBarsInRange(asset, timeframe, startTimestamp, endTimestamp)
            val priceBars = entities.map { it.toDomain() }

            Timber.d("📊 Retrieved ${priceBars.size} bars from local storage: $asset $timeframe")
            Result.success(priceBars)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get historical bars from local storage")
            Result.failure(e)
        }
    }

    /**
     * Get OHLC bars as Flow (reactive updates)
     */
    fun getHistoricalBarsFlow(
        asset: String,
        timeframe: String,
        startTimestamp: Long,
        endTimestamp: Long
    ): Flow<List<PriceBar>> {
        return ohlcBarDao.getBarsInRangeFlow(asset, timeframe, startTimestamp, endTimestamp)
            .map { entities -> entities.map { it.toDomain() } }
    }

    /**
     * Get latest OHLC bar
     */
    suspend fun getLatestBar(asset: String, timeframe: String): Result<PriceBar?> {
        return try {
            val entity = ohlcBarDao.getLatestBar(asset, timeframe)
            Result.success(entity?.toDomain())
        } catch (e: Exception) {
            Timber.e(e, "Failed to get latest bar")
            Result.failure(e)
        }
    }

    /**
     * Check if sufficient data exists for backtesting
     */
    suspend fun hasEnoughData(
        asset: String,
        timeframe: String,
        minBars: Long = 100
    ): Boolean {
        return try {
            val count = ohlcBarDao.getBarCount(asset, timeframe)
            count >= minBars
        } catch (e: Exception) {
            Timber.e(e, "Failed to check data availability")
            false
        }
    }

    /**
     * Get available assets in local storage
     */
    suspend fun getAvailableAssets(): List<String> {
        return try {
            ohlcBarDao.getAllAssets()
        } catch (e: Exception) {
            Timber.e(e, "Failed to get available assets")
            emptyList()
        }
    }

    /**
     * Get available timeframes for asset
     */
    suspend fun getAvailableTimeframes(asset: String): List<String> {
        return try {
            ohlcBarDao.getTimeframesForAsset(asset)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get available timeframes")
            emptyList()
        }
    }

    /**
     * Get technical indicator values
     */
    suspend fun getIndicators(
        asset: String,
        timeframe: String,
        indicatorType: String,
        startTimestamp: Long,
        endTimestamp: Long
    ): Result<List<TechnicalIndicatorEntity>> {
        return try {
            val indicators = technicalIndicatorDao.getIndicatorInRange(
                asset, timeframe, indicatorType, startTimestamp, endTimestamp
            )
            Result.success(indicators)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get indicators")
            Result.failure(e)
        }
    }

    /**
     * Get all indicators for a specific bar
     */
    suspend fun getIndicatorsForBar(
        asset: String,
        timeframe: String,
        timestamp: Long
    ): Result<List<TechnicalIndicatorEntity>> {
        return try {
            val indicators = technicalIndicatorDao.getIndicatorsForBar(asset, timeframe, timestamp)
            Result.success(indicators)
        } catch (e: Exception) {
            Timber.e(e, "Failed to get indicators for bar")
            Result.failure(e)
        }
    }

    /**
     * Insert OHLC bars (for manual data population or testing)
     */
    suspend fun insertBars(bars: List<OHLCBarEntity>): Result<Unit> {
        return try {
            ohlcBarDao.insertAll(bars)
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to insert bars")
            Result.failure(e)
        }
    }

    /**
     * Delete old data to save storage space
     */
    suspend fun deleteOlderThan(timestamp: Long): Result<Unit> {
        return try {
            ohlcBarDao.deleteOlderThan(timestamp)
            technicalIndicatorDao.deleteOlderThan(timestamp)
            Timber.i("🗑️ Deleted data older than $timestamp")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete old data")
            Result.failure(e)
        }
    }
}

/**
 * Extension function to convert OHLCBarEntity to PriceBar domain model
 */
private fun OHLCBarEntity.toDomain(): PriceBar {
    return PriceBar(
        timestamp = this.timestamp,
        open = this.open,
        high = this.high,
        low = this.low,
        close = this.close,
        volume = this.volume
    )
}
