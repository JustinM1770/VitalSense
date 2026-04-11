package mx.ita.vitalsense.ui.device

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.BluetoothConnected
import androidx.compose.material.icons.automirrored.outlined.BluetoothSearching
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.viewmodel.compose.viewModel
import mx.ita.vitalsense.R
import mx.ita.vitalsense.data.ble.BleConnectionState
import mx.ita.vitalsense.data.ble.BleDevice
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

// ─── Permisos BLE requeridos por versión ──────────────────────────────────────

private val BLE_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    arrayOf(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_CONNECT,
    )
} else {
    arrayOf(
        Manifest.permission.BLUETOOTH,
        Manifest.permission.BLUETOOTH_ADMIN,
        Manifest.permission.ACCESS_FINE_LOCATION,
    )
}

// ─── Screen ───────────────────────────────────────────────────────────────────

@Composable
fun DeviceScanScreen(
    onBack: () -> Unit,
    vm: DeviceViewModel = viewModel(),
) {
    val colorScheme = MaterialTheme.colorScheme
    val vitals        by vm.vitals.collectAsStateWithLifecycle()
    val isCodePaired  by vm.isCodePaired.collectAsStateWithLifecycle()
    val codeError     by vm.codeError.collectAsStateWithLifecycle()
    val connState     by vm.connectionState.collectAsStateWithLifecycle()
    var pairingCode   by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
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
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = colorScheme.onSurface)
            }
            Text(
                text = stringResource(R.string.device_scan_title),
                fontFamily = Manrope,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = colorScheme.onSurface,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        Spacer(Modifier.height(40.dp))

        if (isCodePaired || connState is BleConnectionState.Connected) {
            ConnectedPanel(
                deviceName = vm.pairedDeviceName.collectAsState().value,
                vitals = vitals,
                onDisconnect = { vm.disconnectWatch(); vm.disconnect() },
            )
        } else {
            CodeEntryPanel(
                code = pairingCode,
                onCodeChange = {
                    pairingCode = it
                        .filter { ch -> ch.isLetterOrDigit() }
                        .uppercase()
                        .take(8)
                },
                isLoading = connState is BleConnectionState.Connecting,
                errorMessage = codeError,
                onPair = { vm.connectWithCode(pairingCode) },
            )
        }
    }
}

// ─── Panel de código de emparejamiento ───────────────────────────────────────

@Composable
private fun CodeEntryPanel(
    code: String,
    onCodeChange: (String) -> Unit,
    isLoading: Boolean,
    errorMessage: String?,
    onPair: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
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
                .background(colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Watch,
                contentDescription = null,
                tint = colorScheme.primary,
                modifier = Modifier.size(40.dp),
            )
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = stringResource(R.string.device_scan_instruction),
            fontFamily = Manrope,
            color = TextGray,
            modifier = Modifier.padding(horizontal = 12.dp),
        )

        Spacer(Modifier.height(20.dp))

        OutlinedTextField(
            value = code,
            onValueChange = onCodeChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.device_scan_code_label), fontFamily = Manrope) },
            placeholder = { Text(stringResource(R.string.device_scan_code_placeholder), fontFamily = Manrope, color = TextGray) },
            singleLine = true,
            enabled = !isLoading,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = OnboardingBlue,
                unfocusedBorderColor = BorderCol,
                focusedLabelColor = OnboardingBlue,
            ),
        )

        if (errorMessage != null) {
            Spacer(Modifier.height(8.dp))
            Text(errorMessage, color = Color(0xFFEF4444), fontFamily = Manrope, fontSize = 12.sp)
        }

        Spacer(Modifier.height(20.dp))

        Button(
            onClick = onPair,
            enabled = code.length == 8 && !isLoading,
            modifier = Modifier.width(240.dp).height(50.dp),
            shape = RoundedCornerShape(32.dp),
            colors = ButtonDefaults.buttonColors(containerColor = OnboardingBlue),
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.device_scan_pairing_in_progress), fontFamily = Manrope, fontWeight = FontWeight.SemiBold, color = Color.White)
            } else {
                Text(stringResource(R.string.device_scan_pair_action), fontFamily = Manrope, fontWeight = FontWeight.SemiBold, color = Color.White)
            }
        }
    }
}

// ─── Panel de escaneo ─────────────────────────────────────────────────────────

@Composable
private fun ScanPanel(
    devices: List<BleDevice>,
    isScanning: Boolean,
    isConnecting: Boolean,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnect: (BleDevice) -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Icono central animado
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(OnboardingBlue.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (isScanning) Icons.AutoMirrored.Outlined.BluetoothSearching
                              else Icons.Outlined.Bluetooth,
                contentDescription = null,
                tint = OnboardingBlue,
                modifier = Modifier.size(40.dp),
            )
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = if (isScanning) "Buscando dispositivos…"
                   else "Busca tu sensor VitalSense o wearable compatible",
            fontFamily = Manrope,
            fontSize = 14.sp,
            color = colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Spacer(Modifier.height(20.dp))

        // Botón Escanear / Detener
        Button(
            onClick = if (isScanning) onStopScan else onStartScan,
            modifier = Modifier
                .width(240.dp)
                .height(50.dp),
            shape = RoundedCornerShape(32.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isScanning) Color(0xFFEF4444) else colorScheme.primary,
            ),
        ) {
            if (isScanning) {
                CircularProgressIndicator(
                    color = colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("Detener", fontFamily = Manrope, fontWeight = FontWeight.SemiBold,
                    color = colorScheme.onPrimary)
            } else {
                Text("Buscar dispositivos", fontFamily = Manrope,
                    fontWeight = FontWeight.SemiBold, color = colorScheme.onPrimary)
            }
        }

        if (isConnecting) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    color = colorScheme.primary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("Conectando…", fontFamily = Manrope, fontSize = 13.sp, color = colorScheme.onSurfaceVariant)
            }
        }

        Spacer(Modifier.height(24.dp))

        // Lista de dispositivos encontrados
        if (devices.isNotEmpty()) {
            Text(
                text = "Dispositivos encontrados",
                fontFamily = Manrope,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                color = colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(devices, key = { it.address }) { device ->
                    DeviceRow(device = device, onConnect = { onConnect(device) })
                }
            }
        }
    }
}

@Composable
private fun DeviceRow(device: BleDevice, onConnect: () -> Unit) {
    val colorScheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colorScheme.surface)
            .border(1.dp, colorScheme.outline, RoundedCornerShape(12.dp))
            .clickable(onClick = onConnect)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colorScheme.primary.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.BluetoothConnected, null,
                tint = colorScheme.primary, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(device.name, fontFamily = Manrope, fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp, color = colorScheme.onSurface)
            Text(device.address, fontFamily = Manrope, fontSize = 11.sp, color = colorScheme.onSurfaceVariant)
        }
        // RSSI como barras de señal (texto)
        val signal = when {
            device.rssi > -60 -> "●●●"
            device.rssi > -75 -> "●●○"
            else              -> "●○○"
        }
        Text(signal, fontSize = 12.sp, color = colorScheme.primary.copy(alpha = 0.7f))
    }
}

// ─── Panel conectado ──────────────────────────────────────────────────────────

@Composable
private fun ConnectedPanel(
    deviceName: String,
    vitals: BleVitals,
    onDisconnect: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Badge conectado
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(32.dp))
                .background(colorScheme.secondary.copy(alpha = 0.14f))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(colorScheme.secondary),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Conectado a $deviceName",
                fontFamily = Manrope,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                    color = colorScheme.secondary,
            )
        }

        Spacer(Modifier.height(28.dp))

        // Tarjetas de vitales en tiempo real
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            BleVitalCard(
                modifier = Modifier.weight(1f),
                icon = { Icon(Icons.Outlined.FavoriteBorder, null,
                    tint = colorScheme.primary, modifier = Modifier.size(22.dp)) },
                label = "Ritmo cardíaco",
                value = vitals.heartRate?.toString() ?: "—",
                unit = "BPM",
                color = colorScheme.primary,
            )
            BleVitalCard(
                modifier = Modifier.weight(1f),
                icon = { Icon(Icons.Outlined.WaterDrop, null,
                    tint = colorScheme.tertiary, modifier = Modifier.size(22.dp)) },
                label = "Glucosa",
                value = vitals.glucose?.let { "%.0f".format(it) } ?: "—",
                unit = "mg/dL",
                color = colorScheme.tertiary,
            )
        }

        Spacer(Modifier.height(12.dp))

        BleVitalCard(
            modifier = Modifier.fillMaxWidth(),
            icon = { Icon(Icons.Outlined.FavoriteBorder, null,
                tint = colorScheme.secondary, modifier = Modifier.size(22.dp)) },
            label = "SpO₂",
            value = vitals.spo2?.toString() ?: "—",
            unit = "%",
            color = colorScheme.secondary,
        )

        Spacer(Modifier.height(28.dp))

        // Botón desconectar
        Button(
            onClick = onDisconnect,
            modifier = Modifier
                .width(240.dp)
                .height(50.dp),
            shape = RoundedCornerShape(32.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
        ) {
            Icon(Icons.Outlined.Close, null, tint = colorScheme.onError,
                modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Desconectar", fontFamily = Manrope, fontWeight = FontWeight.SemiBold,
                color = colorScheme.onError)
        }
    }
}

@Composable
private fun BleVitalCard(
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
    label: String,
    value: String,
    unit: String,
    color: Color,
) {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(colorScheme.surface)
            .border(1.dp, colorScheme.outline, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        icon()
        Spacer(Modifier.height(8.dp))
        Text(label, fontFamily = Manrope, fontSize = 11.sp, color = colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, fontFamily = Manrope, fontWeight = FontWeight.Bold,
                fontSize = 28.sp, color = colorScheme.onSurface, lineHeight = 28.sp)
            Spacer(Modifier.width(4.dp))
            Text(unit, fontSize = 12.sp, color = colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 3.dp))
        }
    }
}
