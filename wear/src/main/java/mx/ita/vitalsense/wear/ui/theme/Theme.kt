package mx.ita.vitalsense.wear.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme

// Map mobile colors to Wear Material Colors
// Wear OS is dark theme by default, so we adapt the Palette for dark background
private val BioMetricAIWearColors = Colors(
    primary = PrimaryBlue,
    primaryVariant = PrimaryBlueDark,
    secondary = SleepGreen,
    secondaryVariant = SleepGreen,
    background = Color.Black,
    surface = Color(0xFF1A1A1A),
    error = HeartRateRed,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White,
    onError = Color.White
)

@Composable
fun BioMetricAIWearTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = BioMetricAIWearColors,
        content = content,
    )
}
