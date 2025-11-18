package com.cryptotrader.data.repository

import android.content.Context
import com.cryptotrader.data.local.dao.*
import com.cryptotrader.data.local.entities.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data Import Repository - Imports historical data from CSV/Parquet files
 *
 * Supports:
 * - CSV import (raw trades from CryptoLake)
 * - Parquet import (OHLCV and signals from CryptoLake)
 * - Batch processing with progress tracking
 * - Data validation during import
 * - Duplicate detection
 *
 * SKELETON IMPLEMENTATION - Parquet parsing requires Apache Arrow library (not yet added)
 */
@Singleton
class DataImportRepository @Inject constructor(
    private val ohlcBarDao: OHLCBarDao,
    private val technicalIndicatorDao: TechnicalIndicatorDao,
    private val dataCoverageDao: DataCoverageDao,
    private val dataQualityDao: DataQualityDao,
    @ApplicationContext private val context: Context
) {

    companion object {
        const val BATCH_SIZE = 10000 // Import in batches of 10k rows

        /**
         * Default data directory - LOCAL storage (no external G:\ dependency)
         */
        const val DEFAULT_DATA_PATH = "D:\\Development\\Projects\\Mobile\\Android\\CryptoTrader\\data"

        /**
         * crypto_lake subdirectory - PRIORITY hedge fund quality data
         */
        const val CRYPTO_LAKE_PATH = "$DEFAULT_DATA_PATH\\crypto_lake"

        /**
         * binance_raw subdirectory - historical reference data
         */
        const val BINANCE_RAW_PATH = "$DEFAULT_DATA_PATH\\binance_raw"
    }

    /**
     * Import OHLCV data from CSV file
     *
     * CSV format expected:
     * timestamp,open,high,low,close,volume,trades
     */
    suspend fun importOHLCFromCSV(
        filePath: String,
        asset: String,
        timeframe: String
    ): Flow<ImportProgress> = flow {
        withContext(Dispatchers.IO) {
            try {
                emit(ImportProgress(status = ImportStatus.STARTED, message = "Reading file: $filePath"))

                val file = File(filePath)
                if (!file.exists()) {
                    emit(ImportProgress(status = ImportStatus.FAILED, message = "File not found: $filePath"))
                    return@withContext
                }

                val lines = file.readLines()
                val totalLines = lines.size - 1 // Exclude header
                val header = lines.firstOrNull()

                if (header == null || totalLines == 0) {
                    emit(ImportProgress(status = ImportStatus.FAILED, message = "File is empty"))
                    return@withContext
                }

                emit(ImportProgress(
                    status = ImportStatus.IN_PROGRESS,
                    message = "Parsing $totalLines rows",
                    progress = 0f,
                    totalRows = totalLines.toLong()
                ))

                // Parse and import in batches
                val bars = mutableListOf<OHLCBarEntity>()
                var processedRows = 0

                lines.drop(1).forEach { line ->
                    try {
                        val parts = line.split(",")
                        if (parts.size >= 6) {
                            val bar = OHLCBarEntity(
                                asset = asset,
                                timeframe = timeframe,
                                timestamp = parts[0].toLong(),
                                open = parts[1].toDouble(),
                                high = parts[2].toDouble(),
                                low = parts[3].toDouble(),
                                close = parts[4].toDouble(),
                                volume = parts[5].toDouble(),
                                trades = parts.getOrNull(6)?.toIntOrNull() ?: 0,
                                source = "CRYPTOLAKE_CSV",
                                importedAt = System.currentTimeMillis()
                            )
                            bars.add(bar)
                        }

                        // Insert batch when reaching BATCH_SIZE
                        if (bars.size >= BATCH_SIZE) {
                            ohlcBarDao.insertAll(bars)
                            processedRows += bars.size
                            val progress = (processedRows.toFloat() / totalLines.toFloat())
                            emit(ImportProgress(
                                status = ImportStatus.IN_PROGRESS,
                                message = "Imported $processedRows / $totalLines rows",
                                progress = progress,
                                importedRows = processedRows.toLong(),
                                totalRows = totalLines.toLong()
                            ))
                            bars.clear()
                        }
                    } catch (e: Exception) {
                        Timber.w("Failed to parse line: $line - ${e.message}")
                    }
                }

                // Insert remaining bars
                if (bars.isNotEmpty()) {
                    ohlcBarDao.insertAll(bars)
                    processedRows += bars.size
                }

                // Update data coverage
                updateDataCoverage(asset, timeframe, "CRYPTOLAKE_CSV")

                emit(ImportProgress(
                    status = ImportStatus.COMPLETED,
                    message = "Successfully imported $processedRows rows",
                    progress = 1f,
                    importedRows = processedRows.toLong(),
                    totalRows = totalLines.toLong()
                ))

            } catch (e: Exception) {
                Timber.e(e, "Error importing CSV")
                emit(ImportProgress(status = ImportStatus.FAILED, message = "Import failed: ${e.message}"))
            }
        }
    }

    /**
     * Import OHLCV data from Parquet file
     *
     * TODO: Requires Apache Arrow library for Parquet parsing
     * This is a skeleton implementation that will be filled in later
     */
    suspend fun importOHLCFromParquet(
        filePath: String,
        asset: String,
        timeframe: String
    ): Flow<ImportProgress> = flow {
        emit(ImportProgress(
            status = ImportStatus.FAILED,
            message = "Parquet import not yet implemented. Requires Apache Arrow library."
        ))
        // TODO: Implement Parquet parsing when Apache Arrow is added to dependencies
    }

    /**
     * Import technical indicators from Parquet file
     *
     * TODO: Requires Apache Arrow library for Parquet parsing
     */
    suspend fun importIndicatorsFromParquet(
        filePath: String,
        asset: String,
        timeframe: String
    ): Flow<ImportProgress> = flow {
        emit(ImportProgress(
            status = ImportStatus.FAILED,
            message = "Indicator import not yet implemented. Requires Apache Arrow library."
        ))
        // TODO: Implement when Apache Arrow is added
    }

    /**
     * Update data coverage after import
     */
    private suspend fun updateDataCoverage(asset: String, timeframe: String, source: String) {
        try {
            val range = ohlcBarDao.getTimestampRange(asset, timeframe)
            val barCount = ohlcBarDao.getBarCount(asset, timeframe)

            if (range?.earliest != null && range.latest != null) {
                // Calculate expected bars based on timeframe
                val timeframeMs = getTimeframeMillis(timeframe)
                val expectedBars = ((range.latest - range.earliest) / timeframeMs) + 1

                val coverage = DataCoverageEntity(
                    asset = asset,
                    timeframe = timeframe,
                    earliestTimestamp = range.earliest,
                    latestTimestamp = range.latest,
                    totalBars = barCount,
                    expectedBars = expectedBars,
                    dataQualityScore = (barCount.toDouble() / expectedBars.toDouble()).coerceIn(0.0, 1.0),
                    gapsCount = 0, // TODO: Implement gap detection
                    missingBarsCount = expectedBars - barCount,
                    primarySource = source,
                    sources = """["$source"]""",
                    lastUpdated = System.currentTimeMillis(),
                    lastImportedAt = System.currentTimeMillis()
                )

                dataCoverageDao.insert(coverage)
                Timber.i("📊 Data coverage updated: $asset $timeframe - ${barCount} bars")
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to update data coverage")
        }
    }

    /**
     * Get timeframe duration in milliseconds
     */
    private fun getTimeframeMillis(timeframe: String): Long {
        return when (timeframe) {
            "1m" -> 60_000L
            "5m" -> 300_000L
            "15m" -> 900_000L
            "1h" -> 3_600_000L
            "4h" -> 14_400_000L
            "1d" -> 86_400_000L
            else -> 60_000L // Default to 1 minute
        }
    }

    /**
     * Scan directory for available data files
     *
     * Defaults to local data directory: D:\...\CryptoTrader\data
     * Supports both crypto_lake (order book, trades) and binance_raw (aggTrades) formats
     *
     * @param directoryPath Path to scan (defaults to DEFAULT_DATA_PATH)
     */
    suspend fun scanDataDirectory(directoryPath: String = DEFAULT_DATA_PATH): List<DataFileInfo> {
        return withContext(Dispatchers.IO) {
            val files = mutableListOf<DataFileInfo>()
            try {
                val directory = File(directoryPath)
                if (!directory.exists() || !directory.isDirectory) {
                    Timber.w("Directory not found: $directoryPath")
                    return@withContext emptyList()
                }

                // Recursively scan for CSV and Parquet files
                directory.walkTopDown().forEach { file ->
                    if (file.isFile) {
                        when (file.extension.lowercase()) {
                            "csv" -> {
                                // Try to parse asset and timeframe from filename
                                val info = parseFileInfo(file, "csv")
                                if (info != null) files.add(info)
                            }
                            "parquet" -> {
                                val info = parseFileInfo(file, "parquet")
                                if (info != null) files.add(info)
                            }
                        }
                    }
                }

                Timber.i("📁 Found ${files.size} data files in $directoryPath")
            } catch (e: Exception) {
                Timber.e(e, "Error scanning directory")
            }
            files
        }
    }

    /**
     * Parse file information from filename
     */
    private fun parseFileInfo(file: File, format: String): DataFileInfo? {
        try {
            // TODO: Implement filename parsing based on CryptoLake naming conventions
            // Example: BTCUSD_1h_2023_01.parquet
            return DataFileInfo(
                filePath = file.absolutePath,
                fileName = file.name,
                format = format,
                asset = "UNKNOWN", // Parse from filename
                timeframe = "UNKNOWN", // Parse from filename
                sizeBytes = file.length(),
                lastModified = file.lastModified()
            )
        } catch (e: Exception) {
            return null
        }
    }
}

/**
 * Import progress tracking
 */
data class ImportProgress(
    val status: ImportStatus,
    val message: String,
    val progress: Float = 0f, // 0.0 to 1.0
    val importedRows: Long = 0,
    val totalRows: Long = 0
)

enum class ImportStatus {
    STARTED,
    IN_PROGRESS,
    COMPLETED,
    FAILED
}

/**
 * Data file information
 */
data class DataFileInfo(
    val filePath: String,
    val fileName: String,
    val format: String, // "csv" or "parquet"
    val asset: String,
    val timeframe: String,
    val sizeBytes: Long,
    val lastModified: Long
)
