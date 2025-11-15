package com.example.flashcard.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Navy80,
    onPrimary = Color.White,

    secondary = NavyGrey80,
    onSecondary = Color.White,

    tertiary = NavyAccent80,
    onTertiary = Color.White,

    background = Color(0xFF0B0F26),
    onBackground = Color.White,

    surface = Color(0xFF0F1A40),
    onSurface = Color.White,

    surfaceVariant = Color(0xFF1C2A57),
    onSurfaceVariant = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = Navy40,
    secondary = NavyGrey40,
    tertiary = NavyAccent40
)


@Composable
fun FlashcardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
