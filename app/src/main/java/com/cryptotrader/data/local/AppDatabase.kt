package com.cryptotrader.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.cryptotrader.data.local.dao.*
import com.cryptotrader.data.local.entities.*

@Database(
    entities = [
        TradeEntity::class,
        StrategyEntity::class,
        PortfolioSnapshotEntity::class,
        MarketSnapshotEntity::class,
        AIMarketAnalysisEntity::class,
        ExpertReportEntity::class,
        MarketCorrelationEntity::class,
        AdvisorAnalysisEntity::class,
        TradingOpportunityEntity::class,
        AdvisorNotificationEntity::class
    ],
    version = 7, // Fixed DTO duplicates and schema changes
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    // ApiKeyDao removed - API keys are now stored only in EncryptedSharedPreferences
    abstract fun tradeDao(): TradeDao
    abstract fun strategyDao(): StrategyDao
    abstract fun portfolioSnapshotDao(): PortfolioSnapshotDao
    abstract fun marketSnapshotDao(): MarketSnapshotDao
    abstract fun aiMarketAnalysisDao(): AIMarketAnalysisDao
    abstract fun expertReportDao(): ExpertReportDao
    abstract fun marketCorrelationDao(): MarketCorrelationDao

    // AI Trading Advisor DAOs
    abstract fun advisorAnalysisDao(): AdvisorAnalysisDao
    abstract fun tradingOpportunityDao(): TradingOpportunityDao
    abstract fun advisorNotificationDao(): AdvisorNotificationDao

    companion object {
        const val DATABASE_NAME = "crypto_trader_db"
    }
}
