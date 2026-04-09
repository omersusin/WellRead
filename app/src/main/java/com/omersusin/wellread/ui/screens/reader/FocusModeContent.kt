package com.omersusin.wellread.ui.screens.reader

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.omersusin.wellread.ui.theme.FocusColor

@Composable
fun FocusModeContent(uiState: ReaderUiState) {
    val scrollState = rememberScrollState()

    // Auto scroll to current word
    LaunchedEffect(uiState.currentWordIndex) {
        if (uiState.words.isNotEmpty()) {
            val progress = uiState.currentWordIndex.toFloat() / uiState.words.size
            val targetScroll = (scrollState.maxValue * progress).toInt()
            scrollState.animateScrollTo(targetScroll)
        }
    }

    val annotatedText = remember(uiState.words, uiState.currentWordIndex) {
        buildAnnotatedString {
            uiState.words.forEachIndexed { index, word ->
                if (index == uiState.currentWordIndex) {
                    withStyle(
                        SpanStyle(
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            background = FocusColor
                        )
                    ) {
                        append(" $word ")
                    }
                } else {
                    val alpha = when {
                        index < uiState.currentWordIndex - 20 -> 0.25f
                        index < uiState.currentWordIndex -> 0.55f
                        index > uiState.currentWordIndex + 20 -> 0.7f
                        else -> 0.85f
                    }
                    withStyle(SpanStyle(color = Color.White.copy(alpha = alpha))) {
                        append(word)
                    }
                }
                if (index < uiState.words.size - 1) append(" ")
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Text(
            text = annotatedText,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = uiState.fontSize.sp,
                lineHeight = (uiState.fontSize * 1.8f).sp
            ),
            textAlign = TextAlign.Start
        )
        Spacer(modifier = Modifier.height(200.dp))
    }
}
