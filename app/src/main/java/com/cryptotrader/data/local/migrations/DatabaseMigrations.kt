package com.cryptotrader.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Database migrations to preserve user data across schema changes
 *
 * IMPORTANT: Never use fallbackToDestructiveMigration() in production!
 * Each version increment must have a corresponding migration.
 */
object DatabaseMigrations {

    /**
     * Migration from version 1 to 2
     *
     * Changes:
     * - Removed ApiKeyEntity table (API keys moved to EncryptedSharedPreferences for security)
     * - Kept TradeEntity and StrategyEntity tables intact
     */
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Drop ApiKeyEntity table if it exists (security improvement)
            database.execSQL("DROP TABLE IF EXISTS api_keys")

            // TradeEntity and StrategyEntity tables remain unchanged
            // No migration needed for these tables
        }
    }

    /**
     * Migration from version 2 to 3
     *
     * Changes:
     * - Added AI strategy fields (analysisReport, approvalStatus, source)
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Add new columns for AI strategy support
            database.execSQL(
                "ALTER TABLE strategies ADD COLUMN analysisReport TEXT DEFAULT NULL"
            )
            database.execSQL(
                "ALTER TABLE strategies ADD COLUMN approvalStatus TEXT NOT NULL DEFAULT 'APPROVED'"
            )
            database.execSQL(
                "ALTER TABLE strategies ADD COLUMN source TEXT NOT NULL DEFAULT 'USER'"
            )
        }
    }

    /**
     * Migration from version 3 to 4
     *
     * Changes:
     * - Added PortfolioSnapshotEntity table for historical portfolio tracking
     */
    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Create portfolio_snapshots table
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS portfolio_snapshots (
                    timestamp INTEGER PRIMARY KEY NOT NULL,
                    totalValue REAL NOT NULL,
                    totalPnL REAL NOT NULL,
                    totalPnLPercent REAL NOT NULL,
                    holdingsJson TEXT NOT NULL
                )
                """.trimIndent()
            )
        }
    }

    /**
     * Migration from version 4 to 5
     *
     * Changes:
     * - Added MarketSnapshotEntity table for live crypto price tracking
     * - Added AIMarketAnalysisEntity table for Claude AI market insights
     * - Added ExpertReportEntity table for expert trading reports
     * - Added MarketCorrelationEntity table for cross-market analysis
     */
    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Create market_snapshots table
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS market_snapshots (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    symbol TEXT NOT NULL,
                    price REAL NOT NULL,
                    volume24h REAL NOT NULL,
                    high24h REAL NOT NULL,
                    low24h REAL NOT NULL,
                    changePercent24h REAL NOT NULL,
                    timestamp INTEGER NOT NULL
                )
                """.trimIndent()
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_market_snapshots_timestamp ON market_snapshots(timestamp)"
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_market_snapshots_symbol ON market_snapshots(symbol)"
            )

            // Create ai_market_analyses table
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS ai_market_analyses (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    analysisText TEXT NOT NULL,
                    sentiment TEXT NOT NULL,
                    marketCondition TEXT NOT NULL,
                    confidence REAL NOT NULL,
                    keyInsights TEXT NOT NULL,
                    riskFactors TEXT NOT NULL,
                    opportunities TEXT NOT NULL,
                    triggerType TEXT NOT NULL,
                    symbolsAnalyzed TEXT NOT NULL,
                    timestamp INTEGER NOT NULL
                )
                """.trimIndent()
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_ai_market_analyses_triggerType ON ai_market_analyses(triggerType)"
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_ai_market_analyses_timestamp ON ai_market_analyses(timestamp)"
            )

            // Create expert_reports table
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS expert_reports (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    title TEXT NOT NULL,
                    content TEXT NOT NULL,
                    author TEXT,
                    source TEXT,
                    category TEXT NOT NULL,
                    uploadDate INTEGER NOT NULL,
                    isSentToClaude INTEGER NOT NULL DEFAULT 0,
                    claudeAnalysisId INTEGER,
                    tags TEXT
                )
                """.trimIndent()
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_expert_reports_uploadDate ON expert_reports(uploadDate)"
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_expert_reports_category ON expert_reports(category)"
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_expert_reports_isSentToClaude ON expert_reports(isSentToClaude)"
            )

            // Create market_correlations table
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS market_correlations (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    symbol1 TEXT NOT NULL,
                    symbol2 TEXT NOT NULL,
                    correlationValue REAL NOT NULL,
                    period TEXT NOT NULL,
                    timestamp INTEGER NOT NULL
                )
                """.trimIndent()
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_market_correlations_timestamp ON market_correlations(timestamp)"
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_market_correlations_symbol1 ON market_correlations(symbol1)"
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_market_correlations_symbol2 ON market_correlations(symbol2)"
            )
        }
    }

    /**
     * Migration from version 5 to 6
     *
     * Changes:
     * - Added AdvisorAnalysisEntity table for AI Trading Advisor analysis results
     * - Added TradingOpportunityEntity table for identified trading opportunities
     * - Added AdvisorNotificationEntity table for notification tracking
     */
    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Create advisor_analyses table
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS advisor_analyses (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    analysisText TEXT NOT NULL,
                    overallSentiment TEXT NOT NULL,
                    confidenceLevel REAL NOT NULL,
                    marketCondition TEXT NOT NULL,
                    volatilityLevel TEXT NOT NULL,
                    keyInsights TEXT NOT NULL,
                    riskFactors TEXT NOT NULL,
                    recommendations TEXT NOT NULL,
                    technicalIndicators TEXT NOT NULL,
                    opportunitiesCount INTEGER NOT NULL,
                    triggerType TEXT NOT NULL,
                    symbolsAnalyzed TEXT NOT NULL,
                    timestamp INTEGER NOT NULL
                )
                """.trimIndent()
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_advisor_analyses_timestamp ON advisor_analyses(timestamp)"
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_advisor_analyses_triggerType ON advisor_analyses(triggerType)"
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_advisor_analyses_overallSentiment ON advisor_analyses(overallSentiment)"
            )

            // Create trading_opportunities table
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS trading_opportunities (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    analysisId INTEGER NOT NULL,
                    asset TEXT NOT NULL,
                    direction TEXT NOT NULL,
                    timeframe TEXT NOT NULL,
                    entryPrice REAL NOT NULL,
                    targetPrice REAL NOT NULL,
                    stopLoss REAL NOT NULL,
                    potentialGainPercent REAL NOT NULL,
                    riskRewardRatio REAL NOT NULL,
                    rationale TEXT NOT NULL,
                    confidence REAL NOT NULL,
                    priority TEXT NOT NULL,
                    technicalSignals TEXT NOT NULL,
                    riskFactors TEXT NOT NULL,
                    status TEXT NOT NULL,
                    expiresAt INTEGER,
                    notificationSent INTEGER NOT NULL,
                    timestamp INTEGER NOT NULL,
                    FOREIGN KEY(analysisId) REFERENCES advisor_analyses(id) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_trading_opportunities_analysisId ON trading_opportunities(analysisId)"
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_trading_opportunities_asset ON trading_opportunities(asset)"
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_trading_opportunities_timestamp ON trading_opportunities(timestamp)"
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_trading_opportunities_status ON trading_opportunities(status)"
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_trading_opportunities_priority ON trading_opportunities(priority)"
            )

            // Create advisor_notifications table
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS advisor_notifications (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    opportunityId INTEGER,
                    type TEXT NOT NULL,
                    title TEXT NOT NULL,
                    message TEXT NOT NULL,
                    priority TEXT NOT NULL,
                    isRead INTEGER NOT NULL DEFAULT 0,
                    isDismissed INTEGER NOT NULL DEFAULT 0,
                    wasShown INTEGER NOT NULL DEFAULT 0,
                    timestamp INTEGER NOT NULL,
                    readAt INTEGER,
                    dismissedAt INTEGER,
                    FOREIGN KEY(opportunityId) REFERENCES trading_opportunities(id) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_advisor_notifications_opportunityId ON advisor_notifications(opportunityId)"
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_advisor_notifications_timestamp ON advisor_notifications(timestamp)"
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_advisor_notifications_type ON advisor_notifications(type)"
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS index_advisor_notifications_isRead ON advisor_notifications(isRead)"
            )
        }
    }

    /**
     * Migration from version 6 to 7
     *
     * Changes:
     * - Fixed DTO duplicate classes (no database schema changes)
     * - Removed duplicate OrderDescription, OrderInfo, OrderRequest from DTOs
     * - No entity changes needed - this is a no-op migration
     */
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // No schema changes - DTO duplicates were removed but entities are unchanged
            // This migration exists to update the version number after DTO cleanup
        }
    }

    /**
     * Get all migrations
     */
    fun getAllMigrations(): Array<Migration> {
        return arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7
        )
    }
}
