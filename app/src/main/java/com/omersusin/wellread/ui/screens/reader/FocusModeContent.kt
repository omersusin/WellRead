package com.omersusin.wellread.ui.screens.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omersusin.wellread.ui.theme.FocusColor

/**
 * Focus Mode — current word highlighted, surrounding context visible.
 *
 * ANR fix: previously built one AnnotatedString from ALL words, re-computed
 * on every word change, then measured the full book on the main thread.
 *
 * Now: split into paragraphs. Only the paragraph containing the current
 * word gets highlighted spans; all others render as plain text.
 * LazyColumn ensures only visible paragraphs are composed.
 */
@Composable
fun FocusModeContent(uiState: ReaderUiState) {
    val words      = uiState.words
    val currentIdx = uiState.currentWordIndex

    // Build paragraph list once per text change
    val paragraphs: List<List<String>> = remember(uiState.fullText) {
        uiState.fullText
            .split(Regex("\n{2,}"))
            .map { para -> para.trim().split(Regex("\\s+")).filter { it.isNotBlank() } }
            .filter { it.isNotEmpty() }
            .ifEmpty { listOf(words) }
    }

    // Map global word index → (paragraphIndex, localWordIndex)
    val (currentParaIdx, localWordIdx) = remember(currentIdx, paragraphs) {
        var remaining = currentIdx
        var paraIdx = 0
        for ((i, para) in paragraphs.withIndex()) {
            if (remaining < para.size) { paraIdx = i; break }
            remaining -= para.size
            paraIdx = i
        }
        Pair(paraIdx, remaining.coerceAtLeast(0))
    }

    val listState = rememberLazyListState()

    // Auto-scroll so the active paragraph stays visible
    LaunchedEffect(currentParaIdx) {
        listState.animateScrollToItem(currentParaIdx.coerceIn(0, (paragraphs.size - 1).coerceAtLeast(0)))
    }

    LazyColumn(
        state           = listState,
        contentPadding  = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier        = Modifier.fillMaxSize()
    ) {
        itemsIndexed(paragraphs) { paraIdx, paraWords ->
            FocusParagraph(
                words       = paraWords,
                activeWord  = if (paraIdx == currentParaIdx) localWordIdx else -1,
                fontSize    = uiState.fontSize,
                isActive    = paraIdx == currentParaIdx
            )
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun FocusParagraph(
    words: List<String>,
    activeWord: Int,
    fontSize: Float,
    isActive: Boolean
) {
    // Only build AnnotatedString if this paragraph is the active one
    if (isActive && activeWord >= 0) {
        val annotated = remember(words, activeWord) {
            buildAnnotatedString {
                words.forEachIndexed { i, word ->
                    if (i == activeWord) {
                        withStyle(
                            SpanStyle(
                                color      = Color.White,
                                fontWeight = FontWeight.Bold,
                                background = FocusColor
                            )
                        ) { append(word) }
                    } else {
                        append(word)
                    }
                    if (i < words.size - 1) append(" ")
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(FocusColor.copy(alpha = 0.06f))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text      = annotated,
                style     = MaterialTheme.typography.bodyLarge.copy(
                    fontSize   = fontSize.sp,
                    lineHeight = (fontSize * 1.7f).sp
                ),
                textAlign = TextAlign.Start
            )
        }
    } else {
        // Plain text — no span overhead at all
        Text(
            text  = words.joinToString(" "),
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize   = fontSize.sp,
                lineHeight = (fontSize * 1.7f).sp
            ),
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Start
        )
    }
}
