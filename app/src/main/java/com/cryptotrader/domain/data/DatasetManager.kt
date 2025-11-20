package com.cryptotrader.domain.data

import com.cryptotrader.data.repository.HistoricalDataRepository
import com.cryptotrader.domain.model.DataTier
import com.cryptotrader.domain.model.DatasetSource
import com.cryptotrader.domain.model.ManagedDataset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manager for backtest datasets
 *
 * Handles listing, activating, and importing datasets.
 * Acts as a source of truth for which data should be used in backtests.
 */
@Singleton
class DatasetManager @Inject constructor(
    private val historicalDataRepository: HistoricalDataRepository
) {

    private val _availableDatasets = MutableStateFlow<List<ManagedDataset>>(emptyList())
    val availableDatasets: StateFlow<List<ManagedDataset>> = _availableDatasets.asStateFlow()

    private val _activeDataset = MutableStateFlow<ManagedDataset?>(null)
    val activeDataset: StateFlow<ManagedDataset?> = _activeDataset.asStateFlow()

    init {
        // Load initial dummy datasets for MVP
        loadDummyDatasets()
    }

    private fun loadDummyDatasets() {
        val datasets = listOf(
            ManagedDataset(
                id = "default-btc",
                name = "Bitcoin (Recent)",
                description = "Last 30 days of BTC/USD data from Kraken",
                asset = "XXBTZUSD",
                timeframe = "60",
                dataTier = DataTier.TIER_2_PROFESSIONAL,
                startDate = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000),
                endDate = System.currentTimeMillis(),
                barCount = 720,
                source = DatasetSource.KRAKEN_API,
                isFavorite = true
            ),
            ManagedDataset(
                id = "eth-full-history",
                name = "ETH Full History",
                description = "Complete Ethereum dataset from 2020 to present",
                asset = "XETHZUSD",
                timeframe = "60",
                dataTier = DataTier.TIER_1_PREMIUM,
                startDate = 1577836800000L, // Jan 1 2020
                endDate = System.currentTimeMillis(),
                barCount = 43800, // ~5 years hourly
                source = DatasetSource.CRYPTOLAKE_IMPORT,
                isFavorite = false
            ),
            ManagedDataset(
                id = "eth-bull-2021",
                name = "Ethereum Bull Run 2021",
                description = "High volatility period for ETH/USD",
                asset = "XETHZUSD",
                timeframe = "60",
                dataTier = DataTier.TIER_1_PREMIUM,
                startDate = 1609459200000L, // Jan 1 2021
                endDate = 1620000000000L,   // May 2021
                barCount = 3000,
                source = DatasetSource.CRYPTOLAKE_IMPORT
            ),
            ManagedDataset(
                id = "bear-market-2022",
                name = "Bear Market 2022",
                description = "Downtrend stress test",
                asset = "XXBTZUSD",
                timeframe = "60",
                dataTier = DataTier.TIER_2_PROFESSIONAL,
                startDate = 1640995200000L, // Jan 1 2022
                endDate = 1656633600000L,   // July 2022
                barCount = 4300,
                source = DatasetSource.KRAKEN_API
            )
        )
        _availableDatasets.value = datasets
        _activeDataset.value = datasets.first()
    }

    /**
     * Activate a specific dataset for backtesting
     */
    fun activateDataset(datasetId: String) {
        val dataset = _availableDatasets.value.find { it.id == datasetId }
        if (dataset != null) {
            _activeDataset.value = dataset
        }
    }

    /**
     * Import a dataset from a CSV file (MVP: Mock implementation)
     */
    fun importFromCsv(filePath: String, name: String, asset: String) {
        val newDataset = ManagedDataset(
            id = UUID.randomUUID().toString(),
            name = name,
            description = "Imported from $filePath",
            asset = asset,
            timeframe = "60",
            dataTier = DataTier.TIER_3_STANDARD,
            startDate = System.currentTimeMillis(),
            endDate = System.currentTimeMillis(),
            barCount = 1000,
            source = DatasetSource.LOCAL_CSV,
            filePath = filePath
        )
        
        val currentList = _availableDatasets.value.toMutableList()
        currentList.add(newDataset)
        _availableDatasets.value = currentList
    }
}
