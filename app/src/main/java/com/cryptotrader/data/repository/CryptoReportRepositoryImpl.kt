package com.cryptotrader.data.repository

import android.content.Context
import android.os.Environment
import com.cryptotrader.data.local.dao.ExpertReportDao
import com.cryptotrader.data.local.entities.ExpertReportEntity
import com.cryptotrader.domain.model.ExpertReport
import com.cryptotrader.domain.model.ReportCategory
import com.cryptotrader.domain.model.ReportSentiment
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of CryptoReportRepository
 *
 * Manages expert trading reports from /CryptoTrader/ExpertReports/ directory
 * Features:
 * - File monitoring for automatic import of new markdown reports
 * - Markdown parsing and categorization
 * - Badge count for unanalyzed reports
 * - Meta-analysis tracking
 */
@Singleton
class CryptoReportRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val expertReportDao: ExpertReportDao,
    @com.cryptotrader.di.IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : CryptoReportRepository {

    private val json = Json { ignoreUnknownKeys = true }

    // File monitoring
    private var monitoringJob: Job? = null
    private val monitoringScope = CoroutineScope(ioDispatcher)
    private val REPORTS_DIRECTORY_NAME = "ExpertReports" // App-specific directory
    private val MONITORING_INTERVAL_MS = 30_000L // Check every 30 seconds

    // Track imported files to avoid duplicates
    private val importedFiles = mutableSetOf<String>()

    // ==================== Report Operations ====================

    override fun getAllReports(): Flow<List<ExpertReport>> {
        return expertReportDao.getAllReports()
            .map { entities -> entities.map { it.toDomain() } }
            .catch { e ->
                Timber.e(e, "Error getting all reports")
                emit(emptyList())
            }
            .flowOn(ioDispatcher)
    }

    override fun getUnanalyzedReports(): Flow<List<ExpertReport>> {
        return expertReportDao.getUnanalyzedReports()
            .map { entities -> entities.map { it.toDomain() } }
            .catch { e ->
                Timber.e(e, "Error getting unanalyzed reports")
                emit(emptyList())
            }
            .flowOn(ioDispatcher)
    }

    override fun getUnanalyzedReportCount(): Flow<Int> {
        return expertReportDao.getUnanalyzedReportsCount()
            .catch { e ->
                Timber.e(e, "Error getting unanalyzed report count")
                emit(0)
            }
            .flowOn(ioDispatcher)
    }

    override suspend fun getReportById(id: Long): ExpertReport? = withContext(ioDispatcher) {
        try {
            expertReportDao.getReportById(id)?.toDomain()
        } catch (e: Exception) {
            Timber.e(e, "Error getting report by ID: $id")
            null
        }
    }

    override fun getReportsByCategory(category: String): Flow<List<ExpertReport>> {
        return expertReportDao.getReportsByCategory(category)
            .map { entities -> entities.map { it.toDomain() } }
            .catch { e ->
                Timber.e(e, "Error getting reports by category: $category")
                emit(emptyList())
            }
            .flowOn(ioDispatcher)
    }

    override suspend fun insertReport(report: ExpertReport): Long = withContext(ioDispatcher) {
        try {
            val entity = report.toEntity()
            val id = expertReportDao.insertReport(entity)
            Timber.d("Inserted expert report: ${report.title} (ID: $id)")
            id
        } catch (e: Exception) {
            Timber.e(e, "Error inserting report: ${report.title}")
            throw e
        }
    }

    override suspend fun updateReport(report: ExpertReport) = withContext(ioDispatcher) {
        try {
            val entity = report.toEntity()
            expertReportDao.updateReport(entity)
            Timber.d("Updated expert report: ${report.title}")
        } catch (e: Exception) {
            Timber.e(e, "Error updating report: ${report.title}")
            throw e
        }
    }

    override suspend fun deleteReport(reportId: Long) = withContext(ioDispatcher) {
        try {
            expertReportDao.deleteReportById(reportId)
            Timber.d("Deleted report with ID: $reportId")
        } catch (e: Exception) {
            Timber.e(e, "Error deleting report: $reportId")
            throw e
        }
    }

    override suspend fun markReportsAsAnalyzed(reportIds: List<Long>, metaAnalysisId: Long) =
        withContext(ioDispatcher) {
            try {
                expertReportDao.markReportsAsAnalyzed(reportIds, metaAnalysisId)
                Timber.d("Marked ${reportIds.size} reports as analyzed (meta-analysis ID: $metaAnalysisId)")
            } catch (e: Exception) {
                Timber.e(e, "Error marking reports as analyzed")
                throw e
            }
        }

    override fun getReportsByMetaAnalysisId(metaAnalysisId: Long): Flow<List<ExpertReport>> {
        return expertReportDao.getReportsByMetaAnalysisId(metaAnalysisId)
            .map { entities -> entities.map { it.toDomain() } }
            .catch { e ->
                Timber.e(e, "Error getting reports by meta-analysis ID: $metaAnalysisId")
                emit(emptyList())
            }
            .flowOn(ioDispatcher)
    }

    // ==================== File Monitoring Operations ====================

    override suspend fun scanAndImportNewReports(): Int = withContext(ioDispatcher) {
        try {
            val reportsDir = getReportsDirectory()
            if (reportsDir == null || !reportsDir.exists()) {
                Timber.w("Reports directory does not exist: $REPORTS_DIRECTORY_NAME")
                return@withContext 0
            }

            val markdownFiles = reportsDir.listFiles { file ->
                file.isFile && file.extension.lowercase() == "md"
            } ?: emptyArray()

            Timber.d("Found ${markdownFiles.size} markdown files in $REPORTS_DIRECTORY_NAME")

            var importedCount = 0
            for (file in markdownFiles) {
                try {
                    if (shouldImportFile(file)) {
                        val report = parseMarkdownFile(file)
                        insertReport(report)
                        importedFiles.add(file.absolutePath)
                        importedCount++
                        Timber.i("Imported report: ${file.name}")
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error importing file: ${file.name}")
                }
            }

            if (importedCount > 0) {
                notifyNewReports(importedCount)
            }

            importedCount
        } catch (e: Exception) {
            Timber.e(e, "Error scanning for new reports")
            0
        }
    }

    override suspend fun startFileMonitoring() {
        if (monitoringJob?.isActive == true) {
            Timber.w("File monitoring is already active")
            return
        }

        monitoringJob = monitoringScope.launch {
            Timber.i("Started file monitoring for $REPORTS_DIRECTORY_NAME")

            while (isActive) {
                try {
                    scanAndImportNewReports()
                } catch (e: Exception) {
                    Timber.e(e, "Error during file monitoring scan")
                }

                delay(MONITORING_INTERVAL_MS)
            }

            Timber.i("File monitoring stopped")
        }
    }

    override suspend fun stopFileMonitoring() {
        monitoringJob?.cancel()
        monitoringJob = null
        Timber.i("Stopped file monitoring")
    }

    override fun isMonitoringActive(): Boolean {
        return monitoringJob?.isActive == true
    }

    // ==================== Notification Operations ====================

    override suspend fun notifyNewReports(count: Int) {
        // This will be implemented when we add notification system
        // For now, just log
        Timber.i("New reports detected: $count unanalyzed reports available")
    }

    // ==================== Private Helper Methods ====================

    /**
     * Get the ExpertReports directory on app-specific external storage
     * Uses /sdcard/Android/data/com.cryptotrader/files/ExpertReports
     * This doesn't require any permissions on Android 10+
     */
    private fun getReportsDirectory(): File? {
        return try {
            // Use app-specific external files directory (no permission needed)
            val appFilesDir = context.getExternalFilesDir(null)
            val reportsDir = File(appFilesDir, "ExpertReports")

            // Create directory if it doesn't exist
            if (!reportsDir.exists()) {
                reportsDir.mkdirs()
                Timber.d("Created reports directory: ${reportsDir.absolutePath}")
            }

            reportsDir
        } catch (e: Exception) {
            Timber.e(e, "Error accessing reports directory")
            null
        }
    }

    /**
     * Check if a file should be imported (not already in database)
     */
    private suspend fun shouldImportFile(file: File): Boolean {
        // Check if already imported in this session
        if (importedFiles.contains(file.absolutePath)) {
            return false
        }

        // Check if already in database by filepath
        val existing = expertReportDao.getReportByFilePath(file.absolutePath)
        if (existing != null) {
            importedFiles.add(file.absolutePath)
            return false
        }

        return true
    }

    /**
     * Parse a markdown file into an ExpertReport
     */
    private fun parseMarkdownFile(file: File): ExpertReport {
        val content = file.readText()
        val title = extractTitle(content, file.name)
        val category = detectCategory(content, file.name)
        val tags = extractTags(content)
        val author = extractAuthor(content)
        val source = extractSource(content)

        return ExpertReport(
            title = title,
            content = content,
            author = author,
            source = source,
            category = category,
            filePath = file.absolutePath,
            filename = file.name,
            fileSize = file.length(),
            uploadDate = file.lastModified(),
            analyzed = false,
            tags = tags
        )
    }

    /**
     * Extract title from markdown content or filename
     * Looks for: # Title or first line
     */
    private fun extractTitle(content: String, filename: String): String {
        // Try to find markdown h1 title
        val titleRegex = Regex("^#\\s+(.+)$", RegexOption.MULTILINE)
        val match = titleRegex.find(content)
        if (match != null) {
            return match.groupValues[1].trim()
        }

        // Use first non-empty line
        val firstLine = content.lines().firstOrNull { it.isNotBlank() }
        if (firstLine != null && firstLine.length < 100) {
            return firstLine.trim()
        }

        // Fallback to filename without extension
        return filename.substringBeforeLast('.').replace('_', ' ')
    }

    /**
     * Detect report category from content and filename
     */
    private fun detectCategory(content: String, filename: String): ReportCategory {
        val lowerContent = content.lowercase()
        val lowerFilename = filename.lowercase()

        return when {
            lowerContent.contains("technical analysis") ||
            lowerFilename.contains("technical") ||
            lowerContent.contains("rsi") ||
            lowerContent.contains("macd") ||
            lowerContent.contains("moving average") -> ReportCategory.TECHNICAL_ANALYSIS

            lowerContent.contains("fundamental") ||
            lowerFilename.contains("fundamental") ||
            lowerContent.contains("valuation") -> ReportCategory.FUNDAMENTAL

            lowerContent.contains("market analysis") ||
            lowerFilename.contains("market") -> ReportCategory.MARKET_ANALYSIS

            lowerContent.contains("news") ||
            lowerContent.contains("breaking") ||
            lowerFilename.contains("news") -> ReportCategory.NEWS

            lowerContent.contains("sentiment") ||
            lowerFilename.contains("sentiment") -> ReportCategory.SENTIMENT

            else -> ReportCategory.OTHER
        }
    }

    /**
     * Extract tags from markdown content
     * Looks for: Tags: tag1, tag2, tag3
     */
    private fun extractTags(content: String): List<String> {
        val tagsRegex = Regex("(?:tags|Tags):\\s*(.+)$", RegexOption.MULTILINE)
        val match = tagsRegex.find(content)
        if (match != null) {
            return match.groupValues[1]
                .split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        }
        return emptyList()
    }

    /**
     * Extract author from markdown content
     * Looks for: Author: Name or By: Name
     */
    private fun extractAuthor(content: String): String? {
        val authorRegex = Regex("(?:Author|By):\\s*(.+)$", RegexOption.MULTILINE)
        val match = authorRegex.find(content)
        return match?.groupValues?.get(1)?.trim()
    }

    /**
     * Extract source from markdown content
     * Looks for: Source: Name
     */
    private fun extractSource(content: String): String? {
        val sourceRegex = Regex("(?:Source):\\s*(.+)$", RegexOption.MULTILINE)
        val match = sourceRegex.find(content)
        return match?.groupValues?.get(1)?.trim()
    }

    // ==================== Entity/Domain Mapping ====================

    private fun ExpertReport.toEntity() = ExpertReportEntity(
        id = id,
        title = title,
        content = content,
        author = author,
        source = source,
        category = category.toString(),
        uploadDate = uploadDate,
        filePath = filePath,
        filename = filename,
        fileSize = fileSize,
        analyzed = analyzed,
        metaAnalysisId = metaAnalysisId,
        tags = if (tags.isNotEmpty()) json.encodeToString(tags) else null,
        sentiment = sentiment?.toString(),
        sentimentScore = sentimentScore,
        assets = if (assets.isNotEmpty()) json.encodeToString(assets) else null,
        tradingPairs = if (tradingPairs.isNotEmpty()) json.encodeToString(tradingPairs) else null,
        publishedDate = publishedDate,
        usedInStrategies = usedInStrategies,
        impactScore = impactScore
    )

    private fun ExpertReportEntity.toDomain() = ExpertReport(
        id = id,
        title = title,
        content = content,
        author = author,
        source = source,
        category = ReportCategory.fromString(category),
        uploadDate = uploadDate,
        filePath = filePath,
        filename = filename,
        fileSize = fileSize,
        analyzed = analyzed,
        metaAnalysisId = metaAnalysisId,
        tags = tags?.let {
            try {
                json.decodeFromString(it)
            } catch (e: Exception) {
                emptyList()
            }
        } ?: emptyList(),
        sentiment = sentiment?.let { ReportSentiment.fromString(it) },
        sentimentScore = sentimentScore,
        assets = assets?.let {
            try {
                json.decodeFromString(it)
            } catch (e: Exception) {
                emptyList()
            }
        } ?: emptyList(),
        tradingPairs = tradingPairs?.let {
            try {
                json.decodeFromString(it)
            } catch (e: Exception) {
                emptyList()
            }
        } ?: emptyList(),
        publishedDate = publishedDate,
        usedInStrategies = usedInStrategies,
        impactScore = impactScore
    )
}
