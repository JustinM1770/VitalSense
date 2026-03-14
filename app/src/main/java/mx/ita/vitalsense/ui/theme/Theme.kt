package mx.ita.vitalsense.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val VitalSenseColorScheme = lightColorScheme(
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

@Composable
fun VitalSenseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = VitalSenseColorScheme,
        typography  = Typography,
        content     = content,
    )
}
