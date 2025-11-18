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
        AdvisorNotificationEntity::class,
        MetaAnalysisEntity::class,
        OHLCBarEntity::class,
        TechnicalIndicatorEntity::class,
        DataCoverageEntity::class,
        BacktestRunEntity::class,
        DataQualityEntity::class,
        DataQuarterCoverageEntity::class
    ],
    version = 16, // Migration 15→16: Add soft-delete fields to strategies (isInvalid, invalidReason, invalidatedAt)
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

    // Meta-Analysis DAO
    abstract fun metaAnalysisDao(): MetaAnalysisDao

    // Backend Data Storage DAOs (version 13+)
    abstract fun ohlcBarDao(): OHLCBarDao
    abstract fun technicalIndicatorDao(): TechnicalIndicatorDao
    abstract fun dataCoverageDao(): DataCoverageDao
    abstract fun backtestRunDao(): BacktestRunDao
    abstract fun dataQualityDao(): DataQualityDao

    // Cloud Storage DAO (version 15+)
    abstract fun dataQuarterCoverageDao(): DataQuarterCoverageDao

    companion object {
        const val DATABASE_NAME = "crypto_trader_db"
    }
}
