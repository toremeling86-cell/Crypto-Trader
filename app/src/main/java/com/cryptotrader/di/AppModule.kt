package com.cryptotrader.di

import android.content.Context
import com.cryptotrader.data.local.AppDatabase
import com.cryptotrader.data.local.dao.ApiKeyDao
import com.cryptotrader.data.local.dao.StrategyDao
import com.cryptotrader.data.local.dao.TradeDao
import com.cryptotrader.data.repository.AIAdvisorRepository
import com.cryptotrader.data.repository.AIAdvisorRepositoryImpl
import com.cryptotrader.data.repository.AnalyticsRepository
import com.cryptotrader.data.repository.AnalyticsRepositoryImpl
import com.cryptotrader.data.repository.CryptoReportRepository
import com.cryptotrader.data.repository.CryptoReportRepositoryImpl
import com.cryptotrader.data.repository.MetaAnalysisRepository
import com.cryptotrader.data.repository.MetaAnalysisRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideApplicationContext(
        @ApplicationContext context: Context
    ): Context = context
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAIAdvisorRepository(
        impl: AIAdvisorRepositoryImpl
    ): AIAdvisorRepository

    @Binds
    @Singleton
    abstract fun bindCryptoReportRepository(
        impl: CryptoReportRepositoryImpl
    ): CryptoReportRepository

    @Binds
    @Singleton
    abstract fun bindMetaAnalysisRepository(
        impl: MetaAnalysisRepositoryImpl
    ): MetaAnalysisRepository

    @Binds
    @Singleton
    abstract fun bindAnalyticsRepository(
        impl: AnalyticsRepositoryImpl
    ): AnalyticsRepository
}
