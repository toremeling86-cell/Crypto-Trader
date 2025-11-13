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
     * Get all migrations
     */
    fun getAllMigrations(): Array<Migration> {
        return arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3
            // Add future migrations here
            // MIGRATION_3_4,
            // etc.
        )
    }
}
