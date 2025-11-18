package com.cryptotrader.domain.model

import java.time.LocalDateTime
import java.time.Duration

/**
 * Domain Models for Learning Section
 * These are clean domain models without database dependencies
 */

// Core Book Model
data class LearningBook(
    val id: String,
    val title: String,
    val author: String?,
    val category: BookCategory,
    val filePath: String,
    val fileSize: Long, // in bytes
    val pageCount: Int?,
    val uploadDate: LocalDateTime,
    val lastOpenedDate: LocalDateTime?,
    val coverImagePath: String?,
    val isbn: String?,
    val publicationYear: Int?,
    val language: String = "en",
    val tags: List<String> = emptyList(),
    val readingProgress: Float = 0f, // 0.0 to 1.0
    val isFavorite: Boolean = false,
    val analysisStatus: AnalysisStatus = AnalysisStatus.NOT_ANALYZED
)

// Complete AI Analysis of a Book
data class BookAnalysis(
    val id: String,
    val bookId: String,
    val summary: String,
    val keyTakeaways: List<String>,
    val targetAudience: String,
    val difficultyLevel: DifficultyLevel,
    val estimatedReadingTime: Duration,
    val prerequisites: List<String>,
    val chapterSummaries: List<ChapterSummary>,
    val createdDate: LocalDateTime,
    val aiModel: String = "claude-3",
    val confidence: Float // 0.0 to 1.0
)

// Individual Chapter Summary
data class ChapterSummary(
    val id: String,
    val bookId: String,
    val chapterNumber: Int,
    val title: String,
    val summary: String,
    val keyPoints: List<String>,
    val estimatedReadingTime: Duration,
    val difficultyLevel: DifficultyLevel,
    val practicalExercises: List<String>,
    val relatedTopics: List<String>
)

// Personalized Study Plan
data class StudyPlan(
    val id: String,
    val bookId: String,
    val userId: String,
    val title: String,
    val description: String,
    val totalDuration: Duration,
    val dailyCommitment: Duration,
    val startDate: LocalDateTime,
    val targetEndDate: LocalDateTime,
    val schedule: List<WeeklySchedule>,
    val learningObjectives: List<String>,
    val milestones: List<StudyMilestone>,
    val adaptiveDifficulty: Boolean = true,
    val status: PlanStatus = PlanStatus.ACTIVE,
    val createdDate: LocalDateTime,
    val lastModifiedDate: LocalDateTime
)

// Weekly Study Schedule
data class WeeklySchedule(
    val id: String,
    val studyPlanId: String,
    val weekNumber: Int,
    val startDate: LocalDateTime,
    val endDate: LocalDateTime,
    val chapters: List<ChapterAssignment>,
    val topics: List<String>,
    val goals: List<String>,
    val estimatedHours: Float,
    val actualHours: Float? = null,
    val completionRate: Float = 0f, // 0.0 to 1.0
    val notes: String?
)

// Chapter Assignment within a weekly schedule
data class ChapterAssignment(
    val chapterId: String,
    val chapterNumber: Int,
    val title: String,
    val targetCompletionDate: LocalDateTime,
    val isCompleted: Boolean = false,
    val completedDate: LocalDateTime? = null,
    val timeSpent: Duration? = null,
    val notes: String? = null
)

// AI's Quality Assessment of a Book
data class BookEvaluation(
    val id: String,
    val bookId: String,
    val overallRating: Float, // 0.0 to 5.0
    val contentQuality: QualityRating,
    val relevanceToTrading: QualityRating,
    val practicalValue: QualityRating,
    val accuracy: QualityRating,
    val clarity: QualityRating,
    val strengths: List<String>,
    val weaknesses: List<String>,
    val recommendations: List<String>,
    val alternativeBooks: List<BookRecommendation>,
    val bestForAudience: String,
    val evaluationDate: LocalDateTime,
    val detailedReview: String
)

// Book Recommendation
data class BookRecommendation(
    val title: String,
    val author: String,
    val reason: String,
    val similarityScore: Float // 0.0 to 1.0
)

// User's Learning Progress
data class StudyProgress(
    val id: String,
    val userId: String,
    val bookId: String,
    val currentChapter: Int,
    val currentPage: Int?,
    val totalPagesRead: Int,
    val percentComplete: Float, // 0.0 to 1.0
    val totalTimeSpent: Duration,
    val averageSessionDuration: Duration,
    val lastStudyDate: LocalDateTime,
    val streak: Int, // days
    val quizScores: List<QuizScore>,
    val comprehensionLevel: ComprehensionLevel,
    val notes: List<StudyNote>,
    val bookmarks: List<Bookmark>
)

// Quiz Score Record
data class QuizScore(
    val quizId: String,
    val chapterId: String?,
    val score: Float, // 0.0 to 100.0
    val totalQuestions: Int,
    val correctAnswers: Int,
    val timeTaken: Duration,
    val date: LocalDateTime,
    val topics: List<String>
)

// Study Note
data class StudyNote(
    val id: String,
    val bookId: String,
    val chapterId: String?,
    val pageNumber: Int?,
    val content: String,
    val highlightedText: String?,
    val color: String?, // Hex color for highlighting
    val createdDate: LocalDateTime,
    val lastModifiedDate: LocalDateTime,
    val tags: List<String>
)

// Bookmark
data class Bookmark(
    val id: String,
    val bookId: String,
    val pageNumber: Int,
    val label: String?,
    val createdDate: LocalDateTime
)

// Knowledge Topic
data class KnowledgeTopic(
    val id: String,
    val name: String,
    val category: TopicCategory,
    val description: String,
    val importance: ImportanceLevel,
    val masteryLevel: MasteryLevel,
    val lastReviewDate: LocalDateTime?,
    val nextReviewDate: LocalDateTime?,
    val totalStudyTime: Duration,
    val relatedBooks: List<String>, // Book IDs
    val relatedTopics: List<String>, // Topic IDs
    val prerequisites: List<String>, // Topic IDs
    val resources: List<LearningResource>,
    val notes: String?,
    val isStudied: Boolean = false
)

// Learning Resource
data class LearningResource(
    val id: String,
    val type: ResourceType,
    val title: String,
    val url: String?,
    val description: String?,
    val duration: Duration? // For videos/courses
)

// Individual Study Session
data class LearningSession(
    val id: String,
    val userId: String,
    val bookId: String?,
    val topicId: String?,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime,
    val duration: Duration,
    val pagesRead: Int?,
    val chaptersCompleted: List<Int>,
    val topicsReviewed: List<String>,
    val sessionType: SessionType,
    val productivityScore: Float?, // 0.0 to 1.0
    val distractions: Int?,
    val notes: String?,
    val mood: StudyMood?
)

// Study Milestone
data class StudyMilestone(
    val id: String,
    val title: String,
    val description: String,
    val targetDate: LocalDateTime,
    val isCompleted: Boolean = false,
    val completedDate: LocalDateTime? = null,
    val reward: String? // Gamification element
)

// Enums

enum class BookCategory {
    TECHNICAL_ANALYSIS,
    FUNDAMENTAL_ANALYSIS,
    TRADING_PSYCHOLOGY,
    RISK_MANAGEMENT,
    CRYPTO_BASICS,
    DEFI,
    MARKET_THEORY,
    ALGORITHMIC_TRADING,
    PORTFOLIO_MANAGEMENT,
    ECONOMICS,
    OTHER
}

enum class AnalysisStatus {
    NOT_ANALYZED,
    ANALYZING,
    ANALYZED,
    ANALYSIS_FAILED,
    ANALYSIS_OUTDATED
}

enum class DifficultyLevel {
    BEGINNER,
    INTERMEDIATE,
    ADVANCED,
    EXPERT
}

enum class PlanStatus {
    DRAFT,
    ACTIVE,
    PAUSED,
    COMPLETED,
    ABANDONED
}

enum class QualityRating {
    POOR,
    BELOW_AVERAGE,
    AVERAGE,
    GOOD,
    EXCELLENT
}

enum class ComprehensionLevel {
    SURFACE,
    BASIC,
    INTERMEDIATE,
    DEEP,
    MASTERY
}

enum class TopicCategory {
    FUNDAMENTALS,
    TECHNICAL,
    STRATEGIC,
    PSYCHOLOGICAL,
    MATHEMATICAL,
    REGULATORY,
    PRACTICAL
}

enum class ImportanceLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

enum class MasteryLevel {
    NOT_STARTED,
    BEGINNER,
    FAMILIAR,
    PROFICIENT,
    EXPERT,
    MASTER
}

enum class ResourceType {
    VIDEO,
    ARTICLE,
    COURSE,
    PODCAST,
    TOOL,
    WEBSITE,
    PAPER
}

enum class SessionType {
    READING,
    REVIEW,
    PRACTICE,
    QUIZ,
    NOTE_TAKING,
    RESEARCH
}

enum class StudyMood {
    FOCUSED,
    DISTRACTED,
    MOTIVATED,
    TIRED,
    CONFUSED,
    CONFIDENT
}

// Aggregate Models for UI

data class BookWithAnalysis(
    val book: LearningBook,
    val analysis: BookAnalysis?,
    val evaluation: BookEvaluation?,
    val progress: StudyProgress?
)

data class StudyPlanWithProgress(
    val studyPlan: StudyPlan,
    val currentWeek: WeeklySchedule?,
    val overallProgress: Float,
    val sessionsThisWeek: List<LearningSession>
)

data class KnowledgeBaseOverview(
    val totalTopics: Int,
    val studiedTopics: Int,
    val masteryDistribution: Map<MasteryLevel, Int>,
    val topicsToReview: List<KnowledgeTopic>,
    val recentlyStudied: List<KnowledgeTopic>
)

// Analytics & Progress Models

data class LearningVelocity(
    val pagesPerDay: Float,
    val topicsPerWeek: Float,
    val improvementRate: Float, // Percentage
    val trend: Trend
)

data class StudyTimeRecommendation(
    val optimalDailyMinutes: Int,
    val bestTimeOfDay: String,
    val recommendedBreakPattern: String,
    val focusTechniques: List<String>
)

data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val icon: String,
    val dateEarned: LocalDateTime,
    val rarity: AchievementRarity
)

data class UserLevel(
    val currentLevel: Int,
    val levelName: String,
    val currentXP: Int,
    val xpToNextLevel: Int,
    val rank: String,
    val badges: List<String>
)

data class LeaderboardEntry(
    val userId: String,
    val username: String,
    val rank: Int,
    val totalXP: Int,
    val booksCompleted: Int,
    val studyStreak: Int
)

data class LearningStatistics(
    val totalBooks: Int,
    val completedBooks: Int,
    val inProgressBooks: Int,
    val totalStudyHours: Float,
    val averageSessionDuration: Float,
    val currentStreak: Int,
    val longestStreak: Int,
    val averageComprehension: Float,
    val topicsStudied: Int,
    val notesCreated: Int,
    val quizzesTaken: Int,
    val averageQuizScore: Float
)

data class BookStatistics(
    val bookId: String,
    val totalReaders: Int,
    val averageCompletionTime: Float,
    val averageRating: Float,
    val completionRate: Float,
    val mostHighlightedPassages: List<String>,
    val commonNotes: List<String>
)

data class WeeklyStudyReport(
    val weekStartDate: LocalDateTime,
    val weekEndDate: LocalDateTime,
    val totalHoursStudied: Float,
    val booksRead: List<String>,
    val chaptersCompleted: Int,
    val topicsReviewed: List<String>,
    val quizzesTaken: Int,
    val averageProductivity: Float,
    val goalsAchieved: List<String>,
    val nextWeekSuggestions: List<String>
)

// Additional Enums

enum class Trend {
    IMPROVING,
    STABLE,
    DECLINING
}

enum class AchievementRarity {
    COMMON,
    UNCOMMON,
    RARE,
    EPIC,
    LEGENDARY
}

enum class Timeframe {
    DAILY,
    WEEKLY,
    MONTHLY,
    ALL_TIME
}