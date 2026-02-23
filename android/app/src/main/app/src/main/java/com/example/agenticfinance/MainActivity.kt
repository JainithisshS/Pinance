package com.example.agenticfinance

import android.content.Intent
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Home
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.time.YearMonth

class MainActivity : ComponentActivity() {
    
    private lateinit var authManager: AuthManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize AuthManager
        authManager = AuthManager(this)
        
        setContent {
            val isAuthenticated = remember { mutableStateOf(authManager.isSignedIn()) }
            
            if (isAuthenticated.value) {
                // User is signed in, show main app
                AgenticFinanceApp(
                    authManager = authManager,
                    onSignOut = {
                        authManager.signOut()
                        isAuthenticated.value = false
                    }
                )
            } else {
                // User is not signed in, show login screen
                LoginScreen(
                    authManager = authManager,
                    onLoginSuccess = {
                        isAuthenticated.value = true
                    }
                )
            }
        }
    }
}

@Composable
fun AgenticFinanceApp(
    authManager: AuthManager,
    onSignOut: () -> Unit
) {
    val context = LocalContext.current
    var profile by remember {
        mutableStateOf(
            loadUserProfile(context) ?: UserProfile(
                name = "Learner",
                occupation = "Student",
                email = null,
                monthlyIncome = 0.0,
                monthlyExpenses = 0.0,
                monthlySavings = 0.0,
                notificationsEnabled = true,
                acceptedDisclaimer = true
            )
        )
    }
    var currentScreen by remember { mutableStateOf("dashboard") }

    // ══ Shared data cache (persists across tab switches) ══
    var sharedAnalysis by remember { mutableStateOf<FinanceAnalysisResponseDto?>(null) }
    var sharedNewsOverview by remember { mutableStateOf<NewsAnalysisResponseDto?>(null) }
    var sharedTopicAnalysis by remember { mutableStateOf<NewsAnalysisResponseDto?>(null) }
    var sharedNewsFeed by remember { mutableStateOf<List<NewsFeedArticleDto>>(emptyList()) }
    var sharedMarketIndices by remember { mutableStateOf<List<MarketIndexDto>>(emptyList()) }
    var sharedSectorIndices by remember { mutableStateOf<List<SectorIndexDto>>(emptyList()) }
    var sharedDataLoading by remember { mutableStateOf(true) }

    // ══ Analysis-specific shared data ══
    var sharedBreakdown by remember { mutableStateOf<List<ExpenseCategorySummaryDto>>(emptyList()) }
    var sharedPrevAnalysis by remember { mutableStateOf<FinanceAnalysisResponseDto?>(null) }
    var sharedAnalysisLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try {
            val today = java.time.LocalDate.now()
            val start = today.withDayOfMonth(1).toString()
            val end = today.toString()
            coroutineScope {
                val dAnalysis = async { try { ApiClient.api.analyzeFinance(FinanceAnalysisRequestDto(start_date = start, end_date = end)) } catch (_: Exception) { null } }
                val dNewsHome = async { try { ApiClient.api.analyzeNews(NewsAnalysisRequestDto(topic = "NIFTY 50")) } catch (_: Exception) { null } }
                val dTopicNews = async { try { ApiClient.api.analyzeNews(NewsAnalysisRequestDto(topic = "MARKETS")) } catch (_: Exception) { null } }
                val dFeed = async { try { ApiClient.api.getNewsFeed() } catch (_: Exception) { emptyList<NewsFeedArticleDto>() } }
                val dMarket = async { try { ApiClient.api.getMarketData() } catch (_: Exception) { null as MarketDataDto? } }
                sharedAnalysis = dAnalysis.await()
                sharedNewsOverview = dNewsHome.await()
                sharedTopicAnalysis = dTopicNews.await()
                sharedNewsFeed = dFeed.await()
                val mkt = dMarket.await()
                if (mkt != null) { sharedMarketIndices = mkt.indices; sharedSectorIndices = mkt.sectors }

                // Analysis-specific fetches
                val dBreakdown = async { try { ApiClient.api.expenseBreakdown(FinanceAnalysisRequestDto(start_date = start, end_date = end)) } catch (_: Exception) { emptyList<ExpenseCategorySummaryDto>() } }
                val prevEnd = today.withDayOfMonth(1).minusDays(1)
                val prevStart = prevEnd.withDayOfMonth(1)
                val dPrev = async { try { ApiClient.api.analyzeFinance(FinanceAnalysisRequestDto(start_date = prevStart.toString(), end_date = prevEnd.toString())) } catch (_: Exception) { null as FinanceAnalysisResponseDto? } }
                sharedBreakdown = dBreakdown.await()
                sharedPrevAnalysis = dPrev.await()
            }
        } catch (_: Exception) {}
        sharedDataLoading = false
        sharedAnalysisLoading = false
    }

    // Check notification permission
    var showNotificationDialog by remember { mutableStateOf(false) }
    
    LaunchedEffect(Unit) {
        // Check if notification access is enabled
        if (!NotificationPermissionHelper.isNotificationAccessEnabled(context)) {
            showNotificationDialog = true
        }
    }
    
    // Notification permission dialog
    if (showNotificationDialog) {
        AlertDialog(
            onDismissRequest = { showNotificationDialog = false },
            title = { 
                Text(
                    "Enable SMS Notifications",
                    fontWeight = FontWeight.Bold
                ) 
            },
            text = { 
                Text(
                    "To automatically track your transactions from SMS, please enable notification access for this app.\n\n" +
                    "This allows the app to read banking SMS and parse transaction details.",
                    fontSize = 14.sp
                ) 
            },
            confirmButton = {
                Button(
                    onClick = {
                        NotificationPermissionHelper.openNotificationSettings(context)
                        showNotificationDialog = false
                    }
                ) {
                    Text("Enable Now")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNotificationDialog = false }) {
                    Text("Later")
                }
            }
        )
    }


    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF0A0E1A),
                contentColor = Color.White,
                tonalElevation = 0.dp
            ) {
                NavigationBarItem(
                    selected = currentScreen == "dashboard",
                    onClick = { currentScreen = "dashboard" },
                    label = { Text("Home", fontSize = 10.sp) },
                    icon = { Icon(if (currentScreen == "dashboard") Icons.Filled.Home else Icons.Outlined.Home, contentDescription = "Home", modifier = Modifier.size(24.dp)) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF00E5A0),
                        selectedTextColor = Color(0xFF00E5A0),
                        unselectedIconColor = Color(0xFF5A6177),
                        unselectedTextColor = Color(0xFF5A6177),
                        indicatorColor = Color(0xFF00E5A0).copy(alpha = 0.12f)
                    )
                )
                NavigationBarItem(
                    selected = currentScreen == "news",
                    onClick = { currentScreen = "news" },
                    label = { Text("News", fontSize = 10.sp) },
                    icon = {
                        val c = LocalContentColor.current
                        Canvas(modifier = Modifier.size(22.dp)) {
                            val s = 1.6.dp.toPx()
                            drawRoundRect(c, Offset(3.dp.toPx(), 1.dp.toPx()), Size(16.dp.toPx(), 20.dp.toPx()), CornerRadius(2.5.dp.toPx()), style = Stroke(s))
                            drawLine(c, Offset(6.dp.toPx(), 6.dp.toPx()), Offset(16.dp.toPx(), 6.dp.toPx()), s)
                            drawLine(c, Offset(6.dp.toPx(), 10.dp.toPx()), Offset(16.dp.toPx(), 10.dp.toPx()), s)
                            drawLine(c, Offset(6.dp.toPx(), 14.dp.toPx()), Offset(12.dp.toPx(), 14.dp.toPx()), s)
                        }
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF00E5A0),
                        selectedTextColor = Color(0xFF00E5A0),
                        unselectedIconColor = Color(0xFF5A6177),
                        unselectedTextColor = Color(0xFF5A6177),
                        indicatorColor = Color(0xFF00E5A0).copy(alpha = 0.12f)
                    )
                )
                NavigationBarItem(
                    selected = currentScreen == "analysis",
                    onClick = { currentScreen = "analysis" },
                    label = { Text("Analysis", fontSize = 10.sp) },
                    icon = {
                        val c = LocalContentColor.current
                        Canvas(modifier = Modifier.size(22.dp)) {
                            val bw = 3.5.dp.toPx()
                            drawLine(c, Offset(5.dp.toPx(), 18.dp.toPx()), Offset(5.dp.toPx(), 13.dp.toPx()), bw, cap = StrokeCap.Round)
                            drawLine(c, Offset(11.dp.toPx(), 18.dp.toPx()), Offset(11.dp.toPx(), 5.dp.toPx()), bw, cap = StrokeCap.Round)
                            drawLine(c, Offset(17.dp.toPx(), 18.dp.toPx()), Offset(17.dp.toPx(), 9.dp.toPx()), bw, cap = StrokeCap.Round)
                        }
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF00E5A0),
                        selectedTextColor = Color(0xFF00E5A0),
                        unselectedIconColor = Color(0xFF5A6177),
                        unselectedTextColor = Color(0xFF5A6177),
                        indicatorColor = Color(0xFF00E5A0).copy(alpha = 0.12f)
                    )
                )
                NavigationBarItem(
                    selected = currentScreen == "chatHub" || currentScreen == "chat" || currentScreen == "whatif",
                    onClick = { currentScreen = "chatHub" },
                    label = { Text("Chat", fontSize = 10.sp) },
                    icon = {
                        val c = LocalContentColor.current
                        Canvas(modifier = Modifier.size(22.dp)) {
                            val s = 1.6.dp.toPx()
                            drawRoundRect(c, Offset(2.dp.toPx(), 2.dp.toPx()), Size(18.dp.toPx(), 13.dp.toPx()), CornerRadius(3.dp.toPx()), style = Stroke(s))
                            drawLine(c, Offset(6.dp.toPx(), 15.dp.toPx()), Offset(4.dp.toPx(), 20.dp.toPx()), s, cap = StrokeCap.Round)
                            drawLine(c, Offset(4.dp.toPx(), 20.dp.toPx()), Offset(11.dp.toPx(), 15.dp.toPx()), s, cap = StrokeCap.Round)
                        }
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF00E5A0),
                        selectedTextColor = Color(0xFF00E5A0),
                        unselectedIconColor = Color(0xFF5A6177),
                        unselectedTextColor = Color(0xFF5A6177),
                        indicatorColor = Color(0xFF00E5A0).copy(alpha = 0.12f)
                    )
                )
                NavigationBarItem(
                    selected = currentScreen == "learn",
                    onClick = { currentScreen = "learn" },
                    label = { Text("Learn", fontSize = 10.sp) },
                    icon = {
                        val c = LocalContentColor.current
                        Canvas(modifier = Modifier.size(22.dp)) {
                            val s = 1.6.dp.toPx()
                            drawLine(c, Offset(11.dp.toPx(), 3.dp.toPx()), Offset(11.dp.toPx(), 19.dp.toPx()), s)
                            drawRoundRect(c, Offset(1.dp.toPx(), 3.dp.toPx()), Size(10.dp.toPx(), 16.dp.toPx()), CornerRadius(2.dp.toPx()), style = Stroke(s))
                            drawRoundRect(c, Offset(11.dp.toPx(), 3.dp.toPx()), Size(10.dp.toPx(), 16.dp.toPx()), CornerRadius(2.dp.toPx()), style = Stroke(s))
                        }
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFF00E5A0),
                        selectedTextColor = Color(0xFF00E5A0),
                        unselectedIconColor = Color(0xFF5A6177),
                        unselectedTextColor = Color(0xFF5A6177),
                        indicatorColor = Color(0xFF00E5A0).copy(alpha = 0.12f)
                    )
                )
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (currentScreen) {
                "dashboard" -> MainDashboardScreen(
                    authManager = authManager, profile = profile,
                    onNavigate = { target -> currentScreen = target }, onSignOut = onSignOut,
                    analysis = sharedAnalysis, newsOverview = sharedNewsOverview,
                    newsFeed = sharedNewsFeed, marketIndices = sharedMarketIndices,
                    sectorIndices = sharedSectorIndices, isDataLoading = sharedDataLoading
                )
                "news" -> NewsScreen(
                    topicAnalysis = sharedTopicAnalysis, newsFeed = sharedNewsFeed,
                    marketIndices = sharedMarketIndices, sectorIndices = sharedSectorIndices,
                    isDataLoading = sharedDataLoading
                )
                "analysis" -> FinanceDashboardScreen(
                    authManager = authManager,
                    analysis = sharedAnalysis,
                    breakdown = sharedBreakdown,
                    previousMonthAnalysis = sharedPrevAnalysis,
                    isPreloaded = !sharedAnalysisLoading
                )
                "chatHub" -> ChatHubScreen(onNavigate = { target -> currentScreen = target })
                "chat" -> AgentChatScreen(onBack = { currentScreen = "chatHub" })
                "whatif" -> WhatIfSimulationScreen(onBack = { currentScreen = "chatHub" })
                "learn" -> {
                    ReelsStyleLearningScreen(
                        onBackClick = { currentScreen = "dashboard" }
                    )
                }

            }
        }
    }
}

@Composable
fun MainDashboardScreen(
    authManager: AuthManager, profile: UserProfile,
    onNavigate: (String) -> Unit, onSignOut: () -> Unit,
    analysis: FinanceAnalysisResponseDto?,
    newsOverview: NewsAnalysisResponseDto?,
    newsFeed: List<NewsFeedArticleDto>,
    marketIndices: List<MarketIndexDto>,
    sectorIndices: List<SectorIndexDto>,
    isDataLoading: Boolean
) {
    // ── Colour palette ──
    val bgDark       = Color(0xFF0B0F1E)
    val cardDark     = Color(0xFF131831)
    val cardDarkAlt  = Color(0xFF171E38)
    val neonCyan     = Color(0xFF00E5FF)
    val neonGreen    = Color(0xFF00E5A0)
    val neonPurple   = Color(0xFF7C4DFF)
    val softWhite    = Color(0xFFE8ECF4)
    val dimWhite     = Color(0xFF8892A8)
    val glowBrush    = Brush.linearGradient(listOf(neonCyan, neonGreen))

    val isLoading = isDataLoading
    var error by remember { mutableStateOf<String?>(null) }
    var showProfileMenu by remember { mutableStateOf(false) }

    // Gauge animation — use ML confidence when available, matching Analysis screen
    val riskScore = if (analysis?.ml_risk_confidence != null) (analysis!!.ml_risk_confidence!! * 100).toFloat() else
        when (analysis?.risk_level?.lowercase()) { "high" -> 85f; "medium" -> 55f; else -> 25f }
    val animatedGauge by animateFloatAsState(
        targetValue = riskScore,
        animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        label = "gauge"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgDark)
            .verticalScroll(rememberScrollState())
    ) {

        // ═══════════════ USER GREETING ═══════════════
        val userName = authManager.getCurrentUser()?.displayName ?: profile.name
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Hello, $userName",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = softWhite
                )
                Text(
                    "Your financial overview",
                    fontSize = 13.sp,
                    color = dimWhite
                )
            }
            // Profile avatar circle with dropdown
            Box {
                Surface(
                    modifier = Modifier.size(42.dp).clickable { showProfileMenu = true },
                    shape = CircleShape,
                    color = neonCyan.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, neonCyan.copy(alpha = 0.3f))
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = userName.take(1).uppercase(),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = neonCyan
                        )
                    }
                }
                androidx.compose.material3.DropdownMenu(
                    expanded = showProfileMenu,
                    onDismissRequest = { showProfileMenu = false },
                    modifier = Modifier.background(Color(0xFF1A2040))
                ) {
                    // User info header
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(userName, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        val email = authManager.getCurrentUser()?.email ?: ""
                        if (email.isNotBlank()) {
                            Text(email, fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
                        }
                    }
                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text("Sign Out", color = Color(0xFFFF5252), fontSize = 14.sp) },
                        onClick = { showProfileMenu = false; onSignOut() },
                        leadingIcon = { Text("❌", fontSize = 14.sp) }
                    )
                }
            }
        }


        // ═══════════════ FINANCIAL RISK + MARKET SENTIMENT ROW ═══════════════
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── Financial Risk Gauge ──
            Card(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onNavigate("analysis") },
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = cardDarkAlt),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        "FINANCIAL RISK",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = softWhite,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(12.dp))

                    // Speedometer gauge
                    Box(
                        modifier = Modifier.size(100.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Canvas(modifier = Modifier.size(100.dp)) {
                            val sweepTotal = 180f
                            val startAng = 180f
                            val cx = size.width / 2f
                            val cy = size.height * 0.6f
                            val radius = size.width * 0.42f

                            // Background arc segments (red -> yellow -> green from left to right)
                            val segments = listOf(
                                Color(0xFFFF4C4C) to 60f,
                                Color(0xFFFFAA00) to 60f,
                                Color(0xFF00E5A0) to 60f
                            )
                            var segStart = startAng
                            segments.forEach { (color, sweep) ->
                                drawArc(
                                    color = color.copy(alpha = 0.25f),
                                    startAngle = segStart,
                                    sweepAngle = sweep,
                                    useCenter = false,
                                    topLeft = Offset(cx - radius, cy - radius),
                                    size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                                    style = Stroke(width = 10f, cap = StrokeCap.Butt)
                                )
                                segStart += sweep
                            }

                            // Active arc up to needle position
                            val needleAngle = startAng + (animatedGauge / 100f) * sweepTotal
                            val activeColor = when {
                                animatedGauge > 66 -> Color(0xFFFF4C4C)
                                animatedGauge > 33 -> Color(0xFFFFAA00)
                                else -> Color(0xFF00E5A0)
                            }
                            drawArc(
                                color = activeColor,
                                startAngle = startAng,
                                sweepAngle = (animatedGauge / 100f) * sweepTotal,
                                useCenter = false,
                                topLeft = Offset(cx - radius, cy - radius),
                                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                                style = Stroke(width = 10f, cap = StrokeCap.Round)
                            )

                            // Needle
                            val needleRad = Math.toRadians(needleAngle.toDouble())
                            val needleLen = radius * 0.7f
                            val nx = cx + (needleLen * kotlin.math.cos(needleRad)).toFloat()
                            val ny = cy + (needleLen * kotlin.math.sin(needleRad)).toFloat()
                            drawLine(
                                color = Color.White,
                                start = Offset(cx, cy),
                                end = Offset(nx, ny),
                                strokeWidth = 3f,
                                cap = StrokeCap.Round
                            )
                            // Needle center dot
                            drawCircle(color = Color.White, radius = 5f, center = Offset(cx, cy))
                            drawCircle(color = activeColor, radius = 3f, center = Offset(cx, cy))
                        }

                        // Risk label under gauge
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(bottom = 2.dp)
                        ) {
                            val riskLabel = analysis?.risk_level?.uppercase() ?: "LOW"
                            val riskColor = when (analysis?.risk_level?.lowercase()) {
                                "high" -> Color(0xFFFF4C4C)
                                "medium" -> Color(0xFFFFAA00)
                                else -> neonGreen
                            }
                            Text(
                                riskLabel,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = riskColor
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))
                    Text(
                        "SCORE: ${animatedGauge.toInt()}/100",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = dimWhite
                    )
                }
            }

            // ── Live Market ──
            Card(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onNavigate("news") },
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = cardDarkAlt),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
            ) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        "LIVE MARKET",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = softWhite,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(10.dp))

                    if (marketIndices.isNotEmpty()) {
                        marketIndices.forEach { idx ->
                            val pctColor = if (idx.change_percent >= 0) Color(0xFF00E676) else Color(0xFFFF5252)
                            val arrow = if (idx.change_percent >= 0) "\u25B2" else "\u25BC"
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    idx.name,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = dimWhite,
                                    letterSpacing = 0.5.sp
                                )
                                Text(
                                    "\u20B9${"%,.0f".format(idx.price)}",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "$arrow ${"%+.2f".format(idx.change_percent)}%",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = pctColor
                                    )
                                    Text(
                                        "  (${"%+.0f".format(idx.change)})",
                                        fontSize = 11.sp,
                                        color = pctColor.copy(alpha = 0.7f)
                                    )
                                }
                            }
                            if (idx != marketIndices.last()) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(vertical = 4.dp),
                                    color = Color.White.copy(alpha = 0.06f)
                                )
                            }
                        }
                    } else {
                        CircularProgressIndicator(
                            color = neonCyan,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(30.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("Loading...", fontSize = 11.sp, color = dimWhite)
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ═══════════════ AGENTIC BRAIN CARD ═══════════════
        val brainPulse = rememberInfiniteTransition(label = "brainPulse")
        val pulseAlpha by brainPulse.animateFloat(
            initialValue = 0.15f, targetValue = 0.45f,
            animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "pulseA"
        )
        val pulseScale by brainPulse.animateFloat(
            initialValue = 0.92f, targetValue = 1.08f,
            animationSpec = infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "pulseS"
        )

        // Dynamic recommendation from risk + sentiment
        val sentimentTag = when {
            newsOverview?.overall_sentiment?.contains("pos", true) == true -> "bullish"
            newsOverview?.overall_sentiment?.contains("neg", true) == true -> "bearish"
            else -> "neutral"
        }
        val recText = if (analysis != null) {
            val cat = analysis!!.summary.top_category ?: "spending"
            val riskLvl = analysis!!.risk_level.lowercase()
            val expenseStr = "\u20B9${String.format("%,.0f", analysis!!.summary.total_expenses)}"
            val savingsRate = if (analysis!!.summary.total_income > 0) {
                ((analysis!!.summary.total_income - analysis!!.summary.total_expenses) / analysis!!.summary.total_income * 100).toInt()
            } else 0
            when {
                riskLvl == "high" && sentimentTag == "bearish" -> "\u26A0\uFE0F You\u2019ve spent $expenseStr this month\u200A\u2014\u200Amostly on $cat. Markets are down too. Try cutting back on $cat for a week."
                riskLvl == "high" -> "Your $cat spending hit $expenseStr. Move \u20B95,000 to your emergency fund this week."
                riskLvl == "medium" && sentimentTag == "bullish" -> "Spent $expenseStr so far. Markets are up \u2014 great time to bump your SIP by 10-15%."
                riskLvl == "medium" -> "You\u2019re at $expenseStr this month. Try trimming $cat to save \u20B92K more."
                savingsRate >= 30 && sentimentTag == "bullish" -> "Great job saving ${savingsRate}% of income! Markets look strong \u2014 consider adding \u20B92K to your SIP."
                savingsRate >= 30 && sentimentTag == "bearish" -> "You\u2019re saving ${savingsRate}%\u200A\u2014\u200Anice! Markets dipping, hold steady and avoid new lump-sum buys."
                sentimentTag == "bullish" -> "Finances on track ($expenseStr spent). Market\u2019s bullish \u2014 boost your SIP by 10%."
                sentimentTag == "bearish" -> "Spending looks fine ($expenseStr). Bearish signals \u2014 hold positions, skip lump-sum for now."
                else -> "All good! Spent $expenseStr with ${savingsRate}% savings rate. Keep it going."
            }
        } else {
            "Crunching your numbers\u2026 hang tight!"
        }
        val recColor = when (sentimentTag) {
            "bullish" -> neonGreen
            "bearish" -> Color(0xFFFF5252)
            else -> neonCyan
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .clickable { onNavigate("chat") },
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = cardDark),
            border = BorderStroke(1.dp, neonCyan.copy(alpha = 0.12f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ── Pulsing AI icon ──
                Box(
                    modifier = Modifier.size(56.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(56.dp)) {
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val r = size.width * 0.38f
                        // Outer pulse ring
                        drawCircle(
                            color = neonCyan.copy(alpha = pulseAlpha * 0.4f),
                            radius = r * pulseScale + 6.dp.toPx(),
                            center = Offset(cx, cy),
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                        // Inner filled circle
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(neonCyan.copy(alpha = 0.25f), Color.Transparent),
                                center = Offset(cx, cy),
                                radius = r * 1.2f
                            ),
                            radius = r,
                            center = Offset(cx, cy)
                        )
                        // Core ring
                        drawCircle(
                            color = neonCyan.copy(alpha = 0.6f),
                            radius = r * 0.7f,
                            center = Offset(cx, cy),
                            style = Stroke(width = 2.dp.toPx())
                        )
                        // Center dot
                        drawCircle(
                            color = neonCyan,
                            radius = r * 0.22f,
                            center = Offset(cx, cy)
                        )
                        // Neural spokes
                        for (i in 0 until 6) {
                            val angle = Math.toRadians((i * 60.0) - 90.0)
                            val inner = r * 0.45f
                            val outer = r * 0.85f
                            drawLine(
                                color = neonCyan.copy(alpha = 0.35f),
                                start = Offset(cx + (inner * kotlin.math.cos(angle)).toFloat(), cy + (inner * kotlin.math.sin(angle)).toFloat()),
                                end = Offset(cx + (outer * kotlin.math.cos(angle)).toFloat(), cy + (outer * kotlin.math.sin(angle)).toFloat()),
                                strokeWidth = 1.5.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }

                Spacer(Modifier.width(14.dp))

                // ── Text content ──
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "AGENTIC BRAIN",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = softWhite,
                            letterSpacing = 1.5.sp
                        )
                        Spacer(Modifier.width(8.dp))
                        // Status dot
                        Canvas(modifier = Modifier.size(7.dp)) {
                            drawCircle(color = recColor, radius = size.width / 2f)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = recText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = dimWhite,
                        lineHeight = 17.sp,
                        maxLines = 3
                    )
                }

                Spacer(Modifier.width(8.dp))
                // Arrow indicator
                Text("\u203A", fontSize = 22.sp, color = dimWhite)
            }
        }

        Spacer(Modifier.height(16.dp))

        // ═══════════════ NEWS PULSE ═══════════════
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = cardDark),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
        ) {
            Column(modifier = Modifier.padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "NEWS PULSE",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = softWhite,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "›",
                        fontSize = 24.sp,
                        color = dimWhite,
                        modifier = Modifier.clickable { onNavigate("news") }
                    )
                }
                Spacer(Modifier.height(12.dp))

                // Horizontal scrolling news cards
                val newsItems = if (newsFeed.isNotEmpty()) {
                    newsFeed.take(6)
                } else {
                    // Fallback placeholder items
                    listOf(
                        NewsFeedArticleDto(1, "GLOBAL AI SUMMIT INFLUENCES MARKETS", "Reuters", "AI and tech sectors see major movement", null, null),
                        NewsFeedArticleDto(2, "INTEREST RATE HIKE EXPECTED", "Bloomberg", "Central banks signal tightening policy", null, null),
                        NewsFeedArticleDto(3, "RENEWABLE ENERGY STOCKS SURGE", "CNBC", "Green energy sector leads gains", null, null)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    newsItems.forEach { article ->
                        Card(
                            modifier = Modifier
                                .width(180.dp)
                                .clickable { onNavigate("news") },
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = bgDark)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = article.title.uppercase(),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = 15.sp
                                )
                                Spacer(Modifier.height(8.dp))
                                // Sentiment badge
                                val badgeColor: Color
                                val badgeText: String
                                val titleLower = article.title.lowercase()
                                when {
                                    titleLower.contains("surge") || titleLower.contains("gain") || titleLower.contains("bull") || titleLower.contains("grow") || titleLower.contains("summit") -> {
                                        badgeColor = neonGreen; badgeText = "POSITIVE"
                                    }
                                    titleLower.contains("drop") || titleLower.contains("fall") || titleLower.contains("crash") || titleLower.contains("bear") || titleLower.contains("loss") -> {
                                        badgeColor = Color(0xFFFF4C4C); badgeText = "NEGATIVE"
                                    }
                                    else -> {
                                        badgeColor = Color(0xFFFFAA00); badgeText = "NEUTRAL"
                                    }
                                }
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = badgeColor.copy(alpha = 0.2f)
                                ) {
                                    Text(
                                        badgeText,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = badgeColor,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))


        // ═══════════════ SCENARIO INTELLIGENCE TILE ═══════════════
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clickable { onNavigate("whatif") },
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = cardDark),
            border = BorderStroke(1.dp, Color(0xFFBB86FC).copy(alpha = 0.15f))
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Dual-path icon (Canvas)
                    Canvas(modifier = Modifier.size(22.dp)) {
                        val s = size.width
                        // Forking paths
                        val basePath = Path().apply {
                            moveTo(2.dp.toPx(), s * 0.5f)
                            lineTo(s * 0.35f, s * 0.5f)
                        }
                        drawPath(basePath, Color.White.copy(alpha = 0.5f), style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
                        // Upper path (projected)
                        val upperPath = Path().apply {
                            moveTo(s * 0.35f, s * 0.5f)
                            lineTo(s * 0.55f, s * 0.3f)
                            lineTo(s - 2.dp.toPx(), s * 0.2f)
                        }
                        drawPath(upperPath, Color(0xFFBB86FC), style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
                        // Lower path (current)
                        val lowerPath = Path().apply {
                            moveTo(s * 0.35f, s * 0.5f)
                            lineTo(s * 0.55f, s * 0.65f)
                            lineTo(s - 2.dp.toPx(), s * 0.75f)
                        }
                        drawPath(lowerPath, Color.White.copy(alpha = 0.3f), style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "SCENARIO PLANNER",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = softWhite,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.weight(1f))
                    // Arrow
                    Canvas(modifier = Modifier.size(16.dp)) {
                        val s = size.width
                        drawLine(Color.White.copy(alpha = 0.4f), Offset(2.dp.toPx(), s / 2), Offset(s - 2.dp.toPx(), s / 2), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
                        drawLine(Color.White.copy(alpha = 0.4f), Offset(s * 0.6f, s * 0.2f), Offset(s - 2.dp.toPx(), s / 2), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
                        drawLine(Color.White.copy(alpha = 0.4f), Offset(s * 0.6f, s * 0.8f), Offset(s - 2.dp.toPx(), s / 2), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Simulate income changes, spending shifts, SIP investments, and market conditions to see your projected financial future.",
                    fontSize = 12.sp,
                    color = dimWhite,
                    lineHeight = 17.sp
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Mini stat chips
                    listOf(
                        Triple("Wealth", "5yr", Color(0xFF00E5A0)),
                        Triple("Risk", "Live", Color(0xFFBB86FC)),
                        Triple("SIP", "Sim", Color(0xFF448AFF))
                    ).forEach { (label, sub, color) ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = color.copy(alpha = 0.08f),
                            border = BorderStroke(0.5.dp, color.copy(alpha = 0.2f))
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = color)
                                Text(sub, fontSize = 9.sp, color = color.copy(alpha = 0.6f))
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ═══════════════ DAILY INSIGHT FLASH CARD ═══════════════
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clickable { onNavigate("learn") },
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = cardDark),
            border = BorderStroke(1.dp, neonPurple.copy(alpha = 0.15f))
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Book/learn icon (Canvas line-art)
                    Canvas(modifier = Modifier.size(22.dp)) {
                        val s = size.width
                        // Open book shape
                        val leftPage = Path().apply {
                            moveTo(s / 2, s * 0.15f)
                            lineTo(s * 0.05f, s * 0.2f)
                            lineTo(s * 0.05f, s * 0.85f)
                            lineTo(s / 2, s * 0.9f)
                        }
                        drawPath(leftPage, neonPurple.copy(alpha = 0.6f), style = Stroke(1.2f.dp.toPx(), cap = StrokeCap.Round))
                        val rightPage = Path().apply {
                            moveTo(s / 2, s * 0.15f)
                            lineTo(s * 0.95f, s * 0.2f)
                            lineTo(s * 0.95f, s * 0.85f)
                            lineTo(s / 2, s * 0.9f)
                        }
                        drawPath(rightPage, neonPurple.copy(alpha = 0.6f), style = Stroke(1.2f.dp.toPx(), cap = StrokeCap.Round))
                        // Spine
                        drawLine(neonPurple.copy(alpha = 0.4f), Offset(s / 2, s * 0.15f), Offset(s / 2, s * 0.9f), strokeWidth = 1.dp.toPx())
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "DAILY INSIGHT",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = softWhite,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.weight(1f))
                    // Streak badge
                    if (LearningProgress.streak > 0) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFFFFAB40).copy(alpha = 0.1f),
                            border = BorderStroke(0.5.dp, Color(0xFFFFAB40).copy(alpha = 0.2f))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Canvas(modifier = Modifier.size(10.dp)) {
                                    drawCircle(Color(0xFFFFAB40), radius = 4.dp.toPx())
                                    drawCircle(Color.White.copy(alpha = 0.5f), radius = 1.5.dp.toPx())
                                }
                                Spacer(Modifier.width(4.dp))
                                Text("${LearningProgress.streak}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFAB40))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                // Flash card preview
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = neonPurple.copy(alpha = 0.06f)),
                    border = BorderStroke(0.5.dp, neonPurple.copy(alpha = 0.12f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            LearningProgress.dailyInsightTitle,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            LearningProgress.dailyInsightSnippet,
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.55f),
                            lineHeight = 17.sp,
                            maxLines = 2
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Stats row
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White.copy(alpha = 0.04f)
                        ) {
                            Text(
                                "${LearningProgress.masteredConcepts.size} mastered",
                                fontSize = 10.sp,
                                color = Color(0xFF00E5A0),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color.White.copy(alpha = 0.04f)
                        ) {
                            Text(
                                "${LearningProgress.xp} XP",
                                fontSize = 10.sp,
                                color = Color(0xFFFFD700),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    // Arrow to learn
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = neonPurple.copy(alpha = 0.15f),
                        border = BorderStroke(0.5.dp, neonPurple.copy(alpha = 0.3f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Learn", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = neonPurple)
                            Spacer(Modifier.width(4.dp))
                            Canvas(modifier = Modifier.size(10.dp)) {
                                val s = size.width
                                drawLine(Color(0xFFBB86FC), Offset(0f, s / 2), Offset(s, s / 2), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
                                drawLine(Color(0xFFBB86FC), Offset(s * 0.6f, s * 0.15f), Offset(s, s / 2), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
                                drawLine(Color(0xFFBB86FC), Offset(s * 0.6f, s * 0.85f), Offset(s, s / 2), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ═══════════════ AI INSIGHT CARD ═══════════════
        if (analysis != null) {
            val res = analysis!!
            val summary = res.summary
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = cardDark),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🤖", fontSize = 18.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "AI INSIGHT",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = softWhite,
                            letterSpacing = 1.sp
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    val line1 = "Spent ₹${String.format("%,.0f", summary.total_expenses)} this month — risk: ${res.risk_level.uppercase()}."
                    val line2 = if (summary.top_category != null) "Top category: ${summary.top_category}." else "Spread across categories."
                    val line3 = when (res.risk_level.lowercase()) {
                        "high" -> "Consider cutting non-essential spending."
                        "medium" -> "You're okay—watch 1-2 big categories."
                        else -> "Great! Consider increasing SIP allocation."
                    }
                    Text(line1, fontSize = 13.sp, color = softWhite, lineHeight = 18.sp)
                    Text(line2, fontSize = 13.sp, color = dimWhite, lineHeight = 18.sp)
                    Text(line3, fontSize = 13.sp, color = neonGreen, lineHeight = 18.sp)
                    if (!res.ml_risk_explanation.isNullOrBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(res.ml_risk_explanation, fontSize = 12.sp, color = dimWhite.copy(alpha = 0.8f), lineHeight = 16.sp)
                    }
                }
            }
        }

        // ═══════════════ LOADING / ERROR ═══════════════
        if (isLoading && analysis == null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = neonCyan, strokeWidth = 3.dp)
            }
        }
        error?.let {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2D1215))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Could not load data", fontWeight = FontWeight.SemiBold, color = Color(0xFFFF6B6B))
                    Text(it, fontSize = 12.sp, color = Color(0xFFFFAAAA))
                }
            }
        }

        Spacer(Modifier.height(20.dp))
    }
}

@Composable
fun FinanceDashboardScreen(
    authManager: AuthManager,
    analysis: FinanceAnalysisResponseDto? = null,
    breakdown: List<ExpenseCategorySummaryDto> = emptyList(),
    previousMonthAnalysis: FinanceAnalysisResponseDto? = null,
    isPreloaded: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Use parent-provided data or fallback to local fetch
    var localAnalysis by remember { mutableStateOf(analysis) }
    var localPrevAnalysis by remember { mutableStateOf(previousMonthAnalysis) }
    var localBreakdown by remember { mutableStateOf(breakdown) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(!isPreloaded) }
    var error by remember { mutableStateOf<String?>(null) }
    var lastUpdated by remember { mutableStateOf<String?>(null) }

    var manualEntryDialogOpen by remember { mutableStateOf(false) }
    var manualIsIncome by remember { mutableStateOf(false) }
    var manualAmount by remember { mutableStateOf("") }
    var manualCategory by remember { mutableStateOf("") }
    var manualDate by remember { mutableStateOf("") }

    // Transaction history state
    var showHistory by remember { mutableStateOf(false) }
    var transactionHistory by remember { mutableStateOf<List<TransactionDto>>(emptyList()) }
    var historyLoading by remember { mutableStateOf(false) }

    // Micro-learning card state (implicit curriculum-driven learning)
    var learningItem by remember { mutableStateOf<PlanItemDto?>(null) }
    var learningBeliefs by remember { mutableStateOf<Map<String, BeliefStateDto>>(emptyMap()) }
    var learningIsUpdating by remember { mutableStateOf(false) }
    var learningError by remember { mutableStateOf<String?>(null) }
    var showLearningDialog by remember { mutableStateOf(false) }

    // Sync parent data when it arrives
    LaunchedEffect(analysis, breakdown, previousMonthAnalysis) {
        if (analysis != null) localAnalysis = analysis
        if (breakdown.isNotEmpty()) localBreakdown = breakdown
        if (previousMonthAnalysis != null) localPrevAnalysis = previousMonthAnalysis
        if (isPreloaded) {
            isLoading = false
            lastUpdated = java.time.LocalDateTime.now().toString()
        }
    }

    // Only fetch if parent has not provided data
    LaunchedEffect(isPreloaded) {
        if (isPreloaded) return@LaunchedEffect
        isLoading = true
        try {
            val today = java.time.LocalDate.now()
            val start = today.withDayOfMonth(1).toString()
            val end = today.toString()
            val body = FinanceAnalysisRequestDto(start_date = start, end_date = end)
            localAnalysis = ApiClient.api.analyzeFinance(body)
            localBreakdown = ApiClient.api.expenseBreakdown(body)
            val prevEnd = today.withDayOfMonth(1).minusDays(1)
            val prevStart = prevEnd.withDayOfMonth(1)
            localPrevAnalysis = ApiClient.api.analyzeFinance(
                FinanceAnalysisRequestDto(start_date = prevStart.toString(), end_date = prevEnd.toString())
            )
            lastUpdated = java.time.LocalDateTime.now().toString()
            error = null
        } catch (e: Exception) {
            error = e.message ?: "Could not load finance dashboard."
        } finally {
            isLoading = false
        }
    }

    // Fetch learning curriculum once (lightweight call)
    LaunchedEffect(Unit) {
        try {
            val curriculum = ApiClient.api.getCurriculumPlan()
            learningItem = curriculum.plan.firstOrNull()
            learningBeliefs = curriculum.beliefs
        } catch (e: Exception) {
            learningError = e.message
        }
    }

    val notifOn = remember {
        try {
            val enabled = android.provider.Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: ""
            enabled.contains(context.packageName)
        } catch (_: Exception) {
            false
        }
    }

    // ── Color palette ──
    val bgColor = Color(0xFF060B28)
    val cardBg = Color(0xFF0F1B2E)
    val accentCyan = Color(0xFF00E5A0)
    val glowPurple = Color(0xFFBB86FC)
    val glowBlue = Color(0xFF448AFF)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // ─── Header: Hello, Name + icons ───
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("Hello,", fontSize = 14.sp, color = Color.White.copy(alpha = 0.6f))
                Text(
                    text = authManager.getCurrentUser()?.displayName ?: "User",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Manual entry button
                IconButton(onClick = {
                    manualIsIncome = false
                    manualAmount = ""
                    manualCategory = selectedCategory ?: "Misc"
                    manualDate = ""
                    manualEntryDialogOpen = true
                }) {
                    Text("✏️", fontSize = 20.sp)
                }
                // History toggle
                IconButton(onClick = {
                    showHistory = !showHistory
                    if (showHistory && transactionHistory.isEmpty()) {
                        historyLoading = true
                        scope.launch {
                            try {
                                transactionHistory = ApiClient.api.getTransactions()
                            } catch (_: Exception) {
                                transactionHistory = emptyList()
                            } finally {
                                historyLoading = false
                            }
                        }
                    }
                }) {
                    Text(if (showHistory) "📊" else "🕐", fontSize = 20.sp)
                }
            }
        }

        if (isLoading && localAnalysis == null) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = accentCyan)
            }
            return@Column
        }

        // ─── Transaction History overlay ───
        if (showHistory) {
            Text("Transaction History", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(bottom = 12.dp))
            if (historyLoading) {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = accentCyan)
                }
            } else if (transactionHistory.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("No transactions yet.", fontSize = 14.sp, color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(16.dp))
                }
            } else {
                transactionHistory.forEachIndexed { index, tx ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        colors = CardDefaults.cardColors(containerColor = if (index % 2 == 0) cardBg else Color(0xFF0A1525)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(tx.merchant ?: "Unknown", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Color.White)
                                Text("${tx.category ?: "Uncategorized"} • ${tx.timestamp}", fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
                            }
                            Text("₹${String.format("%.0f", tx.amount)}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF5252))
                        }
                    }
                }
            }
            return@Column
        }

        error?.let {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF3D0A0A)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Could not load data", fontWeight = FontWeight.SemiBold, color = Color(0xFFFF5252))
                    Text(it, fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
                }
            }
        }

        localAnalysis?.let { res ->
            val summary = res.summary
            val riskColor = when (res.risk_level.lowercase()) {
                "high" -> Color(0xFFFF5252)
                "medium" -> Color(0xFFFFA726)
                else -> Color(0xFF00E676)
            }
            val riskPercent = if (res.ml_risk_confidence != null) (res.ml_risk_confidence * 100).toInt() else
                when (res.risk_level.lowercase()) { "high" -> 85; "medium" -> 55; else -> 25 }
            val topCat = summary.top_category ?: "Other"
            val balance = summary.savings
            val balanceRate = if (summary.total_income > 0) ((balance / summary.total_income) * 100).toInt() else 0
            val dayOfMonth = java.time.LocalDate.now().dayOfMonth
            val daysInMonth = java.time.LocalDate.now().lengthOfMonth()
            val monthProgress = (dayOfMonth.toFloat() / daysInMonth * 100).toInt()
            val expectedBalanceRate = 100 - monthProgress
            val balanceVsExpected = balanceRate - expectedBalanceRate

            // ─── 1. Income / Expenses / Savings cards ───
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Income", fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("↑", fontSize = 12.sp, color = Color(0xFF00E676))
                        }
                        Canvas(modifier = Modifier.fillMaxWidth().height(24.dp).padding(vertical = 4.dp)) {
                            val w = size.width; val h = size.height
                            val pts = listOf(0.3f, 0.5f, 0.4f, 0.7f, 0.6f, 0.8f, 0.9f)
                            val path = Path()
                            pts.forEachIndexed { i, v ->
                                val x = w * i / (pts.size - 1)
                                val y = h * (1f - v)
                                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            }
                            drawPath(path, Color(0xFF00E676), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                        }
                        Text("\u20B9${"%.2f".format(summary.total_income)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E676))
                    }
                }
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Expenses", fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("↓", fontSize = 12.sp, color = Color(0xFFFF5252))
                        }
                        Canvas(modifier = Modifier.fillMaxWidth().height(24.dp).padding(vertical = 4.dp)) {
                            val w = size.width; val h = size.height
                            val pts = listOf(0.5f, 0.6f, 0.8f, 0.7f, 0.9f, 0.75f, 0.85f)
                            val path = Path()
                            pts.forEachIndexed { i, v ->
                                val x = w * i / (pts.size - 1)
                                val y = h * (1f - v)
                                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            }
                            drawPath(path, Color(0xFFFF5252), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                        }
                        Text("\u20B9${"%.2f".format(summary.total_expenses)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFF5252))
                    }
                }
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Balance", fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
                        Canvas(modifier = Modifier.fillMaxWidth().height(24.dp).padding(vertical = 4.dp)) {
                            val w = size.width; val h = size.height
                            val pts = listOf(0.4f, 0.35f, 0.5f, 0.45f, 0.6f, 0.55f, 0.7f)
                            val path = Path()
                            pts.forEachIndexed { i, v ->
                                val x = w * i / (pts.size - 1)
                                val y = h * (1f - v)
                                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            }
                            drawPath(path, Color(0xFF448AFF), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                        }
                        Text("\u20B9${"%.2f".format(summary.savings)}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }

            // ─── 2. Risk + AI Insight Card ───
            val animatedRisk by animateFloatAsState(
                targetValue = riskPercent / 100f,
                animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
                label = "risk"
            )

            // Risk ring row
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(90.dp)) {
                        val stroke = 9.dp.toPx()
                        drawArc(color = Color.White.copy(alpha = 0.08f), startAngle = 135f, sweepAngle = 270f, useCenter = false, style = Stroke(stroke, cap = StrokeCap.Round))
                        drawArc(
                            brush = Brush.sweepGradient(listOf(Color(0xFF00E676), Color(0xFF00E5FF), Color(0xFF448AFF), Color(0xFFBB86FC), Color(0xFFFF5252))),
                            startAngle = 135f, sweepAngle = 270f * animatedRisk, useCenter = false, style = Stroke(stroke, cap = StrokeCap.Round)
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${riskPercent}%", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Text(res.risk_level.uppercase(), fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = riskColor)
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("Risk Assessment", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Balance: ${balanceRate}% of income (Day $dayOfMonth/$daysInMonth)", fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f))
                    Text("Top category: $topCat", fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f))
                }
            }

            // AI Insight Card
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
                        Text("\uD83E\uDD16", fontSize = 18.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("AI Insight", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(modifier = Modifier.weight(1f))
                        Text(res.risk_level.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = riskColor,
                            modifier = Modifier.background(riskColor.copy(alpha = 0.15f), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 2.dp))
                    }
                    // Dynamic insights based on actual data + day-of-month context
                    val totalSpent = localBreakdown.sumOf { it.total_spent }
                    val sortedCats = localBreakdown.sortedByDescending { it.total_spent }
                    val topCatAmt = sortedCats.firstOrNull()?.total_spent ?: 0.0
                    val topCatPct = if (totalSpent > 0) (topCatAmt * 100 / totalSpent).toInt() else 0
                    val secondCat = sortedCats.getOrNull(1)
                    val secondCatName = secondCat?.category ?: ""
                    val secondCatAmt = secondCat?.total_spent ?: 0.0
                    val spentRatio = if (summary.total_income > 0) ((summary.total_expenses / summary.total_income) * 100).toInt() else 100
                    val dailyBudget = if (daysInMonth - dayOfMonth > 0) (balance / (daysInMonth - dayOfMonth)).toInt() else 0
                    val daysLeft = daysInMonth - dayOfMonth

                    // Day-of-month aware risk context
                    val isLateMonth = monthProgress >= 75
                    val isMidMonth = monthProgress in 40..74
                    val isEarlyMonth = monthProgress < 40
                    val balanceHealthy = balanceVsExpected >= 0
                    val balanceSlightlyLow = balanceVsExpected in -15..-1
                    val balanceCritical = balanceVsExpected < -15

                    val bullets = mutableListOf<String>()

                    // Line 1: Balance status relative to day of month
                    if (isLateMonth && balanceRate <= 20) {
                        bullets.add("Day $dayOfMonth of $daysInMonth with ${balanceRate}% balance left. End-of-month spending is on track.")
                    } else if (isLateMonth && balanceHealthy) {
                        bullets.add("Day $dayOfMonth of $daysInMonth and ${balanceRate}% balance remaining. You will finish the month strong.")
                    } else if (balanceHealthy) {
                        bullets.add("Balance at ${balanceRate}% of income on day $dayOfMonth. You are ahead of schedule this month.")
                    } else if (balanceSlightlyLow) {
                        bullets.add("Balance at ${balanceRate}% on day $dayOfMonth. Slightly below the expected ${expectedBalanceRate}% pace.")
                    } else {
                        bullets.add("Balance at ${balanceRate}% on day $dayOfMonth. Expected around ${expectedBalanceRate}% at this point.")
                    }

                    // Line 2: Top spending category
                    bullets.add("$topCat is your top spend at \u20B9${"%,.0f".format(topCatAmt)} (${topCatPct}% of total expenses).")

                    // Line 3: Days remaining + daily budget
                    if (daysLeft > 0 && balance > 0) {
                        bullets.add("$daysLeft days left this month. You can spend \u20B9$dailyBudget/day to stay on track.")
                    } else if (daysLeft == 0) {
                        bullets.add("Last day of the month. Total spent: \u20B9${"%,.0f".format(summary.total_expenses)} of \u20B9${"%,.0f".format(summary.total_income)} income.")
                    } else {
                        bullets.add("Balance is negative. You have overspent by \u20B9${"%,.0f".format(-balance)}.")
                    }

                    // Line 4: Second category or spending ratio
                    if (secondCat != null) {
                        bullets.add("$secondCatName is second highest at \u20B9${"%,.0f".format(secondCatAmt)}. Combined top 2 = ${topCatPct + (if (totalSpent > 0) (secondCatAmt * 100 / totalSpent).toInt() else 0)}% of spending.")
                    } else {
                        bullets.add("Spent ${spentRatio}% of income so far across ${sortedCats.size} categories.")
                    }

                    // Line 5: Contextual advice based on month position
                    if (isEarlyMonth && balanceCritical) {
                        bullets.add("High spending early in the month. Consider pausing discretionary expenses.")
                    } else if (isMidMonth && balanceCritical) {
                        bullets.add("Below target at mid-month. Trim $topCat to recover by month end.")
                    } else if (isLateMonth && balanceRate <= 10) {
                        bullets.add("Low balance near month end is normal. Next month, try front-loading savings.")
                    } else if (balanceHealthy) {
                        bullets.add("You are managing expenses well. Consider investing the surplus balance.")
                    } else {
                        bullets.add("Tighten spending on $topCat to bring balance closer to target.")
                    }

                    // Line 6: Overall assessment
                    if (isLateMonth && balanceRate >= 0 && balanceRate <= 25) {
                        bullets.add("Month nearly done with spending under control. Solid financial discipline.")
                    } else if (balanceRate > 40) {
                        bullets.add("Strong balance of ${balanceRate}%. You have good room for the rest of the month.")
                    } else if (balanceCritical && isEarlyMonth) {
                        bullets.add("Action needed. You have used ${spentRatio}% of income with ${100 - monthProgress}% of the month remaining.")
                    } else {
                        bullets.add("Overall spending is at ${spentRatio}% of income through ${monthProgress}% of the month.")
                    }
                    bullets.forEach { line ->
                        Row(modifier = Modifier.padding(bottom = 4.dp)) {
                            Text("•", fontSize = 14.sp, color = accentCyan, modifier = Modifier.padding(end = 8.dp, top = 1.dp))
                            Text(line, fontSize = 12.5.sp, color = Color.White.copy(alpha = 0.85f), lineHeight = 18.sp)
                        }
                    }
                }
            }

            // ─── 3. Donut Chart ───
            val total = localBreakdown.sumOf { it.total_spent }
            if (total > 0.0 && localBreakdown.isNotEmpty()) {
                val catColors = listOf(Color(0xFF448AFF), Color(0xFF00E5FF), Color(0xFFBA68C8), Color(0xFFFFEB3B), Color(0xFFFF7043), Color(0xFF00E676))
                Box(
                    modifier = Modifier.fillMaxWidth().height(260.dp).padding(bottom = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(180.dp)) {
                        var startAngle = -90f
                        localBreakdown.forEachIndexed { index, item ->
                            val sweep = (item.total_spent / total).toFloat() * 360f
                            if (sweep > 0f) {
                                drawArc(color = catColors[index % catColors.size], startAngle = startAngle, sweepAngle = sweep - 2f, useCenter = false, style = Stroke(width = 34.dp.toPx(), cap = StrokeCap.Butt))
                                startAngle += sweep
                            }
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Total Spent", fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
                        Text("\u20B9${"%.2f".format(summary.total_expenses)}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
                // Category legend
                localBreakdown.forEachIndexed { index, item ->
                    val share = (item.total_spent * 100 / total).toInt()
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { selectedCategory = item.category }.padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(modifier = Modifier.size(10.dp).background(catColors[index % catColors.size], shape = RoundedCornerShape(3.dp)))
                        Text(item.category, fontSize = 13.sp, color = Color.White, modifier = Modifier.padding(start = 8.dp).weight(1f))
                        Text("\u20B9${"%.0f".format(item.total_spent)} ($share%)", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // ─── 4. Manual Entry buttons ───
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { manualIsIncome = false; manualAmount = ""; manualCategory = selectedCategory ?: "Misc"; manualDate = ""; manualEntryDialogOpen = true },
                    modifier = Modifier.weight(1f).height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A2A45)),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("+ Expense", fontSize = 13.sp, color = Color(0xFFFF5252)) }
                Button(
                    onClick = { manualIsIncome = true; manualAmount = ""; manualCategory = "Income"; manualDate = ""; manualEntryDialogOpen = true },
                    modifier = Modifier.weight(1f).height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A2A45)),
                    shape = RoundedCornerShape(12.dp)
                ) { Text("+ Income", fontSize = 13.sp, color = Color(0xFF00E676)) }
            }

            // ─── 6. Learning card ───
            learningItem?.let { item ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).clickable { if (!learningIsUpdating) showLearningDialog = true },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A3E)),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, glowPurple.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("💡 Quick insight", fontSize = 12.sp, color = glowPurple)
                        Text(if (item.card_title.isNotBlank()) item.card_title else item.concept_name, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = Color.White, modifier = Modifier.padding(vertical = 6.dp))
                        Text("Tap to learn →", fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
                    }
                }
            }

            // ─── 7. Spending trend ───
            val prev = localPrevAnalysis
            if (prev != null) {
                val currentSpent = summary.total_expenses
                val prevSpent = prev.summary.total_expenses
                val diff = currentSpent - prevSpent
                val arrow = if (diff > 0) "↑" else if (diff < 0) "↓" else "→"
                val trendColor = if (diff > 0) Color(0xFFFF5252) else Color(0xFF00E676)
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Spending Trend", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        Text("$arrow \u20B9${"%.0f".format(currentSpent)} vs last month \u20B9${"%.0f".format(prevSpent)}", fontSize = 13.sp, color = trendColor, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }

    // Learning detail dialog: full text + quick quiz
    if (showLearningDialog && learningItem != null) {
        val item = learningItem!!
        var selectedOption by remember { mutableStateOf(-1) }
        var answered by remember { mutableStateOf(false) }
        var isCorrect by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { if (!learningIsUpdating && !answered) showLearningDialog = false },
            confirmButton = {},
            title = null,
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                ) {
                    // Title
                    Text(
                        text = if (item.card_title.isNotBlank()) item.card_title else item.concept_name,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    // Learning text (the main content)
                    Text(
                        text = if (item.learning_text.isNotBlank()) item.learning_text else item.content_snippet,
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.95f),
                        lineHeight = 22.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // Divider
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color.White.copy(alpha = 0.2f))
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // Quiz section
                    if (item.quiz_question.isNotBlank() && item.quiz_options.isNotEmpty()) {
                        Text(
                            text = "Quick check",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF90CAF9),
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        Text(
                            text = item.quiz_question,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )

                        item.quiz_options.forEachIndexed { index, option ->
                            val bgColor = when {
                                !answered -> if (selectedOption == index) Color(0xFF1E3A5F) else Color(0xFF0D1235)
                                index == item.quiz_correct -> Color(0xFF2E7D32) // correct = green
                                selectedOption == index -> Color(0xFFC62828) // wrong selection = red
                                else -> Color(0xFF0D1235)
                            }
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable(enabled = !answered && !learningIsUpdating) { selectedOption = index },
                                colors = CardDefaults.cardColors(containerColor = bgColor)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${('A' + index)}.",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                    Text(
                                        text = option,
                                        fontSize = 14.sp,
                                        color = Color.White
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (!answered) {
                            Button(
                                onClick = {
                                    if (selectedOption >= 0 && !learningIsUpdating) {
                                        isCorrect = selectedOption == item.quiz_correct
                                        answered = true
                                    }
                                },
                                enabled = selectedOption >= 0 && !learningIsUpdating,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Check Answer")
                            }
                        } else {
                            // Show result and continue button
                            Text(
                                text = if (isCorrect) "✓ Correct! Well done." else "✗ Not quite. The right answer is highlighted.",
                                fontSize = 13.sp,
                                color = if (isCorrect) Color(0xFF81C784) else Color(0xFFFFCDD2),
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Button(
                                onClick = {
                                    val obs = ObservationModelDto(
                                        concept_id = item.concept_id,
                                        observation = if (isCorrect) "quiz_correct" else "quiz_wrong"
                                    )
                                    scope.launch {
                                        learningIsUpdating = true
                                        try {
                                            val res = ApiClient.api.updateCurriculum(
                                                CurriculumUpdateRequestDto(
                                                    beliefs = learningBeliefs,
                                                    observation = obs
                                                )
                                            )
                                            learningItem = res.plan.firstOrNull()
                                            learningBeliefs = res.beliefs
                                            learningError = null
                                            showLearningDialog = false
                                        } catch (e: Exception) {
                                            learningError = e.message ?: "Could not update."
                                        } finally {
                                            learningIsUpdating = false
                                        }
                                    }
                                },
                                enabled = !learningIsUpdating,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(if (learningIsUpdating) "Saving..." else "Continue")
                            }
                        }
                    } else {
                        // Fallback if no quiz
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val obs = ObservationModelDto(
                                        concept_id = item.concept_id,
                                        observation = "quiz_correct"
                                    )
                                    scope.launch {
                                        learningIsUpdating = true
                                        try {
                                            val res = ApiClient.api.updateCurriculum(
                                                CurriculumUpdateRequestDto(
                                                    beliefs = learningBeliefs,
                                                    observation = obs
                                                )
                                            )
                                            learningItem = res.plan.firstOrNull()
                                            learningBeliefs = res.beliefs
                                            learningError = null
                                            showLearningDialog = false
                                        } catch (e: Exception) {
                                            learningError = e.message ?: "Could not update."
                                        } finally {
                                            learningIsUpdating = false
                                        }
                                    }
                                },
                                enabled = !learningIsUpdating,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Got it")
                            }
                            OutlinedButton(
                                onClick = {
                                    val obs = ObservationModelDto(
                                        concept_id = item.concept_id,
                                        observation = "quiz_wrong"
                                    )
                                    scope.launch {
                                        learningIsUpdating = true
                                        try {
                                            val res = ApiClient.api.updateCurriculum(
                                                CurriculumUpdateRequestDto(
                                                    beliefs = learningBeliefs,
                                                    observation = obs
                                                )
                                            )
                                            learningItem = res.plan.firstOrNull()
                                            learningBeliefs = res.beliefs
                                            learningError = null
                                            showLearningDialog = false
                                        } catch (e: Exception) {
                                            learningError = e.message ?: "Could not update."
                                        } finally {
                                            learningIsUpdating = false
                                        }
                                    }
                                },
                                enabled = !learningIsUpdating,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Still confused")
                            }
                        }
                    }

                    learningError?.let {
                        Text(
                            text = it,
                            fontSize = 11.sp,
                            color = Color(0xFFFFA726),
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                }
            },
            containerColor = Color(0xFF151A3D)
        )
    }

    if (manualEntryDialogOpen) {
        AlertDialog(
            onDismissRequest = { manualEntryDialogOpen = false },
            confirmButton = {},
            title = null,
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (manualIsIncome) "Add income" else "Add expense",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = manualAmount,
                        onValueChange = { manualAmount = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text("Amount") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = manualCategory,
                        onValueChange = { manualCategory = it },
                        label = { Text("Category") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = manualDate,
                        onValueChange = { manualDate = it },
                        label = { Text("Date (optional)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { manualEntryDialogOpen = false }) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        TextButton(onClick = {
                            val amount = manualAmount.toDoubleOrNull()
                            if (amount != null && manualCategory.isNotBlank()) {
                                localAnalysis?.let { current ->
                                    val summary = current.summary
                                    val newSummary = if (manualIsIncome) {
                                        summary.copy(
                                            total_income = summary.total_income + amount,
                                            savings = summary.savings + amount
                                        )
                                    } else {
                                        summary.copy(
                                            total_expenses = summary.total_expenses + amount,
                                            savings = summary.savings - amount
                                        )
                                    }
                                    localAnalysis = current.copy(summary = newSummary)

                                    if (!manualIsIncome) {
                                        val existing = localBreakdown.find { it.category.equals(manualCategory, true) }
                                        localBreakdown = if (existing != null) {
                                            localBreakdown.map {
                                                if (it.category.equals(manualCategory, true))
                                                    it.copy(total_spent = it.total_spent + amount, transactions_count = it.transactions_count + 1)
                                                else it
                                            }
                                        } else {
                                            localBreakdown + ExpenseCategorySummaryDto(category = manualCategory, total_spent = amount, transactions_count = 1)
                                        }
                                    }
                                }
                                manualEntryDialogOpen = false
                            }
                        }) {
                            Text("Save")
                        }
                    }
                }
            },
            containerColor = Color(0xFF151A3D)
        )
    }
}



@Composable
fun ChatHubScreen(onNavigate: (String) -> Unit) {
    val bgColor = Color(0xFF060B28)
    val cardBg = Color(0xFF0F1B2E)
    val accentCyan = Color(0xFF00E5A0)
    val glowPurple = Color(0xFFBB86FC)
    val glowBlue = Color(0xFF448AFF)

    val infiniteTransition = rememberInfiniteTransition(label = "hub")
    val orbGlow by infiniteTransition.animateFloat(
        initialValue = 0.6f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "orbGlow"
    )
    val waveOffset by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing)),
        label = "wave"
    )

    Box(modifier = Modifier.fillMaxSize().background(bgColor)) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Header with animated orb
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
                Box(
                    modifier = Modifier.size(44.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(44.dp)) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                listOf(glowBlue.copy(alpha = orbGlow * 0.8f), Color.Transparent)
                            ),
                            radius = 22.dp.toPx()
                        )
                        drawCircle(color = glowBlue, radius = 12.dp.toPx())
                        drawCircle(color = Color.White.copy(alpha = 0.9f), radius = 5.dp.toPx())
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text("Agent C", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Text("Multi-agent decision engine", fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Quick Action Chips
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                listOf(
                    Triple("Ask Agent C", glowBlue, "chat"),
                    Triple("Plan Scenario", glowPurple, "whatif"),
                    Triple("Market Pulse", accentCyan, "news")
                ).forEach { (label, color, target) ->
                    Card(
                        modifier = Modifier.clickable { onNavigate(target) },
                        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
                    ) {
                        Text(
                            label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            color = color, modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // ─── AI Chatbot Card ───
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp).clickable { onNavigate("chat") },
                colors = CardDefaults.cardColors(containerColor = cardBg),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, glowBlue.copy(alpha = 0.15f))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Thin line-art icon for chat
                        Canvas(modifier = Modifier.size(36.dp)) {
                            val s = size.width
                            drawRoundRect(
                                color = glowBlue,
                                topLeft = Offset(2.dp.toPx(), 4.dp.toPx()),
                                size = androidx.compose.ui.geometry.Size(s - 4.dp.toPx(), s * 0.6f),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()),
                                style = Stroke(1.8.dp.toPx())
                            )
                            val path = Path().apply {
                                moveTo(s * 0.3f, s * 0.64f + 4.dp.toPx())
                                lineTo(s * 0.2f, s * 0.85f)
                                lineTo(s * 0.45f, s * 0.64f + 4.dp.toPx())
                            }
                            drawPath(path, glowBlue, style = Stroke(1.8.dp.toPx()))
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Agentic Chat", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Ask anything about your finances", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                        }
                        // Arrow
                        Canvas(modifier = Modifier.size(20.dp)) {
                            val path = Path().apply {
                                moveTo(4.dp.toPx(), 4.dp.toPx())
                                lineTo(16.dp.toPx(), 10.dp.toPx())
                                lineTo(4.dp.toPx(), 16.dp.toPx())
                            }
                            drawPath(path, Color.White.copy(alpha = 0.4f), style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Animated waveform preview
                    Canvas(modifier = Modifier.fillMaxWidth().height(28.dp)) {
                        val w = size.width; val h = size.height
                        val barCount = 32
                        val barWidth = 3.dp.toPx()
                        val gap = (w - barCount * barWidth) / (barCount - 1)
                        for (i in 0 until barCount) {
                            val phase = (waveOffset + i * 20f) % 360f
                            val amp = (kotlin.math.sin(Math.toRadians(phase.toDouble())).toFloat() + 1f) / 2f
                            val barH = h * 0.2f + h * 0.8f * amp * 0.5f
                            drawRoundRect(
                                color = glowBlue.copy(alpha = 0.3f + amp * 0.4f),
                                topLeft = Offset(i * (barWidth + gap), (h - barH) / 2),
                                size = androidx.compose.ui.geometry.Size(barWidth, barH),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Sample suggestions
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("Summarize my spending", "How is my risk?", "Tips to save more").forEach { tip ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = glowBlue.copy(alpha = 0.08f),
                                border = BorderStroke(0.5.dp, glowBlue.copy(alpha = 0.2f))
                            ) {
                                Text(tip, fontSize = 11.sp, color = glowBlue.copy(alpha = 0.8f),
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                            }
                        }
                    }
                }
            }

            // ─── What-If Simulator Card ───
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp).clickable { onNavigate("whatif") },
                colors = CardDefaults.cardColors(containerColor = cardBg),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, glowPurple.copy(alpha = 0.15f))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Thin line-art scale/balance icon
                        Canvas(modifier = Modifier.size(36.dp)) {
                            val s = size.width
                            val mid = s / 2
                            // Vertical pole
                            drawLine(glowPurple, Offset(mid, 4.dp.toPx()), Offset(mid, s - 4.dp.toPx()), strokeWidth = 1.8.dp.toPx(), cap = StrokeCap.Round)
                            // Horizontal beam
                            drawLine(glowPurple, Offset(4.dp.toPx(), s * 0.35f), Offset(s - 4.dp.toPx(), s * 0.35f), strokeWidth = 1.8.dp.toPx(), cap = StrokeCap.Round)
                            // Left pan
                            drawLine(glowPurple, Offset(4.dp.toPx(), s * 0.35f), Offset(6.dp.toPx(), s * 0.55f), strokeWidth = 1.2.dp.toPx())
                            drawLine(glowPurple, Offset(4.dp.toPx() + 12.dp.toPx(), s * 0.35f), Offset(6.dp.toPx() + 10.dp.toPx(), s * 0.55f), strokeWidth = 1.2.dp.toPx())
                            // Right pan
                            drawLine(glowPurple, Offset(s - 4.dp.toPx(), s * 0.35f), Offset(s - 6.dp.toPx(), s * 0.55f), strokeWidth = 1.2.dp.toPx())
                            drawLine(glowPurple, Offset(s - 4.dp.toPx() - 12.dp.toPx(), s * 0.35f), Offset(s - 6.dp.toPx() - 10.dp.toPx(), s * 0.55f), strokeWidth = 1.2.dp.toPx())
                            // Base
                            drawLine(glowPurple, Offset(mid - 8.dp.toPx(), s - 4.dp.toPx()), Offset(mid + 8.dp.toPx(), s - 4.dp.toPx()), strokeWidth = 1.8.dp.toPx(), cap = StrokeCap.Round)
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("What-If Simulator", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Plan scenarios with live projections", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                        }
                        Canvas(modifier = Modifier.size(20.dp)) {
                            val path = Path().apply {
                                moveTo(4.dp.toPx(), 4.dp.toPx())
                                lineTo(16.dp.toPx(), 10.dp.toPx())
                                lineTo(4.dp.toPx(), 16.dp.toPx())
                            }
                            drawPath(path, Color.White.copy(alpha = 0.4f), style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Mini preview: existing vs projected path
                    Canvas(modifier = Modifier.fillMaxWidth().height(50.dp)) {
                        val w = size.width; val h = size.height
                        val solidPts = listOf(0.7f, 0.65f, 0.6f, 0.55f, 0.5f, 0.45f, 0.42f)
                        val dashPts = listOf(0.42f, 0.48f, 0.55f, 0.6f, 0.68f, 0.72f, 0.78f)
                        // Solid line (existing path)
                        val solidPath = Path()
                        solidPts.forEachIndexed { i, v ->
                            val x = w * i / (solidPts.size - 1)
                            val y = h * (1f - v)
                            if (i == 0) solidPath.moveTo(x, y) else solidPath.lineTo(x, y)
                        }
                        drawPath(solidPath, Color.White.copy(alpha = 0.5f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
                        // Dashed projected line
                        val dashPath = Path()
                        dashPts.forEachIndexed { i, v ->
                            val x = w * i / (dashPts.size - 1)
                            val y = h * (1f - v)
                            if (i == 0) dashPath.moveTo(x, y) else dashPath.lineTo(x, y)
                        }
                        drawPath(dashPath, glowPurple.copy(alpha = 0.7f), style = Stroke(2.dp.toPx(), cap = StrokeCap.Round,
                            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 6f))))
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(12.dp, 2.dp).background(Color.White.copy(alpha = 0.5f)))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Current path", fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(12.dp, 2.dp).background(glowPurple.copy(alpha = 0.7f)))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Projected", fontSize = 10.sp, color = glowPurple.copy(alpha = 0.7f))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Footer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text("Powered by multi-agent reasoning", fontSize = 11.sp, color = Color.White.copy(alpha = 0.35f))
            }
        }
    }
}

@Composable
fun WhatIfSimulationScreen(onBack: () -> Unit) {
    val bgColor = Color(0xFF060B28)
    val cardBg = Color(0xFF0F1B2E)
    val accentCyan = Color(0xFF00E5A0)
    val glowPurple = Color(0xFFBB86FC)
    val glowBlue = Color(0xFF448AFF)

    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Slider states
    var incomeAdjust by remember { mutableStateOf(0f) }
    var foodAdjust by remember { mutableStateOf(0f) }
    var billsAdjust by remember { mutableStateOf(0f) }
    var shoppingAdjust by remember { mutableStateOf(0f) }
    var sipInvestment by remember { mutableStateOf(0f) }
    var marketSentiment by remember { mutableStateOf(0.5f) }

    // Saved scenario
    var scenarioSaved by remember { mutableStateOf(false) }

    // Text prompt for custom what-if scenario
    var whatIfPrompt by remember { mutableStateOf("") }

    // Risk calculation (includes SIP effect)
    val riskValue = remember(incomeAdjust, foodAdjust, billsAdjust, shoppingAdjust, sipInvestment, marketSentiment) {
        val totalExpenseChange = foodAdjust + billsAdjust + shoppingAdjust
        val incEffect = incomeAdjust / 50000f
        val expEffect = totalExpenseChange / 30000f
        val sipEffect = sipInvestment / 25000f * 0.12f
        val marketEffect = (marketSentiment - 0.5f) * 0.3f
        (0.5f - incEffect + expEffect - sipEffect - marketEffect).coerceIn(0.05f, 0.95f)
    }
    val animatedRisk by animateFloatAsState(
        targetValue = riskValue,
        animationSpec = tween(600, easing = FastOutSlowInEasing),
        label = "simRisk"
    )
    val riskPercent = (animatedRisk * 100).toInt()
    val riskColor = when {
        riskPercent >= 65 -> Color(0xFFFF5252)
        riskPercent >= 40 -> Color(0xFFFFA726)
        else -> Color(0xFF00E676)
    }
    val riskLabel = when {
        riskPercent >= 65 -> "HIGH"
        riskPercent >= 40 -> "MEDIUM"
        else -> "LOW"
    }

    // Future Wealth calculations
    val baseIncome = 50000f
    val baseExpenses = 35000f
    val adjMonthlySurplus = remember(incomeAdjust, foodAdjust, billsAdjust, shoppingAdjust, sipInvestment) {
        val adj = (baseIncome + incomeAdjust) - (baseExpenses + foodAdjust + billsAdjust + shoppingAdjust) - sipInvestment
        adj.coerceAtLeast(0f)
    }
    val estNetWorth5yr = remember(adjMonthlySurplus, sipInvestment, marketSentiment) {
        val annualReturn = if (marketSentiment > 0.6f) 0.12f else if (marketSentiment < 0.3f) 0.06f else 0.09f
        val monthlyRate = annualReturn / 12f
        val months = 60f
        val savingsTotal = adjMonthlySurplus * months
        val sipFV = if (sipInvestment > 0) sipInvestment * ((Math.pow((1 + monthlyRate).toDouble(), months.toDouble()).toFloat() - 1f) / monthlyRate) * (1 + monthlyRate) else 0f
        savingsTotal + sipFV
    }
    val retirementPct = remember(estNetWorth5yr) {
        val target = 10000000f
        ((estNetWorth5yr / target) * 100f).coerceIn(0f, 100f).toInt()
    }
    val animatedRetirement by animateFloatAsState(
        targetValue = retirementPct.toFloat(),
        animationSpec = tween(800, easing = FastOutSlowInEasing),
        label = "retPct"
    )

    // Waveform animation for loading
    val infiniteTransition = rememberInfiniteTransition(label = "sim")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing)),
        label = "simWave"
    )

    Column(
        modifier = Modifier.fillMaxSize().background(bgColor)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Canvas(modifier = Modifier.size(28.dp).clickable { onBack() }) {
                drawLine(Color.White, Offset(18.dp.toPx(), 4.dp.toPx()), Offset(4.dp.toPx(), 14.dp.toPx()), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                drawLine(Color.White, Offset(4.dp.toPx(), 14.dp.toPx()), Offset(18.dp.toPx(), 24.dp.toPx()), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text("What-If Simulator", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text("Precision scenario planning", fontSize = 11.sp, color = Color.White.copy(alpha = 0.4f))
            }
            Spacer(modifier = Modifier.weight(1f))
            // Mini risk ring
            Box(modifier = Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(38.dp)) {
                    val stroke = 4.dp.toPx()
                    drawArc(Color.White.copy(alpha = 0.08f), 135f, 270f, false, style = Stroke(stroke, cap = StrokeCap.Round))
                    drawArc(riskColor, 135f, 270f * animatedRisk, false, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                Text("$riskPercent", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = riskColor)
            }
        }

        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
        ) {

            // ─── CONTROL HUB ───
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 14.dp)) {
                        // Settings gear icon (Canvas line-art)
                        Canvas(modifier = Modifier.size(18.dp)) {
                            val c = size.width / 2f
                            val r = size.width * 0.28f
                            drawCircle(Color.White.copy(alpha = 0.7f), radius = r, center = Offset(c, c), style = Stroke(1.5.dp.toPx()))
                            for (i in 0 until 8) {
                                val angle = Math.toRadians((i * 45.0))
                                val inner = r + 1.dp.toPx()
                                val outer = r + 4.dp.toPx()
                                drawLine(
                                    Color.White.copy(alpha = 0.7f),
                                    Offset(c + (inner * kotlin.math.cos(angle)).toFloat(), c + (inner * kotlin.math.sin(angle)).toFloat()),
                                    Offset(c + (outer * kotlin.math.cos(angle)).toFloat(), c + (outer * kotlin.math.sin(angle)).toFloat()),
                                    strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Control Hub", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }

                    // ─── Text Prompt for custom scenario ───
                    OutlinedTextField(
                        value = whatIfPrompt,
                        onValueChange = { whatIfPrompt = it },
                        placeholder = { Text("e.g. What if I lose my job?", color = Color.White.copy(alpha = 0.3f), fontSize = 13.sp) },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White.copy(alpha = 0.8f),
                            cursorColor = accentCyan,
                            focusedBorderColor = accentCyan.copy(alpha = 0.5f),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.1f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp),
                        singleLine = false,
                        maxLines = 3,
                        minLines = 1
                    )

                    // Income slider
                    SliderRow("Income Adjust", incomeAdjust, -50000f, 50000f, accentCyan,
                        formatLabel = { v -> "${if (v >= 0) "+" else ""}\u20B9${"%,.0f".format(v)}" }) { incomeAdjust = it }

                    // Food slider
                    SliderRow("Food & Dining", foodAdjust, -10000f, 10000f, Color(0xFFFF7043),
                        formatLabel = { v -> "${if (v >= 0) "+" else ""}\u20B9${"%,.0f".format(v)}" }) { foodAdjust = it }

                    // Bills slider
                    SliderRow("Bills & Utilities", billsAdjust, -10000f, 10000f, Color(0xFF00E5FF),
                        formatLabel = { v -> "${if (v >= 0) "+" else ""}\u20B9${"%,.0f".format(v)}" }) { billsAdjust = it }

                    // Shopping slider
                    SliderRow("Shopping", shoppingAdjust, -10000f, 10000f, Color(0xFFBA68C8),
                        formatLabel = { v -> "${if (v >= 0) "+" else ""}\u20B9${"%,.0f".format(v)}" }) { shoppingAdjust = it }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                    Spacer(modifier = Modifier.height(8.dp))

                    // ─── Investment Push (SIP) ───
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
                        // Upward trend icon (Canvas)
                        Canvas(modifier = Modifier.size(16.dp)) {
                            val s = size.width
                            val path = Path().apply {
                                moveTo(2.dp.toPx(), s - 2.dp.toPx())
                                lineTo(s * 0.4f, s * 0.5f)
                                lineTo(s * 0.6f, s * 0.65f)
                                lineTo(s - 2.dp.toPx(), 2.dp.toPx())
                            }
                            drawPath(path, Color(0xFF00E676), style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
                            // Arrow tip
                            drawLine(Color(0xFF00E676), Offset(s * 0.7f, 2.dp.toPx()), Offset(s - 2.dp.toPx(), 2.dp.toPx()), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
                            drawLine(Color(0xFF00E676), Offset(s - 2.dp.toPx(), s * 0.3f), Offset(s - 2.dp.toPx(), 2.dp.toPx()), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Investment Push", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF00E676).copy(alpha = 0.9f))
                    }
                    SliderRow("Monthly SIP", sipInvestment, 0f, 25000f, Color(0xFF00E676),
                        formatLabel = { v -> "\u20B9${"%,.0f".format(v)}/mo" }) { sipInvestment = it }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                    Spacer(modifier = Modifier.height(8.dp))

                    // Market sentiment
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Market", fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.width(70.dp))
                        val sentLabel = when {
                            marketSentiment < 0.3f -> "Bearish"
                            marketSentiment < 0.7f -> "Neutral"
                            else -> "Bullish"
                        }
                        val sentColor = when {
                            marketSentiment < 0.3f -> Color(0xFFFF5252)
                            marketSentiment < 0.7f -> Color(0xFFFFA726)
                            else -> Color(0xFF00E676)
                        }
                        Slider(
                            value = marketSentiment, onValueChange = { marketSentiment = it },
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = sentColor, activeTrackColor = sentColor,
                                inactiveTrackColor = Color.White.copy(alpha = 0.08f)
                            )
                        )
                        Text(sentLabel, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = sentColor,
                            modifier = Modifier.width(52.dp), textAlign = TextAlign.End)
                    }
                }
            }

            // ─── PROJECTION MATRIX (Dual-path chart) ───
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                        // Chart icon (Canvas)
                        Canvas(modifier = Modifier.size(16.dp)) {
                            val s = size.width
                            drawLine(Color.White.copy(alpha = 0.6f), Offset(2.dp.toPx(), s - 2.dp.toPx()), Offset(2.dp.toPx(), 2.dp.toPx()), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
                            drawLine(Color.White.copy(alpha = 0.6f), Offset(2.dp.toPx(), s - 2.dp.toPx()), Offset(s - 2.dp.toPx(), s - 2.dp.toPx()), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Projection Matrix", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                    }

                    Canvas(modifier = Modifier.fillMaxWidth().height(160.dp)) {
                        val w = size.width; val h = size.height
                        val months = 6
                        val topPad = h * 0.05f
                        val botPad = h * 0.12f
                        val chartH = h - topPad - botPad

                        // Existing path (linear decline from 100 to ~40)
                        val existing = listOf(1.0f, 0.88f, 0.76f, 0.64f, 0.52f, 0.42f)
                        // Projected path with slider + SIP boost
                        val boost = (incomeAdjust / 50000f - (foodAdjust + billsAdjust + shoppingAdjust) / 30000f + sipInvestment / 25000f * 0.2f + (marketSentiment - 0.5f) * 0.2f)
                        val projected = existing.mapIndexed { i, v -> (v + boost * (i + 1) * 0.15f).coerceIn(0.05f, 1f) }

                        // Grid lines
                        for (i in 0..4) {
                            val y = topPad + chartH * i / 4f
                            drawLine(Color.White.copy(alpha = 0.04f), Offset(0f, y), Offset(w, y))
                        }

                        // Fill under existing path
                        val fillExisting = Path().apply {
                            existing.forEachIndexed { i, v ->
                                val x = w * i / (months - 1)
                                val y = topPad + chartH * (1f - v * 0.85f)
                                if (i == 0) moveTo(x, y) else lineTo(x, y)
                            }
                            lineTo(w, topPad + chartH)
                            lineTo(0f, topPad + chartH)
                            close()
                        }
                        drawPath(fillExisting, Color.White.copy(alpha = 0.03f))

                        // Fill under projected path
                        val fillProjected = Path().apply {
                            projected.forEachIndexed { i, v ->
                                val x = w * i / (months - 1)
                                val y = topPad + chartH * (1f - v * 0.85f)
                                if (i == 0) moveTo(x, y) else lineTo(x, y)
                            }
                            lineTo(w, topPad + chartH)
                            lineTo(0f, topPad + chartH)
                            close()
                        }
                        drawPath(fillProjected, glowPurple.copy(alpha = 0.06f))

                        // Existing path line (solid white)
                        val solidPath = Path()
                        existing.forEachIndexed { i, v ->
                            val x = w * i / (months - 1)
                            val y = topPad + chartH * (1f - v * 0.85f)
                            if (i == 0) solidPath.moveTo(x, y) else solidPath.lineTo(x, y)
                        }
                        drawPath(solidPath, Color.White.copy(alpha = 0.45f), style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))

                        // Projected path (dashed glow)
                        val dashPath = Path()
                        projected.forEachIndexed { i, v ->
                            val x = w * i / (months - 1)
                            val y = topPad + chartH * (1f - v * 0.85f)
                            if (i == 0) dashPath.moveTo(x, y) else dashPath.lineTo(x, y)
                        }
                        drawPath(dashPath, glowPurple.copy(alpha = 0.2f), style = Stroke(6.dp.toPx(), cap = StrokeCap.Round))
                        drawPath(dashPath, glowPurple, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round,
                            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 8f))))

                        // Data points on projected
                        projected.forEachIndexed { i, v ->
                            val x = w * i / (months - 1)
                            val y = topPad + chartH * (1f - v * 0.85f)
                            drawCircle(glowPurple.copy(alpha = 0.3f), radius = 5.dp.toPx(), center = Offset(x, y))
                            drawCircle(glowPurple, radius = 2.5.dp.toPx(), center = Offset(x, y))
                        }

                        // Month labels
                        val labels = listOf("Now", "M+1", "M+2", "M+3", "M+4", "M+5")
                        labels.forEachIndexed { i, lbl ->
                            drawContext.canvas.nativeCanvas.drawText(
                                lbl, w * i / (months - 1), h - 2.dp.toPx(),
                                android.graphics.Paint().apply {
                                    color = 0x66FFFFFF
                                    textSize = 9.sp.toPx()
                                    textAlign = android.graphics.Paint.Align.CENTER
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(14.dp, 2.dp).background(Color.White.copy(alpha = 0.45f)))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Current path", fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(14.dp, 2.dp).background(glowPurple))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Projected", fontSize = 10.sp, color = glowPurple.copy(alpha = 0.8f))
                        }
                    }
                }
            }

            // ─── Impact Analysis: Risk Ring + Future Wealth Cards ───
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, riskColor.copy(alpha = 0.15f))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    // Risk Assessment Row
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                            Canvas(modifier = Modifier.size(58.dp)) {
                                val stroke = 6.dp.toPx()
                                drawArc(Color.White.copy(alpha = 0.08f), 135f, 270f, false, style = Stroke(stroke, cap = StrokeCap.Round))
                                drawArc(
                                    brush = Brush.sweepGradient(listOf(Color(0xFF00E676), Color(0xFF00E5FF), Color(0xFF448AFF), Color(0xFFBB86FC), Color(0xFFFF5252))),
                                    startAngle = 135f, sweepAngle = 270f * animatedRisk, useCenter = false, style = Stroke(stroke, cap = StrokeCap.Round)
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("$riskPercent%", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Text(riskLabel, fontSize = 8.sp, fontWeight = FontWeight.SemiBold, color = riskColor)
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text("Projected Risk", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                            Spacer(modifier = Modifier.height(4.dp))
                            val summary = buildString {
                                if (incomeAdjust != 0f) append("Income ${if (incomeAdjust > 0) "+" else ""}\u20B9${"%,.0f".format(incomeAdjust)}. ")
                                val totalExp = foodAdjust + billsAdjust + shoppingAdjust
                                if (totalExp != 0f) append("Expenses ${if (totalExp > 0) "+" else ""}\u20B9${"%,.0f".format(totalExp)}. ")
                                if (sipInvestment > 0f) append("SIP \u20B9${"%,.0f".format(sipInvestment)}/mo. ")
                                val sentLabel = when { marketSentiment < 0.3f -> "bearish"; marketSentiment < 0.7f -> "neutral"; else -> "bullish" }
                                append("Market: $sentLabel.")
                            }
                            Text(summary, fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f), lineHeight = 16.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                    Spacer(modifier = Modifier.height(16.dp))

                    // Future Wealth Cards Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Est. Net Worth 5yr Card
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0A1428)),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, accentCyan.copy(alpha = 0.12f))
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Diamond icon
                                Canvas(modifier = Modifier.size(22.dp)) {
                                    val s = size.width
                                    val path = Path().apply {
                                        moveTo(s / 2, 1.dp.toPx())
                                        lineTo(s - 1.dp.toPx(), s / 2)
                                        lineTo(s / 2, s - 1.dp.toPx())
                                        lineTo(1.dp.toPx(), s / 2)
                                        close()
                                    }
                                    drawPath(path, accentCyan.copy(alpha = 0.7f), style = Stroke(1.5.dp.toPx()))
                                    // Inner line
                                    drawLine(accentCyan.copy(alpha = 0.3f), Offset(s * 0.25f, s / 2), Offset(s * 0.75f, s / 2), strokeWidth = 1.dp.toPx())
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Est. Net Worth", fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f))
                                Text("5-Year", fontSize = 9.sp, color = Color.White.copy(alpha = 0.35f))
                                Spacer(modifier = Modifier.height(6.dp))
                                val formatted = when {
                                    estNetWorth5yr >= 10000000f -> "\u20B9${"%,.1f".format(estNetWorth5yr / 10000000f)} Cr"
                                    estNetWorth5yr >= 100000f -> "\u20B9${"%,.1f".format(estNetWorth5yr / 100000f)} L"
                                    else -> "\u20B9${"%,.0f".format(estNetWorth5yr)}"
                                }
                                Text(formatted, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = accentCyan)
                            }
                        }

                        // Retirement Readiness Card
                        Card(
                            modifier = Modifier.weight(1f),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0A1428)),
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, glowPurple.copy(alpha = 0.12f))
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Shield icon
                                Canvas(modifier = Modifier.size(22.dp)) {
                                    val s = size.width
                                    val path = Path().apply {
                                        moveTo(s / 2, 1.dp.toPx())
                                        lineTo(s - 2.dp.toPx(), s * 0.25f)
                                        lineTo(s - 2.dp.toPx(), s * 0.55f)
                                        quadraticBezierTo(s / 2, s - 1.dp.toPx(), s / 2, s - 1.dp.toPx())
                                        quadraticBezierTo(s / 2, s - 1.dp.toPx(), 2.dp.toPx(), s * 0.55f)
                                        lineTo(2.dp.toPx(), s * 0.25f)
                                        close()
                                    }
                                    drawPath(path, glowPurple.copy(alpha = 0.6f), style = Stroke(1.5.dp.toPx()))
                                    // Checkmark inside
                                    drawLine(glowPurple.copy(alpha = 0.5f), Offset(s * 0.3f, s * 0.48f), Offset(s * 0.45f, s * 0.62f), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
                                    drawLine(glowPurple.copy(alpha = 0.5f), Offset(s * 0.45f, s * 0.62f), Offset(s * 0.7f, s * 0.32f), strokeWidth = 1.5.dp.toPx(), cap = StrokeCap.Round)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text("Retirement", fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f))
                                Text("Readiness", fontSize = 9.sp, color = Color.White.copy(alpha = 0.35f))
                                Spacer(modifier = Modifier.height(6.dp))
                                // Circular progress
                                Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                                    Canvas(modifier = Modifier.size(40.dp)) {
                                        val stroke = 4.dp.toPx()
                                        drawArc(Color.White.copy(alpha = 0.06f), 0f, 360f, false, style = Stroke(stroke))
                                        drawArc(
                                            color = if (animatedRetirement > 60f) Color(0xFF00E676) else if (animatedRetirement > 30f) Color(0xFFFFA726) else Color(0xFFFF5252),
                                            startAngle = -90f, sweepAngle = 3.6f * animatedRetirement, useCenter = false,
                                            style = Stroke(stroke, cap = StrokeCap.Round)
                                        )
                                    }
                                    Text("${animatedRetirement.toInt()}%", fontSize = 11.sp, fontWeight = FontWeight.Bold,
                                        color = if (animatedRetirement > 60f) Color(0xFF00E676) else if (animatedRetirement > 30f) Color(0xFFFFA726) else Color(0xFFFF5252))
                                }
                            }
                        }
                    }
                }
            }

            // ─── ACTION HUB ───
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Save Scenario
                Button(
                    onClick = { scenarioSaved = true },
                    modifier = Modifier.weight(1f).height(46.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (scenarioSaved) accentCyan.copy(alpha = 0.15f) else cardBg),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, if (scenarioSaved) accentCyan.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.1f))
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Bookmark icon
                        Canvas(modifier = Modifier.size(14.dp)) {
                            val s = size.width
                            val path = Path().apply {
                                moveTo(2.dp.toPx(), 1.dp.toPx())
                                lineTo(s - 2.dp.toPx(), 1.dp.toPx())
                                lineTo(s - 2.dp.toPx(), s - 1.dp.toPx())
                                lineTo(s / 2, s * 0.7f)
                                lineTo(2.dp.toPx(), s - 1.dp.toPx())
                                close()
                            }
                            if (scenarioSaved) {
                                drawPath(path, accentCyan.copy(alpha = 0.3f))
                            }
                            drawPath(path, if (scenarioSaved) accentCyan else Color.White.copy(alpha = 0.5f), style = Stroke(1.5.dp.toPx()))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (scenarioSaved) "Saved" else "Save",
                            fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                            color = if (scenarioSaved) accentCyan else Color.White.copy(alpha = 0.7f)
                        )
                    }
                }

                // Get Agent C Advice
                Button(
                    onClick = {
                        if (!isLoading) {
                            isLoading = true; error = null; result = null
                            val scenarioText = buildString {
                                append("What-if scenario: ")
                                if (whatIfPrompt.isNotBlank()) append("User asks: ${whatIfPrompt.trim()}. ")
                                if (incomeAdjust != 0f) append("Income changes by \u20B9${"%,.0f".format(incomeAdjust)}. ")
                                if (foodAdjust != 0f) append("Food spending changes by \u20B9${"%,.0f".format(foodAdjust)}. ")
                                if (billsAdjust != 0f) append("Bills change by \u20B9${"%,.0f".format(billsAdjust)}. ")
                                if (shoppingAdjust != 0f) append("Shopping changes by \u20B9${"%,.0f".format(shoppingAdjust)}. ")
                                if (sipInvestment > 0f) append("SIP investment of \u20B9${"%,.0f".format(sipInvestment)} per month. ")
                                val sentLabel = when { marketSentiment < 0.3f -> "bearish"; marketSentiment < 0.7f -> "neutral"; else -> "bullish" }
                                append("Market sentiment: $sentLabel. ")
                                append("Projected 5yr net worth: \u20B9${"%,.0f".format(estNetWorth5yr)}. ")
                                append("Retirement readiness: $retirementPct%. ")
                                append("Projected risk: $riskPercent%. ")
                                append("Analyze financial impact, project savings/losses, evaluate SIP strategy, and give specific recommendations.")
                            }
                            scope.launch {
                                try {
                                    val body = SynthesizeChatRequestDto(
                                        finance_insight = "User simulating a what-if scenario with SIP investment.",
                                        news_insight = "Market sentiment is ${when { marketSentiment < 0.3f -> "bearish"; marketSentiment < 0.7f -> "neutral"; else -> "bullish" }}.",
                                        user_question = scenarioText,
                                        history = emptyList()
                                    )
                                    val res = ApiClient.api.synthesizeChat(body)
                                    result = res.recommendation
                                } catch (e: Exception) {
                                    error = e.message ?: "Simulation failed."
                                } finally { isLoading = false }
                            }
                        }
                    },
                    modifier = Modifier.weight(1f).height(46.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = glowPurple.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, glowPurple.copy(alpha = 0.4f))
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Brain icon
                        Canvas(modifier = Modifier.size(14.dp)) {
                            val c = size.width / 2f
                            val r = size.width * 0.4f
                            drawCircle(glowPurple.copy(alpha = 0.7f), radius = r, center = Offset(c, c), style = Stroke(1.5.dp.toPx()))
                            drawCircle(Color.White.copy(alpha = 0.6f), radius = r * 0.3f, center = Offset(c, c))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isLoading) "..." else "Agent C", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = glowPurple)
                    }
                }
            }

            // Loading waveform
            if (isLoading) {
                Canvas(modifier = Modifier.fillMaxWidth().height(24.dp).padding(bottom = 8.dp)) {
                    val w = size.width; val h = size.height
                    val barCount = 40
                    val barWidth = 3.dp.toPx()
                    val gap = (w - barCount * barWidth) / (barCount - 1)
                    for (i in 0 until barCount) {
                        val phase = (wavePhase + i * 15f) % 360f
                        val amp = (kotlin.math.sin(Math.toRadians(phase.toDouble())).toFloat() + 1f) / 2f
                        val barH = h * 0.15f + h * 0.85f * amp
                        drawRoundRect(
                            color = glowPurple.copy(alpha = 0.4f + amp * 0.5f),
                            topLeft = Offset(i * (barWidth + gap), (h - barH) / 2),
                            size = androidx.compose.ui.geometry.Size(barWidth, barH),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
                        )
                    }
                }
            }

            // Error
            error?.let {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF3D0A0A)),
                    shape = RoundedCornerShape(12.dp)
                ) { Text(it, fontSize = 12.sp, color = Color(0xFFFF5252), modifier = Modifier.padding(12.dp)) }
            }

            // AI Analysis Result
            result?.let { res ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    shape = RoundedCornerShape(18.dp),
                    border = BorderStroke(1.dp, glowPurple.copy(alpha = 0.15f))
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
                            // Graph line-art icon
                            Canvas(modifier = Modifier.size(20.dp)) {
                                val s = size.width
                                drawLine(Color.White.copy(alpha = 0.6f), Offset(2.dp.toPx(), s - 2.dp.toPx()), Offset(2.dp.toPx(), 2.dp.toPx()), strokeWidth = 1.5.dp.toPx())
                                drawLine(Color.White.copy(alpha = 0.6f), Offset(2.dp.toPx(), s - 2.dp.toPx()), Offset(s - 2.dp.toPx(), s - 2.dp.toPx()), strokeWidth = 1.5.dp.toPx())
                                val path = Path().apply {
                                    moveTo(4.dp.toPx(), s * 0.7f)
                                    lineTo(s * 0.4f, s * 0.4f)
                                    lineTo(s * 0.6f, s * 0.55f)
                                    lineTo(s - 4.dp.toPx(), s * 0.2f)
                                }
                                drawPath(path, glowPurple, style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round))
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Agent C Analysis", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                        Text(res, fontSize = 13.sp, color = Color.White.copy(alpha = 0.85f), lineHeight = 20.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
fun SliderRow(
    label: String,
    value: Float,
    min: Float,
    max: Float,
    color: Color,
    formatLabel: (Float) -> String,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.padding(bottom = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.width(90.dp))
            Spacer(modifier = Modifier.weight(1f))
            Text(formatLabel(value), fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                color = if (value >= 0) Color(0xFF00E676) else Color(0xFFFF5252))
        }
        Slider(
            value = value, onValueChange = onValueChange,
            valueRange = min..max,
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.colors(
                thumbColor = color, activeTrackColor = color,
                inactiveTrackColor = Color.White.copy(alpha = 0.06f)
            )
        )
    }
}

@Composable
fun AgentChatScreen(onBack: () -> Unit = {}) {
    val bgColor = Color(0xFF060B28)
    val cardBg = Color(0xFF0F1B2E)
    val accentCyan = Color(0xFF00E5A0)
    val glowBlue = Color(0xFF448AFF)
    val glowPurple = Color(0xFFBB86FC)

    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var chatHistory by remember { mutableStateOf(listOf<ChatTurnDto>()) }
    var error by remember { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()

    // Waveform animation for "thinking"
    val infiniteTransition = rememberInfiniteTransition(label = "chatWave")
    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "chatWavePhase"
    )

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(chatHistory.size) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Column(
        modifier = Modifier.fillMaxSize().background(bgColor)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Canvas(modifier = Modifier.size(28.dp).clickable { onBack() }) {
                drawLine(Color.White, Offset(18.dp.toPx(), 4.dp.toPx()), Offset(4.dp.toPx(), 14.dp.toPx()), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                drawLine(Color.White, Offset(4.dp.toPx(), 14.dp.toPx()), Offset(18.dp.toPx(), 24.dp.toPx()), strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
            }
            Spacer(modifier = Modifier.width(12.dp))
            // Agent orb
            Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(32.dp)) {
                    drawCircle(brush = Brush.radialGradient(listOf(glowBlue.copy(alpha = 0.6f), Color.Transparent)), radius = 16.dp.toPx())
                    drawCircle(color = glowBlue, radius = 9.dp.toPx())
                    drawCircle(color = Color.White.copy(alpha = 0.85f), radius = 4.dp.toPx())
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text("Agent C", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Text(if (isSending) "Reasoning..." else "Online", fontSize = 11.sp, color = if (isSending) glowPurple else accentCyan)
            }
        }

        // Chat area
        Box(
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp)
        ) {
            if (chatHistory.isEmpty() && !isSending) {
                // Empty state
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Canvas(modifier = Modifier.size(80.dp)) {
                        drawCircle(brush = Brush.radialGradient(listOf(glowBlue.copy(alpha = 0.15f), Color.Transparent)), radius = 40.dp.toPx())
                        drawCircle(color = glowBlue.copy(alpha = 0.3f), radius = 20.dp.toPx())
                        drawCircle(color = glowBlue, radius = 10.dp.toPx())
                        drawCircle(color = Color.White.copy(alpha = 0.8f), radius = 4.dp.toPx())
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Text("Ask Agent C anything", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.7f))
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Your personal finance reasoning engine", fontSize = 12.sp, color = Color.White.copy(alpha = 0.4f))
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(scrollState)
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    chatHistory.forEach { turn ->
                        val isUser = turn.role == "user"
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
                        ) {
                            if (!isUser) {
                                // Agent avatar
                                Box(modifier = Modifier.size(28.dp).padding(top = 4.dp), contentAlignment = Alignment.Center) {
                                    Canvas(modifier = Modifier.size(24.dp)) {
                                        drawCircle(color = glowBlue, radius = 8.dp.toPx())
                                        drawCircle(color = Color.White.copy(alpha = 0.7f), radius = 3.dp.toPx())
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            Surface(
                                modifier = Modifier.widthIn(max = 280.dp),
                                shape = RoundedCornerShape(
                                    topStart = 16.dp, topEnd = 16.dp,
                                    bottomStart = if (isUser) 16.dp else 4.dp,
                                    bottomEnd = if (isUser) 4.dp else 16.dp
                                ),
                                color = if (isUser) glowBlue.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.06f),
                                border = BorderStroke(0.5.dp, if (isUser) glowBlue.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.08f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    if (!isUser) {
                                        Text("Agent C", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = glowBlue.copy(alpha = 0.7f),
                                            modifier = Modifier.padding(bottom = 4.dp))
                                    }
                                    Text(turn.content, fontSize = 13.sp, color = Color.White.copy(alpha = 0.9f), lineHeight = 19.sp)
                                }
                            }
                        }
                    }

                    // Thinking waveform
                    if (isSending) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Box(modifier = Modifier.size(28.dp).padding(top = 4.dp), contentAlignment = Alignment.Center) {
                                Canvas(modifier = Modifier.size(24.dp)) {
                                    drawCircle(color = glowPurple, radius = 8.dp.toPx())
                                    drawCircle(color = Color.White.copy(alpha = 0.7f), radius = 3.dp.toPx())
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Canvas(modifier = Modifier.width(120.dp).height(24.dp)) {
                                val w = size.width; val h = size.height
                                val barCount = 20
                                val barWidth = 3.dp.toPx()
                                val gap = (w - barCount * barWidth) / (barCount - 1)
                                for (i in 0 until barCount) {
                                    val phase = (wavePhase + i * 18f) % 360f
                                    val amp = (kotlin.math.sin(Math.toRadians(phase.toDouble())).toFloat() + 1f) / 2f
                                    val barH = h * 0.15f + h * 0.7f * amp
                                    drawRoundRect(
                                        color = glowPurple.copy(alpha = 0.4f + amp * 0.5f),
                                        topLeft = Offset(i * (barWidth + gap), (h - barH) / 2),
                                        size = androidx.compose.ui.geometry.Size(barWidth, barH),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        // Suggestion chips
        if (chatHistory.isEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(
                    "Summarize my day",
                    "How is my risk level?",
                    "What if I invest \u20B910k?",
                    "Where can I cut costs?",
                    "Tips for this month"
                ).forEach { suggestion ->
                    Surface(
                        modifier = Modifier.clickable {
                            input = suggestion
                        },
                        shape = RoundedCornerShape(16.dp),
                        color = glowBlue.copy(alpha = 0.08f),
                        border = BorderStroke(0.5.dp, glowBlue.copy(alpha = 0.2f))
                    ) {
                        Text(suggestion, fontSize = 12.sp, color = glowBlue.copy(alpha = 0.8f),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                    }
                }
            }
        }

        // Error
        error?.let {
            Text(it, color = Color(0xFFFFA726), fontSize = 11.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
        }

        // Input bar
        Row(
            modifier = Modifier.fillMaxWidth()
                .background(Color(0xFF0A1025))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(20.dp),
                color = Color.White.copy(alpha = 0.06f),
                border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.1f))
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Ask anything...", fontSize = 14.sp, color = Color.White.copy(alpha = 0.35f)) },
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        cursorColor = glowBlue,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            // Send button
            Surface(
                modifier = Modifier.size(44.dp).clickable {
                    val question = input.trim()
                    if (question.isNotEmpty() && !isSending) {
                        isSending = true; error = null
                        val updatedHistory = chatHistory + ChatTurnDto(role = "user", content = question)
                        chatHistory = updatedHistory
                        input = ""
                        scope.launch {
                            try {
                                val userText = updatedHistory.filter { it.role == "user" }.joinToString("; ") { it.content }
                                val body = SynthesizeChatRequestDto(
                                    finance_insight = "User-described personal finance situation: $userText",
                                    news_insight = "User-described news/market view: $userText",
                                    user_question = question,
                                    history = updatedHistory
                                )
                                val res = ApiClient.api.synthesizeChat(body)
                                chatHistory = chatHistory + ChatTurnDto(role = "assistant", content = res.recommendation)
                            } catch (e: Exception) {
                                error = "Error: ${e.message}"
                            } finally { isSending = false }
                        }
                    }
                },
                shape = CircleShape,
                color = if (input.isNotBlank() && !isSending) glowBlue else glowBlue.copy(alpha = 0.3f)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Canvas(modifier = Modifier.size(18.dp)) {
                        val path = Path().apply {
                            moveTo(3.dp.toPx(), 14.dp.toPx())
                            lineTo(9.dp.toPx(), 3.dp.toPx())
                            lineTo(15.dp.toPx(), 14.dp.toPx())
                            lineTo(9.dp.toPx(), 11.dp.toPx())
                            close()
                        }
                        drawPath(path, Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun TransactionParserScreen() {
    val scope = rememberCoroutineScope()
    var smsText by remember { mutableStateOf("") }
    var parseResult by remember { mutableStateOf("Result will appear here") }
    var history by remember { mutableStateOf<List<TransactionDto>>(emptyList()) }

    LaunchedEffect(Unit) {
        try {
            history = ApiClient.api.getTransactions()
        } catch (_: Exception) {
            history = emptyList()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A237E))
            .padding(16.dp)
    ) {
        Text(
            text = "Parse Transaction SMS",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        OutlinedTextField(
            value = smsText,
            onValueChange = { smsText = it },
            label = { Text("Paste transaction SMS here") },
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp),
            maxLines = 5
        )
        Button(
            onClick = {
                if (smsText.isNotBlank()) {
                    parseResult = "Sending..."
                    scope.launch {
                        try {
                            val body = ParseMessageRequestDto(raw_message = smsText)
                            val tx = ApiClient.api.parseMessage(body)
                            parseResult =
                                "Amount: ${tx.amount}\nMerchant: ${tx.merchant ?: "-"}\nCategory: ${tx.category ?: "-"}\nTime: ${tx.timestamp}"
                        } catch (e: Exception) {
                            parseResult = "Error: ${e.message}"
                        }
                    }
                }
            },
            modifier = Modifier
                .padding(top = 16.dp)
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5C6BC0))
        ) {
            Text("Parse Transaction", fontSize = 16.sp)
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF283593)),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Result:",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = parseResult,
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Recent SMS History",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (history.isEmpty()) {
            Text(
                text = "No transactions captured yet. New SMS notifications will appear here.",
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.8f)
            )
        } else {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                history.take(20).forEach { tx ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF303F9F)),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "₹ ${String.format("%.2f", tx.amount)} • ${tx.category ?: "-"}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                            Text(
                                text = tx.merchant ?: "No merchant",
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                            Text(
                                text = tx.timestamp,
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun IntroScreen(onGetStarted: () -> Unit) {
    var currentPage by remember { mutableStateOf(0) }
    val pages = listOf(
        OnboardingPage(
            emoji = "💰",
            title = "Welcome to Agentic Finance",
            description = "Get monthly money tips and stay on top of your finance",
            backgroundColor = Color(0xFF1A237E)
        ),
        OnboardingPage(
            emoji = "📊",
            title = "Understand your financial habits",
            description = "Analyze your finance with beautiful, simple and easy to understand",
            backgroundColor = Color(0xFF0D47A1)
        ),
        OnboardingPage(
            emoji = "🎯",
            title = "Make your spending stress-free",
            description = "You can follow me if you wanted comment on any to get some freebies",
            backgroundColor = Color(0xFF1565C0)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(pages[currentPage].backgroundColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Skip button
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterEnd
            ) {
                TextButton(onClick = onGetStarted) {
                    Text(
                        text = "Skip",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 16.sp
                    )
                }
            }

            // Content
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                if (currentPage == 0) {
                    Image(
                        painter = painterResource(id = R.drawable.pinance_logo),
                        contentDescription = "Pinance Logo",
                        modifier = Modifier
                            .padding(bottom = 32.dp)
                            .size(140.dp)
                    )
                } else {
                    Text(
                        text = pages[currentPage].emoji,
                        fontSize = 120.sp,
                        modifier = Modifier.padding(bottom = 48.dp)
                    )
                }
                Text(
                    text = pages[currentPage].title,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                Text(
                    text = pages[currentPage].description,
                    fontSize = 16.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center,
                    lineHeight = 24.sp
                )
            }

            // Bottom navigation
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Page indicators
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(bottom = 32.dp)
                ) {
                    repeat(pages.size) { index ->
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .size(
                                    width = if (index == currentPage) 24.dp else 8.dp,
                                    height = 8.dp
                                )
                                .background(
                                    color = if (index == currentPage)
                                        Color.White
                                    else
                                        Color.White.copy(alpha = 0.3f),
                                    shape = MaterialTheme.shapes.small
                                )
                        )
                    }
                }

                // Next/Get Started button
                Button(
                    onClick = {
                        if (currentPage < pages.size - 1) {
                            currentPage++
                        } else {
                            onGetStarted()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White,
                        contentColor = pages[currentPage].backgroundColor
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        text = if (currentPage < pages.size - 1) "Next" else "Get Started",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

data class OnboardingPage(
    val emoji: String,
    val title: String,
    val description: String,
    val backgroundColor: Color
)

data class UserProfile(
    val name: String,
    val occupation: String,
    val email: String?,
    val monthlyIncome: Double,
    val monthlyExpenses: Double,
    val monthlySavings: Double,
    val notificationsEnabled: Boolean,
    val acceptedDisclaimer: Boolean
)

fun loadUserProfile(context: android.content.Context): UserProfile? {
    val prefs = context.getSharedPreferences("agentic_finance_profile", android.content.Context.MODE_PRIVATE)
    val name = prefs.getString("name", null) ?: return null
    val occupation = prefs.getString("occupation", "") ?: ""
    val email = prefs.getString("email", null)
    val income = prefs.getFloat("income", 0f).toDouble()
    val expenses = prefs.getFloat("expenses", 0f).toDouble()
    val savings = prefs.getFloat("savings", 0f).toDouble()
    val notificationsEnabled = prefs.getBoolean("notificationsEnabled", true)
    val acceptedDisclaimer = prefs.getBoolean("acceptedDisclaimer", false)

    return UserProfile(
        name = name,
        occupation = occupation,
        email = email,
        monthlyIncome = income,
        monthlyExpenses = expenses,
        monthlySavings = savings,
        notificationsEnabled = notificationsEnabled,
        acceptedDisclaimer = acceptedDisclaimer
    )
}

fun saveUserProfile(context: android.content.Context, profile: UserProfile) {
    val prefs = context.getSharedPreferences("agentic_finance_profile", android.content.Context.MODE_PRIVATE)
    prefs.edit()
        .putString("name", profile.name)
        .putString("occupation", profile.occupation)
        .putString("email", profile.email)
        .putFloat("income", profile.monthlyIncome.toFloat())
        .putFloat("expenses", profile.monthlyExpenses.toFloat())
        .putFloat("savings", profile.monthlySavings.toFloat())
        .putBoolean("notificationsEnabled", profile.notificationsEnabled)
        .putBoolean("acceptedDisclaimer", profile.acceptedDisclaimer)
        .apply()
}

@Composable
fun LoginScreen(
    onLogin: (String) -> Unit,
    onGuest: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D102B))
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1F2A6B)),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "Welcome back",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "Sign in to see your AI finance insights",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                )

                error?.let {
                    Text(
                        text = it,
                        color = Color(0xFFFFCDD2),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                Button(
                    onClick = {
                        if (email.isBlank() || !email.contains("@")) {
                            error = "Please enter a valid email to continue."
                        } else {
                            error = null
                            onLogin(email.trim())
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF64B5F6))
                ) {
                    Text("Login", fontSize = 16.sp)
                }

                TextButton(
                    onClick = { onGuest() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                ) {
                    Text(
                        text = "Continue as guest",
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }

                Text(
                    text = "Educational prototype only – not investment advice.",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
    }
}

@Composable
fun ProfileCreationScreen(
    email: String?,
    onProfileCreated: (UserProfile) -> Unit
) {
    var step by remember { mutableStateOf(0) }

    var name by remember { mutableStateOf("") }
    var occupation by remember { mutableStateOf("") }

    var monthlyIncome by remember { mutableStateOf("") }
    var monthlyExpenses by remember { mutableStateOf("") }
    var monthlySavings by remember { mutableStateOf("") }

    var notificationsEnabled by remember { mutableStateOf(true) }
    var acceptedDisclaimer by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D102B))
            .padding(24.dp)
    ) {
        Text(
            text = if (step == 0) "Create your profile" else "Tell us about your finances",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Text(
            text = "We use this only on your device to personalise insights.",
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.7f),
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1F2A6B)),
            elevation = CardDefaults.cardElevation(4.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                if (step == 0) {
                    Text(
                        text = "Step 1 of 2 – Basics",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    )

                    OutlinedTextField(
                        value = occupation,
                        onValueChange = { occupation = it },
                        label = { Text("Occupation (e.g., Student, Engineer)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    )

                    email?.let {
                        Text(
                            text = "Email: $it",
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                } else {
                    Text(
                        text = "Step 2 of 2 – Monthly snapshot",
                        fontSize = 13.sp,
                        color = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    OutlinedTextField(
                        value = monthlyIncome,
                        onValueChange = { monthlyIncome = it },
                        label = { Text("Monthly income (₹)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = monthlyExpenses,
                        onValueChange = { monthlyExpenses = it },
                        label = { Text("Monthly expenses (₹)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = monthlySavings,
                        onValueChange = { monthlySavings = it },
                        label = { Text("Monthly savings (₹)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Use notifications to auto-detect expenses",
                            fontSize = 13.sp,
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = notificationsEnabled,
                            onCheckedChange = { notificationsEnabled = it }
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = acceptedDisclaimer,
                            onCheckedChange = { acceptedDisclaimer = it }
                        )
                        Text(
                            text = "I understand this is educational only and not investment advice.",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            TextButton(
                onClick = {
                    if (step == 0) {
                        // no-op or exit
                    } else {
                        step = 0
                    }
                }
            ) {
                Text(if (step == 0) "" else "Back")
            }

            if (step == 0) {
                Button(
                    onClick = {
                        if (name.isNotBlank()) {
                            step = 1
                        }
                    },
                    enabled = name.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF64B5F6))
                ) {
                    Text("Next")
                }
            } else {
                val income = monthlyIncome.toDoubleOrNull() ?: 0.0
                val expenses = monthlyExpenses.toDoubleOrNull() ?: 0.0
                val savings = monthlySavings.toDoubleOrNull() ?: 0.0
                Button(
                    onClick = {
                        val profile = UserProfile(
                            name = name,
                            occupation = occupation,
                            email = email,
                            monthlyIncome = income,
                            monthlyExpenses = expenses,
                            monthlySavings = savings,
                            notificationsEnabled = notificationsEnabled,
                            acceptedDisclaimer = acceptedDisclaimer
                        )
                        onProfileCreated(profile)
                    },
                    enabled = acceptedDisclaimer,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF81C784))
                ) {
                    Text("Create Profile")
                }
            }
        }
    }
}

@Composable
fun NewsScreen(
    topicAnalysis: NewsAnalysisResponseDto?,
    newsFeed: List<NewsFeedArticleDto>,
    marketIndices: List<MarketIndexDto>,
    sectorIndices: List<SectorIndexDto>,
    isDataLoading: Boolean
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // ── Screen-local state ──
    val isLoadingNews = isDataLoading
    val isTopicAnalyzing = isDataLoading
    var newsError by remember { mutableStateOf<String?>(null) }
    var selectedArticle by remember { mutableStateOf<NewsFeedArticleDto?>(null) }
    var articleAnalysisResult by remember { mutableStateOf("") }
    var lastArticleAnalysis by remember { mutableStateOf<ArticleAnalysisResponseDto?>(null) }
    val analysisHistory = remember { mutableStateMapOf<String, MutableList<ArticleAnalysisResponseDto>>() }
    var topicError by remember { mutableStateOf<String?>(null) }

    // ── Sentiment gauge value: prefer live market %, fallback to FinBERT ──
    val niftyIdx = marketIndices.firstOrNull { it.name.contains("NIFTY 50") }
    val sentimentValue = if (niftyIdx != null && niftyIdx.change_percent != 0.0) {
        // Map market % change to -1..1 range (±3% = full swing)
        (niftyIdx.change_percent.toFloat() / 3f).coerceIn(-1f, 1f)
    } else when {
        topicAnalysis?.trend_label?.lowercase() == "bullish" -> 0.8f
        topicAnalysis?.trend_label?.lowercase() == "bearish" -> -0.8f
        topicAnalysis?.trend_label?.lowercase() == "sideways" -> 0f
        topicAnalysis?.overall_sentiment?.lowercase()?.contains("positive") == true -> 0.5f
        topicAnalysis?.overall_sentiment?.lowercase()?.contains("negative") == true -> -0.5f
        topicAnalysis != null -> 0f
        else -> 0f
    }

    val needleAnim = remember { Animatable(0f) }
    LaunchedEffect(sentimentValue) {
        needleAnim.animateTo(sentimentValue, tween(1500))
    }

    val sentimentLabel = if (niftyIdx != null && niftyIdx.change_percent != 0.0) {
        val arrow = if (niftyIdx.change_percent >= 0) "\u25B2" else "\u25BC"
        val trend = if (niftyIdx.change_percent >= 0) "BULLISH" else "BEARISH"
        "$trend $arrow ${"%+.2f".format(niftyIdx.change_percent)}%"
    } else when {
        topicAnalysis?.trend_label?.lowercase() == "bullish" -> "Bullish"
        topicAnalysis?.trend_label?.lowercase() == "bearish" -> "Bearish"
        topicAnalysis?.trend_label?.lowercase() == "sideways" -> "Sideways"
        topicAnalysis?.overall_sentiment?.lowercase()?.contains("positive") == true -> "Positive"
        topicAnalysis?.overall_sentiment?.lowercase()?.contains("negative") == true -> "Negative"
        topicAnalysis != null -> "Neutral"
        else -> "\u2014"
    }

    val sentimentSubLabel = if (niftyIdx != null) {
        "NIFTY 50  \u20B9${"%,.0f".format(niftyIdx.price)}"
    } else {
        topicAnalysis?.trend_label?.uppercase() ?: ""
    }

    val darkBg = Color(0xFF0A0E1A)
    val cardBg = Color(0xFF0F1B2E)
    val accent = Color(0xFF00E5A0)
    val cyanAccent = Color(0xFF00E5FF)

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(darkBg)
            .padding(16.dp)
    ) {
        if (selectedArticle == null) {
            // ──────────────── MAIN FEED VIEW ────────────────
            if (isLoadingNews && newsFeed.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = cyanAccent)
                }
            } else {
                Column(modifier = Modifier.verticalScroll(scrollState)) {

                    // ─── 1. Market Sentiment Gauge ───
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Market Sentiment",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Text("\uD83D\uDCCA", fontSize = 18.sp)
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            if (isTopicAnalyzing) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(160.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = cyanAccent, strokeWidth = 2.dp)
                                }
                            } else {
                                val currentNeedle = needleAnim.value
                                Canvas(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(160.dp)
                                ) {
                                    val w = size.width
                                    val h = size.height
                                    val strokeW = 12.dp.toPx()
                                    val radius = (w * 0.35f).coerceAtMost(h * 0.7f)
                                    val cx = w / 2f
                                    val cy = h * 0.85f
                                    val arcDiam = radius * 2f

                                    val arcColors = listOf(
                                        Color(0xFFFF1744),
                                        Color(0xFFFF9100),
                                        Color(0xFFFFEA00),
                                        Color(0xFF00E676),
                                        Color(0xFF00E5FF),
                                        Color(0xFF2979FF),
                                        Color(0xFFD500F9)
                                    )
                                    val segAngle = 180f / arcColors.size
                                    arcColors.forEachIndexed { i, c ->
                                        drawArc(
                                            color = c,
                                            startAngle = 180f + i * segAngle,
                                            sweepAngle = segAngle + 0.5f,
                                            useCenter = false,
                                            topLeft = Offset(cx - radius, cy - radius),
                                            size = androidx.compose.ui.geometry.Size(arcDiam, arcDiam),
                                            style = Stroke(width = strokeW, cap = StrokeCap.Round)
                                        )
                                    }

                                    // Needle
                                    val needleDeg = 180f + (currentNeedle + 1f) / 2f * 180f
                                    val needleRad = needleDeg * Math.PI.toFloat() / 180f
                                    val needleLen = radius * 0.65f
                                    val endX = cx + needleLen * Math.cos(needleRad.toDouble()).toFloat()
                                    val endY = cy + needleLen * Math.sin(needleRad.toDouble()).toFloat()

                                    drawLine(
                                        Color.White,
                                        Offset(cx, cy),
                                        Offset(endX, endY),
                                        strokeWidth = 3.dp.toPx()
                                    )
                                    drawCircle(
                                        Color.White,
                                        radius = 6.dp.toPx(),
                                        center = Offset(cx, cy)
                                    )
                                }
                            }

                            // Sentiment label + live index
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                val labelColor = if (niftyIdx != null && niftyIdx.change_percent != 0.0) {
                                    if (niftyIdx.change_percent >= 0) Color(0xFF00E676) else Color(0xFFFF5252)
                                } else cyanAccent
                                Text(
                                    text = sentimentLabel,
                                    color = labelColor,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                                if (sentimentSubLabel.isNotEmpty()) {
                                    Text(
                                        text = sentimentSubLabel,
                                        color = Color.White.copy(alpha = 0.6f),
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                // Live index tickers
                                if (marketIndices.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceEvenly
                                    ) {
                                        marketIndices.forEach { idx ->
                                            val pctColor = if (idx.change_percent >= 0) Color(0xFF00E676) else Color(0xFFFF5252)
                                            val arrow = if (idx.change_percent >= 0) "\u25B2" else "\u25BC"
                                            Card(
                                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0A1428)),
                                                shape = RoundedCornerShape(10.dp),
                                                border = BorderStroke(1.dp, pctColor.copy(alpha = 0.2f))
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                                    horizontalAlignment = Alignment.CenterHorizontally
                                                ) {
                                                    Text(idx.name, fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f), fontWeight = FontWeight.Medium)
                                                    Text("\u20B9${"%,.0f".format(idx.price)}", fontSize = 16.sp, color = Color.White, fontWeight = FontWeight.ExtraBold)
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text(
                                                            "$arrow ${"%+.2f".format(idx.change_percent)}%",
                                                            fontSize = 13.sp,
                                                            color = pctColor,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        Text(
                                                            "  (${"%+.0f".format(idx.change)})",
                                                            fontSize = 10.sp,
                                                            color = pctColor.copy(alpha = 0.6f)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ─── Topic error card ───
                    topicError?.let { err ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF2C1010)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    "Analysis Error",
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFFF5252)
                                )
                                Text(err, fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
                            }
                        }
                    }

                    // ─── 2. Agentic Briefing ───
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBg),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Agentic Briefing",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    "\u22EE",
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 20.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = topicAnalysis?.llm_summary
                                    ?: topicAnalysis?.summary
                                    ?: "Fetching market intelligence\u2026",
                                color = Color.White.copy(alpha = 0.75f),
                                fontSize = 13.sp,
                                lineHeight = 20.sp
                            )
                        }
                    }

                    // ─── 3. Sector Pulse (Live NSE Data) ───
                    if (sectorIndices.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Sector Pulse",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text("LIVE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF00E676),
                                modifier = Modifier
                                    .background(Color(0xFF00E676).copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp))
                        }

                        // Top row: 3 sectors
                        val topSectors = sectorIndices.take(3)
                        val bottomSectors = sectorIndices.drop(3).take(3)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            topSectors.forEach { sector ->
                                val isUp = sector.change_percent >= 0
                                val chipColor = if (isUp) Color(0xFF00E676) else Color(0xFFFF5252)
                                Card(
                                    modifier = Modifier.weight(1f),
                                    colors = CardDefaults.cardColors(containerColor = cardBg),
                                    shape = RoundedCornerShape(12.dp),
                                    border = BorderStroke(1.dp, chipColor.copy(alpha = 0.25f))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            sector.display_name,
                                            color = Color.White,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 12.sp,
                                            maxLines = 1
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        val arrow = if (isUp) "\u25B2" else "\u25BC"
                                        Text(
                                            "$arrow ${"%+.2f".format(sector.change_percent)}%",
                                            color = chipColor,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        Text(
                                            "\u20B9${"%,.0f".format(sector.price)}",
                                            color = Color.White.copy(alpha = 0.5f),
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }
                        // Bottom row: 3 more sectors
                        if (bottomSectors.isNotEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                bottomSectors.forEach { sector ->
                                    val isUp = sector.change_percent >= 0
                                    val chipColor = if (isUp) Color(0xFF00E676) else Color(0xFFFF5252)
                                    Card(
                                        modifier = Modifier.weight(1f),
                                        colors = CardDefaults.cardColors(containerColor = cardBg),
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, chipColor.copy(alpha = 0.25f))
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(10.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                sector.display_name,
                                                color = Color.White,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 12.sp,
                                                maxLines = 1
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            val arrow = if (isUp) "\u25B2" else "\u25BC"
                                            Text(
                                                "$arrow ${"%+.2f".format(sector.change_percent)}%",
                                                color = chipColor,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                "\u20B9${"%,.0f".format(sector.price)}",
                                                color = Color.White.copy(alpha = 0.5f),
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ─── News error card ───
                    newsError?.let { errMsg ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF2C1010)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    "Error loading news",
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFFF5252)
                                )
                                Text(
                                    errMsg,
                                    fontSize = 13.sp,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }

                    // ─── 4. Live Market Feed ───
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Live Market Feed",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Text(
                            "\u2022\u2022\u2022",
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 16.sp
                        )
                    }

                    if (newsFeed.isEmpty() && newsError == null) {
                        Text(
                            "No news available right now.",
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }

                    newsFeed.forEach { article ->
                        // Derive sentiment from summary keywords
                        val sentiment = run {
                            val lower = article.summary.lowercase()
                            val posWords = listOf(
                                "surge", "gain", "rise", "bull", "growth",
                                "profit", "rally", "recovery", "upbeat", "optimis",
                                "positive", "strong"
                            )
                            val negWords = listOf(
                                "fall", "drop", "crash", "bear", "loss",
                                "decline", "slump", "downturn", "pessimis", "fear",
                                "negative", "weak"
                            )
                            val posCount = posWords.count { lower.contains(it) }
                            val negCount = negWords.count { lower.contains(it) }
                            when {
                                posCount > negCount -> "Positive"
                                negCount > posCount -> "Negative"
                                else -> "Neutral"
                            }
                        }
                        val sentimentColor = when (sentiment) {
                            "Positive" -> Color(0xFF00E676)
                            "Negative" -> Color(0xFFFF5252)
                            else -> Color(0xFF9E9E9E)
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp)
                                .clickable { selectedArticle = article },
                            colors = CardDefaults.cardColors(containerColor = cardBg),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                // Headline + summary
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = article.title,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (article.summary.isNotBlank()) {
                                        Text(
                                            text = article.summary,
                                            color = Color.White.copy(alpha = 0.6f),
                                            fontSize = 12.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.width(8.dp))

                                // Sentiment pill
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = sentimentColor.copy(alpha = 0.12f),
                                    border = BorderStroke(1.dp, sentimentColor.copy(alpha = 0.5f))
                                ) {
                                    Text(
                                        text = sentiment,
                                        color = sentimentColor,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(
                                            horizontal = 8.dp,
                                            vertical = 4.dp
                                        )
                                    )
                                }

                                // Chevron
                                Text(
                                    "\u203A",
                                    color = Color.White.copy(alpha = 0.4f),
                                    fontSize = 20.sp,
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // ──────────────── DETAIL VIEW ────────────────
            val article = selectedArticle!!

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                TextButton(onClick = {
                    selectedArticle = null
                    articleAnalysisResult = ""
                    lastArticleAnalysis = null
                }) {
                    Text(text = "\u2190 Back", color = Color.White)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "News Detail",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Text(
                text = article.title,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Text(
                text = "Source: ${article.source}",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.padding(bottom = 4.dp)
            )

            if (!article.image_url.isNullOrBlank()) {
                AsyncImage(
                    model = article.image_url,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .padding(bottom = 8.dp)
                )
            }

            var webViewLoading by remember { mutableStateOf(false) }
            var webViewError by remember { mutableStateOf<String?>(null) }

            if (!article.url.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(bottom = 8.dp)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(
                                        view: WebView?,
                                        url: String?,
                                        favicon: android.graphics.Bitmap?
                                    ) {
                                        webViewLoading = true
                                        webViewError = null
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        webViewLoading = false
                                    }

                                    override fun onReceivedError(
                                        view: WebView?,
                                        request: android.webkit.WebResourceRequest?,
                                        error: android.webkit.WebResourceError?
                                    ) {
                                        if (request?.isForMainFrame == true) {
                                            webViewLoading = false
                                            webViewError = "Could not load article page."
                                        }
                                    }
                                }
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                loadUrl(article.url!!)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    if (webViewLoading) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = cyanAccent)
                        }
                    }

                    if (webViewError != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF2C1010)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = webViewError ?: "Error loading page",
                                color = Color.White,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            } else {
                Text(
                    text = article.summary,
                    fontSize = 14.sp,
                    color = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier
                        .weight(1f)
                        .padding(bottom = 8.dp)
                )
            }

            var isAnalyzing by remember { mutableStateOf(false) }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        isAnalyzing = true
                        articleAnalysisResult = ""
                        scope.launch {
                            try {
                                val body = ArticleAnalysisRequestDto(
                                    title = article.title,
                                    summary = article.summary,
                                    url = article.url
                                )
                                val res = ApiClient.api.analyzeArticle(body)
                                lastArticleAnalysis = res
                                val key = res.company.ifBlank { article.title }
                                val list = analysisHistory.getOrPut(key) { mutableListOf() }
                                list.add(0, res)
                                if (list.size > 5) {
                                    list.removeLast()
                                }
                                articleAnalysisResult = buildString {
                                    appendLine("Company: ${res.company}")
                                    appendLine("Sector: ${res.sector}")
                                    appendLine("Trend: ${res.trend}")
                                    appendLine("Impact: ${res.impact_strength.capitalize()} news")
                                    appendLine("Recommendation: ${res.recommendation}")
                                    appendLine("Confidence: ${String.format("%.0f", res.confidence_score * 100)}% (${res.confidence_level})")
                                    appendLine()
                                    appendLine("Verdict: ${res.trend.replace("_", " ")} for ${res.company}.")
                                    appendLine()
                                    appendLine("Reasoning:")
                                    appendLine(res.reasoning)
                                    appendLine()
                                    appendLine("Why this confidence:")
                                    appendLine(res.confidence_explanation)
                                }
                            } catch (e: Exception) {
                                articleAnalysisResult = "Error: ${e.message}"
                            } finally {
                                isAnalyzing = false
                            }
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    colors = ButtonDefaults.buttonColors(containerColor = accent)
                ) {
                    if (isAnalyzing) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Analyzing...")
                    } else {
                        Text("Analyze this News")
                    }
                }

                OutlinedButton(
                    onClick = {
                        val shareText = buildString {
                            append(article.title)
                            if (!article.url.isNullOrBlank()) {
                                append("\n")
                                append(article.url)
                            }
                        }
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, shareText)
                        }
                        context.startActivity(Intent.createChooser(intent, "Share article"))
                    },
                    modifier = Modifier
                        .width(80.dp)
                        .fillMaxHeight(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Text("Share", fontSize = 12.sp)
                }
            }

            if (articleAnalysisResult.isNotBlank()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Analysis",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        lastArticleAnalysis?.let { analysis ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 8.dp)
                            ) {
                                val trendColor = when (analysis.trend) {
                                    "strong_uptrend", "uptrend" -> Color(0xFF4CAF50)
                                    "downtrend", "strong_downtrend" -> Color(0xFFF44336)
                                    else -> Color(0xFF9E9E9E)
                                }
                                val impactColor = when (analysis.impact_strength.lowercase()) {
                                    "high" -> Color(0xFFFFA000)
                                    "medium" -> Color(0xFF29B6F6)
                                    else -> Color(0xFF9E9E9E)
                                }

                                PillChip(
                                    text = when (analysis.trend) {
                                        "strong_uptrend" -> "\u2B06 Strong Uptrend"
                                        "uptrend" -> "\u2B06 Uptrend"
                                        "downtrend" -> "\u2B07 Downtrend"
                                        "strong_downtrend" -> "\u2B07 Strong Downtrend"
                                        else -> "\u2192 Sideways"
                                    },
                                    color = trendColor
                                )

                                PillChip(
                                    text = "Impact: ${analysis.impact_strength.capitalize()}",
                                    color = impactColor
                                )
                            }

                            val key = analysis.company.ifBlank { article.title }
                            val historyList = analysisHistory[key]
                            if (historyList != null && historyList.size > 1) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Recent sentiment for this company:",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier.padding(bottom = 2.dp)
                                )
                                historyList.take(3).forEachIndexed { index, item ->
                                    Text(
                                        text = "${index + 1}. ${item.trend} \u00B7 Impact ${item.impact_strength}",
                                        fontSize = 11.sp,
                                        color = Color.White.copy(alpha = 0.8f)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                            }
                        }
                        Text(
                            text = articleAnalysisResult,
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.95f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PillChip(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.18f),
        shape = RoundedCornerShape(50)
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun FinanceScreen() {
    val scope = rememberCoroutineScope()

    var smsText by remember { mutableStateOf("") }
    var parseResult by remember { mutableStateOf("Result will appear here") }

    var startDate by remember { mutableStateOf("") }
    var endDate by remember { mutableStateOf("") }
    var financeResult by remember { mutableStateOf("") }

    var newsTopic by remember { mutableStateOf("") }
    var newsResult by remember { mutableStateOf("") }

    var financeInsight by remember { mutableStateOf("") }
    var newsInsight by remember { mutableStateOf("") }
    var recoResult by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Transaction Parser", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = smsText,
            onValueChange = { smsText = it },
            label = { Text("Paste transaction SMS here") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = {
                if (smsText.isNotBlank()) {
                    parseResult = "Sending..."
                    scope.launch {
                        try {
                            val body = ParseMessageRequestDto(raw_message = smsText)
                            val tx = ApiClient.api.parseMessage(body)
                            parseResult =
                                "Amount: ${tx.amount}\nMerchant: ${tx.merchant ?: "-"}\nCategory: ${tx.category ?: "-"}\nTime: ${tx.timestamp}"
                        } catch (e: Exception) {
                            parseResult = "Error: ${e.message}"
                        }
                    }
                }
            },
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
        ) {
            Text("Send to Backend")
        }
        Text(
            text = parseResult,
            modifier = Modifier.padding(top = 8.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text("Finance Analysis", style = MaterialTheme.typography.titleMedium)
        Row(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = startDate,
                onValueChange = { startDate = it },
                label = { Text("Start date (YYYY-MM-DD)") },
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedTextField(
                value = endDate,
                onValueChange = { endDate = it },
                label = { Text("End date (YYYY-MM-DD)") },
                modifier = Modifier.weight(1f)
            )
        }
        Button(
            onClick = {
                if (startDate.isNotBlank() && endDate.isNotBlank()) {
                    financeResult = "Analyzing..."
                    scope.launch {
                        try {
                            val body = FinanceAnalysisRequestDto(start_date = startDate, end_date = endDate)
                            val res = ApiClient.api.analyzeFinance(body)
                            financeResult = buildString {
                                appendLine(res.message)
                                appendLine("Total spent: ${res.summary.total_spent}")
                                appendLine("Transactions: ${res.summary.transactions_count}")
                                appendLine("Top category: ${res.summary.top_category ?: "-"}")
                            }
                            // Pre-fill finance insight field to help with synthesis
                            financeInsight = res.message
                        } catch (e: Exception) {
                            financeResult = "Error: ${e.message}"
                        }
                    }
                }
            },
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
        ) {
            Text("Analyze Finance")
        }
        Text(
            text = financeResult,
            modifier = Modifier.padding(top = 8.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text("News Analysis", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = newsTopic,
            onValueChange = { newsTopic = it },
            label = { Text("Topic (e.g. NIFTY, BANKING)") },
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = {
                if (newsTopic.isNotBlank()) {
                    newsResult = "Analyzing..."
                    scope.launch {
                        try {
                            val body = NewsAnalysisRequestDto(topic = newsTopic)
                            val res = ApiClient.api.analyzeNews(body)
                            newsResult = buildString {
                                appendLine("Overall sentiment: ${res.overall_sentiment}")
                                if (!res.dominant_sector.isNullOrBlank()) {
                                    appendLine("Dominant sector: ${res.dominant_sector}")
                                }
                                if (!res.trend_label.isNullOrBlank() && res.trend_confidence != null) {
                                    appendLine("ML trend: ${res.trend_label.uppercase()} (≈ ${"%.0f".format(res.trend_confidence * 100)}% confidence)")
                                }
                                appendLine(res.summary)
                            }
                            // Pre-fill news insight for synthesis
                            newsInsight = res.summary
                        } catch (e: Exception) {
                            newsResult = "Error: ${e.message}"
                        }
                    }
                }
            },
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
        ) {
            Text("Analyze News")
        }
        Text(
            text = newsResult,
            modifier = Modifier.padding(top = 8.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text("Recommendation (Agent C)", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = financeInsight,
            onValueChange = { financeInsight = it },
            label = { Text("Finance insight text") },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = newsInsight,
            onValueChange = { newsInsight = it },
            label = { Text("News insight text") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
        )
        Button(
            onClick = {
                if (financeInsight.isNotBlank() && newsInsight.isNotBlank()) {
                    recoResult = "Synthesizing..."
                    scope.launch {
                        try {
                            val body = RecommendationRequestDto(
                                finance_insight = financeInsight,
                                news_insight = newsInsight,
                            )
                            val res = ApiClient.api.synthesize(body)
                            recoResult = buildString {
                                appendLine("Recommendation: ${res.recommendation}")
                                appendLine()
                                appendLine("Rationale: ${res.rationale}")
                            }
                        } catch (e: Exception) {
                            recoResult = "Error: ${e.message}"
                        }
                    }
                }
            },
            modifier = Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
        ) {
            Text("Get Recommendation")
        }
        Text(
            text = recoResult,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

@Composable
fun LearnScreen() {
    val scope = rememberCoroutineScope()
    var currentCard by remember { mutableStateOf<LearningCardDto?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    
    // Quiz state
    var selectedOption by remember { mutableStateOf(-1) }
    var isSubmitting by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<SubmitAnswerResponseDto?>(null) }

    fun loadNextCard() {
        scope.launch {
            isLoading = true
            error = null
            feedback = null
            selectedOption = -1
            try {
                val response = ApiClient.api.getNextCard()
                currentCard = response.card
            } catch (e: Exception) {
                error = e.message ?: "Failed to load learning card."
            } finally {
                isLoading = false
            }
        }
    }

    fun submitAnswer() {
        val card = currentCard ?: return
        if (selectedOption == -1) return
        
        scope.launch {
            isSubmitting = true
            try {
                val response = ApiClient.api.submitAnswer(
                    SubmitAnswerRequestDto(
                        card_id = card.id,
                        answer_index = selectedOption,
                        time_spent_seconds = 15
                    )
                )
                feedback = response
            } catch (e: Exception) {
                error = e.message ?: "Failed to submit answer."
            } finally {
                isSubmitting = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadNextCard()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF060B28))
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Adaptive Learning",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        if (isLoading) {
             Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                 CircularProgressIndicator(color = Color.White)
             }
        } else if (error != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFB71C1C)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Error", fontWeight = FontWeight.Bold, color = Color.White)
                    Text(error ?: "", color = Color.White)
                    Button(onClick = { loadNextCard() }, modifier = Modifier.padding(top = 8.dp)) {
                        Text("Retry")
                    }
                }
            }
        } else if (currentCard != null) {
            val card = currentCard!!
            
            // Content Card
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF151A3D))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Topic: ${card.concept_id}", 
                        fontSize = 12.sp, 
                        color = Color(0xFF64B5F6),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = card.content, 
                        fontSize = 16.sp,
                        color = Color.White,
                        lineHeight = 24.sp
                    )
                }
            }
            
            // Quiz Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1F2A6B))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Quick Quiz",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Text(
                        text = card.quiz.question,
                        fontSize = 16.sp,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    card.quiz.options.forEachIndexed { index, option ->
                        val isSelected = selectedOption == index
                        val isCorrectOption = index == card.quiz.correct_answer_index
                        // Show colors only if feedback exists
                        val backgroundColor = when {
                            feedback != null && isCorrectOption -> Color(0xFF2E7D32) // Correct -> Green
                            feedback != null && isSelected && !isCorrectOption -> Color(0xFFC62828) // Wrong selection -> Red
                            isSelected -> Color(0xFF3949AB) // Selected -> Blue
                            else -> Color(0xFF0D1235) // Default -> Dark Blue
                        }
                        
                        Card(
                            colors = CardDefaults.cardColors(containerColor = backgroundColor),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable(enabled = feedback == null) { selectedOption = index }
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(Color.White.copy(alpha=0.1f), RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = ('A' + index).toString(),
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = option,
                                    color = Color.White,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (feedback == null) {
                         Button(
                            onClick = { submitAnswer() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = selectedOption != -1 && !isSubmitting,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                        ) {
                            Text(if (isSubmitting) "Checking..." else "Check Answer")
                        }
                    } else {
                        // Feedback Section
                        Text(
                            text = if (feedback!!.is_correct) "Correct!" else "Incorrect",
                            fontWeight = FontWeight.Bold,
                            color = if (feedback!!.is_correct) Color(0xFF81C784) else Color(0xFFE57373),
                            fontSize = 18.sp
                        )
                        Text(
                            text = feedback!!.explanation,
                            color = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                        
                        Button(
                            onClick = { loadNextCard() },
                            modifier = Modifier.fillMaxWidth().padding(top=8.dp),
                             colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF64B5F6))
                        ) {
                            Text("Next Concept")
                        }
                    }
                }
            }
        }
    }
}