package com.cryptotrader.presentation.screens.learning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cryptotrader.data.repository.KnowledgeBaseRepository
import com.cryptotrader.data.repository.LearningRepository
import com.cryptotrader.domain.model.KnowledgeBaseOverview
import com.cryptotrader.domain.model.KnowledgeTopic
import com.cryptotrader.domain.model.MasteryLevel
import com.cryptotrader.domain.model.TopicCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import timber.log.Timber
import java.time.LocalDateTime
import javax.inject.Inject

/**
 * ViewModel for Knowledge Base Screen
 * Manages knowledge topics and learning paths
 */
@HiltViewModel
class KnowledgeBaseViewModel @Inject constructor(
    private val knowledgeBaseRepository: KnowledgeBaseRepository,
    private val learningRepository: LearningRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(KnowledgeBaseUiState())
    val uiState: StateFlow<KnowledgeBaseUiState> = _uiState.asStateFlow()

    private val userId = "current_user" // TODO: Get from auth service

    // Internal state for filtering
    private val _selectedCategory = MutableStateFlow<TopicCategory?>(null)
    private val _searchQuery = MutableStateFlow("")
    private val _showOnlyUnstudied = MutableStateFlow(false)

    init {
        loadKnowledgeBase()
    }

    /**
     * Load knowledge base overview and topics
     */
    fun loadKnowledgeBase() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)

                // Get overview
                val overviewResult = knowledgeBaseRepository.getKnowledgeBaseOverview(userId)
                val overview = overviewResult.getOrNull()

                // Observe topics with filters
                combine(
                    knowledgeBaseRepository.getAllTopics(),
                    _selectedCategory,
                    _searchQuery,
                    _showOnlyUnstudied
                ) { topics, category, query, unstudiedOnly ->
                    var filtered = topics

                    // Filter by category
                    if (category != null) {
                        filtered = filtered.filter { it.category == category }
                    }

                    // Filter by search query
                    if (query.isNotBlank()) {
                        filtered = filtered.filter { topic ->
                            topic.name.contains(query, ignoreCase = true) ||
                                    topic.description.contains(query, ignoreCase = true)
                        }
                    }

                    // Filter by studied status
                    if (unstudiedOnly) {
                        filtered = filtered.filter { !it.isStudied }
                    }

                    // Group by category
                    filtered.groupBy { it.category }
                }.collect { topicsByCategory ->
                    _uiState.value = _uiState.value.copy(
                        overview = overview,
                        topicsByCategory = topicsByCategory,
                        isLoading = false
                    )
                }

            } catch (e: Exception) {
                Timber.e(e, "Error loading knowledge base")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load knowledge base: ${e.message}"
                )
            }
        }
    }

    /**
     * Filter topics by category
     */
    fun filterByCategory(category: TopicCategory?) {
        _selectedCategory.value = category
        _uiState.value = _uiState.value.copy(selectedCategory = category)
        Timber.d("Filtering by category: ${category?.name ?: "All"}")
    }

    /**
     * Search topics
     */
    fun searchTopics(query: String) {
        _searchQuery.value = query
        _uiState.value = _uiState.value.copy(searchQuery = query)
        Timber.d("Searching topics: $query")
    }

    /**
     * Toggle showing only unstudied topics
     */
    fun toggleUnstudiedFilter() {
        _showOnlyUnstudied.value = !_showOnlyUnstudied.value
        _uiState.value = _uiState.value.copy(showOnlyUnstudied = _showOnlyUnstudied.value)
        Timber.d("Show only unstudied: ${_showOnlyUnstudied.value}")
    }

    /**
     * Mark a topic as studied
     */
    fun markTopicStudied(topicId: String) {
        viewModelScope.launch {
            try {
                val result = knowledgeBaseRepository.markTopicStudied(topicId)
                if (result.isSuccess) {
                    Timber.i("Marked topic as studied: $topicId")
                    // Topics will auto-update through Flow
                } else {
                    _uiState.value = _uiState.value.copy(
                        error = "Failed to mark topic as studied: ${result.exceptionOrNull()?.message}"
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Error marking topic as studied")
                _uiState.value = _uiState.value.copy(
                    error = "Error marking topic as studied: ${e.message}"
                )
            }
        }
    }

    /**
     * Update mastery level for a topic
     */
    fun updateMasteryLevel(topicId: String, level: MasteryLevel) {
        viewModelScope.launch {
            try {
                val result = knowledgeBaseRepository.updateMasteryLevel(topicId, level)
                if (result.isSuccess) {
                    Timber.i("Updated mastery level for topic: $topicId to $level")
                    // Topics will auto-update through Flow
                } else {
                    _uiState.value = _uiState.value.copy(
                        error = "Failed to update mastery level: ${result.exceptionOrNull()?.message}"
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Error updating mastery level")
                _uiState.value = _uiState.value.copy(
                    error = "Error updating mastery level: ${e.message}"
                )
            }
        }
    }

    /**
     * Schedule a topic for review
     */
    fun scheduleReview(topicId: String, reviewDate: LocalDateTime) {
        viewModelScope.launch {
            try {
                val result = knowledgeBaseRepository.scheduleTopicReview(topicId, reviewDate)
                if (result.isSuccess) {
                    Timber.i("Scheduled review for topic: $topicId on $reviewDate")
                    // Topics will auto-update through Flow
                } else {
                    _uiState.value = _uiState.value.copy(
                        error = "Failed to schedule review: ${result.exceptionOrNull()?.message}"
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Error scheduling review")
                _uiState.value = _uiState.value.copy(
                    error = "Error scheduling review: ${e.message}"
                )
            }
        }
    }

    /**
     * Load topics due for review
     */
    fun loadReviewTopics() {
        viewModelScope.launch {
            try {
                knowledgeBaseRepository.getTopicsForReview().collect { reviewTopics ->
                    _uiState.value = _uiState.value.copy(reviewTopics = reviewTopics)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error loading review topics")
            }
        }
    }

    /**
     * Get books related to a topic
     */
    fun loadRelatedBooks(topicId: String) {
        viewModelScope.launch {
            try {
                knowledgeBaseRepository.getBooksForTopic(topicId).collect { books ->
                    _uiState.value = _uiState.value.copy(
                        selectedTopicBooks = books,
                        selectedTopicId = topicId
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Error loading related books")
            }
        }
    }

    /**
     * Generate a learning roadmap for target topics
     */
    fun generateRoadmap(targetTopicIds: List<String>) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isGeneratingRoadmap = true)

                val result = knowledgeBaseRepository.generateTopicRoadmap(userId, targetTopicIds)
                if (result.isSuccess) {
                    val roadmap = result.getOrNull()!!
                    Timber.i("Generated roadmap with ${roadmap.learningPath.size} steps")
                    _uiState.value = _uiState.value.copy(
                        isGeneratingRoadmap = false,
                        generatedRoadmap = roadmap,
                        showRoadmap = true
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isGeneratingRoadmap = false,
                        error = "Failed to generate roadmap: ${result.exceptionOrNull()?.message}"
                    )
                }

            } catch (e: Exception) {
                Timber.e(e, "Error generating roadmap")
                _uiState.value = _uiState.value.copy(
                    isGeneratingRoadmap = false,
                    error = "Error generating roadmap: ${e.message}"
                )
            }
        }
    }

    /**
     * Dismiss roadmap
     */
    fun dismissRoadmap() {
        _uiState.value = _uiState.value.copy(
            showRoadmap = false,
            generatedRoadmap = null
        )
    }

    /**
     * Record study time for a topic
     */
    fun recordStudyTime(topicId: String, minutes: Long) {
        viewModelScope.launch {
            try {
                val result = knowledgeBaseRepository.recordTopicStudyTime(topicId, minutes)
                if (result.isSuccess) {
                    Timber.d("Recorded $minutes minutes of study time for topic: $topicId")
                    // Topics will auto-update through Flow
                }
            } catch (e: Exception) {
                Timber.e(e, "Error recording study time")
            }
        }
    }

    /**
     * Create a new topic
     */
    fun createTopic(topic: KnowledgeTopic) {
        viewModelScope.launch {
            try {
                val result = knowledgeBaseRepository.createTopic(topic)
                if (result.isSuccess) {
                    Timber.i("Created new topic: ${topic.name}")
                    _uiState.value = _uiState.value.copy(
                        successMessage = "Topic created successfully!"
                    )
                    // Topics will auto-update through Flow
                } else {
                    _uiState.value = _uiState.value.copy(
                        error = "Failed to create topic: ${result.exceptionOrNull()?.message}"
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Error creating topic")
                _uiState.value = _uiState.value.copy(
                    error = "Error creating topic: ${e.message}"
                )
            }
        }
    }

    /**
     * Delete a topic
     */
    fun deleteTopic(topicId: String) {
        viewModelScope.launch {
            try {
                val result = knowledgeBaseRepository.deleteTopic(topicId)
                if (result.isSuccess) {
                    Timber.i("Deleted topic: $topicId")
                    // Topics will auto-update through Flow
                } else {
                    _uiState.value = _uiState.value.copy(
                        error = "Failed to delete topic: ${result.exceptionOrNull()?.message}"
                    )
                }
            } catch (e: Exception) {
                Timber.e(e, "Error deleting topic")
                _uiState.value = _uiState.value.copy(
                    error = "Error deleting topic: ${e.message}"
                )
            }
        }
    }

    /**
     * Expand/collapse a category
     */
    fun toggleCategoryExpansion(category: TopicCategory) {
        val currentExpanded = _uiState.value.expandedCategories.toMutableSet()
        if (currentExpanded.contains(category)) {
            currentExpanded.remove(category)
        } else {
            currentExpanded.add(category)
        }
        _uiState.value = _uiState.value.copy(expandedCategories = currentExpanded)
        Timber.d("Toggled expansion for category: ${category.name}")
    }

    /**
     * Clear messages
     */
    fun clearMessages() {
        _uiState.value = _uiState.value.copy(error = null, successMessage = null)
    }
}

/**
 * UI State for Knowledge Base Screen
 */
data class KnowledgeBaseUiState(
    val overview: KnowledgeBaseOverview? = null,
    val topicsByCategory: Map<TopicCategory, List<KnowledgeTopic>> = emptyMap(),
    val reviewTopics: List<KnowledgeTopic> = emptyList(),
    val selectedCategory: TopicCategory? = null,
    val searchQuery: String = "",
    val showOnlyUnstudied: Boolean = false,
    val expandedCategories: Set<TopicCategory> = emptySet(),
    val selectedTopicId: String? = null,
    val selectedTopicBooks: List<com.cryptotrader.domain.model.LearningBook> = emptyList(),
    val isLoading: Boolean = false,
    val isGeneratingRoadmap: Boolean = false,
    val generatedRoadmap: com.cryptotrader.data.repository.TopicRoadmap? = null,
    val showRoadmap: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null
)
