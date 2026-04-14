package com.omersusin.wellread.ui.screens.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import com.omersusin.wellread.domain.model.Book
import com.omersusin.wellread.domain.model.BookType
import com.omersusin.wellread.domain.model.ReadingMode
import com.omersusin.wellread.ui.screens.reader.modeColor
import com.omersusin.wellread.ui.screens.reader.modeIcon
import com.omersusin.wellread.ui.screens.reader.modeLabel
import com.omersusin.wellread.ui.theme.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToLibrary: () -> Unit,
    onNavigateToReader: (Long, String) -> Unit,
    onNavigateToStats: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            HomeTopBar(greeting = uiState.greeting, onSettingsClick = onNavigateToSettings)
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            item {
                DailyProgressCard(
                    todayMinutes = uiState.stats.todayMinutes,
                    goalMinutes = uiState.preferences.dailyGoalMinutes,
                    streak = uiState.stats.currentStreak,
                    totalWords = uiState.stats.totalWordsRead
                )
            }
            item {
                ReadingModesSection(
                    onModeSelected = { mode ->
                        val recent = uiState.recentBooks.firstOrNull()
                        if (recent != null) onNavigateToReader(recent.id, mode.name)
                        else onNavigateToLibrary()
                    }
                )
            }
            if (uiState.recentBooks.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "Continue Reading",
                        actionLabel = "See All",
                        onAction = onNavigateToLibrary
                    )
                }
                item {
                    RecentBooksRow(
                        books = uiState.recentBooks,
                        onBookClick = { book ->
                            onNavigateToReader(book.id, uiState.preferences.defaultMode.name)
                        }
                    )
                }
            } else {
                item { EmptyLibraryCard(onAddBook = onNavigateToLibrary) }
            }
            item {
                QuickStatsRow(
                    avgWpm = uiState.stats.averageWpm,
                    totalMinutes = uiState.stats.totalMinutesRead,
                    onStatsClick = onNavigateToStats
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeTopBar(greeting: String, onSettingsClick: () -> Unit) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = greeting,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "WellRead",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        actions = {
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Outlined.Settings, "Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
    )
}

@Composable
private fun DailyProgressCard(
    todayMinutes: Int,
    goalMinutes: Int,
    streak: Int,
    totalWords: Int
) {
    val progress = (todayMinutes.toFloat() / goalMinutes.toFloat()).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow),
        label = "progress"
    )

    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                WellReadPurple.copy(alpha = 0.9f),
                                WellReadIndigo.copy(alpha = 0.95f)
                            ),
                            start = Offset(0f, 0f),
                            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
                        ),
                        shape = RoundedCornerShape(32.dp)
                    )
                    .padding(24.dp)
            ) {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column {
                            Text("Daily Goal",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.7f))
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("$todayMinutes / $goalMinutes min",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White)
                        }
                        if (streak > 0) {
                            Surface(shape = RoundedCornerShape(16.dp), color = Color.White.copy(alpha = 0.2f)) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Filled.LocalFireDepartment, null,
                                        modifier = Modifier.size(18.dp), tint = Color(0xFFFFB74D))
                                    Text("$streak",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth().height(8.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(animatedProgress).fillMaxHeight()
                                .clip(CircleShape).background(Color.White)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${(progress * 100).roundToInt()}% complete",
                            style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
                        Text("${totalWords.formatNumber()} words total",
                            style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ReadingModesSection(onModeSelected: (ReadingMode) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        SectionHeader(title = "Reading Modes")
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ModeCard(modifier = Modifier.weight(1f), mode = ReadingMode.BIONIC,
                description = "Bold fixation", onClick = { onModeSelected(ReadingMode.BIONIC) })
            ModeCard(modifier = Modifier.weight(1f), mode = ReadingMode.FLASH,
                description = "RSVP speed", onClick = { onModeSelected(ReadingMode.FLASH) })
            ModeCard(modifier = Modifier.weight(1f), mode = ReadingMode.FOCUS,
                description = "Highlight", onClick = { onModeSelected(ReadingMode.FOCUS) })
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ModeCard(modifier = Modifier.weight(1f), mode = ReadingMode.CHUNK,
                description = "Multi-word", onClick = { onModeSelected(ReadingMode.CHUNK) })
            ModeCard(modifier = Modifier.weight(1f), mode = ReadingMode.PARAGRAPH,
                description = "One block", onClick = { onModeSelected(ReadingMode.PARAGRAPH) })
            ModeCard(modifier = Modifier.weight(1f), mode = ReadingMode.TRAIN,
                description = "Eye train", onClick = { onModeSelected(ReadingMode.TRAIN) })
        }
        Spacer(modifier = Modifier.height(10.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ModeCard(modifier = Modifier.weight(1f), mode = ReadingMode.SENTENCE_SWIPE,
                description = "Swipe sentences", onClick = { onModeSelected(ReadingMode.SENTENCE_SWIPE) })
            Spacer(modifier = Modifier.weight(2f))
        }
    }
}

@Composable
private fun ModeCard(
    modifier: Modifier = Modifier,
    mode: ReadingMode,
    description: String,
    onClick: () -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "scale"
    )

    Card(
        modifier = modifier.scale(scale).clickable { pressed = true; onClick() },
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = mode.modeColor().copy(alpha = 0.12f)),
        border = BorderStroke(1.dp, mode.modeColor().copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(14.dp), horizontalAlignment = Alignment.Start) {
            Surface(shape = RoundedCornerShape(10.dp), color = mode.modeColor().copy(alpha = 0.18f), modifier = Modifier.size(36.dp)) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(imageVector = mode.modeIcon(), contentDescription = null,
                        modifier = Modifier.size(18.dp), tint = mode.modeColor())
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(mode.modeLabel(), style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold, color = mode.modeColor())
            Text(description, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }

    LaunchedEffect(pressed) {
        if (pressed) { kotlinx.coroutines.delay(150); pressed = false }
    }
}

@Composable
private fun RecentBooksRow(books: List<Book>, onBookClick: (Book) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(books, key = { it.id }) { book ->
            BookCard(book = book, onClick = { onBookClick(book) })
        }
    }
}

@Composable
private fun BookCard(book: Book, onClick: () -> Unit) {
    val progress = if (book.totalWords > 0) book.currentPosition.toFloat() / book.totalWords else 0f
    val typeColor = when (book.type) {
        BookType.PDF       -> BionicColor
        BookType.EPUB      -> FocusColor
        BookType.TXT       -> TrainColor
        BookType.WEB       -> FlashColor
        BookType.DOCX      -> SwipeColor
        BookType.MARKDOWN  -> ChunkColor
        BookType.HTML      -> ParagraphColor
        BookType.CLIPBOARD -> AccentTeal
    }
    val typeIcon: ImageVector = when (book.type) {
        BookType.PDF       -> Icons.Outlined.PictureAsPdf
        BookType.EPUB      -> Icons.AutoMirrored.Outlined.MenuBook
        BookType.TXT       -> Icons.Outlined.Article
        BookType.WEB       -> Icons.Outlined.Language
        BookType.DOCX      -> Icons.Outlined.Description
        BookType.MARKDOWN  -> Icons.Outlined.Code
        BookType.HTML      -> Icons.Outlined.Code
        BookType.CLIPBOARD -> Icons.Outlined.ContentPaste
    }

    Card(
        modifier = Modifier.width(150.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Box(
                modifier = Modifier.fillMaxWidth().height(90.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        brush = Brush.linearGradient(
                            listOf(typeColor.copy(alpha = 0.35f), typeColor.copy(alpha = 0.6f))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(imageVector = typeIcon, contentDescription = null,
                    modifier = Modifier.size(36.dp), tint = Color.White.copy(alpha = 0.9f))
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(book.title, style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold, maxLines = 2,
                overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(4.dp))
            Text(book.author, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                color = typeColor, trackColor = typeColor.copy(alpha = 0.2f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text("${(progress * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmptyLibraryCard(onAddBook: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp).clickable(onClick = onAddBook),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = BorderStroke(1.5.dp, brush = Brush.linearGradient(
            listOf(WellReadPurple.copy(alpha = 0.4f), WellReadIndigo.copy(alpha = 0.4f))
        ))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(shape = CircleShape, color = WellReadPurple.copy(alpha = 0.12f), modifier = Modifier.size(72.dp)) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(Icons.Outlined.LibraryBooks, null, modifier = Modifier.size(36.dp), tint = WellReadPurple)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text("Start Your Library", style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(6.dp))
            Text("Add a book, PDF, EPUB or paste a web URL to get started",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onAddBook,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = WellReadPurple)
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Content")
            }
        }
    }
}

@Composable
private fun QuickStatsRow(avgWpm: Int, totalMinutes: Int, onStatsClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        QuickStatCard(modifier = Modifier.weight(1f), icon = Icons.Filled.Bolt,
            value = "$avgWpm", label = "Avg WPM", color = FlashColor)
        QuickStatCard(modifier = Modifier.weight(1f), icon = Icons.Outlined.Timer,
            value = "${totalMinutes}m", label = "Total Time", color = FocusColor)
        Card(
            modifier = Modifier.weight(1f).clickable(onClick = onStatsClick),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = WellReadPurple.copy(alpha = 0.12f)),
            border = BorderStroke(1.dp, WellReadPurple.copy(alpha = 0.3f))
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Outlined.BarChart, null, modifier = Modifier.size(22.dp), tint = WellReadPurple)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Full Stats", style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold, color = WellReadPurple)
            }
        }
    }
}

@Composable
private fun QuickStatCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    value: String,
    label: String,
    color: Color
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.10f))
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, null, modifier = Modifier.size(22.dp), tint = color)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold, color = color)
            Text(label, style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(actionLabel, color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

private fun Int.formatNumber(): String = when {
    this >= 1_000_000 -> "${this / 1_000_000}M"
    this >= 1_000     -> "${this / 1_000}K"
    else              -> this.toString()
}
