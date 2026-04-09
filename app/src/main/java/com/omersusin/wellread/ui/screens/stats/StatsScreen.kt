package com.omersusin.wellread.ui.screens.stats

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import com.omersusin.wellread.domain.model.ReadingMode
import com.omersusin.wellread.ui.screens.reader.*
import com.omersusin.wellread.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onNavigateBack: () -> Unit,
    viewModel: StatsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Statistics", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Overview cards
            OverviewSection(stats = uiState.stats)

            // Streak section
            StreakCard(
                currentStreak = uiState.stats.currentStreak,
                longestStreak = uiState.stats.longestStreak,
                todayMinutes = uiState.stats.todayMinutes,
                goalMinutes = uiState.stats.dailyGoalMinutes
            )

            // Mode breakdown
            ModeBreakdownSection(sessionsPerMode = uiState.stats.sessionsPerMode)

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun OverviewSection(stats: com.omersusin.wellread.domain.model.ReadingStats) {
    Text("Overview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatOverviewCard(
            modifier = Modifier.weight(1f),
            emoji = "📚",
            value = formatNumber(stats.totalWordsRead),
            label = "Words Read",
            color = WellReadPurple
        )
        StatOverviewCard(
            modifier = Modifier.weight(1f),
            emoji = "⏱️",
            value = "${stats.totalMinutesRead}",
            label = "Minutes",
            color = FocusColor
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatOverviewCard(
            modifier = Modifier.weight(1f),
            emoji = "⚡",
            value = "${stats.averageWpm}",
            label = "Avg WPM",
            color = FlashColor
        )
        StatOverviewCard(
            modifier = Modifier.weight(1f),
            emoji = "🔥",
            value = "${stats.currentStreak}",
            label = "Day Streak",
            color = AccentOrange
        )
    }
}

@Composable
private fun StatOverviewCard(
    modifier: Modifier = Modifier,
    emoji: String,
    value: String,
    label: String,
    color: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
        border = BorderStroke(1.dp, color.copy(alpha = 0.25f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(emoji, fontSize = 24.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = color)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StreakCard(
    currentStreak: Int,
    longestStreak: Int,
    todayMinutes: Int,
    goalMinutes: Int
) {
    val progress = (todayMinutes.toFloat() / goalMinutes).coerceIn(0f, 1f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(listOf(WellReadPurple.copy(0.8f), WellReadIndigo.copy(0.9f))),
                    RoundedCornerShape(24.dp)
                )
                .padding(20.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("🔥 Current Streak", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(0.7f))
                        Text("$currentStreak days", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Best", style = MaterialTheme.typography.labelMedium, color = Color.White.copy(0.7f))
                        Text("$longestStreak days", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("Today: $todayMinutes / $goalMinutes min", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(0.85f))
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape).background(Color.White.copy(0.2f))
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth(progress).fillMaxHeight().clip(CircleShape).background(Color.White)
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeBreakdownSection(sessionsPerMode: Map<ReadingMode, Int>) {
    val total = sessionsPerMode.values.sum().coerceAtLeast(1)
    Text("Mode Breakdown", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ReadingMode.values().forEach { mode ->
                val count = sessionsPerMode[mode] ?: 0
                val pct = count.toFloat() / total
                ModeProgressRow(
                    emoji = mode.modeEmoji(),
                    label = mode.modeLabel(),
                    count = count,
                    progress = pct,
                    color = mode.modeColor()
                )
            }
        }
    }
}

@Composable
private fun ModeProgressRow(
    emoji: String,
    label: String,
    count: Int,
    progress: Float,
    color: Color
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(emoji, fontSize = 18.sp)
                Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
            Text("$count sessions", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
            color = color,
            trackColor = color.copy(alpha = 0.15f)
        )
    }
}

private fun formatNumber(n: Int): String = when {
    n >= 1_000_000 -> "${n / 1_000_000}M"
    n >= 1_000 -> "${n / 1_000}K"
    else -> n.toString()
}
