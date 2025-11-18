package com.cryptotrader.presentation.screens.learning

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cryptotrader.data.repository.LearningRepository
import com.cryptotrader.domain.model.LearningBook
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * ViewModel for Learning Home/Dashboard Screen
 * Manages overall learning statistics and recent books
 */
@HiltViewModel
class LearningViewModel @Inject constructor(
    private val learningRepository: LearningRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LearningUiState())
    val uiState: StateFlow<LearningUiState> = _uiState.asStateFlow()

    init {
        loadDashboardData()
    }

    /**
     * Load dashboard data including recent books and statistics
     */
    fun loadDashboardData() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)

                // Get recent books (top 5 by last opened)
                val allBooks = learningRepository.getAllBooks().first()
                val recentBooks = allBooks
                    .filter { it.lastOpenedDate != null }
                    .sortedByDescending { it.lastOpenedDate }
                    .take(5)

                // Get learning statistics
                val userId = "current_user" // TODO: Get from auth service
                val stats = learningRepository.getLearningStatistics(userId).getOrNull()

                // Calculate current streak
                val streakResult = learningRepository.getStudyStreak(userId)
                val currentStreak = streakResult.getOrNull() ?: 0

                // Calculate total study hours since account creation
                val since = LocalDateTime.now().minus(90, ChronoUnit.DAYS) // Last 90 days
                val totalMinutes = learningRepository.getTotalStudyTime(userId, since).getOrNull() ?: 0L
                val hoursStudied = totalMinutes / 60.0

                _uiState.value = _uiState.value.copy(
                    recentBooks = recentBooks,
                    currentStreak = currentStreak,
                    totalBooksRead = stats?.completedBooks ?: 0,
                    hoursStudied = hoursStudied,
                    isLoading = false
                )

                Timber.d("Dashboard loaded: ${recentBooks.size} recent books, $currentStreak day streak")

            } catch (e: Exception) {
                Timber.e(e, "Error loading dashboard data")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load dashboard: ${e.message}"
                )
            }
        }
    }

    /**
     * Upload a PDF file
     */
    fun uploadPdf(uri: Uri) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)

                // TODO: Implement PDF upload
                // This will require:
                // 1. Convert Uri to File
                // 2. Extract metadata (title, author, page count)
                // 3. Call learningRepository.uploadBook()
                // 4. Trigger AI analysis

                Timber.i("PDF upload initiated: $uri")

                // Placeholder - in real implementation:
                // val file = uriToFile(uri)
                // val result = learningRepository.uploadBook(file, title, author, category)
                // if (result.isSuccess) {
                //     val book = result.getOrNull()!!
                //     // Trigger analysis
                //     learningRepository.analyzeBook(book.id)
                //     refreshData()
                // }

                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "PDF upload not yet implemented"
                )

            } catch (e: Exception) {
                Timber.e(e, "Error uploading PDF")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to upload PDF: ${e.message}"
                )
            }
        }
    }

    /**
     * Refresh dashboard data
     */
    fun refreshData() {
        loadDashboardData()
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

/**
 * UI State for Learning Home Screen
 */
data class LearningUiState(
    val recentBooks: List<LearningBook> = emptyList(),
    val currentStreak: Int = 0,
    val totalBooksRead: Int = 0,
    val hoursStudied: Double = 0.0,
    val isLoading: Boolean = false,
    val error: String? = null
)
