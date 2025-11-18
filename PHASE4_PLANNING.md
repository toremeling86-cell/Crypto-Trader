# Phase 4 Planning - Post v0.19.0

**Status:** 🔄 Planning Stage
**Release:** Post v0.19.0 stable
**Database:** v19 (FROZEN until planning approved)
**Created:** November 18, 2024

---

## Overview

Phase 4 focuses on three key areas after establishing a stable backtest foundation in v0.19.0:
1. Live/Paper Trading Security
2. Performance Optimization
3. UX Improvements

**Current Status:** v0.19.0 is a **safe rollback point**. Phase 4 work will NOT begin until:
- Manual verification checklist complete
- CI green for 48 hours
- No production issues reported
- Database freeze lifted

---

## Section 1: Live/Paper Trading Security

**Priority:** P0 (Critical for real money trading)

### Objectives

Enable safe live and paper trading with comprehensive risk controls and monitoring.

### Proposed Features (Priority Order)

#### 1.1 Order Tracking & Reconciliation (P0-1)

**Problem:** Need to track order lifecycle from creation to execution to settlement.

**Proposed Solution:**
- Extend `OrderEntity` with lifecycle states:
  - `PENDING` → `SUBMITTED` → `PARTIALLY_FILLED` → `FILLED` / `CANCELLED` / `REJECTED`
- Create `OrderReconciliationService`:
  - Poll Kraken API for order status every 5 seconds
  - Update local database with execution details
  - Detect and alert on order discrepancies (e.g., filled but not recorded locally)
- Add `order_history` table for audit trail:
  - Track every state change with timestamp
  - Store fill prices, quantities, fees
  - Link to corresponding `TradeEntity`

**Database Impact:** New table `order_history`, possible migration v20
**Testing:** Mock Kraken API responses, simulate order lifecycle
**Risk:** Medium (new table, no breaking changes to existing schema)

#### 1.2 Rate Limiting & Throttling (P0-2)

**Problem:** Kraken API has rate limits (15-20 req/sec). Exceeding limits causes temporary bans.

**Proposed Solution:**
- Create `RateLimiter` class with token bucket algorithm:
  - 15 tokens per second for REST API
  - Separate limit for WebSocket connections
- Implement request queue:
  - Queue orders when rate limit approached
  - Prioritize critical requests (cancel orders > new orders > queries)
  - Retry with exponential backoff on 429 errors
- Add monitoring:
  - Log rate limit warnings
  - Alert if approaching 80% of limit
  - Dashboard widget showing current API usage

**Database Impact:** None (in-memory rate limiter)
**Testing:** Stress test with rapid API calls
**Risk:** Low (no schema changes)

#### 1.3 Emergency Kill-Switch (P0-3)

**Problem:** Need to immediately stop ALL trading activity in emergency scenarios.

**Proposed Solution:**
- Add `emergency_mode` flag to app settings (EncryptedSharedPreferences)
- Create `EmergencyKillSwitch` service:
  - Cancels all open orders immediately
  - Closes all open positions (market orders)
  - Disables strategy execution
  - Sends alert notification
  - Logs emergency event to audit trail
- UI trigger:
  - Big red "EMERGENCY STOP" button on dashboard
  - Requires confirmation + reason input
  - Shows confirmation dialog with countdown (5 seconds)
- Auto-trigger conditions:
  - Portfolio drawdown > 10% in 1 hour
  - API connection lost for > 5 minutes
  - Detected order execution errors > 3 in 10 minutes

**Database Impact:** Add `emergency_events` table for audit (v21?)
**Testing:** Simulate emergency scenarios, verify order cancellation
**Risk:** High (critical safety feature, must work 100%)

---

## Section 2: Performance Optimization

**Priority:** P1 (Important but not blocking)

### Objectives

Enable larger datasets and faster backtests to support more comprehensive strategy testing.

### Proposed Features (Priority Order)

#### 2.1 Larger Dataset Support (P1-1)

**Problem:** Current sample data is 30 bars. Real backtests need 1000-10,000+ bars.

**Proposed Solution:**
- Optimize `OHLCBarDao` queries:
  - Add pagination (load 1000 bars at a time)
  - Use indexes on `timestamp` + `asset` + `timeframe` + `dataTier`
  - Implement lazy loading (don't load all bars into memory)
- Compress older data:
  - Store bars >6 months old in compressed format (Gzip)
  - Decompress on-demand during backtest
- Database vacuum:
  - Schedule periodic `VACUUM` to reclaim space
  - Monitor database file size

**Database Impact:** Add indexes, possible schema for compressed data (v22?)
**Testing:** Load 100k bars, measure query performance
**Risk:** Medium (query optimization, no breaking changes)

#### 2.2 Backtest Parallelization (P1-2)

**Problem:** Running 10 backtests sequentially takes 10x time of 1 backtest.

**Proposed Solution:**
- Create `ParallelBacktestOrchestrator`:
  - Uses Kotlin coroutines for parallel execution
  - Limit concurrency to 3-5 backtests (avoid overloading device)
  - Each backtest gets isolated dataset copy (no shared state)
- Progress tracking:
  - Show progress bar for batch backtests
  - Display ETA based on completed runs
  - Allow cancellation of individual runs
- Result aggregation:
  - Compare multiple strategies side-by-side
  - Generate summary report (best/worst performers)
  - Export to CSV for external analysis

**Database Impact:** None (parallel reads, sequential writes)
**Testing:** Run 10 backtests in parallel, verify no race conditions
**Risk:** Medium (concurrency complexity)

---

## Section 3: UX Improvements

**Priority:** P2 (Nice to have, enhances user experience)

### Objectives

Make the app more user-friendly and visually appealing for strategy development and analysis.

### Proposed Features (Priority Order)

#### 3.1 Report Visualization Dashboard (P2-1)

**Problem:** Expert reports and meta-analyses are text-only. Hard to visualize insights.

**Proposed Solution:**
- Create `ReportDashboardScreen`:
  - List all expert reports with filters (date, category, sentiment)
  - Show sentiment distribution (pie chart: bullish/bearish/neutral)
  - Display meta-analysis timeline (when analyses were run)
  - Highlight top insights from knowledge base
- Report detail view:
  - Markdown rendering with syntax highlighting
  - Inline charts for mentioned data (e.g., "RSI at 65" → show RSI chart)
  - Related strategies section (which strategies used this report)
- Meta-analysis visualization:
  - Show consensus vs contradictions
  - Display confidence meter (0-100%)
  - Link to generated strategies

**Database Impact:** Add `report_views` table to track views/favorites (v23?)
**Testing:** Render 100 reports, measure scroll performance
**Risk:** Low (presentation layer only)

#### 3.2 Strategy Gallery & Sharing (P2-2)

**Problem:** Strategies are isolated. No way to discover or share successful strategies.

**Proposed Solution:**
- Create `StrategyGalleryScreen`:
  - Grid view of all strategies with thumbnails (equity curve preview)
  - Sort by performance (Sharpe ratio, total return, win rate)
  - Filter by risk level, trading pair, source (USER vs AI_CLAUDE)
  - Tag-based search (e.g., "RSI", "momentum", "reversal")
- Strategy sharing:
  - Export strategy to JSON file
  - Share via Android share sheet (email, Drive, etc.)
  - Import strategy from JSON (with validation)
  - QR code generation for quick sharing
- Strategy templates:
  - Pre-built strategies (RSI, MACD, Bollinger Bands)
  - One-click import and customize
  - Community templates (future: online gallery)

**Database Impact:** Add `strategy_tags` junction table (v24?)
**Testing:** Export/import 10 strategies, verify data integrity
**Risk:** Low (no critical functionality)

---

## Implementation Timeline (Tentative)

**Phase 4.1 (Live Trading Security)** - 2-3 weeks
- Order tracking & reconciliation
- Rate limiting & throttling
- Emergency kill-switch

**Phase 4.2 (Performance)** - 1-2 weeks
- Larger dataset support
- Backtest parallelization

**Phase 4.3 (UX)** - 2-3 weeks
- Report visualization dashboard
- Strategy gallery & sharing

**Total Estimated Time:** 5-8 weeks

---

## Dependencies & Risks

### Dependencies

1. **v0.19.0 stability** - Must verify no production issues before proceeding
2. **Database freeze lift** - No new migrations until approved
3. **API access** - Kraken API credentials for live trading tests
4. **Test devices** - Physical Android devices for real-world testing

### Risks

| Risk | Impact | Mitigation |
|------|--------|-----------|
| Live trading bugs cause real money loss | **CRITICAL** | Extensive paper trading phase, emergency kill-switch |
| Performance optimization breaks existing features | High | Comprehensive regression testing, feature flags |
| Database migrations fail on user devices | High | Follow DB_MIGRATION_POLICY.md strictly |
| API rate limits block trading | Medium | Implement robust rate limiting before live trading |
| UX changes confuse existing users | Low | Gradual rollout, user feedback collection |

---

## Success Metrics

### Phase 4.1 (Security)

- [ ] 0 order reconciliation discrepancies in 100 paper trades
- [ ] Rate limiter prevents 100% of API limit violations
- [ ] Emergency kill-switch executes in <5 seconds
- [ ] All emergency events logged to audit trail

### Phase 4.2 (Performance)

- [ ] Backtest with 10,000 bars completes in <30 seconds
- [ ] 5 parallel backtests complete in <2 minutes
- [ ] Database size remains <100MB with 100k bars
- [ ] Query performance: <100ms for typical backtest data fetch

### Phase 4.3 (UX)

- [ ] 90% of users find report dashboard useful (survey)
- [ ] Strategy export/import works for 100% of test cases
- [ ] Gallery loads 100 strategies in <1 second
- [ ] 0 crashes in UX components

---

## Open Questions

1. **Live Trading Approval:** Do we need user agreement/disclaimer before enabling live trading?
2. **Data Storage:** Should we use cloud storage (Cloudflare R2) for large datasets?
3. **Concurrency Limits:** What's the optimal number of parallel backtests for typical Android devices?
4. **Community Features:** Should we build online strategy gallery (requires backend)?
5. **Export Format:** JSON only, or also support CSV, XML, proprietary format?

---

## Phase 4 Approval Process

### Before Starting Phase 4:

1. **Complete Manual Verification** (RELEASE_NOTES_v19.md checklist)
2. **Monitor CI for 48 hours** (ensure green, no flaky tests)
3. **Review this planning document** with database owner
4. **Lift database freeze** (update DB_MIGRATION_POLICY.md)
5. **Create Phase 4 branch** (`feature/phase4`)
6. **Begin implementation** following normal procedures

### Approval Checklist:

- [ ] Manual verification complete (all items checked)
- [ ] CI green for 48+ hours on main
- [ ] No production issues reported
- [ ] Database owner reviewed and approved this plan
- [ ] Team consensus on priorities (1.1, 1.2, 1.3 first)
- [ ] Freeze officially lifted (announcement sent)

**Approver:** [Database Owner Name]
**Approval Date:** [TBD]

---

## Related Documents

- **Release Notes:** [RELEASE_NOTES_v19.md](./RELEASE_NOTES_v19.md)
- **Migration Policy:** [DB_MIGRATION_POLICY.md](./DB_MIGRATION_POLICY.md)
- **Migration History:** [MIGRATIONS.md](./MIGRATIONS.md)
- **Build Instructions:** [BUILD_RUN.md](./BUILD_RUN.md)
- **Phase 3 E2E Guide:** [docs/PHASE3_E2E_GUIDE.md](./docs/PHASE3_E2E_GUIDE.md)

---

## Notes

This is a **planning document only**. No implementation should begin until:
1. Database freeze is lifted
2. This document is approved by database owner
3. Manual verification checklist is complete

**Phase 4 Status:** 📋 **PLANNING** (awaiting v0.19.0 verification)

---

**Last Updated:** November 18, 2024
**Next Review:** After manual verification complete (TBD)
