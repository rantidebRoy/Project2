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

// -----------------------------
// Dark Theme (same as light)
// -----------------------------
private val DarkColorScheme = darkColorScheme(
    primary = Navy40,
    onPrimary = Color.White,

    secondary = NavyGrey40,
    onSecondary = Color.White,

    tertiary = NavyAccent40,
    onTertiary = Color.White,

    background = Color.White,      // PURE WHITE
    onBackground = Color.Black,

    surface = Color.White,         // PURE WHITE
    onSurface = Color.Black,

    surfaceVariant = Color.White,
    onSurfaceVariant = Color.Black
)

// -----------------------------
// Light Theme (same as dark)
// -----------------------------
private val LightColorScheme = lightColorScheme(
    primary = Navy40,
    onPrimary = Color.White,

    secondary = NavyGrey40,
    onSecondary = Color.White,

    tertiary = NavyAccent40,
    onTertiary = Color.White,

    background = Color.White,      // PURE WHITE
    onBackground = Color.Black,

    surface = Color.White,         // PURE WHITE
    onSurface = Color.Black,

    surfaceVariant = Color.White,
    onSurfaceVariant = Color.Black
)


// -----------------------------
// Main Theme
// -----------------------------
@Composable
fun FlashcardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    // Use same color scheme for both light & dark
    val colorScheme = LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
