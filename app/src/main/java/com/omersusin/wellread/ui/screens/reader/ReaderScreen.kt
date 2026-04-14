package com.omersusin.wellread.ui.screens.reader

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import com.omersusin.wellread.domain.model.ReadingMode
import com.omersusin.wellread.ui.theme.*
import com.omersusin.wellread.ui.theme.ChunkColor
import com.omersusin.wellread.ui.theme.ParagraphColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    bookId: Long,
    initialMode: String,
    onNavigateBack: () -> Unit,
    viewModel: ReaderViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(bookId, initialMode) {
        viewModel.initialize(bookId, initialMode)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when {
            uiState.isLoading -> LoadingState()
            uiState.error != null -> ErrorState(
                error = uiState.error!!,
                onBack = onNavigateBack,
                onRetry = viewModel::retryLoad
            )
            uiState.words.isEmpty() -> ErrorState(
                error = "No content loaded",
                onBack = onNavigateBack,
                onRetry = viewModel::retryLoad
            )
            else -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    ReaderTopBar(
                        title = uiState.book?.title ?: "",
                        mode = uiState.currentMode,
                        onBack = onNavigateBack,
                        onModeClick = viewModel::toggleModeSelector,
                        onSettingsClick = viewModel::toggleSettings
                    )

                    val progress = if (uiState.words.isNotEmpty())
                        uiState.currentWordIndex.toFloat() / uiState.words.size else 0f
                    val animatedProgress by animateFloatAsState(
                        targetValue = progress,
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        label = "progress"
                    )

                    LinearProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.fillMaxWidth().height(3.dp),
                        color = uiState.currentMode.modeColor(),
                        trackColor = uiState.currentMode.modeColor().copy(alpha = 0.15f)
                    )

                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        when (uiState.currentMode) {
                            ReadingMode.BIONIC ->
                                BionicModeContent(uiState = uiState, onSeek = viewModel::seekTo)
                            ReadingMode.FLASH  -> FlashModeContent(uiState = uiState)
                            ReadingMode.CHUNK  -> ChunkModeContent(uiState = uiState)
                            ReadingMode.FOCUS  -> FocusModeContent(uiState = uiState)
                            ReadingMode.PARAGRAPH -> ParagraphModeContent(
                                uiState    = uiState,
                                onNext     = viewModel::nextParagraph,
                                onPrevious = viewModel::previousParagraph
                            )
                            ReadingMode.TRAIN  -> TrainModeContent(uiState = uiState)
                            ReadingMode.SENTENCE_SWIPE -> SentenceSwipeModeContent(
                                uiState    = uiState,
                                onSwipe    = viewModel::onSentenceSwiped,
                                onPrevious = viewModel::previousSentence
                            )
                        }
                    }

                    ReaderBottomBar(
                        uiState = uiState,
                        onPlayPause = viewModel::togglePlay,
                        onNext = viewModel::nextWord,
                        onPrevious = viewModel::previousWord,
                        onWpmChange = viewModel::setWpm
                    )
                }

                AnimatedVisibility(
                    visible = uiState.showModeSelector,
                    enter = fadeIn() + slideInVertically { it },
                    exit = fadeOut() + slideOutVertically { it }
                ) {
                    ModeSelectorSheet(
                        currentMode = uiState.currentMode,
                        onModeSelected = viewModel::setMode,
                        onDismiss = viewModel::toggleModeSelector
                    )
                }

                AnimatedVisibility(
                    visible = uiState.showSettings,
                    enter = fadeIn() + slideInVertically { it },
                    exit = fadeOut() + slideOutVertically { it }
                ) {
                    ReaderSettingsSheet(
                        uiState = uiState,
                        onFontSizeChange = viewModel::setFontSize,
                        onDismiss = viewModel::toggleSettings
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReaderTopBar(
    title: String,
    mode: ReadingMode,
    onBack: () -> Unit,
    onModeClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
            }
        },
        actions = {
            Surface(
                onClick = onModeClick,
                shape = RoundedCornerShape(14.dp),
                color = mode.modeColor().copy(alpha = 0.15f),
                modifier = Modifier.padding(end = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = mode.modeIcon(),
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = mode.modeColor()
                    )
                    Text(
                        text = mode.modeLabel(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = mode.modeColor()
                    )
                    Icon(
                        Icons.Default.KeyboardArrowDown, null,
                        modifier = Modifier.size(16.dp),
                        tint = mode.modeColor()
                    )
                }
            }
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Outlined.Settings, "Settings")
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
    )
}

@Composable
private fun ReaderBottomBar(
    uiState: ReaderUiState,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onWpmChange: (Int) -> Unit
) {
    val showControls = uiState.currentMode in listOf(ReadingMode.FLASH, ReadingMode.FOCUS, ReadingMode.CHUNK)

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 8.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            if (showControls) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Filled.Speed, null,
                        modifier = Modifier.size(18.dp),
                        tint = uiState.currentMode.modeColor()
                    )
                    Slider(
                        value = uiState.wpm.toFloat(),
                        onValueChange = { onWpmChange(it.toInt()) },
                        valueRange = 50f..800f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = uiState.currentMode.modeColor(),
                            activeTrackColor = uiState.currentMode.modeColor()
                        )
                    )
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = uiState.currentMode.modeColor().copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "${uiState.wpm} wpm",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = uiState.currentMode.modeColor()
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${uiState.currentWordIndex} / ${uiState.words.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )

                if (showControls) {
                    IconButton(onClick = onPrevious) {
                        Icon(Icons.Default.SkipPrevious, "Previous")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    FloatingActionButton(
                        onClick = onPlayPause,
                        containerColor = uiState.currentMode.modeColor(),
                        contentColor = Color.White,
                        shape = CircleShape,
                        modifier = Modifier.size(52.dp)
                    ) {
                        Icon(
                            imageVector = if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = onNext) {
                        Icon(Icons.Default.SkipNext, "Next")
                    }
                }

                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ModeSelectorSheet(
    currentMode: ReadingMode,
    onModeSelected: (ReadingMode) -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = false, onClick = {}),
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    "Reading Mode",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Choose how you want to read",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                ReadingMode.entries.forEach { mode ->
                    val selected = mode == currentMode
                    val animatedAlpha by animateFloatAsState(
                        targetValue = if (selected) 1f else 0f,
                        animationSpec = spring(stiffness = Spring.StiffnessMedium),
                        label = "alpha"
                    )
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onModeSelected(mode) },
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (selected)
                                mode.modeColor().copy(alpha = 0.18f)
                            else MaterialTheme.colorScheme.surface
                        ),
                        border = if (selected) BorderStroke(1.5.dp, mode.modeColor()) else null
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = mode.modeColor().copy(alpha = 0.15f),
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                    Icon(
                                        imageVector = mode.modeIcon(),
                                        contentDescription = null,
                                        modifier = Modifier.size(22.dp),
                                        tint = mode.modeColor()
                                    )
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    mode.modeLabel(),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selected) mode.modeColor()
                                    else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    mode.modeDescription(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (selected) {
                                Icon(
                                    Icons.Default.CheckCircle, null,
                                    tint = mode.modeColor(),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Spacer(modifier = Modifier.navigationBarsPadding())
            }
        }
    }
}

@Composable
private fun ReaderSettingsSheet(
    uiState: ReaderUiState,
    onFontSizeChange: (Float) -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = false, onClick = {}),
            shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    "Reader Settings",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(20.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Outlined.TextFields, null, tint = WellReadPurple, modifier = Modifier.size(18.dp))
                        Text(
                            "Font Size",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = WellReadPurple.copy(alpha = 0.12f)
                    ) {
                        Text(
                            "${uiState.fontSize.toInt()} sp",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = WellReadPurple
                        )
                    }
                }
                Slider(
                    value = uiState.fontSize,
                    onValueChange = onFontSizeChange,
                    valueRange = 12f..32f,
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = WellReadPurple,
                        activeTrackColor = WellReadPurple
                    )
                )
                Spacer(modifier = Modifier.navigationBarsPadding())
            }
        }
    }
}

@Composable
fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                color = WellReadPurple,
                strokeWidth = 3.dp,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                "Loading content…",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun ErrorState(
    error: String,
    onBack: () -> Unit,
    onRetry: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.errorContainer,
                modifier = Modifier.size(72.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        Icons.Outlined.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Could not load content",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onBack,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Go Back")
                }
                Button(
                    onClick = onRetry,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = WellReadPurple)
                ) {
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Retry")
                }
            }
        }
    }
}

// ── ReadingMode extensions ────────────────────────────────────────────────────

fun ReadingMode.modeColor(): Color = when (this) {
    ReadingMode.BIONIC         -> BionicColor
    ReadingMode.FLASH          -> FlashColor
    ReadingMode.CHUNK          -> ChunkColor
    ReadingMode.FOCUS          -> FocusColor
    ReadingMode.PARAGRAPH      -> ParagraphColor
    ReadingMode.TRAIN          -> TrainColor
    ReadingMode.SENTENCE_SWIPE -> SwipeColor
}

fun ReadingMode.modeIcon(): ImageVector = when (this) {
    ReadingMode.BIONIC         -> Icons.AutoMirrored.Outlined.MenuBook
    ReadingMode.FLASH          -> Icons.Filled.Bolt
    ReadingMode.CHUNK          -> Icons.Outlined.DynamicFeed
    ReadingMode.FOCUS          -> Icons.Outlined.CenterFocusStrong
    ReadingMode.PARAGRAPH      -> Icons.Outlined.ViewAgenda
    ReadingMode.TRAIN          -> Icons.Outlined.FitnessCenter
    ReadingMode.SENTENCE_SWIPE -> Icons.Outlined.SwipeRight
}

fun ReadingMode.modeLabel(): String = when (this) {
    ReadingMode.BIONIC         -> "Bionic"
    ReadingMode.FLASH          -> "Flash"
    ReadingMode.CHUNK          -> "Chunk"
    ReadingMode.FOCUS          -> "Focus"
    ReadingMode.PARAGRAPH      -> "Paragraph"
    ReadingMode.TRAIN          -> "Train"
    ReadingMode.SENTENCE_SWIPE -> "Swipe"
}

fun ReadingMode.modeDescription(): String = when (this) {
    ReadingMode.BIONIC         -> "Bold fixation points for faster recognition"
    ReadingMode.FLASH          -> "RSVP — words flash at your target speed"
    ReadingMode.CHUNK          -> "RSVP — multiple words at once"
    ReadingMode.FOCUS          -> "Auto-scroll with highlighted word"
    ReadingMode.PARAGRAPH      -> "One paragraph at a time, swipe to advance"
    ReadingMode.TRAIN          -> "Eye movement exercises"
    ReadingMode.SENTENCE_SWIPE -> "Swipe through sentence by sentence"
}
