package com.cryptotrader.presentation.screens.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cryptotrader.data.repository.CryptoReportRepository
import com.cryptotrader.domain.model.ExpertReport
import com.cryptotrader.domain.model.ReportCategory
import com.cryptotrader.domain.model.ReportSentiment
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for Reports Library Screen
 *
 * Manages expert reports with filtering, searching, and sorting capabilities
 */
@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val cryptoReportRepository: CryptoReportRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReportsState())
    val uiState: StateFlow<ReportsState> = _uiState.asStateFlow()

    // Filter states
    private val _selectedCategory = MutableStateFlow<ReportCategory?>(null)
    private val _selectedSentiment = MutableStateFlow<ReportSentiment?>(null)
    private val _selectedAsset = MutableStateFlow<String?>(null)
    private val _showOnlyUnanalyzed = MutableStateFlow(false)
    private val _searchQuery = MutableStateFlow("")
    private val _sortBy = MutableStateFlow(SortOption.NEWEST_FIRST)

    init {
        observeReports()
    }

    /**
     * Observe reports with reactive filtering
     */
    private fun observeReports() {
        viewModelScope.launch {
            combine(
                cryptoReportRepository.getAllReports(),
                _selectedCategory,
                _selectedSentiment,
                _selectedAsset,
                _showOnlyUnanalyzed,
                _searchQuery,
                _sortBy
            ) { flows ->
                val reports = flows[0] as List<ExpertReport>
                val category = flows[1] as ReportCategory?
                val sentiment = flows[2] as ReportSentiment?
                val asset = flows[3] as String?
                val onlyUnanalyzed = flows[4] as Boolean
                val query = flows[5] as String
                val sortBy = flows[6] as SortOption

                var filtered = reports

                // Filter by category
                if (category != null) {
                    filtered = filtered.filter { it.category == category }
                }

                // Filter by sentiment
                if (sentiment != null) {
                    filtered = filtered.filter { it.sentiment == sentiment }
                }

                // Filter by asset
                if (asset != null) {
                    filtered = filtered.filter { report ->
                        report.assets.any { it.equals(asset, ignoreCase = true) }
                    }
                }

                // Filter by analysis status
                if (onlyUnanalyzed) {
                    filtered = filtered.filter { !it.analyzed }
                }

                // Search filter
                if (query.isNotBlank()) {
                    filtered = filtered.filter { report ->
                        report.title.contains(query, ignoreCase = true) ||
                        report.content.contains(query, ignoreCase = true) ||
                        report.author?.contains(query, ignoreCase = true) == true
                    }
                }

                // Sort
                filtered = when (sortBy) {
                    SortOption.NEWEST_FIRST -> filtered.sortedByDescending { it.publishedDate ?: it.uploadDate }
                    SortOption.OLDEST_FIRST -> filtered.sortedBy { it.publishedDate ?: it.uploadDate }
                    SortOption.HIGHEST_IMPACT -> filtered.sortedByDescending { it.impactScore ?: 0.0 }
                    SortOption.MOST_USED -> filtered.sortedByDescending { it.usedInStrategies }
                }

                // Get unique assets for filter dropdown
                val allAssets = reports.flatMap { it.assets }.distinct().sorted()

                ReportsState(
                    reports = filtered,
                    availableAssets = allAssets,
                    isLoading = false,
                    totalReports = reports.size,
                    analyzedCount = reports.count { it.analyzed },
                    unanalyzedCount = reports.count { !it.analyzed },
                    selectedCategory = category,
                    selectedSentiment = sentiment,
                    selectedAsset = asset,
                    showOnlyUnanalyzed = onlyUnanalyzed,
                    searchQuery = query,
                    sortBy = sortBy
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }

    /**
     * Update filters
     */
    fun setCategory(category: ReportCategory?) {
        _selectedCategory.value = category
    }

    fun setSentiment(sentiment: ReportSentiment?) {
        _selectedSentiment.value = sentiment
    }

    fun setAsset(asset: String?) {
        _selectedAsset.value = asset
    }

    fun toggleUnanalyzedOnly() {
        _showOnlyUnanalyzed.value = !_showOnlyUnanalyzed.value
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setSortBy(sortBy: SortOption) {
        _sortBy.value = sortBy
    }

    fun clearFilters() {
        _selectedCategory.value = null
        _selectedSentiment.value = null
        _selectedAsset.value = null
        _showOnlyUnanalyzed.value = false
        _searchQuery.value = ""
    }

    /**
     * Manually trigger report import scan
     */
    fun scanForNewReports() {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isScanning = true)
                val importedCount = cryptoReportRepository.scanAndImportNewReports()
                _uiState.value = _uiState.value.copy(
                    isScanning = false,
                    scanMessage = if (importedCount > 0) {
                        "Imported $importedCount new reports"
                    } else {
                        "No new reports found"
                    }
                )
                Timber.i("Manual scan completed: $importedCount reports imported")
            } catch (e: Exception) {
                Timber.e(e, "Error scanning for reports")
                _uiState.value = _uiState.value.copy(
                    isScanning = false,
                    errorMessage = "Scan failed: ${e.message}"
                )
            }
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(
            scanMessage = null,
            errorMessage = null
        )
    }
}

/**
 * UI State for Reports Screen
 */
data class ReportsState(
    val reports: List<ExpertReport> = emptyList(),
    val availableAssets: List<String> = emptyList(),
    val isLoading: Boolean = true,
    val isScanning: Boolean = false,
    val totalReports: Int = 0,
    val analyzedCount: Int = 0,
    val unanalyzedCount: Int = 0,

    // Filters
    val selectedCategory: ReportCategory? = null,
    val selectedSentiment: ReportSentiment? = null,
    val selectedAsset: String? = null,
    val showOnlyUnanalyzed: Boolean = false,
    val searchQuery: String = "",
    val sortBy: SortOption = SortOption.NEWEST_FIRST,

    // Messages
    val scanMessage: String? = null,
    val errorMessage: String? = null
)

/**
 * Sort options for reports
 */
enum class SortOption(val displayName: String) {
    NEWEST_FIRST("Newest First"),
    OLDEST_FIRST("Oldest First"),
    HIGHEST_IMPACT("Highest Impact"),
    MOST_USED("Most Used");
}
