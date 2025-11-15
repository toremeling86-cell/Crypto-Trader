package com.cryptotrader.di

import com.cryptotrader.domain.indicators.cache.IndicatorCache
import com.cryptotrader.domain.trading.MarketDataAdapter
import com.cryptotrader.domain.trading.PriceHistoryManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for providing trading-related dependencies
 *
 * This module wires up the advanced calculator-based indicator system components
 * for Phase 2 migration. It provides:
 * - MarketDataAdapter: Adapts raw market data to calculator-compatible format
 * - PriceHistoryManager: Manages price history with caching support
 */
@Module
@InstallIn(SingletonComponent::class)
object TradingModule {

    /**
     * Provides MarketDataAdapter as singleton
     *
     * The MarketDataAdapter converts raw market data (from Kraken API or other sources)
     * into the standardized format required by the calculator-based indicators.
     * This ensures clean separation between data sources and calculation logic.
     */
    @Provides
    @Singleton
    fun provideMarketDataAdapter(): MarketDataAdapter {
        return MarketDataAdapter()
    }

    /**
     * Provides PriceHistoryManager as singleton with IndicatorCache injection
     *
     * The PriceHistoryManager maintains price history data for all trading pairs,
     * leveraging the IndicatorCache for optimal performance. It handles:
     * - Price data retrieval and updates
     * - Cache invalidation on new data
     * - Historical data window management
     *
     * @param indicatorCache The cache instance for storing calculated indicators
     */
    @Provides
    @Singleton
    fun providePriceHistoryManager(
        indicatorCache: IndicatorCache
    ): PriceHistoryManager {
        return PriceHistoryManager(indicatorCache)
    }
}
