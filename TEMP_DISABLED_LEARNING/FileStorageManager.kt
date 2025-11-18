package com.cryptotrader.data.local

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.*
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import timber.log.Timber

/**
 * Manages file storage for learning materials (PDFs, images, etc.)
 * Implements best practices for Android file storage with proper organization
 */
@Singleton
class FileStorageManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        // Directory structure constants
        private const val ROOT_DIR = "CryptoTrader"
        private const val LEARNING_DIR = "Learning"
        private const val BOOKS_DIR = "Books"
        private const val COVERS_DIR = "Covers"
        private const val EXPORTS_DIR = "Exports"
        private const val BACKUPS_DIR = "Backups"
        private const val TEMP_DIR = "Temp"

        // File naming patterns
        private const val BOOK_PREFIX = "book_"
        private const val COVER_PREFIX = "cover_"
        private const val BACKUP_PREFIX = "backup_"
        private const val DATE_FORMAT = "yyyyMMdd_HHmmss"

        // Size constraints
        private const val MAX_BOOK_SIZE_MB = 100L
        private const val MAX_TOTAL_STORAGE_GB = 5L
        private const val CLEANUP_THRESHOLD_GB = 4L

        // File extensions
        private const val PDF_EXT = ".pdf"
        private const val JPG_EXT = ".jpg"
        private const val PNG_EXT = ".png"
        private const val ZIP_EXT = ".zip"
    }

    private val dateFormatter = DateTimeFormatter.ofPattern(DATE_FORMAT)

    /**
     * Get the root directory for all app files
     */
    private fun getRootDirectory(): File {
        return if (isExternalStorageAvailable()) {
            // Use external storage for better user access
            File(context.getExternalFilesDir(null), ROOT_DIR)
        } else {
            // Fallback to internal storage
            File(context.filesDir, ROOT_DIR)
        }
    }

    /**
     * Get specific directory for different file types
     */
    fun getBooksDirectory(): File {
        val dir = File(getRootDirectory(), "$LEARNING_DIR/$BOOKS_DIR")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getCoversDirectory(): File {
        val dir = File(getRootDirectory(), "$LEARNING_DIR/$COVERS_DIR")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getBackupsDirectory(): File {
        val dir = File(getRootDirectory(), "$LEARNING_DIR/$BACKUPS_DIR")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getTempDirectory(): File {
        val dir = File(getRootDirectory(), "$LEARNING_DIR/$TEMP_DIR")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getExportsDirectory(): File {
        val dir = File(getRootDirectory(), "$LEARNING_DIR/$EXPORTS_DIR")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Get directory for a specific book (used by PdfUploadService)
     * Structure: /CryptoTrader/Learning/Books/[bookId]/
     */
    fun getBookDirectory(bookId: String): File {
        val dir = File(getBooksDirectory(), bookId)
        if (!dir.exists()) {
            dir.mkdirs()
            Timber.d("Created book directory: ${dir.absolutePath}")
        }
        return dir
    }

    /**
     * Get directory for book chunks
     * Structure: /CryptoTrader/Learning/Books/[bookId]/Chunks/
     */
    fun getChunksDirectory(bookId: String): File {
        val dir = File(getBookDirectory(bookId), "Chunks")
        if (!dir.exists()) {
            dir.mkdirs()
            Timber.d("Created chunks directory: ${dir.absolutePath}")
        }
        return dir
    }

    /**
     * Create a file for storing a PDF book (used by PdfUploadService)
     * @param bookId Unique identifier for the book
     * @param fileName Original file name
     * @return File object for the book
     */
    fun createBookFile(bookId: String, fileName: String): File {
        val bookDir = getBookDirectory(bookId)
        val sanitizedFileName = sanitizeFileName(fileName)
        return File(bookDir, sanitizedFileName)
    }

    /**
     * Create a chunk file for storing text chunks
     * @param bookId Unique identifier for the book
     * @param chunkIndex Index of the chunk
     * @return File object for the chunk
     */
    fun createChunkFile(bookId: String, chunkIndex: Int): File {
        val chunksDir = getChunksDirectory(bookId)
        return File(chunksDir, "chunk_${chunkIndex.toString().padStart(4, '0')}.txt")
    }

    /**
     * Calculate SHA-256 hash of a file for integrity checking
     * @param filePath Absolute path to the file
     * @return Result containing hex-encoded hash string
     */
    suspend fun calculateFileHash(filePath: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                return@withContext Result.failure(IllegalArgumentException("File not found: $filePath"))
            }

            val hash = generateFileHash(file)
            Timber.d("Calculated hash for $filePath: $hash")
            Result.success(hash)
        } catch (e: Exception) {
            Timber.e(e, "Error calculating file hash")
            Result.failure(e)
        }
    }

    /**
     * Delete a book and all its associated files (including chunks)
     * @param bookId Unique identifier for the book
     * @return Result indicating success or failure
     */
    suspend fun deleteBook(bookId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val bookDir = getBookDirectory(bookId)

            if (bookDir.exists()) {
                val deleted = bookDir.deleteRecursively()

                if (deleted) {
                    Timber.i("Deleted book directory: $bookId")
                    Result.success(Unit)
                } else {
                    Result.failure(IllegalStateException("Failed to delete book directory"))
                }
            } else {
                Timber.w("Book directory does not exist: $bookId")
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error deleting book: $bookId")
            Result.failure(e)
        }
    }

    /**
     * Save a PDF book file
     */
    suspend fun saveBookFile(
        sourceUri: Uri,
        title: String,
        bookId: String = UUID.randomUUID().toString()
    ): Result<BookFileInfo> = withContext(Dispatchers.IO) {
        try {
            // Validate file size
            val fileSize = getFileSize(sourceUri)
            if (fileSize > MAX_BOOK_SIZE_MB * 1024 * 1024) {
                return@withContext Result.failure(
                    FileSizeException("File size exceeds maximum allowed size of ${MAX_BOOK_SIZE_MB}MB")
                )
            }

            // Check available storage
            if (!hasEnoughStorage(fileSize)) {
                performStorageCleanup()
                if (!hasEnoughStorage(fileSize)) {
                    return@withContext Result.failure(
                        InsufficientStorageException("Not enough storage space available")
                    )
                }
            }

            // Generate unique filename
            val sanitizedTitle = sanitizeFileName(title)
            val timestamp = LocalDateTime.now().format(dateFormatter)
            val fileName = "${BOOK_PREFIX}${bookId}_${timestamp}_$sanitizedTitle$PDF_EXT"
            val targetFile = File(getBooksDirectory(), fileName)

            // Copy file to target location
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext Result.failure(
                FileAccessException("Cannot read source file")
            )

            // Generate file hash for integrity checking
            val fileHash = generateFileHash(targetFile)

            // Extract metadata
            val pageCount = extractPdfPageCount(targetFile)

            Result.success(
                BookFileInfo(
                    bookId = bookId,
                    filePath = targetFile.absolutePath,
                    fileName = fileName,
                    fileSize = targetFile.length(),
                    fileHash = fileHash,
                    pageCount = pageCount,
                    createdAt = LocalDateTime.now()
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Save a cover image for a book
     */
    suspend fun saveCoverImage(
        sourceUri: Uri,
        bookId: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val extension = getMimeType(sourceUri)?.let {
                MimeTypeMap.getSingleton().getExtensionFromMimeType(it)
            } ?: "jpg"

            val fileName = "${COVER_PREFIX}${bookId}.$extension"
            val targetFile = File(getCoversDirectory(), fileName)

            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }

            Result.success(targetFile.absolutePath)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete a book file and its associated files
     */
    suspend fun deleteBookFiles(bookId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Delete book PDF
            getBooksDirectory().listFiles()?.forEach { file ->
                if (file.name.contains(bookId)) {
                    file.delete()
                }
            }

            // Delete cover image
            getCoversDirectory().listFiles()?.forEach { file ->
                if (file.name.contains(bookId)) {
                    file.delete()
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Create a backup of all learning data
     */
    suspend fun createBackup(): Result<BackupInfo> = withContext(Dispatchers.IO) {
        try {
            val timestamp = LocalDateTime.now().format(dateFormatter)
            val backupFileName = "${BACKUP_PREFIX}${timestamp}$ZIP_EXT"
            val backupFile = File(getBackupsDirectory(), backupFileName)

            // Create zip file with all learning data
            ZipUtils.zipDirectory(
                sourceDir = File(getRootDirectory(), LEARNING_DIR),
                targetFile = backupFile,
                excludeDirs = listOf(TEMP_DIR, BACKUPS_DIR)
            )

            Result.success(
                BackupInfo(
                    fileName = backupFileName,
                    filePath = backupFile.absolutePath,
                    fileSize = backupFile.length(),
                    createdAt = LocalDateTime.now(),
                    itemsCount = countBackupItems()
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Restore from backup
     */
    suspend fun restoreFromBackup(backupPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val backupFile = File(backupPath)
            if (!backupFile.exists()) {
                return@withContext Result.failure(
                    FileNotFoundException("Backup file not found")
                )
            }

            // Extract backup to temp directory first
            val tempRestoreDir = File(getTempDirectory(), "restore_${System.currentTimeMillis()}")
            tempRestoreDir.mkdirs()

            ZipUtils.unzipFile(backupFile, tempRestoreDir)

            // Validate restored content
            if (!validateRestoredContent(tempRestoreDir)) {
                tempRestoreDir.deleteRecursively()
                return@withContext Result.failure(
                    InvalidBackupException("Invalid or corrupted backup file")
                )
            }

            // Move restored files to actual locations
            moveRestoredFiles(tempRestoreDir)

            // Cleanup temp directory
            tempRestoreDir.deleteRecursively()

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get storage statistics
     */
    fun getStorageStats(): StorageStats {
        val rootDir = getRootDirectory()
        val totalSize = calculateDirectorySize(rootDir)
        val booksSize = calculateDirectorySize(getBooksDirectory())
        val coversSize = calculateDirectorySize(getCoversDirectory())
        val backupsSize = calculateDirectorySize(getBackupsDirectory())

        val availableSpace = rootDir.freeSpace
        val totalSpace = rootDir.totalSpace

        return StorageStats(
            totalUsedBytes = totalSize,
            booksBytes = booksSize,
            coversBytes = coversSize,
            backupsBytes = backupsSize,
            availableBytes = availableSpace,
            totalBytes = totalSpace,
            usagePercentage = (totalSize.toFloat() / totalSpace * 100).toInt()
        )
    }

    /**
     * Perform storage cleanup
     */
    suspend fun performStorageCleanup(): Result<CleanupResult> = withContext(Dispatchers.IO) {
        try {
            var freedBytes = 0L

            // Clean temp directory
            val tempDir = getTempDirectory()
            val tempSize = calculateDirectorySize(tempDir)
            tempDir.deleteRecursively()
            tempDir.mkdirs()
            freedBytes += tempSize

            // Remove old backups (keep last 5)
            val backups = getBackupsDirectory().listFiles()
                ?.sortedByDescending { it.lastModified() }
                ?: emptyList()

            backups.drop(5).forEach { backup ->
                freedBytes += backup.length()
                backup.delete()
            }

            // Remove orphaned cover images
            val orphanedCovers = findOrphanedFiles()
            orphanedCovers.forEach { file ->
                freedBytes += file.length()
                file.delete()
            }

            Result.success(
                CleanupResult(
                    freedBytes = freedBytes,
                    deletedFiles = orphanedCovers.size + (backups.size - 5).coerceAtLeast(0),
                    timestamp = LocalDateTime.now()
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Export book with notes and progress
     */
    suspend fun exportBookWithData(
        bookId: String,
        includeNotes: Boolean,
        includeProgress: Boolean
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val timestamp = LocalDateTime.now().format(dateFormatter)
            val exportFileName = "export_${bookId}_$timestamp$ZIP_EXT"
            val exportFile = File(getExportsDirectory(), exportFileName)

            // Create export package
            // This would include the PDF, notes JSON, progress JSON, etc.
            // Implementation depends on your specific requirements

            Result.success(exportFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Helper methods

    private fun isExternalStorageAvailable(): Boolean {
        return Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
    }

    private fun getFileSize(uri: Uri): Long {
        return context.contentResolver.openFileDescriptor(uri, "r")?.use {
            it.statSize
        } ?: 0L
    }

    private fun hasEnoughStorage(requiredBytes: Long): Boolean {
        val availableBytes = getRootDirectory().freeSpace
        return availableBytes > requiredBytes + (100 * 1024 * 1024) // Keep 100MB buffer
    }

    private fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            .take(50) // Limit length
    }

    private fun generateFileHash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun extractPdfPageCount(file: File): Int? {
        // This would use a PDF library like PdfBox or iText to count pages
        // For now, returning null as placeholder
        return null
    }

    private fun getMimeType(uri: Uri): String? {
        return context.contentResolver.getType(uri)
    }

    private fun calculateDirectorySize(dir: File): Long {
        var size = 0L
        dir.walkTopDown().forEach { file ->
            if (file.isFile) {
                size += file.length()
            }
        }
        return size
    }

    private fun countBackupItems(): Int {
        return getBooksDirectory().listFiles()?.size ?: 0
    }

    private fun validateRestoredContent(dir: File): Boolean {
        // Validate that restored content has expected structure
        return File(dir, BOOKS_DIR).exists()
    }

    private fun moveRestoredFiles(sourceDir: File) {
        // Move restored files from temp to actual locations
        // Implementation depends on specific requirements
    }

    private fun findOrphanedFiles(): List<File> {
        // Find files that are no longer referenced in the database
        // This would require database access to check
        return emptyList()
    }
}

// Data classes for file operations

data class BookFileInfo(
    val bookId: String,
    val filePath: String,
    val fileName: String,
    val fileSize: Long,
    val fileHash: String,
    val pageCount: Int?,
    val createdAt: LocalDateTime
)

data class BackupInfo(
    val fileName: String,
    val filePath: String,
    val fileSize: Long,
    val createdAt: LocalDateTime,
    val itemsCount: Int
)

data class StorageStats(
    val totalUsedBytes: Long,
    val booksBytes: Long,
    val coversBytes: Long,
    val backupsBytes: Long,
    val availableBytes: Long,
    val totalBytes: Long,
    val usagePercentage: Int
) {
    val totalUsedMB: Float get() = totalUsedBytes / (1024f * 1024f)
    val totalUsedGB: Float get() = totalUsedBytes / (1024f * 1024f * 1024f)
    val availableGB: Float get() = availableBytes / (1024f * 1024f * 1024f)
}

data class CleanupResult(
    val freedBytes: Long,
    val deletedFiles: Int,
    val timestamp: LocalDateTime
) {
    val freedMB: Float get() = freedBytes / (1024f * 1024f)
}

// Custom exceptions

class FileSizeException(message: String) : Exception(message)
class InsufficientStorageException(message: String) : Exception(message)
class FileAccessException(message: String) : Exception(message)
class InvalidBackupException(message: String) : Exception(message)

// Utility class for ZIP operations (simplified)
object ZipUtils {
    fun zipDirectory(sourceDir: File, targetFile: File, excludeDirs: List<String> = emptyList()) {
        // Implementation for creating ZIP archives
    }

    fun unzipFile(zipFile: File, targetDir: File) {
        // Implementation for extracting ZIP archives
    }
}