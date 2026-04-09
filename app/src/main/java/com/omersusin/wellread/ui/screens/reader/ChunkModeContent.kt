package com.omersusin.wellread.ui.screens.reader

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.omersusin.wellread.ui.theme.ChunkColor

/**
 * Chunk Mode: displays N words at once (configurable), advancing automatically
 * at a speed derived from WPM / chunkSize. Easier on the eyes than single-word
 * RSVP while still being faster than normal reading.
 */
@Composable
fun ChunkModeContent(uiState: ReaderUiState) {
    val words = uiState.words
    if (words.isEmpty()) return

    val startIdx = uiState.currentWordIndex
    val endIdx   = (startIdx + uiState.chunkSize).coerceAtMost(words.size)
    val chunk    = words.subList(startIdx, endIdx).joinToString(" ")

    val progress = if (words.isNotEmpty()) startIdx.toFloat() / words.size else 0f

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment  = Alignment.CenterHorizontally,
            verticalArrangement  = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            // ORP (Optimal Recognition Point) marker
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = ChunkColor.copy(alpha = 0.15f)
            ) {
                Text(
                    text = "Chunk ${uiState.chunkSize}",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style    = MaterialTheme.typography.labelSmall,
                    color    = ChunkColor,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(28.dp))

            AnimatedContent(
                targetState   = chunk,
                transitionSpec = {
                    fadeIn(tween(120)) togetherWith fadeOut(tween(80))
                },
                label = "chunk"
            ) { displayChunk ->
                Text(
                    text      = displayChunk,
                    style     = MaterialTheme.typography.headlineLarge.copy(
                        fontSize = uiState.fontSize.sp * 1.4f
                    ),
                    fontWeight  = FontWeight.Medium,
                    textAlign   = TextAlign.Center,
                    color       = MaterialTheme.colorScheme.onBackground,
                    lineHeight  = (uiState.fontSize * 1.8f).sp
                )
            }

            Spacer(Modifier.height(28.dp))

            Text(
                text  = "$startIdx / ${words.size} words",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
