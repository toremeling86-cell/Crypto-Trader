package com.cryptotrader.di

import android.content.Context
import androidx.room.Room
import com.cryptotrader.data.local.AppDatabase
import com.cryptotrader.data.local.dao.AIMarketAnalysisDao
import com.cryptotrader.data.local.dao.ExpertReportDao
import com.cryptotrader.data.local.dao.MarketCorrelationDao
import com.cryptotrader.data.local.dao.MarketSnapshotDao
import com.cryptotrader.data.local.dao.PortfolioSnapshotDao
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
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        )
            // PRODUCTION: Use proper migrations to preserve user data
            .addMigrations(*com.cryptotrader.data.local.migrations.DatabaseMigrations.getAllMigrations())
            // Enable WAL mode for better concurrency
            .setJournalMode(androidx.room.RoomDatabase.JournalMode.WRITE_AHEAD_LOGGING)
            .build()
    }

    // ApiKeyDao removed - API keys are now stored only in EncryptedSharedPreferences for security

    @Provides
    @Singleton
    fun provideTradeDao(database: AppDatabase): TradeDao {
        return database.tradeDao()
    }

    @Provides
    @Singleton
    fun provideStrategyDao(database: AppDatabase): StrategyDao {
        return database.strategyDao()
    }

    @Provides
    @Singleton
    fun providePortfolioSnapshotDao(database: AppDatabase): PortfolioSnapshotDao {
        return database.portfolioSnapshotDao()
    }

    @Provides
    @Singleton
    fun provideMarketSnapshotDao(database: AppDatabase): MarketSnapshotDao {
        return database.marketSnapshotDao()
    }

    @Provides
    @Singleton
    fun provideAIMarketAnalysisDao(database: AppDatabase): AIMarketAnalysisDao {
        return database.aiMarketAnalysisDao()
    }

    @Provides
    @Singleton
    fun provideExpertReportDao(database: AppDatabase): ExpertReportDao {
        return database.expertReportDao()
    }

    @Provides
    @Singleton
    fun provideMarketCorrelationDao(database: AppDatabase): MarketCorrelationDao {
        return database.marketCorrelationDao()
    }

    // AI Trading Advisor DAOs

    @Provides
    @Singleton
    fun provideAdvisorAnalysisDao(database: AppDatabase): com.cryptotrader.data.local.dao.AdvisorAnalysisDao {
        return database.advisorAnalysisDao()
    }

    @Provides
    @Singleton
    fun provideTradingOpportunityDao(database: AppDatabase): com.cryptotrader.data.local.dao.TradingOpportunityDao {
        return database.tradingOpportunityDao()
    }

    @Provides
    @Singleton
    fun provideAdvisorNotificationDao(database: AppDatabase): com.cryptotrader.data.local.dao.AdvisorNotificationDao {
        return database.advisorNotificationDao()
    }
}
