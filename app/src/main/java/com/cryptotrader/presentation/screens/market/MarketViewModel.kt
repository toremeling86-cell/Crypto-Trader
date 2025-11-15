package com.cryptotrader.presentation.screens.market

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cryptotrader.data.repository.MarketSnapshotRepository
import com.cryptotrader.domain.model.MarketSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class MarketViewModel @Inject constructor(
    private val marketSnapshotRepository: MarketSnapshotRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MarketUiState())
    val uiState: StateFlow<MarketUiState> = _uiState.asStateFlow()

    private var autoRefreshJob: Job? = null

    init {
        Timber.d("MarketViewModel initialized")
        refreshMarketData()
    }

    fun refreshMarketData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            val result = marketSnapshotRepository.fetchAndStoreMarketData(
                MarketSnapshotRepository.DEFAULT_WATCHLIST
            )

            if (result.isSuccess) {
                val snapshots = result.getOrNull() ?: emptyList()
                _uiState.value = _uiState.value.copy(
                    snapshots = snapshots.sortedByDescending { it.changePercent24h },
                    lastRefreshTime = System.currentTimeMillis(),
                    isLoading = false,
                    error = null
                )
                Timber.d("Market data refreshed: ${snapshots.size} snapshots")
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = result.exceptionOrNull()?.message ?: "Unknown error"
                )
                Timber.e("Failed to refresh market data: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    fun startAutoRefresh() {
        if (autoRefreshJob?.isActive == true) {
            Timber.d("Auto-refresh already running")
            return
        }

        autoRefreshJob = viewModelScope.launch {
            while (true) {
                delay(AUTO_REFRESH_INTERVAL_MS)
                Timber.d("Auto-refreshing market data...")
                refreshMarketData()
            }
        }
        Timber.d("Auto-refresh started (every ${AUTO_REFRESH_INTERVAL_MS / 1000}s)")
    }

    fun stopAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = null
        Timber.d("Auto-refresh stopped")
    }

    override fun onCleared() {
        super.onCleared()
        stopAutoRefresh()
    }

    companion object {
        private const val AUTO_REFRESH_INTERVAL_MS = 30_000L // 30 seconds
    }
}

data class MarketUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val snapshots: List<MarketSnapshot> = emptyList(),
    val lastRefreshTime: Long? = null
)
