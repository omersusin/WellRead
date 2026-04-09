package com.omersusin.wellread.ui.screens.stats

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import com.omersusin.wellread.domain.model.ReadingMode
import com.omersusin.wellread.ui.screens.reader.modeColor
import com.omersusin.wellread.ui.screens.reader.modeIcon
import com.omersusin.wellread.ui.screens.reader.modeLabel
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
            LargeTopAppBar(
                title = {
                    Text(
                        "Statistics",
                        style      = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                },
                navigationIcon = {
                    FilledTonalIconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
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
            OverviewSection(stats = uiState.stats)
            StreakCard(
                currentStreak = uiState.stats.currentStreak,
                longestStreak = uiState.stats.longestStreak,
                todayMinutes  = uiState.stats.todayMinutes,
                goalMinutes   = uiState.stats.dailyGoalMinutes
            )
            ModeBreakdownSection(sessionsPerMode = uiState.stats.sessionsPerMode)
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun OverviewSection(stats: com.omersusin.wellread.domain.model.ReadingStats) {
    Text(
        "Overview",
        style      = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.ExtraBold
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard(
            modifier = Modifier.weight(1f),
            icon     = Icons.Outlined.MenuBook,
            value    = formatNumber(stats.totalWordsRead),
            label    = "Words Read",
            color    = WellReadPurple
        )
        StatCard(
            modifier = Modifier.weight(1f),
            icon     = Icons.Outlined.Timer,
            value    = "${stats.totalMinutesRead}",
            label    = "Minutes",
            color    = FocusColor
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard(
            modifier = Modifier.weight(1f),
            icon     = Icons.Filled.Bolt,
            value    = "${stats.averageWpm}",
            label    = "Avg WPM",
            color    = FlashColor
        )
        StatCard(
            modifier = Modifier.weight(1f),
            icon     = Icons.Filled.LocalFireDepartment,
            value    = "${stats.currentStreak}",
            label    = "Day Streak",
            color    = AccentOrange
        )
    }
}

@Composable
private fun StatCard(
    modifier: Modifier = Modifier,
    icon:  ImageVector,
    value: String,
    label: String,
    color: Color
) {
    Card(
        modifier = modifier,
        shape    = RoundedCornerShape(22.dp),
        colors   = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.10f)),
        border   = BorderStroke(1.dp, color.copy(alpha = 0.22f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Surface(
                shape    = RoundedCornerShape(10.dp),
                color    = color.copy(alpha = 0.18f),
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(icon, null, modifier = Modifier.size(20.dp), tint = color)
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                value,
                style      = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color      = color
            )
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
    val animProg by animateFloatAsState(
        targetValue  = progress,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness    = Spring.StiffnessLow
        ),
        label = "streak-progress"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(28.dp),
        colors   = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(WellReadPurple.copy(0.85f), WellReadIndigo.copy(0.95f))
                    ),
                    RoundedCornerShape(28.dp)
                )
                .padding(22.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Filled.LocalFireDepartment, null,
                                modifier = Modifier.size(18.dp),
                                tint     = Color(0xFFFFB74D)
                            )
                            Text(
                                "Current Streak",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(0.75f)
                            )
                        }
                        Text(
                            "$currentStreak days",
                            style      = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color      = Color.White
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "Best",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(0.75f)
                        )
                        Text(
                            "$longestStreak days",
                            style      = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color      = Color.White
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Today: $todayMinutes / $goalMinutes min",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(0.85f)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth().height(8.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(0.2f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(animProg).fillMaxHeight()
                            .clip(CircleShape).background(Color.White)
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeBreakdownSection(sessionsPerMode: Map<ReadingMode, Int>) {
    val total = sessionsPerMode.values.sum().coerceAtLeast(1)
    Text(
        "Mode Breakdown",
        style      = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.ExtraBold
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(22.dp),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ReadingMode.values().forEach { mode ->
                val count = sessionsPerMode[mode] ?: 0
                val pct   = count.toFloat() / total
                ModeProgressRow(
                    mode     = mode,
                    count    = count,
                    progress = pct
                )
            }
        }
    }
}

@Composable
private fun ModeProgressRow(
    mode: ReadingMode,
    count: Int,
    progress: Float
) {
    val animProg by animateFloatAsState(
        targetValue  = progress,
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label        = "mode-prog-${mode.name}"
    )
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Surface(
                    shape    = RoundedCornerShape(8.dp),
                    color    = mode.modeColor().copy(alpha = 0.15f),
                    modifier = Modifier.size(30.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            mode.modeIcon(), null,
                            modifier = Modifier.size(16.dp),
                            tint     = mode.modeColor()
                        )
                    }
                }
                Text(
                    mode.modeLabel(),
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                "$count sessions",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(
            progress   = { animProg },
            modifier   = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
            color      = mode.modeColor(),
            trackColor = mode.modeColor().copy(alpha = 0.15f)
        )
    }
}

private fun formatNumber(n: Int): String = when {
    n >= 1_000_000 -> "${n / 1_000_000}M"
    n >= 1_000     -> "${n / 1_000}K"
    else           -> n.toString()
}
