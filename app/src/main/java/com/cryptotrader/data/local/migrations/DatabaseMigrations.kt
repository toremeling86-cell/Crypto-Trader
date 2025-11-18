package com.cryptotrader.data.local.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import timber.log.Timber

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
     * - Added execution tracking fields to TradeEntity
     *   - orderType, feeCurrency, krakenOrderId, krakenTradeId
     *   - realizedPnL, realizedPnLPercent, executedAt, positionId
     * - Added execution tracking fields to StrategyEntity
     *   - executionStatus, lastCheckedTime, triggeredAt
     *   - lastExecutionError, executionCount
     */
    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Add new execution tracking columns to trades table
            // Note: NOT NULL columns need a temporary default, then we update existing rows

            // Add orderType column
            database.execSQL("ALTER TABLE trades ADD COLUMN orderType TEXT")
            database.execSQL("UPDATE trades SET orderType = 'MARKET' WHERE orderType IS NULL")

            // Add feeCurrency column
            database.execSQL("ALTER TABLE trades ADD COLUMN feeCurrency TEXT")
            database.execSQL("UPDATE trades SET feeCurrency = 'USD' WHERE feeCurrency IS NULL")

            // Add nullable columns (no default needed)
            database.execSQL("ALTER TABLE trades ADD COLUMN krakenOrderId TEXT")
            database.execSQL("ALTER TABLE trades ADD COLUMN krakenTradeId TEXT")
            database.execSQL("ALTER TABLE trades ADD COLUMN realizedPnL REAL")
            database.execSQL("ALTER TABLE trades ADD COLUMN realizedPnLPercent REAL")
            database.execSQL("ALTER TABLE trades ADD COLUMN positionId TEXT")

            // Add executedAt column
            database.execSQL("ALTER TABLE trades ADD COLUMN executedAt INTEGER")
            database.execSQL("UPDATE trades SET executedAt = timestamp WHERE executedAt IS NULL")

            // Create indices for trades table
            database.execSQL("CREATE INDEX IF NOT EXISTS index_trades_orderType ON trades(orderType)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_trades_status ON trades(status)")

            // Add new execution tracking columns to strategies table
            database.execSQL("ALTER TABLE strategies ADD COLUMN executionStatus TEXT")
            database.execSQL("UPDATE strategies SET executionStatus = 'INACTIVE' WHERE executionStatus IS NULL")

            database.execSQL("ALTER TABLE strategies ADD COLUMN lastCheckedTime INTEGER")
            database.execSQL("ALTER TABLE strategies ADD COLUMN triggeredAt INTEGER")
            database.execSQL("ALTER TABLE strategies ADD COLUMN lastExecutionError TEXT")

            database.execSQL("ALTER TABLE strategies ADD COLUMN executionCount INTEGER")
            database.execSQL("UPDATE strategies SET executionCount = 0 WHERE executionCount IS NULL")
        }
    }

    /**
     * Migration from version 7 to 8
     *
     * Changes:
     * - Added file-based report fields to ExpertReportEntity
     *   - filePath, filename, fileSize
     * - Added meta-analysis tracking fields to ExpertReportEntity
     *   - analyzed, metaAnalysisId
     * - Added MetaAnalysisEntity table for Opus 4.1 meta-analysis results
     */
    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Add new file-based report fields to expert_reports table
            database.execSQL("ALTER TABLE expert_reports ADD COLUMN filePath TEXT")
            database.execSQL("ALTER TABLE expert_reports ADD COLUMN filename TEXT")
            database.execSQL("ALTER TABLE expert_reports ADD COLUMN fileSize INTEGER NOT NULL DEFAULT 0")

            // Add meta-analysis tracking fields
            database.execSQL("ALTER TABLE expert_reports ADD COLUMN analyzed INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE expert_reports ADD COLUMN metaAnalysisId INTEGER")

            // Add tags field
            database.execSQL("ALTER TABLE expert_reports ADD COLUMN tags TEXT")

            // Add deprecated legacy fields (required for schema compatibility)
            database.execSQL("ALTER TABLE expert_reports ADD COLUMN isSentToClaude INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE expert_reports ADD COLUMN claudeAnalysisId INTEGER")

            // Create indices for new fields
            database.execSQL("CREATE INDEX IF NOT EXISTS index_expert_reports_analyzed ON expert_reports(analyzed)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_expert_reports_metaAnalysisId ON expert_reports(metaAnalysisId)")

            // Create meta_analyses table
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS meta_analyses (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    timestamp INTEGER NOT NULL,
                    reportIds TEXT NOT NULL,
                    reportCount INTEGER NOT NULL,
                    findings TEXT NOT NULL,
                    consensus TEXT,
                    contradictions TEXT,
                    marketOutlook TEXT,
                    recommendedStrategyJson TEXT NOT NULL,
                    strategyName TEXT NOT NULL,
                    tradingPairs TEXT NOT NULL,
                    confidence REAL NOT NULL,
                    riskLevel TEXT NOT NULL,
                    expectedReturn TEXT,
                    status TEXT NOT NULL,
                    strategyId INTEGER,
                    approvedAt INTEGER,
                    rejectedAt INTEGER,
                    rejectionReason TEXT,
                    opusModel TEXT NOT NULL,
                    tokensUsed INTEGER,
                    analysisTimeMs INTEGER
                )
                """.trimIndent()
            )

            // Create indices for meta_analyses table
            database.execSQL("CREATE INDEX IF NOT EXISTS index_meta_analyses_timestamp ON meta_analyses(timestamp)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_meta_analyses_status ON meta_analyses(status)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_meta_analyses_strategyId ON meta_analyses(strategyId)")
        }
    }

    /**
     * Migration from version 8 to 9
     *
     * Changes (Phase 3A: Reports Library):
     * - Added smart analysis fields to expert_reports (sentiment, assets, impact score)
     * - Added performance tracking fields to strategies table
     * - Added timeframe analysis support
     */
    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Add smart analysis fields to expert_reports
            database.execSQL("ALTER TABLE expert_reports ADD COLUMN sentiment TEXT")
            database.execSQL("ALTER TABLE expert_reports ADD COLUMN sentimentScore REAL")
            database.execSQL("ALTER TABLE expert_reports ADD COLUMN assets TEXT")
            database.execSQL("ALTER TABLE expert_reports ADD COLUMN tradingPairs TEXT")
            database.execSQL("ALTER TABLE expert_reports ADD COLUMN publishedDate INTEGER")
            database.execSQL("ALTER TABLE expert_reports ADD COLUMN usedInStrategies INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE expert_reports ADD COLUMN impactScore REAL")

            // Add performance tracking fields to strategies
            database.execSQL("ALTER TABLE strategies ADD COLUMN metaAnalysisId INTEGER")
            database.execSQL("ALTER TABLE strategies ADD COLUMN sourceReportCount INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE strategies ADD COLUMN maxDrawdown REAL NOT NULL DEFAULT 0.0")
            database.execSQL("ALTER TABLE strategies ADD COLUMN avgWinAmount REAL NOT NULL DEFAULT 0.0")
            database.execSQL("ALTER TABLE strategies ADD COLUMN avgLossAmount REAL NOT NULL DEFAULT 0.0")
            database.execSQL("ALTER TABLE strategies ADD COLUMN profitFactor REAL NOT NULL DEFAULT 0.0")
            database.execSQL("ALTER TABLE strategies ADD COLUMN sharpeRatio REAL")
            database.execSQL("ALTER TABLE strategies ADD COLUMN largestWin REAL NOT NULL DEFAULT 0.0")
            database.execSQL("ALTER TABLE strategies ADD COLUMN largestLoss REAL NOT NULL DEFAULT 0.0")
            database.execSQL("ALTER TABLE strategies ADD COLUMN currentStreak INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE strategies ADD COLUMN longestWinStreak INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE strategies ADD COLUMN longestLossStreak INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE strategies ADD COLUMN performanceScore REAL NOT NULL DEFAULT 0.0")
            database.execSQL("ALTER TABLE strategies ADD COLUMN isTopPerformer INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE strategies ADD COLUMN totalProfitPercent REAL NOT NULL DEFAULT 0.0")

            // Add timeframe field to meta_analyses
            database.execSQL("ALTER TABLE meta_analyses ADD COLUMN timeframe TEXT")
            database.execSQL("ALTER TABLE meta_analyses ADD COLUMN reportWeights TEXT")

            // Create indices for new fields
            database.execSQL("CREATE INDEX IF NOT EXISTS index_expert_reports_sentiment ON expert_reports(sentiment)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_expert_reports_publishedDate ON expert_reports(publishedDate)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_strategies_metaAnalysisId ON strategies(metaAnalysisId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_strategies_performanceScore ON strategies(performanceScore)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_strategies_isTopPerformer ON strategies(isTopPerformer)")
        }
    }

    /**
     * Migration from version 9 to 10
     *
     * Changes (Phase 3B - Smart Meta-Analysis with Temporal Weighting):
     * - Added oldestReportDate to meta_analyses for temporal range tracking
     * - Added newestReportDate to meta_analyses for temporal range tracking
     * - Added temporalWeightingApplied flag to meta_analyses
     */
    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(database: SupportSQLiteDatabase) {
            // Add temporal range fields to meta_analyses
            database.execSQL("ALTER TABLE meta_analyses ADD COLUMN oldestReportDate INTEGER")
            database.execSQL("ALTER TABLE meta_analyses ADD COLUMN newestReportDate INTEGER")
            database.execSQL("ALTER TABLE meta_analyses ADD COLUMN temporalWeightingApplied INTEGER NOT NULL DEFAULT 0")

            // Create indices for new fields
            database.execSQL("CREATE INDEX IF NOT EXISTS index_meta_analyses_oldestReportDate ON meta_analyses(oldestReportDate)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_meta_analyses_newestReportDate ON meta_analyses(newestReportDate)")
        }
    }

    /**
     * Migration from version 10 to 11
     *
     * Changes:
     * - Remove legacy strategies with hardcoded price levels
     * - These bypass validation as they were created before validation existed
     * - Uses production-quality regex validation (same as StrategyRepository)
     * - Prevents false positives by checking for valid indicators
     */
    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(database: SupportSQLiteDatabase) {
            Timber.i("Running migration 10→11: Cleaning legacy strategies with hardcoded prices")

            // Regex patterns to detect hardcoded price levels (from StrategyRepository.kt)
            val pricePatterns = listOf(
                // Dollar amounts: $42,500 or $2,100
                Regex("\\$\\s*\\d{1,3}(?:,\\d{3})*(?:\\.\\d{2})?"),

                // Large numbers that look like crypto prices (4-5+ digits)
                Regex("\\b\\d{4,}(?:\\.\\d+)?\\b"),

                // Price comparisons with large numbers: "price > 42500"
                Regex("price\\s*[><=]+\\s*\\d{4,}", RegexOption.IGNORE_CASE),

                // Trading pair with dollar amounts: "BTC: $42,500-43,500"
                Regex("[A-Z]{3,}:\\s*\\$\\s*\\d{1,3}(?:,\\d{3})*"),

                // Price ranges with large numbers: "42500-43500"
                Regex("\\d{4,}\\s*-\\s*\\d{4,}"),

                // Support/resistance keywords
                Regex("support\\s+levels?", RegexOption.IGNORE_CASE),
                Regex("resistance\\s+levels?", RegexOption.IGNORE_CASE)
            )

            // Valid indicator-based patterns (these are allowed)
            val validIndicatorPatterns = listOf(
                Regex("RSI", RegexOption.IGNORE_CASE),
                Regex("MACD", RegexOption.IGNORE_CASE),
                Regex("SMA", RegexOption.IGNORE_CASE),
                Regex("EMA", RegexOption.IGNORE_CASE),
                Regex("Bollinger", RegexOption.IGNORE_CASE),
                Regex("ATR", RegexOption.IGNORE_CASE),
                Regex("Volume", RegexOption.IGNORE_CASE),
                Regex("\\d+%"), // Percentage-based conditions
                Regex("crossover", RegexOption.IGNORE_CASE),
                Regex("above|below", RegexOption.IGNORE_CASE)
            )

            // Get all strategies
            val cursor = database.query("""
                SELECT id, name, entryConditions, exitConditions
                FROM strategies
            """)

            val strategyIdsToDelete = mutableListOf<String>()
            val strategyNamesToLog = mutableListOf<String>()
            val deletionReasons = mutableListOf<String>()

            try {
                while (cursor.moveToNext()) {
                    val id = cursor.getString(0)
                    val name = cursor.getString(1)
                    val entryConditions = cursor.getString(2) ?: ""
                    val exitConditions = cursor.getString(3) ?: ""
                    val allConditions = entryConditions + " " + exitConditions

                    // Check for hardcoded price patterns
                    var shouldDelete = false
                    var matchedPattern = ""

                    for (pattern in pricePatterns) {
                        if (pattern.containsMatchIn(allConditions)) {
                            // Check if this is a false positive by looking for valid indicators
                            val hasValidIndicator = validIndicatorPatterns.any {
                                it.containsMatchIn(allConditions)
                            }

                            // Special case: Allow small numbers (likely percentages, timeframes, etc.)
                            val matchedValue = pattern.find(allConditions)?.value
                            val isSmallNumber = matchedValue?.replace(Regex("[^0-9]"), "")?.toIntOrNull()?.let {
                                it < 1000
                            } ?: false

                            if (!hasValidIndicator && !isSmallNumber) {
                                shouldDelete = true
                                matchedPattern = matchedValue ?: pattern.pattern
                                break
                            }
                        }
                    }

                    if (shouldDelete) {
                        strategyIdsToDelete.add(id)
                        strategyNamesToLog.add(name)
                        deletionReasons.add(matchedPattern)
                        Timber.w("Found legacy strategy with hardcoded prices: $name (ID: $id, matched: $matchedPattern)")
                    }
                }
            } finally {
                cursor.close()
            }

            // Delete invalid strategies
            strategyIdsToDelete.forEach { id ->
                database.execSQL("DELETE FROM strategies WHERE id = ?", arrayOf(id))
            }

            if (strategyIdsToDelete.isNotEmpty()) {
                Timber.i("Migration 10→11: Removed ${strategyIdsToDelete.size} legacy strategies: ${strategyNamesToLog.joinToString(", ")}")
                Timber.i("   Patterns matched: ${deletionReasons.joinToString(", ")}")
            } else {
                Timber.i("Migration 10→11: No legacy strategies found. Database is clean.")
            }
        }
    }

    /**
     * Migration from version 11 to 12
     *
     * Changes:
     * - Add tradingMode column to strategies table (INACTIVE, PAPER, LIVE)
     * - Enables auto-trading with Paper Trading and Live Trading modes
     */
    val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(database: SupportSQLiteDatabase) {
            Timber.i("Running migration 11→12: Adding trading mode support")

            // Add tradingMode column
            database.execSQL("ALTER TABLE strategies ADD COLUMN tradingMode TEXT NOT NULL DEFAULT 'INACTIVE'")

            // For existing active strategies, set them to INACTIVE by default
            // Users will need to explicitly choose PAPER or LIVE mode
            database.execSQL("UPDATE strategies SET tradingMode = 'INACTIVE'")

            Timber.i("Migration 11→12: Added tradingMode column. All strategies set to INACTIVE.")
        }
    }

    val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(database: SupportSQLiteDatabase) {
            Timber.i("Running migration 12→13: Adding backend data storage system")

            // Create ohlc_bars table
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS ohlc_bars (
                    asset TEXT NOT NULL,
                    timeframe TEXT NOT NULL,
                    timestamp INTEGER NOT NULL,
                    open REAL NOT NULL,
                    high REAL NOT NULL,
                    low REAL NOT NULL,
                    close REAL NOT NULL,
                    volume REAL NOT NULL,
                    trades INTEGER NOT NULL DEFAULT 0,
                    source TEXT NOT NULL DEFAULT 'UNKNOWN',
                    importedAt INTEGER NOT NULL,
                    PRIMARY KEY(asset, timeframe, timestamp)
                )
            """)
            database.execSQL("CREATE INDEX IF NOT EXISTS index_ohlc_bars_asset_timeframe_timestamp ON ohlc_bars(asset, timeframe, timestamp)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_ohlc_bars_asset_timestamp ON ohlc_bars(asset, timestamp)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_ohlc_bars_timestamp ON ohlc_bars(timestamp)")

            // Create technical_indicators table
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS technical_indicators (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    asset TEXT NOT NULL,
                    timeframe TEXT NOT NULL,
                    timestamp INTEGER NOT NULL,
                    indicatorType TEXT NOT NULL,
                    value REAL NOT NULL,
                    parameters TEXT NOT NULL DEFAULT '',
                    source TEXT NOT NULL DEFAULT 'UNKNOWN',
                    calculatedAt INTEGER NOT NULL
                )
            """)
            database.execSQL("CREATE INDEX IF NOT EXISTS index_technical_indicators_asset_timeframe_timestamp ON technical_indicators(asset, timeframe, timestamp)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_technical_indicators_indicatorType_asset_timestamp ON technical_indicators(indicatorType, asset, timestamp)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_technical_indicators_timestamp ON technical_indicators(timestamp)")

            // Create data_coverage table
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS data_coverage (
                    asset TEXT NOT NULL,
                    timeframe TEXT NOT NULL,
                    earliestTimestamp INTEGER NOT NULL,
                    latestTimestamp INTEGER NOT NULL,
                    totalBars INTEGER NOT NULL,
                    expectedBars INTEGER NOT NULL,
                    dataQualityScore REAL NOT NULL,
                    gapsCount INTEGER NOT NULL,
                    missingBarsCount INTEGER NOT NULL,
                    primarySource TEXT NOT NULL,
                    sources TEXT NOT NULL,
                    lastUpdated INTEGER NOT NULL,
                    lastImportedAt INTEGER NOT NULL,
                    PRIMARY KEY(asset, timeframe)
                )
            """)
            database.execSQL("CREATE INDEX IF NOT EXISTS index_data_coverage_asset ON data_coverage(asset)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_data_coverage_timeframe ON data_coverage(timeframe)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_data_coverage_dataQualityScore ON data_coverage(dataQualityScore)")

            // Create backtest_runs table
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS backtest_runs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    strategyId TEXT NOT NULL,
                    asset TEXT NOT NULL,
                    timeframe TEXT NOT NULL,
                    startTimestamp INTEGER NOT NULL,
                    endTimestamp INTEGER NOT NULL,
                    totalBarsUsed INTEGER NOT NULL,
                    totalTrades INTEGER NOT NULL,
                    winningTrades INTEGER NOT NULL,
                    losingTrades INTEGER NOT NULL,
                    winRate REAL NOT NULL,
                    totalPnL REAL NOT NULL,
                    totalPnLPercent REAL NOT NULL,
                    sharpeRatio REAL NOT NULL,
                    maxDrawdown REAL NOT NULL,
                    profitFactor REAL NOT NULL,
                    status TEXT NOT NULL,
                    dataQualityScore REAL NOT NULL,
                    dataSource TEXT NOT NULL,
                    executedAt INTEGER NOT NULL,
                    durationMs INTEGER NOT NULL DEFAULT 0
                )
            """)
            database.execSQL("CREATE INDEX IF NOT EXISTS index_backtest_runs_strategyId ON backtest_runs(strategyId)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_backtest_runs_asset_timeframe ON backtest_runs(asset, timeframe)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_backtest_runs_executedAt ON backtest_runs(executedAt)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_backtest_runs_winRate ON backtest_runs(winRate)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_backtest_runs_totalPnLPercent ON backtest_runs(totalPnLPercent)")

            // Create data_quality table
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS data_quality (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    asset TEXT NOT NULL,
                    timeframe TEXT NOT NULL,
                    coverageStartTimestamp INTEGER NOT NULL,
                    coverageEndTimestamp INTEGER NOT NULL,
                    overallQualityScore REAL NOT NULL,
                    completenessScore REAL NOT NULL,
                    consistencyScore REAL NOT NULL,
                    gapScore REAL NOT NULL,
                    totalGaps INTEGER NOT NULL,
                    largestGapMs INTEGER NOT NULL,
                    averageGapMs INTEGER NOT NULL,
                    gapsDetails TEXT NOT NULL,
                    invalidBarsCount INTEGER NOT NULL,
                    zeroVolumeBarsCount INTEGER NOT NULL,
                    duplicateBarsCount INTEGER NOT NULL,
                    anomaliesCount INTEGER NOT NULL,
                    issuesDetails TEXT NOT NULL,
                    isBacktestSuitable INTEGER NOT NULL,
                    warningMessage TEXT NOT NULL DEFAULT '',
                    recommendedMinDate INTEGER NOT NULL DEFAULT 0,
                    validatedAt INTEGER NOT NULL,
                    validationDurationMs INTEGER NOT NULL DEFAULT 0
                )
            """)
            database.execSQL("CREATE INDEX IF NOT EXISTS index_data_quality_asset_timeframe ON data_quality(asset, timeframe)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_data_quality_validatedAt ON data_quality(validatedAt)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_data_quality_overallQualityScore ON data_quality(overallQualityScore)")

            Timber.i("Migration 12→13: Backend data storage tables created successfully")
        }
    }

    /**
     * Migration from version 13 to 14
     *
     * Changes:
     * - Added dataTier field to ohlc_bars for data quality separation
     * - Added dataTier and tierValidated fields to backtest_runs for quality tracking
     * - Prevents mixing PREMIUM/PROFESSIONAL/STANDARD/BASIC data in backtests
     *
     * CRITICAL: Hedge fund quality control - never mix data tiers!
     */
    val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(database: SupportSQLiteDatabase) {
            Timber.i("Running migration 13→14: Adding data tier quality tracking")

            // Add dataTier to ohlc_bars
            database.execSQL("ALTER TABLE ohlc_bars ADD COLUMN dataTier TEXT NOT NULL DEFAULT 'TIER_4_BASIC'")

            // Add dataTier and tierValidated to backtest_runs
            database.execSQL("ALTER TABLE backtest_runs ADD COLUMN dataTier TEXT NOT NULL DEFAULT 'TIER_4_BASIC'")
            database.execSQL("ALTER TABLE backtest_runs ADD COLUMN tierValidated INTEGER NOT NULL DEFAULT 0")

            // Create indexes for efficient tier querying
            database.execSQL("CREATE INDEX IF NOT EXISTS index_ohlc_bars_dataTier_asset ON ohlc_bars(dataTier, asset)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_ohlc_bars_source_dataTier ON ohlc_bars(source, dataTier)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_backtest_runs_dataTier ON backtest_runs(dataTier)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_backtest_runs_dataTier_status ON backtest_runs(dataTier, status)")

            Timber.i("Migration 13→14: Data tier tracking enabled. Hedge fund quality control active.")
        }
    }

    /**
     * Migration from version 14 to 15
     *
     * Changes:
     * - Added DataQuarterCoverageEntity table for cloud storage quarter tracking
     * - Enables CloudDataRepository to track which quarters are downloaded from R2
     */
    val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(database: SupportSQLiteDatabase) {
            Timber.i("Running migration 14→15: Adding cloud storage quarter tracking")

            // Create data_quarter_coverage table
            database.execSQL("""
                CREATE TABLE IF NOT EXISTS data_quarter_coverage (
                    asset TEXT NOT NULL,
                    timeframe TEXT NOT NULL,
                    dataTier TEXT NOT NULL,
                    quarter TEXT NOT NULL,
                    startTime INTEGER NOT NULL,
                    endTime INTEGER NOT NULL,
                    barCount INTEGER NOT NULL,
                    downloadedAt INTEGER NOT NULL,
                    lastUpdated INTEGER NOT NULL,
                    source TEXT NOT NULL DEFAULT 'CLOUDFLARE_R2',
                    PRIMARY KEY(asset, timeframe, dataTier, quarter)
                )
            """)

            // Create indexes for efficient querying
            database.execSQL("CREATE INDEX IF NOT EXISTS index_data_quarter_coverage_asset_timeframe ON data_quarter_coverage(asset, timeframe)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_data_quarter_coverage_dataTier ON data_quarter_coverage(dataTier)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_data_quarter_coverage_lastUpdated ON data_quarter_coverage(lastUpdated)")

            Timber.i("Migration 14→15: Cloud storage quarter tracking enabled")
        }
    }

    /**
     * Migration from version 15 to 16
     *
     * Changes:
     * - Added soft-delete fields to strategies table (isInvalid, invalidReason, invalidatedAt)
     * - Prevents permanent deletion of strategies with hardcoded prices
     * - Enables strategy validation history and debugging
     *
     * P0-1: Strategy Soft-Delete Implementation
     */
    val MIGRATION_15_16 = object : Migration(15, 16) {
        override fun migrate(database: SupportSQLiteDatabase) {
            Timber.i("Running migration 15→16: Adding soft-delete fields to strategies")

            // Add soft-delete fields to strategies table
            database.execSQL("ALTER TABLE strategies ADD COLUMN isInvalid INTEGER NOT NULL DEFAULT 0")
            database.execSQL("ALTER TABLE strategies ADD COLUMN invalidReason TEXT DEFAULT NULL")
            database.execSQL("ALTER TABLE strategies ADD COLUMN invalidatedAt INTEGER DEFAULT NULL")

            // Create index for efficient filtering of valid strategies
            database.execSQL("CREATE INDEX IF NOT EXISTS index_strategies_isInvalid ON strategies(isInvalid)")

            Timber.i("Migration 15→16: Soft-delete fields added. Strategies with hardcoded prices will be marked invalid instead of deleted.")
        }
    }

    /**
     * Migration from version 16 to 17
     *
     * Changes:
     * - Added data provenance fields to backtest_runs table
     * - Added dataFileHashes column (JSON array of SHA-256 hashes)
     * - Added parserVersion column (semver for data parser)
     * - Added engineVersion column (semver for backtest engine)
     * - Enables 100% reproducible backtest verification
     *
     * P1-4: Data Provenance Implementation (Phase 1.6)
     */
    val MIGRATION_16_17 = object : Migration(16, 17) {
        override fun migrate(database: SupportSQLiteDatabase) {
            Timber.i("Running migration 16→17: Adding data provenance tracking to backtest_runs")

            // Add data provenance fields to backtest_runs table
            database.execSQL("ALTER TABLE backtest_runs ADD COLUMN dataFileHashes TEXT NOT NULL DEFAULT '[]'")
            database.execSQL("ALTER TABLE backtest_runs ADD COLUMN parserVersion TEXT NOT NULL DEFAULT ''")
            database.execSQL("ALTER TABLE backtest_runs ADD COLUMN engineVersion TEXT NOT NULL DEFAULT ''")

            Timber.i("Migration 16→17: Data provenance fields added. Backtest runs are now 100% traceable to source datasets and parser/engine versions.")
        }
    }

    /**
     * Migration from version 17 to 18
     *
     * Changes:
     * - Added cost model tracking fields to backtest_runs table
     * - Added assumedCostBps column (assumed trading cost in basis points)
     * - Added observedCostBps column (observed cost from actual trades)
     * - Added costDeltaBps column (delta between observed and assumed)
     * - Added aggregatedFees column (total fees paid)
     * - Added aggregatedSlippage column (total slippage observed)
     * - Enables tracking of actual vs assumed trading costs per backtest run
     *
     * P1-5: Cost Model Tracking Implementation (Phase 1.7)
     */
    val MIGRATION_17_18 = object : Migration(17, 18) {
        override fun migrate(database: SupportSQLiteDatabase) {
            Timber.i("Running migration 17→18: Adding cost model tracking to backtest_runs")

            // Add cost model tracking fields to backtest_runs table
            database.execSQL("ALTER TABLE backtest_runs ADD COLUMN assumedCostBps REAL NOT NULL DEFAULT 0.0")
            database.execSQL("ALTER TABLE backtest_runs ADD COLUMN observedCostBps REAL NOT NULL DEFAULT 0.0")
            database.execSQL("ALTER TABLE backtest_runs ADD COLUMN costDeltaBps REAL NOT NULL DEFAULT 0.0")
            database.execSQL("ALTER TABLE backtest_runs ADD COLUMN aggregatedFees REAL NOT NULL DEFAULT 0.0")
            database.execSQL("ALTER TABLE backtest_runs ADD COLUMN aggregatedSlippage REAL NOT NULL DEFAULT 0.0")

            Timber.i("Migration 17→18: Cost model tracking fields added. Backtest runs now track assumed vs observed trading costs.")
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
            MIGRATION_6_7,
            MIGRATION_7_8,
            MIGRATION_8_9,
            MIGRATION_9_10,
            MIGRATION_10_11,
            MIGRATION_11_12,
            MIGRATION_12_13,
            MIGRATION_13_14,
            MIGRATION_14_15,
            MIGRATION_15_16,
            MIGRATION_16_17,
            MIGRATION_17_18
        )
    }
}
