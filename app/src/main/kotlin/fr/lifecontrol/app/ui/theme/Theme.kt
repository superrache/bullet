package fr.lifecontrol.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import fr.lifecontrol.app.data.repository.ThemeMode

private val LightColors = lightColorScheme(
    primary = Color(0xFF5B4FD6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE4DFFF),
    onPrimaryContainer = Color(0xFF1A0F6B),
    secondary = Color(0xFFE07A3F),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDCC7),
    onSecondaryContainer = Color(0xFF3A1600),
    tertiary = Color(0xFF2E9E78),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFC6F2DE),
    onTertiaryContainer = Color(0xFF00382A),
    background = Color(0xFFFFFBF7),
    onBackground = Color(0xFF1D1B20),
    surface = Color(0xFFFFFBF7),
    onSurface = Color(0xFF1D1B20),
    surfaceVariant = Color(0xFFF1ECF4),
    onSurfaceVariant = Color(0xFF49454F),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFBF6F3),
    surfaceContainer = Color(0xFFF6F0EE),
    surfaceContainerHigh = Color(0xFFF0EAE8),
    surfaceContainerHighest = Color(0xFFEAE4E2),
    outline = Color(0xFF7A757F),
    outlineVariant = Color(0xFFCBC4CF),
    error = Color(0xFFD03A3A),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFC7BFFF),
    onPrimary = Color(0xFF2B1E9E),
    primaryContainer = Color(0xFF4337BD),
    onPrimaryContainer = Color(0xFFE4DFFF),
    secondary = Color(0xFFFFB68C),
    onSecondary = Color(0xFF552100),
    secondaryContainer = Color(0xFF7A3510),
    onSecondaryContainer = Color(0xFFFFDCC7),
    tertiary = Color(0xFF7FD9B4),
    onTertiary = Color(0xFF003828),
    tertiaryContainer = Color(0xFF00513C),
    onTertiaryContainer = Color(0xFFC6F2DE),
    background = Color(0xFF151318),
    onBackground = Color(0xFFE7E1E6),
    surface = Color(0xFF151318),
    onSurface = Color(0xFFE7E1E6),
    surfaceVariant = Color(0xFF2C2930),
    onSurfaceVariant = Color(0xFFCAC4CF),
    surfaceContainerLowest = Color(0xFF100E12),
    surfaceContainerLow = Color(0xFF1D1B20),
    surfaceContainer = Color(0xFF221F25),
    surfaceContainerHigh = Color(0xFF2C292F),
    surfaceContainerHighest = Color(0xFF37343A),
    outline = Color(0xFF948F99),
    outlineVariant = Color(0xFF49454F),
    error = Color(0xFFFFB4AB),
)

/** Couleurs sémantiques des statuts. Jamais utilisées seules : toujours doublées d'une icône (§9). */
@Immutable
data class StatusColors(
    val success: Color,
    val onSuccess: Color,
    val failure: Color,
    val onFailure: Color,
    val missing: Color,
    val pending: Color,
    val neutral: Color,
)

private val LightStatus = StatusColors(
    success = Color(0xFF2E9E78), onSuccess = Color.White,
    failure = Color(0xFFE5574F), onFailure = Color.White,
    missing = Color(0xFFD9D3D8), pending = Color(0xFFF2B84B), neutral = Color(0xFFEFEAEE),
)

private val DarkStatus = StatusColors(
    success = Color(0xFF4CC497), onSuccess = Color(0xFF00281C),
    failure = Color(0xFFFF7A70), onFailure = Color(0xFF3B0503),
    missing = Color(0xFF4A464D), pending = Color(0xFFF2C266), neutral = Color(0xFF2A272D),
)

val LocalStatusColors = staticCompositionLocalOf { LightStatus }

/** Palette de 12 couleurs d'habitudes (F-03). */
object HabitPalette {
    val colors = listOf(
        Color(0xFF5B4FD6), Color(0xFFE07A3F), Color(0xFF2E9E78), Color(0xFFD94F7A),
        Color(0xFF3B8ED8), Color(0xFFC9A227), Color(0xFF8E5BD6), Color(0xFF2BA8A8),
        Color(0xFF7A9E2E), Color(0xFFB5543C), Color(0xFF5C6B7A), Color(0xFFE05A5A),
    )

    fun color(index: Int): Color = colors[index.mod(colors.size)]
}

private val AppTypography = Typography().let { t ->
    t.copy(
        headlineLarge = t.headlineLarge.copy(fontWeight = FontWeight.Bold),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    )
}

val EmojiStyle = TextStyle(fontSize = 22.sp)

@Composable
fun BulletTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dark -> DarkColors
        else -> LightColors
    }
    CompositionLocalProvider(LocalStatusColors provides if (dark) DarkStatus else LightStatus) {
        MaterialTheme(colorScheme = colors, typography = AppTypography, content = content)
    }
}
