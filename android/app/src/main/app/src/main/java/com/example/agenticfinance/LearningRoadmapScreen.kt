package com.example.agenticfinance

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearningRoadmapScreen(
    onLessonClick: (Lesson) -> Unit,
    onBackClick: () -> Unit
) {
    var roadmap by remember { mutableStateOf<LearningRoadmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    
    // Load roadmap on launch
    LaunchedEffect(Unit) {
        try {
            isLoading = true
            val response = ApiClient.roadmapApi.getRoadmap()
            roadmap = convertToRoadmap(response)
            isLoading = false
        } catch (e: Exception) {
            error = e.message ?: "Failed to load roadmap"
            isLoading = false
        }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Learning Path") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A237E)
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                error != null -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(error!!, color = Color.Red)
                        Button(onClick = {
                            scope.launch {
                                try {
                                    isLoading = true
                                    error = null
                                    val response = ApiClient.roadmapApi.getRoadmap()
                                    roadmap = convertToRoadmap(response)
                                    isLoading = false
                                } catch (e: Exception) {
                                    error = e.message
                                    isLoading = false
                                }
                            }
                        }) {
                            Text("Retry")
                        }
                    }
                }
                roadmap != null -> {
                    RoadmapContent(
                        roadmap = roadmap!!,
                        onLessonClick = onLessonClick
                    )
                }
            }
        }
    }
}

@Composable
fun RoadmapContent(
    roadmap: LearningRoadmap,
    onLessonClick: (Lesson) -> Unit
) {
    val listState = rememberLazyListState()
    val currentLessonIndex = roadmap.lessons.indexOfFirst { it.id == roadmap.currentLessonId }
    
    // Auto-scroll to current lesson
    LaunchedEffect(currentLessonIndex) {
        if (currentLessonIndex >= 0) {
            listState.animateScrollToItem(currentLessonIndex)
        }
    }
    
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Progress header
        ProgressHeader(roadmap.userProgress)
        
        // Roadmap path
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(roadmap.lessons) { lesson ->
                LessonNode(
                    lesson = lesson,
                    isCurrent = lesson.id == roadmap.currentLessonId,
                    onClick = { onLessonClick(lesson) }
                )
            }
        }
    }
}

@Composable
fun ProgressHeader(progress: UserProgress) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A237E)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ProgressStat(
                icon = "🔥",
                value = "${progress.currentStreak}",
                label = "Day Streak"
            )
            ProgressStat(
                icon = "⭐",
                value = "${progress.totalPoints}",
                label = "Points"
            )
            ProgressStat(
                icon = "🏆",
                value = "${progress.achievements.count { it.unlockedAt != null }}",
                label = "Achievements"
            )
        }
    }
}

@Composable
fun ProgressStat(icon: String, value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(icon, fontSize = 24.sp)
        Text(
            value,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            label,
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.7f)
        )
    }
}

@Composable
fun LessonNode(
    lesson: Lesson,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    val (backgroundColor, borderColor, iconColor) = when (lesson.state) {
        is LessonState.Completed -> Triple(
            Color(0xFF4CAF50).copy(alpha = 0.1f),
            Color(0xFF4CAF50),
            Color(0xFF4CAF50)
        )
        is LessonState.Current -> Triple(
            Color(0xFF2196F3).copy(alpha = 0.1f),
            Color(0xFF2196F3),
            Color(0xFF2196F3)
        )
        is LessonState.Locked -> Triple(
            Color(0xFFBDBDBD).copy(alpha = 0.1f),
            Color(0xFFBDBDBD),
            Color(0xFFBDBDBD)
        )
        is LessonState.Future -> Triple(
            Color(0xFFE0E0E0).copy(alpha = 0.1f),
            Color(0xFFE0E0E0),
            Color(0xFFE0E0E0)
        )
    }
    
    val scale by animateFloatAsState(
        targetValue = if (isCurrent) 1.05f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
    )
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .shadow(if (isCurrent) 8.dp else 2.dp, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .border(2.dp, borderColor, CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                when (lesson.state) {
                    is LessonState.Completed -> Text("✅", fontSize = 28.sp)
                    is LessonState.Current -> Text("📘", fontSize = 28.sp)
                    is LessonState.Locked -> Text("🔒", fontSize = 28.sp)
                    is LessonState.Future -> Text("⏳", fontSize = 28.sp)
                }
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            // Content
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    lesson.title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Text(
                    lesson.description,
                    fontSize = 14.sp,
                    color = Color.Gray,
                    maxLines = 2
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "⏱️ ${lesson.estimatedMinutes} min",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    DifficultyBadge(lesson.difficulty)
                }
                
                // Show score for completed lessons
                if (lesson.state is LessonState.Completed) {
                    val state = lesson.state as LessonState.Completed
                    Row(
                        modifier = Modifier.padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(state.stars) {
                            Text("⭐", fontSize = 16.sp)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "${state.score}%",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4CAF50)
                        )
                    }
                }
            }
            
            // Arrow
            Icon(
                Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = borderColor
            )
        }
    }
}

@Composable
fun DifficultyBadge(difficulty: Difficulty) {
    val (color, text) = when (difficulty) {
        Difficulty.BEGINNER -> Color(0xFF4CAF50) to "Beginner"
        Difficulty.INTERMEDIATE -> Color(0xFFFF9800) to "Intermediate"
        Difficulty.ADVANCED -> Color(0xFFF44336) to "Advanced"
    }
    
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.2f)
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

// Helper function to convert DTO to domain model
fun convertToRoadmap(dto: RoadmapResponseDto): LearningRoadmap {
    return LearningRoadmap(
        lessons = dto.lessons.map { lessonDto ->
            Lesson(
                id = lessonDto.id,
                title = lessonDto.title,
                description = lessonDto.description,
                difficulty = when (lessonDto.difficulty.lowercase()) {
                    "beginner" -> Difficulty.BEGINNER
                    "intermediate" -> Difficulty.INTERMEDIATE
                    "advanced" -> Difficulty.ADVANCED
                    else -> Difficulty.BEGINNER
                },
                estimatedMinutes = lessonDto.estimatedMinutes,
                cards = emptyList(), // Will be loaded when lesson is opened
                state = when (lessonDto.state) {
                    "completed" -> LessonState.Completed(
                        score = lessonDto.score ?: 0,
                        stars = lessonDto.stars ?: 0,
                        completedAt = lessonDto.completedAt ?: 0
                    )
                    "current" -> LessonState.Current
                    "locked" -> LessonState.Locked("Complete previous lesson")
                    else -> LessonState.Future
                },
                prerequisiteId = lessonDto.prerequisiteId,
                previewTopics = lessonDto.previewTopics,
                order = lessonDto.order
            )
        },
        currentLessonId = dto.currentLessonId,
        userProgress = UserProgress(
            completedLessons = dto.userProgress.completedLessons.toSet(),
            currentStreak = dto.userProgress.currentStreak,
            totalPoints = dto.userProgress.totalPoints,
            achievements = dto.userProgress.achievements.map { achievementDto ->
                Achievement(
                    id = achievementDto.id,
                    title = achievementDto.title,
                    description = achievementDto.description,
                    icon = achievementDto.icon,
                    unlockedAt = achievementDto.unlockedAt,
                    category = AchievementCategory.valueOf(achievementDto.category.uppercase())
                )
            },
            dailyGoalProgress = dto.userProgress.dailyGoalProgress,
            lastActiveDate = dto.userProgress.lastActiveDate
        )
    )
}
