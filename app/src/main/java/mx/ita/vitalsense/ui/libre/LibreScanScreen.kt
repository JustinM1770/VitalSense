package mx.ita.vitalsense.ui.libre

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import mx.ita.vitalsense.R
import mx.ita.vitalsense.data.ble.FreestyleLibreReader
import mx.ita.vitalsense.ui.theme.DashBlue
import mx.ita.vitalsense.ui.theme.Manrope
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LibreScanScreen(
    onBack: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    val db = remember { FirebaseDatabase.getInstance() }
    val prefs = remember { context.getSharedPreferences("vitalsense_profile", android.content.Context.MODE_PRIVATE) }
    val reader = remember { FreestyleLibreReader(context) }
    val lifecycleOwner = LocalLifecycleOwner.current

    var nfcSupported by remember { mutableStateOf(reader.isNfcSupported()) }
    var nfcEnabled by remember { mutableStateOf(reader.isNfcEnabled()) }
    var glucose by remember { mutableStateOf(0.0) }
    var timestamp by remember { mutableStateOf(0L) }
    var source by remember { mutableStateOf("") }
    var wearableBattery by remember { mutableStateOf<Int?>(null) }

    val localGlucose = if (uid.isNotBlank()) prefs.getFloat("libre_last_glucose_$uid", 0f).toDouble() else 0.0
    val localTime = if (uid.isNotBlank()) prefs.getLong("libre_last_time_$uid", 0L) else 0L
    val localSource = if (uid.isNotBlank()) prefs.getString("libre_last_source_$uid", "") ?: "" else ""
    val confidence = if (uid.isNotBlank()) {
        prefs.getString("libre_last_confidence_$uid", "Compatibilidad no evaluada") ?: "Compatibilidad no evaluada"
    } else {
        "Compatibilidad no evaluada"
    }

    DisposableEffect(uid) {
        if (uid.isBlank()) return@DisposableEffect onDispose { }

        val patientRef = db.getReference("patients/$uid")
        val watchRef = db.getReference("patients/$uid/watch")

        val patientListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                glucose = snapshot.child("glucose").getValue(Double::class.java) ?: 0.0
                timestamp = snapshot.child("timestamp").getValue(Long::class.java) ?: 0L
                source = snapshot.child("glucoseSource").getValue(String::class.java).orEmpty()
                nfcSupported = reader.isNfcSupported()
                nfcEnabled = reader.isNfcEnabled()
            }

            override fun onCancelled(error: DatabaseError) {}
        }

        val watchListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                wearableBattery = snapshot.child("batteryPercent").getValue(Int::class.java)
                    ?: snapshot.child("battery").getValue(Int::class.java)
            }

            override fun onCancelled(error: DatabaseError) {}
        }

        patientRef.addValueEventListener(patientListener)
        watchRef.addValueEventListener(watchListener)

        onDispose {
            patientRef.removeEventListener(patientListener)
            watchRef.removeEventListener(watchListener)
        }
    }

    LaunchedEffect(Unit) {
        nfcSupported = reader.isNfcSupported()
        nfcEnabled = reader.isNfcEnabled()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                nfcSupported = reader.isNfcSupported()
                nfcEnabled = reader.isNfcEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val displayGlucose = when {
        glucose > 0.0 -> glucose
        localGlucose > 0.0 -> localGlucose
        else -> 0.0
    }
    val displayTime = when {
        timestamp > 0L -> timestamp
        else -> localTime
    }
    val displaySource = when {
        source.isNotBlank() -> source
        localSource.isNotBlank() -> localSource
        else -> stringResource(R.string.libre_scan_no_sources)
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(colorScheme.primary.copy(alpha = 0.12f), CircleShape)
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = colorScheme.onSurface,
                    )
                }
                Spacer(modifier = Modifier.size(10.dp))
                Text(
                    text = stringResource(R.string.libre_scan_title),
                    fontFamily = Manrope,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = colorScheme.onSurface,
                )
            }

            StatusCard(
                title = stringResource(R.string.libre_scan_nfc_title),
                value = when {
                    !nfcSupported -> stringResource(R.string.libre_scan_nfc_unsupported)
                    nfcEnabled -> stringResource(R.string.libre_scan_nfc_on)
                    else -> stringResource(R.string.libre_scan_nfc_off)
                },
                subtitle = when {
                    !nfcSupported -> stringResource(R.string.libre_scan_nfc_hardware_missing)
                    nfcEnabled -> stringResource(R.string.libre_scan_ready)
                    else -> stringResource(R.string.libre_scan_enable_settings)
                },
                highlight = when {
                    !nfcSupported -> Color(0xFFD32F2F)
                    nfcEnabled -> Color(0xFF10B981)
                    else -> Color(0xFFF59E0B)
                },
                icon = Icons.Rounded.Info,
            )

            if (nfcSupported && !nfcEnabled) {
                Button(
                    onClick = { reader.openNfcSettings() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = colorScheme.primary),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(stringResource(R.string.libre_scan_open_nfc_settings), color = colorScheme.onPrimary, fontFamily = Manrope)
                }
            }

            StatusCard(
                title = stringResource(R.string.libre_scan_latest_glucose),
                value = if (displayGlucose > 0.0) "${"%.0f".format(displayGlucose)} mg/dL" else stringResource(R.string.libre_scan_no_reading),
                subtitle = if (displayTime > 0L) {
                    stringResource(
                        R.string.libre_scan_time_label,
                        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(displayTime)),
                    )
                } else {
                    stringResource(R.string.libre_scan_place_sensor)
                },
                highlight = colorScheme.primary,
                icon = Icons.Rounded.MonitorHeart,
            )

            StatusCard(
                title = stringResource(R.string.libre_scan_data_source),
                value = displaySource,
                subtitle = confidence,
                highlight = Color(0xFF6B7280),
                icon = Icons.Rounded.Info,
            )

            StatusCard(
                title = stringResource(R.string.libre_scan_wearable_battery),
                value = wearableBattery?.let { "$it%" } ?: stringResource(R.string.libre_scan_unavailable),
                subtitle = stringResource(R.string.libre_scan_wearable_hint),
                highlight = Color(0xFF0EA5E9),
                icon = Icons.Rounded.BatteryChargingFull,
            )

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.libre_scan_how_to),
                        fontFamily = Manrope,
                        fontWeight = FontWeight.Bold,
                        color = colorScheme.onSurface,
                    )
                    Text("1. Activa NFC en tu telefono.", fontFamily = Manrope, fontSize = 13.sp, color = colorScheme.onSurfaceVariant)
                    Text("2. Abre esta pantalla.", fontFamily = Manrope, fontSize = 13.sp, color = colorScheme.onSurfaceVariant)
                    Text("3. Acerca el sensor Libre al telefono por 1-2 segundos.", fontFamily = Manrope, fontSize = 13.sp, color = colorScheme.onSurfaceVariant)
                    Text("4. La lectura se guarda automaticamente en la app.", fontFamily = Manrope, fontSize = 13.sp, color = colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun StatusCard(
    title: String,
    value: String,
    subtitle: String? = null,
    highlight: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    val colorScheme = MaterialTheme.colorScheme
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(38.dp).background(highlight.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = highlight)
            }
            Spacer(modifier = Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontFamily = Manrope, fontSize = 12.sp, color = colorScheme.onSurfaceVariant)
                Text(value, fontFamily = Manrope, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = colorScheme.onSurface)
                if (!subtitle.isNullOrBlank()) {
                    Text(subtitle, fontFamily = Manrope, fontSize = 12.sp, color = colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
