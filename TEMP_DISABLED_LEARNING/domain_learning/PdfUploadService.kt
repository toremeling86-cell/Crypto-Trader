package com.cryptotrader.domain.learning

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.cryptotrader.data.local.FileStorageManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Result of PDF upload operation
 */
data class PdfUploadResult(
    val bookId: String,
    val filePath: String,
    val fileName: String,
    val fileSize: Long,
    val pageCount: Int,
    val hash: String
)

/**
 * PDF validation result
 */
data class PdfValidation(
    val isValid: Boolean,
    val fileName: String,
    val fileSize: Long,
    val error: String? = null
)

/**
 * Chapter information extracted from PDF
 */
data class ChapterInfo(
    val number: Int,
    val title: String,
    val startPage: Int,
    val endPage: Int,
    val pageCount: Int
)

/**
 * Upload progress tracking
 */
data class UploadProgress(
    val stage: UploadStage,
    val progress: Float,
    val message: String
)

enum class UploadStage {
    VALIDATING,
    COPYING,
    EXTRACTING_TEXT,
    PROCESSING_CHAPTERS,
    CALCULATING_HASH,
    COMPLETE
}

/**
 * Service interface for PDF upload and processing operations
 */
interface PdfUploadService {
    /**
     * Upload and process a PDF file
     * @param uri Content URI of the PDF file
     * @return Result containing upload details or error
     */
    suspend fun uploadPdf(uri: Uri): Result<PdfUploadResult>

    /**
     * Upload PDF with progress tracking
     * @param uri Content URI of the PDF file
     * @return Flow emitting progress updates and final result
     */
    fun uploadPdfWithProgress(uri: Uri): Flow<Result<UploadProgress>>

    /**
     * Validate a PDF file before upload
     * @param uri Content URI of the PDF file
     * @return Result containing validation details
     */
    suspend fun validatePdf(uri: Uri): Result<PdfValidation>

    /**
     * Extract text content from a PDF file
     * @param pdfPath Absolute path to the PDF file
     * @return Result containing extracted text
     */
    suspend fun extractText(pdfPath: String): Result<String>

    /**
     * Extract chapter information from PDF
     * @param pdfPath Absolute path to the PDF file
     * @return Result containing list of chapters
     */
    suspend fun extractChapters(pdfPath: String): Result<List<ChapterInfo>>
}

/**
 * Implementation of PdfUploadService
 */
@Singleton
class PdfUploadServiceImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val textExtractor: PdfTextExtractor,
    private val storageManager: FileStorageManager
) : PdfUploadService {

    companion object {
        private const val MAX_FILE_SIZE = 100L * 1024 * 1024 // 100MB
        private const val MIN_FILE_SIZE = 1024L // 1KB
        private const val PDF_MIME_TYPE = "application/pdf"
        private const val PDF_MAGIC_BYTES = "%PDF"
    }

    override suspend fun uploadPdf(uri: Uri): Result<PdfUploadResult> = withContext(Dispatchers.IO) {
        try {
            // Step 1: Validate the PDF
            val validation = validatePdf(uri).getOrElse {
                return@withContext Result.failure(it)
            }

            if (!validation.isValid) {
                return@withContext Result.failure(
                    IllegalArgumentException(validation.error ?: "PDF validation failed")
                )
            }

            // Step 2: Generate unique book ID
            val bookId = generateBookId()

            // Step 3: Copy PDF to app storage
            val destinationFile = storageManager.createBookFile(bookId, validation.fileName)
            copyPdfToStorage(uri, destinationFile).getOrElse {
                return@withContext Result.failure(it)
            }

            // Step 4: Extract text to verify PDF is readable
            val text = extractText(destinationFile.absolutePath).getOrElse {
                destinationFile.delete()
                return@withContext Result.failure(it)
            }

            // Step 5: Count pages
            val pageCount = textExtractor.getPageCount(destinationFile.absolutePath).getOrElse {
                destinationFile.delete()
                return@withContext Result.failure(it)
            }

            // Step 6: Calculate file hash for integrity
            val hash = storageManager.calculateFileHash(destinationFile.absolutePath).getOrElse {
                destinationFile.delete()
                return@withContext Result.failure(it)
            }

            Timber.i("PDF uploaded successfully: $bookId, pages: $pageCount")

            Result.success(
                PdfUploadResult(
                    bookId = bookId,
                    filePath = destinationFile.absolutePath,
                    fileName = validation.fileName,
                    fileSize = validation.fileSize,
                    pageCount = pageCount,
                    hash = hash
                )
            )
        } catch (e: Exception) {
            Timber.e(e, "Error uploading PDF")
            Result.failure(e)
        }
    }

    override fun uploadPdfWithProgress(uri: Uri): Flow<Result<UploadProgress>> = flow {
        try {
            // Stage 1: Validation
            emit(Result.success(UploadProgress(UploadStage.VALIDATING, 0.1f, "Validating PDF file...")))

            val validation = validatePdf(uri).getOrElse {
                emit(Result.failure(it))
                return@flow
            }

            if (!validation.isValid) {
                emit(Result.failure(IllegalArgumentException(validation.error ?: "PDF validation failed")))
                return@flow
            }

            val bookId = generateBookId()
            val destinationFile = storageManager.createBookFile(bookId, validation.fileName)

            // Stage 2: Copying
            emit(Result.success(UploadProgress(UploadStage.COPYING, 0.3f, "Copying PDF to storage...")))
            copyPdfToStorage(uri, destinationFile).getOrElse {
                emit(Result.failure(it))
                return@flow
            }

            // Stage 3: Extracting text
            emit(Result.success(UploadProgress(UploadStage.EXTRACTING_TEXT, 0.5f, "Extracting text from PDF...")))
            extractText(destinationFile.absolutePath).getOrElse {
                destinationFile.delete()
                emit(Result.failure(it))
                return@flow
            }

            // Stage 4: Processing chapters
            emit(Result.success(UploadProgress(UploadStage.PROCESSING_CHAPTERS, 0.7f, "Detecting chapters...")))
            val pageCount = textExtractor.getPageCount(destinationFile.absolutePath).getOrElse {
                destinationFile.delete()
                emit(Result.failure(it))
                return@flow
            }

            // Stage 5: Calculating hash
            emit(Result.success(UploadProgress(UploadStage.CALCULATING_HASH, 0.9f, "Verifying file integrity...")))
            storageManager.calculateFileHash(destinationFile.absolutePath).getOrElse {
                destinationFile.delete()
                emit(Result.failure(it))
                return@flow
            }

            // Complete
            emit(Result.success(UploadProgress(UploadStage.COMPLETE, 1.0f, "Upload complete!")))

        } catch (e: Exception) {
            Timber.e(e, "Error during PDF upload with progress")
            emit(Result.failure(e))
        }
    }

    override suspend fun validatePdf(uri: Uri): Result<PdfValidation> = withContext(Dispatchers.IO) {
        try {
            // Get file name and size
            val (fileName, fileSize) = getFileInfo(uri).getOrElse {
                return@withContext Result.failure(it)
            }

            // Validate file size
            if (fileSize < MIN_FILE_SIZE) {
                return@withContext Result.success(
                    PdfValidation(
                        isValid = false,
                        fileName = fileName,
                        fileSize = fileSize,
                        error = "File is too small (minimum 1KB)"
                    )
                )
            }

            if (fileSize > MAX_FILE_SIZE) {
                return@withContext Result.success(
                    PdfValidation(
                        isValid = false,
                        fileName = fileName,
                        fileSize = fileSize,
                        error = "File exceeds maximum size of 100MB"
                    )
                )
            }

            // Validate file extension
            if (!fileName.endsWith(".pdf", ignoreCase = true)) {
                return@withContext Result.success(
                    PdfValidation(
                        isValid = false,
                        fileName = fileName,
                        fileSize = fileSize,
                        error = "File must be a PDF (.pdf extension)"
                    )
                )
            }

            // Validate PDF magic bytes
            val isPdf = checkPdfMagicBytes(uri).getOrElse {
                return@withContext Result.failure(it)
            }

            if (!isPdf) {
                return@withContext Result.success(
                    PdfValidation(
                        isValid = false,
                        fileName = fileName,
                        fileSize = fileSize,
                        error = "File is not a valid PDF"
                    )
                )
            }

            Result.success(
                PdfValidation(
                    isValid = true,
                    fileName = fileName,
                    fileSize = fileSize
                )
            )
        } catch (e: Exception) {
            Timber.e(e, "Error validating PDF")
            Result.failure(e)
        }
    }

    override suspend fun extractText(pdfPath: String): Result<String> {
        return textExtractor.extractText(pdfPath)
    }

    override suspend fun extractChapters(pdfPath: String): Result<List<ChapterInfo>> {
        return textExtractor.extractChapters(pdfPath)
    }

    // Helper functions

    private fun getFileInfo(uri: Uri): Result<Pair<String, Long>> {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)

                    val fileName = if (nameIndex != -1) {
                        cursor.getString(nameIndex)
                    } else {
                        "unknown.pdf"
                    }

                    val fileSize = if (sizeIndex != -1) {
                        cursor.getLong(sizeIndex)
                    } else {
                        -1L
                    }

                    Result.success(Pair(fileName, fileSize))
                } else {
                    Result.failure(IllegalStateException("Could not read file information"))
                }
            } ?: Result.failure(IllegalStateException("Could not access file"))
        } catch (e: Exception) {
            Timber.e(e, "Error getting file info")
            Result.failure(e)
        }
    }

    private fun checkPdfMagicBytes(uri: Uri): Result<Boolean> {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val buffer = ByteArray(4)
                val bytesRead = inputStream.read(buffer)

                if (bytesRead < 4) {
                    return Result.success(false)
                }

                val header = String(buffer, Charsets.US_ASCII)
                Result.success(header == PDF_MAGIC_BYTES)
            } ?: Result.failure(IllegalStateException("Could not open file"))
        } catch (e: Exception) {
            Timber.e(e, "Error checking PDF magic bytes")
            Result.failure(e)
        }
    }

    private suspend fun copyPdfToStorage(uri: Uri, destinationFile: File): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(destinationFile).use { outputStream ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                    }
                }
            } ?: return@withContext Result.failure(IllegalStateException("Could not open input stream"))

            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Error copying PDF to storage")
            destinationFile.delete()
            Result.failure(e)
        }
    }

    private fun generateBookId(): String {
        return "book_${System.currentTimeMillis()}_${(1000..9999).random()}"
    }
}
