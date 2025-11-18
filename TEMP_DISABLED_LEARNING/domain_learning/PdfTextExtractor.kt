package com.cryptotrader.domain.learning

import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext

/**
 * Page text with metadata
 */
data class PageText(
    val pageNumber: Int,
    val text: String,
    val wordCount: Int
)

/**
 * Service for extracting text and metadata from PDF files using PDFBox
 */
@Singleton
class PdfTextExtractor @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val CHAPTER_PATTERN = "^(Chapter|CHAPTER)\\s+\\d+.*$"
        private val TOC_KEYWORDS = listOf(
            "table of contents",
            "contents",
            "table des matières"
        )
        private const val HEADING_PATTERN = "^[A-Z][A-Z\\s]{5,50}$"
        private const val MIN_CHAPTER_PAGES = 2
        private const val MAX_CHAPTER_PAGES = 100
    }

    init {
        // Initialize PDFBox for Android
        PDFBoxResourceLoader.init(context)
    }

    /**
     * Extract all text from a PDF file
     * @param pdfPath Absolute path to PDF file
     * @return Result containing extracted text with cleaned formatting
     */
    suspend fun extractText(pdfPath: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val file = File(pdfPath)
            if (!file.exists()) {
                return@withContext Result.failure(IllegalArgumentException("PDF file not found: $pdfPath"))
            }

            PDDocument.load(file).use { document ->
                val stripper = PDFTextStripper()
                val text = stripper.getText(document)

                // Clean the extracted text
                val cleanedText = cleanText(text)

                Timber.d("Extracted ${cleanedText.length} characters from PDF")
                Result.success(cleanedText)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error extracting text from PDF: $pdfPath")
            Result.failure(e)
        }
    }

    /**
     * Extract text from specific page range
     * @param pdfPath Absolute path to PDF file
     * @param startPage Starting page (1-indexed)
     * @param endPage Ending page (1-indexed, inclusive)
     * @return Result containing extracted text
     */
    suspend fun extractTextFromPages(
        pdfPath: String,
        startPage: Int,
        endPage: Int
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val file = File(pdfPath)
            if (!file.exists()) {
                return@withContext Result.failure(IllegalArgumentException("PDF file not found: $pdfPath"))
            }

            PDDocument.load(file).use { document ->
                val stripper = PDFTextStripper().apply {
                    this.startPage = startPage
                    this.endPage = endPage
                }

                val text = stripper.getText(document)
                val cleanedText = cleanText(text)

                Result.success(cleanedText)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error extracting text from pages $startPage-$endPage")
            Result.failure(e)
        }
    }

    /**
     * Extract text page by page
     * @param pdfPath Absolute path to PDF file
     * @return Result containing list of PageText objects
     */
    suspend fun extractTextByPage(pdfPath: String): Result<List<PageText>> = withContext(Dispatchers.IO) {
        try {
            val file = File(pdfPath)
            if (!file.exists()) {
                return@withContext Result.failure(IllegalArgumentException("PDF file not found: $pdfPath"))
            }

            val pages = mutableListOf<PageText>()

            PDDocument.load(file).use { document ->
                val totalPages = document.numberOfPages
                val stripper = PDFTextStripper()

                for (pageNum in 1..totalPages) {
                    stripper.startPage = pageNum
                    stripper.endPage = pageNum

                    val pageText = stripper.getText(document)
                    val cleanedText = cleanText(pageText)
                    val wordCount = countWords(cleanedText)

                    pages.add(
                        PageText(
                            pageNumber = pageNum,
                            text = cleanedText,
                            wordCount = wordCount
                        )
                    )
                }
            }

            Timber.d("Extracted text from ${pages.size} pages")
            Result.success(pages)
        } catch (e: Exception) {
            Timber.e(e, "Error extracting text by page")
            Result.failure(e)
        }
    }

    /**
     * Get page count of PDF
     * @param pdfPath Absolute path to PDF file
     * @return Result containing number of pages
     */
    suspend fun getPageCount(pdfPath: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val file = File(pdfPath)
            if (!file.exists()) {
                return@withContext Result.failure(IllegalArgumentException("PDF file not found: $pdfPath"))
            }

            PDDocument.load(file).use { document ->
                Result.success(document.numberOfPages)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error getting page count")
            Result.failure(e)
        }
    }

    /**
     * Extract chapter information from PDF
     * Detects chapters based on common patterns and table of contents
     * @param pdfPath Absolute path to PDF file
     * @return Result containing list of detected chapters
     */
    suspend fun extractChapters(pdfPath: String): Result<List<ChapterInfo>> = withContext(Dispatchers.IO) {
        try {
            val file = File(pdfPath)
            if (!file.exists()) {
                return@withContext Result.failure(IllegalArgumentException("PDF file not found: $pdfPath"))
            }

            val chapters = mutableListOf<ChapterInfo>()

            PDDocument.load(file).use { document ->
                val totalPages = document.numberOfPages

                // Try to extract chapters from table of contents
                val tocChapters = extractChaptersFromTOC(document)
                if (tocChapters.isNotEmpty()) {
                    Timber.d("Found ${tocChapters.size} chapters from TOC")
                    return@withContext Result.success(tocChapters)
                }

                // Fallback: detect chapters by scanning pages
                val detectedChapters = detectChaptersByPattern(document)
                if (detectedChapters.isNotEmpty()) {
                    Timber.d("Detected ${detectedChapters.size} chapters by pattern matching")
                    return@withContext Result.success(detectedChapters)
                }

                // If no chapters detected, create single chapter for entire book
                chapters.add(
                    ChapterInfo(
                        number = 1,
                        title = "Complete Book",
                        startPage = 1,
                        endPage = totalPages,
                        pageCount = totalPages
                    )
                )
            }

            Result.success(chapters)
        } catch (e: Exception) {
            Timber.e(e, "Error extracting chapters")
            Result.failure(e)
        }
    }

    /**
     * Extract metadata from PDF (title, author, etc.)
     */
    suspend fun extractMetadata(pdfPath: String): Result<Map<String, String>> = withContext(Dispatchers.IO) {
        try {
            val file = File(pdfPath)
            if (!file.exists()) {
                return@withContext Result.failure(IllegalArgumentException("PDF file not found: $pdfPath"))
            }

            val metadata = mutableMapOf<String, String>()

            PDDocument.load(file).use { document ->
                document.documentInformation?.let { info ->
                    info.title?.let { metadata["title"] = it }
                    info.author?.let { metadata["author"] = it }
                    info.subject?.let { metadata["subject"] = it }
                    info.keywords?.let { metadata["keywords"] = it }
                    info.creator?.let { metadata["creator"] = it }
                    info.producer?.let { metadata["producer"] = it }
                }
            }

            Result.success(metadata)
        } catch (e: Exception) {
            Timber.e(e, "Error extracting metadata")
            Result.failure(e)
        }
    }

    // Private helper methods

    /**
     * Clean extracted text by removing headers, footers, page numbers, and extra whitespace
     */
    private fun cleanText(text: String): String {
        return text
            // Remove multiple consecutive spaces
            .replace(Regex("[ \\t]+"), " ")
            // Remove multiple consecutive newlines (keep max 2)
            .replace(Regex("\n{3,}"), "\n\n")
            // Remove common page number patterns
            .replace(Regex("(?m)^\\s*\\d+\\s*$"), "")
            // Remove lines with only special characters
            .replace(Regex("(?m)^[\\-_=*]{3,}\\s*$"), "")
            // Trim each line
            .split("\n")
            .joinToString("\n") { it.trim() }
            .trim()
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
     * Attempt to extract chapters from PDF table of contents
     */
    private fun extractChaptersFromTOC(document: PDDocument): List<ChapterInfo> {
        val chapters = mutableListOf<ChapterInfo>()

        try {
            // Search for TOC in first 20 pages
            val stripper = PDFTextStripper()
            val searchPages = minOf(20, document.numberOfPages)

            for (pageNum in 1..searchPages) {
                stripper.startPage = pageNum
                stripper.endPage = pageNum

                val pageText = stripper.getText(document).lowercase()

                // Check if this page contains TOC
                val hasTOC = TOC_KEYWORDS.any { keyword ->
                    pageText.contains(keyword)
                }

                if (hasTOC) {
                    // Extract chapter entries from TOC
                    val lines = pageText.split("\n")
                    var chapterNum = 0

                    for (line in lines) {
                        // Look for chapter pattern with page number
                        val chapterMatch = Regex("(chapter|ch\\.)\\s+(\\d+).*?(\\d+)\\s*$", RegexOption.IGNORE_CASE)
                            .find(line)

                        if (chapterMatch != null) {
                            chapterNum++
                            val startPage = chapterMatch.groupValues[3].toIntOrNull() ?: continue

                            chapters.add(
                                ChapterInfo(
                                    number = chapterNum,
                                    title = line.trim(),
                                    startPage = startPage,
                                    endPage = startPage, // Will be updated later
                                    pageCount = 0 // Will be calculated later
                                )
                            )
                        }
                    }

                    // Update end pages and page counts
                    for (i in chapters.indices) {
                        val endPage = if (i < chapters.size - 1) {
                            chapters[i + 1].startPage - 1
                        } else {
                            document.numberOfPages
                        }

                        chapters[i] = chapters[i].copy(
                            endPage = endPage,
                            pageCount = endPage - chapters[i].startPage + 1
                        )
                    }

                    break
                }
            }
        } catch (e: Exception) {
            Timber.w(e, "Error extracting chapters from TOC")
        }

        return chapters
    }

    /**
     * Detect chapters by scanning for chapter headings in the document
     */
    private fun detectChaptersByPattern(document: PDDocument): List<ChapterInfo> {
        val chapters = mutableListOf<ChapterInfo>()

        try {
            val stripper = PDFTextStripper()
            var chapterNum = 0

            for (pageNum in 1..document.numberOfPages) {
                stripper.startPage = pageNum
                stripper.endPage = pageNum

                val pageText = stripper.getText(document)
                val lines = pageText.split("\n").map { it.trim() }

                for (line in lines) {
                    // Check for "Chapter X" pattern
                    val chapterMatch = Regex(CHAPTER_PATTERN).matches(line)

                    if (chapterMatch) {
                        chapterNum++
                        chapters.add(
                            ChapterInfo(
                                number = chapterNum,
                                title = line,
                                startPage = pageNum,
                                endPage = pageNum, // Will be updated
                                pageCount = 0 // Will be calculated
                            )
                        )
                    }
                }
            }

            // Update end pages and validate
            val validChapters = mutableListOf<ChapterInfo>()
            for (i in chapters.indices) {
                val endPage = if (i < chapters.size - 1) {
                    chapters[i + 1].startPage - 1
                } else {
                    document.numberOfPages
                }

                val pageCount = endPage - chapters[i].startPage + 1

                // Filter out invalid chapters (too short or too long)
                if (pageCount in MIN_CHAPTER_PAGES..MAX_CHAPTER_PAGES) {
                    validChapters.add(
                        chapters[i].copy(
                            endPage = endPage,
                            pageCount = pageCount
                        )
                    )
                }
            }

            return validChapters
        } catch (e: Exception) {
            Timber.w(e, "Error detecting chapters by pattern")
            return emptyList()
        }
    }
}
