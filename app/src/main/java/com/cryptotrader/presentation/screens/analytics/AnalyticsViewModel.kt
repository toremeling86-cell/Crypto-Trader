package com.cryptotrader.presentation.screens.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cryptotrader.data.repository.MarketSnapshotRepository
import com.cryptotrader.domain.model.MarketSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val marketSnapshotRepository: MarketSnapshotRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AnalyticsUiState())
    val uiState: StateFlow<AnalyticsUiState> = _uiState.asStateFlow()

    init {
        // Initial data load will happen when we implement each tab
        Timber.d("AnalyticsViewModel initialized")
    }

    fun refreshMarketData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            val result = marketSnapshotRepository.fetchAndStoreMarketData(
                MarketSnapshotRepository.DEFAULT_WATCHLIST
            )

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                error = if (result.isFailure) result.exceptionOrNull()?.message else null
            )
        }
    }
}

data class AnalyticsUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val marketSnapshots: List<MarketSnapshot> = emptyList(),
    val lastRefreshTime: Long? = null
)
