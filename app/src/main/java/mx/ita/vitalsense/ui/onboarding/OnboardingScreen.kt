package mx.ita.vitalsense.ui.onboarding

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import mx.ita.vitalsense.R
import mx.ita.vitalsense.ui.theme.GradientEnd
import mx.ita.vitalsense.ui.theme.GradientStart
import mx.ita.vitalsense.ui.theme.OnboardingBlue
import mx.ita.vitalsense.ui.theme.OnboardingButtonText
import mx.ita.vitalsense.ui.theme.OnboardingDotInactive

// ─── Page data ────────────────────────────────────────────────────────────────

private data class OnboardingPage(
    val title: String,
    val body: String,
    @DrawableRes val illustration: Int?,
)

private val pages = listOf(
    OnboardingPage(
        title = "Bienvenido a VitalSense",
        body = "Conectamos tecnología y cuidado medico para proteger a quienes mas quieres. No solo medimos, predecimos para actuar a tiempo",
        illustration = R.drawable.illus_stethoscope,
    ),
    OnboardingPage(
        title = "Actua con ventaja.",
        body = "Ante una crisis, la app te guia. Gestiona alertas automaticas a servicios de emergencia y comparte el historial medico mediante un QR dinamico para atencion medica precisa",
        illustration = R.drawable.illus_qr,
    ),
    OnboardingPage(
        title = "Prevencion basada en datos.",
        body = "No necesitas estar pegado a la pantalla. Nuestra IA analiza el rito cardiaco y niveles de glucosa por ti, notificandote de inmediato solo si detecta un patron de riesgo.",
        illustration = R.drawable.corazon,
    ),
)

// ─── Screen ───────────────────────────────────────────────────────────────────

@Composable
fun OnboardingScreen(
    onSkip: () -> Unit,        // "Omitir" → va directo a Register/Login
    onGetStarted: () -> Unit,  // "Comenzar" (última página) → flujo registro
) {
    val onFinish = onGetStarted // alias interno para no cambiar toda la lógica
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    val background = Brush.verticalGradient(
        colorStops = arrayOf(
            0.00f to GradientStart,
            0.50f to GradientStart,
            0.90f to GradientEnd,
            1.00f to GradientEnd,
        ),
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background),
    ) {
        // ── Pages ────────────────────────────────────────────────────────────
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { index ->
            PageContent(page = pages[index])
        }

        // ── Skip button ──────────────────────────────────────────────────────
        // Moved here to be on top of the pager
        TextButton(
            onClick = onSkip,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 52.dp, end = 16.dp),
        ) {
            Text(
                text = "Omitir",
                style = MaterialTheme.typography.labelSmall,
                color = Color.Black,
            )
        }

        // ── Dots + Button ────────────────────────────────────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 52.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            DotsIndicator(
                total = pages.size,
                current = pagerState.currentPage,
            )

            Spacer(Modifier.height(28.dp))

            // "Siguiente" button — 325×59dp, radius 32, #126AFF
            Button(
                onClick = {
                    if (pagerState.currentPage < pages.lastIndex) {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    } else {
                        onFinish()
                    }
                },
                modifier = Modifier
                    .width(325.dp)
                    .height(59.dp),
                shape = RoundedCornerShape(32.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = OnboardingBlue,
                    contentColor = OnboardingButtonText,
                ),
            ) {
                Text(
                    text = if (pagerState.currentPage < pages.lastIndex) "Siguiente" else "Comenzar",
                    style = MaterialTheme.typography.labelLarge,
                    color = OnboardingButtonText,
                )
            }
        }
    }
}

// ─── Single page ──────────────────────────────────────────────────────────────

@Composable
private fun PageContent(page: OnboardingPage) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Illustration area — top 55% of screen
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.55f),
            contentAlignment = Alignment.Center,
        ) {
            if (page.illustration != null) {
                Image(
                    painter = painterResource(page.illustration),
                    contentDescription = null,
                    modifier = Modifier.size(width = 249.dp, height = 213.dp),
                    contentScale = ContentScale.Fit,
                )
            }
        }

        // Text area — bottom 45%
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.45f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = page.title,
                style = MaterialTheme.typography.titleLarge,
                color = Color.Black,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = page.body,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.Black.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ─── Dots indicator ───────────────────────────────────────────────────────────

@Composable
private fun DotsIndicator(total: Int, current: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(total) { index ->
            val isActive = index == current
            // Active pill: 16×8 | Inactive circle: 8×8
            val width: Dp by animateDpAsState(
                targetValue = if (isActive) 16.dp else 8.dp,
                animationSpec = tween(300),
                label = "dot_width_$index",
            )
            Box(
                modifier = Modifier
                    .width(width)
                    .height(8.dp)
                    .clip(if (isActive) RoundedCornerShape(4.dp) else CircleShape)
                    .background(if (isActive) OnboardingBlue else OnboardingDotInactive),
            )
        }
    }
}
