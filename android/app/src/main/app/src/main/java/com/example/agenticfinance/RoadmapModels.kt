package com.example.agenticfinance

import java.time.Instant

// ============================================
// Learning Roadmap Data Models
// ============================================

data class LearningRoadmap(
    val lessons: List<Lesson>,
    val currentLessonId: String,
    val userProgress: UserProgress
)

data class Lesson(
    val id: String,
    val title: String,
    val description: String,
    val difficulty: Difficulty,
    val estimatedMinutes: Int,
    val cards: List<LearningCardDto>,
    val state: LessonState,
    val prerequisiteId: String?,
    val previewTopics: List<String>,
    val order: Int
)

enum class Difficulty {
    BEGINNER,
    INTERMEDIATE,
    ADVANCED
}

sealed class LessonState {
    data class Completed(val score: Int, val stars: Int, val completedAt: Long) : LessonState()
    object Current : LessonState()
    data class Locked(val unlockRequirement: String) : LessonState()
    object Future : LessonState()
}

// ============================================
// User Progress & Gamification
// ============================================

data class UserProgress(
    val completedLessons: Set<String>,
    val currentStreak: Int,
    val totalPoints: Int,
    val achievements: List<Achievement>,
    val dailyGoalProgress: Float, // 0.0 to 1.0
    val lastActiveDate: String
)

data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val icon: String,
    val unlockedAt: Long?,
    val category: AchievementCategory
)

enum class AchievementCategory {
    FIRST_STEPS,
    COMPLETION,
    ACCURACY,
    SPEED,
    STREAK,
    MASTERY
}

data class DailyGoal(
    val type: GoalType,
    val target: Int,
    val current: Int,
    val completed: Boolean
)

enum class GoalType {
    COMPLETE_LESSONS,
    ANSWER_QUESTIONS,
    SPEND_MINUTES,
    MAINTAIN_STREAK
}

// ============================================
// Points & Scoring
// ============================================

object PointsSystem {
    const val CORRECT_FIRST_TRY = 10
    const val CORRECT_SECOND_TRY = 5
    const val COMPLETE_LESSON = 50
    const val DAILY_STREAK_BONUS = 20
    const val PERFECT_SCORE = 100
    
    fun calculateLessonScore(correctAnswers: Int, totalQuestions: Int, attempts: Int): Int {
        val baseScore = (correctAnswers.toFloat() / totalQuestions * 100).toInt()
        val attemptPenalty = (attempts - totalQuestions) * 2
        return maxOf(0, baseScore - attemptPenalty)
    }
    
    fun calculateStars(score: Int): Int {
        return when {
            score >= 90 -> 3
            score >= 70 -> 2
            score >= 50 -> 1
            else -> 0
        }
    }
}

// ============================================
// Roadmap Response DTOs
// ============================================

data class RoadmapResponseDto(
    val lessons: List<LessonDto>,
    val currentLessonId: String,
    val userProgress: UserProgressDto
)

data class LessonDto(
    val id: String,
    val title: String,
    val description: String,
    val difficulty: String,
    val estimatedMinutes: Int,
    val cardCount: Int,
    val state: String, // "completed", "current", "locked", "future"
    val prerequisiteId: String?,
    val previewTopics: List<String>,
    val order: Int,
    val score: Int?,
    val stars: Int?,
    val completedAt: Long?
)

data class UserProgressDto(
    val completedLessons: List<String>,
    val currentStreak: Int,
    val totalPoints: Int,
    val achievements: List<AchievementDto>,
    val dailyGoalProgress: Float,
    val lastActiveDate: String
)

data class AchievementDto(
    val id: String,
    val title: String,
    val description: String,
    val icon: String,
    val unlockedAt: Long?,
    val category: String
)

// ============================================
// Unlock Lesson Request/Response
// ============================================

data class UnlockLessonRequest(
    val lessonId: String
)

data class UnlockLessonResponse(
    val unlocked: Boolean,
    val newLesson: LessonDto?,
    val celebration: CelebrationData?
)

data class CelebrationData(
    val message: String,
    val pointsEarned: Int,
    val newAchievements: List<AchievementDto>,
    val streakBonus: Int?
)

// ============================================
// Stats Response
// ============================================

data class UserStatsDto(
    val totalPoints: Int,
    val currentStreak: Int,
    val longestStreak: Int,
    val achievements: List<AchievementDto>,
    val dailyGoals: List<DailyGoalDto>,
    val lessonsCompleted: Int,
    val totalLessons: Int,
    val averageScore: Float,
    val totalTimeMinutes: Int
)

data class DailyGoalDto(
    val type: String,
    val target: Int,
    val current: Int,
    val completed: Boolean
)
