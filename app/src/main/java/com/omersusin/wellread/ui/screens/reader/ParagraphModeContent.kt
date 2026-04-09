package com.omersusin.wellread.ui.screens.reader

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.omersusin.wellread.ui.theme.ParagraphColor

/**
 * Paragraph Mode: shows one paragraph at a time. Swipe left/right (or use
 * arrows) to navigate. Useful for focused reading of long-form content.
 */
@Composable
fun ParagraphModeContent(
    uiState: ReaderUiState,
    onNext: () -> Unit,
    onPrevious: () -> Unit
) {
    val paragraphs = uiState.paragraphs
    if (paragraphs.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No paragraphs found", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val idx  = uiState.currentParagraphIndex.coerceIn(0, paragraphs.size - 1)
    val para = paragraphs[idx]
    val scrollState = rememberScrollState()

    LaunchedEffect(idx) { scrollState.scrollTo(0) }

    var dragAccum by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        when {
                            dragAccum < -80f -> onNext()
                            dragAccum >  80f -> onPrevious()
                        }
                        dragAccum = 0f
                    },
                    onHorizontalDrag = { _, delta -> dragAccum += delta }
                )
            }
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // Progress chip
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(shape = RoundedCornerShape(8.dp), color = ParagraphColor.copy(alpha = 0.15f)) {
                    Text(
                        text = "Paragraph",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = ParagraphColor, fontWeight = FontWeight.SemiBold
                    )
                }
                Text(
                    "${idx + 1} / ${paragraphs.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Paragraph text
            AnimatedContent(
                targetState   = idx,
                transitionSpec = {
                    if (targetState > initialState)
                        slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
                    else
                        slideInHorizontally { -it } + fadeIn() togetherWith slideOutHorizontally { it } + fadeOut()
                },
                label = "paragraph",
                modifier = Modifier.weight(1f)
            ) { displayIdx ->
                val displayPara = paragraphs.getOrElse(displayIdx) { "" }
                Box(
                    modifier = Modifier.fillMaxSize().verticalScroll(scrollState),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text       = displayPara,
                        modifier   = Modifier.padding(horizontal = 28.dp, vertical = 20.dp),
                        style      = MaterialTheme.typography.bodyLarge.copy(fontSize = uiState.fontSize.sp),
                        lineHeight = (uiState.fontSize * 1.7f).sp,
                        textAlign  = TextAlign.Start,
                        color      = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            // Navigation row
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                FilledTonalButton(
                    onClick  = onPrevious,
                    enabled  = idx > 0,
                    shape    = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Previous")
                }
                FilledTonalButton(
                    onClick = onNext,
                    enabled = idx < paragraphs.size - 1,
                    shape   = RoundedCornerShape(14.dp)
                ) {
                    Text("Next")
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(18.dp))
                }
            }
        }
    }
}
