package com.netvision.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// NETVISION Color Palette: Navy, Charcoal, Electric Blue, Cyan
val NavyDark = Color(0xFF0A0F1D)
val NavySurface = Color(0xFF111A2E)
val NavyCard = Color(0xFF17233D)
val NavyBorder = Color(0xFF22355A)

val CyanAccent = Color(0xFF00E5FF)
val ElectricBlue = Color(0xFF0091FF)
val ElectricBlueContainer = Color(0xFF0A2E5C)

val StatusGreen = Color(0xFF10B981)
val StatusAmber = Color(0xFFF59E0B)
val StatusRed = Color(0xFFEF4444)

val TextPrimaryDark = Color(0xFFF8FAFC)
val TextSecondaryDark = Color(0xFF94A3B8)
val TextMutedDark = Color(0xFF64748B)

// Light Theme equivalents
val SlateLightBg = Color(0xFFF2F0FA)
val SlateLightSurface = Color(0xFFFFFFFF)
val SlateLightCard = Color(0xFFF8F6FF)
val SlateLightBorder = Color(0xFFDDD6F3)
val TextPrimaryLight = Color(0xFF211B35)
val TextSecondaryLight = Color(0xFF625A75)
val PurplePrimary = Color(0xFF5426B8)
val PurpleDeep = Color(0xFF3D168F)
val PurpleContainer = Color(0xFFE8DFFF)
val TealAccent = Color(0xFF0F9D9A)

private val DarkColorScheme = darkColorScheme(
    primary = CyanAccent,
    onPrimary = NavyDark,
    primaryContainer = ElectricBlueContainer,
    onPrimaryContainer = CyanAccent,
    secondary = ElectricBlue,
    onSecondary = Color.White,
    background = NavyDark,
    onBackground = TextPrimaryDark,
    surface = NavySurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = NavyCard,
    onSurfaceVariant = TextSecondaryDark,
    outline = NavyBorder
)

private val LightColorScheme = lightColorScheme(
    primary = PurplePrimary,
    onPrimary = Color.White,
    primaryContainer = PurpleContainer,
    onPrimaryContainer = PurpleDeep,
    secondary = TealAccent,
    onSecondary = Color.White,
    background = SlateLightBg,
    onBackground = TextPrimaryLight,
    surface = SlateLightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = SlateLightCard,
    onSurfaceVariant = TextSecondaryLight,
    outline = SlateLightBorder
)

@Composable
fun NetVisionTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
