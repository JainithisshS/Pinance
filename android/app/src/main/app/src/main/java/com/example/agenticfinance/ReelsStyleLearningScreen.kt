package com.example.agenticfinance

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.pager.*
import kotlinx.coroutines.launch

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// TOPIC MAP
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
data class TopicInfo(
    val id: String,
    val name: String,
    val level: Int,
    val emoji: String
)

val ALL_TOPICS = listOf(
    TopicInfo("money_basics",      "Money Basics",           1, "\uD83D\uDCB0"),
    TopicInfo("income_basics",     "Income Basics",          1, "\uD83D\uDCB5"),
    TopicInfo("expense_tracking",  "Expense Tracking",       2, "\uD83D\uDCCA"),
    TopicInfo("budgeting_basics",  "Budgeting Basics",       2, "\uD83D\uDCCB"),
    TopicInfo("emergency_fund",    "Emergency Fund",         3, "\uD83D\uDEE1\uFE0F"),
    TopicInfo("debt_management",   "Debt Management",        3, "\uD83D\uDCB3"),
    TopicInfo("saving_strategies", "Saving Strategies",      3, "\uD83D\uDC16"),
    TopicInfo("investment_basics", "Investment Basics",       4, "\uD83D\uDCC8"),
    TopicInfo("credit_score",      "Credit Score",           4, "\u2B50"),
    TopicInfo("financial_goals",   "Financial Goal Setting", 4, "\uD83C\uDFAF"),
)

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// LEARNING PROGRESS
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
object LearningProgress {
    var cardsCompleted by mutableStateOf(0)
    var xp by mutableStateOf(0)
    var streak by mutableStateOf(0)
    var bestStreak by mutableStateOf(0)
    var topicsUnlocked by mutableStateOf(1)
    var cardsInTopic by mutableStateOf(0)
    const val cardsPerTopic = 5
    val totalTopics get() = ALL_TOPICS.size
    var masteredConcepts = mutableStateListOf<String>()
    var arrearConcepts = mutableStateListOf<String>()
    var currentConcept by mutableStateOf("")
    // Daily insight for dashboard
    var dailyInsightTitle by mutableStateOf("Financial Literacy")
    var dailyInsightSnippet by mutableStateOf("Start learning to unlock daily insights")

    // Session persistence (survives tab switches within the same app session)
    var sessionCards by mutableStateOf<List<CardWithMeta>>(emptyList())
    var sessionAnsweredMap by mutableStateOf<Map<String, Pair<Int, Boolean>>>(emptyMap())
    var sessionCorrectlyAnswered by mutableStateOf<Set<String>>(emptySet())
    var sessionMasteryChangeMap by mutableStateOf<Map<String, Double>>(emptyMap())
    var sessionLastPage by mutableStateOf(0)

    fun onCorrect(conceptId: String) {
        cardsCompleted++; xp += 15; streak++
        if (streak > bestStreak) bestStreak = streak
        cardsInTopic++
        if (!masteredConcepts.contains(conceptId)) masteredConcepts.add(conceptId)
        arrearConcepts.remove(conceptId)
        if (cardsInTopic >= cardsPerTopic) {
            cardsInTopic = 0
            if (topicsUnlocked < totalTopics) topicsUnlocked++
        }
        val topic = ALL_TOPICS.find { it.id == conceptId }
        if (topic != null) {
            dailyInsightTitle = topic.name
            dailyInsightSnippet = "Mastered! Streak: $streak"
        }
    }
    fun onWrong(conceptId: String) {
        cardsCompleted++; xp += 3; streak = 0
        if (!arrearConcepts.contains(conceptId) && !masteredConcepts.contains(conceptId)) {
            arrearConcepts.add(conceptId)
        }
        val topic = ALL_TOPICS.find { it.id == conceptId }
        if (topic != null) {
            dailyInsightTitle = topic.name
            dailyInsightSnippet = "Needs revisiting"
        }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// TOPIC ILLUSTRATIONS (Canvas-drawn, always topic-relevant)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
@Composable
private fun ConceptIllustration(
    conceptId: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f

        // Background glow
        drawCircle(
            brush = Brush.radialGradient(
                listOf(accent.copy(alpha = 0.12f), Color.Transparent),
                center = Offset(cx, cy), radius = w * 0.45f
            ),
            center = Offset(cx, cy), radius = w * 0.45f
        )

        when (conceptId) {
            "money_basics" -> {
                // Rupee symbol ₹ with stacked coins
                val paint = android.graphics.Paint().apply {
                    color = accent.hashCode(); textSize = h * 0.38f
                    textAlign = android.graphics.Paint.Align.CENTER
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    isAntiAlias = true
                }
                drawContext.canvas.nativeCanvas.drawText("₹", cx, cy + h * 0.05f, paint)
                // Coins below
                for (i in 0..2) {
                    val coinY = cy + h * 0.22f + i * 10f
                    drawRoundRect(
                        color = accent.copy(alpha = 0.3f - i * 0.08f),
                        topLeft = Offset(cx - w * 0.18f, coinY),
                        size = androidx.compose.ui.geometry.Size(w * 0.36f, 8f),
                        cornerRadius = CornerRadius(4f)
                    )
                }
            }
            "income_basics" -> {
                // Upward arrow with ₹
                val arrowPath = Path().apply {
                    moveTo(cx, cy - h * 0.22f)
                    lineTo(cx + w * 0.12f, cy - h * 0.08f)
                    lineTo(cx + w * 0.04f, cy - h * 0.08f)
                    lineTo(cx + w * 0.04f, cy + h * 0.18f)
                    lineTo(cx - w * 0.04f, cy + h * 0.18f)
                    lineTo(cx - w * 0.04f, cy - h * 0.08f)
                    lineTo(cx - w * 0.12f, cy - h * 0.08f)
                    close()
                }
                drawPath(arrowPath, color = accent.copy(alpha = 0.7f))
                val paint = android.graphics.Paint().apply {
                    color = 0xFFFFFFFF.toInt(); textSize = h * 0.13f
                    textAlign = android.graphics.Paint.Align.CENTER
                    typeface = android.graphics.Typeface.DEFAULT_BOLD; isAntiAlias = true
                }
                drawContext.canvas.nativeCanvas.drawText("₹", cx, cy + h * 0.08f, paint)
            }
            "expense_tracking" -> {
                // Pie chart with 3 segments
                val oval = android.graphics.RectF(cx - w * 0.2f, cy - w * 0.2f, cx + w * 0.2f, cy + w * 0.2f)
                val colors = listOf(accent, accent.copy(alpha = 0.5f), Color(0xFFFF9800).copy(alpha = 0.6f))
                val sweeps = listOf(140f, 120f, 100f)
                var startAngle = -90f
                for (i in sweeps.indices) {
                    drawArc(
                        color = colors[i], startAngle = startAngle, sweepAngle = sweeps[i],
                        useCenter = true,
                        topLeft = Offset(cx - w * 0.2f, cy - w * 0.2f),
                        size = androidx.compose.ui.geometry.Size(w * 0.4f, w * 0.4f)
                    )
                    startAngle += sweeps[i]
                }
                // Center cutout for donut look
                drawCircle(color = Color(0xFF0A0E27), radius = w * 0.09f, center = Offset(cx, cy))
            }
            "budgeting_basics" -> {
                // Clipboard / checklist
                val clipW = w * 0.3f; val clipH = h * 0.42f
                val clipLeft = cx - clipW / 2; val clipTop = cy - clipH / 2
                drawRoundRect(
                    color = accent.copy(alpha = 0.18f),
                    topLeft = Offset(clipLeft, clipTop),
                    size = androidx.compose.ui.geometry.Size(clipW, clipH),
                    cornerRadius = CornerRadius(12f)
                )
                drawRoundRect(
                    color = accent.copy(alpha = 0.5f),
                    topLeft = Offset(clipLeft, clipTop),
                    size = androidx.compose.ui.geometry.Size(clipW, clipH),
                    cornerRadius = CornerRadius(12f),
                    style = Stroke(width = 2f)
                )
                // Clip at top
                drawRoundRect(
                    color = accent.copy(alpha = 0.6f),
                    topLeft = Offset(cx - w * 0.06f, clipTop - 6f),
                    size = androidx.compose.ui.geometry.Size(w * 0.12f, 12f),
                    cornerRadius = CornerRadius(4f)
                )
                // Checklist lines
                for (i in 0..2) {
                    val lineY = clipTop + clipH * 0.25f + i * clipH * 0.22f
                    // Check box
                    drawRoundRect(
                        color = if (i < 2) accent else Color.White.copy(0.2f),
                        topLeft = Offset(clipLeft + 14f, lineY - 5f),
                        size = androidx.compose.ui.geometry.Size(10f, 10f),
                        cornerRadius = CornerRadius(2f)
                    )
                    // Line
                    drawLine(
                        color = Color.White.copy(if (i < 2) 0.4f else 0.15f),
                        start = Offset(clipLeft + 30f, lineY),
                        end = Offset(clipLeft + clipW - 14f, lineY),
                        strokeWidth = 2.5f, cap = StrokeCap.Round
                    )
                }
            }
            "emergency_fund" -> {
                // Shield icon
                val shieldPath = Path().apply {
                    moveTo(cx, cy - h * 0.24f)
                    lineTo(cx + w * 0.2f, cy - h * 0.12f)
                    lineTo(cx + w * 0.2f, cy + h * 0.05f)
                    cubicTo(cx + w * 0.18f, cy + h * 0.2f, cx, cy + h * 0.28f, cx, cy + h * 0.28f)
                    cubicTo(cx, cy + h * 0.28f, cx - w * 0.18f, cy + h * 0.2f, cx - w * 0.2f, cy + h * 0.05f)
                    lineTo(cx - w * 0.2f, cy - h * 0.12f)
                    close()
                }
                drawPath(shieldPath, color = accent.copy(alpha = 0.25f))
                drawPath(shieldPath, color = accent.copy(alpha = 0.7f), style = Stroke(width = 2.5f))
                // Plus / cross inside
                drawLine(accent, Offset(cx, cy - h * 0.06f), Offset(cx, cy + h * 0.08f), strokeWidth = 3f, cap = StrokeCap.Round)
                drawLine(accent, Offset(cx - w * 0.06f, cy + h * 0.01f), Offset(cx + w * 0.06f, cy + h * 0.01f), strokeWidth = 3f, cap = StrokeCap.Round)
            }
            "debt_management" -> {
                // Broken chain links
                val linkW = w * 0.1f; val linkH = h * 0.13f
                for (i in -1..1) {
                    val lx = cx + i * linkW * 1.6f
                    val ly = cy + i * 4f
                    drawRoundRect(
                        color = if (i == 0) accent.copy(alpha = 0.15f) else accent.copy(alpha = 0.4f),
                        topLeft = Offset(lx - linkW / 2, ly - linkH / 2),
                        size = androidx.compose.ui.geometry.Size(linkW, linkH),
                        cornerRadius = CornerRadius(linkW / 2),
                        style = Stroke(width = 3f)
                    )
                }
                // Break flash
                drawLine(Color(0xFFFFD700), Offset(cx - 3f, cy - h * 0.1f), Offset(cx + 3f, cy - h * 0.02f), strokeWidth = 2.5f)
                drawLine(Color(0xFFFFD700), Offset(cx + 3f, cy - h * 0.02f), Offset(cx - 3f, cy + h * 0.06f), strokeWidth = 2.5f)
            }
            "saving_strategies" -> {
                // Piggy bank
                val bodyW = w * 0.28f; val bodyH = h * 0.2f
                // Body
                drawOval(
                    color = accent.copy(alpha = 0.35f),
                    topLeft = Offset(cx - bodyW, cy - bodyH * 0.6f),
                    size = androidx.compose.ui.geometry.Size(bodyW * 2, bodyH * 1.4f)
                )
                drawOval(
                    color = accent.copy(alpha = 0.7f),
                    topLeft = Offset(cx - bodyW, cy - bodyH * 0.6f),
                    size = androidx.compose.ui.geometry.Size(bodyW * 2, bodyH * 1.4f),
                    style = Stroke(width = 2f)
                )
                // Ear
                drawOval(
                    color = accent.copy(alpha = 0.5f),
                    topLeft = Offset(cx - bodyW * 0.5f, cy - bodyH * 0.9f),
                    size = androidx.compose.ui.geometry.Size(bodyW * 0.4f, bodyH * 0.5f)
                )
                // Eye
                drawCircle(Color.White.copy(0.8f), radius = 3.5f, center = Offset(cx - bodyW * 0.45f, cy - bodyH * 0.1f))
                // Coin slot on top
                drawRoundRect(
                    color = accent,
                    topLeft = Offset(cx - 12f, cy - bodyH * 0.62f),
                    size = androidx.compose.ui.geometry.Size(24f, 4f),
                    cornerRadius = CornerRadius(2f)
                )
                // Legs
                for (dx in listOf(-0.5f, 0.5f)) {
                    drawRoundRect(
                        color = accent.copy(alpha = 0.5f),
                        topLeft = Offset(cx + bodyW * dx - 4f, cy + bodyH * 0.65f),
                        size = androidx.compose.ui.geometry.Size(8f, h * 0.06f),
                        cornerRadius = CornerRadius(3f)
                    )
                }
            }
            "investment_basics" -> {
                // Stock chart going up
                val points = listOf(0.7f, 0.6f, 0.65f, 0.4f, 0.5f, 0.3f, 0.15f)
                val stepX = w * 0.55f / (points.size - 1)
                val startX = cx - w * 0.275f
                val baseY = cy + h * 0.15f; val rangeY = h * 0.35f
                val chartPath = Path().apply {
                    moveTo(startX, baseY - points[0] * rangeY)
                    for (i in 1 until points.size) {
                        val px = startX + i * stepX
                        val py = baseY - points[i] * rangeY
                        val prevX = startX + (i - 1) * stepX
                        val prevY = baseY - points[i - 1] * rangeY
                        cubicTo(prevX + stepX * 0.5f, prevY, px - stepX * 0.5f, py, px, py)
                    }
                }
                drawPath(chartPath, color = accent, style = Stroke(width = 3f, cap = StrokeCap.Round))
                // Fill below
                val fillPath = Path().apply {
                    addPath(chartPath)
                    lineTo(startX + (points.size - 1) * stepX, baseY)
                    lineTo(startX, baseY)
                    close()
                }
                drawPath(fillPath, brush = Brush.verticalGradient(
                    listOf(accent.copy(alpha = 0.2f), Color.Transparent),
                    startY = cy - h * 0.2f, endY = baseY
                ))
                // Arrow tip
                val lastX = startX + (points.size - 1) * stepX
                val lastY = baseY - points.last() * rangeY
                drawLine(accent, Offset(lastX - 10f, lastY + 6f), Offset(lastX, lastY), strokeWidth = 2.5f, cap = StrokeCap.Round)
                drawLine(accent, Offset(lastX - 8f, lastY - 8f), Offset(lastX, lastY), strokeWidth = 2.5f, cap = StrokeCap.Round)
            }
            "credit_score" -> {
                // Gauge / speedometer
                drawArc(
                    color = Color.White.copy(0.08f), startAngle = 180f, sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(cx - w * 0.22f, cy - h * 0.14f),
                    size = androidx.compose.ui.geometry.Size(w * 0.44f, h * 0.28f),
                    style = Stroke(width = 8f, cap = StrokeCap.Round)
                )
                // Colored arc (green portion ≈ 140°)
                drawArc(
                    brush = Brush.sweepGradient(listOf(Color(0xFFFF5252), Color(0xFFFFD700), accent)),
                    startAngle = 180f, sweepAngle = 140f, useCenter = false,
                    topLeft = Offset(cx - w * 0.22f, cy - h * 0.14f),
                    size = androidx.compose.ui.geometry.Size(w * 0.44f, h * 0.28f),
                    style = Stroke(width = 8f, cap = StrokeCap.Round)
                )
                // Needle
                val needleAngle = Math.toRadians(250.0)
                val needleLen = w * 0.15f
                drawLine(
                    Color.White, Offset(cx, cy),
                    Offset(cx + (needleLen * kotlin.math.cos(needleAngle)).toFloat(),
                        cy + (needleLen * kotlin.math.sin(needleAngle)).toFloat()),
                    strokeWidth = 2.5f, cap = StrokeCap.Round
                )
                drawCircle(Color.White, radius = 4f, center = Offset(cx, cy))
                // Score text
                val paint = android.graphics.Paint().apply {
                    color = accent.hashCode(); textSize = h * 0.1f
                    textAlign = android.graphics.Paint.Align.CENTER
                    typeface = android.graphics.Typeface.DEFAULT_BOLD; isAntiAlias = true
                }
                drawContext.canvas.nativeCanvas.drawText("750", cx, cy + h * 0.16f, paint)
            }
            "financial_goals" -> {
                // Target / bullseye with flag
                for (i in 3 downTo 0) {
                    val r = w * (0.06f + i * 0.045f)
                    drawCircle(
                        color = if (i % 2 == 0) accent.copy(alpha = 0.3f) else accent.copy(alpha = 0.12f),
                        radius = r, center = Offset(cx, cy)
                    )
                }
                drawCircle(color = accent, radius = 5f, center = Offset(cx, cy))
                // Flag pole
                val flagX = cx + w * 0.16f
                drawLine(accent.copy(0.7f), Offset(flagX, cy - h * 0.2f), Offset(flagX, cy + h * 0.08f), strokeWidth = 2f)
                // Flag
                val flagPath = Path().apply {
                    moveTo(flagX, cy - h * 0.2f)
                    lineTo(flagX + w * 0.1f, cy - h * 0.15f)
                    lineTo(flagX, cy - h * 0.1f)
                    close()
                }
                drawPath(flagPath, color = accent.copy(alpha = 0.6f))
            }
            else -> {
                // Fallback: ₹ symbol
                val paint = android.graphics.Paint().apply {
                    color = accent.hashCode(); textSize = h * 0.35f
                    textAlign = android.graphics.Paint.Align.CENTER
                    typeface = android.graphics.Typeface.DEFAULT_BOLD; isAntiAlias = true
                }
                drawContext.canvas.nativeCanvas.drawText("₹", cx, cy + h * 0.08f, paint)
            }
        }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// MESH GRADIENT PALETTES (concept-driven)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
private val CONCEPT_GRADIENTS = mapOf(
    "money_basics"      to listOf(Color(0xFF0A0E27), Color(0xFF1A1A2E), Color(0xFF2B1F0A)),
    "income_basics"     to listOf(Color(0xFF060B28), Color(0xFF0A2A1B), Color(0xFF0E3B16)),
    "expense_tracking"  to listOf(Color(0xFF0A0A1E), Color(0xFF1C1024), Color(0xFF2A0E1A)),
    "budgeting_basics"  to listOf(Color(0xFF060D28), Color(0xFF0C1840), Color(0xFF0A2545)),
    "emergency_fund"    to listOf(Color(0xFF0D0505), Color(0xFF2A0A0A), Color(0xFF1A0000)),
    "debt_management"   to listOf(Color(0xFF0A0520), Color(0xFF1A0A35), Color(0xFF2E1065)),
    "saving_strategies" to listOf(Color(0xFF050F0F), Color(0xFF0A2020), Color(0xFF0B3535)),
    "investment_basics" to listOf(Color(0xFF050A1A), Color(0xFF0C2040), Color(0xFF001F3F)),
    "credit_score"      to listOf(Color(0xFF0D0D05), Color(0xFF1A1A0A), Color(0xFF2D2D0A)),
    "financial_goals"   to listOf(Color(0xFF050D0A), Color(0xFF0A2015), Color(0xFF1B4332)),
)

private val CONCEPT_ACCENTS = mapOf(
    "money_basics"      to Color(0xFFFFD700),
    "income_basics"     to Color(0xFF00E5A0),
    "expense_tracking"  to Color(0xFFFF9800),
    "budgeting_basics"  to Color(0xFF448AFF),
    "emergency_fund"    to Color(0xFFFF5252),
    "debt_management"   to Color(0xFFBB86FC),
    "saving_strategies" to Color(0xFF00E5FF),
    "investment_basics" to Color(0xFF00BCD4),
    "credit_score"      to Color(0xFFFFEB3B),
    "financial_goals"   to Color(0xFF00E5A0),
)

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// CARD WITH META
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
data class CardWithMeta(
    val card: LearningCardDto,
    val masteryPercent: Double = 0.0,
    val difficulty: Int = 1,
    val conceptName: String = ""
)

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// MAIN SCREEN
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
@OptIn(ExperimentalPagerApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ReelsStyleLearningScreen(onBackClick: () -> Unit = {}) {
    val bgColor = Color(0xFF060B28)
    val cardBg = Color(0xFF0F1B2E)
    val accentCyan = Color(0xFF00E5FF)
    val glowPurple = Color(0xFFBB86FC)
    val glowBlue = Color(0xFF448AFF)

    // Restore session state (survives tab switches)
    val hasSession = LearningProgress.sessionCards.isNotEmpty()
    var cardsWithMeta by remember { mutableStateOf(LearningProgress.sessionCards) }
    var isLoadingFirst by remember { mutableStateOf(!hasSession) }
    var isLoadingNext by remember { mutableStateOf(false) }
    var correctlyAnsweredConcepts by remember { mutableStateOf(LearningProgress.sessionCorrectlyAnswered) }
    var answeredMap by remember { mutableStateOf(LearningProgress.sessionAnsweredMap) }
    var masteryChangeMap by remember { mutableStateOf(LearningProgress.sessionMasteryChangeMap) }
    var showRoadmapSheet by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState(initialPage = if (hasSession) LearningProgress.sessionLastPage.coerceAtMost((LearningProgress.sessionCards.size - 1).coerceAtLeast(0)) else 0)
    val scope = rememberCoroutineScope()
    val view = LocalView.current

    // Save session state when leaving the screen
    DisposableEffect(Unit) {
        onDispose {
            LearningProgress.sessionCards = cardsWithMeta
            LearningProgress.sessionAnsweredMap = answeredMap
            LearningProgress.sessionCorrectlyAnswered = correctlyAnsweredConcepts
            LearningProgress.sessionMasteryChangeMap = masteryChangeMap
            LearningProgress.sessionLastPage = pagerState.currentPage
        }
    }

    // Breathing animation for streak orb
    val infiniteTransition = rememberInfiniteTransition(label = "learnPulse")
    val breathScale by infiniteTransition.animateFloat(
        initialValue = 0.85f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing), RepeatMode.Reverse),
        label = "breathScale"
    )
    val meshPhase by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(8000, easing = LinearEasing)),
        label = "meshPhase"
    )

    // Load first card only if no session exists
    LaunchedEffect(Unit) {
        if (cardsWithMeta.isEmpty()) {
            isLoadingFirst = true
            val result = fetchCardWithMeta(exclude = emptySet())
            cardsWithMeta = listOf(result)
            LearningProgress.currentConcept = result.card.concept_id
            LearningProgress.dailyInsightTitle = result.conceptName
            LearningProgress.dailyInsightSnippet = result.card.content.take(80) + "..."
            isLoadingFirst = false
        }
    }

    // Fetch next card when current is answered
    LaunchedEffect(pagerState.currentPage, answeredMap.size) {
        val meta = cardsWithMeta.getOrNull(pagerState.currentPage) ?: return@LaunchedEffect
        val isLastCard = pagerState.currentPage == cardsWithMeta.size - 1
        val isAnswered = answeredMap.containsKey(meta.card.id)
        if (isLastCard && isAnswered && !isLoadingNext) {
            isLoadingNext = true
            val next = fetchCardWithMeta(exclude = correctlyAnsweredConcepts)
            cardsWithMeta = cardsWithMeta + next
            isLoadingNext = false
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        cardsWithMeta.getOrNull(pagerState.currentPage)?.let {
            LearningProgress.currentConcept = it.card.concept_id
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(bgColor)) {
        when {
            isLoadingFirst -> {
                // Premium loading state
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Pulsing orb
                    Box(modifier = Modifier.size(80.dp), contentAlignment = Alignment.Center) {
                        Canvas(modifier = Modifier.size(80.dp)) {
                            drawCircle(
                                brush = Brush.radialGradient(listOf(accentCyan.copy(alpha = 0.15f), Color.Transparent)),
                                radius = 40.dp.toPx()
                            )
                            drawCircle(color = accentCyan.copy(alpha = 0.3f), radius = 16.dp.toPx())
                            drawCircle(color = accentCyan, radius = 6.dp.toPx())
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Text("Personalising your lesson...", color = Color.White.copy(0.6f), fontSize = 13.sp)
                }
            }
            cardsWithMeta.isNotEmpty() -> {
                VerticalPager(
                    count = cardsWithMeta.size,
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val meta = cardsWithMeta[page]
                    val gradient = CONCEPT_GRADIENTS[meta.card.concept_id]
                        ?: listOf(Color(0xFF060B28), Color(0xFF0F1B2E), Color(0xFF1A1A2E))
                    val answered = answeredMap[meta.card.id]
                    val masteryDelta = masteryChangeMap[meta.card.id]
                    ReelsCard(
                        card = meta.card,
                        gradient = gradient,
                        isAnswered = answered != null,
                        wasCorrect = answered?.second,
                        masteryPercent = meta.masteryPercent,
                        difficulty = meta.difficulty,
                        conceptName = meta.conceptName,
                        masteryDelta = masteryDelta,
                        cardNumber = page + 1,
                        meshPhase = meshPhase,
                        onAnswer = { answerIndex, isCorrect ->
                            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            answeredMap = answeredMap + (meta.card.id to Pair(answerIndex, isCorrect))
                            if (isCorrect) {
                                LearningProgress.onCorrect(meta.card.concept_id)
                                correctlyAnsweredConcepts = correctlyAnsweredConcepts + meta.card.concept_id
                            } else {
                                LearningProgress.onWrong(meta.card.concept_id)
                            }

                            scope.launch {
                                try {
                                    val response = ApiClient.api.submitAnswer(
                                        SubmitAnswerRequestDto(
                                            card_id = meta.card.id,
                                            answer_index = answerIndex,
                                            time_spent_seconds = 30
                                        )
                                    )
                                    val change = response.belief_update.new_mastery - response.belief_update.previous_mastery
                                    masteryChangeMap = masteryChangeMap + (meta.card.id to change * 100)
                                } catch (_: Exception) {}
                            }

                            scope.launch {
                                kotlinx.coroutines.delay(2200)
                                if (page < cardsWithMeta.size - 1) {
                                    pagerState.animateScrollToPage(page + 1)
                                }
                            }
                        }
                    )
                }

                // ═══════════════ HOLOGRAPHIC TOP BAR ═══════════════
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .align(Alignment.TopCenter),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Close button (line-art X)
                    Surface(
                        modifier = Modifier.size(36.dp).clickable { onBackClick() },
                        shape = CircleShape,
                        color = Color.Black.copy(0.5f),
                        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.1f))
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Canvas(modifier = Modifier.size(14.dp)) {
                                val s = size.width
                                drawLine(Color.White.copy(alpha = 0.8f), Offset(2.dp.toPx(), 2.dp.toPx()), Offset(s - 2.dp.toPx(), s - 2.dp.toPx()), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
                                drawLine(Color.White.copy(alpha = 0.8f), Offset(s - 2.dp.toPx(), 2.dp.toPx()), Offset(2.dp.toPx(), s - 2.dp.toPx()), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
                            }
                        }
                    }

                    // ── Knowledge Streak Orb (breathing holographic glow) ──
                    if (LearningProgress.streak > 0) {
                        val streakColor = if (LearningProgress.streak >= 5) accentCyan else Color(0xFFFFAB40)
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color.Black.copy(0.5f),
                            border = BorderStroke(0.5.dp, streakColor.copy(alpha = 0.3f)),
                            modifier = Modifier.scale(breathScale.coerceIn(0.95f, 1.05f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Streak orb (Canvas, no emoji)
                                Canvas(modifier = Modifier.size(18.dp)) {
                                    drawCircle(
                                        brush = Brush.radialGradient(listOf(streakColor.copy(alpha = 0.6f), Color.Transparent)),
                                        radius = 9.dp.toPx()
                                    )
                                    drawCircle(color = streakColor, radius = 5.dp.toPx())
                                    drawCircle(color = Color.White.copy(alpha = 0.6f), radius = 2.dp.toPx())
                                }
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "${LearningProgress.streak}",
                                    color = streakColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    } else {
                        Spacer(Modifier.width(48.dp))
                    }

                    // ── XP badge ──
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color.Black.copy(0.5f),
                        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.1f))
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            // Lightning bolt icon
                            Canvas(modifier = Modifier.size(12.dp)) {
                                val s = size.width
                                val path = Path().apply {
                                    moveTo(s * 0.6f, 0f)
                                    lineTo(s * 0.25f, s * 0.5f)
                                    lineTo(s * 0.5f, s * 0.5f)
                                    lineTo(s * 0.4f, s)
                                    lineTo(s * 0.75f, s * 0.45f)
                                    lineTo(s * 0.5f, s * 0.45f)
                                    close()
                                }
                                drawPath(path, Color(0xFFFFD700))
                            }
                            Spacer(Modifier.width(4.dp))
                            Text("${LearningProgress.xp}", color = Color(0xFFFFD700),
                                fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }

                    // ── Pathfinder Roadmap (compass line-art) ──
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Black.copy(0.5f),
                        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.1f)),
                        modifier = Modifier.clickable { showRoadmapSheet = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Compass icon (Canvas line-art)
                            Canvas(modifier = Modifier.size(16.dp)) {
                                val cx = size.width / 2f; val cy = size.height / 2f
                                val r = size.width * 0.42f
                                drawCircle(Color.White.copy(alpha = 0.4f), radius = r, center = Offset(cx, cy), style = Stroke(1.dp.toPx()))
                                // N-S needle
                                drawLine(Color(0xFFFF5252), Offset(cx, cy - r * 0.7f), Offset(cx, cy), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
                                drawLine(Color.White.copy(alpha = 0.5f), Offset(cx, cy), Offset(cx, cy + r * 0.7f), strokeWidth = 1.dp.toPx(), cap = StrokeCap.Round)
                                // E-W
                                drawLine(Color.White.copy(alpha = 0.3f), Offset(cx - r * 0.5f, cy), Offset(cx + r * 0.5f, cy), strokeWidth = 0.8.dp.toPx())
                            }
                            // Progress dots
                            ALL_TOPICS.take(6).forEach { topic ->
                                val isMastered = LearningProgress.masteredConcepts.contains(topic.id)
                                val isArrear = LearningProgress.arrearConcepts.contains(topic.id)
                                val isCurrent = LearningProgress.currentConcept == topic.id
                                Box(
                                    modifier = Modifier
                                        .size(if (isCurrent) 8.dp else 5.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                isCurrent -> accentCyan
                                                isMastered -> Color(0xFF00E5A0)
                                                isArrear -> Color(0xFFFF5252)
                                                else -> Color.White.copy(0.15f)
                                            }
                                        )
                                        .then(if (isCurrent) Modifier.border(0.5.dp, Color.White, CircleShape) else Modifier)
                                )
                            }
                        }
                    }
                }

                // Loading indicator at bottom
                if (isLoadingNext) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
                        color = accentCyan, trackColor = Color.Transparent
                    )
                }

                // Swipe hint for first card
                if (pagerState.currentPage == 0 && answeredMap.isEmpty()) {
                    Surface(
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = Color.Black.copy(0.6f),
                        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.1f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Up arrow icon
                            Canvas(modifier = Modifier.size(12.dp)) {
                                val s = size.width
                                drawLine(Color.White.copy(alpha = 0.6f), Offset(s / 2, 0f), Offset(s / 2, s), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
                                drawLine(Color.White.copy(alpha = 0.6f), Offset(s * 0.2f, s * 0.35f), Offset(s / 2, 0f), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
                                drawLine(Color.White.copy(alpha = 0.6f), Offset(s * 0.8f, s * 0.35f), Offset(s / 2, 0f), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text("Swipe up for next", color = Color.White.copy(0.6f), fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    // ── ROADMAP BOTTOM SHEET ──
    if (showRoadmapSheet) {
        RoadmapBottomSheet(onDismiss = { showRoadmapSheet = false })
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// ROADMAP BOTTOM SHEET
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoadmapBottomSheet(onDismiss: () -> Unit) {
    val bgColor = Color(0xFF0A0E1A)
    val accentCyan = Color(0xFF00E5FF)
    val glowPurple = Color(0xFFBB86FC)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = bgColor,
        contentColor = Color.White,
        dragHandle = {
            Box(
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
                    .width(40.dp).height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(0.2f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            // Header with compass icon
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(modifier = Modifier.size(24.dp)) {
                    val cx = size.width / 2f; val cy = size.height / 2f
                    val r = size.width * 0.4f
                    drawCircle(Color.White.copy(alpha = 0.3f), radius = r, center = Offset(cx, cy), style = Stroke(1.5.dp.toPx()))
                    drawLine(Color(0xFFFF5252), Offset(cx, cy - r * 0.75f), Offset(cx, cy), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                    drawLine(Color.White.copy(alpha = 0.5f), Offset(cx, cy), Offset(cx, cy + r * 0.75f), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
                    drawCircle(accentCyan, radius = 2.dp.toPx(), center = Offset(cx, cy))
                }
                Spacer(Modifier.width(10.dp))
                Text("Financial Maturity Roadmap", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "${LearningProgress.masteredConcepts.size} mastered  |  ${LearningProgress.arrearConcepts.size} to revisit  |  ${LearningProgress.xp} XP",
                color = Color.White.copy(0.4f), fontSize = 12.sp
            )

            Spacer(Modifier.height(16.dp))

            // Arrears section
            if (LearningProgress.arrearConcepts.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A0A0A)),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, Color(0xFFFF5252).copy(alpha = 0.2f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Circular arrow icon
                            Canvas(modifier = Modifier.size(16.dp)) {
                                val cx = size.width / 2f; val cy = size.height / 2f; val r = 6.dp.toPx()
                                drawArc(Color(0xFFFF8A80), 0f, 300f, false, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round),
                                    topLeft = Offset(cx - r, cy - r), size = androidx.compose.ui.geometry.Size(r * 2, r * 2))
                            }
                            Spacer(Modifier.width(8.dp))
                            Text("Needs Revisiting", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFFFF8A80))
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "POMDP detected weak areas. These topics will appear again with fresh content.",
                            color = Color.White.copy(0.5f), fontSize = 11.sp, lineHeight = 16.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        LearningProgress.arrearConcepts.forEach { conceptId ->
                            val topic = ALL_TOPICS.find { it.id == conceptId }
                            if (topic != null) {
                                Row(
                                    modifier = Modifier.padding(vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFFFF5252)))
                                    Spacer(Modifier.width(10.dp))
                                    Text(topic.name, color = Color.White.copy(0.8f), fontSize = 13.sp)
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // All topics by level — vertical progress map
            val topicsByLevel = ALL_TOPICS.groupBy { it.level }
            val levelNames = mapOf(1 to "Foundations", 2 to "Basic Skills", 3 to "Intermediate", 4 to "Advanced")

            topicsByLevel.forEach { (level, topics) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Level $level",
                        color = accentCyan,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(levelNames[level] ?: "", color = Color.White.copy(0.35f), fontSize = 11.sp)
                    Spacer(Modifier.weight(1f))
                    val mastered = topics.count { LearningProgress.masteredConcepts.contains(it.id) }
                    Text(
                        "$mastered/${topics.size}",
                        color = if (mastered == topics.size) Color(0xFF00E5A0) else Color.White.copy(0.3f),
                        fontSize = 11.sp
                    )
                }

                topics.forEach { topic ->
                    val isMastered = LearningProgress.masteredConcepts.contains(topic.id)
                    val isArrear = LearningProgress.arrearConcepts.contains(topic.id)
                    val isCurrent = LearningProgress.currentConcept == topic.id
                    val accent = CONCEPT_ACCENTS[topic.id] ?: accentCyan

                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = when {
                                isCurrent -> accent.copy(0.1f)
                                isMastered -> Color(0xFF00E5A0).copy(0.06f)
                                isArrear -> Color(0xFFFF5252).copy(0.06f)
                                else -> Color.White.copy(0.03f)
                            }
                        ),
                        border = if (isCurrent) BorderStroke(1.dp, accent.copy(alpha = 0.3f)) else null
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Status dot
                            Box(
                                modifier = Modifier.size(8.dp).clip(CircleShape).background(
                                    when {
                                        isMastered -> Color(0xFF00E5A0)
                                        isArrear -> Color(0xFFFF5252)
                                        isCurrent -> accent
                                        else -> Color.White.copy(0.15f)
                                    }
                                )
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    topic.name,
                                    color = Color.White.copy(if (isMastered || isCurrent || isArrear) 0.9f else 0.4f),
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                    fontSize = 14.sp
                                )
                                if (isCurrent) {
                                    Text("Currently studying", color = accent.copy(0.6f), fontSize = 10.sp)
                                }
                            }
                            // Status label
                            val statusText = when {
                                isMastered -> "Mastered"
                                isArrear -> "Revisit"
                                isCurrent -> "Active"
                                else -> "Locked"
                            }
                            val statusColor = when {
                                isMastered -> Color(0xFF00E5A0)
                                isArrear -> Color(0xFFFF5252)
                                isCurrent -> accent
                                else -> Color.White.copy(0.2f)
                            }
                            Text(statusText, fontSize = 10.sp, color = statusColor, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                // Level divider
                if (level < 4) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f), color = Color.White.copy(0.06f))
                        // Down arrow (Canvas)
                        Canvas(modifier = Modifier.size(12.dp).padding(horizontal = 4.dp)) {
                            val s = size.width
                            drawLine(Color.White.copy(alpha = 0.25f), Offset(s / 2, 0f), Offset(s / 2, s), strokeWidth = 1.dp.toPx(), cap = StrokeCap.Round)
                            drawLine(Color.White.copy(alpha = 0.25f), Offset(s * 0.2f, s * 0.6f), Offset(s / 2, s), strokeWidth = 1.dp.toPx(), cap = StrokeCap.Round)
                            drawLine(Color.White.copy(alpha = 0.25f), Offset(s * 0.8f, s * 0.6f), Offset(s / 2, s), strokeWidth = 1.dp.toPx(), cap = StrokeCap.Round)
                        }
                        HorizontalDivider(modifier = Modifier.weight(1f), color = Color.White.copy(0.06f))
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Stats footer
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(0.04f))
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatItem("Cards", "${LearningProgress.cardsCompleted}", accentCyan)
                    StatItem("XP", "${LearningProgress.xp}", Color(0xFFFFD700))
                    StatItem("Best", "${LearningProgress.bestStreak}", Color(0xFFFFAB40))
                    StatItem("Done", "${LearningProgress.masteredConcepts.size}", Color(0xFF00E5A0))
                }
            }
        }
    }
}

@Composable
fun StatItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text(label, color = Color.White.copy(0.4f), fontSize = 10.sp)
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// FETCH CARD
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
private suspend fun fetchCardWithMeta(exclude: Set<String>): CardWithMeta {
    return try {
        val response = ApiClient.api.getNextCard(exclude = exclude.joinToString(","))
        CardWithMeta(
            card = response.card,
            masteryPercent = response.explanation.mastery_percent,
            difficulty = response.explanation.difficulty,
            conceptName = response.explanation.concept_name.ifEmpty {
                response.card.concept_id.replace("_", " ").replaceFirstChar { it.uppercase() }
            }
        )
    } catch (_: Exception) {
        val fallbacks = listOf(
            LearningCardDto("off_1", "income_basics",
                "Income is money received regularly \u2014 salary, freelance, investments, or side hustles. Understanding your income sources is the first step to financial control.",
                QuizDto("Which is NOT a source of income?", listOf("Salary", "Rent from property", "Grocery shopping", "Dividends"), 2, "Grocery shopping is an expense."), "offline", ""),
            LearningCardDto("off_2", "budgeting_basics",
                "The 50/30/20 rule: 50% on needs, 30% on wants, 20% on savings. It\u2019s the simplest budgeting framework.",
                QuizDto("In 50/30/20, what % goes to savings?", listOf("10%", "20%", "30%", "50%"), 1, "20% goes to savings."), "offline", ""),
            LearningCardDto("off_3", "emergency_fund",
                "Emergency fund = 3\u20136 months of expenses in a liquid account. Your financial airbag.",
                QuizDto("How many months should it cover?", listOf("1", "2", "3\u20136", "12+"), 2, "3\u20136 months is standard."), "offline", ""),
            LearningCardDto("off_4", "debt_management",
                "Not all debt is equal. Home loans build assets. Credit card debt (36\u201342%!) destroys wealth.",
                QuizDto("Which debt to pay first?", listOf("Home loan", "Education", "Credit card", "Car"), 2, "Credit card charges 36\u201342%."), "offline", ""),
            LearningCardDto("off_5", "saving_strategies",
                "Pay yourself first: auto-transfer 20% of income to savings on payday.",
                QuizDto("'Pay yourself first' means?", listOf("Buy something nice", "Auto-save first", "Pay loans first", "Invest in gold"), 1, "Auto-save before spending."), "offline", ""),
        )
        val card = fallbacks.firstOrNull { !exclude.contains(it.concept_id) } ?: fallbacks.first()
        CardWithMeta(card = card, masteryPercent = 0.0, difficulty = 1,
            conceptName = card.concept_id.replace("_", " ").replaceFirstChar { it.uppercase() })
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// GLASSMORPHIC REELS CARD
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReelsCard(
    card: LearningCardDto,
    gradient: List<Color>,
    isAnswered: Boolean,
    wasCorrect: Boolean?,
    masteryPercent: Double,
    difficulty: Int,
    conceptName: String,
    masteryDelta: Double?,
    cardNumber: Int,
    meshPhase: Float,
    onAnswer: (Int, Boolean) -> Unit
) {
    var selectedAnswer by remember(card.id) { mutableStateOf<Int?>(null) }
    var showQuiz by remember(card.id) { mutableStateOf(false) }
    val accent = CONCEPT_ACCENTS[card.concept_id] ?: Color(0xFF00E5FF)

    val animatedMastery by animateFloatAsState(
        targetValue = (masteryPercent / 100f).toFloat().coerceIn(0f, 1f),
        animationSpec = tween(800),
        label = "mastery"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(gradient))
    ) {
        // Subtle animated mesh effect
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f; val cy = size.height / 3f
            val phase = meshPhase * 0.0175f
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(accent.copy(alpha = 0.04f), Color.Transparent),
                    center = Offset(cx + 50f * kotlin.math.sin(phase.toDouble()).toFloat(), cy)
                ),
                radius = size.width * 0.6f,
                center = Offset(cx + 50f * kotlin.math.sin(phase.toDouble()).toFloat(), cy)
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(gradient.last().copy(alpha = 0.08f), Color.Transparent),
                    center = Offset(size.width * 0.3f, size.height * 0.7f)
                ),
                radius = size.width * 0.4f,
                center = Offset(size.width * 0.3f, size.height * 0.7f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = 56.dp, bottom = 20.dp, start = 18.dp, end = 18.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Top
        ) {
            // ── Header: Concept badge + difficulty + card # ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = accent.copy(0.12f),
                    border = BorderStroke(0.5.dp, accent.copy(alpha = 0.25f))
                ) {
                    Text(
                        conceptName.ifEmpty { card.concept_id.replace("_", " ").replaceFirstChar { it.uppercase() } },
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        color = accent, fontSize = 13.sp, fontWeight = FontWeight.Bold
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Difficulty dots
                    (1..5).forEach { i ->
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 1.dp)
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (i <= difficulty) Color(0xFFFFD700) else Color.White.copy(0.12f))
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Text("#$cardNumber", color = Color.White.copy(0.3f), fontSize = 11.sp)
                }
            }

            Spacer(Modifier.height(10.dp))

            // ── Mastery gauge (vertical neon track) ──
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Mastery", color = Color.White.copy(0.45f), fontSize = 10.sp)
                Text("${masteryPercent.toInt()}%", color = accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier.fillMaxWidth().height(4.dp)
                    .clip(RoundedCornerShape(2.dp)).background(Color.White.copy(0.06f))
            ) {
                Box(
                    modifier = Modifier.fillMaxHeight().fillMaxWidth(animatedMastery)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Brush.horizontalGradient(listOf(accent.copy(0.5f), accent)))
                )
            }

            Spacer(Modifier.height(14.dp))

            // ── Topic Illustration ──
            Card(
                modifier = Modifier.fillMaxWidth().height(175.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.03f)),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.15f))
            ) {
                ConceptIllustration(
                    conceptId = card.concept_id,
                    accent = accent,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(Modifier.height(16.dp))

            // ── Topic Headline ──
            Text(
                conceptName.uppercase(),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                letterSpacing = 1.sp
            )

            Spacer(Modifier.height(8.dp))

            // ── Content in glassmorphic card ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.08f))
            ) {
                Text(
                    card.content,
                    color = Color.White.copy(0.88f),
                    fontSize = 15.sp,
                    lineHeight = 24.sp,
                    modifier = Modifier.padding(16.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            // ── Interactive Quiz Layer (with slide-in) ──
            AnimatedVisibility(
                visible = showQuiz,
                enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                QuizSection(
                    quiz = card.quiz, selectedAnswer = selectedAnswer,
                    isAnswered = isAnswered, accent = accent,
                    onOptionClick = { index ->
                        if (!isAnswered) {
                            selectedAnswer = index
                            onAnswer(index, index == card.quiz.correct_answer_index)
                        }
                    }
                )
            }

            // ── Feedback Banner ──
            AnimatedVisibility(
                visible = isAnswered && wasCorrect != null,
                enter = fadeIn() + scaleIn(), exit = fadeOut()
            ) {
                val bannerColor = if (wasCorrect == true) Color(0xFF00E5A0) else Color(0xFFFF5252)
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (wasCorrect == true) Color(0xFF0A2A1B) else Color(0xFF2A0A0A)
                    ),
                    border = BorderStroke(1.dp, bannerColor.copy(alpha = 0.25f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Checkmark or X icon
                            Canvas(modifier = Modifier.size(24.dp)) {
                                val s = size.width
                                if (wasCorrect == true) {
                                    drawCircle(Color(0xFF00E5A0).copy(alpha = 0.2f), radius = s / 2)
                                    drawLine(Color(0xFF00E5A0), Offset(s * 0.25f, s * 0.5f), Offset(s * 0.42f, s * 0.68f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                                    drawLine(Color(0xFF00E5A0), Offset(s * 0.42f, s * 0.68f), Offset(s * 0.75f, s * 0.3f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                                } else {
                                    drawCircle(Color(0xFFFF5252).copy(alpha = 0.2f), radius = s / 2)
                                    drawLine(Color(0xFFFF5252), Offset(s * 0.3f, s * 0.3f), Offset(s * 0.7f, s * 0.7f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                                    drawLine(Color(0xFFFF5252), Offset(s * 0.7f, s * 0.3f), Offset(s * 0.3f, s * 0.7f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                                }
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (wasCorrect == true) "Correct! +15 XP" else "We'll revisit this topic",
                                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp
                                )
                                Text(
                                    if (wasCorrect == true) "Topic mastered \u2014 moving on!"
                                    else "POMDP will re-queue this concept with fresh content",
                                    color = Color.White.copy(0.7f), fontSize = 12.sp
                                )
                            }
                        }
                        if (masteryDelta != null) {
                            Spacer(Modifier.height(6.dp))
                            val sign = if (masteryDelta >= 0) "+" else ""
                            Text(
                                "Mastery: $sign${String.format("%.1f", masteryDelta)}%",
                                color = if (masteryDelta >= 0) Color(0xFF00E5A0) else Color(0xFFFF5252),
                                fontWeight = FontWeight.Bold, fontSize = 13.sp
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(card.quiz.explanation, color = Color.White.copy(0.65f), fontSize = 12.sp, lineHeight = 17.sp)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Test Button (glassmorphic) ──
            if (!showQuiz && !isAnswered) {
                Button(
                    onClick = { showQuiz = true },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = accent.copy(0.15f)),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.3f))
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Brain/test icon
                        Canvas(modifier = Modifier.size(18.dp)) {
                            val s = size.width
                            // Simple brain outline
                            drawCircle(accent.copy(alpha = 0.5f), radius = s * 0.35f, center = Offset(s * 0.4f, s * 0.45f), style = Stroke(1.5.dp.toPx()))
                            drawCircle(accent.copy(alpha = 0.5f), radius = s * 0.3f, center = Offset(s * 0.6f, s * 0.45f), style = Stroke(1.5.dp.toPx()))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("Take Quiz", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = accent)
                    }
                }
            }

            if (isAnswered) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Canvas(modifier = Modifier.size(10.dp)) {
                        drawLine(Color.White.copy(alpha = 0.4f), Offset(size.width / 2, size.height), Offset(size.width / 2, 0f), strokeWidth = 1.dp.toPx(), cap = StrokeCap.Round)
                        drawLine(Color.White.copy(alpha = 0.4f), Offset(size.width * 0.2f, size.height * 0.4f), Offset(size.width / 2, 0f), strokeWidth = 1.dp.toPx(), cap = StrokeCap.Round)
                        drawLine(Color.White.copy(alpha = 0.4f), Offset(size.width * 0.8f, size.height * 0.4f), Offset(size.width / 2, 0f), strokeWidth = 1.dp.toPx(), cap = StrokeCap.Round)
                    }
                    Spacer(Modifier.width(6.dp))
                    Text("Swipe up for next card", textAlign = TextAlign.Center, color = Color.White.copy(0.35f), fontSize = 12.sp)
                }
            }

            // Right sidebar actions (Save/Share)
            Spacer(Modifier.height(8.dp))
            if (!isAnswered) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    // Bookmark icon
                    Surface(
                        modifier = Modifier.size(36.dp),
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.06f),
                        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.1f))
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Canvas(modifier = Modifier.size(14.dp)) {
                                val s = size.width
                                val path = Path().apply {
                                    moveTo(s * 0.2f, 1.dp.toPx())
                                    lineTo(s * 0.8f, 1.dp.toPx())
                                    lineTo(s * 0.8f, s - 1.dp.toPx())
                                    lineTo(s * 0.5f, s * 0.7f)
                                    lineTo(s * 0.2f, s - 1.dp.toPx())
                                    close()
                                }
                                drawPath(path, Color.White.copy(alpha = 0.5f), style = Stroke(1.2.dp.toPx(), cap = StrokeCap.Round))
                            }
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    // Share icon
                    Surface(
                        modifier = Modifier.size(36.dp),
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.06f),
                        border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.1f))
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Canvas(modifier = Modifier.size(14.dp)) {
                                val s = size.width
                                // Share arrow
                                drawLine(Color.White.copy(alpha = 0.5f), Offset(s * 0.5f, s * 0.1f), Offset(s * 0.5f, s * 0.7f), strokeWidth = 1.2.dp.toPx(), cap = StrokeCap.Round)
                                drawLine(Color.White.copy(alpha = 0.5f), Offset(s * 0.25f, s * 0.35f), Offset(s * 0.5f, s * 0.1f), strokeWidth = 1.2.dp.toPx(), cap = StrokeCap.Round)
                                drawLine(Color.White.copy(alpha = 0.5f), Offset(s * 0.75f, s * 0.35f), Offset(s * 0.5f, s * 0.1f), strokeWidth = 1.2.dp.toPx(), cap = StrokeCap.Round)
                                // Base
                                val basePath = Path().apply {
                                    moveTo(s * 0.15f, s * 0.5f)
                                    lineTo(s * 0.15f, s * 0.9f)
                                    lineTo(s * 0.85f, s * 0.9f)
                                    lineTo(s * 0.85f, s * 0.5f)
                                }
                                drawPath(basePath, Color.White.copy(alpha = 0.5f), style = Stroke(1.2.dp.toPx(), cap = StrokeCap.Round))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// QUIZ SECTION (glassmorphic selection buttons)
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizSection(
    quiz: QuizDto,
    selectedAnswer: Int?,
    isAnswered: Boolean,
    accent: Color,
    onOptionClick: (Int) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
        border = BorderStroke(0.5.dp, accent.copy(alpha = 0.15f))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(quiz.question, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Spacer(Modifier.height(10.dp))
            quiz.options.forEachIndexed { index, option ->
                val isSelected = selectedAnswer == index
                val isCorrect = index == quiz.correct_answer_index
                val bgColor = when {
                    isAnswered && isCorrect -> Color(0xFF00E5A0).copy(alpha = 0.15f)
                    isAnswered && isSelected && !isCorrect -> Color(0xFFFF5252).copy(alpha = 0.15f)
                    isSelected -> accent.copy(0.2f)
                    else -> Color.White.copy(0.04f)
                }
                val borderColor = when {
                    isAnswered && isCorrect -> Color(0xFF00E5A0).copy(alpha = 0.4f)
                    isAnswered && isSelected && !isCorrect -> Color(0xFFFF5252).copy(alpha = 0.4f)
                    isSelected -> accent.copy(0.3f)
                    else -> Color.White.copy(0.06f)
                }
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = bgColor,
                    border = BorderStroke(0.5.dp, borderColor),
                    onClick = { onOptionClick(index) },
                    enabled = !isAnswered
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Letter badge
                        Surface(
                            modifier = Modifier.size(24.dp),
                            shape = CircleShape,
                            color = if (isSelected) accent.copy(0.2f) else Color.White.copy(0.08f),
                            border = BorderStroke(0.5.dp, if (isSelected) accent.copy(0.3f) else Color.White.copy(0.1f))
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Text(
                                    "${'A' + index}",
                                    color = if (isSelected) accent else Color.White.copy(0.6f),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                        Spacer(Modifier.width(10.dp))
                        Text(option, color = Color.White.copy(0.85f), fontSize = 13.sp, modifier = Modifier.weight(1f))
                        if (isAnswered && isCorrect) {
                            Canvas(modifier = Modifier.size(16.dp)) {
                                val s = size.width
                                drawLine(Color(0xFF00E5A0), Offset(s * 0.2f, s * 0.5f), Offset(s * 0.4f, s * 0.7f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                                drawLine(Color(0xFF00E5A0), Offset(s * 0.4f, s * 0.7f), Offset(s * 0.8f, s * 0.25f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                            }
                        }
                        if (isAnswered && isSelected && !isCorrect) {
                            Canvas(modifier = Modifier.size(16.dp)) {
                                val s = size.width
                                drawLine(Color(0xFFFF5252), Offset(s * 0.25f, s * 0.25f), Offset(s * 0.75f, s * 0.75f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                                drawLine(Color(0xFFFF5252), Offset(s * 0.75f, s * 0.25f), Offset(s * 0.25f, s * 0.75f), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                            }
                        }
                    }
                }
            }
        }
    }
}
