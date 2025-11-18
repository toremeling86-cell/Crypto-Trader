package com.cryptotrader.data.local.entity

import androidx.room.*
import java.time.LocalDateTime
import java.time.Duration

/**
 * Room Database Entities for Learning Section
 * These entities map to database tables with proper relationships
 */

// Books Table
@Entity(
    tableName = "learning_books",
    indices = [
        Index(value = ["title"]),
        Index(value = ["author"]),
        Index(value = ["category"]),
        Index(value = ["upload_date"]),
        Index(value = ["is_favorite"])
    ]
)
data class LearningBookEntity(
    @PrimaryKey
    @ColumnInfo(name = "book_id")
    val bookId: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "author")
    val author: String?,

    @ColumnInfo(name = "category")
    val category: String, // BookCategory enum as String

    @ColumnInfo(name = "file_path")
    val filePath: String,

    @ColumnInfo(name = "file_size")
    val fileSize: Long,

    @ColumnInfo(name = "page_count")
    val pageCount: Int?,

    @ColumnInfo(name = "upload_date")
    val uploadDate: LocalDateTime,

    @ColumnInfo(name = "last_opened_date")
    val lastOpenedDate: LocalDateTime?,

    @ColumnInfo(name = "cover_image_path")
    val coverImagePath: String?,

    @ColumnInfo(name = "isbn")
    val isbn: String?,

    @ColumnInfo(name = "publication_year")
    val publicationYear: Int?,

    @ColumnInfo(name = "language")
    val language: String,

    @ColumnInfo(name = "tags")
    val tags: String, // JSON array stored as String

    @ColumnInfo(name = "reading_progress")
    val readingProgress: Float,

    @ColumnInfo(name = "is_favorite")
    val isFavorite: Boolean,

    @ColumnInfo(name = "analysis_status")
    val analysisStatus: String, // AnalysisStatus enum as String

    @ColumnInfo(name = "created_at")
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: LocalDateTime = LocalDateTime.now()
)

// Book Analysis Table
@Entity(
    tableName = "book_analyses",
    foreignKeys = [
        ForeignKey(
            entity = LearningBookEntity::class,
            parentColumns = ["book_id"],
            childColumns = ["book_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["book_id"], unique = true),
        Index(value = ["created_date"])
    ]
)
data class BookAnalysisEntity(
    @PrimaryKey
    @ColumnInfo(name = "analysis_id")
    val analysisId: String,

    @ColumnInfo(name = "book_id")
    val bookId: String,

    @ColumnInfo(name = "summary")
    val summary: String,

    @ColumnInfo(name = "key_takeaways")
    val keyTakeaways: String, // JSON array

    @ColumnInfo(name = "target_audience")
    val targetAudience: String,

    @ColumnInfo(name = "difficulty_level")
    val difficultyLevel: String,

    @ColumnInfo(name = "estimated_reading_time_minutes")
    val estimatedReadingTimeMinutes: Long,

    @ColumnInfo(name = "prerequisites")
    val prerequisites: String, // JSON array

    @ColumnInfo(name = "created_date")
    val createdDate: LocalDateTime,

    @ColumnInfo(name = "ai_model")
    val aiModel: String,

    @ColumnInfo(name = "confidence")
    val confidence: Float
)

// Chapter Summaries Table
@Entity(
    tableName = "chapter_summaries",
    foreignKeys = [
        ForeignKey(
            entity = LearningBookEntity::class,
            parentColumns = ["book_id"],
            childColumns = ["book_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["book_id", "chapter_number"], unique = true),
        Index(value = ["book_id"])
    ]
)
data class ChapterSummaryEntity(
    @PrimaryKey
    @ColumnInfo(name = "chapter_id")
    val chapterId: String,

    @ColumnInfo(name = "book_id")
    val bookId: String,

    @ColumnInfo(name = "chapter_number")
    val chapterNumber: Int,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "summary")
    val summary: String,

    @ColumnInfo(name = "key_points")
    val keyPoints: String, // JSON array

    @ColumnInfo(name = "estimated_reading_time_minutes")
    val estimatedReadingTimeMinutes: Long,

    @ColumnInfo(name = "difficulty_level")
    val difficultyLevel: String,

    @ColumnInfo(name = "practical_exercises")
    val practicalExercises: String, // JSON array

    @ColumnInfo(name = "related_topics")
    val relatedTopics: String // JSON array
)

// Study Plans Table
@Entity(
    tableName = "study_plans",
    foreignKeys = [
        ForeignKey(
            entity = LearningBookEntity::class,
            parentColumns = ["book_id"],
            childColumns = ["book_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["book_id"]),
        Index(value = ["user_id"]),
        Index(value = ["status"]),
        Index(value = ["start_date"])
    ]
)
data class StudyPlanEntity(
    @PrimaryKey
    @ColumnInfo(name = "plan_id")
    val planId: String,

    @ColumnInfo(name = "book_id")
    val bookId: String,

    @ColumnInfo(name = "user_id")
    val userId: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "description")
    val description: String,

    @ColumnInfo(name = "total_duration_days")
    val totalDurationDays: Int,

    @ColumnInfo(name = "daily_commitment_minutes")
    val dailyCommitmentMinutes: Int,

    @ColumnInfo(name = "start_date")
    val startDate: LocalDateTime,

    @ColumnInfo(name = "target_end_date")
    val targetEndDate: LocalDateTime,

    @ColumnInfo(name = "learning_objectives")
    val learningObjectives: String, // JSON array

    @ColumnInfo(name = "adaptive_difficulty")
    val adaptiveDifficulty: Boolean,

    @ColumnInfo(name = "status")
    val status: String, // PlanStatus enum

    @ColumnInfo(name = "created_date")
    val createdDate: LocalDateTime,

    @ColumnInfo(name = "last_modified_date")
    val lastModifiedDate: LocalDateTime
)

// Weekly Schedules Table
@Entity(
    tableName = "weekly_schedules",
    foreignKeys = [
        ForeignKey(
            entity = StudyPlanEntity::class,
            parentColumns = ["plan_id"],
            childColumns = ["study_plan_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["study_plan_id", "week_number"], unique = true),
        Index(value = ["study_plan_id"]),
        Index(value = ["start_date"])
    ]
)
data class WeeklyScheduleEntity(
    @PrimaryKey
    @ColumnInfo(name = "schedule_id")
    val scheduleId: String,

    @ColumnInfo(name = "study_plan_id")
    val studyPlanId: String,

    @ColumnInfo(name = "week_number")
    val weekNumber: Int,

    @ColumnInfo(name = "start_date")
    val startDate: LocalDateTime,

    @ColumnInfo(name = "end_date")
    val endDate: LocalDateTime,

    @ColumnInfo(name = "chapter_assignments")
    val chapterAssignments: String, // JSON array

    @ColumnInfo(name = "topics")
    val topics: String, // JSON array

    @ColumnInfo(name = "goals")
    val goals: String, // JSON array

    @ColumnInfo(name = "estimated_hours")
    val estimatedHours: Float,

    @ColumnInfo(name = "actual_hours")
    val actualHours: Float?,

    @ColumnInfo(name = "completion_rate")
    val completionRate: Float,

    @ColumnInfo(name = "notes")
    val notes: String?
)

// Book Evaluations Table
@Entity(
    tableName = "book_evaluations",
    foreignKeys = [
        ForeignKey(
            entity = LearningBookEntity::class,
            parentColumns = ["book_id"],
            childColumns = ["book_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["book_id"], unique = true),
        Index(value = ["overall_rating"])
    ]
)
data class BookEvaluationEntity(
    @PrimaryKey
    @ColumnInfo(name = "evaluation_id")
    val evaluationId: String,

    @ColumnInfo(name = "book_id")
    val bookId: String,

    @ColumnInfo(name = "overall_rating")
    val overallRating: Float,

    @ColumnInfo(name = "content_quality")
    val contentQuality: String, // QualityRating enum

    @ColumnInfo(name = "relevance_to_trading")
    val relevanceToTrading: String,

    @ColumnInfo(name = "practical_value")
    val practicalValue: String,

    @ColumnInfo(name = "accuracy")
    val accuracy: String,

    @ColumnInfo(name = "clarity")
    val clarity: String,

    @ColumnInfo(name = "strengths")
    val strengths: String, // JSON array

    @ColumnInfo(name = "weaknesses")
    val weaknesses: String, // JSON array

    @ColumnInfo(name = "recommendations")
    val recommendations: String, // JSON array

    @ColumnInfo(name = "alternative_books")
    val alternativeBooks: String, // JSON array

    @ColumnInfo(name = "best_for_audience")
    val bestForAudience: String,

    @ColumnInfo(name = "evaluation_date")
    val evaluationDate: LocalDateTime,

    @ColumnInfo(name = "detailed_review")
    val detailedReview: String
)

// Study Progress Table
@Entity(
    tableName = "study_progress",
    foreignKeys = [
        ForeignKey(
            entity = LearningBookEntity::class,
            parentColumns = ["book_id"],
            childColumns = ["book_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["user_id", "book_id"], unique = true),
        Index(value = ["user_id"]),
        Index(value = ["book_id"]),
        Index(value = ["last_study_date"])
    ]
)
data class StudyProgressEntity(
    @PrimaryKey
    @ColumnInfo(name = "progress_id")
    val progressId: String,

    @ColumnInfo(name = "user_id")
    val userId: String,

    @ColumnInfo(name = "book_id")
    val bookId: String,

    @ColumnInfo(name = "current_chapter")
    val currentChapter: Int,

    @ColumnInfo(name = "current_page")
    val currentPage: Int?,

    @ColumnInfo(name = "total_pages_read")
    val totalPagesRead: Int,

    @ColumnInfo(name = "percent_complete")
    val percentComplete: Float,

    @ColumnInfo(name = "total_time_spent_minutes")
    val totalTimeSpentMinutes: Long,

    @ColumnInfo(name = "average_session_duration_minutes")
    val averageSessionDurationMinutes: Long,

    @ColumnInfo(name = "last_study_date")
    val lastStudyDate: LocalDateTime,

    @ColumnInfo(name = "streak_days")
    val streakDays: Int,

    @ColumnInfo(name = "comprehension_level")
    val comprehensionLevel: String // ComprehensionLevel enum
)

// Quiz Scores Table
@Entity(
    tableName = "quiz_scores",
    foreignKeys = [
        ForeignKey(
            entity = StudyProgressEntity::class,
            parentColumns = ["progress_id"],
            childColumns = ["progress_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["progress_id"]),
        Index(value = ["date"]),
        Index(value = ["score"])
    ]
)
data class QuizScoreEntity(
    @PrimaryKey
    @ColumnInfo(name = "quiz_id")
    val quizId: String,

    @ColumnInfo(name = "progress_id")
    val progressId: String,

    @ColumnInfo(name = "chapter_id")
    val chapterId: String?,

    @ColumnInfo(name = "score")
    val score: Float,

    @ColumnInfo(name = "total_questions")
    val totalQuestions: Int,

    @ColumnInfo(name = "correct_answers")
    val correctAnswers: Int,

    @ColumnInfo(name = "time_taken_seconds")
    val timeTakenSeconds: Long,

    @ColumnInfo(name = "date")
    val date: LocalDateTime,

    @ColumnInfo(name = "topics")
    val topics: String // JSON array
)

// Study Notes Table
@Entity(
    tableName = "study_notes",
    foreignKeys = [
        ForeignKey(
            entity = LearningBookEntity::class,
            parentColumns = ["book_id"],
            childColumns = ["book_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["book_id"]),
        Index(value = ["created_date"]),
        Index(value = ["chapter_id"])
    ]
)
data class StudyNoteEntity(
    @PrimaryKey
    @ColumnInfo(name = "note_id")
    val noteId: String,

    @ColumnInfo(name = "book_id")
    val bookId: String,

    @ColumnInfo(name = "chapter_id")
    val chapterId: String?,

    @ColumnInfo(name = "page_number")
    val pageNumber: Int?,

    @ColumnInfo(name = "content")
    val content: String,

    @ColumnInfo(name = "highlighted_text")
    val highlightedText: String?,

    @ColumnInfo(name = "color")
    val color: String?,

    @ColumnInfo(name = "created_date")
    val createdDate: LocalDateTime,

    @ColumnInfo(name = "last_modified_date")
    val lastModifiedDate: LocalDateTime,

    @ColumnInfo(name = "tags")
    val tags: String // JSON array
)

// Bookmarks Table
@Entity(
    tableName = "bookmarks",
    foreignKeys = [
        ForeignKey(
            entity = LearningBookEntity::class,
            parentColumns = ["book_id"],
            childColumns = ["book_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["book_id", "page_number"], unique = true),
        Index(value = ["book_id"])
    ]
)
data class BookmarkEntity(
    @PrimaryKey
    @ColumnInfo(name = "bookmark_id")
    val bookmarkId: String,

    @ColumnInfo(name = "book_id")
    val bookId: String,

    @ColumnInfo(name = "page_number")
    val pageNumber: Int,

    @ColumnInfo(name = "label")
    val label: String?,

    @ColumnInfo(name = "created_date")
    val createdDate: LocalDateTime
)

// Knowledge Topics Table
@Entity(
    tableName = "knowledge_topics",
    indices = [
        Index(value = ["name"], unique = true),
        Index(value = ["category"]),
        Index(value = ["mastery_level"]),
        Index(value = ["is_studied"]),
        Index(value = ["next_review_date"])
    ]
)
data class KnowledgeTopicEntity(
    @PrimaryKey
    @ColumnInfo(name = "topic_id")
    val topicId: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "category")
    val category: String, // TopicCategory enum

    @ColumnInfo(name = "description")
    val description: String,

    @ColumnInfo(name = "importance")
    val importance: String, // ImportanceLevel enum

    @ColumnInfo(name = "mastery_level")
    val masteryLevel: String, // MasteryLevel enum

    @ColumnInfo(name = "last_review_date")
    val lastReviewDate: LocalDateTime?,

    @ColumnInfo(name = "next_review_date")
    val nextReviewDate: LocalDateTime?,

    @ColumnInfo(name = "total_study_time_minutes")
    val totalStudyTimeMinutes: Long,

    @ColumnInfo(name = "related_books")
    val relatedBooks: String, // JSON array of book IDs

    @ColumnInfo(name = "related_topics")
    val relatedTopics: String, // JSON array of topic IDs

    @ColumnInfo(name = "prerequisites")
    val prerequisites: String, // JSON array of topic IDs

    @ColumnInfo(name = "resources")
    val resources: String, // JSON array

    @ColumnInfo(name = "notes")
    val notes: String?,

    @ColumnInfo(name = "is_studied")
    val isStudied: Boolean
)

// Learning Sessions Table
@Entity(
    tableName = "learning_sessions",
    indices = [
        Index(value = ["user_id"]),
        Index(value = ["book_id"]),
        Index(value = ["topic_id"]),
        Index(value = ["start_time"]),
        Index(value = ["session_type"])
    ]
)
data class LearningSessionEntity(
    @PrimaryKey
    @ColumnInfo(name = "session_id")
    val sessionId: String,

    @ColumnInfo(name = "user_id")
    val userId: String,

    @ColumnInfo(name = "book_id")
    val bookId: String?,

    @ColumnInfo(name = "topic_id")
    val topicId: String?,

    @ColumnInfo(name = "start_time")
    val startTime: LocalDateTime,

    @ColumnInfo(name = "end_time")
    val endTime: LocalDateTime,

    @ColumnInfo(name = "duration_minutes")
    val durationMinutes: Long,

    @ColumnInfo(name = "pages_read")
    val pagesRead: Int?,

    @ColumnInfo(name = "chapters_completed")
    val chaptersCompleted: String, // JSON array of chapter numbers

    @ColumnInfo(name = "topics_reviewed")
    val topicsReviewed: String, // JSON array

    @ColumnInfo(name = "session_type")
    val sessionType: String, // SessionType enum

    @ColumnInfo(name = "productivity_score")
    val productivityScore: Float?,

    @ColumnInfo(name = "distractions")
    val distractions: Int?,

    @ColumnInfo(name = "notes")
    val notes: String?,

    @ColumnInfo(name = "mood")
    val mood: String? // StudyMood enum
)

// Study Milestones Table
@Entity(
    tableName = "study_milestones",
    foreignKeys = [
        ForeignKey(
            entity = StudyPlanEntity::class,
            parentColumns = ["plan_id"],
            childColumns = ["plan_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["plan_id"]),
        Index(value = ["target_date"]),
        Index(value = ["is_completed"])
    ]
)
data class StudyMilestoneEntity(
    @PrimaryKey
    @ColumnInfo(name = "milestone_id")
    val milestoneId: String,

    @ColumnInfo(name = "plan_id")
    val planId: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "description")
    val description: String,

    @ColumnInfo(name = "target_date")
    val targetDate: LocalDateTime,

    @ColumnInfo(name = "is_completed")
    val isCompleted: Boolean,

    @ColumnInfo(name = "completed_date")
    val completedDate: LocalDateTime?,

    @ColumnInfo(name = "reward")
    val reward: String?
)

// Cross-reference table for Book-Topic relationships
@Entity(
    tableName = "book_topic_cross_ref",
    primaryKeys = ["book_id", "topic_id"],
    foreignKeys = [
        ForeignKey(
            entity = LearningBookEntity::class,
            parentColumns = ["book_id"],
            childColumns = ["book_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = KnowledgeTopicEntity::class,
            parentColumns = ["topic_id"],
            childColumns = ["topic_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["book_id"]),
        Index(value = ["topic_id"])
    ]
)
data class BookTopicCrossRef(
    @ColumnInfo(name = "book_id")
    val bookId: String,

    @ColumnInfo(name = "topic_id")
    val topicId: String,

    @ColumnInfo(name = "relevance_score")
    val relevanceScore: Float // 0.0 to 1.0
)