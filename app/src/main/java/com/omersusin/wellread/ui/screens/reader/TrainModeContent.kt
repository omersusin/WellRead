package com.omersusin.wellread.ui.screens.reader

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.omersusin.wellread.ui.theme.*
import kotlinx.coroutines.delay

enum class TrainExercise(val label: String, val description: String, val emoji: String) {
    WORD_FLASH("Word Flash", "Focus on words without sub-vocalizing", "⚡"),
    EYE_MOVEMENT("Eye Movement", "Train your eyes to move smoothly", "👁️"),
    PERIPHERAL("Peripheral Vision", "Expand your reading width", "🔭"),
    COLUMN_SCROLL("Column Read", "Simulate natural book reading", "📜")
}

@Composable
fun TrainModeContent(uiState: ReaderUiState) {
    var currentExercise by remember { mutableStateOf(TrainExercise.WORD_FLASH) }
    var isRunning by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Exercise selector tabs
        ScrollableTabRow(
            selectedTabIndex = TrainExercise.entries.indexOf(currentExercise),
            edgePadding = 0.dp,
            containerColor = Color.Transparent,
            contentColor = TrainColor,
            divider = {}
        ) {
            TrainExercise.entries.forEachIndexed { index, exercise ->
                Tab(
                    selected = currentExercise == exercise,
                    onClick = {
                        currentExercise = exercise
                        isRunning = false
                    },
                    text = {
                        Text(
                            exercise.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (currentExercise == exercise) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        when (currentExercise) {
            TrainExercise.WORD_FLASH -> WordFlashExercise(
                words = uiState.words,
                wpm = uiState.wpm,
                isRunning = isRunning,
                onToggle = { isRunning = !isRunning }
            )
            TrainExercise.EYE_MOVEMENT -> EyeMovementExercise(
                isRunning = isRunning,
                onToggle = { isRunning = !isRunning }
            )
            TrainExercise.PERIPHERAL -> PeripheralExercise(
                words = uiState.words,
                isRunning = isRunning,
                onToggle = { isRunning = !isRunning }
            )
            TrainExercise.COLUMN_SCROLL -> ColumnScrollExercise(
                words = uiState.words,
                wpm = uiState.wpm,
                isRunning = isRunning,
                onToggle = { isRunning = !isRunning }
            )
        }
    }
}

@Composable
private fun WordFlashExercise(
    words: List<String>,
    wpm: Int,
    isRunning: Boolean,
    onToggle: () -> Unit
) {
    var currentIndex by remember { mutableStateOf(0) }
    val currentWord = words.getOrNull(currentIndex) ?: "Ready"

    LaunchedEffect(isRunning) {
        if (isRunning) {
            while (isRunning && currentIndex < words.size - 1) {
                delay(60000L / wpm)
                currentIndex++
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(" Word Flash", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = TrainColor)
        Text("Focus without sub-vocalizing", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(48.dp))
        Card(
            modifier = Modifier.size(260.dp, 140.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = TrainColor.copy(alpha = 0.1f))
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = currentWord,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onToggle,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = TrainColor)
        ) {
            Text(if (isRunning) "Pause" else "Start")
        }
    }
}

@Composable
private fun EyeMovementExercise(isRunning: Boolean, onToggle: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "eye")
    val offsetX by infiniteTransition.animateFloat(
        initialValue = -120f,
        targetValue = 120f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "x"
    )
    val offsetY by infiniteTransition.animateFloat(
        initialValue = -40f,
        targetValue = 40f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "y"
    )

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("️ Eye Movement", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = TrainColor)
        Text("Follow the dot with your eyes", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(48.dp))
        Box(modifier = Modifier.size(280.dp, 200.dp), contentAlignment = Alignment.Center) {
            if (isRunning) {
                Surface(
                    modifier = Modifier
                        .offset(x = offsetX.dp, y = offsetY.dp)
                        .size(24.dp),
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = TrainColor
                ) {}
            } else {
                Text("Press Start", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onToggle,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = TrainColor)
        ) {
            Text(if (isRunning) "Stop" else "Start")
        }
    }
}

@Composable
private fun PeripheralExercise(words: List<String>, isRunning: Boolean, onToggle: () -> Unit) {
    var index by remember { mutableStateOf(0) }

    LaunchedEffect(isRunning) {
        if (isRunning) {
            while (isRunning) {
                delay(2000)
                if (index + 2 < words.size) index += 3
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(" Peripheral Vision", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = TrainColor)
        Text("Read all three words at once", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(48.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(words.getOrNull(index) ?: "", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f))
            Text(words.getOrNull(index + 1) ?: "", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = TrainColor)
            Text(words.getOrNull(index + 2) ?: "", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(0.5f))
        }
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onToggle,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = TrainColor)
        ) {
            Text(if (isRunning) "Stop" else "Start")
        }
    }
}

@Composable
private fun ColumnScrollExercise(words: List<String>, wpm: Int, isRunning: Boolean, onToggle: () -> Unit) {
    var currentLine by remember { mutableStateOf(0) }
    val chunkSize = 3
    val lines = words.chunked(chunkSize)

    LaunchedEffect(isRunning) {
        if (isRunning) {
            while (isRunning && currentLine < lines.size - 1) {
                delay(60000L / wpm * chunkSize)
                currentLine++
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(" Column Read", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = TrainColor)
        Text("Read line by line, top to bottom", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(24.dp))
        Card(
            modifier = Modifier.width(220.dp).weight(1f),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = TrainColor.copy(alpha = 0.08f))
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                (-2..2).forEach { offset ->
                    val lineIndex = currentLine + offset
                    val line = lines.getOrNull(lineIndex)?.joinToString(" ") ?: ""
                    val alpha = when (offset) {
                        0 -> 1f
                        -1, 1 -> 0.4f
                        else -> 0.15f
                    }
                    val size = if (offset == 0) 18.sp else 14.sp
                    Text(
                        text = line,
                        fontSize = size,
                        fontWeight = if (offset == 0) FontWeight.Bold else FontWeight.Normal,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onToggle,
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = TrainColor)
        ) {
            Text(if (isRunning) "Stop" else "Start")
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}
