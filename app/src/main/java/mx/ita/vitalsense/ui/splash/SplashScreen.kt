package mx.ita.vitalsense.ui.splash

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import mx.ita.vitalsense.R
import mx.ita.vitalsense.ui.theme.GradientEnd
import mx.ita.vitalsense.ui.theme.GradientStart

private val SplashEasing = Easing { it * it * (3f - 2f * it) } // smoothstep

@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    var visible by remember { mutableStateOf(false) }

    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 800, easing = SplashEasing),
        label = "logo_alpha",
    )

    // Fade in logo, wait, then navigate
    LaunchedEffect(Unit) {
        visible = true
        delay(1_500) // System splash already shows ~500ms, total ≈ 2s brand experience
        onTimeout()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.00f to GradientStart,
                        0.50f to GradientStart,
                        0.90f to GradientEnd,
                        1.00f to GradientEnd,
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        // The PNG exported from Figma already includes the eye + "VitalSense" wordmark
        Image(
            painter = painterResource(R.drawable.ic_logo_eye),
            contentDescription = "HealthSensor Logo",
            modifier = Modifier
                .size(width = 220.dp, height = 160.dp)
                .alpha(alpha),
        )
    }
}
