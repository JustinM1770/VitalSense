package mx.ita.vitalsense.ui.device

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.rounded.NightsStay
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import mx.ita.vitalsense.data.ble.BleConnectionState
import mx.ita.vitalsense.data.ble.BleVitals
import mx.ita.vitalsense.ui.theme.HeartRateRed
import mx.ita.vitalsense.ui.theme.Manrope
import mx.ita.vitalsense.ui.theme.NeomorphicBackground
import mx.ita.vitalsense.ui.theme.OnboardingBlue
import mx.ita.vitalsense.ui.theme.SpO2Green

private val CardBg    = Color(0xFFFFFFFF)
private val TextDark  = Color(0xFF221F1F)
private val TextGray  = Color(0xFF6B7280)
private val BorderCol = Color(0xFFE5E7EB)

// ─── Screen ───────────────────────────────────────────────────────────────────

@Composable
fun DeviceScanScreen(
    onBack: () -> Unit,
    vm: DeviceViewModel = viewModel(),
) {
    val connState     by vm.connectionState.collectAsStateWithLifecycle()
    val vitals        by vm.vitals.collectAsStateWithLifecycle()
    val isCodePaired  by vm.isCodePaired.collectAsStateWithLifecycle()
    val codeError     by vm.codeError.collectAsStateWithLifecycle()
    val deviceName    by vm.pairedDeviceName.collectAsStateWithLifecycle()

    // Limpiar al salir
    DisposableEffect(Unit) {
        onDispose { vm.stopScan() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NeomorphicBackground)
            .statusBarsPadding()
            .padding(top = 32.dp),
    ) {

        // ── Header ────────────────────────────────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .height(40.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onBack)
                    .align(Alignment.CenterStart),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TextDark)
            }
            Text(
                text = "Conectar Wearable",
                fontFamily = Manrope,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = TextDark,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        Spacer(Modifier.height(24.dp))

        // Si el reloj ya está emparejado por código, mostrar panel permanente
        if (isCodePaired) {
            PairedWatchPanel(
                deviceName = deviceName,
                vitals = vitals,
                onDisconnect = { vm.disconnectWatch() }
            )
        } else {
            // Solo mostrar la opción de vincular por código
            CodePanel(
                isConnecting = connState is BleConnectionState.Connecting,
                errorMessage = codeError,
                onConnectWithCode = { vm.connectWithCode(it) }
            )
        }
    }
}

// ─── Panel de Código ──────────────────────────────────────────────────────────

@Composable
private fun CodePanel(
    isConnecting: Boolean,
    errorMessage: String?,
    onConnectWithCode: (String) -> Unit
) {
    var code by remember { mutableStateOf("") }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(OnboardingBlue.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Bluetooth,
                contentDescription = null,
                tint = OnboardingBlue,
                modifier = Modifier.size(40.dp),
            )
        }
        
        Spacer(Modifier.height(16.dp))

        Text(
            text = "Para ver datos en tiempo real, abre VitalSense en tu reloj e ingresa el código de 8 caracteres que aparecerá allí.",
            fontFamily = Manrope,
            fontSize = 14.sp,
            color = TextGray,
            modifier = Modifier.padding(horizontal = 16.dp),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Si ya vinculaste por Bluetooth, recuerda que aún debes ingresar el código para sincronizar con esta app.",
            fontFamily = Manrope,
            fontSize = 12.sp,
            color = OnboardingBlue.copy(alpha = 0.7f),
            modifier = Modifier.padding(horizontal = 24.dp),
            textAlign = TextAlign.Center
        )

        // Mensaje de error
        if (errorMessage != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = errorMessage,
                fontFamily = Manrope,
                fontSize = 13.sp,
                color = Color(0xFFEF4444),
                modifier = Modifier.padding(horizontal = 16.dp),
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = code,
            onValueChange = { 
                if (it.length <= 8) {
                    code = it.uppercase() 
                }
            },
            label = { Text("Código de vinculación", fontFamily = Manrope) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(0.8f),
            shape = RoundedCornerShape(12.dp),
            isError = errorMessage != null
        )

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = { onConnectWithCode(code) },
            enabled = code.length == 8 && !isConnecting,
            modifier = Modifier
                .width(240.dp)
                .height(50.dp),
            shape = RoundedCornerShape(32.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = OnboardingBlue,
                disabledContainerColor = OnboardingBlue.copy(alpha = 0.5f)
            ),
        ) {
            if (isConnecting) {
                CircularProgressIndicator(
                    color = Color.White,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("Validando código…", fontFamily = Manrope, fontWeight = FontWeight.SemiBold, color = Color.White)
            } else {
                Text("Vincular", fontFamily = Manrope, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        }
    }
}

// ─── Panel Permanente del Reloj Emparejado ────────────────────────────────────────

@Composable
private fun PairedWatchPanel(
    deviceName: String,
    vitals: BleVitals,
    onDisconnect: () -> Unit
) {
    val hrValue = vitals.heartRate?.toString()
    val glucoseValue = vitals.glucose?.let { "%.0f".format(it) }
    val spo2Value = vitals.spo2?.toString()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Badge conectado permanente
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(32.dp))
                .background(SpO2Green.copy(alpha = 0.12f))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(SpO2Green),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "$deviceName vinculado",
                fontFamily = Manrope,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = SpO2Green,
            )
        }

        Spacer(Modifier.height(12.dp))

        Text(
            text = "Datos en tiempo real",
            fontFamily = Manrope,
            fontSize = 12.sp,
            color = TextGray,
        )

        Spacer(Modifier.height(28.dp))

        // Tarjetas de vitales
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BleVitalCard(
                modifier = Modifier.weight(1f),
                icon = { Icon(Icons.Outlined.FavoriteBorder, null,
                    tint = HeartRateRed, modifier = Modifier.size(22.dp)) },
                label = "Ritmo cardíaco",
                value = hrValue ?: "—",
                unit = "BPM",
                color = HeartRateRed,
            )
            BleVitalCard(
                modifier = Modifier.weight(1f),
                icon = { Icon(Icons.Outlined.WaterDrop, null,
                    tint = Color(0xFFFF9800), modifier = Modifier.size(22.dp)) },
                label = "Glucosa",
                value = glucoseValue ?: "—",
                unit = "mg/dL",
                color = Color(0xFFFF9800),
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BleVitalCard(
                modifier = Modifier.weight(1f),
                icon = { Icon(Icons.Outlined.FavoriteBorder, null,
                    tint = SpO2Green, modifier = Modifier.size(22.dp)) },
                label = "SpO₂",
                value = spo2Value ?: "—",
                unit = "%",
                color = SpO2Green,
            )
            // CARD DE SUEÑO
            val sleepValue = vitals.sleep?.score?.toString()
            BleVitalCard(
                modifier = Modifier.weight(1f),
                icon = { Icon(Icons.Rounded.NightsStay, null,
                    tint = Color(0xFF10B981), modifier = Modifier.size(22.dp)) },
                label = "Sueño",
                value = sleepValue ?: "—",
                unit = "%",
                color = Color(0xFF10B981),
            )
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "Los datos se actualizan automáticamente cada 5 segundos.",
            fontFamily = Manrope,
            fontSize = 12.sp,
            color = TextGray,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = onDisconnect,
            modifier = Modifier
                .width(240.dp)
                .height(50.dp),
            shape = RoundedCornerShape(32.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
        ) {
            Icon(Icons.Outlined.Close, null, tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Desvincular Reloj", fontFamily = Manrope, fontWeight = FontWeight.SemiBold, color = Color.White)
        }
    }
}

// ─── Tarjeta de vital ─────────────────────────────────────────────────────────

@Composable
private fun BleVitalCard(
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
    label: String,
    value: String,
    unit: String,
    color: Color,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(CardBg)
            .border(1.dp, BorderCol, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        icon()
        Spacer(Modifier.height(8.dp))
        Text(label, fontFamily = Manrope, fontSize = 11.sp, color = TextGray)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, fontFamily = Manrope, fontWeight = FontWeight.Bold,
                fontSize = 28.sp, color = TextDark, lineHeight = 28.sp)
            Spacer(Modifier.width(4.dp))
            Text(unit, fontSize = 12.sp, color = TextGray,
                modifier = Modifier.padding(bottom = 3.dp))
        }
    }
}
