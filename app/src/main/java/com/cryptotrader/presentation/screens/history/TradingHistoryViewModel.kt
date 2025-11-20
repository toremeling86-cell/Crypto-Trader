package com.cryptotrader.presentation.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cryptotrader.data.local.dao.TradeDao
import com.cryptotrader.domain.model.Trade
import com.cryptotrader.domain.model.TradeStatus
import com.cryptotrader.domain.model.TradeType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class TradingHistoryViewModel @Inject constructor(
    private val tradeDao: TradeDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(TradingHistoryState())
    val uiState: StateFlow<TradingHistoryState> = _uiState.asStateFlow()

    // Filters
    private val _filterPair = MutableStateFlow<String?>(null)
    private val _filterStrategy = MutableStateFlow<String?>(null)

    // Combined flow of trades with filters
    val trades: StateFlow<List<Trade>> = combine(
        tradeDao.getAllTradesFlow(),
        _filterPair,
        _filterStrategy
    ) { allTrades, pair, strategyId ->
        val domainTrades = allTrades.map { it.toDomain() }

        domainTrades.filter { trade ->
            (pair == null || trade.pair.contains(pair, ignoreCase = true)) &&
            (strategyId == null || trade.strategyId == strategyId)
        }.sortedByDescending { it.timestamp } // Newest first

    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setFilterPair(pair: String?) {
        _filterPair.value = if (pair.isNullOrBlank()) null else pair
    }

    fun setFilterStrategy(strategyId: String?) {
        _filterStrategy.value = strategyId
    }

    fun clearFilters() {
        _filterPair.value = null
        _filterStrategy.value = null
    }

    private fun com.cryptotrader.data.local.entities.TradeEntity.toDomain() = Trade(
        id = id,
        orderId = orderId,
        pair = pair,
        type = TradeType.fromString(type),
        price = price,
        volume = volume,
        cost = cost,
        fee = fee,
        timestamp = timestamp,
        strategyId = strategyId,
        status = TradeStatus.fromString(status),
        profit = profit
    )
}

data class TradingHistoryState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)
