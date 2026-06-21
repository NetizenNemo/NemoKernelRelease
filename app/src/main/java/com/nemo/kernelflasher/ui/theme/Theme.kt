package com.nemo.kernelflasher.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowInsetsControllerCompat
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

enum class AppThemeMode(
    val value: Int,
    val colorSchemeMode: ColorSchemeMode,
) {
    MIUIX_SYSTEM(0, ColorSchemeMode.System),
    MIUIX_LIGHT(1, ColorSchemeMode.Light),
    MIUIX_DARK(2, ColorSchemeMode.Dark),
    MONET_SYSTEM(3, ColorSchemeMode.MonetSystem),
    MONET_LIGHT(4, ColorSchemeMode.MonetLight),
    MONET_DARK(5, ColorSchemeMode.MonetDark);

    fun isDark(systemDark: Boolean): Boolean = when (this) {
        MIUIX_SYSTEM -> systemDark
        MIUIX_LIGHT -> false
        MIUIX_DARK -> true
        MONET_SYSTEM -> systemDark
        MONET_LIGHT -> false
        MONET_DARK -> true
    }

    companion object {
        val default = MIUIX_SYSTEM

        fun fromValue(value: Int): AppThemeMode =
            entries.firstOrNull { it.value == value } ?: default
    }
}

@Composable
@ReadOnlyComposable
fun isAppInDarkTheme(themeMode: AppThemeMode): Boolean {
    return themeMode.isDark(isSystemInDarkTheme())
}

@Composable
fun AppTheme(
    themeMode: AppThemeMode = AppThemeMode.default,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val darkTheme = isAppInDarkTheme(themeMode)
    val controller = ThemeController(themeMode.colorSchemeMode)

    MiuixTheme(controller) {
        LaunchedEffect(darkTheme) {
            val window = (context as? Activity)?.window ?: return@LaunchedEffect
            WindowInsetsControllerCompat(window, window.decorView).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
        content()
    }
}
