package kittoku.osc.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color


// Палитра фиксированная, без Material You: приложение должно выглядеть одинаково
// на любом телефоне, а зелёный несёт смысл — им же показано состояние туннеля.
private val Green = Color(0xFF16A34A)
private val GreenDark = Color(0xFF15803D)
private val GreenLight = Color(0xFF4ADE80)
private val GreenSurface = Color(0xFFE8F5EC)

private val LightScheme = lightColorScheme(
    primary = Green,
    onPrimary = Color.White,
    primaryContainer = GreenSurface,
    onPrimaryContainer = GreenDark,
    secondary = GreenDark,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6E9EA),
    onSecondaryContainer = Color(0xFF14181A),
    tertiary = Color(0xFF2563EB),
    background = Color(0xFFF7F8F8),
    onBackground = Color(0xFF14181A),
    surface = Color.White,
    onSurface = Color(0xFF14181A),
    surfaceVariant = Color(0xFFF1F3F4),
    onSurfaceVariant = Color(0xFF6B7280),
    outline = Color(0xFFD5DADD),
    outlineVariant = Color(0xFFE7EAEC),
    error = Color(0xFFDC2626),
)

private val DarkScheme = darkColorScheme(
    primary = GreenLight,
    onPrimary = Color(0xFF04240F),
    primaryContainer = Color(0xFF14361F),
    onPrimaryContainer = GreenLight,
    secondary = GreenLight,
    onSecondary = Color(0xFF04240F),
    secondaryContainer = Color(0xFF242926),
    onSecondaryContainer = Color(0xFFE7EAEC),
    tertiary = Color(0xFF93C5FD),
    background = Color(0xFF0E1211),
    onBackground = Color(0xFFE7EAEC),
    surface = Color(0xFF171B1A),
    onSurface = Color(0xFFE7EAEC),
    surfaceVariant = Color(0xFF1F2422),
    onSurfaceVariant = Color(0xFF9AA3A8),
    outline = Color(0xFF39403D),
    outlineVariant = Color(0xFF262B29),
    error = Color(0xFFF87171),
)

@Composable
internal fun SstpTheme(
    isDark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (isDark) DarkScheme else LightScheme,
        content = content,
    )
}
