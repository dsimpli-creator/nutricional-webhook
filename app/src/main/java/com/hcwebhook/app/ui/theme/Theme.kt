package com.hcwebhook.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// Nutricional Webhook: esquema da marca do Nutricional (verde do ícone, #23875E -> #1A6A49),
// com superfícies neutras. O verde vivo fica só nas ações principais e no cartão de estado.
private val BrandLight = lightColorScheme(
    primary = Color(0xFF1A6A49),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF1F7A55),
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = Color(0xFF4E6357),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD0E8D8),
    onSecondaryContainer = Color(0xFF0B1F15),
    tertiary = Color(0xFF006A35),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFB7F1C4),
    onTertiaryContainer = Color(0xFF00210D),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    background = Color(0xFFF6FAF7),
    onBackground = Color(0xFF171D19),
    surface = Color(0xFFF6FAF7),
    onSurface = Color(0xFF171D19),
    surfaceVariant = Color(0xFFDCE5DE),
    onSurfaceVariant = Color(0xFF404943),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F5F1),
    surfaceContainer = Color(0xFFEAF0EB),
    surfaceContainerHigh = Color(0xFFE4EAE6),
    surfaceContainerHighest = Color(0xFFDFE5E0),
    outline = Color(0xFF707973),
    outlineVariant = Color(0xFFC0C9C1),
    inverseSurface = Color(0xFF2C322E),
    inverseOnSurface = Color(0xFFEDF2EE),
    inversePrimary = Color(0xFF8AD6B0),
)

private val BrandDark = darkColorScheme(
    primary = Color(0xFF8AD6B0),
    onPrimary = Color(0xFF003824),
    primaryContainer = Color(0xFF1F7A55),
    onPrimaryContainer = Color(0xFFFFFFFF),
    secondary = Color(0xFFB4CCBD),
    onSecondary = Color(0xFF20352A),
    secondaryContainer = Color(0xFF364B3F),
    onSecondaryContainer = Color(0xFFD0E8D8),
    tertiary = Color(0xFF6DDC8E),
    onTertiary = Color(0xFF003919),
    tertiaryContainer = Color(0xFF005226),
    onTertiaryContainer = Color(0xFFB7F1C4),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0F1714),
    onBackground = Color(0xFFDFE4DF),
    surface = Color(0xFF0F1714),
    onSurface = Color(0xFFDFE4DF),
    surfaceVariant = Color(0xFF404943),
    onSurfaceVariant = Color(0xFFC0C9C1),
    surfaceContainerLowest = Color(0xFF0A110E),
    surfaceContainerLow = Color(0xFF17201B),
    surfaceContainer = Color(0xFF1B241F),
    surfaceContainerHigh = Color(0xFF252E29),
    surfaceContainerHighest = Color(0xFF303934),
    outline = Color(0xFF8A938C),
    outlineVariant = Color(0xFF404943),
    inverseSurface = Color(0xFFDFE4DF),
    inverseOnSurface = Color(0xFF2C322E),
    inversePrimary = Color(0xFF1A6A49),
)

@Composable
fun HCWebhookTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Colors from the wallpaper (Android 12+). Off by default; the user turns it on in Settings.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> BrandDark
        else -> BrandLight
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
