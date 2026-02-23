"""Learning roadmap endpoints with gamification."""
from fastapi import APIRouter, Depends
from typing import Dict, List
from datetime import datetime, timedelta
from backend.models.roadmap import (
    RoadmapResponse, LessonDto, UserProgressDto, AchievementDto,
    CompleteLessonRequest, CompleteLessonResponse, UserStatsDto, DailyGoalDto
)
from backend.auth import get_current_user

router = APIRouter(prefix="/api/learning", tags=["Learning Roadmap"])

# In-memory storage for demo (replace with database in production)
_user_progress: Dict[str, dict] = {}
_lesson_completions: Dict[str, dict] = {}

# Mock lesson data
MOCK_LESSONS = [
    {
        "id": "lesson_1",
        "title": "Financial Basics",
        "description": "Learn the fundamentals of personal finance",
        "difficulty": "beginner",
        "estimatedMinutes": 10,
        "cardCount": 5,
        "prerequisiteId": None,
        "previewTopics": ["Income vs Expenses", "Budgeting 101", "Saving Basics"],
        "order": 1
    },
    {
        "id": "lesson_2",
        "title": "Budgeting Mastery",
        "description": "Create and stick to a budget",
        "difficulty": "beginner",
        "estimatedMinutes": 15,
        "cardCount": 7,
        "prerequisiteId": "lesson_1",
        "previewTopics": ["50/30/20 Rule", "Tracking Expenses", "Budget Apps"],
        "order": 2
    },
    {
        "id": "lesson_3",
        "title": "Emergency Fund",
        "description": "Build your financial safety net",
        "difficulty": "intermediate",
        "estimatedMinutes": 12,
        "cardCount": 6,
        "prerequisiteId": "lesson_2",
        "previewTopics": ["Why 6 months?", "Where to keep it", "How to build it"],
        "order": 3
    },
    {
        "id": "lesson_4",
        "title": "Debt Management",
        "description": "Strategies to eliminate debt",
        "difficulty": "intermediate",
        "estimatedMinutes": 20,
        "cardCount": 8,
        "prerequisiteId": "lesson_3",
        "previewTopics": ["Good vs Bad Debt", "Snowball Method", "Avalanche Method"],
        "order": 4
    },
    {
        "id": "lesson_5",
        "title": "Investment Basics",
        "description": "Start your investment journey",
        "difficulty": "advanced",
        "estimatedMinutes": 25,
        "cardCount": 10,
        "prerequisiteId": "lesson_4",
        "previewTopics": ["Stocks", "Bonds", "Mutual Funds", "Risk vs Return"],
        "order": 5
    }
]

ACHIEVEMENTS = [
    {"id": "first_steps", "title": "First Steps", "description": "Complete your first lesson", "icon": "🔥", "category": "FIRST_STEPS"},
    {"id": "bookworm", "title": "Bookworm", "description": "Complete 5 lessons", "icon": "📚", "category": "COMPLETION"},
    {"id": "perfect_score", "title": "Perfect Score", "description": "Get 100% on any lesson", "icon": "🎯", "category": "ACCURACY"},
    {"id": "speed_learner", "title": "Speed Learner", "description": "Complete lesson in under 5 minutes", "icon": "⚡", "category": "SPEED"},
    {"id": "week_warrior", "title": "Week Warrior", "description": "7-day streak", "icon": "🏆", "category": "STREAK"},
    {"id": "master", "title": "Master", "description": "Complete all lessons", "icon": "💎", "category": "MASTERY"}
]


def get_user_progress_data(user_id: str) -> dict:
    """Get or initialize user progress."""
    if user_id not in _user_progress:
        _user_progress[user_id] = {
            "completedLessons": [],
            "currentStreak": 0,
            "totalPoints": 0,
            "unlockedAchievements": [],
            "lastActiveDate": datetime.now().isoformat(),
            "dailyGoals": {
                "lessonsCompleted": 0,
                "questionsAnswered": 0,
                "minutesSpent": 0
            }
        }
    return _user_progress[user_id]


def calculate_lesson_state(lesson: dict, user_progress: dict) -> str:
    """Determine lesson state based on user progress."""
    lesson_id = lesson["id"]
    completed = user_progress["completedLessons"]
    
    if lesson_id in completed:
        return "completed"
    
    # Check if prerequisite is met
    if lesson["prerequisiteId"]:
        if lesson["prerequisiteId"] not in completed:
            return "locked"
    
    # First incomplete lesson is current
    if not completed or lesson["prerequisiteId"] in completed:
        # Check if this is the first uncompleted lesson
        for l in MOCK_LESSONS:
            if l["id"] not in completed:
                if l["id"] == lesson_id:
                    return "current"
                break
    
    return "future"


@router.get("/roadmap", response_model=RoadmapResponse)
async def get_roadmap(user_id: str = Depends(get_current_user)):
    """Get learning roadmap with user progress."""
    user_progress = get_user_progress_data(user_id)
    
    # Build lesson DTOs
    lessons = []
    current_lesson_id = None
    
    for lesson in MOCK_LESSONS:
        state = calculate_lesson_state(lesson, user_progress)
        
        if state == "current" and not current_lesson_id:
            current_lesson_id = lesson["id"]
        
        lesson_dto = LessonDto(
            id=lesson["id"],
            title=lesson["title"],
            description=lesson["description"],
            difficulty=lesson["difficulty"],
            estimatedMinutes=lesson["estimatedMinutes"],
            cardCount=lesson["cardCount"],
            state=state,
            prerequisiteId=lesson["prerequisiteId"],
            previewTopics=lesson["previewTopics"],
            order=lesson["order"]
        )
        
        # Add completion data if completed
        if lesson["id"] in _lesson_completions.get(user_id, {}):
            completion = _lesson_completions[user_id][lesson["id"]]
            lesson_dto.score = completion["score"]
            lesson_dto.stars = completion["stars"]
            lesson_dto.completedAt = completion["completedAt"]
        
        lessons.append(lesson_dto)
    
    # If no current lesson, use first lesson
    if not current_lesson_id:
        current_lesson_id = MOCK_LESSONS[0]["id"]
    
    # Build achievements
    achievements = []
    for ach in ACHIEVEMENTS:
        unlocked_at = None
        if ach["id"] in user_progress["unlockedAchievements"]:
            unlocked_at = int(datetime.now().timestamp() * 1000)
        
        achievements.append(AchievementDto(
            id=ach["id"],
            title=ach["title"],
            description=ach["description"],
            icon=ach["icon"],
            unlockedAt=unlocked_at,
            category=ach["category"]
        ))
    
    # Calculate daily goal progress
    daily_goals = user_progress["dailyGoals"]
    total_progress = (
        (daily_goals["lessonsCompleted"] / 1) * 0.4 +
        (min(daily_goals["questionsAnswered"], 10) / 10) * 0.3 +
        (min(daily_goals["minutesSpent"], 15) / 15) * 0.3
    )
    
    return RoadmapResponse(
        lessons=lessons,
        currentLessonId=current_lesson_id,
        userProgress=UserProgressDto(
            completedLessons=user_progress["completedLessons"],
            currentStreak=user_progress["currentStreak"],
            totalPoints=user_progress["totalPoints"],
            achievements=achievements,
            dailyGoalProgress=min(total_progress, 1.0),
            lastActiveDate=user_progress["lastActiveDate"]
        )
    )


@router.post("/complete-lesson", response_model=CompleteLessonResponse)
async def complete_lesson(
    request: CompleteLessonRequest,
    user_id: str = Depends(get_current_user)
):
    """Mark lesson as complete and award points."""
    user_progress = get_user_progress_data(user_id)
    
    # Calculate stars
    stars = 3 if request.score >= 90 else 2 if request.score >= 70 else 1 if request.score >= 50 else 0
    
    # Calculate points
    points = 50  # Base completion points
    if request.score == 100:
        points += 100  # Perfect score bonus
    if request.timeSpentMinutes < 5:
        points += 20  # Speed bonus
    
    # Update progress
    if request.lessonId not in user_progress["completedLessons"]:
        user_progress["completedLessons"].append(request.lessonId)
    
    user_progress["totalPoints"] += points
    user_progress["dailyGoals"]["lessonsCompleted"] += 1
    user_progress["dailyGoals"]["questionsAnswered"] += request.totalQuestions
    user_progress["dailyGoals"]["minutesSpent"] += request.timeSpentMinutes
    
    # Store completion data
    if user_id not in _lesson_completions:
        _lesson_completions[user_id] = {}
    
    _lesson_completions[user_id][request.lessonId] = {
        "score": request.score,
        "stars": stars,
        "completedAt": int(datetime.now().timestamp() * 1000)
    }
    
    # Check for new achievements
    new_achievements = []
    
    # First lesson
    if len(user_progress["completedLessons"]) == 1 and "first_steps" not in user_progress["unlockedAchievements"]:
        user_progress["unlockedAchievements"].append("first_steps")
        new_achievements.append(next(a for a in ACHIEVEMENTS if a["id"] == "first_steps"))
    
    # 5 lessons
    if len(user_progress["completedLessons"]) >= 5 and "bookworm" not in user_progress["unlockedAchievements"]:
        user_progress["unlockedAchievements"].append("bookworm")
        new_achievements.append(next(a for a in ACHIEVEMENTS if a["id"] == "bookworm"))
    
    # Perfect score
    if request.score == 100 and "perfect_score" not in user_progress["unlockedAchievements"]:
        user_progress["unlockedAchievements"].append("perfect_score")
        new_achievements.append(next(a for a in ACHIEVEMENTS if a["id"] == "perfect_score"))
    
    # All lessons
    if len(user_progress["completedLessons"]) >= len(MOCK_LESSONS) and "master" not in user_progress["unlockedAchievements"]:
        user_progress["unlockedAchievements"].append("master")
        new_achievements.append(next(a for a in ACHIEVEMENTS if a["id"] == "master"))
    
    # Find next lesson
    current_index = next((i for i, l in enumerate(MOCK_LESSONS) if l["id"] == request.lessonId), -1)
    next_lesson_id = MOCK_LESSONS[current_index + 1]["id"] if current_index < len(MOCK_LESSONS) - 1 else None
    
    return CompleteLessonResponse(
        success=True,
        pointsEarned=points,
        stars=stars,
        newAchievements=[AchievementDto(**a, unlockedAt=int(datetime.now().timestamp() * 1000), category=a["category"]) for a in new_achievements],
        unlockedNextLesson=next_lesson_id is not None,
        nextLessonId=next_lesson_id
    )


@router.get("/stats", response_model=UserStatsDto)
async def get_user_stats(user_id: str = Depends(get_current_user)):
    """Get user learning statistics."""
    user_progress = get_user_progress_data(user_id)
    
    # Calculate average score
    completions = _lesson_completions.get(user_id, {})
    avg_score = sum(c["score"] for c in completions.values()) / len(completions) if completions else 0
    
    # Build daily goals
    goals = user_progress["dailyGoals"]
    daily_goals = [
        DailyGoalDto(type="COMPLETE_LESSONS", target=1, current=goals["lessonsCompleted"], completed=goals["lessonsCompleted"] >= 1),
        DailyGoalDto(type="ANSWER_QUESTIONS", target=10, current=goals["questionsAnswered"], completed=goals["questionsAnswered"] >= 10),
        DailyGoalDto(type="SPEND_MINUTES", target=15, current=goals["minutesSpent"], completed=goals["minutesSpent"] >= 15),
    ]
    
    # Build achievements
    achievements = []
    for ach in ACHIEVEMENTS:
        if ach["id"] in user_progress["unlockedAchievements"]:
            achievements.append(AchievementDto(
                **ach,
                unlockedAt=int(datetime.now().timestamp() * 1000),
                category=ach["category"]
            ))
    
    return UserStatsDto(
        totalPoints=user_progress["totalPoints"],
        currentStreak=user_progress["currentStreak"],
        longestStreak=user_progress["currentStreak"],  # TODO: Track separately
        achievements=achievements,
        dailyGoals=daily_goals,
        lessonsCompleted=len(user_progress["completedLessons"]),
        totalLessons=len(MOCK_LESSONS),
        averageScore=avg_score,
        totalTimeMinutes=goals["minutesSpent"]
    )
