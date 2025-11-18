# Database Migration Policy

**Version:** 1.0
**Effective Date:** November 2024
**Last Updated:** November 18, 2024
**Status:** ✅ Active (TODO 8)

---

## 🔒 FREEZE NOTICE - v19 Stable Release

**⚠️ DATABASE SCHEMA FREEZE UNTIL PHASE 4 PLANNING COMPLETE**

**Effective:** November 18, 2024 (Release v0.19.0)
**Scope:** Database v19 and core backtest systems
**Duration:** Until Phase 4 planning approved by database owner

### What is Frozen:

1. **No new database migrations** without explicit Phase 4 approval
2. **No breaking changes** to these core systems:
   - `BacktestEngine.kt`
   - `BacktestOrchestrator.kt`
   - `StrategyEvaluator.kt` / `StrategyEvaluatorV2.kt`
   - `TradingCostModel.kt`
   - `BacktestDataProvider.kt`
   - All `*Entity.kt` files (database schema)
3. **No changes** to migration files (`MIGRATION_16_17`, `MIGRATION_17_18`, `MIGRATION_18_19`)

### What is Allowed:

✅ **Bug fixes** that don't change schema or core logic
✅ **Documentation updates**
✅ **UI/UX improvements** (presentation layer only)
✅ **New features** outside of backtest core (e.g., report visualization)
✅ **Performance optimizations** with database owner approval
✅ **Test additions** (strongly encouraged)

### Rationale:

Database v19 represents a **stable foundation** with:
- Complete data provenance tracking
- Accurate cost modeling
- AI-powered meta-analysis
- Full observability (NDJSON)
- Comprehensive testing

This freeze ensures:
- Time to verify production stability
- Manual verification completion
- Phase 4 planning before new complexity
- Safe rollback point (v0.19.0 tag)

### How to Request Changes:

1. **Document the need** in PHASE4_PLANNING.md
2. **Get database owner approval** before implementation
3. **Wait for freeze lift** (announced via team communication)
4. **Follow normal migration procedures** once freeze is lifted

### Freeze Lift Criteria:

- [ ] Manual verification checklist completed (see RELEASE_NOTES_v19.md)
- [ ] CI green for 48 hours on main
- [ ] No production issues reported
- [ ] Phase 4 planning document approved
- [ ] Database owner explicitly lifts freeze

**Freeze Status:** 🔒 **ACTIVE**
**Next Review:** 7 days from November 18, 2024

---

## Table of Contents

1. [Purpose](#purpose)
2. [Ownership](#ownership)
3. [Migration Principles](#migration-principles)
4. [Creating Migrations](#creating-migrations)
5. [Testing Requirements](#testing-requirements)
6. [Review Process](#review-process)
7. [Deployment Procedure](#deployment-procedure)
8. [Rollback Strategy](#rollback-strategy)
9. [Version Control](#version-control)
10. [Emergency Procedures](#emergency-procedures)

---

## Purpose

This policy defines the process for creating, testing, and deploying database schema changes in the CryptoTrader Android application. Adherence to this policy ensures:

- **Data Safety:** User data is never lost during migrations
- **Reproducibility:** Migrations are tested and deterministic
- **Auditability:** All schema changes are tracked and documented
- **Reliability:** Migrations work on all supported Android versions
- **Rollback Safety:** Failed migrations can be recovered

---

## Ownership

### Database Owner

**Primary:** Lead Android Developer
**Backup:** Senior Developer

### Responsibilities

1. **Approve all migrations** before merge to main
2. **Review migration code** for correctness and safety
3. **Maintain MIGRATIONS.md** with detailed history
4. **Coordinate breaking changes** with team
5. **Monitor production migrations** for failures
6. **Execute emergency rollbacks** if needed

### Escalation

If database owner is unavailable:
1. Backup owner reviews and approves
2. For P0 emergencies: Any senior developer may approve with post-hoc review
3. Document all emergency approvals in MIGRATIONS.md

---

## Migration Principles

### Safe Migration Patterns

✅ **ALWAYS DO:**
- Add columns with default values
- Create new tables
- Create new indexes
- Add NOT NULL constraints with defaults
- Expand column sizes (e.g., VARCHAR(50) → VARCHAR(100))
- Add foreign keys with cascade deletes

❌ **NEVER DO:**
- Drop columns (unless truly unused for 6+ months)
- Drop tables without explicit approval
- Change column types without migration path
- Add NOT NULL without default value
- Use `fallbackToDestructiveMigration()` in production

### Example: Safe Column Addition

```kotlin
// CORRECT: Add column with default value
database.execSQL("ALTER TABLE strategies ADD COLUMN newField TEXT NOT NULL DEFAULT ''")

// WRONG: Add column without default (will crash on existing data)
database.execSQL("ALTER TABLE strategies ADD COLUMN newField TEXT NOT NULL")
```

### Backwards Compatibility

All migrations MUST be **backwards compatible** for at least one release cycle:

| Version | Change | Backwards Compatible? |
|---------|--------|----------------------|
| v17 → v18 | Add column `observedCostBps` | ✅ Yes (default 0.0) |
| v18 → v19 | Create `knowledge_base` table | ✅ Yes (new table, optional) |
| v19 → v20 | Drop unused `legacy_trades` table | ⚠️ Requires approval |

---

## Creating Migrations

### Step 1: Update Entity

Modify the Room entity class:

```kotlin
// app/src/main/java/com/cryptotrader/data/local/entities/StrategyEntity.kt

@Entity(tableName = "strategies")
data class StrategyEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,

    // NEW: Add new field with default value (v20)
    val executionTimeoutMs: Long = 30000 // Default: 30 seconds
)
```

### Step 2: Create Migration

Add migration to `DatabaseMigrations.kt`:

```kotlin
// app/src/main/java/com/cryptotrader/data/local/migrations/DatabaseMigrations.kt

val MIGRATION_19_20 = object : Migration(19, 20) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Add new column with default value
        database.execSQL(
            "ALTER TABLE strategies ADD COLUMN executionTimeoutMs INTEGER NOT NULL DEFAULT 30000"
        )

        // Add index if needed for query performance
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS index_strategies_executionTimeoutMs ON strategies(executionTimeoutMs)"
        )

        Timber.i("Migration 19→20 completed: Added executionTimeoutMs to strategies")
    }
}
```

### Step 3: Register Migration

Update `DatabaseMigrations.kt` migration list:

```kotlin
val ALL_MIGRATIONS = arrayOf(
    // ... existing migrations
    MIGRATION_18_19,
    MIGRATION_19_20  // NEW
)
```

### Step 4: Update AppDatabase

Increment version in `AppDatabase.kt`:

```kotlin
@Database(
    entities = [ /* ... */ ],
    version = 20,  // Increment from 19
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    // ...
}
```

### Step 5: Document Migration

Update `MIGRATIONS.md`:

```markdown
### v19 → v20: Strategy Execution Timeout
- **Date:** November 2024
- **Changes:**
  - Added `executionTimeoutMs` column to `strategies` (default 30000ms)
  - Added index on `executionTimeoutMs` for query performance
- **Reason:** Prevent runaway strategy execution (P2-3, Phase 2.1)
- **Rollback:** Safe - column has default value, app can ignore on downgrade
```

---

## Testing Requirements

### Unit Tests (MANDATORY)

Every migration MUST have a corresponding test:

```kotlin
// app/src/test/java/com/cryptotrader/data/local/migrations/DatabaseMigrationTest.kt

@Test
fun migrate19To20() {
    // GIVEN: Database at version 19
    val db = helper.createDatabase(TEST_DB, 19)
    db.execSQL("INSERT INTO strategies (id, name) VALUES (1, 'Test Strategy')")
    db.close()

    // WHEN: Migrate to version 20
    val migratedDb = helper.runMigrationsAndValidate(TEST_DB, 20, true, MIGRATION_19_20)

    // THEN: New column should exist with default value
    val cursor = migratedDb.query("SELECT executionTimeoutMs FROM strategies WHERE id = 1")
    cursor.moveToFirst()
    assertEquals(30000, cursor.getLong(0))
    cursor.close()

    // AND: Index should exist
    val indexCursor = migratedDb.query(
        "SELECT name FROM sqlite_master WHERE type='index' AND name='index_strategies_executionTimeoutMs'"
    )
    assertTrue("Index should be created", indexCursor.count > 0)
    indexCursor.close()
}
```

### Manual Testing (RECOMMENDED)

1. **Install previous version** (v19) on test device
2. **Add test data** (create strategies, trades, etc.)
3. **Install new version** (v20) - migration runs automatically
4. **Verify data integrity:**
   - All existing data still present?
   - New columns have correct default values?
   - App functions normally?
5. **Check logcat** for migration success message

### Smoke Test (REQUIRED for P0 migrations)

Run backtest smoke test after migration:

```bash
./gradlew :app:test --tests "BacktestSmokeTest"
```

---

## Review Process

### Pre-Review Checklist

Before requesting review, ensure:

- [ ] Entity updated with new field(s)
- [ ] Migration created in DatabaseMigrations.kt
- [ ] Migration registered in ALL_MIGRATIONS array
- [ ] AppDatabase version incremented
- [ ] MIGRATIONS.md updated with documentation
- [ ] Unit test added and passing
- [ ] Manual testing completed on physical device
- [ ] Rollback plan documented

### Code Review Requirements

All migrations require:

1. **Database Owner approval** (mandatory)
2. **One peer review** (recommended)
3. **CI passing** (all tests green)

### Review Focus Areas

Reviewers should verify:

✅ **Correctness:**
- SQL syntax is valid
- Column types match Room entity
- Default values are appropriate
- Indexes improve query performance

✅ **Safety:**
- No data loss scenarios
- Backwards compatible (can downgrade)
- Handles edge cases (empty tables, null values)
- No destructive operations without approval

✅ **Testing:**
- Unit test covers migration logic
- Manual testing documented
- Smoke tests pass

✅ **Documentation:**
- MIGRATIONS.md updated
- Migration reason clear
- Rollback strategy defined

---

## Deployment Procedure

### Development Deployment

1. **Merge to main** after approval
2. **CI runs automatically** - all tests must pass
3. **Install on test device:**
   ```bash
   ./gradlew :app:installDebug
   adb logcat | grep -i migration
   ```
4. **Verify migration success** in logs

### Production Deployment (Google Play)

1. **Build release APK:**
   ```bash
   ./gradlew :app:assembleRelease
   ```

2. **Test on physical device** (not emulator)

3. **Upload to Google Play Console:**
   - Internal testing track first
   - Monitor crash reports for 24-48 hours
   - Promote to production if stable

4. **Monitor migrations:**
   - Check Firebase Crashlytics for migration errors
   - Review user reports for data issues
   - Watch for migration timeout errors

### Migration Monitoring

Post-deployment, monitor:

- **Crash rate** (should not increase)
- **ANRs** (Application Not Responding) - migrations should be fast
- **User reports** of missing data
- **Database version distribution** (via analytics)

**Alert threshold:** If crash rate increases >2% in first 24 hours, consider rollback.

---

## Rollback Strategy

### When to Rollback

Trigger rollback if:
- Migration failure rate >5%
- Data loss reported by users
- ANRs caused by slow migrations
- Critical bugs introduced by schema change

### Rollback Procedure

**Option 1: Downgrade APK (Preferred)**

1. **Publish previous version** to Google Play
2. **Notify users** to update (downgrade)
3. **Data preserved** if migration was safe

**Option 2: Emergency Hotfix**

1. **Revert migration commit** on main branch
2. **Decrement database version** in AppDatabase.kt
3. **Remove migration** from ALL_MIGRATIONS array
4. **Add compensating migration** to clean up changes:

```kotlin
val MIGRATION_20_19_ROLLBACK = object : Migration(20, 19) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Remove column added in v19→v20 (safe to drop)
        // Note: SQLite doesn't support ALTER TABLE DROP COLUMN directly
        // Options:
        // 1. Leave column (safest, just ignore it)
        // 2. Recreate table without column (risky, data copy)

        // Safest: Do nothing, app will ignore extra column
        Timber.w("Rollback 20→19: Leaving executionTimeoutMs column (ignored by v19 schema)")
    }
}
```

5. **Test thoroughly** before deployment
6. **Deploy hotfix** via Google Play expedited review

### Data Recovery

If data loss occurs:

1. **Check user's local database** (may be intact)
2. **Restore from backup** (if implemented)
3. **Manual data entry** as last resort
4. **Compensation** for affected users (if applicable)

---

## Version Control

### Branch Strategy

- **Main branch:** Always contains latest migration
- **Feature branches:** Include migration if schema changes
- **Release tags:** Tag after successful production deployment

### Commit Message Format

```
feat: Implement strategy execution timeout (v19→v20)

Migration v19→v20:
- Add executionTimeoutMs column to strategies table
- Default value: 30000ms (30 seconds)
- Add index for query performance

Testing:
- Unit test: DatabaseMigrationTest.migrate19To20() ✅
- Manual test: Verified on Pixel 7 (Android 14) ✅
- Smoke test: BacktestSmokeTest passed ✅

Rollback: Safe - column has default, can ignore on downgrade

Reviewed-by: @lead-developer
Co-Authored-By: Claude <noreply@anthropic.com>
```

### Git Tags

After production deployment:

```bash
git tag -a db-v20 -m "Database schema v20 deployed to production"
git push origin db-v20
```

---

## Emergency Procedures

### P0 Emergency Migration

If critical security or data corruption issue:

1. **Notify database owner** immediately
2. **Create emergency branch** from main
3. **Implement hotfix migration:**
   - Fix the issue (e.g., encrypt exposed data)
   - Add migration to correct data in-place
4. **Expedited review:**
   - Database owner + 1 senior developer
   - Can skip manual testing if risk is high
5. **Deploy via Google Play emergency release**
6. **Monitor closely** for first 2 hours

### Migration Failure Recovery

If migration crashes on user devices:

1. **Gather crash reports** from Firebase Crashlytics
2. **Identify root cause:**
   - SQL syntax error?
   - Missing default value?
   - Timeout on large tables?
3. **Options:**
   - **Fix forward:** Deploy hotfix with corrected migration
   - **Rollback:** Revert to previous version
4. **Communicate with users** via in-app message or email

### Database Corruption

If database becomes corrupted:

1. **DO NOT use fallbackToDestructiveMigration()** (data loss!)
2. **Implement recovery migration:**

```kotlin
val MIGRATION_RECOVERY = object : Migration(20, 20) {
    override fun migrate(database: SupportSQLiteDatabase) {
        try {
            // Attempt to repair corruption
            database.execSQL("PRAGMA integrity_check")
            Timber.i("Database integrity OK")
        } catch (e: Exception) {
            Timber.e("Database corruption detected: ${e.message}")
            // Options:
            // 1. Export user data to JSON
            // 2. Recreate tables
            // 3. Re-import data
        }
    }
}
```

---

## Appendix

### Useful SQL Commands

```sql
-- Check table schema
PRAGMA table_info(strategies);

-- List all indexes
SELECT name FROM sqlite_master WHERE type='index';

-- Check database integrity
PRAGMA integrity_check;

-- Get database version
PRAGMA user_version;

-- Vacuum database (reclaim space)
VACUUM;
```

### Room Migration Resources

- [Official Room Migration Guide](https://developer.android.com/training/data-storage/room/migrating-db-versions)
- [Testing Room Migrations](https://developer.android.com/training/data-storage/room/migrating-db-versions#test)
- [Room Migration Best Practices](https://medium.com/androiddevelopers/understanding-migrations-with-room-f01e04b07929)

### Internal Links

- **Migration History:** [MIGRATIONS.md](./MIGRATIONS.md)
- **Build Instructions:** [BUILD_RUN.md](./BUILD_RUN.md)
- **Contributing Guide:** [CONTRIBUTING.md](./CONTRIBUTING.md) (if exists)

---

## Changelog

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 1.0 | 2024-11-18 | Initial policy creation (TODO 8) | Development Team + Claude Code |

---

## Acknowledgments

This policy is based on industry best practices and lessons learned from:
- Database migrations v1 → v19
- Production incidents and resolutions
- Room framework best practices
- Android community guidelines

---

**Next Review:** Q1 2025
**Policy Owner:** Lead Android Developer
**Status:** ✅ Active and Enforced
