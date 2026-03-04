package mx.ita.vitalsense.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val HealthSensorColorScheme = lightColorScheme(
    primary = PrimaryBlue,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    background = NeomorphicBackground,
    surface = NeomorphicBackground,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
)

@Composable
fun VitalSenseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = HealthSensorColorScheme,
        typography = Typography,
        content = content,
    )
}
