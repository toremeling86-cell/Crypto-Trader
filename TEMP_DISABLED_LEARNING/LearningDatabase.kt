package com.cryptotrader.data.local

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.cryptotrader.data.local.dao.*
import com.cryptotrader.data.local.entity.*
import java.time.LocalDateTime
import java.time.Duration
import java.util.concurrent.Executors

/**
 * Room Database for Learning Section
 * Manages all learning-related data with proper version control and migrations
 */
@Database(
    entities = [
        LearningBookEntity::class,
        BookAnalysisEntity::class,
        ChapterSummaryEntity::class,
        StudyPlanEntity::class,
        WeeklyScheduleEntity::class,
        BookEvaluationEntity::class,
        StudyProgressEntity::class,
        QuizScoreEntity::class,
        StudyNoteEntity::class,
        BookmarkEntity::class,
        KnowledgeTopicEntity::class,
        LearningSessionEntity::class,
        StudyMilestoneEntity::class,
        BookTopicCrossRef::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(LearningTypeConverters::class)
abstract class LearningDatabase : RoomDatabase() {

    // DAO References
    abstract fun learningBookDao(): LearningBookDao
    abstract fun bookAnalysisDao(): BookAnalysisDao
    abstract fun chapterSummaryDao(): ChapterSummaryDao
    abstract fun studyPlanDao(): StudyPlanDao
    abstract fun weeklyScheduleDao(): WeeklyScheduleDao
    abstract fun bookEvaluationDao(): BookEvaluationDao
    abstract fun studyProgressDao(): StudyProgressDao
    abstract fun quizScoreDao(): QuizScoreDao
    abstract fun studyNoteDao(): StudyNoteDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun knowledgeTopicDao(): KnowledgeTopicDao
    abstract fun learningSessionDao(): LearningSessionDao
    abstract fun studyMilestoneDao(): StudyMilestoneDao
    abstract fun bookTopicDao(): BookTopicDao

    companion object {
        private const val DATABASE_NAME = "learning_database"

        @Volatile
        private var INSTANCE: LearningDatabase? = null

        fun getInstance(context: Context): LearningDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = buildDatabase(context)
                INSTANCE = instance
                instance
            }
        }

        private fun buildDatabase(context: Context): LearningDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                LearningDatabase::class.java,
                DATABASE_NAME
            )
            .addCallback(DatabaseCallback())
            .addMigrations(*getAllMigrations())
            .setQueryCallback(
                { sqlQuery, bindArgs ->
                    // Log queries in debug mode for performance monitoring
                    println("SQL Query: $sqlQuery SQL Args: $bindArgs")
                },
                Executors.newSingleThreadExecutor()
            )
            .build()
        }

        private fun getAllMigrations(): Array<Migration> {
            return arrayOf(
                // Future migrations will be added here
                // MIGRATION_1_2,
                // MIGRATION_2_3,
            )
        }

        // Example migration template for future use
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Example: Add a new column to existing table
                // database.execSQL("ALTER TABLE learning_books ADD COLUMN publisher TEXT")
            }
        }

        // Database callback for initial setup or seeding
        private class DatabaseCallback : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                // Perform any initial database setup here
                // For example, create triggers or seed initial data
                createDatabaseTriggers(db)
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                super.onOpen(db)
                // Enable foreign key constraints
                db.execSQL("PRAGMA foreign_keys=ON")
            }
        }

        private fun createDatabaseTriggers(db: SupportSQLiteDatabase) {
            // Trigger to update 'updated_at' timestamp on book update
            db.execSQL("""
                CREATE TRIGGER update_book_timestamp
                AFTER UPDATE ON learning_books
                BEGIN
                    UPDATE learning_books
                    SET updated_at = datetime('now')
                    WHERE book_id = NEW.book_id;
                END
            """.trimIndent())

            // Trigger to update book analysis status when analysis is inserted
            db.execSQL("""
                CREATE TRIGGER update_book_analysis_status_on_insert
                AFTER INSERT ON book_analyses
                BEGIN
                    UPDATE learning_books
                    SET analysis_status = 'ANALYZED'
                    WHERE book_id = NEW.book_id;
                END
            """.trimIndent())

            // Trigger to update book analysis status when analysis is deleted
            db.execSQL("""
                CREATE TRIGGER update_book_analysis_status_on_delete
                AFTER DELETE ON book_analyses
                BEGIN
                    UPDATE learning_books
                    SET analysis_status = 'NOT_ANALYZED'
                    WHERE book_id = OLD.book_id;
                END
            """.trimIndent())

            // Trigger to calculate average session duration on new session insert
            db.execSQL("""
                CREATE TRIGGER update_average_session_duration
                AFTER INSERT ON learning_sessions
                WHEN NEW.book_id IS NOT NULL
                BEGIN
                    UPDATE study_progress
                    SET average_session_duration_minutes = (
                        SELECT AVG(duration_minutes)
                        FROM learning_sessions
                        WHERE book_id = NEW.book_id AND user_id = NEW.user_id
                    )
                    WHERE book_id = NEW.book_id AND user_id = NEW.user_id;
                END
            """.trimIndent())
        }
    }
}

/**
 * Type Converters for complex data types in Learning Database
 */
class LearningTypeConverters {

    @TypeConverter
    fun fromLocalDateTime(value: LocalDateTime?): Long? {
        return value?.toEpochSecond(java.time.ZoneOffset.UTC)
    }

    @TypeConverter
    fun toLocalDateTime(value: Long?): LocalDateTime? {
        return value?.let {
            LocalDateTime.ofEpochSecond(it, 0, java.time.ZoneOffset.UTC)
        }
    }

    @TypeConverter
    fun fromDuration(value: Duration?): Long? {
        return value?.toMinutes()
    }

    @TypeConverter
    fun toDuration(value: Long?): Duration? {
        return value?.let { Duration.ofMinutes(it) }
    }

    // JSON serialization for String lists
    @TypeConverter
    fun fromStringList(value: List<String>?): String? {
        return value?.joinToString(separator = "||") ?: ""
    }

    @TypeConverter
    fun toStringList(value: String?): List<String>? {
        return value?.takeIf { it.isNotEmpty() }?.split("||") ?: emptyList()
    }

    // JSON serialization for Int lists
    @TypeConverter
    fun fromIntList(value: List<Int>?): String? {
        return value?.joinToString(separator = ",") ?: ""
    }

    @TypeConverter
    fun toIntList(value: String?): List<Int>? {
        return value?.takeIf { it.isNotEmpty() }?.split(",")?.mapNotNull { it.toIntOrNull() } ?: emptyList()
    }
}