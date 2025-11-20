package com.cryptotrader.presentation.screens.positions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cryptotrader.data.repository.PositionRepository
import com.cryptotrader.domain.model.Position
import com.cryptotrader.domain.model.PositionStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class PositionManagementViewModel @Inject constructor(
    private val positionRepository: PositionRepository,
    private val focusModeManager: com.cryptotrader.utils.FocusModeManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(PositionManagementState())
    val uiState: StateFlow<PositionManagementState> = _uiState.asStateFlow()

    // Expose Focus Mode state
    val focusModeEnabled = focusModeManager.focusModeEnabled

    // Filters
    private val _filterStatus = MutableStateFlow<PositionFilter>(PositionFilter.OPEN)
    val currentFilter: StateFlow<PositionFilter> = _filterStatus.asStateFlow() // Expose for UI
    private val _searchQuery = MutableStateFlow("")

    // Combine flows to get the displayed positions
    val positions: StateFlow<List<Position>> = combine(
        _filterStatus,
        _searchQuery,
        positionRepository.getOpenPositionsFlow(),
        positionRepository.getClosedPositionsFlow(limit = 100) // Limit closed positions for performance
    ) { filter, query, openPositions, closedPositions ->
        
        val allPositions = when (filter) {
            PositionFilter.OPEN -> openPositions
            PositionFilter.CLOSED -> closedPositions
            PositionFilter.ALL -> openPositions + closedPositions
        }

        allPositions.filter { position ->
            position.pair.contains(query, ignoreCase = true)
        }.sortedByDescending { it.openedAt }
        
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        // Initial sync of prices for open positions
        refreshPrices()
    }

    fun setFilter(filter: PositionFilter) {
        _filterStatus.value = filter
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun refreshPrices() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                positionRepository.syncPositionPrices()
                _uiState.value = _uiState.value.copy(isLoading = false)
            } catch (e: Exception) {
                Timber.e(e, "Error syncing position prices")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to sync prices: ${e.message}"
                )
            }
        }
    }

    fun closePosition(positionId: String, currentPrice: Double) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val result = positionRepository.closePosition(
                    positionId = positionId,
                    exitPrice = currentPrice,
                    closeReason = "MANUAL_USER"
                )
                
                if (result.isSuccess) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        successMessage = "Position closed successfully"
                    )
                } else {
                    throw result.exceptionOrNull() ?: Exception("Unknown error")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error closing position")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to close position: ${e.message}"
                )
            }
        }
    }

    fun clearMessages() {
        _uiState.value = _uiState.value.copy(errorMessage = null, successMessage = null)
    }
}

enum class PositionFilter {
    OPEN, CLOSED, ALL
}

data class PositionManagementState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)
