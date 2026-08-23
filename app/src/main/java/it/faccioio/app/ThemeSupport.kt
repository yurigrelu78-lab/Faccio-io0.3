package it.faccioio.app

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

internal const val THEME_SYSTEM = "Sistema"
internal const val THEME_LIGHT = "Chiaro"
internal const val THEME_DARK = "Scuro"
internal val THEME_OPTIONS = listOf(THEME_SYSTEM, THEME_LIGHT, THEME_DARK)

private const val THEME_PREFS = "faccio_io_appearance"
private const val THEME_MODE_KEY = "theme_mode"

private val LightColors = lightColorScheme(
    primary = Color(0xFF173A5E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD8E7F3),
    onPrimaryContainer = Color(0xFF0B2944),
    secondary = Color(0xFF188C8C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD2EFEF),
    onSecondaryContainer = Color(0xFF063E3E),
    tertiary = Color(0xFFF1AD43),
    background = Color(0xFFF7F9FB),
    onBackground = Color(0xFF173A5E),
    surface = Color.White,
    onSurface = Color(0xFF173A5E),
    surfaceVariant = Color(0xFFF0F4F7),
    onSurfaceVariant = Color(0xFF5E6875),
    outline = Color(0xFF78838F)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA9CBE7),
    onPrimary = Color(0xFF0D2F4D),
    primaryContainer = Color(0xFF173A5E),
    onPrimaryContainer = Color(0xFFD7E8F5),
    secondary = Color(0xFF66CCCC),
    onSecondary = Color(0xFF003737),
    secondaryContainer = Color(0xFF0C4F50),
    onSecondaryContainer = Color(0xFFB5EEEE),
    tertiary = Color(0xFFFFC66C),
    background = Color(0xFF10161C),
    onBackground = Color(0xFFE2E8EE),
    surface = Color(0xFF182129),
    onSurface = Color(0xFFE2E8EE),
    surfaceVariant = Color(0xFF222E37),
    onSurfaceVariant = Color(0xFFB8C3CC),
    outline = Color(0xFF89959F)
)

internal fun loadThemeMode(context: Context): String =
    context.getSharedPreferences(THEME_PREFS, Context.MODE_PRIVATE)
        .getString(THEME_MODE_KEY, THEME_SYSTEM)
        ?.takeIf { it in THEME_OPTIONS }
        ?: THEME_SYSTEM

internal fun saveThemeMode(context: Context, mode: String) {
    if (mode !in THEME_OPTIONS) return
    context.getSharedPreferences(THEME_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(THEME_MODE_KEY, mode)
        .apply()
    AgendaWidgetProvider.updateAll(context)
}

@Composable
internal fun FaccioIoTheme(
    mode: String,
    content: @Composable () -> Unit
) {
    val useDarkTheme = when (mode) {
        THEME_LIGHT -> false
        THEME_DARK -> true
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (useDarkTheme) DarkColors else LightColors,
        content = content
    )
}
