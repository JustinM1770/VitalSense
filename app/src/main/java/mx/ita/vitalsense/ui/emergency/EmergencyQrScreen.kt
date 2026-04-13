package mx.ita.vitalsense.ui.emergency

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

// ─── Paleta de emergencia ─────────────────────────────────────────────────────
private val EmergencyRed      = Color(0xFFD32F2F)
private val EmergencyDarkRed  = Color(0xFFB71C1C)
private val EmergencyLightRed = Color(0xFFEF9A9A)
private val OnEmergency       = Color(0xFFFFEBEE)
private val YellowWarning     = Color(0xFFFFD600)

@Composable
fun EmergencyQrScreen(
    vm: EmergencyQrViewModel = viewModel(),
    onResolve: () -> Unit = {},
) {
    val state by vm.state.collectAsState()
    val remainingSecs by vm.remainingSecs.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(EmergencyDarkRed, EmergencyRed))
            )
            .statusBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        when (val s = state) {
            is EmergencyQrState.Loading -> LoadingContent()

            is EmergencyQrState.Active -> ActiveContent(
                state         = s,
                remainingSecs = remainingSecs,
                onResolve     = {
                    vm.resolveEmergency()
                    onResolve()
                },
            )

            is EmergencyQrState.Expired -> ExpiredContent(
                anomalyType = s.anomalyType,
                onClose     = onResolve,
            )

            is EmergencyQrState.Error -> ErrorContent(
                state   = s,
                onRetry = { vm.retry(s.anomalyType, s.heartRate) },
                onClose = onResolve,
            )

            EmergencyQrState.Idle -> {
                // No debería llegar aquí si la navegación es correcta,
                // pero por seguridad cerramos la pantalla.
                onResolve()
            }
        }
    }
}

// ─── Contenido: Cargando ──────────────────────────────────────────────────────

@Composable
private fun LoadingContent() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        PulsingIcon()
        Text(
            text       = "Generando perfil de emergencia...",
            color      = OnEmergency,
            fontSize   = 16.sp,
            fontWeight = FontWeight.Medium,
        )
        CircularProgressIndicator(color = OnEmergency, strokeWidth = 3.dp)
    }
}

// ─── Contenido: QR activo ─────────────────────────────────────────────────────

@Composable
private fun ActiveContent(
    state: EmergencyQrState.Active,
    remainingSecs: Int,
    onResolve: () -> Unit,
) {
    Column(
        modifier                = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment     = Alignment.CenterHorizontally,
        verticalArrangement     = Arrangement.spacedBy(16.dp),
    ) {
        // — Encabezado —
        PulsingIcon()

        Text(
            text       = "EMERGENCIA DETECTADA",
            color      = OnEmergency,
            fontSize   = 20.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
        )

        Text(
            text     = state.anomalyType,
            color    = EmergencyLightRed,
            fontSize = 15.sp,
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Favorite,
                contentDescription = null,
                tint = EmergencyLightRed,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text     = "${state.heartRate} BPM",
                color    = EmergencyLightRed,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        // — Código QR —
        Card(
            shape     = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
            colors    = CardDefaults.cardColors(containerColor = Color.White),
        ) {
            Image(
                bitmap              = state.qrBitmap.asImageBitmap(),
                contentDescription  = "Código QR de emergencia",
                modifier            = Modifier
                    .size(240.dp)
                    .padding(16.dp),
            )
        }

        // — Contador de expiración —
        val mins = remainingSecs / 60
        val secs = remainingSecs % 60
        val isUrgent = remainingSecs < 300    // últimos 5 min → amarillo

        Text(
            text       = "Expira en %02d:%02d".format(mins, secs),
            color      = if (isUrgent) YellowWarning else OnEmergency,
            fontSize   = 14.sp,
            fontWeight = if (isUrgent) FontWeight.Bold else FontWeight.Normal,
        )

        // — PIN de acceso —
        Card(
            shape     = RoundedCornerShape(16.dp),
            colors    = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.15f)),
            elevation = CardDefaults.cardElevation(0.dp),
        ) {
            Column(
                modifier            = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text       = "PIN de acceso",
                    color      = EmergencyLightRed,
                    fontSize   = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 1.sp,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    state.pin.chunked(1).forEachIndexed { index, digit ->
                        Text(
                            text       = digit,
                            color      = Color.White,
                            fontSize   = 36.sp,
                            fontWeight = FontWeight.Black,
                        )
                        if (index < 3) {
                            Text(
                                text     = "–",
                                color    = EmergencyLightRed,
                                fontSize = 24.sp,
                            )
                        }
                    }
                }
                Text(
                    text      = "El paramédico necesita este PIN para ver el perfil médico",
                    color     = EmergencyLightRed,
                    fontSize  = 10.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }

        // — Instrucción —
        Text(
            text      = "Muestra este QR al paramédico.\nEscanéalo e ingresa el PIN para ver el perfil médico.",
            color     = EmergencyLightRed,
            fontSize  = 13.sp,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
        )

        Spacer(Modifier.height(8.dp))

        // — Botón resolver —
        OutlinedButton(
            onClick = onResolve,
            colors  = ButtonDefaults.outlinedButtonColors(contentColor = OnEmergency),
            border  = androidx.compose.foundation.BorderStroke(1.dp, OnEmergency.copy(alpha = 0.5f)),
        ) {
            Text("Emergencia resuelta — cerrar QR")
        }
    }
}

// ─── Contenido: Expirado ──────────────────────────────────────────────────────

@Composable
private fun ExpiredContent(anomalyType: String, onClose: () -> Unit) {
    Column(
        modifier            = Modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector        = Icons.Filled.Warning,
            contentDescription = null,
            tint               = YellowWarning,
            modifier           = Modifier.size(64.dp),
        )
        Text(
            text       = "QR Expirado",
            color      = OnEmergency,
            fontSize   = 22.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text      = "El acceso de emergencia para\n\"$anomalyType\" ha caducado.",
            color     = EmergencyLightRed,
            fontSize  = 14.sp,
            textAlign = TextAlign.Center,
        )
        Button(
            onClick = onClose,
            colors  = ButtonDefaults.buttonColors(containerColor = OnEmergency, contentColor = EmergencyRed),
        ) {
            Text("Cerrar", fontWeight = FontWeight.Bold)
        }
    }
}

// ─── Contenido: Error ─────────────────────────────────────────────────────────

@Composable
private fun ErrorContent(
    state: EmergencyQrState.Error,
    onRetry: () -> Unit,
    onClose: () -> Unit,
) {
    Column(
        modifier            = Modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector        = Icons.Filled.Warning,
            contentDescription = null,
            tint               = YellowWarning,
            modifier           = Modifier.size(64.dp),
        )
        Text(
            text       = "No se pudo generar el QR",
            color      = OnEmergency,
            fontSize   = 20.sp,
            fontWeight = FontWeight.Bold,
            textAlign  = TextAlign.Center,
        )
        Text(
            text      = state.message,
            color     = EmergencyLightRed,
            fontSize  = 13.sp,
            textAlign = TextAlign.Center,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = onClose,
                colors  = ButtonDefaults.outlinedButtonColors(contentColor = OnEmergency),
            ) {
                Text("Cerrar")
            }
            Button(
                onClick = onRetry,
                colors  = ButtonDefaults.buttonColors(containerColor = OnEmergency, contentColor = EmergencyRed),
            ) {
                Text("Reintentar", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ─── Ícono pulsante ───────────────────────────────────────────────────────────

@Composable
private fun PulsingIcon() {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue  = 1.25f,
        animationSpec = infiniteRepeatable(
            animation  = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scale",
    )
    Icon(
        imageVector        = Icons.Filled.Favorite,
        contentDescription = null,
        tint               = OnEmergency,
        modifier           = Modifier.size(56.dp).scale(scale),
    )
}
