package net.meshkati.umm

import android.app.Activity
import android.app.UiModeManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

enum class ThemeMode { SYSTEM, LIGHT, DARK }

private val LocalDarkTheme = staticCompositionLocalOf { false }

/** True when the current [UmmTheme] is dark. */
val isDarkTheme: Boolean
    @Composable get() = LocalDarkTheme.current

/** Material 3 baseline light or dark colors, following [mode]. */
@Composable
fun UmmTheme(mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    // The window theme sets the bar icons from the system's night mode, which can disagree with [mode].
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    CompositionLocalProvider(LocalDarkTheme provides dark) {
        MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme(), content = content)
    }
}

/**
 * Tells the system which night mode Umm wants (Android 12+), so windows open in the right colors
 * before Compose draws — the pause screen would otherwise flash white over the app being opened.
 * The change recreates running activities. Older versions only get the Compose colors.
 */
fun applyNightMode(context: Context, mode: ThemeMode) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val nightMode = when (mode) {
        ThemeMode.SYSTEM -> UiModeManager.MODE_NIGHT_AUTO
        ThemeMode.LIGHT -> UiModeManager.MODE_NIGHT_NO
        ThemeMode.DARK -> UiModeManager.MODE_NIGHT_YES
    }
    context.getSystemService(UiModeManager::class.java).setApplicationNightMode(nightMode)
}
