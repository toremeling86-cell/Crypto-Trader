package com.cryptotrader.presentation.screens.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cryptotrader.data.repository.KrakenRepository
import com.cryptotrader.domain.model.Order
import com.cryptotrader.domain.model.OrderStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class OrderManagementViewModel @Inject constructor(
    private val krakenRepository: KrakenRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(OrderManagementState())
    val uiState: StateFlow<OrderManagementState> = _uiState.asStateFlow()

    // Filters
    private val _filterStatus = MutableStateFlow<OrderStatus?>(null)
    private val _filterPair = MutableStateFlow<String?>(null)

    val orders: StateFlow<List<Order>> = combine(
        krakenRepository.getRecentOrders(),
        _filterStatus,
        _filterPair
    ) { allOrders, status, pair ->
        allOrders.filter { order ->
            (status == null || order.status == status) &&
            (pair == null || order.pair.contains(pair, ignoreCase = true))
        }.sortedByDescending { it.placedAt }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setStatusFilter(status: OrderStatus?) {
        _filterStatus.value = status
    }

    fun setPairFilter(pair: String?) {
        _filterPair.value = if (pair.isNullOrBlank()) null else pair
    }

    fun cancelOrder(orderId: String) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true)
                val result = krakenRepository.cancelOrder(orderId)
                if (result.isSuccess) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        successMessage = "Order $orderId cancelled successfully"
                    )
                    // No need to manually refresh, Flow will update automatically from DB
                } else {
                    throw result.exceptionOrNull() ?: Exception("Unknown error")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error cancelling order")
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Failed to cancel order: ${e.message}"
                )
            }
        }
    }
    
    fun clearMessages() {
        _uiState.value = _uiState.value.copy(errorMessage = null, successMessage = null)
    }
}

data class OrderManagementState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)
