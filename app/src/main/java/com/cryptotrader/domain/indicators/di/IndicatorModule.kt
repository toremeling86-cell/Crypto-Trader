package com.cryptotrader.domain.indicators.di

import com.cryptotrader.domain.indicators.atr.AtrCalculator
import com.cryptotrader.domain.indicators.atr.AtrCalculatorImpl
import com.cryptotrader.domain.indicators.bollingerbands.BollingerBandsCalculator
import com.cryptotrader.domain.indicators.bollingerbands.BollingerBandsCalculatorImpl
import com.cryptotrader.domain.indicators.cache.IndicatorCache
import com.cryptotrader.domain.indicators.macd.MacdCalculator
import com.cryptotrader.domain.indicators.macd.MacdCalculatorImpl
import com.cryptotrader.domain.indicators.movingaverage.MovingAverageCalculator
import com.cryptotrader.domain.indicators.movingaverage.MovingAverageCalculatorImpl
import com.cryptotrader.domain.indicators.rsi.RsiCalculator
import com.cryptotrader.domain.indicators.rsi.RsiCalculatorImpl
import com.cryptotrader.domain.indicators.stochastic.StochasticCalculator
import com.cryptotrader.domain.indicators.stochastic.StochasticCalculatorImpl
import com.cryptotrader.domain.indicators.volume.VolumeIndicatorCalculator
import com.cryptotrader.domain.indicators.volume.VolumeIndicatorCalculatorImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for providing indicator calculator dependencies
 */
@Module
@InstallIn(SingletonComponent::class)
object IndicatorModule {

    /**
     * Provides RSI calculator instance
     */
    @Provides
    @Singleton
    fun provideRsiCalculator(): RsiCalculator {
        return RsiCalculatorImpl()
    }

    /**
     * Provides Moving Average calculator instance
     */
    @Provides
    @Singleton
    fun provideMovingAverageCalculator(): MovingAverageCalculator {
        return MovingAverageCalculatorImpl()
    }

    /**
     * Provides MACD calculator instance
     */
    @Provides
    @Singleton
    fun provideMacdCalculator(
        maCalculator: MovingAverageCalculator
    ): MacdCalculator {
        return MacdCalculatorImpl(maCalculator)
    }

    /**
     * Provides Bollinger Bands calculator instance
     */
    @Provides
    @Singleton
    fun provideBollingerBandsCalculator(
        maCalculator: MovingAverageCalculator
    ): BollingerBandsCalculator {
        return BollingerBandsCalculatorImpl(maCalculator)
    }

    /**
     * Provides ATR calculator instance
     */
    @Provides
    @Singleton
    fun provideAtrCalculator(): AtrCalculator {
        return AtrCalculatorImpl()
    }

    /**
     * Provides Stochastic calculator instance
     */
    @Provides
    @Singleton
    fun provideStochasticCalculator(
        maCalculator: MovingAverageCalculator
    ): StochasticCalculator {
        return StochasticCalculatorImpl(maCalculator)
    }

    /**
     * Provides Volume Indicator calculator instance
     */
    @Provides
    @Singleton
    fun provideVolumeIndicatorCalculator(
        maCalculator: MovingAverageCalculator
    ): VolumeIndicatorCalculator {
        return VolumeIndicatorCalculatorImpl(maCalculator)
    }

    /**
     * Provides Indicator Cache instance with configurable max size
     */
    @Provides
    @Singleton
    fun provideIndicatorCache(): IndicatorCache {
        return IndicatorCache(maxSize = 100)
    }
}
