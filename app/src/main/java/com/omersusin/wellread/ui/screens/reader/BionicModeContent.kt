package com.omersusin.wellread.ui.screens.reader

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omersusin.wellread.utils.BionicWord
import com.omersusin.wellread.utils.TextProcessor

/**
 * Bionic Mode — bold-prefix fixation reading.
 *
 * ANR fix: previously this built one giant AnnotatedString from the entire book
 * (up to 500K chars / 200K span changes) and passed it to a single Text().
 * Compose measured all spans on the main thread → 5-second freeze → ANR.
 *
 * Now: split into paragraphs, render each in its own LazyColumn item.
 * Only visible paragraphs are composed/measured at any time.
 */
@Composable
fun BionicModeContent(
    uiState: ReaderUiState,
    onSeek: (Int) -> Unit
) {
    // Split full text into paragraphs once
    val paragraphs = remember(uiState.fullText) {
        uiState.fullText
            .split(Regex("\n{2,}"))
            .map { it.trim() }
            .filter { it.length > 5 }
            .ifEmpty { listOf(uiState.fullText) }
    }

    val strength = uiState.preferences.bionicFixationStrength
    val listState = rememberLazyListState()

    // Scroll to current word's paragraph on first load
    val wordIndex = uiState.currentWordIndex
    LaunchedEffect(wordIndex) {
        if (wordIndex == 0 || paragraphs.isEmpty()) return@LaunchedEffect
        // Estimate which paragraph contains the current word
        var wordCount = 0
        var targetPara = 0
        for ((i, para) in paragraphs.withIndex()) {
            val paraWords = para.split(Regex("\\s+")).count { it.isNotBlank() }
            wordCount += paraWords
            if (wordCount >= wordIndex) { targetPara = i; break }
        }
        listState.animateScrollToItem(targetPara.coerceIn(0, paragraphs.size - 1))
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        itemsIndexed(paragraphs) { _, paragraph ->
            BionicParagraph(
                text     = paragraph,
                strength = strength,
                fontSize = uiState.fontSize
            )
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}

@Composable
private fun BionicParagraph(
    text: String,
    strength: Float,
    fontSize: Float
) {
    // Apply bionic to this paragraph's words only — small, bounded work
    val bionicWords: List<BionicWord> = remember(text, strength) {
        TextProcessor.applyBionic(text, strength)
    }

    val annotated = remember(bionicWords) {
        buildAnnotatedString {
            bionicWords.forEachIndexed { index, word ->
                withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) {
                    append(word.boldPart)
                }
                withStyle(SpanStyle(fontWeight = FontWeight.Normal)) {
                    append(word.normalPart)
                }
                if (index < bionicWords.size - 1) append(" ")
            }
        }
    }

    Text(
        text      = annotated,
        style     = MaterialTheme.typography.bodyLarge.copy(
            fontSize   = fontSize.sp,
            lineHeight = (fontSize * 1.7f).sp
        ),
        textAlign = TextAlign.Start,
        color     = MaterialTheme.colorScheme.onBackground
    )
}
