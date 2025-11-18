package com.cryptotrader.data.local.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import com.cryptotrader.data.local.entity.*
import java.time.LocalDateTime

/**
 * Data Access Objects for Learning Database
 */

@Dao
interface LearningBookDao {

    @Query("SELECT * FROM learning_books ORDER BY upload_date DESC")
    fun getAllBooks(): Flow<List<LearningBookEntity>>

    @Query("SELECT * FROM learning_books WHERE is_favorite = 1 ORDER BY last_opened_date DESC")
    fun getFavoriteBooks(): Flow<List<LearningBookEntity>>

    @Query("SELECT * FROM learning_books WHERE category = :category ORDER BY upload_date DESC")
    fun getBooksByCategory(category: String): Flow<List<LearningBookEntity>>

    @Query("SELECT * FROM learning_books WHERE book_id = :bookId")
    suspend fun getBookById(bookId: String): LearningBookEntity?

    @Query("SELECT * FROM learning_books WHERE title LIKE '%' || :query || '%' OR author LIKE '%' || :query || '%' ORDER BY upload_date DESC")
    fun searchBooks(query: String): Flow<List<LearningBookEntity>>

    @Query("SELECT * FROM learning_books WHERE analysis_status = :status")
    fun getBooksByAnalysisStatus(status: String): Flow<List<LearningBookEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBook(book: LearningBookEntity)

    @Update
    suspend fun updateBook(book: LearningBookEntity)

    @Delete
    suspend fun deleteBook(book: LearningBookEntity)

    @Query("UPDATE learning_books SET reading_progress = :progress WHERE book_id = :bookId")
    suspend fun updateReadingProgress(bookId: String, progress: Float)

    @Query("UPDATE learning_books SET last_opened_date = :date WHERE book_id = :bookId")
    suspend fun updateLastOpenedDate(bookId: String, date: LocalDateTime)

    @Query("UPDATE learning_books SET is_favorite = :isFavorite WHERE book_id = :bookId")
    suspend fun updateFavoriteStatus(bookId: String, isFavorite: Boolean)

    @Query("SELECT COUNT(*) FROM learning_books")
    suspend fun getTotalBooksCount(): Int

    @Query("SELECT COUNT(*) FROM learning_books WHERE reading_progress = 1.0")
    suspend fun getCompletedBooksCount(): Int

    @Query("SELECT SUM(file_size) FROM learning_books")
    suspend fun getTotalStorageUsed(): Long?
}

@Dao
interface BookAnalysisDao {

    @Query("SELECT * FROM book_analyses WHERE book_id = :bookId")
    suspend fun getAnalysisByBookId(bookId: String): BookAnalysisEntity?

    @Query("SELECT * FROM book_analyses ORDER BY created_date DESC")
    fun getAllAnalyses(): Flow<List<BookAnalysisEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnalysis(analysis: BookAnalysisEntity)

    @Update
    suspend fun updateAnalysis(analysis: BookAnalysisEntity)

    @Delete
    suspend fun deleteAnalysis(analysis: BookAnalysisEntity)

    @Query("DELETE FROM book_analyses WHERE book_id = :bookId")
    suspend fun deleteAnalysisByBookId(bookId: String)
}

@Dao
interface ChapterSummaryDao {

    @Query("SELECT * FROM chapter_summaries WHERE book_id = :bookId ORDER BY chapter_number ASC")
    fun getChaptersByBookId(bookId: String): Flow<List<ChapterSummaryEntity>>

    @Query("SELECT * FROM chapter_summaries WHERE chapter_id = :chapterId")
    suspend fun getChapterById(chapterId: String): ChapterSummaryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapter(chapter: ChapterSummaryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChapters(chapters: List<ChapterSummaryEntity>)

    @Update
    suspend fun updateChapter(chapter: ChapterSummaryEntity)

    @Delete
    suspend fun deleteChapter(chapter: ChapterSummaryEntity)

    @Query("DELETE FROM chapter_summaries WHERE book_id = :bookId")
    suspend fun deleteChaptersByBookId(bookId: String)
}

@Dao
interface StudyPlanDao {

    @Query("SELECT * FROM study_plans WHERE user_id = :userId ORDER BY created_date DESC")
    fun getUserStudyPlans(userId: String): Flow<List<StudyPlanEntity>>

    @Query("SELECT * FROM study_plans WHERE user_id = :userId AND status = :status")
    fun getUserStudyPlansByStatus(userId: String, status: String): Flow<List<StudyPlanEntity>>

    @Query("SELECT * FROM study_plans WHERE plan_id = :planId")
    suspend fun getStudyPlanById(planId: String): StudyPlanEntity?

    @Query("SELECT * FROM study_plans WHERE book_id = :bookId AND user_id = :userId")
    suspend fun getStudyPlanByBookAndUser(bookId: String, userId: String): StudyPlanEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStudyPlan(plan: StudyPlanEntity)

    @Update
    suspend fun updateStudyPlan(plan: StudyPlanEntity)

    @Delete
    suspend fun deleteStudyPlan(plan: StudyPlanEntity)

    @Query("UPDATE study_plans SET status = :status WHERE plan_id = :planId")
    suspend fun updatePlanStatus(planId: String, status: String)
}

@Dao
interface WeeklyScheduleDao {

    @Query("SELECT * FROM weekly_schedules WHERE study_plan_id = :planId ORDER BY week_number ASC")
    fun getSchedulesByPlanId(planId: String): Flow<List<WeeklyScheduleEntity>>

    @Query("SELECT * FROM weekly_schedules WHERE schedule_id = :scheduleId")
    suspend fun getScheduleById(scheduleId: String): WeeklyScheduleEntity?

    @Query("SELECT * FROM weekly_schedules WHERE study_plan_id = :planId AND :date BETWEEN start_date AND end_date")
    suspend fun getCurrentWeekSchedule(planId: String, date: LocalDateTime): WeeklyScheduleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedule(schedule: WeeklyScheduleEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedules(schedules: List<WeeklyScheduleEntity>)

    @Update
    suspend fun updateSchedule(schedule: WeeklyScheduleEntity)

    @Query("UPDATE weekly_schedules SET completion_rate = :rate, actual_hours = :hours WHERE schedule_id = :scheduleId")
    suspend fun updateScheduleProgress(scheduleId: String, rate: Float, hours: Float)
}

@Dao
interface BookEvaluationDao {

    @Query("SELECT * FROM book_evaluations WHERE book_id = :bookId")
    suspend fun getEvaluationByBookId(bookId: String): BookEvaluationEntity?

    @Query("SELECT * FROM book_evaluations WHERE overall_rating >= :minRating ORDER BY overall_rating DESC")
    fun getHighRatedEvaluations(minRating: Float): Flow<List<BookEvaluationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvaluation(evaluation: BookEvaluationEntity)

    @Update
    suspend fun updateEvaluation(evaluation: BookEvaluationEntity)

    @Delete
    suspend fun deleteEvaluation(evaluation: BookEvaluationEntity)
}

@Dao
interface StudyProgressDao {

    @Query("SELECT * FROM study_progress WHERE user_id = :userId AND book_id = :bookId")
    suspend fun getProgressByUserAndBook(userId: String, bookId: String): StudyProgressEntity?

    @Query("SELECT * FROM study_progress WHERE user_id = :userId ORDER BY last_study_date DESC")
    fun getUserProgress(userId: String): Flow<List<StudyProgressEntity>>

    @Query("SELECT * FROM study_progress WHERE user_id = :userId AND percent_complete < 1.0 ORDER BY last_study_date DESC")
    fun getInProgressBooks(userId: String): Flow<List<StudyProgressEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProgress(progress: StudyProgressEntity)

    @Update
    suspend fun updateProgress(progress: StudyProgressEntity)

    @Query("UPDATE study_progress SET current_chapter = :chapter, current_page = :page, percent_complete = :percent WHERE progress_id = :progressId")
    suspend fun updateReadingPosition(progressId: String, chapter: Int, page: Int?, percent: Float)

    @Query("UPDATE study_progress SET total_time_spent_minutes = total_time_spent_minutes + :minutes WHERE progress_id = :progressId")
    suspend fun addStudyTime(progressId: String, minutes: Long)

    @Query("UPDATE study_progress SET streak_days = :streak, last_study_date = :date WHERE progress_id = :progressId")
    suspend fun updateStreak(progressId: String, streak: Int, date: LocalDateTime)
}

@Dao
interface QuizScoreDao {

    @Query("SELECT * FROM quiz_scores WHERE progress_id = :progressId ORDER BY date DESC")
    fun getScoresByProgressId(progressId: String): Flow<List<QuizScoreEntity>>

    @Query("SELECT AVG(score) FROM quiz_scores WHERE progress_id = :progressId")
    suspend fun getAverageScore(progressId: String): Float?

    @Query("SELECT * FROM quiz_scores WHERE progress_id = :progressId AND date >= :since ORDER BY date DESC")
    fun getRecentScores(progressId: String, since: LocalDateTime): Flow<List<QuizScoreEntity>>

    @Insert
    suspend fun insertScore(score: QuizScoreEntity)

    @Delete
    suspend fun deleteScore(score: QuizScoreEntity)
}

@Dao
interface StudyNoteDao {

    @Query("SELECT * FROM study_notes WHERE book_id = :bookId ORDER BY created_date DESC")
    fun getNotesByBookId(bookId: String): Flow<List<StudyNoteEntity>>

    @Query("SELECT * FROM study_notes WHERE book_id = :bookId AND chapter_id = :chapterId ORDER BY page_number ASC")
    fun getNotesByChapter(bookId: String, chapterId: String): Flow<List<StudyNoteEntity>>

    @Query("SELECT * FROM study_notes WHERE note_id = :noteId")
    suspend fun getNoteById(noteId: String): StudyNoteEntity?

    @Query("SELECT * FROM study_notes WHERE content LIKE '%' || :query || '%' OR highlighted_text LIKE '%' || :query || '%' ORDER BY created_date DESC")
    fun searchNotes(query: String): Flow<List<StudyNoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: StudyNoteEntity)

    @Update
    suspend fun updateNote(note: StudyNoteEntity)

    @Delete
    suspend fun deleteNote(note: StudyNoteEntity)
}

@Dao
interface BookmarkDao {

    @Query("SELECT * FROM bookmarks WHERE book_id = :bookId ORDER BY page_number ASC")
    fun getBookmarksByBookId(bookId: String): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity)

    @Delete
    suspend fun deleteBookmark(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE book_id = :bookId AND page_number = :pageNumber")
    suspend fun deleteBookmarkByPage(bookId: String, pageNumber: Int)
}

@Dao
interface KnowledgeTopicDao {

    @Query("SELECT * FROM knowledge_topics ORDER BY importance DESC, name ASC")
    fun getAllTopics(): Flow<List<KnowledgeTopicEntity>>

    @Query("SELECT * FROM knowledge_topics WHERE is_studied = 1 ORDER BY mastery_level DESC")
    fun getStudiedTopics(): Flow<List<KnowledgeTopicEntity>>

    @Query("SELECT * FROM knowledge_topics WHERE is_studied = 0 ORDER BY importance DESC")
    fun getUnstudiedTopics(): Flow<List<KnowledgeTopicEntity>>

    @Query("SELECT * FROM knowledge_topics WHERE category = :category ORDER BY name ASC")
    fun getTopicsByCategory(category: String): Flow<List<KnowledgeTopicEntity>>

    @Query("SELECT * FROM knowledge_topics WHERE next_review_date <= :date ORDER BY next_review_date ASC")
    fun getTopicsForReview(date: LocalDateTime): Flow<List<KnowledgeTopicEntity>>

    @Query("SELECT * FROM knowledge_topics WHERE topic_id = :topicId")
    suspend fun getTopicById(topicId: String): KnowledgeTopicEntity?

    @Query("SELECT * FROM knowledge_topics WHERE name LIKE '%' || :query || '%' OR description LIKE '%' || :query || '%' ORDER BY importance DESC")
    fun searchTopics(query: String): Flow<List<KnowledgeTopicEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTopic(topic: KnowledgeTopicEntity)

    @Update
    suspend fun updateTopic(topic: KnowledgeTopicEntity)

    @Delete
    suspend fun deleteTopic(topic: KnowledgeTopicEntity)

    @Query("UPDATE knowledge_topics SET mastery_level = :level, last_review_date = :date WHERE topic_id = :topicId")
    suspend fun updateMasteryLevel(topicId: String, level: String, date: LocalDateTime)

    @Query("UPDATE knowledge_topics SET total_study_time_minutes = total_study_time_minutes + :minutes WHERE topic_id = :topicId")
    suspend fun addStudyTime(topicId: String, minutes: Long)
}

@Dao
interface LearningSessionDao {

    @Query("SELECT * FROM learning_sessions WHERE user_id = :userId ORDER BY start_time DESC")
    fun getUserSessions(userId: String): Flow<List<LearningSessionEntity>>

    @Query("SELECT * FROM learning_sessions WHERE user_id = :userId AND start_time >= :since ORDER BY start_time DESC")
    fun getRecentSessions(userId: String, since: LocalDateTime): Flow<List<LearningSessionEntity>>

    @Query("SELECT * FROM learning_sessions WHERE user_id = :userId AND book_id = :bookId ORDER BY start_time DESC")
    fun getBookSessions(userId: String, bookId: String): Flow<List<LearningSessionEntity>>

    @Query("SELECT * FROM learning_sessions WHERE session_id = :sessionId")
    suspend fun getSessionById(sessionId: String): LearningSessionEntity?

    @Query("SELECT SUM(duration_minutes) FROM learning_sessions WHERE user_id = :userId AND start_time >= :since")
    suspend fun getTotalStudyTime(userId: String, since: LocalDateTime): Long?

    @Query("SELECT AVG(productivity_score) FROM learning_sessions WHERE user_id = :userId AND start_time >= :since AND productivity_score IS NOT NULL")
    suspend fun getAverageProductivity(userId: String, since: LocalDateTime): Float?

    @Insert
    suspend fun insertSession(session: LearningSessionEntity)

    @Update
    suspend fun updateSession(session: LearningSessionEntity)

    @Delete
    suspend fun deleteSession(session: LearningSessionEntity)
}

@Dao
interface StudyMilestoneDao {

    @Query("SELECT * FROM study_milestones WHERE plan_id = :planId ORDER BY target_date ASC")
    fun getMilestonesByPlanId(planId: String): Flow<List<StudyMilestoneEntity>>

    @Query("SELECT * FROM study_milestones WHERE plan_id = :planId AND is_completed = 0 AND target_date <= :date")
    fun getOverdueMilestones(planId: String, date: LocalDateTime): Flow<List<StudyMilestoneEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMilestone(milestone: StudyMilestoneEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMilestones(milestones: List<StudyMilestoneEntity>)

    @Update
    suspend fun updateMilestone(milestone: StudyMilestoneEntity)

    @Query("UPDATE study_milestones SET is_completed = 1, completed_date = :date WHERE milestone_id = :milestoneId")
    suspend fun completeMilestone(milestoneId: String, date: LocalDateTime)
}

@Dao
interface BookTopicDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookTopicRelation(crossRef: BookTopicCrossRef)

    @Delete
    suspend fun deleteBookTopicRelation(crossRef: BookTopicCrossRef)

    @Query("SELECT * FROM book_topic_cross_ref WHERE book_id = :bookId")
    fun getTopicsByBookId(bookId: String): Flow<List<BookTopicCrossRef>>

    @Query("SELECT * FROM book_topic_cross_ref WHERE topic_id = :topicId")
    fun getBooksByTopicId(topicId: String): Flow<List<BookTopicCrossRef>>

    @Query("DELETE FROM book_topic_cross_ref WHERE book_id = :bookId")
    suspend fun deleteAllTopicsForBook(bookId: String)
}