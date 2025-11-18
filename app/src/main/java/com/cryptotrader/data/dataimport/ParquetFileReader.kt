package com.cryptotrader.data.dataimport

import com.cryptotrader.data.local.entities.OHLCBarEntity
import com.cryptotrader.domain.model.DataTier
import com.github.luben.zstd.ZstdInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.arrow.memory.RootAllocator
import org.apache.arrow.vector.BigIntVector
import org.apache.arrow.vector.Float8Vector
import org.apache.arrow.vector.IntVector
import org.apache.arrow.vector.VectorSchemaRoot
import org.apache.arrow.vector.ipc.ArrowFileReader
import org.apache.arrow.vector.ipc.SeekableReadChannel
import org.apache.arrow.vector.util.ByteArrayReadableSeekableByteChannel
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parquet File Reader using Apache Arrow
 *
 * Reads Parquet files containing OHLC data and converts to OHLCBarEntity.
 * Supports Zstandard (.zst) compressed files.
 *
 * Schema expected:
 * - timestamp: int64
 * - open: float64
 * - high: float64
 * - low: float64
 * - close: float64
 * - volume: float64
 * - trades: int32 (optional)
 */
@Singleton
class ParquetFileReader @Inject constructor() {

    companion object {
        private const val BUFFER_SIZE = 8192
    }

    /**
     * Read Parquet file and convert to OHLCBarEntity list
     *
     * @param file Parquet file (can be .parquet or .parquet.zst)
     * @param asset Asset symbol (e.g., "XXBTZUSD")
     * @param timeframe Timeframe (e.g., "1h")
     * @param dataTier Data tier for classification
     * @return List of OHLC bars
     */
    suspend fun readParquetFile(
        file: File,
        asset: String,
        timeframe: String,
        dataTier: DataTier,
        source: String = "BINANCE"
    ): Result<List<OHLCBarEntity>> = withContext(Dispatchers.IO) {
        try {
            Timber.d("📖 Reading Parquet file: ${file.name}")

            // Check if file is compressed
            val isCompressed = file.name.endsWith(".zst") || file.name.endsWith(".zstd")

            // Decompress if needed
            val dataBytes = if (isCompressed) {
                Timber.d("   Decompressing Zstandard...")
                decompressZstd(file)
            } else {
                file.readBytes()
            }

            Timber.d("   Data size: ${dataBytes.size / 1024}KB")

            // Read Parquet using Arrow
            val bars = readArrowParquet(dataBytes, asset, timeframe, dataTier, source)

            Timber.i("✅ Read ${bars.size} bars from ${file.name}")
            Result.success(bars)

        } catch (e: Exception) {
            Timber.e(e, "Failed to read Parquet file: ${file.name}")
            Result.failure(e)
        }
    }

    /**
     * Decompress Zstandard-compressed file
     */
    private fun decompressZstd(file: File): ByteArray {
        FileInputStream(file).use { fis ->
            BufferedInputStream(fis, BUFFER_SIZE).use { bis ->
                ZstdInputStream(bis).use { zis ->
                    return zis.readBytes()
                }
            }
        }
    }

    /**
     * Read Parquet data using Apache Arrow
     */
    private fun readArrowParquet(
        data: ByteArray,
        asset: String,
        timeframe: String,
        dataTier: DataTier,
        source: String
    ): List<OHLCBarEntity> {
        val allocator = RootAllocator(Long.MAX_VALUE)
        val bars = mutableListOf<OHLCBarEntity>()

        try {
            // Create seekable channel from byte array
            val channel = ByteArrayReadableSeekableByteChannel(data)
            val seekableChannel = SeekableReadChannel(channel)

            // Create Arrow file reader
            ArrowFileReader(seekableChannel, allocator).use { reader ->
                // Get schema
                val schema = reader.vectorSchemaRoot.schema
                Timber.d("   Parquet schema: ${schema.fields.map { it.name }}")

                // Validate schema
                validateSchema(schema)

                // Read all batches
                while (reader.loadNextBatch()) {
                    val root = reader.vectorSchemaRoot
                    val rowCount = root.rowCount

                    // Get column vectors
                    val timestampVector = root.getVector("timestamp") as BigIntVector
                    val openVector = root.getVector("open") as Float8Vector
                    val highVector = root.getVector("high") as Float8Vector
                    val lowVector = root.getVector("low") as Float8Vector
                    val closeVector = root.getVector("close") as Float8Vector
                    val volumeVector = root.getVector("volume") as Float8Vector

                    // Trades column is optional
                    val tradesVector = try {
                        root.getVector("trades") as? IntVector
                    } catch (e: Exception) {
                        null
                    }

                    // Convert to entities
                    for (i in 0 until rowCount) {
                        if (!timestampVector.isNull(i)) {
                            val bar = OHLCBarEntity(
                                asset = asset,
                                timeframe = timeframe,
                                timestamp = timestampVector.get(i),
                                open = openVector.get(i),
                                high = highVector.get(i),
                                low = lowVector.get(i),
                                close = closeVector.get(i),
                                volume = volumeVector.get(i),
                                trades = tradesVector?.get(i) ?: 0,
                                source = source,
                                dataTier = dataTier.name,
                                importedAt = System.currentTimeMillis()
                            )
                            bars.add(bar)
                        }
                    }
                }
            }

        } finally {
            allocator.close()
        }

        return bars
    }

    /**
     * Validate Parquet schema has required columns
     */
    private fun validateSchema(schema: org.apache.arrow.vector.types.pojo.Schema) {
        val fieldNames = schema.fields.map { it.name.lowercase() }

        val requiredFields = listOf("timestamp", "open", "high", "low", "close", "volume")

        requiredFields.forEach { field ->
            if (!fieldNames.contains(field)) {
                throw IllegalArgumentException("Missing required field: $field")
            }
        }
    }

    /**
     * Get Parquet file statistics without reading all data
     */
    suspend fun getFileStats(file: File): Result<ParquetFileStats> = withContext(Dispatchers.IO) {
        try {
            // Decompress if needed
            val isCompressed = file.name.endsWith(".zst") || file.name.endsWith(".zstd")
            val dataBytes = if (isCompressed) {
                decompressZstd(file)
            } else {
                file.readBytes()
            }

            val allocator = RootAllocator(Long.MAX_VALUE)

            try {
                val channel = ByteArrayReadableSeekableByteChannel(dataBytes)
                val seekableChannel = SeekableReadChannel(channel)

                ArrowFileReader(seekableChannel, allocator).use { reader ->
                    val schema = reader.vectorSchemaRoot.schema

                    var totalRows = 0
                    while (reader.loadNextBatch()) {
                        totalRows += reader.vectorSchemaRoot.rowCount
                    }

                    val stats = ParquetFileStats(
                        rowCount = totalRows,
                        columnCount = schema.fields.size,
                        columnNames = schema.fields.map { it.name },
                        compressedSizeBytes = file.length(),
                        uncompressedSizeBytes = dataBytes.size.toLong()
                    )

                    Result.success(stats)
                }
            } finally {
                allocator.close()
            }

        } catch (e: Exception) {
            Timber.e(e, "Failed to get Parquet file stats: ${file.name}")
            Result.failure(e)
        }
    }
}

/**
 * Parquet file statistics
 */
data class ParquetFileStats(
    val rowCount: Int,
    val columnCount: Int,
    val columnNames: List<String>,
    val compressedSizeBytes: Long,
    val uncompressedSizeBytes: Long
) {
    val compressionRatio: Float
        get() = if (compressedSizeBytes > 0) {
            uncompressedSizeBytes.toFloat() / compressedSizeBytes.toFloat()
        } else 1.0f
}
