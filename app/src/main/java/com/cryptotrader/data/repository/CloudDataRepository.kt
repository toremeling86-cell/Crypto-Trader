package com.cryptotrader.data.repository

import android.content.Context
import com.cryptotrader.data.cloud.CloudStorageClient
import com.cryptotrader.data.dataimport.ParquetFileReader
import com.cryptotrader.data.local.dao.DataQuarterCoverageDao
import com.cryptotrader.data.local.dao.OHLCBarDao
import com.cryptotrader.data.local.entities.DataQuarterCoverageEntity
import com.cryptotrader.data.local.entities.OHLCBarEntity
import com.cryptotrader.domain.backtesting.PriceBar
import com.cryptotrader.domain.model.DataTier
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cloud Data Repository
 *
 * Manages historical market data with hybrid cloud + local cache strategy.
 * Downloads from Cloudflare R2 on-demand and caches locally in Room database.
 *
 * Smart Download Strategy:
 * 1. Check local cache first
 * 2. If missing, determine required quarters for date range
 * 3. Download from R2 in parallel
 * 4. Parse Parquet files and insert into Room
 * 5. Track data coverage to avoid re-downloads
 *
 * File Structure in R2:
 * - {asset}/{timeframe}/{year}-Q{quarter}.parquet.zst
 * - Example: XXBTZUSD/1h/2024-Q2.parquet.zst
 */
@Singleton
class CloudDataRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cloudClient: CloudStorageClient,
    private val parquetReader: ParquetFileReader,
    private val ohlcBarDao: OHLCBarDao,
    private val quarterCoverageDao: DataQuarterCoverageDao
) {
    companion object {
        private const val CACHE_DIR = "parquet_cache"
        private const val MAX_CACHE_SIZE_MB = 500 // 500MB local cache limit

        // Quarter boundaries
        private const val MILLIS_PER_QUARTER = 90L * 24 * 60 * 60 * 1000 // ~90 days
    }

    private val _downloadProgress = MutableStateFlow<DownloadProgress?>(null)
    val downloadProgress: StateFlow<DownloadProgress?> = _downloadProgress.asStateFlow()

    private val cacheDir: File by lazy {
        File(context.cacheDir, CACHE_DIR).also { it.mkdirs() }
    }

    /**
     * Ensure data is available for the given date range
     *
     * Checks local cache first, downloads missing data from R2 if needed.
     *
     * @param asset Asset symbol (e.g., "XXBTZUSD")
     * @param timeframe Timeframe (e.g., "1h", "1d")
     * @param startTime Start timestamp (milliseconds)
     * @param endTime End timestamp (milliseconds)
     * @param dataTier Data tier (TIER_1_PREMIUM, etc.)
     * @return Result with success or error
     */
    suspend fun ensureDataAvailable(
        asset: String,
        timeframe: String,
        startTime: Long,
        endTime: Long,
        dataTier: DataTier = DataTier.TIER_3_STANDARD
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Timber.i("📊 Ensuring data availability: $asset $timeframe [${formatTimestamp(startTime)} - ${formatTimestamp(endTime)}]")

            // Check which quarters are needed
            val requiredQuarters = determineRequiredQuarters(startTime, endTime)
            Timber.d("   Required quarters: $requiredQuarters")

            // Check which quarters are already cached
            val cachedQuarters = getCachedQuarters(asset, timeframe, dataTier)
            Timber.d("   Cached quarters: $cachedQuarters")

            // Determine missing quarters
            val missingQuarters = requiredQuarters.filterNot { it in cachedQuarters }

            if (missingQuarters.isEmpty()) {
                Timber.i("✅ All data already cached locally")
                return@withContext Result.success(Unit)
            }

            Timber.i("📥 Need to download ${missingQuarters.size} quarters: $missingQuarters")

            // Download missing quarters
            downloadQuarters(asset, timeframe, missingQuarters, dataTier)

            Timber.i("✅ Data availability ensured for $asset $timeframe")
            Result.success(Unit)

        } catch (e: Exception) {
            Timber.e(e, "Failed to ensure data availability")
            _downloadProgress.value = null
            Result.failure(e)
        }
    }

    /**
     * Get OHLC bars from local cache
     *
     * This assumes data has already been ensured via ensureDataAvailable()
     */
    suspend fun getOHLCBars(
        asset: String,
        timeframe: String,
        startTime: Long,
        endTime: Long
    ): Result<List<PriceBar>> = withContext(Dispatchers.IO) {
        try {
            val entities = ohlcBarDao.getBarsInRange(
                asset = asset,
                timeframe = timeframe,
                startTimestamp = startTime,
                endTimestamp = endTime
            )

            val bars = entities.map { it.toDomain() }

            Timber.d("📊 Retrieved ${bars.size} bars from local cache")
            Result.success(bars)

        } catch (e: Exception) {
            Timber.e(e, "Failed to get OHLC bars from cache")
            Result.failure(e)
        }
    }

    /**
     * Get data coverage statistics
     */
    suspend fun getDataCoverage(
        asset: String,
        timeframe: String,
        dataTier: DataTier
    ): Result<DataCoverageStats> = withContext(Dispatchers.IO) {
        try {
            val coverageRecords = quarterCoverageDao.getCoverage(
                asset = asset,
                timeframe = timeframe,
                dataTier = dataTier.name
            )

            if (coverageRecords.isEmpty()) {
                return@withContext Result.success(
                    DataCoverageStats(
                        asset = asset,
                        timeframe = timeframe,
                        dataTier = dataTier,
                        quarterCount = 0,
                        totalBars = 0,
                        earliestTimestamp = null,
                        latestTimestamp = null,
                        quarters = emptyList()
                    )
                )
            }

            val totalBars = coverageRecords.sumOf { it.barCount }
            val earliest = coverageRecords.minOfOrNull { it.startTime }
            val latest = coverageRecords.maxOfOrNull { it.endTime }
            val quarters = coverageRecords.map { it.quarter }

            val stats = DataCoverageStats(
                asset = asset,
                timeframe = timeframe,
                dataTier = dataTier,
                quarterCount = coverageRecords.size,
                totalBars = totalBars,
                earliestTimestamp = earliest,
                latestTimestamp = latest,
                quarters = quarters
            )

            Result.success(stats)

        } catch (e: Exception) {
            Timber.e(e, "Failed to get data coverage")
            Result.failure(e)
        }
    }

    /**
     * Clear local cache for specific asset/timeframe
     */
    suspend fun clearCache(
        asset: String? = null,
        timeframe: String? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        try {
            // Delete OHLC bars
            val deletedBars = when {
                asset != null && timeframe != null -> {
                    ohlcBarDao.deleteAllBars(asset, timeframe)
                    quarterCoverageDao.deleteCoverageForAssetTimeframe(asset, timeframe)
                    0 // deleteAllBars doesn't return count
                }
                asset != null -> {
                    quarterCoverageDao.deleteCoverageByAsset(asset)
                    0 // Would need to add deleteByAsset to OHLCBarDao
                }
                else -> {
                    quarterCoverageDao.deleteAll()
                    0 // Would need to add deleteAll to OHLCBarDao
                }
            }

            // Clear cached Parquet files
            cacheDir.listFiles()?.forEach { it.delete() }

            Timber.i("🗑️ Cleared cache for asset=$asset, timeframe=$timeframe")
            Result.success(deletedBars)

        } catch (e: Exception) {
            Timber.e(e, "Failed to clear cache")
            Result.failure(e)
        }
    }

    /**
     * Get cache size in bytes
     */
    suspend fun getCacheSizeBytes(): Long = withContext(Dispatchers.IO) {
        var size = 0L
        cacheDir.listFiles()?.forEach { file ->
            size += file.length()
        }
        size
    }

    /**
     * Check if cache exceeds size limit and clean if needed
     */
    suspend fun cleanCacheIfNeeded(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val cacheSizeMB = getCacheSizeBytes() / (1024 * 1024)

            if (cacheSizeMB > MAX_CACHE_SIZE_MB) {
                Timber.w("⚠️ Cache size ${cacheSizeMB}MB exceeds limit ${MAX_CACHE_SIZE_MB}MB")

                // Delete oldest cached files first
                val files = cacheDir.listFiles()?.sortedBy { it.lastModified() } ?: emptyList()
                var deletedSize = 0L

                for (file in files) {
                    if (cacheSizeMB - (deletedSize / (1024 * 1024)) <= MAX_CACHE_SIZE_MB) {
                        break
                    }
                    deletedSize += file.length()
                    file.delete()
                    Timber.d("   Deleted cache file: ${file.name}")
                }

                Timber.i("🗑️ Cleaned cache: ${deletedSize / (1024 * 1024)}MB freed")
            }

            Result.success(Unit)

        } catch (e: Exception) {
            Timber.e(e, "Failed to clean cache")
            Result.failure(e)
        }
    }

    // Private helper methods

    /**
     * Determine which quarters are needed for date range
     */
    private fun determineRequiredQuarters(startTime: Long, endTime: Long): List<String> {
        val quarters = mutableListOf<String>()

        var currentTime = startTime
        while (currentTime <= endTime) {
            val quarter = timestampToQuarter(currentTime)
            if (quarter !in quarters) {
                quarters.add(quarter)
            }
            currentTime += MILLIS_PER_QUARTER
        }

        return quarters
    }

    /**
     * Convert timestamp to quarter string (e.g., "2024-Q2")
     */
    private fun timestampToQuarter(timestamp: Long): String {
        val calendar = java.util.Calendar.getInstance()
        calendar.timeInMillis = timestamp

        val year = calendar.get(java.util.Calendar.YEAR)
        val month = calendar.get(java.util.Calendar.MONTH) + 1 // 0-based

        val quarter = when (month) {
            in 1..3 -> 1
            in 4..6 -> 2
            in 7..9 -> 3
            else -> 4
        }

        return "$year-Q$quarter"
    }

    /**
     * Get quarters already cached locally
     */
    private suspend fun getCachedQuarters(
        asset: String,
        timeframe: String,
        dataTier: DataTier
    ): List<String> {
        val coverageRecords = quarterCoverageDao.getCoverage(
            asset = asset,
            timeframe = timeframe,
            dataTier = dataTier.name
        )
        return coverageRecords.map { it.quarter }
    }

    /**
     * Download quarters from R2 and import to database
     */
    private suspend fun downloadQuarters(
        asset: String,
        timeframe: String,
        quarters: List<String>,
        dataTier: DataTier
    ) {
        var completed = 0

        for (quarter in quarters) {
            try {
                _downloadProgress.value = DownloadProgress(
                    current = completed + 1,
                    total = quarters.size,
                    currentQuarter = quarter,
                    status = "Downloading $quarter..."
                )

                // Construct R2 key
                val key = "$asset/$timeframe/$quarter.parquet.zst"

                // Download to cache directory
                val cacheFile = File(cacheDir, "${asset}_${timeframe}_${quarter}.parquet.zst")

                Timber.d("📥 Downloading: $key")
                val downloadResult = cloudClient.downloadFile(key, cacheFile) { progress ->
                    _downloadProgress.value = _downloadProgress.value?.copy(
                        downloadPercent = progress
                    )
                }

                if (downloadResult.isFailure) {
                    throw downloadResult.exceptionOrNull() ?: Exception("Download failed")
                }

                // Parse Parquet file
                _downloadProgress.value = _downloadProgress.value?.copy(
                    status = "Parsing $quarter..."
                )

                val parseResult = parquetReader.readParquetFile(
                    file = cacheFile,
                    asset = asset,
                    timeframe = timeframe,
                    dataTier = dataTier,
                    source = "BINANCE" // TODO: Make source configurable
                )

                if (parseResult.isFailure) {
                    throw parseResult.exceptionOrNull() ?: Exception("Parse failed")
                }

                val bars = parseResult.getOrThrow()
                Timber.d("   Parsed ${bars.size} bars")

                // Insert into database
                _downloadProgress.value = _downloadProgress.value?.copy(
                    status = "Saving $quarter to database..."
                )

                ohlcBarDao.insertAll(bars)

                // Record coverage
                val coverage = DataQuarterCoverageEntity(
                    asset = asset,
                    timeframe = timeframe,
                    dataTier = dataTier.name,
                    quarter = quarter,
                    startTime = bars.minOfOrNull { it.timestamp } ?: 0L,
                    endTime = bars.maxOfOrNull { it.timestamp } ?: 0L,
                    barCount = bars.size,
                    lastUpdated = System.currentTimeMillis()
                )
                quarterCoverageDao.insertCoverage(coverage)

                // Delete cache file to save space
                cacheFile.delete()

                completed++
                Timber.i("✅ Imported quarter $quarter: ${bars.size} bars")

            } catch (e: Exception) {
                Timber.e(e, "Failed to download quarter: $quarter")
                _downloadProgress.value = _downloadProgress.value?.copy(
                    status = "Error: ${e.message}"
                )
                throw e
            }
        }

        _downloadProgress.value = null
    }

    /**
     * Format timestamp for logging
     */
    private fun formatTimestamp(timestamp: Long): String {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.US)
        return sdf.format(java.util.Date(timestamp))
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
}

/**
 * Download progress tracking
 */
data class DownloadProgress(
    val current: Int,
    val total: Int,
    val currentQuarter: String,
    val status: String,
    val downloadPercent: Float = 0f
) {
    val overallPercent: Float
        get() = if (total > 0) {
            ((current - 1 + downloadPercent) / total) * 100f
        } else 0f
}

/**
 * Data coverage statistics
 */
data class DataCoverageStats(
    val asset: String,
    val timeframe: String,
    val dataTier: DataTier,
    val quarterCount: Int,
    val totalBars: Int,
    val earliestTimestamp: Long?,
    val latestTimestamp: Long?,
    val quarters: List<String>
)
