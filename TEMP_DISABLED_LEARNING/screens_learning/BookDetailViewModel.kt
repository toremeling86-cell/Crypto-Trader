package com.cryptotrader.presentation.screens.learning

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cryptotrader.data.repository.LearningRepository
import com.cryptotrader.domain.model.BookWithAnalysis
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for Book Detail Screen
 * Manages detailed book information, analysis, and reading progress
 */
@HiltViewModel
class BookDetailViewModel @Inject constructor(
    private val learningRepository: LearningRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val _uiState = MutableStateFlow(BookDetailUiState())
    val uiState: StateFlow<BookDetailUiState> = _uiState.asStateFlow()

    private val bookId: String? = savedStateHandle["bookId"]

    init {
        bookId?.let { loadBookDetails(it) }
    }

    /**
     * Load complete book details with analysis
     */
    fun loadBookDetails(bookId: String) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)

                // Get book
                val bookResult = learningRepository.getBookById(bookId)
                if (bookResult.isFailure || bookResult.getOrNull() == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Book not found"
                    )
                    return@launch
                }

                val book = bookResult.getOrNull()!!

                // Get analysis
                val analysis = learningRepository.getBookAnalysis(bookId).getOrNull()

                // Get evaluation
                val evaluation = learningRepository.getBookEvaluation(bookId).getOrNull()

                // Get progress
                val userId = "current_user" // TODO: Get from auth
                val progress = learningRepository.getStudyProgress(userId, bookId).getOrNull()

                // Create BookWithAnalysis
                val bookWithAnalysis = BookWithAnalysis(
                    book = book,
                    analysis = analysis,
                    evaluation = evaluation,
                    progress = progress
                )

                _uiState.value = _uiState.value.copy(
                    book = bookWithAnalysis,
                    isLoading = false
                )

                Timber.d("Loaded book details: ${book.title}")

                // Trigger AI analysis if not analyzed yet
                if (analysis == null && book.analysisStatus != com.cryptotrader.domain.model.AnalysisStatus.ANALYZING) {
                    triggerAnalysis(bookId)
                }

            } catch (e: Exception) {
                Timber.e(e, "Error loading book details")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load book: ${e.message}"
                )
            }
        }
    }

    /**
     * Trigger AI analysis for the book
     */
    private fun triggerAnalysis(bookId: String) {
        viewModelScope.launch {
            try {
                Timber.i("Triggering AI analysis for book: $bookId")

                // Start book analysis
                val analysisResult = learningRepository.analyzeBook(bookId)
                if (analysisResult.isSuccess) {
                    Timber.i("AI analysis completed successfully")
                    // Reload book details to get the analysis
                    loadBookDetails(bookId)
                } else {
                    Timber.e("AI analysis failed: ${analysisResult.exceptionOrNull()?.message}")
                }

                // Also trigger evaluation
                val evaluationResult = learningRepository.evaluateBookQuality(bookId)
                if (evaluationResult.isSuccess) {
                    Timber.i("Book evaluation completed successfully")
                    loadBookDetails(bookId)
                } else {
                    Timber.e("Book evaluation failed: ${evaluationResult.exceptionOrNull()?.message}")
                }

            } catch (e: Exception) {
                Timber.e(e, "Error triggering analysis")
            }
        }
    }

    /**
     * Select a tab
     */
    fun selectTab(index: Int) {
        _uiState.value = _uiState.value.copy(selectedTab = index)
        Timber.d("Selected tab: $index")
    }

    /**
     * Start reading the book (navigate to reading screen)
     */
    fun startReading() {
        viewModelScope.launch {
            try {
                val book = _uiState.value.book?.book ?: return@launch

                // Update last opened date
                // This will be handled by navigation to reading screen
                Timber.i("Starting reading: ${book.title}")

                // TODO: Navigate to reading screen
                // navigationService.navigateToReader(book.id)

            } catch (e: Exception) {
                Timber.e(e, "Error starting reading")
                _uiState.value = _uiState.value.copy(
                    error = "Failed to start reading: ${e.message}"
                )
            }
        }
    }

    /**
     * Mark a chapter as complete
     */
    fun markChapterComplete(chapterId: String) {
        viewModelScope.launch {
            try {
                val book = _uiState.value.book?.book ?: return@launch
                val userId = "current_user" // TODO: Get from auth

                // Get current study progress
                val progress = learningRepository.getStudyProgress(userId, book.id).getOrNull()

                // Find chapter number from analysis
                val analysis = _uiState.value.book?.analysis
                val chapterNumber = analysis?.chapterSummaries?.find { it.id == chapterId }?.chapterNumber

                if (chapterNumber != null) {
                    // Update reading position
                    learningRepository.updateReadingPosition(
                        userId = userId,
                        bookId = book.id,
                        chapter = chapterNumber,
                        page = null // Will be set by reading screen
                    )

                    Timber.i("Marked chapter $chapterNumber as complete")

                    // Reload to update progress
                    loadBookDetails(book.id)
                }

            } catch (e: Exception) {
                Timber.e(e, "Error marking chapter complete")
                _uiState.value = _uiState.value.copy(
                    error = "Failed to update progress: ${e.message}"
                )
            }
        }
    }

    /**
     * Generate study plan for this book
     */
    fun generateStudyPlan(dailyCommitmentMinutes: Int, targetWeeks: Int) {
        viewModelScope.launch {
            try {
                val book = _uiState.value.book?.book ?: return@launch
                val userId = "current_user" // TODO: Get from auth

                _uiState.value = _uiState.value.copy(isGeneratingPlan = true)

                val result = learningRepository.generateStudyPlan(
                    bookId = book.id,
                    userId = userId,
                    dailyCommitmentMinutes = dailyCommitmentMinutes,
                    targetCompletionWeeks = targetWeeks
                )

                if (result.isSuccess) {
                    val studyPlan = result.getOrNull()!!

                    // Save the study plan
                    learningRepository.createStudyPlan(studyPlan)

                    Timber.i("Generated study plan: ${studyPlan.title}")

                    _uiState.value = _uiState.value.copy(
                        isGeneratingPlan = false,
                        successMessage = "Study plan created!"
                    )

                    // Reload to show the plan
                    loadBookDetails(book.id)
                } else {
                    _uiState.value = _uiState.value.copy(
                        isGeneratingPlan = false,
                        error = "Failed to generate study plan: ${result.exceptionOrNull()?.message}"
                    )
                }

            } catch (e: Exception) {
                Timber.e(e, "Error generating study plan")
                _uiState.value = _uiState.value.copy(
                    isGeneratingPlan = false,
                    error = "Error generating study plan: ${e.message}"
                )
            }
        }
    }

    /**
     * Toggle favorite status
     */
    fun toggleFavorite() {
        viewModelScope.launch {
            try {
                val bookId = _uiState.value.book?.book?.id ?: return@launch

                val result = learningRepository.toggleBookFavorite(bookId)
                if (result.isSuccess) {
                    Timber.d("Toggled favorite status")
                    loadBookDetails(bookId)
                }

            } catch (e: Exception) {
                Timber.e(e, "Error toggling favorite")
            }
        }
    }

    /**
     * Clear messages
     */
    fun clearMessages() {
        _uiState.value = _uiState.value.copy(error = null, successMessage = null)
    }
}

/**
 * UI State for Book Detail Screen
 */
data class BookDetailUiState(
    val book: BookWithAnalysis? = null,
    val selectedTab: Int = 0,
    val isLoading: Boolean = false,
    val isGeneratingPlan: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)
