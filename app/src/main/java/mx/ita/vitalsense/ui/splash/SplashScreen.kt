package mx.ita.vitalsense.ui.splash

import android.content.Context
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.delay
import mx.ita.vitalsense.R
import mx.ita.vitalsense.ui.theme.GradientEnd
import mx.ita.vitalsense.ui.theme.GradientStart

private val SplashEasing = Easing { it * it * (3f - 2f * it) } // smoothstep

@Composable
fun SplashScreen(
    onNavigateToOnboarding: () -> Unit,
    onNavigateToDashboard: () -> Unit,
    onNavigateToCuestionario: () -> Unit,
) {
    var visible by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 800, easing = SplashEasing),
        label = "logo_alpha",
    )

    // Check auth + biometric, then navigate
    LaunchedEffect(Unit) {
        visible = true
        delay(2000)
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            onNavigateToOnboarding()
            return@LaunchedEffect
        }
        val uid = currentUser.uid
        val profilePrefs = context.getSharedPreferences("vitalsense_profile", Context.MODE_PRIVATE)
        if (!profilePrefs.getBoolean("cuestionario_completed_$uid", false)) {
            // User logged in but hasn't completed profile setup
            onNavigateToCuestionario()
            return@LaunchedEffect
        }

        // Check if biometric is required
        val prefs = context.getSharedPreferences("vitalsense_watch_prefs", Context.MODE_PRIVATE)
        val requireBiometric = prefs.getBoolean("require_biometric", false)

        if (!requireBiometric) {
            onNavigateToDashboard()
            return@LaunchedEffect
        }

        // Check hardware capability
        val bm = BiometricManager.from(context)
        val canAuth = bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) ==
                BiometricManager.BIOMETRIC_SUCCESS
        if (!canAuth) {
            // No biometrics enrolled, skip prompt
            onNavigateToDashboard()
            return@LaunchedEffect
        }

        // Show biometric prompt
        val activity = context as? FragmentActivity ?: run {
            onNavigateToDashboard()
            return@LaunchedEffect
        }
        val executor = ContextCompat.getMainExecutor(context)
        BiometricPrompt(
            activity, executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    onNavigateToDashboard()
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // Failed / cancelled → stay on splash, user can retry or kill app
                    // Re-show the prompt after a short delay
                    activity.runOnUiThread {
                        val innerExecutor = ContextCompat.getMainExecutor(activity)
                        BiometricPrompt(
                            activity, innerExecutor,
                            object : BiometricPrompt.AuthenticationCallback() {
                                override fun onAuthenticationSucceeded(r: BiometricPrompt.AuthenticationResult) {
                                    onNavigateToDashboard()
                                }
                            }
                        ).authenticate(buildPromptInfo())
                    }
                }
            }
        ).authenticate(buildPromptInfo())
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
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.alpha(alpha)
        ) {
            Image(
                painter = painterResource(R.drawable.ic_logo_eye),
                contentDescription = "VitalSense Logo",
                modifier = Modifier.size(width = 180.dp, height = 120.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = buildAnnotatedString {
                    withStyle(style = SpanStyle(color = Color(0xFF0F172A), fontWeight = FontWeight.Bold)) {
                        append("Vital")
                    }
                    withStyle(style = SpanStyle(color = Color(0xFF1169FF), fontWeight = FontWeight.Bold)) {
                        append("Sense")
                    }
                },
                fontSize = 36.sp
            )
        }
    }
}

private fun buildPromptInfo(): BiometricPrompt.PromptInfo =
    BiometricPrompt.PromptInfo.Builder()
        .setTitle("VitalSense")
        .setSubtitle("Verifica tu identidad para continuar")
        .setNegativeButtonText("Cancelar")
        .build()
