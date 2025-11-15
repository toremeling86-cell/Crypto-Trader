package com.cryptotrader.presentation.screens.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cryptotrader.data.local.dao.AIMarketAnalysisDao
import com.cryptotrader.data.local.entities.AIMarketAnalysisEntity
import com.cryptotrader.domain.ai.ClaudeMarketAnalyzer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * ViewModel for AI Market Analysis tab
 *
 * Manages:
 * - Display of latest market analysis from Claude
 * - Manual "Analyze Now" trigger
 * - Analysis history
 */
@HiltViewModel
class AnalysisViewModel @Inject constructor(
    private val claudeMarketAnalyzer: ClaudeMarketAnalyzer,
    private val aiMarketAnalysisDao: AIMarketAnalysisDao
) : ViewModel() {

    // UI State
    private val _uiState = MutableStateFlow(AnalysisUiState())
    val uiState: StateFlow<AnalysisUiState> = _uiState.asStateFlow()

    // Latest analysis from database
    val latestAnalysis: StateFlow<AIMarketAnalysisEntity?> = aiMarketAnalysisDao
        .getLatestAnalysisFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    // Analysis history (last 10)
    val analysisHistory: StateFlow<List<AIMarketAnalysisEntity>> = aiMarketAnalysisDao
        .getRecentAnalyses(limit = 10)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        Timber.d("AnalysisViewModel initialized")
    }

    /**
     * Trigger manual market analysis
     */
    fun analyzeMarket() {
        viewModelScope.launch {
            _uiState.update { it.copy(isAnalyzing = true, error = null) }

            Timber.i("Manual market analysis triggered by user")

            val result = claudeMarketAnalyzer.analyzeMarket(
                triggerType = "MANUAL",
                includeExpertReports = false
            )

            if (result.isSuccess) {
                val analysisId = result.getOrNull()
                Timber.i("Market analysis completed successfully (ID: $analysisId)")
                _uiState.update { it.copy(isAnalyzing = false, error = null) }
            } else {
                val error = result.exceptionOrNull()?.message ?: "Unknown error"
                Timber.e("Market analysis failed: $error")
                _uiState.update { it.copy(isAnalyzing = false, error = error) }
            }
        }
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}

/**
 * UI state for Analysis screen
 */
data class AnalysisUiState(
    val isAnalyzing: Boolean = false,
    val error: String? = null
)
