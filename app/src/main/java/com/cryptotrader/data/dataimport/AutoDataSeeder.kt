package com.cryptotrader.data.dataimport

import com.cryptotrader.data.local.dao.OHLCBarDao
import com.cryptotrader.domain.model.DataTier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Auto Data Seeder - Automatically seeds historical data after database wipe
 *
 * Monitors database state and auto-imports data when empty:
 * 1. Checks if database has OHLC data
 * 2. If empty, scans for available data files
 * 3. Auto-imports highest-tier data first (TIER_1_PREMIUM → TIER_4_BASIC)
 * 4. Limits initial seed to reasonable amount (e.g., last 30 days)
 *
 * USAGE:
 * - Automatically runs on app startup via AppModule
 * - Can be triggered manually via seedIfNeeded()
 */
@Singleton
class AutoDataSeeder @Inject constructor(
    private val ohlcBarDao: OHLCBarDao,
    private val batchDataImporter: BatchDataImporter
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var hasSeeded = false

    companion object {
        private const val MIN_BARS_THRESHOLD = 100  // Consider database "empty" if < 100 bars
        private const val MAX_SEED_FILES = 5  // Limit initial seed to 5 files max
        private const val SEED_PRIORITY_ASSET = "XXBTZUSD"  // Prioritize BTC data
    }

    /**
     * Check if database needs seeding and auto-import if needed
     *
     * Runs asynchronously and doesn't block app startup
     */
    fun seedIfNeeded() {
        if (hasSeeded) {
            Timber.d("Auto-seeding already completed in this session")
            return
        }

        scope.launch {
            try {
                Timber.i("🌱 Auto Data Seeder: Checking database state...")

                // Check if database has enough data
                val barCount = ohlcBarDao.getBarCount()
                Timber.i("   Current database: $barCount OHLC bars")

                if (barCount >= MIN_BARS_THRESHOLD) {
                    Timber.i("   ✅ Database has sufficient data ($barCount bars), no seeding needed")
                    hasSeeded = true
                    return@launch
                }

                Timber.w("   ⚠️ Database is empty or has insufficient data ($barCount bars)")
                Timber.i("   🔍 Scanning for available data files to seed...")

                // Scan for available data files
                val availableFiles = batchDataImporter.scanAvailableData()

                if (availableFiles.isEmpty()) {
                    Timber.w("   ❌ No data files found for seeding")
                    Timber.w("   Please place data files in:")
                    Timber.w("   - ${BatchDataImporter.CRYPTO_LAKE_OHLCV_DIR}")
                    Timber.w("   - ${BatchDataImporter.BINANCE_RAW_DIR}")
                    hasSeeded = true  // Don't retry
                    return@launch
                }

                Timber.i("   📊 Found ${availableFiles.size} importable files")

                // Prioritize files for seeding:
                // 1. Highest data tier first (TIER_1_PREMIUM > TIER_2_PROFESSIONAL > etc.)
                // 2. Prefer XXBTZUSD (Bitcoin) if available
                // 3. Most recent data first
                val prioritizedFiles = availableFiles
                    .sortedWith(
                        compareBy<ParsedDataFile> { it.dataTier.ordinal }  // Tier 1 = 0 (first)
                            .thenByDescending { it.asset == SEED_PRIORITY_ASSET }  // BTC first
                            .thenByDescending { it.startDate }  // Most recent first
                    )
                    .take(MAX_SEED_FILES)

                Timber.i("   📥 Auto-importing ${prioritizedFiles.size} files...")
                prioritizedFiles.forEachIndexed { index, file ->
                    Timber.i("   ${index + 1}. ${file.fileName} (${file.dataTier.tierName}, ${file.asset})")
                }

                // Import selected files
                var totalImported = 0L
                var successCount = 0

                batchDataImporter.importBatch(prioritizedFiles).collect { progress ->
                    if (progress.isComplete) {
                        totalImported = progress.totalBarsImported
                        successCount = progress.successCount
                    }
                }

                Timber.i("   ✅ Auto-seeding complete!")
                Timber.i("   Imported $totalImported bars from $successCount files")

                // Verify seeding
                val newBarCount = ohlcBarDao.getBarCount()
                Timber.i("   Database now contains: $newBarCount OHLC bars")

                hasSeeded = true

            } catch (e: Exception) {
                Timber.e(e, "Auto-seeding failed: ${e.message}")
                hasSeeded = true  // Don't retry on error
            }
        }
    }

    /**
     * Force re-seeding (even if already seeded in this session)
     */
    fun forceSeed() {
        hasSeeded = false
        seedIfNeeded()
    }

    /**
     * Check if seeding has been completed
     */
    fun hasCompletedSeeding(): Boolean = hasSeeded
}
