"""Backend models for learning roadmap and gamification."""
from pydantic import BaseModel
from typing import List, Optional
from datetime import datetime

# ============================================
# Roadmap Response Models
# ============================================

class LessonDto(BaseModel):
    id: str
    title: str
    description: str
    difficulty: str  # "beginner", "intermediate", "advanced"
    estimatedMinutes: int
    cardCount: int
    state: str  # "completed", "current", "locked", "future"
    prerequisiteId: Optional[str] = None
    previewTopics: List[str]
    order: int
    score: Optional[int] = None
    stars: Optional[int] = None
    completedAt: Optional[int] = None


class AchievementDto(BaseModel):
    id: str
    title: str
    description: str
    icon: str
    unlockedAt: Optional[int] = None
    category: str  # "FIRST_STEPS", "COMPLETION", "ACCURACY", "SPEED", "STREAK", "MASTERY"


class UserProgressDto(BaseModel):
    completedLessons: List[str]
    currentStreak: int
    totalPoints: int
    achievements: List[AchievementDto]
    dailyGoalProgress: float  # 0.0 to 1.0
    lastActiveDate: str


class RoadmapResponse(BaseModel):
    lessons: List[LessonDto]
    currentLessonId: str
    userProgress: UserProgressDto


# ============================================
# Lesson Completion Models
# ============================================

class CompleteLessonRequest(BaseModel):
    lessonId: str
    score: int
    timeSpentMinutes: int
    correctAnswers: int
    totalQuestions: int


class CompleteLessonResponse(BaseModel):
    success: bool
    pointsEarned: int
    stars: int
    newAchievements: List[AchievementDto]
    unlockedNextLesson: bool
    nextLessonId: Optional[str] = None


# ============================================
# Stats Models
# ============================================

class DailyGoalDto(BaseModel):
    type: str  # "COMPLETE_LESSONS", "ANSWER_QUESTIONS", "SPEND_MINUTES", "MAINTAIN_STREAK"
    target: int
    current: int
    completed: bool


class UserStatsDto(BaseModel):
    totalPoints: int
    currentStreak: int
    longestStreak: int
    achievements: List[AchievementDto]
    dailyGoals: List[DailyGoalDto]
    lessonsCompleted: int
    totalLessons: int
    averageScore: float
    totalTimeMinutes: int
