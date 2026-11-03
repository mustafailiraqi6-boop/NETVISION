package com.netvision.app.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Shared NETVISION identity: charcoal, green actions, red stop/error, amber favorites.
val NetGreen = Color(0xFF16834B)
val NetGreenLight = Color(0xFF16834B)
val NetRed = Color(0xFFB92F32)
val NetAmber = Color(0xFFE6A23C)

// Compatibility palette names used by older screens, now mapped to the unified identity.
val NavyDark = Color(0xFF000000)
val NavySurface = Color(0xFF080808)
val NavyCard = Color(0xFF242424)
val NavyBorder = Color(0xFF303030)
val CyanAccent = NetGreen
val ElectricBlue = Color(0xFF319B68)
val ElectricBlueContainer = Color(0xFF164D34)
val StatusGreen = NetGreen
val StatusAmber = NetAmber
val StatusRed = NetRed
val TextPrimaryDark = Color(0xFFF5F5F5)
val TextSecondaryDark = Color(0xFFAAAAAA)
val TextMutedDark = Color(0xFF777777)
val SlateLightBg = Color(0xFFF3F4F6)
val SlateLightSurface = Color.White
val SlateLightCard = Color(0xFFE8E8E8)
val SlateLightBorder = Color(0xFFD1D5DB)
val TextPrimaryLight = Color(0xFF171717)
val TextSecondaryLight = Color(0xFF666666)
val PurplePrimary = NetGreen
val PurpleDeep = Color(0xFF104A2C)
val PurpleContainer = Color(0xFFD9F2E3)
val TealAccent = NetGreen

private val DarkColorScheme = darkColorScheme(
    primary = NetGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF164D34),
    onPrimaryContainer = Color(0xFFD6F5E3),
    secondary = Color(0xFF8BC9A5),
    onSecondary = Color(0xFF082516),
    tertiary = NetAmber,
    onTertiary = Color(0xFF241700),
    error = NetRed,
    onError = Color.White,
    background = Color(0xFF000000),
    onBackground = Color(0xFFF5F5F5),
    surface = Color(0xFF080808),
    onSurface = Color(0xFFF5F5F5),
    surfaceVariant = Color(0xFF242424),
    onSurfaceVariant = Color(0xFFAAAAAA),
    outline = Color(0xFF303030)
)

private val LightColorScheme = lightColorScheme(
    primary = NetGreenLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9F2E3),
    onPrimaryContainer = Color(0xFF104A2C),
    secondary = Color(0xFF397A57),
    onSecondary = Color.White,
    tertiary = Color(0xFFB7791F),
    onTertiary = Color.White,
    error = NetRed,
    onError = Color.White,
    background = Color(0xFFF3F4F6),
    onBackground = Color(0xFF171717),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF171717),
    surfaceVariant = Color(0xFFE8E8E8),
    onSurfaceVariant = Color(0xFF666666),
    outline = Color(0xFFD1D5DB)
)

@Composable
fun NetVisionTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
