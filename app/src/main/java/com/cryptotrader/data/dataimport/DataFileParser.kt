package com.cryptotrader.data.dataimport

import com.cryptotrader.domain.model.DataTier
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Data File Parser - Parse CryptoLake file naming conventions
 *
 * Supported formats:
 * - BINANCE_BTCUSDT_20240501_20240503_ohlcv_1min.parquet
 * - BINANCE_BTCUSDT_20240501_20240503_ohlcv_1h.csv
 * - crypto_lake_BTCUSD_trades_2024_01.parquet
 *
 * Extracts:
 * - Exchange (BINANCE, crypto_lake)
 * - Asset (BTCUSDT, BTCUSD)
 * - Start/End dates
 * - Data type (ohlcv, trades, book)
 * - Timeframe (1min, 5min, 1h, etc.)
 */
object DataFileParser {

    /**
     * Parse data file to extract metadata
     */
    fun parseFile(file: File): ParsedDataFile? {
        return try {
            val filename = file.nameWithoutExtension
            val extension = file.extension.lowercase()

            // Detect file format
            when {
                filename.startsWith("BINANCE_", ignoreCase = true) -> parseBinanceFormat(file, filename, extension)
                filename.contains("crypto_lake", ignoreCase = true) -> parseCryptoLakeFormat(file, filename, extension)
                else -> {
                    Timber.w("Unknown file format: $filename")
                    null
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to parse file: ${file.name}")
            null
        }
    }

    /**
     * Parse Binance format: BINANCE_BTCUSDT_20240501_20240503_ohlcv_1min.parquet
     */
    private fun parseBinanceFormat(file: File, filename: String, extension: String): ParsedDataFile? {
        val parts = filename.split("_")
        if (parts.size < 6) {
            Timber.w("Invalid Binance format: $filename")
            return null
        }

        val exchange = parts[0] // BINANCE
        val asset = parts[1]    // BTCUSDT -> normalize to XXBTZUSD format if needed
        val startDate = parseDate(parts[2]) // 20240501
        val endDate = parseDate(parts[3])   // 20240503
        val dataType = parts[4] // ohlcv, trades, book
        val timeframe = parts[5] // 1min, 5min, 1h

        val normalizedAsset = normalizeAsset(asset)
        val normalizedTimeframe = normalizeTimeframe(timeframe)
        val dataTier = detectDataTier(dataType, exchange)

        return ParsedDataFile(
            file = file,
            exchange = exchange,
            asset = normalizedAsset,
            dataType = dataType,
            timeframe = normalizedTimeframe,
            startDate = startDate,
            endDate = endDate,
            dataTier = dataTier,
            format = extension,
            sizeBytes = file.length()
        )
    }

    /**
     * Parse crypto_lake format: crypto_lake_BTCUSD_trades_2024_01.parquet
     */
    private fun parseCryptoLakeFormat(file: File, filename: String, extension: String): ParsedDataFile? {
        // TODO: Implement when we have crypto_lake files in this format
        Timber.w("crypto_lake format parsing not yet implemented")
        return null
    }

    /**
     * Normalize asset name to Kraken format (XXBTZUSD)
     */
    private fun normalizeAsset(asset: String): String {
        return when (asset.uppercase()) {
            "BTCUSDT", "BTCUSD" -> "XXBTZUSD"
            "ETHUSDT", "ETHUSD" -> "XETHZUSD"
            "SOLUSDT", "SOLUSD" -> "SOLUSD"
            else -> asset // Keep as-is if unknown
        }
    }

    /**
     * Normalize timeframe to standard format
     */
    private fun normalizeTimeframe(timeframe: String): String {
        return when (timeframe.lowercase()) {
            "1min" -> "1m"
            "5min" -> "5m"
            "15min" -> "15m"
            "1hour", "1h" -> "1h"
            "4hour", "4h" -> "4h"
            "1day", "1d" -> "1d"
            else -> timeframe
        }
    }

    /**
     * Detect data tier based on data type and source
     */
    private fun detectDataTier(dataType: String, exchange: String): DataTier {
        return when {
            dataType.equals("book", ignoreCase = true) -> DataTier.TIER_1_PREMIUM
            dataType.equals("trades", ignoreCase = true) && exchange.contains("crypto_lake", ignoreCase = true) -> DataTier.TIER_2_PROFESSIONAL
            dataType.equals("aggTrades", ignoreCase = true) || exchange.equals("BINANCE", ignoreCase = true) -> DataTier.TIER_3_STANDARD
            dataType.equals("ohlcv", ignoreCase = true) || dataType.equals("candles", ignoreCase = true) -> DataTier.TIER_4_BASIC
            else -> DataTier.TIER_4_BASIC
        }
    }

    /**
     * Parse date string (20240501) to timestamp
     */
    private fun parseDate(dateStr: String): Long {
        return try {
            val format = SimpleDateFormat("yyyyMMdd", Locale.US)
            format.parse(dateStr)?.time ?: 0L
        } catch (e: Exception) {
            Timber.w("Failed to parse date: $dateStr")
            0L
        }
    }
}

/**
 * Parsed data file metadata
 */
data class ParsedDataFile(
    val file: File,
    val exchange: String,
    val asset: String,
    val dataType: String,
    val timeframe: String,
    val startDate: Long,
    val endDate: Long,
    val dataTier: DataTier,
    val format: String, // "csv", "parquet"
    val sizeBytes: Long
) {
    val fileName: String get() = file.name
    val filePath: String get() = file.absolutePath

    override fun toString(): String {
        return "ParsedDataFile(asset=$asset, timeframe=$timeframe, tier=${dataTier.tierName}, format=$format, size=${sizeBytes / 1024}KB)"
    }
}
