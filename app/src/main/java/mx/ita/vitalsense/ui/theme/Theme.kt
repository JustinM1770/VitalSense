package mx.ita.vitalsense.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color

private val BioMetricAIColorScheme = lightColorScheme(
    primary            = PrimaryBlue,
    onPrimary          = Color.White,
    primaryContainer   = PrimaryBlueLight,
    onPrimaryContainer = PrimaryBlueDark,
    secondary          = SleepGreen,
    onSecondary        = Color.White,
    background         = Color.White,
    onBackground       = TextPrimary,
    surface            = SurfaceWhite,
    onSurface          = TextPrimary,
    surfaceVariant     = InputBg,
    onSurfaceVariant   = TextSecondary,
    error              = HeartRateRed,
    onError            = Color.White,
)

private val BioMetricAIDarkColorScheme = darkColorScheme(
    primary = PrimaryBlue,
    onPrimary = Color.White,
    secondary = SleepGreen,
    onSecondary = Color.White,
    background = Color(0xFF0B1220),
    onBackground = Color(0xFFE2E8F0),
    surface = Color(0xFF111827),
    onSurface = Color(0xFFE5E7EB),
    surfaceVariant = Color(0xFF1F2937),
    onSurfaceVariant = Color(0xFFF1F5F9),
    error = HeartRateRed,
    onError = Color.White,
)

@Composable
fun BioMetricAITheme(
    themeMode: String = "system",
    content: @Composable () -> Unit,
) {
    val isDark = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (isDark) BioMetricAIDarkColorScheme else BioMetricAIColorScheme,
        typography  = Typography,
        content     = content,
    )
}
