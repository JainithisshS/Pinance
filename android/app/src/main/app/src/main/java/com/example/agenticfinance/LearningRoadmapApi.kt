package com.example.agenticfinance

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

interface LearningRoadmapApi {
    
    @GET("/api/learning/roadmap")
    suspend fun getRoadmap(): RoadmapResponseDto
    
    @POST("/api/learning/unlock-lesson")
    suspend fun unlockLesson(@Body request: UnlockLessonRequest): UnlockLessonResponse
    
    @GET("/api/learning/stats")
    suspend fun getUserStats(): UserStatsDto
    
    @POST("/api/learning/complete-lesson")
    suspend fun completeLesson(@Body request: CompleteLessonRequest): CompleteLessonResponse
}

data class CompleteLessonRequest(
    val lessonId: String,
    val score: Int,
    val timeSpentMinutes: Int,
    val correctAnswers: Int,
    val totalQuestions: Int
)

data class CompleteLessonResponse(
    val success: Boolean,
    val pointsEarned: Int,
    val stars: Int,
    val newAchievements: List<AchievementDto>,
    val unlockedNextLesson: Boolean,
    val nextLessonId: String?
)
