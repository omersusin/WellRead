package com.omersusin.wellread.ui.screens.reader

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.omersusin.wellread.ui.theme.*
import kotlin.math.absoluteValue

@Composable
fun SentenceSwipeModeContent(
    uiState: ReaderUiState,
    onSwipe: () -> Unit,
    onPrevious: () -> Unit
) {
    val currentSentence = uiState.sentences.getOrNull(uiState.currentSentenceIndex) ?: ""
    val nextSentence = uiState.sentences.getOrNull(uiState.currentSentenceIndex + 1) ?: ""
    val prevSentence = uiState.sentences.getOrNull(uiState.currentSentenceIndex - 1) ?: ""
    val progress = if (uiState.sentences.isNotEmpty())
        uiState.currentSentenceIndex.toFloat() / uiState.sentences.size else 0f

    var dragOffset by remember { mutableStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    val animatedOffset by animateFloatAsState(
        targetValue = if (isDragging) dragOffset else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "drag"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        // Sentence counter
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "${uiState.currentSentenceIndex + 1}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = SwipeColor
            )
            Text(
                text = "/ ${uiState.sentences.size}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Progress bar
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
            color = SwipeColor,
            trackColor = SwipeColor.copy(alpha = 0.15f)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Previous sentence (faded)
        if (prevSentence.isNotBlank()) {
            Text(
                text = prevSentence,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = (uiState.fontSize * 0.85f).sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Main sentence card with drag
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            // Next sentence preview (shows when dragging left)
            if (animatedOffset < -30f) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(((-animatedOffset - 30f) / 100f).coerceIn(0f, 0.6f))
                        .scale(0.92f),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = SwipeColor.copy(alpha = 0.08f)
                    )
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = nextSentence,
                            style = MaterialTheme.typography.bodyLarge.copy(fontSize = uiState.fontSize.sp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // Main card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(x = animatedOffset.dp)
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragStart = { isDragging = true },
                            onDragEnd = {
                                isDragging = false
                                if (dragOffset < -80f) {
                                    onSwipe()
                                } else if (dragOffset > 80f) {
                                    onPrevious()
                                }
                                dragOffset = 0f
                            },
                            onDragCancel = {
                                isDragging = false
                                dragOffset = 0f
                            },
                            onHorizontalDrag = { _, dragAmount ->
                                dragOffset = (dragOffset + dragAmount * 0.4f).coerceIn(-160f, 160f)
                            }
                        )
                    },
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer
                ),
                border = BorderStroke(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(SwipeColor.copy(alpha = 0.5f), SwipeColor.copy(alpha = 0.2f))
                    )
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(
                        targetState = currentSentence,
                        transitionSpec = {
                            fadeIn(tween(200)).togetherWith(fadeOut(tween(150)))
                        },
                        label = "sentence"
                    ) { sentence ->
                        Text(
                            text = sentence,
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = uiState.fontSize.sp,
                                lineHeight = (uiState.fontSize * 1.8f).sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Swipe hint overlay when dragging
                    if (animatedOffset.absoluteValue > 30f) {
                        val isNext = animatedOffset < 0
                        Surface(
                            modifier = Modifier
                                .align(if (isNext) Alignment.CenterEnd else Alignment.CenterStart)
                                .alpha((animatedOffset.absoluteValue / 160f).coerceIn(0f, 0.9f)),
                            shape = CircleShape,
                            color = SwipeColor.copy(alpha = 0.15f)
                        ) {
                            Icon(
                                imageVector = if (isNext) Icons.Default.ArrowForward else Icons.Default.ArrowBack,
                                contentDescription = null,
                                modifier = Modifier.padding(12.dp).size(28.dp),
                                tint = SwipeColor
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Next sentence preview (faded)
        if (nextSentence.isNotBlank()) {
            Text(
                text = nextSentence,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = (uiState.fontSize * 0.85f).sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Manual buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledTonalIconButton(
                onClick = onPrevious,
                enabled = uiState.currentSentenceIndex > 0,
                modifier = Modifier.size(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = SwipeColor.copy(alpha = 0.12f)
                )
            ) {
                Icon(Icons.Default.ArrowBack, null, tint = SwipeColor)
            }

            // Swipe hint
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("← swipe →", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("to navigate", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            }

            FilledTonalIconButton(
                onClick = onSwipe,
                enabled = uiState.currentSentenceIndex < uiState.sentences.size - 1,
                modifier = Modifier.size(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = SwipeColor.copy(alpha = 0.12f)
                )
            ) {
                Icon(Icons.Default.ArrowForward, null, tint = SwipeColor)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}
