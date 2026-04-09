package com.omersusin.wellread.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// MD3 Expressive — Dark Scheme
private val DarkColorScheme = darkColorScheme(
    primary              = Color(0xFF9F8FFF),   // Daha canlı mor
    onPrimary            = Color(0xFF1A0080),
    primaryContainer     = Color(0xFF4B3BBF),
    onPrimaryContainer   = Color(0xFFE2DCFF),
    secondary            = Color(0xFF72EFDD),   // Canlı teal
    onSecondary          = Color(0xFF003730),
    secondaryContainer   = Color(0xFF005049),
    onSecondaryContainer = Color(0xFF9EFBE8),
    tertiary             = Color(0xFFFFB74D),   // Sıcak amber
    onTertiary           = Color(0xFF3D2000),
    tertiaryContainer    = Color(0xFF7A4500),
    onTertiaryContainer  = Color(0xFFFFDDB4),
    background           = Color(0xFF0D0D1A),
    onBackground         = Color(0xFFEAE8FF),
    surface              = Color(0xFF0D0D1A),
    onSurface            = Color(0xFFEAE8FF),
    surfaceVariant       = Color(0xFF1E1B35),
    onSurfaceVariant     = Color(0xFFB8B4D0),
    surfaceContainer         = Color(0xFF191628),
    surfaceContainerHigh     = Color(0xFF211E35),
    surfaceContainerHighest  = Color(0xFF2B2843),
    outline              = Color(0xFF6B6880),
    outlineVariant       = Color(0xFF3A3750),
    error                = Color(0xFFFF6B8A),
    onError              = Color(0xFF5C0020),
    errorContainer       = Color(0xFF8C0035),
    onErrorContainer     = Color(0xFFFFB3C3),
    inverseSurface       = Color(0xFFE9E6FF),
    inverseOnSurface     = Color(0xFF1E1B35),
    inversePrimary       = Color(0xFF4F3FBF)
)

// MD3 Expressive — Light Scheme
private val LightColorScheme = lightColorScheme(
    primary              = Color(0xFF4F3FBF),
    onPrimary            = Color.White,
    primaryContainer     = Color(0xFFE2DCFF),
    onPrimaryContainer   = Color(0xFF180070),
    secondary            = Color(0xFF006B5F),
    onSecondary          = Color.White,
    secondaryContainer   = Color(0xFF9EFBE8),
    onSecondaryContainer = Color(0xFF00201C),
    tertiary             = Color(0xFF7A4500),
    onTertiary           = Color.White,
    tertiaryContainer    = Color(0xFFFFDDB4),
    onTertiaryContainer  = Color(0xFF271900),
    background           = Color(0xFFFAF8FF),
    onBackground         = Color(0xFF1C1A2E),
    surface              = Color(0xFFFAF8FF),
    onSurface            = Color(0xFF1C1A2E),
    surfaceVariant       = Color(0xFFE5E0FF),
    onSurfaceVariant     = Color(0xFF47455C),
    surfaceContainer         = Color(0xFFEEEBFF),
    surfaceContainerHigh     = Color(0xFFE8E4FF),
    surfaceContainerHighest  = Color(0xFFE2DCFF),
    outline              = Color(0xFF78758C),
    error                = Color(0xFFB00040),
    onError              = Color.White,
    inverseSurface       = Color(0xFF312F47),
    inverseOnSurface     = Color(0xFFEFECFF),
    inversePrimary       = Color(0xFFB8AFFF)
)

@Composable
fun WellReadTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else      -> LightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor     = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars     = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = WellReadTypography,
        shapes      = WellReadShapes,
        content     = content
    )
}
