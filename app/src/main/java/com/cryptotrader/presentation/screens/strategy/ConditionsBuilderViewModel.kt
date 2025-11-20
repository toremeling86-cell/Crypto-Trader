package com.cryptotrader.presentation.screens.strategy

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class ConditionsBuilderViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(ConditionsBuilderState())
    val uiState: StateFlow<ConditionsBuilderState> = _uiState.asStateFlow()

    fun addEntryCondition(condition: String) {
        if (condition.isNotBlank()) {
            _uiState.value = _uiState.value.copy(
                entryConditions = _uiState.value.entryConditions + condition
            )
        }
    }

    fun removeEntryCondition(index: Int) {
        _uiState.value = _uiState.value.copy(
            entryConditions = _uiState.value.entryConditions.filterIndexed { i, _ -> i != index }
        )
    }

    fun addExitCondition(condition: String) {
        if (condition.isNotBlank()) {
            _uiState.value = _uiState.value.copy(
                exitConditions = _uiState.value.exitConditions + condition
            )
        }
    }

    fun removeExitCondition(index: Int) {
        _uiState.value = _uiState.value.copy(
            exitConditions = _uiState.value.exitConditions.filterIndexed { i, _ -> i != index }
        )
    }

    fun clearAll() {
        _uiState.value = ConditionsBuilderState()
    }
}

data class ConditionsBuilderState(
    val entryConditions: List<String> = emptyList(),
    val exitConditions: List<String> = emptyList()
)
