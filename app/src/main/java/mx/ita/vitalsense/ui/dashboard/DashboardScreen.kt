package mx.ita.vitalsense.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BluetoothSearching
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import mx.ita.vitalsense.data.model.VitalsData
import mx.ita.vitalsense.ui.components.NeuCard
import mx.ita.vitalsense.ui.theme.AlertBackground
import mx.ita.vitalsense.ui.theme.AlertBorder
import mx.ita.vitalsense.ui.theme.AlertText
import mx.ita.vitalsense.ui.theme.GlucoseOrange
import mx.ita.vitalsense.ui.theme.GlucoseSoft
import mx.ita.vitalsense.ui.theme.HeartRateRed
import mx.ita.vitalsense.ui.theme.HeartRateSoft
import mx.ita.vitalsense.ui.theme.NeomorphicBackground
import mx.ita.vitalsense.ui.theme.PrimaryBlue
import mx.ita.vitalsense.ui.theme.SpO2Green
import mx.ita.vitalsense.ui.theme.SpO2Soft
import mx.ita.vitalsense.ui.theme.TextMuted
import mx.ita.vitalsense.ui.theme.TextPrimary
import mx.ita.vitalsense.ui.theme.TextSecondary

private const val GLUCOSE_ALERT_THRESHOLD = 150.0

@Composable
fun DashboardScreen(
    onConnectDevice: () -> Unit = {},
    vm: DashboardViewModel = viewModel(),
) {
    val uiState by vm.uiState.collectAsState()

    Scaffold(
        containerColor = NeomorphicBackground,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onConnectDevice,
                containerColor = PrimaryBlue,
                contentColor = Color.White,
            ) {
                Icon(
                    imageVector = Icons.Outlined.BluetoothSearching,
                    contentDescription = "Conectar wearable",
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (val state = uiState) {
                is DashboardUiState.Loading -> LoadingContent()
                is DashboardUiState.Error   -> ErrorContent(state.message)
                is DashboardUiState.Success -> DashboardContent(state.vitals)
            }
        }
    }
}

// ─── Loading ─────────────────────────────────────────────────────────────────

@Composable
private fun LoadingContent() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = PrimaryBlue, strokeWidth = 3.dp)
            Spacer(Modifier.height(16.dp))
            Text("Conectando con Firebase…", color = TextSecondary, fontSize = 14.sp)
        }
    }
}

// ─── Error ───────────────────────────────────────────────────────────────────

@Composable
private fun ErrorContent(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        NeuCard(modifier = Modifier.padding(32.dp)) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Rounded.Warning, contentDescription = null, tint = HeartRateRed, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(12.dp))
                Text("Sin conexión", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 18.sp)
                Spacer(Modifier.height(6.dp))
                Text(message, color = TextSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

// ─── Main dashboard ──────────────────────────────────────────────────────────

@Composable
private fun DashboardContent(vitals: VitalsData) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        Spacer(Modifier.height(52.dp))

        // ── Header ──────────────────────────────────────────────────────────
        DashboardHeader(vitals.patientName)

        Spacer(Modifier.height(28.dp))

        // ── Glucose alert banner (AI layer) ─────────────────────────────────
        AnimatedVisibility(
            visible = vitals.glucose > GLUCOSE_ALERT_THRESHOLD,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            GlucoseAlertBanner()
            Spacer(Modifier.height(20.dp))
        }

        Spacer(Modifier.height(4.dp))

        // ── Metric cards ────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            VitalCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.Favorite,
                label = "Ritmo Cardíaco",
                value = "${vitals.heartRate}",
                unit = "BPM",
                accentColor = HeartRateRed,
                softColor = HeartRateSoft,
                status = heartRateStatus(vitals.heartRate),
            )
            VitalCard(
                modifier = Modifier.weight(1f),
                icon = Icons.Rounded.WaterDrop,
                label = "Glucosa",
                value = "%.0f".format(vitals.glucose),
                unit = "mg/dL",
                accentColor = GlucoseOrange,
                softColor = GlucoseSoft,
                status = glucoseStatus(vitals.glucose),
            )
        }

        Spacer(Modifier.height(16.dp))

        // SpO2 full-width card
        SpO2Card(vitals.spo2)

        Spacer(Modifier.height(32.dp))

        // ── Footer ──────────────────────────────────────────────────────────
        Text(
            "Datos en tiempo real · Firebase",
            color = TextMuted,
            fontSize = 12.sp,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
    }
}

// ─── Header ──────────────────────────────────────────────────────────────────

@Composable
private fun DashboardHeader(patientName: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "HealthSensor",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
            )
            Text(
                text = patientName,
                fontSize = 14.sp,
                color = TextSecondary,
            )
        }
        // Live indicator
        LiveIndicator()
    }
}

@Composable
private fun LiveIndicator() {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val alpha by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "alpha",
    )
    NeuCard(cornerRadius = 12.dp) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(SpO2Green.copy(alpha = alpha)),
            )
            Spacer(Modifier.width(6.dp))
            Text("EN VIVO", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SpO2Green)
        }
    }
}

// ─── Glucose Alert Banner ────────────────────────────────────────────────────

@Composable
private fun GlucoseAlertBanner() {
    val pulse = rememberInfiniteTransition(label = "banner_pulse")
    val scale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = 1.015f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Reverse),
        label = "scale",
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(16.dp))
            .background(AlertBackground),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(AlertBorder.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Warning,
                    contentDescription = "Alerta",
                    tint = AlertBorder,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    "Pico de glucosa detectado",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = AlertBorder,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Sugiere hidratación y una caminata ligera",
                    fontSize = 12.sp,
                    color = AlertText,
                )
            }
        }
    }
}

// ─── Vital Card ──────────────────────────────────────────────────────────────

@Composable
private fun VitalCard(
    modifier: Modifier = Modifier,
    icon: ImageVector,
    label: String,
    value: String,
    unit: String,
    accentColor: Color,
    softColor: Color,
    status: String,
) {
    NeuCard(modifier = modifier) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Icon badge
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(softColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text(label, fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    value,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    lineHeight = 32.sp,
                )
                Spacer(Modifier.width(4.dp))
                Text(unit, fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(bottom = 4.dp))
            }
            Spacer(Modifier.height(10.dp))
            // Status pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(softColor)
                    .padding(horizontal = 10.dp, vertical = 3.dp),
            ) {
                Text(status, fontSize = 11.sp, color = accentColor, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ─── SpO2 full-width card ────────────────────────────────────────────────────

@Composable
private fun SpO2Card(spo2: Int) {
    NeuCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SpO2Soft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.MonitorHeart, contentDescription = null, tint = SpO2Green, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Saturación de Oxígeno", fontSize = 11.sp, color = TextSecondary, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$spo2", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = TextPrimary, lineHeight = 32.sp)
                    Spacer(Modifier.width(4.dp))
                    Text("%", fontSize = 14.sp, color = TextSecondary, modifier = Modifier.padding(bottom = 4.dp))
                }
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(SpO2Soft)
                    .padding(horizontal = 10.dp, vertical = 3.dp),
            ) {
                Text(spo2Status(spo2), fontSize = 11.sp, color = SpO2Green, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ─── Status helpers ──────────────────────────────────────────────────────────

private fun heartRateStatus(bpm: Int): String = when {
    bpm < 50 -> "⚠ Bradicardia"
    bpm > 100 -> "⚠ Taquicardia"
    else -> "✓ Normal"
}

private fun glucoseStatus(mgDl: Double): String = when {
    mgDl < 70 -> "⚠ Hipoglucemia"
    mgDl > 150 -> "⚠ Elevada"
    else -> "✓ Normal"
}

private fun spo2Status(pct: Int): String = when {
    pct < 90 -> "⚠ Crítico"
    pct < 95 -> "⚠ Bajo"
    else -> "✓ Normal"
}
