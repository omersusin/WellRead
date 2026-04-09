package com.omersusin.wellread.ui.screens.reader

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.omersusin.wellread.ui.theme.FlashColor
import com.omersusin.wellread.ui.theme.WellReadPurple

@Composable
fun FlashModeContent(uiState: ReaderUiState) {
    val currentWord = uiState.words.getOrNull(uiState.currentWordIndex) ?: ""
    val progress = if (uiState.words.isNotEmpty())
        uiState.currentWordIndex.toFloat() / uiState.words.size else 0f

    val scale by animateFloatAsState(
        targetValue = if (uiState.isPlaying) 1f else 0.95f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            FocusGuideLines()

            Spacer(modifier = Modifier.height(24.dp))

            AnimatedContent(
                targetState = currentWord,
                transitionSpec = {
                    (fadeIn(tween(80)) + scaleIn(tween(80), initialScale = 0.85f))
                        .togetherWith(fadeOut(tween(60)) + scaleOut(tween(60)))
                },
                label = "word"
            ) { word ->
                Text(
                    text = word,
                    style = MaterialTheme.typography.displayMedium.copy(
                        fontSize = uiState.fontSize.times(2.2f).sp
                    ),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.scale(scale)
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = uiState.words.getOrNull(uiState.currentWordIndex - 1) ?: "",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
                Text(
                    text = "•",
                    style = MaterialTheme.typography.titleMedium,
                    color = FlashColor.copy(alpha = 0.6f)
                )
                Text(
                    text = uiState.words.getOrNull(uiState.currentWordIndex + 1) ?: "",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            Text(
                text = "${(progress * 100).toInt()}% · ${uiState.wpm} WPM",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FocusGuideLines() {
    Box(
        modifier = Modifier
            .width(280.dp)
            .height(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HorizontalDivider(
                modifier = Modifier.width(110.dp),
                color = WellReadPurple.copy(alpha = 0.3f),
                thickness = 2.dp
            )
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                modifier = Modifier.size(8.dp),
                shape = CircleShape,
                color = FlashColor
            ) {}
            Spacer(modifier = Modifier.width(8.dp))
            HorizontalDivider(
                modifier = Modifier.width(110.dp),
                color = WellReadPurple.copy(alpha = 0.3f),
                thickness = 2.dp
            )
        }
    }
}
