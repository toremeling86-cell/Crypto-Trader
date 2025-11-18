package com.cryptotrader.presentation.screens.learning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cryptotrader.data.repository.LearningRepository
import com.cryptotrader.domain.model.BookCategory
import com.cryptotrader.domain.model.BookWithAnalysis
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for Library Screen
 * Manages book collection with filtering, searching, and sorting
 */
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val learningRepository: LearningRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    // Internal state for filtering
    private val _selectedCategory = MutableStateFlow<BookCategory?>(null)
    private val _searchQuery = MutableStateFlow("")
    private val _sortBy = MutableStateFlow(SortOption.RECENT)

    init {
        loadBooks()
    }

    /**
     * Load all books and observe changes
     */
    fun loadBooks() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)

                // Combine books with filter/search/sort criteria
                combine(
                    learningRepository.getAllBooks(),
                    _selectedCategory,
                    _searchQuery,
                    _sortBy
                ) { books, category, query, sortOption ->
                    // Create BookWithAnalysis objects
                    val booksWithAnalysis = books.map { book ->
                        val analysis = learningRepository.getBookAnalysis(book.id).getOrNull()
                        val evaluation = learningRepository.getBookEvaluation(book.id).getOrNull()
                        val userId = "current_user" // TODO: Get from auth
                        val progress = learningRepository.getStudyProgress(userId, book.id).getOrNull()

                        BookWithAnalysis(
                            book = book,
                            analysis = analysis,
                            evaluation = evaluation,
                            progress = progress
                        )
                    }

                    // Apply filters
                    var filtered = booksWithAnalysis

                    // Filter by category
                    if (category != null) {
                        filtered = filtered.filter { it.book.category == category }
                    }

                    // Filter by search query
                    if (query.isNotBlank()) {
                        filtered = filtered.filter { bookWithAnalysis ->
                            val book = bookWithAnalysis.book
                            book.title.contains(query, ignoreCase = true) ||
                                    book.author?.contains(query, ignoreCase = true) == true ||
                                    book.tags.any { it.contains(query, ignoreCase = true) }
                        }
                    }

                    // Apply sorting
                    filtered = when (sortOption) {
                        SortOption.RECENT -> filtered.sortedByDescending {
                            it.book.lastOpenedDate ?: it.book.uploadDate
                        }
                        SortOption.TITLE_AZ -> filtered.sortedBy { it.book.title }
                        SortOption.TITLE_ZA -> filtered.sortedByDescending { it.book.title }
                        SortOption.PROGRESS -> filtered.sortedByDescending { it.book.readingProgress }
                        SortOption.RATING -> filtered.sortedByDescending {
                            it.evaluation?.overallRating ?: 0f
                        }
                    }

                    filtered
                }.collect { filteredBooks ->
                    _uiState.value = _uiState.value.copy(
                        books = filteredBooks,
                        filteredBooks = filteredBooks,
                        isLoading = false
                    )
                }

            } catch (e: Exception) {
                Timber.e(e, "Error loading books")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load library: ${e.message}"
                )
            }
        }
    }

    /**
     * Filter books by category
     */
    fun filterByCategory(category: BookCategory?) {
        _selectedCategory.value = category
        _uiState.value = _uiState.value.copy(selectedCategory = category)
        Timber.d("Filtering by category: ${category?.name ?: "All"}")
    }

    /**
     * Search books
     */
    fun searchBooks(query: String) {
        _searchQuery.value = query
        _uiState.value = _uiState.value.copy(searchQuery = query)
        Timber.d("Searching books: $query")
    }

    /**
     * Sort books
     */
    fun sortBooks(option: SortOption) {
        _sortBy.value = option
        _uiState.value = _uiState.value.copy(sortBy = option)
        Timber.d("Sorting by: ${option.name}")
    }

    /**
     * Toggle view mode between grid and list
     */
    fun toggleViewMode() {
        val newMode = when (_uiState.value.viewMode) {
            ViewMode.GRID -> ViewMode.LIST
            ViewMode.LIST -> ViewMode.GRID
        }
        _uiState.value = _uiState.value.copy(viewMode = newMode)
        Timber.d("View mode changed to: ${newMode.name}")
    }

    /**
     * Delete a book
     */
    fun deleteBook(bookId: String) {
        viewModelScope.launch {
            try {
                val result = learningRepository.deleteBook(bookId)
                if (result.isSuccess) {
                    Timber.i("Book deleted: $bookId")
                    // Books will automatically update through Flow
                } else {
                    val error = result.exceptionOrNull()
                    Timber.e(error, "Failed to delete book")
                    _uiState.value = _uiState.value.copy(
                        error = "Failed to delete book: ${error?.message}"
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Error deleting book")
                _uiState.value = _uiState.value.copy(
                    error = "Error deleting book: ${e.message}"
                )
            }
        }
    }

    /**
     * Toggle favorite status
     */
    fun toggleFavorite(bookId: String) {
        viewModelScope.launch {
            try {
                val result = learningRepository.toggleBookFavorite(bookId)
                if (result.isSuccess) {
                    Timber.d("Toggled favorite for book: $bookId")
                    // Books will automatically update through Flow
                } else {
                    Timber.e("Failed to toggle favorite")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error toggling favorite")
            }
        }
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

/**
 * UI State for Library Screen
 */
data class LibraryUiState(
    val books: List<BookWithAnalysis> = emptyList(),
    val filteredBooks: List<BookWithAnalysis> = emptyList(),
    val selectedCategory: BookCategory? = null,
    val searchQuery: String = "",
    val sortBy: SortOption = SortOption.RECENT,
    val viewMode: ViewMode = ViewMode.GRID,
    val isLoading: Boolean = false,
    val error: String? = null
)

/**
 * Sort options for library
 */
enum class SortOption {
    RECENT,
    TITLE_AZ,
    TITLE_ZA,
    PROGRESS,
    RATING
}

/**
 * View mode for library
 */
enum class ViewMode {
    GRID,
    LIST
}
