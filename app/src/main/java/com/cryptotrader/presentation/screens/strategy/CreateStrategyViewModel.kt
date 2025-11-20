package com.cryptotrader.presentation.screens.strategy

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cryptotrader.data.repository.StrategyRepository
import com.cryptotrader.domain.model.Strategy
import com.cryptotrader.domain.model.StrategySource
import com.cryptotrader.domain.model.TradingMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class CreateStrategyViewModel @Inject constructor(
    private val strategyRepository: StrategyRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateStrategyState())
    val uiState: StateFlow<CreateStrategyState> = _uiState.asStateFlow()

    fun updateName(name: String) {
        _uiState.value = _uiState.value.copy(name = name)
    }

    fun updateDescription(description: String) {
        _uiState.value = _uiState.value.copy(description = description)
    }

    fun updatePairs(pairs: String) {
        _uiState.value = _uiState.value.copy(pairs = pairs)
    }

    fun updateStopLoss(stopLoss: String) {
        _uiState.value = _uiState.value.copy(stopLoss = stopLoss)
    }

    fun updateTakeProfit(takeProfit: String) {
        _uiState.value = _uiState.value.copy(takeProfit = takeProfit)
    }

    fun updatePositionSize(positionSize: String) {
        _uiState.value = _uiState.value.copy(positionSize = positionSize)
    }

    fun importFromAi(strategy: Strategy) {
        _uiState.value = _uiState.value.copy(
            name = strategy.name,
            description = strategy.description,
            pairs = strategy.tradingPairs.joinToString(", "),
            stopLoss = strategy.stopLossPercent.toString(),
            takeProfit = strategy.takeProfitPercent.toString(),
            positionSize = strategy.positionSizePercent.toString(),
            isAiGenerated = true,
            aiSource = strategy.source.name
        )
    }

    fun saveStrategy() {
        viewModelScope.launch {
            try {
                val state = _uiState.value
                
                // Basic validation
                if (state.name.isBlank()) {
                    _uiState.value = state.copy(errorMessage = "Name is required")
                    return@launch
                }

                val pairsList = state.pairs.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                if (pairsList.isEmpty()) {
                    _uiState.value = state.copy(errorMessage = "At least one trading pair is required")
                    return@launch
                }

                val stopLoss = state.stopLoss.toDoubleOrNull()
                val takeProfit = state.takeProfit.toDoubleOrNull()
                val positionSize = state.positionSize.toDoubleOrNull()

                if (stopLoss == null || takeProfit == null || positionSize == null) {
                    _uiState.value = state.copy(errorMessage = "Invalid numeric values")
                    return@launch
                }

                val strategy = Strategy(
                    id = UUID.randomUUID().toString(),
                    name = state.name,
                    description = state.description,
                    tradingPairs = pairsList,
                    stopLossPercent = stopLoss,
                    takeProfitPercent = takeProfit,
                    positionSizePercent = positionSize,
                    entryConditions = emptyList(), // TODO: Add UI for conditions
                    exitConditions = emptyList(),
                    isActive = false,
                    tradingMode = TradingMode.INACTIVE,
                    source = if (state.isAiGenerated) StrategySource.AI_CLAUDE else StrategySource.MANUAL
                )

                strategyRepository.insertStrategy(strategy)
                _uiState.value = state.copy(successMessage = "Strategy saved successfully!")
                
            } catch (e: Exception) {
                Timber.e(e, "Error saving strategy")
                _uiState.value = _uiState.value.copy(errorMessage = "Failed to save: ${e.message}")
            }
        }
    }
    
    fun clearMessages() {
        _uiState.value = _uiState.value.copy(errorMessage = null, successMessage = null)
    }
}

data class CreateStrategyState(
    val name: String = "",
    val description: String = "",
    val pairs: String = "",
    val stopLoss: String = "",
    val takeProfit: String = "",
    val positionSize: String = "",
    val isAiGenerated: Boolean = false,
    val aiSource: String? = null,
    val errorMessage: String? = null,
    val successMessage: String? = null
)
