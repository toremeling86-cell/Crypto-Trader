package com.cryptotrader.di

import android.content.Context
import com.cryptotrader.data.local.AppDatabase
import com.cryptotrader.data.local.dao.ApiKeyDao
import com.cryptotrader.data.local.dao.StrategyDao
import com.cryptotrader.data.local.dao.TradeDao
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
