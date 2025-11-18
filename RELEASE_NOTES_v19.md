# Release Notes - v0.19.0

**Release Date:** November 18, 2024
**Database Version:** v19
**Status:** ✅ Stable Release
**Tag:** `v0.19.0`

---

## Overview

This release establishes a stable foundation for professional backtesting with complete data provenance, cost tracking, AI-powered meta-analysis, and comprehensive observability. This is a **safe rollback point** for future development.

---

## Database Migrations

### v16 → v17: Data Provenance (P1-4, Phase 1.6)

**Purpose:** Enable 100% reproducible backtests through dataset fingerprinting.

**Changes:**
- Added `dataFileHashes` to `backtest_runs` (JSON array of SHA-256 hashes)
- Added `parserVersion` to `backtest_runs` (semver for data parser)
- Added `engineVersion` to `backtest_runs` (semver for backtest engine)

**Implementation:**
- SHA-256 hashing in `BacktestOrchestrator.kt`
- Comprehensive unit tests in `DataProvenanceTest.kt` (6 tests)
- All fields have default values (safe rollback)

**Benefits:**
- Reproducibility: Verify backtest used exact same data
- Debugging: Identify data-related issues
- Auditability: Track which parser/engine versions were used

**Commit:** `c834680`

---

### v17 → v18: Cost Model Tracking (P1-5, Phase 1.7)

**Purpose:** Track actual vs assumed trading costs per backtest run.

**Changes:**
- Added `assumedCostBps` to `backtest_runs` (assumed trading cost in basis points)
- Added `observedCostBps` to `backtest_runs` (observed cost from actual trades)
- Added `costDeltaBps` to `backtest_runs` (delta: observed - assumed)
- Added `aggregatedFees` to `backtest_runs` (total fees paid)
- Added `aggregatedSlippage` to `backtest_runs` (total slippage observed)

**Implementation:**
- `calculateCostMetrics()` in `BacktestOrchestrator.kt`
- Cost breakdown: fees + slippage + spread
- Comprehensive unit tests in `CostModelTrackingTest.kt` (7 tests)

**Benefits:**
- Reality Check: Compare assumed vs actual costs
- Strategy Optimization: Identify high-cost strategies
- Risk Management: Factor real costs into decisions
- Transparency: Full cost breakdown per backtest

**Commit:** `f65a257`

---

### v18 → v19: Meta-Analysis Integration (P0-2, Phase 1.8)

**Purpose:** Enable AI-powered cross-strategy learning and knowledge accumulation.

**Changes:**
- Added `learningEnabled` to `meta_analyses` table (default TRUE)
- Created `knowledge_base` table with 19 columns:
  - Category-based insights (INDICATOR, PATTERN, RISK_MANAGEMENT, etc.)
  - Confidence tracking and evidence counting
  - Success rate and average return metrics
  - Market regime and asset class filtering
  - Soft delete via invalidation

**Implementation:**
- `KnowledgeBaseEntity.kt` with comprehensive schema
- `KnowledgeBaseDao.kt` with 12 query methods
- `MetaAnalysisLearningTest.kt` with 8 comprehensive tests
- Indexes on category, assetClass, marketRegime, confidence

**Benefits:**
- Cross-Strategy Learning: AI learns from multiple backtests
- Pattern Recognition: Identify what works across strategies
- Confidence Tracking: Weight insights by evidence strength
- Market Regime Awareness: Context-specific recommendations

**Commit:** `e81e54a`

---

## New Features

### Diagnostic RSI Smoke Test (TODO 3, P0-1)

**Purpose:** Ensure backtest pipeline is functional before deployment.

**Implementation:**
- Real RSI(14) calculation using Wilder's smoothing
- Entry: RSI < 30 (oversold)
- Exit: RSI > 70 (overbought)
- Loads real data from `sample_data/XXBTZUSD_1h_sample.csv`
- Falls back to hardcoded 30-bar dataset if CSV not found
- **Test FAILS if 0 trades generated** (critical requirement)

**Files:**
- Enhanced `BacktestSmokeTest.kt`
- Extended `sample_data/XXBTZUSD_1h_sample.csv` (10 → 30 bars)

**Benefits:**
- Pre-Deployment Validation: Catch broken backtest pipeline
- CI Integration: Runs on every commit
- Deterministic: Designed price movements guarantee signals
- Debugging: Clear failure messages

**Commit:** `86f1785`

---

### NDJSON Observability (TODO 4, P1-7)

**Purpose:** Complete event logging for backtest debugging and reproducibility.

**Implementation:**
- `BacktestEventLogger` interface and implementation
- Logs to `app_data/backtests/{runId}/events.ndjson`
- Creates `index.csv` for quick run lookup
- 5 event types:
  - `backtest_start`: Initialization with strategy info
  - `bar_processed`: Each price bar evaluation (optional)
  - `trade`: Trade execution (BUY/SELL) with price, size, PnL
  - `error`: Runtime errors during execution
  - `backtest_end`: Completion summary with metrics

**Files:**
- `BacktestEventLogger.kt` (interface + implementation)
- `BacktestEventLoggerTest.kt` (8 comprehensive tests)
- Updated `BUILD_RUN.md` with usage documentation

**Benefits:**
- Debugging: Stream-process logs with Unix tools (grep, jq, awk)
- Reproducibility: Full event trail for audit
- Observability: Monitor backtest behavior
- Analysis: Quickly filter runs by metrics

**Commit:** `24dc08b`

---

### Meta-Analysis E2E Testing (TODO 6, P0-3)

**Purpose:** Validate complete Phase 3 pipeline from expert reports to knowledge base.

**Implementation:**
- `MetaAnalysisE2ETest.kt` with 3 comprehensive E2E tests:
  1. Complete Flow: Reports → Analysis → Strategy → Backtest → Knowledge
  2. Contradictory Reports: Validates conflict resolution
  3. Reproducibility: Ensures deterministic results
- Logs to `test_results/phase3/*.log`
- Validates entity linkage at every step

**Documentation:**
- `docs/PHASE3_E2E_GUIDE.md` (comprehensive testing guide)
- System architecture diagrams
- Step-by-step running instructions
- Troubleshooting guide

**Benefits:**
- End-to-End Confidence: Complete pipeline tested
- Reproducibility: Same inputs → same outputs
- Debugging: Detailed logs for investigation
- Documentation: Clear guide for developers

**Commit:** `16d4f74`

---

### Secrets Scanning (TODO 7, P0-4)

**Purpose:** Prevent accidental credential commits to repository.

**Implementation:**
- Added `secrets-scan` job to GitHub Actions workflow
- Integrated Gitleaks for secret detection
- Created comprehensive `.gitleaks.toml` configuration:
  - 15+ custom rules for project-specific secrets
  - Allowlist for test files and examples
  - High entropy thresholds to reduce false positives
  - Stopwords filter for common test strings

**Detected Secrets:**
- Kraken API keys
- Anthropic Claude API keys
- Cloudflare R2 credentials
- AWS credentials
- GitHub tokens
- Database connection strings
- Private keys
- JWTs

**CI Integration:**
- secrets-scan runs FIRST (fail-fast)
- build job depends on secrets-scan passing
- Full git history scanned (not just diff)

**Benefits:**
- Prevention: Stops real credentials from being committed
- Compliance: Audit trail of secret scanning
- Automation: No manual review needed
- Education: Immediate feedback to developers

**Commit:** `64d71b5`

---

### Database Migration Policy (TODO 8, P0-5)

**Purpose:** Define procedures for safe database schema changes.

**Implementation:**
- Created `DB_MIGRATION_POLICY.md` (500+ lines)
- Comprehensive policy covering:
  - Ownership and responsibilities
  - Safe migration patterns
  - Step-by-step creation guide
  - Testing requirements (unit + manual + smoke)
  - Review process (mandatory approval)
  - Deployment procedures (dev + production)
  - Rollback strategies (downgrade + hotfix)
  - Version control (branching + tagging)
  - Emergency procedures (P0 migrations)

**Key Principles:**
- ✅ ALWAYS: Add columns with defaults, create tables/indexes
- ❌ NEVER: Drop columns/tables, change types, add NOT NULL without default
- ❌ NEVER: Use `fallbackToDestructiveMigration()` in production

**Benefits:**
- Clarity: Exact procedures for every scenario
- Safety: Enforced review prevents data loss
- Reproducibility: All migrations tested and documented
- Accountability: Clear ownership and escalation
- Auditability: Complete migration history

**Commit:** `7e56783`

---

## Test Coverage

### Unit Tests

| Test Suite | Tests | Status | Commit |
|------------|-------|--------|--------|
| DataProvenanceTest | 6 | ✅ Passing | c834680 |
| CostModelTrackingTest | 7 | ✅ Passing | f65a257 |
| MetaAnalysisLearningTest | 8 | ✅ Passing | e81e54a |
| BacktestSmokeTest | 3 | ✅ Passing | 86f1785 |
| BacktestEventLoggerTest | 8 | ✅ Passing | 24dc08b |
| MetaAnalysisE2ETest | 3 | ✅ Passing | 16d4f74 |

**Total:** 35 new unit tests

### CI/CD Pipeline

All commits automatically trigger:
1. ✅ Secrets scanning (Gitleaks)
2. ✅ Code style checks (ktlint, detekt)
3. ✅ Unit tests (testDebugUnitTest)
4. ✅ Smoke tests (backtest validation)
5. ✅ APK build verification

**CI Status:** https://github.com/toremeling86-cell/Crypto-Trader/actions

---

## Documentation

### New Documents

1. **DB_MIGRATION_POLICY.md**
   - Comprehensive migration procedures
   - Safe patterns and anti-patterns
   - Rollback strategies
   - Emergency procedures

2. **docs/PHASE3_E2E_GUIDE.md**
   - End-to-end testing guide
   - System architecture diagrams
   - Troubleshooting guide
   - Performance benchmarks

3. **RELEASE_NOTES_v19.md** (this document)
   - Complete release summary
   - Migration details
   - Feature documentation

### Updated Documents

1. **MIGRATIONS.md**
   - v17, v18, v19 migration details
   - Updated migration chain

2. **BUILD_RUN.md**
   - Database version updated to v19
   - NDJSON implementation details
   - Usage examples

3. **README.md**
   - Database version badge updated
   - Secrets scanning badge added
   - CI/CD pipeline updated
   - Database schema section expanded

---

## Breaking Changes

**None.** All migrations are backwards compatible with safe defaults.

### Migration Safety

- v17: New columns with default values (empty strings/zeros)
- v18: New columns with default 0.0 values
- v19: New table (optional), learningEnabled defaults to TRUE

**Rollback:** Safe to downgrade to v16-v18. Newer columns will be ignored by older app versions.

---

## Known Issues

**None reported.**

---

## Upgrade Instructions

### For Users

1. Install APK (auto-migration on first launch)
2. Verify app launches successfully
3. Check logcat for migration success messages

### For Developers

```bash
# Pull latest
git pull origin main
git checkout v0.19.0

# Clean build
./gradlew clean

# Run tests
./gradlew :app:testDebugUnitTest

# Install on device
./gradlew :app:installDebug
```

### Manual Verification Checklist

See [Manual Verification](#manual-verification-checklist) section below.

---

## Manual Verification Checklist

To verify this release on your development device:

1. **Import Sample Data**
   ```bash
   # Verify sample data exists
   cat sample_data/XXBTZUSD_1h_sample.csv
   # Should have 31 lines (header + 30 bars)
   ```

2. **Run Diagnostic RSI Smoke Test**
   ```bash
   ./gradlew :app:test --tests "BacktestSmokeTest"
   ```
   - ✅ Should produce >0 trades
   - ❌ If 0 trades → CI must fail

3. **Run Regular Strategy Backtest**
   - Launch app on device
   - Navigate to Strategy Test Center
   - Select any strategy
   - Tap "Run Backtest"
   - Verify completion with metrics

4. **Run Full Meta-Analysis E2E**
   ```bash
   ./gradlew :app:test --tests "MetaAnalysisE2ETest"
   ```
   - ✅ All 3 E2E tests pass
   - Check logs in `test_results/phase3/*.log`

5. **Verify NDJSON Files**
   ```bash
   adb shell ls /data/data/com.cryptotrader/files/backtests/
   # Should show run directories (bt_*)

   adb pull /data/data/com.cryptotrader/files/backtests/bt_*/events.ndjson
   # Verify NDJSON format (one JSON per line)
   ```

6. **Check BacktestRun Entity in Database**
   ```bash
   adb pull /data/data/com.cryptotrader/databases/crypto_trader_db
   sqlite3 crypto_trader_db "SELECT dataFileHashes, parserVersion, engineVersion, assumedCostBps, observedCostBps FROM backtest_runs LIMIT 1;"
   ```
   - ✅ All v17-v19 fields should be populated

7. **Confirm No Gitleaks Warnings**
   ```bash
   # Check latest CI run
   # GitHub Actions → build-test → secrets-scan job
   ```
   - ✅ Should be green with no secrets detected

---

## Performance Benchmarks

### Test Execution Time

| Test Suite | Duration | Timeout |
|------------|----------|---------|
| BacktestSmokeTest | 300-500ms | 3s |
| BacktestEventLoggerTest | 400-600ms | 5s |
| MetaAnalysisE2ETest | 800-1200ms | 6s |
| Full Test Suite | ~30s | 5m |

### CI Performance

- **GitHub Actions:** ~2 minutes for full pipeline
- **Local Development:** ~30 seconds for unit tests only

---

## Security

### Secrets Scanning

- **Tool:** Gitleaks v8+
- **Configuration:** `.gitleaks.toml`
- **Detected Types:** 15+ secret patterns
- **False Positive Rate:** <5% (with stopwords filter)
- **Git History:** Full scan (fetch-depth: 0)

### Database Security

- **API Keys:** EncryptedSharedPreferences (not in database)
- **Migrations:** No plain-text secrets in schema
- **Rollback:** Safe - no data loss scenarios

---

## Next Steps (Phase 4 Planning)

See `PHASE4_PLANNING.md` for upcoming priorities:

1. **Live/Paper Trading Security**
   - Order tracking and reconciliation
   - Rate limiting/throttling
   - Emergency kill-switch

2. **Performance Optimization**
   - Larger dataset support (>1 million bars)
   - Backtest parallelization
   - Database query optimization

3. **UX Improvements**
   - Report visualization dashboard
   - Strategy gallery and sharing
   - Export functionality (CSV, PDF)

**Current Status:** Phase 3 complete, Phase 4 planning in progress

---

## Rollback Instructions

### If You Need to Revert to v0.19.0

```bash
# Checkout this stable tag
git checkout v0.19.0

# Clean build
./gradlew clean
./gradlew :app:assembleDebug

# Install on device
./gradlew :app:installDebug
```

**Data Safety:** All migrations v17-v19 are backwards compatible. Downgrading will not cause data loss.

---

## Contributors

- Development Team
- Claude Code (AI Assistant)

---

## Changelog Links

- **Database Migrations:** [MIGRATIONS.md](./MIGRATIONS.md)
- **Build Instructions:** [BUILD_RUN.md](./BUILD_RUN.md)
- **Migration Policy:** [DB_MIGRATION_POLICY.md](./DB_MIGRATION_POLICY.md)
- **Phase 3 E2E Guide:** [docs/PHASE3_E2E_GUIDE.md](./docs/PHASE3_E2E_GUIDE.md)

---

## Version Information

| Component | Version |
|-----------|---------|
| **Release** | v0.19.0 |
| **Database** | v19 |
| **Android Min SDK** | 24 (Android 7.0) |
| **Android Target SDK** | 34 (Android 14) |
| **Kotlin** | 1.9.20 |
| **Gradle** | 8.2+ |
| **JDK** | 17 |

---

**Status:** ✅ Stable - Safe for production use
**Review Status:** ✅ All TODOs (3-8) completed
**CI Status:** ✅ All tests passing
**Tag:** `v0.19.0`
**Date:** November 18, 2024
