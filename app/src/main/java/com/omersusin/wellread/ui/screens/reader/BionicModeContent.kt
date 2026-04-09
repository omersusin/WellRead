package com.omersusin.wellread.ui.screens.reader

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.omersusin.wellread.utils.TextProcessor

@Composable
fun BionicModeContent(
    uiState: ReaderUiState,
    onSeek: (Int) -> Unit
) {
    val scrollState = rememberScrollState()
    val bionicWords = remember(uiState.fullText, uiState.preferences.bionicFixationStrength) {
        TextProcessor.applyBionic(uiState.fullText, uiState.preferences.bionicFixationStrength)
    }

    val annotatedText = remember(bionicWords) {
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
                lineHeight = (uiState.fontSize * 1.7f).sp
            ),
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Start
        )
        Spacer(modifier = Modifier.height(80.dp))
    }
}
