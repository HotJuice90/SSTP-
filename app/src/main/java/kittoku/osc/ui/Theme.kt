package kittoku.osc.ui

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


// Запасная палитра в тон иконке — используется там, где системных цветов нет
// (Android 11 и ниже) или обои не дают динамической схемы.
private val Navy = Color(0xFF14304A)
private val NavyLight = Color(0xFF2E5A83)
private val Sky = Color(0xFF8ECAF6)

private val LightScheme = lightColorScheme(
    primary = Navy,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3E4F5),
    onPrimaryContainer = Navy,
    secondary = NavyLight,
    tertiary = Color(0xFF2F6B4F),
)

private val DarkScheme = darkColorScheme(
    primary = Sky,
    onPrimary = Navy,
    primaryContainer = Color(0xFF1E4062),
    onPrimaryContainer = Color(0xFFD3E4F5),
    secondary = Color(0xFFA8C7E5),
    tertiary = Color(0xFF8ED6AC),
)

@Composable
internal fun SstpTheme(
    isDark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current

    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        isDark -> DarkScheme

        else -> LightScheme
    }

    MaterialTheme(colorScheme = scheme, content = content)
}
