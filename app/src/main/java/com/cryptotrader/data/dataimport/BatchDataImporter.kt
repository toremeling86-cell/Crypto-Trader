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
     * Import CSV file
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

            // Detect CSV format and parse
            val bars = mutableListOf<OHLCBarEntity>()
            var processedRows = 0

            dataLines.forEach { line ->
                try {
                    val parts = line.split(",")
                    if (parts.size >= 6) {
                        val bar = OHLCBarEntity(
                            asset = parsedFile.asset,
                            timeframe = parsedFile.timeframe,
                            timestamp = parts[0].toLongOrNull() ?: 0L,
                            open = parts[1].toDoubleOrNull() ?: 0.0,
                            high = parts[2].toDoubleOrNull() ?: 0.0,
                            low = parts[3].toDoubleOrNull() ?: 0.0,
                            close = parts[4].toDoubleOrNull() ?: 0.0,
                            volume = parts[5].toDoubleOrNull() ?: 0.0,
                            trades = parts.getOrNull(6)?.toIntOrNull() ?: 0,
                            source = parsedFile.exchange,
                            dataTier = parsedFile.dataTier.name,
                            importedAt = System.currentTimeMillis()
                        )
                        bars.add(bar)
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

            Timber.i("✅ Imported $processedRows bars from ${parsedFile.fileName}")
            emit(ImportProgress.completed("Imported $processedRows bars", processedRows.toLong()))

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
