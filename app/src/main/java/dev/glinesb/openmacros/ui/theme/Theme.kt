package dev.glinesb.openmacros.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Branded fallback for Android 10–11 (no dynamic color): green primary.
private val LightScheme = lightColorScheme(
    primary = Color(0xFF2E6B3F), onPrimary = Color.White,
    primaryContainer = Color(0xFFB1F1BC), onPrimaryContainer = Color(0xFF00210B),
    secondary = Color(0xFF506352), tertiary = Color(0xFF3A656F),
)
private val DarkScheme = darkColorScheme(
    primary = Color(0xFF96D5A1), onPrimary = Color(0xFF003918),
    primaryContainer = Color(0xFF125229), onPrimaryContainer = Color(0xFFB1F1BC),
    secondary = Color(0xFFB7CCB7), tertiary = Color(0xFFA2CED9),
)

/** Fixed macro colours so protein/carbs/fat read the same everywhere, tuned per light/dark. */
@Immutable
data class MacroColors(val protein: Color, val carbs: Color, val fat: Color)

val LocalMacroColors = staticCompositionLocalOf { MacroColors(Color(0xFFD9534F), Color(0xFF2B8CBE), Color(0xFFE0A100)) }

/** Follows the system light/dark setting; Material You dynamic color on Android 12+. */
@Composable
fun OpenMacrosTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> DarkScheme
        else -> LightScheme
    }
    val macro = if (darkTheme) MacroColors(Color(0xFFFF8A80), Color(0xFF80C8F0), Color(0xFFFFD166))
    else MacroColors(Color(0xFFC62828), Color(0xFF1565C0), Color(0xFFB07800))
    androidx.compose.runtime.CompositionLocalProvider(LocalMacroColors provides macro) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
