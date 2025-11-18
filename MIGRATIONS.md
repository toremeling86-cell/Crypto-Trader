# Database Migrations

**Current Version:** v17
**Schema File:** `app/src/main/java/com/cryptotrader/data/local/AppDatabase.kt`
**Migration File:** `app/src/main/java/com/cryptotrader/data/local/migrations/DatabaseMigrations.kt`

## Migration Chain

```
v1 → v2 → v3 → v4 → v5 → v6 → v7 → v8 → v9 → v10 → v11 → v12 → v13 → v14 → v15 → v16 → v17
```

## Migration History

### v1 → v2: Security Hardening
- **Date:** Early 2024
- **Changes:**
  - Removed `api_keys` table
  - Migrated API keys to EncryptedSharedPreferences
- **Reason:** Security improvement - never store API keys in database

### v2 → v3: AI Strategy Support
- **Date:** Q1 2024
- **Changes:**
  - Added `analysisReport` column to `strategies`
  - Added `approvalStatus` column to `strategies`
  - Added `source` column to `strategies` (USER, AI_CLAUDE)
- **Reason:** Support AI-generated trading strategies

### v3 → v4: Portfolio Tracking
- **Date:** Q1 2024
- **Changes:**
  - Created `portfolio_snapshots` table
- **Reason:** Historical portfolio value tracking

### v4 → v5: Market Data Infrastructure
- **Date:** Q1 2024
- **Changes:**
  - Created `market_snapshots` table
  - Created `ai_market_analysis` table
  - Created `expert_reports` table
  - Created `market_correlations` table
- **Reason:** Live market data and AI analysis support

### v5 → v6: Trading Execution
- **Date:** Q2 2024
- **Changes:**
  - Added `tradingMode` column to `strategies` (INACTIVE, PAPER, LIVE)
  - Created `orders` table
  - Created `positions` table
- **Reason:** Real-time trading execution tracking

### v6 → v7: Execution Status
- **Date:** Q2 2024
- **Changes:**
  - Added `executionStatus` column to `strategies`
  - Added `lastCheckedTime` column to `strategies`
  - Added `triggeredAt` column to `strategies`
  - Added `lastExecutionError` column to `strategies`
  - Added `executionCount` column to `strategies`
- **Reason:** Monitor strategy execution state

### v7 → v8: Order Lifecycle
- **Date:** Q2 2024
- **Changes:**
  - Added `orderId` column to `trades`
  - Added `executionFee` column to `trades`
  - Added `notes` column to `trades`
- **Reason:** Link trades to orders for audit trail

### v8 → v9: Position Tracking Enhancement
- **Date:** Q2 2024
- **Changes:**
  - Added `averagePrice` column to `positions`
  - Added `realizedPnL` column to `positions`
  - Added `unrealizedPnL` column to `positions`
  - Added `stopLossPrice` column to `positions`
  - Added `takeProfitPrice` column to `positions`
- **Reason:** Advanced position management

### v9 → v10: AI Trading Advisor
- **Date:** Q3 2024
- **Changes:**
  - Created `advisor_analysis` table
  - Created `trading_opportunities` table
  - Created `advisor_notifications` table
- **Reason:** AI trading advisor recommendations

### v10 → v11: Meta-Analysis System
- **Date:** Q3 2024
- **Changes:**
  - Created `meta_analysis` table
  - Added `metaAnalysisId` column to `strategies`
  - Added `sourceReportCount` column to `strategies`
- **Reason:** Strategy lineage and learning from expert reports

### v11 → v12: Performance Tracking
- **Date:** Q3 2024
- **Changes:**
  - Added 15 performance metrics to `strategies`:
    - maxDrawdown, avgWinAmount, avgLossAmount
    - profitFactor, sharpeRatio
    - largestWin, largestLoss
    - currentStreak, longestWinStreak, longestLossStreak
    - performanceScore, isTopPerformer, totalProfitPercent
- **Reason:** Comprehensive strategy performance analysis

### v12 → v13: Backend Data Storage (Hedge Fund Quality)
- **Date:** Q4 2024
- **Changes:**
  - Created `ohlc_bars` table (historical OHLC data)
  - Created `technical_indicators` table (precomputed indicators)
  - Created `data_coverage` table (data availability tracking)
  - Created `backtest_runs` table (backtest results)
  - Created `data_quality` table (data quality metrics)
- **Reason:** Professional backtesting infrastructure

### v13 → v14: Data Tier Quality Control
- **Date:** November 2024
- **Changes:**
  - Added `dataTier` column to `ohlc_bars` (TIER_4_BASIC default)
  - Added `dataTier` column to `backtest_runs`
  - Added `tierValidated` column to `backtest_runs`
  - Created indexes for efficient tier querying
- **Reason:** Prevent mixing PREMIUM/PROFESSIONAL/STANDARD/BASIC data

### v14 → v15: Cloud Storage Quarter Tracking
- **Date:** November 2024
- **Changes:**
  - Created `data_quarter_coverage` table
  - Composite primary key: (asset, timeframe, dataTier, quarter)
  - Tracks downloaded quarters from Cloudflare R2
- **Reason:** Efficient cloud data management

### v15 → v16: Strategy Soft-Delete
- **Date:** November 2024
- **Changes:**
  - Added `isInvalid` column to `strategies` (0 = valid, 1 = invalid)
  - Added `invalidReason` column to `strategies`
  - Added `invalidatedAt` column to `strategies`
  - Created index on `isInvalid` for efficient filtering
- **Reason:** Preserve strategy history for debugging (P0-1)

### v16 → v17: Data Provenance (CURRENT)
- **Date:** November 2024
- **Changes:**
  - Added `dataFileHashes` column to `backtest_runs` (JSON array of SHA-256 hashes)
  - Added `parserVersion` column to `backtest_runs` (semver for data parser)
  - Added `engineVersion` column to `backtest_runs` (semver for backtest engine)
- **Reason:** 100% reproducible backtest verification (P1-4, Phase 1.6)
- **Rollback:** Safe - new columns have default values, existing runs unaffected

## Pending Migrations

### v17 → v18: Cost Model Tracking (P1-5)
- **Status:** Planned
- **Changes:**
  - Add cost tracking fields to `backtest_runs`:
    - `assumedCostBps` - Assumed cost basis points
    - `observedCostBps` - Observed cost from actual trades
    - `costDeltaBps` - Delta between assumed and observed
    - `aggregatedFees` - Total fees paid
    - `aggregatedSlippage` - Total slippage observed
- **Reason:** Track actual vs assumed trading costs per run

### v18 → v19: Meta-Analysis Integration (P0-2)
- **Status:** Planned
- **Changes:**
  - Re-enable MetaAnalysisEntity in AppDatabase
  - Add `learningEnabled` column to `meta_analysis`
  - Create `knowledge_base` table for cross-strategy learning
- **Reason:** Restore full AI learning functionality

## Migration Testing

All migrations are tested in:
- `app/src/test/java/com/cryptotrader/data/local/migrations/DatabaseMigrationTest.kt`

Each migration test:
1. Creates database at version N-1
2. Inserts test data
3. Runs migration to version N
4. Validates schema and data integrity

## Rollback Policy

**PRODUCTION:** Never use `fallbackToDestructiveMigration()`

For rollback:
1. Uninstall app (preserves user data in backup)
2. Install previous version
3. Restore from backup

## Schema Export

Schema files exported to: `app/schemas/` (if `exportSchema = true` in AppDatabase)

Currently: `exportSchema = false` (development mode)
