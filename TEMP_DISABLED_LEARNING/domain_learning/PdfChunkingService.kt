package com.cryptotrader.domain.learning

import com.cryptotrader.data.local.FileStorageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Text chunk with metadata for Claude API processing
 */
data class TextChunk(
    val chunkIndex: Int,
    val text: String,
    val startPage: Int,
    val endPage: Int,
    val wordCount: Int,
    val tokenEstimate: Int,
    val hasOverlap: Boolean,
    val overlapText: String? = null
)

/**
 * Chunking configuration
 */
data class ChunkingConfig(
    val targetTokens: Int = 20000, // ~30-40 pages
    val wordsPerToken: Float = 0.75f, // Approximate conversion
    val overlapWords: Int = 500, // Words to overlap between chunks
    val minChunkSize: Int = 5000, // Minimum tokens per chunk
    val maxChunkSize: Int = 30000 // Maximum tokens per chunk
)

/**
 * Chunking result with all chunks and metadata
 */
data class ChunkingResult(
    val bookId: String,
    val chunks: List<TextChunk>,
    val totalChunks: Int,
    val totalPages: Int,
    val totalWords: Int,
    val totalTokens: Int,
    val config: ChunkingConfig
)

/**
 * Service for splitting large PDF texts into chunks suitable for Claude API
 * Implements smart chunking with chapter boundaries and context overlap
 */
@Singleton
class PdfChunkingService @Inject constructor(
    private val textExtractor: PdfTextExtractor,
    private val storageManager: FileStorageManager
) {

    companion object {
        private const val DEFAULT_WORDS_PER_PAGE = 300
    }

    /**
     * Chunk a PDF file into manageable pieces for Claude API
     * @param pdfPath Absolute path to the PDF file
     * @param bookId Unique identifier for the book
     * @param config Chunking configuration
     * @param saveChunks Whether to save chunks to disk
     * @return Result containing chunking details
     */
    suspend fun chunkPdf(
        pdfPath: String,
        bookId: String,
        config: ChunkingConfig = ChunkingConfig(),
        saveChunks: Boolean = true
    ): Result<ChunkingResult> = withContext(Dispatchers.IO) {
        try {
            // Extract full text by page
            val pages = textExtractor.extractTextByPage(pdfPath).getOrElse {
                return@withContext Result.failure(it)
            }

            // Try to extract chapters for smart chunking
            val chapters = textExtractor.extractChapters(pdfPath).getOrNull()

            val chunks = if (chapters != null && chapters.size > 1) {
                // Smart chunking based on chapters
                chunkByChapters(pages, chapters, config)
            } else {
                // Standard chunking by token count
                chunkByTokenCount(pages, config)
            }

            // Save chunks to disk if requested
            if (saveChunks) {
                saveChunksToStorage(bookId, chunks).getOrElse {
                    Timber.w(it, "Failed to save chunks to storage")
                }
            }

            // Calculate totals
            val totalWords = chunks.sumOf { it.wordCount }
            val totalTokens = chunks.sumOf { it.tokenEstimate }

            val result = ChunkingResult(
                bookId = bookId,
                chunks = chunks,
                totalChunks = chunks.size,
                totalPages = pages.size,
                totalWords = totalWords,
                totalTokens = totalTokens,
                config = config
            )

            Timber.i("PDF chunked successfully: ${chunks.size} chunks, $totalTokens tokens")
            Result.success(result)
        } catch (e: Exception) {
            Timber.e(e, "Error chunking PDF")
            Result.failure(e)
        }
    }

    /**
     * Load previously saved chunks from storage
     * @param bookId Unique identifier for the book
     * @return Result containing list of chunks
     */
    suspend fun loadChunks(bookId: String): Result<List<TextChunk>> = withContext(Dispatchers.IO) {
        try {
            val chunksDir = storageManager.getChunksDirectory(bookId)

            if (!chunksDir.exists()) {
                return@withContext Result.failure(IllegalStateException("No chunks found for book: $bookId"))
            }

            val chunkFiles = chunksDir.listFiles()
                ?.filter { it.extension == "txt" }
                ?.sortedBy { it.name }
                ?: return@withContext Result.failure(IllegalStateException("No chunk files found"))

            val chunks = chunkFiles.mapIndexed { index, file ->
                val text = file.readText()
                val lines = text.split("\n")

                // Parse metadata from first line (format: "Pages X-Y | Words: N | Tokens: T")
                val metadata = if (lines.isNotEmpty() && lines[0].startsWith("Pages")) {
                    parseChunkMetadata(lines[0])
                } else {
                    ChunkMetadata(1, 1, 0, 0)
                }

                // Remove metadata line from text
                val actualText = if (lines.isNotEmpty() && lines[0].startsWith("Pages")) {
                    lines.drop(1).joinToString("\n")
                } else {
                    text
                }

                TextChunk(
                    chunkIndex = index,
                    text = actualText,
                    startPage = metadata.startPage,
                    endPage = metadata.endPage,
                    wordCount = metadata.wordCount,
                    tokenEstimate = metadata.tokenEstimate,
                    hasOverlap = index > 0
                )
            }

            Timber.d("Loaded ${chunks.size} chunks for book: $bookId")
            Result.success(chunks)
        } catch (e: Exception) {
            Timber.e(e, "Error loading chunks")
            Result.failure(e)
        }
    }

    /**
     * Delete saved chunks for a book
     * @param bookId Unique identifier for the book
     * @return Result indicating success
     */
    suspend fun deleteChunks(bookId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val chunksDir = storageManager.getChunksDirectory(bookId)

            if (chunksDir.exists()) {
                chunksDir.deleteRecursively()
                Timber.i("Deleted chunks for book: $bookId")
            }

            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Error deleting chunks")
            Result.failure(e)
        }
    }

    /**
     * Get chunk by index
     * @param bookId Unique identifier for the book
     * @param chunkIndex Index of the chunk to retrieve
     * @return Result containing the chunk
     */
    suspend fun getChunk(bookId: String, chunkIndex: Int): Result<TextChunk> {
        return try {
            val chunks = loadChunks(bookId).getOrElse {
                return Result.failure(it)
            }

            chunks.getOrNull(chunkIndex)?.let { Result.success(it) }
                ?: Result.failure(IndexOutOfBoundsException("Chunk index $chunkIndex not found"))
        } catch (e: Exception) {
            Timber.e(e, "Error getting chunk")
            Result.failure(e)
        }
    }

    // Private helper methods

    /**
     * Chunk pages based on chapter boundaries
     */
    private fun chunkByChapters(
        pages: List<PageText>,
        chapters: List<ChapterInfo>,
        config: ChunkingConfig
    ): List<TextChunk> {
        val chunks = mutableListOf<TextChunk>()
        val targetWords = (config.targetTokens / config.wordsPerToken).toInt()

        for (chapter in chapters) {
            val chapterPages = pages.filter { page ->
                page.pageNumber in chapter.startPage..chapter.endPage
            }

            val chapterText = chapterPages.joinToString("\n\n") { it.text }
            val chapterWords = chapterPages.sumOf { it.wordCount }

            // If chapter is small enough, keep it as one chunk
            if (chapterWords <= config.maxChunkSize / config.wordsPerToken) {
                chunks.add(
                    TextChunk(
                        chunkIndex = chunks.size,
                        text = chapterText,
                        startPage = chapter.startPage,
                        endPage = chapter.endPage,
                        wordCount = chapterWords,
                        tokenEstimate = (chapterWords * config.wordsPerToken).toInt(),
                        hasOverlap = chunks.isNotEmpty(),
                        overlapText = if (chunks.isNotEmpty()) getOverlapText(chunks.last(), config) else null
                    )
                )
            } else {
                // Split large chapter into smaller chunks
                val chapterChunks = splitTextIntoChunks(
                    chapterPages,
                    targetWords,
                    config,
                    chunks.size
                )
                chunks.addAll(chapterChunks)
            }
        }

        return chunks
    }

    /**
     * Chunk pages by target token count
     */
    private fun chunkByTokenCount(
        pages: List<PageText>,
        config: ChunkingConfig
    ): List<TextChunk> {
        val targetWords = (config.targetTokens / config.wordsPerToken).toInt()
        return splitTextIntoChunks(pages, targetWords, config, 0)
    }

    /**
     * Split pages into chunks of approximately target word count
     */
    private fun splitTextIntoChunks(
        pages: List<PageText>,
        targetWords: Int,
        config: ChunkingConfig,
        startIndex: Int
    ): List<TextChunk> {
        val chunks = mutableListOf<TextChunk>()
        var currentPages = mutableListOf<PageText>()
        var currentWordCount = 0

        for (page in pages) {
            currentPages.add(page)
            currentWordCount += page.wordCount

            // Check if we've reached target size
            if (currentWordCount >= targetWords) {
                val chunkText = currentPages.joinToString("\n\n") { it.text }
                val overlapText = if (chunks.isNotEmpty()) {
                    getOverlapText(chunks.last(), config)
                } else null

                // Add overlap to beginning if this is not the first chunk
                val finalText = if (overlapText != null) {
                    "$overlapText\n\n$chunkText"
                } else {
                    chunkText
                }

                chunks.add(
                    TextChunk(
                        chunkIndex = startIndex + chunks.size,
                        text = finalText,
                        startPage = currentPages.first().pageNumber,
                        endPage = currentPages.last().pageNumber,
                        wordCount = countWords(finalText),
                        tokenEstimate = (countWords(finalText) * config.wordsPerToken).toInt(),
                        hasOverlap = overlapText != null,
                        overlapText = overlapText
                    )
                )

                currentPages.clear()
                currentWordCount = 0
            }
        }

        // Handle remaining pages
        if (currentPages.isNotEmpty()) {
            val chunkText = currentPages.joinToString("\n\n") { it.text }
            val overlapText = if (chunks.isNotEmpty()) {
                getOverlapText(chunks.last(), config)
            } else null

            val finalText = if (overlapText != null) {
                "$overlapText\n\n$chunkText"
            } else {
                chunkText
            }

            chunks.add(
                TextChunk(
                    chunkIndex = startIndex + chunks.size,
                    text = finalText,
                    startPage = currentPages.first().pageNumber,
                    endPage = currentPages.last().pageNumber,
                    wordCount = countWords(finalText),
                    tokenEstimate = (countWords(finalText) * config.wordsPerToken).toInt(),
                    hasOverlap = overlapText != null,
                    overlapText = overlapText
                )
            )
        }

        return chunks
    }

    /**
     * Get overlap text from the end of previous chunk
     */
    private fun getOverlapText(previousChunk: TextChunk, config: ChunkingConfig): String {
        val words = previousChunk.text.split(Regex("\\s+"))
        val overlapStart = maxOf(0, words.size - config.overlapWords)
        return words.subList(overlapStart, words.size).joinToString(" ")
    }

    /**
     * Count words in text
     */
    private fun countWords(text: String): Int {
        return text.split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .size
    }

    /**
     * Save chunks to storage
     */
    private suspend fun saveChunksToStorage(
        bookId: String,
        chunks: List<TextChunk>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // Clear existing chunks
            deleteChunks(bookId)

            // Save each chunk
            chunks.forEach { chunk ->
                val chunkFile = storageManager.createChunkFile(bookId, chunk.chunkIndex)

                // Add metadata as first line
                val metadata = "Pages ${chunk.startPage}-${chunk.endPage} | Words: ${chunk.wordCount} | Tokens: ${chunk.tokenEstimate}"
                val content = "$metadata\n${chunk.text}"

                chunkFile.writeText(content)
            }

            Timber.d("Saved ${chunks.size} chunks to storage")
            Result.success(Unit)
        } catch (e: Exception) {
            Timber.e(e, "Error saving chunks to storage")
            Result.failure(e)
        }
    }

    /**
     * Parse chunk metadata from saved file
     */
    private fun parseChunkMetadata(metadataLine: String): ChunkMetadata {
        return try {
            // Format: "Pages X-Y | Words: N | Tokens: T"
            val parts = metadataLine.split("|").map { it.trim() }

            val pageRange = parts[0].removePrefix("Pages ").split("-")
            val startPage = pageRange[0].toInt()
            val endPage = pageRange.getOrNull(1)?.toInt() ?: startPage

            val wordCount = parts.getOrNull(1)?.removePrefix("Words: ")?.toInt() ?: 0
            val tokenEstimate = parts.getOrNull(2)?.removePrefix("Tokens: ")?.toInt() ?: 0

            ChunkMetadata(startPage, endPage, wordCount, tokenEstimate)
        } catch (e: Exception) {
            Timber.w(e, "Error parsing chunk metadata")
            ChunkMetadata(1, 1, 0, 0)
        }
    }

    /**
     * Internal data class for chunk metadata
     */
    private data class ChunkMetadata(
        val startPage: Int,
        val endPage: Int,
        val wordCount: Int,
        val tokenEstimate: Int
    )
}
