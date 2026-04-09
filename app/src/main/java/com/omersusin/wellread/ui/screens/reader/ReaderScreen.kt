package com.omersusin.wellread.ui.screens.reader

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.hilt.navigation.compose.hiltViewModel
import com.omersusin.wellread.domain.model.ReadingMode
import com.omersusin.wellread.ui.theme.*

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
        if (uiState.isLoading) {
            LoadingState()
        } else if (uiState.error != null) {
            ErrorState(error = uiState.error!!, onBack = onNavigateBack)
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top bar
                ReaderTopBar(
                    title = uiState.book?.title ?: "",
                    mode = uiState.currentMode,
                    onBack = onNavigateBack,
                    onModeClick = viewModel::toggleModeSelector,
                    onSettingsClick = viewModel::toggleSettings
                )

                // Progress indicator
                val progress = if (uiState.words.isNotEmpty())
                    uiState.currentWordIndex.toFloat() / uiState.words.size else 0f
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = uiState.currentMode.modeColor(),
                    trackColor = uiState.currentMode.modeColor().copy(alpha = 0.15f)
                )

                // Main reading area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when (uiState.currentMode) {
                        ReadingMode.BIONIC -> BionicModeContent(
                            uiState = uiState,
                            onSeek = viewModel::seekTo
                        )
                        ReadingMode.FLASH -> FlashModeContent(uiState = uiState)
                        ReadingMode.FOCUS -> FocusModeContent(uiState = uiState)
                        ReadingMode.TRAIN -> TrainModeContent(uiState = uiState)
                        ReadingMode.SENTENCE_SWIPE -> SentenceSwipeModeContent(
                            uiState = uiState,
                            onSwipe = viewModel::onSentenceSwiped,
                            onPrevious = viewModel::previousSentence
                        )
                    }
                }

                // Bottom controls
                ReaderBottomBar(
                    uiState = uiState,
                    onPlayPause = viewModel::togglePlay,
                    onNext = viewModel::nextWord,
                    onPrevious = viewModel::previousWord,
                    onWpmChange = viewModel::setWpm
                )
            }

            // Mode selector overlay
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

            // Settings overlay
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
                Icon(Icons.Default.ArrowBack, "Back")
            }
        },
        actions = {
            // Mode chip
            Surface(
                onClick = onModeClick,
                shape = RoundedCornerShape(12.dp),
                color = mode.modeColor().copy(alpha = 0.15f),
                modifier = Modifier.padding(end = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(mode.modeEmoji(), fontSize = 14.sp)
                    Text(
                        text = mode.modeLabel(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = mode.modeColor()
                    )
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        null,
                        modifier = Modifier.size(16.dp),
                        tint = mode.modeColor()
                    )
                }
            }
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Default.Settings, "Settings")
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
    val showControls = uiState.currentMode in listOf(
        ReadingMode.FLASH, ReadingMode.FOCUS
    )

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
                // WPM slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "⚡",
                        fontSize = 16.sp
                    )
                    Slider(
                        value = uiState.wpm.toFloat(),
                        onValueChange = { onWpmChange(it.toInt()) },
                        valueRange = 50f..800f,
                        steps = 14,
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
                            text = "${uiState.wpm}",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            style = MaterialTheme.typography.labelLarge,
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
                // Word count info
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
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    "Switch Mode",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                ReadingMode.values().forEach { mode ->
                    val selected = mode == currentMode
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onModeSelected(mode) },
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (selected)
                                mode.modeColor().copy(alpha = 0.2f)
                            else
                                MaterialTheme.colorScheme.surface
                        ),
                        border = if (selected) BorderStroke(1.5.dp, mode.modeColor()) else null
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(mode.modeEmoji(), fontSize = 24.sp)
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
                                    Icons.Default.CheckCircle,
                                    null,
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
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    "Reader Settings",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    "Font Size: ${uiState.fontSize.toInt()}sp",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
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
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = WellReadPurple)
            Spacer(modifier = Modifier.height(16.dp))
            Text("Loading content...", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ErrorState(error: String, onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("❌", fontSize = 48.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(error, color = MaterialTheme.colorScheme.error)
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onBack) { Text("Go Back") }
        }
    }
}

fun ReadingMode.modeColor(): Color = when (this) {
    ReadingMode.BIONIC -> BionicColor
    ReadingMode.FLASH -> FlashColor
    ReadingMode.FOCUS -> FocusColor
    ReadingMode.TRAIN -> TrainColor
    ReadingMode.SENTENCE_SWIPE -> SwipeColor
}

fun ReadingMode.modeEmoji(): String = when (this) {
    ReadingMode.BIONIC -> "📖"
    ReadingMode.FLASH -> "⚡"
    ReadingMode.FOCUS -> "🎯"
    ReadingMode.TRAIN -> "🏋️"
    ReadingMode.SENTENCE_SWIPE -> "👆"
}

fun ReadingMode.modeLabel(): String = when (this) {
    ReadingMode.BIONIC -> "Bionic"
    ReadingMode.FLASH -> "Flash"
    ReadingMode.FOCUS -> "Focus"
    ReadingMode.TRAIN -> "Train"
    ReadingMode.SENTENCE_SWIPE -> "Swipe"
}

fun ReadingMode.modeDescription(): String = when (this) {
    ReadingMode.BIONIC -> "Bold fixation points for faster recognition"
    ReadingMode.FLASH -> "RSVP — words flash at your target speed"
    ReadingMode.FOCUS -> "Auto-scroll with highlighted word"
    ReadingMode.TRAIN -> "Eye movement exercises"
    ReadingMode.SENTENCE_SWIPE -> "Swipe through sentence by sentence"
}
