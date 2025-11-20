package com.cryptotrader.data.dataimport

import com.cryptotrader.data.local.dao.DataCoverageDao
import com.cryptotrader.data.local.dao.OHLCBarDao
import com.cryptotrader.data.local.entities.OHLCBarEntity
import com.cryptotrader.data.repository.DataImportRepository
import com.cryptotrader.domain.model.DataTier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Batch Data Importer - Scan and import historical data files
 *
 * Scans data directories and imports:
 * - Parquet files from crypto_lake
 * - CSV files from various sources
 * - Automatically detects data tier and timeframes
 *
 * SKELETON IMPLEMENTATION:
 * - CSV import fully functional
 * - Parquet import requires Apache Arrow (skeleton for now)
 */
@Singleton
class BatchDataImporter @Inject constructor(
    private val ohlcBarDao: OHLCBarDao,
    private val dataCoverageDao: DataCoverageDao,
    private val dataImportRepository: DataImportRepository
) {

    companion object {
        const val DEFAULT_DATA_DIR = "D:\\Development\\Projects\\Mobile\\Android\\CryptoTrader\\data"
        const val CRYPTO_LAKE_OHLCV_DIR = "$DEFAULT_DATA_DIR\\crypto_lake_ohlcv"
        const val BINANCE_RAW_DIR = "$DEFAULT_DATA_DIR\\binance_raw"
        private const val BITCOIN_GENESIS_TIME = 1231006505000L  // January 3, 2009
        private const val MAX_PRICE_RANGE_PERCENT = 50.0  // Warn if candle has >50% range
    }

    /**
     * Validate OHLC data integrity (same validation as HistoricalDataRepository)
     *
     * Checks:
     * 1. All prices > 0
     * 2. Volume >= 0
     * 3. Low <= High
     * 4. Low <= Open <= High
     * 5. Low <= Close <= High
     * 6. Timestamp not in future
     * 7. Timestamp after Bitcoin genesis
     * 8. Detect extreme price spikes
     */
    private fun validateOHLC(
        timestamp: Long,
        open: Double,
        high: Double,
        low: Double,
        close: Double,
        volume: Double,
        asset: String
    ): Boolean {
        val now = System.currentTimeMillis()

        // Check 1: All prices must be positive
        if (open <= 0 || high <= 0 || low <= 0 || close <= 0) {
            return false
        }

        // Check 2: Volume must be non-negative
        if (volume < 0) {
            return false
        }

        // Check 3: Low must be <= High
        if (low > high) {
            return false
        }

        // Check 4: Open must be between Low and High
        if (open < low || open > high) {
            return false
        }

        // Check 5: Close must be between Low and High
        if (close < low || close > high) {
            return false
        }

        // Check 6: Timestamp must not be in the future
        if (timestamp > now + (60 * 60 * 1000)) {  // Allow 1 hour tolerance
            return false
        }

        // Check 7: Timestamp must not be too old
        if (timestamp < BITCOIN_GENESIS_TIME) {
            return false
        }

        // Check 8: Detect price spikes (optional - log warning only)
        val priceRange = high - low
        val avgPrice = (high + low) / 2.0
        val rangePercent = (priceRange / avgPrice) * 100.0

        if (rangePercent > MAX_PRICE_RANGE_PERCENT) {
            Timber.w("Suspicious OHLC for $asset: ${String.format("%.2f%%", rangePercent)} range in single candle")
            // Don't reject - could be real volatility
        }

        return true
    }

    /**
     * Scan data directories and find all importable files
     */
    suspend fun scanAvailableData(): List<ParsedDataFile> = withContext(Dispatchers.IO) {
        val files = mutableListOf<ParsedDataFile>()

        try {
            // Scan crypto_lake_ohlcv directory
            val cryptoLakeDir = File(CRYPTO_LAKE_OHLCV_DIR)
            if (cryptoLakeDir.exists() && cryptoLakeDir.isDirectory) {
                Timber.i("Scanning $CRYPTO_LAKE_OHLCV_DIR")
                cryptoLakeDir.listFiles()?.forEach { file ->
                    if (file.isFile && (file.extension == "parquet" || file.extension == "csv")) {
                        val parsed = DataFileParser.parseFile(file)
                        if (parsed != null) {
                            files.add(parsed)
                        }
                    }
                }
            } else {
                Timber.w("crypto_lake_ohlcv directory not found: $CRYPTO_LAKE_OHLCV_DIR")
            }

            // Scan binance_raw directory
            val binanceDir = File(BINANCE_RAW_DIR)
            if (binanceDir.exists() && binanceDir.isDirectory) {
                Timber.i("Scanning $BINANCE_RAW_DIR")
                binanceDir.listFiles()?.forEach { file ->
                    if (file.isFile && (file.extension == "parquet" || file.extension == "csv")) {
                        val parsed = DataFileParser.parseFile(file)
                        if (parsed != null) {
                            files.add(parsed)
                        }
                    }
                }
            }

            Timber.i("Found ${files.size} importable data files")
            files.groupBy { it.dataTier }.forEach { (tier, tierFiles) ->
                Timber.i("  ${tier.tierName}: ${tierFiles.size} files")
            }

        } catch (e: Exception) {
            Timber.e(e, "Error scanning data directories")
        }

        files.sortedBy { it.startDate }
    }

    /**
     * Import a single data file
     */
    suspend fun importFile(parsedFile: ParsedDataFile): Flow<ImportProgress> = flow {
        try {
            emit(ImportProgress.started("Importing ${parsedFile.fileName}"))

            when (parsedFile.format) {
                "csv" -> importCsvFile(parsedFile).collect { emit(it) }
                "parquet" -> importParquetFile(parsedFile).collect { emit(it) }
                else -> emit(ImportProgress.failed("Unsupported format: ${parsedFile.format}"))
            }

        } catch (e: Exception) {
            Timber.e(e, "Import failed: ${parsedFile.fileName}")
            emit(ImportProgress.failed("Import error: ${e.message}"))
        }
    }

    /**
     * Import CSV file with comprehensive validation
     */
    private suspend fun importCsvFile(parsedFile: ParsedDataFile): Flow<ImportProgress> = flow {
        emit(ImportProgress.inProgress("Reading CSV: ${parsedFile.fileName}", 0f))

        try {
            val file = parsedFile.file
            val lines = file.readLines()

            if (lines.isEmpty()) {
                emit(ImportProgress.failed("Empty file"))
                return@flow
            }

            val header = lines.first()
            val dataLines = lines.drop(1)

            Timber.i("CSV header: $header")
            Timber.i("CSV rows: ${dataLines.size}")

            // Detect CSV format and parse with validation
            val bars = mutableListOf<OHLCBarEntity>()
            var processedRows = 0
            var invalidRows = 0

            dataLines.forEach { line ->
                try {
                    val parts = line.split(",")
                    if (parts.size >= 6) {
                        // Parse values with strict validation
                        val timestamp = parts[0].toLongOrNull()
                        val open = parts[1].toDoubleOrNull()
                        val high = parts[2].toDoubleOrNull()
                        val low = parts[3].toDoubleOrNull()
                        val close = parts[4].toDoubleOrNull()
                        val volume = parts[5].toDoubleOrNull()

                        // Validate all values are non-null and pass OHLC validation
                        if (timestamp != null && open != null && high != null &&
                            low != null && close != null && volume != null &&
                            validateOHLC(timestamp, open, high, low, close, volume, parsedFile.asset)) {

                            val bar = OHLCBarEntity(
                                asset = parsedFile.asset,
                                timeframe = parsedFile.timeframe,
                                timestamp = timestamp,
                                open = open,
                                high = high,
                                low = low,
                                close = close,
                                volume = volume,
                                trades = parts.getOrNull(6)?.toIntOrNull() ?: 0,
                                source = parsedFile.exchange,
                                dataTier = parsedFile.dataTier.name,
                                importedAt = System.currentTimeMillis()
                            )
                            bars.add(bar)
                        } else {
                            invalidRows++
                            if (invalidRows <= 5) {
                                Timber.w("Invalid CSV row (showing first 5): $line")
                            }
                        }
                    } else {
                        invalidRows++
                    }

                    // Batch insert every 1000 rows
                    if (bars.size >= 1000) {
                        ohlcBarDao.insertAll(bars)
                        processedRows += bars.size
                        val progress = processedRows.toFloat() / dataLines.size.toFloat()
                        emit(ImportProgress.inProgress("Imported $processedRows / ${dataLines.size} rows", progress))
                        bars.clear()
                    }

                } catch (e: Exception) {
                    Timber.w("Failed to parse line: ${e.message}")
                }
            }

            // Insert remaining bars
            if (bars.isNotEmpty()) {
                ohlcBarDao.insertAll(bars)
                processedRows += bars.size
            }

            val totalRows = dataLines.size
            val validationRate = if (totalRows > 0) (processedRows.toDouble() / totalRows * 100) else 0.0

            Timber.i("✅ Imported $processedRows valid bars from ${parsedFile.fileName}")
            Timber.i("   Validation: $processedRows valid, $invalidRows invalid (${String.format("%.1f%%", validationRate)} pass rate)")

            emit(ImportProgress.completed(
                "Imported $processedRows valid bars ($invalidRows invalid filtered)",
                processedRows.toLong()
            ))

        } catch (e: Exception) {
            Timber.e(e, "CSV import failed")
            emit(ImportProgress.failed("CSV import error: ${e.message}"))
        }
    }

    /**
     * Import Parquet file
     *
     * SKELETON IMPLEMENTATION - Requires Apache Arrow library
     * TODO: Add Apache Arrow dependency and implement full Parquet parsing
     */
    private suspend fun importParquetFile(parsedFile: ParsedDataFile): Flow<ImportProgress> = flow {
        emit(ImportProgress.failed(
            "Parquet import not yet implemented. " +
            "Requires Apache Arrow library. " +
            "File: ${parsedFile.fileName}"
        ))

        // TODO: Implement when Apache Arrow is added to dependencies
        // Example structure:
        // 1. Read Parquet file with Arrow
        // 2. Extract schema and validate
        // 3. Read data in batches
        // 4. Convert to OHLCBarEntity
        // 5. Insert to database
    }

    /**
     * Import multiple files in batch
     */
    suspend fun importBatch(files: List<ParsedDataFile>): Flow<BatchImportProgress> = flow {
        var totalImported = 0L
        var successCount = 0
        var failureCount = 0

        files.forEachIndexed { index, file ->
            emit(BatchImportProgress(
                currentFile = file.fileName,
                currentFileIndex = index + 1,
                totalFiles = files.size,
                totalBarsImported = totalImported,
                successCount = successCount,
                failureCount = failureCount,
                isComplete = false
            ))

            importFile(file).collect { progress ->
                when (progress.status) {
                    ImportStatus.COMPLETED -> {
                        totalImported += progress.importedRows
                        successCount++
                    }
                    ImportStatus.FAILED -> {
                        failureCount++
                    }
                    else -> { /* In progress */ }
                }
            }
        }

        emit(BatchImportProgress(
            currentFile = "",
            currentFileIndex = files.size,
            totalFiles = files.size,
            totalBarsImported = totalImported,
            successCount = successCount,
            failureCount = failureCount,
            isComplete = true
        ))
    }
}

/**
 * Import progress (single file)
 */
data class ImportProgress(
    val status: ImportStatus,
    val message: String,
    val progress: Float = 0f,
    val importedRows: Long = 0,
    val totalRows: Long = 0
) {
    companion object {
        fun started(message: String) = ImportProgress(ImportStatus.STARTED, message)
        fun inProgress(message: String, progress: Float) = ImportProgress(ImportStatus.IN_PROGRESS, message, progress)
        fun completed(message: String, importedRows: Long) = ImportProgress(ImportStatus.COMPLETED, message, 1f, importedRows)
        fun failed(message: String) = ImportProgress(ImportStatus.FAILED, message)
    }
}

enum class ImportStatus {
    STARTED,
    IN_PROGRESS,
    COMPLETED,
    FAILED
}

/**
 * Batch import progress
 */
data class BatchImportProgress(
    val currentFile: String,
    val currentFileIndex: Int,
    val totalFiles: Int,
    val totalBarsImported: Long,
    val successCount: Int,
    val failureCount: Int,
    val isComplete: Boolean
)
