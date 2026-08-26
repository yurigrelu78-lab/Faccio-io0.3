package it.faccioio.app

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    background = Color(0xFFFBF9F6),
    onBackground = Color(0xFF173A5E),
    surface = Color.White,
    onSurface = Color(0xFF173A5E),
    surfaceVariant = Color(0xFFF2F5F5),
    onSurfaceVariant = Color(0xFF5E6875),
    outline = Color(0xFF9AA4AA),
    error = Color(0xFFE96957),
    errorContainer = Color(0xFFFFE4DF)
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
    background = Color(0xFF0F161C),
    onBackground = Color(0xFFE2E8EE),
    surface = Color(0xFF18232C),
    onSurface = Color(0xFFE2E8EE),
    surfaceVariant = Color(0xFF22313B),
    onSurfaceVariant = Color(0xFFB8C3CC),
    outline = Color(0xFF7F909B),
    error = Color(0xFFFF9384),
    errorContainer = Color(0xFF5D2521)
)

private val FaccioTypography = Typography(
    headlineLarge = Typography().headlineLarge.copy(fontSize = 38.sp, lineHeight = 42.sp, fontWeight = FontWeight.Bold),
    titleLarge = Typography().titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = Typography().titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = Typography().labelLarge.copy(fontWeight = FontWeight.SemiBold)
)

private val FaccioShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
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
        typography = FaccioTypography,
        shapes = FaccioShapes,
        content = content
    )
}
