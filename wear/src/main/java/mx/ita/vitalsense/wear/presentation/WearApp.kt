package mx.ita.vitalsense.wear.presentation

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Text
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import mx.ita.vitalsense.wear.VitalSignsService
import mx.ita.vitalsense.wear.ui.theme.VitalSenseWearTheme
import mx.ita.vitalsense.wear.ui.theme.*
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

private const val TAG = "WearApp"
private const val DB_URL = "https://vitalsenseai-1cb9f-default-rtdb.firebaseio.com"
private const val PREFS_NAME = "vitalsense_wear_prefs"
private const val KEY_PAIRED = "is_paired"
private const val KEY_PAIRING_CODE = "pairing_code"
private const val KEY_USER_ID = "user_id"
private val HEALTH_SENSOR_PERMISSIONS = listOf(
    "android.permission.health.READ_HEART_RATE",
    "android.permission.health.READ_OXYGEN_SATURATION",
    "android.permission.health.READ_RESPIRATORY_RATE",
    "android.permission.health.READ_BODY_TEMPERATURE",
    "android.permission.health.READ_BLOOD_PRESSURE",
    "android.permission.health.READ_BLOOD_GLUCOSE",
)

@Composable
fun WearApp(
    isAmbient: Boolean = false,
    openSosFromNotification: Boolean = false,
    initialSosId: String? = null,
    initialSosUserId: String? = null,
    onSosNotificationConsumed: () -> Unit = {},
) {
    val context = LocalContext.current
    val database = remember { FirebaseDatabase.getInstance(DB_URL) }
    val auth = remember { FirebaseAuth.getInstance() }
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    // --- 1. Permisos ---
    // criticalPermissions: gates the app — the dialog CAN grant these.
    // ACCESS_BACKGROUND_LOCATION and health permissions are intentionally excluded
    // because Android 11+ only grants them via System Settings, not the dialog.
    val criticalPermissions = remember(context) {
        buildList {
            add(Manifest.permission.BODY_SENSORS)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACTIVITY_RECOGNITION)
        }.toTypedArray()
    }

    // allPermissionsToRequest: the full set we request on first launch, including
    // optional background permissions. Not granting these is accepted gracefully.
    val allPermissionsToRequest = remember(context) {
        buildList {
            addAll(criticalPermissions)

            // Background location — must be enabled via Settings ("Allow all the time").
            // We request it so the system can prompt, but we do NOT block the app on it.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
                add(Manifest.permission.BODY_SENSORS_BACKGROUND)
            }

            // Health read permissions — only if they exist on this device
            HEALTH_SENSOR_PERMISSIONS.forEach { permission ->
                val existsOnDevice = runCatching {
                    context.packageManager.getPermissionInfo(permission, 0)
                }.isSuccess
                if (existsOnDevice) add(permission)
            }
        }.distinct().toTypedArray()
    }

    // Only check critical permissions for the gate — background perms can't be
    // granted via dialog so we must not block the app on them.
    fun allGranted() = criticalPermissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    var hasPermission by remember { mutableStateOf(allGranted()) }

    fun hasLocationPermission(): Boolean {
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        return fineGranted || coarseGranted
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val denied = result.filterValues { granted -> !granted }.keys
        if (denied.isNotEmpty()) {
            Log.w(TAG, "Permisos denegados: ${denied.joinToString()}")
        }
        hasPermission = allGranted()
        // If critical perms still denied after dialog, send user to Settings
        if (!hasPermission) {
            val settingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(settingsIntent)
        }
    }

    // Auto-launch the full permission request on first open.
    LaunchedEffect(Unit) {
        if (!allGranted()) {
            val missing = allPermissionsToRequest.filter {
                ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
            }
            if (missing.isNotEmpty()) {
                launcher.launch(missing.toTypedArray())
            }
        }
    }

    // --- 2. Estado ---
    var isPaired by remember { mutableStateOf(prefs.getBoolean(KEY_PAIRED, false)) }
    var pairingCode by remember { mutableStateOf(prefs.getString(KEY_PAIRING_CODE, "") ?: "") }
    var isAuthenticated by remember { mutableStateOf(auth.currentUser != null) }
    var showSuccessScreen by remember { mutableStateOf(false) }
    var forcedSosId by remember { mutableStateOf<String?>(null) }
    var forcedSosUserId by remember { mutableStateOf<String?>(null) }

    // Estado de emergencia activa (viene de patients/{userId}/activeEmergency en Firebase)
    var activeEmergencyTokenId  by remember { mutableStateOf<String?>(null) }
    var activeEmergencyPin      by remember { mutableStateOf("") }
    var activeEmergencyType     by remember { mutableStateOf("") }
    var activeEmergencyExpires  by remember { mutableStateOf(0L) }
    
    // Auth Anónima
    LaunchedEffect(Unit) {
        if (auth.currentUser != null) {
            isAuthenticated = true
        } else {
            auth.signInAnonymously().addOnSuccessListener { isAuthenticated = true }
        }
    }

    // Código Aleatorio
    if (!isPaired) {
        val currentTime = System.currentTimeMillis()
        val lastCodeTime = prefs.getLong("last_code_time", 0L)
        if (pairingCode.isEmpty() || currentTime - lastCodeTime > 5 * 60 * 1000L) {
            val oldCode = pairingCode
            val chars = ('A'..'Z') + ('0'..'9')
            pairingCode = (1..8).map { chars.random() }.joinToString("")
            prefs.edit()
                .putString("pairing_code", pairingCode)
                .putLong("last_code_time", currentTime)
                .apply()
            
            if (oldCode.isNotEmpty() && isAuthenticated) {
                database.getReference("patients/pairing_codes").child(oldCode).removeValue()
            }
        }
    }

    LaunchedEffect(isPaired, isAuthenticated) {
        if (!isPaired) {
            while (true) {
                kotlinx.coroutines.delay(60_000L) // Verificar cada minuto
                val currentTime = System.currentTimeMillis()
                val lastCodeTime = prefs.getLong("last_code_time", 0L)
                if (currentTime - lastCodeTime > 5 * 60 * 1000L) {
                    val oldCode = pairingCode
                    val chars = ('A'..'Z') + ('0'..'9')
                    pairingCode = (1..8).map { chars.random() }.joinToString("")
                    prefs.edit()
                        .putString("pairing_code", pairingCode)
                        .putLong("last_code_time", currentTime)
                        .apply()
                    if (oldCode.isNotEmpty() && isAuthenticated) {
                        database.getReference("patients/pairing_codes").child(oldCode).removeValue()
                    }
                }
            }
        }
    }

    // Escuchar emergencias activas desde Firebase para mostrar QR+PIN en el reloj
    LaunchedEffect(isPaired, isAuthenticated) {
        val userId = prefs.getString(KEY_USER_ID, null)
        if (!isPaired || userId.isNullOrEmpty()) return@LaunchedEffect

        val ref = database.getReference("patients/$userId/activeEmergency")
        ref.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    activeEmergencyTokenId = null
                    return
                }
                val expiresAt = snapshot.child("expiresAt").getValue(Long::class.java) ?: 0L
                if (System.currentTimeMillis() > expiresAt) {
                    activeEmergencyTokenId = null
                    return
                }
                activeEmergencyTokenId = snapshot.child("tokenId").getValue(String::class.java)
                activeEmergencyPin     = snapshot.child("pin").getValue(String::class.java) ?: ""
                activeEmergencyType    = snapshot.child("anomalyType").getValue(String::class.java) ?: "Emergencia"
                activeEmergencyExpires = expiresAt
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    // Lógica de Servicio
    LaunchedEffect(isPaired, hasPermission) {
        if (isPaired && hasPermission) {
            val intent = Intent(context, VitalSignsService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    // --- 3. Heart Rate Binding ---
    var vitalSignsService by remember { mutableStateOf<VitalSignsService?>(null) }
    val currentHeartRate by (vitalSignsService?.currentHeartRate?.collectAsState() ?: remember { mutableStateOf(0.0) })
    val currentSpO2 by (vitalSignsService?.currentSpO2?.collectAsState() ?: remember { mutableStateOf(0) })
    val isSpO2Supported by (vitalSignsService?.isSpO2Supported?.collectAsState() ?: remember { mutableStateOf(true) })
    val activeSosId by (vitalSignsService?.activeSosId?.collectAsState() ?: remember { mutableStateOf(null) })

    DisposableEffect(context) {
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val binder = service as VitalSignsService.LocalBinder
                vitalSignsService = binder.getService()
            }
            override fun onServiceDisconnected(name: ComponentName?) {
                vitalSignsService = null
            }
        }
        val serviceIntent = Intent(context, VitalSignsService::class.java)
        context.bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE)
        onDispose {
            context.unbindService(connection)
        }
    }

    // --- 4. Alerta SOS Local ---
    var sosSent by remember { mutableStateOf(false) }
    var pendingSosAfterLocationGrant by remember { mutableStateOf(false) }

    val sosLocationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        val granted = hasLocationPermission()
        if (granted && pendingSosAfterLocationGrant) {
            vitalSignsService?.triggerSosAlert()
            sosSent = true
        }
        pendingSosAfterLocationGrant = false
    }

    LaunchedEffect(sosSent) {
        if (sosSent) {
            delay(5000) // 5 Segundos
            sosSent = false
        }
    }

    LaunchedEffect(openSosFromNotification, initialSosId, initialSosUserId) {
        if (openSosFromNotification) {
            forcedSosId = initialSosId
            forcedSosUserId = initialSosUserId
            onSosNotificationConsumed()
        }
    }

    val triggerSosUI = {
        if (!sosSent) {
            if (hasLocationPermission()) {
                sosSent = true
                vitalSignsService?.triggerSosAlert()
            } else {
                pendingSosAfterLocationGrant = true
                sosLocationLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    ),
                )
            }
        }
    }

    // --- 5. Unpairing logic helper ---
    val unpairWatch = {
        prefs.edit()
            .putBoolean(KEY_PAIRED, false)
            .remove(KEY_USER_ID)
            .remove(KEY_PAIRING_CODE)
            .apply()
        isPaired = false
        pairingCode = ""
        val intent = Intent(context, VitalSignsService::class.java)
        context.stopService(intent)
    }

    // UI
    VitalSenseWearTheme {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (!isPaired) {
                CodeScreen(pairingCode)
            } else if (showSuccessScreen) {
                SuccessScreen()
            } else if (!hasPermission) {
                PermissionScreen {
                    val missing = criticalPermissions.filter {
                        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                    }

                    if (missing.isEmpty()) {
                        hasPermission = true
                    } else {
                        launcher.launch(missing.toTypedArray())
                    }
                }
            } else if (activeEmergencyTokenId != null) {
                // Emergencia crítica detectada por la IA — mostrar QR + PIN en el reloj
                EmergencyQrWearScreen(
                    tokenId    = activeEmergencyTokenId!!,
                    pin        = activeEmergencyPin,
                    anomalyType = activeEmergencyType,
                    expiresAt  = activeEmergencyExpires,
                )
            } else if (activeSosId != null || forcedSosId != null) {
                SosQrScreen(
                    sosId = activeSosId ?: forcedSosId.orEmpty(),
                    userId = forcedSosUserId ?: (prefs.getString(KEY_USER_ID, "global") ?: "global"),
                    onDismiss = {
                        forcedSosId = null
                        forcedSosUserId = null
                        vitalSignsService?.clearSos()
                    }
                )
            } else {
                MonitoringScreen(
                    isAmbient = isAmbient,
                    heartRate = currentHeartRate.toInt(),
                    spo2 = currentSpO2,
                    isSpO2Supported = isSpO2Supported,
                    sosSent = sosSent,
                    onSosTriggered = { triggerSosUI() }
                )
            }
        }
    }

    // Publicar código a Firebase apenas se autentica
    LaunchedEffect(isAuthenticated) {
        if (isAuthenticated && !isPaired && pairingCode.isNotEmpty()) {
            val ref = database.getReference("patients/pairing_codes").child(pairingCode)
            val authUid = auth.currentUser?.uid.orEmpty()
            ref.setValue(mapOf(
                "code" to pairingCode,
                "active" to true,
                "paired" to false,
                "deviceName" to Build.MODEL,
                "timestamp" to System.currentTimeMillis(),
                "userId" to authUid,
            )).addOnSuccessListener {
                Log.d(TAG, "Codigo enviado a Firebase: $pairingCode")
            }.addOnFailureListener { e ->
                Log.e(TAG, "Error publicando codigo inicial en Firebase", e)
            }
        }
    }

    // Publicar cuando el código cambia
    LaunchedEffect(pairingCode) {
        if (isAuthenticated && !isPaired && pairingCode.isNotEmpty()) {
            val ref = database.getReference("patients/pairing_codes").child(pairingCode)
            val authUid = auth.currentUser?.uid.orEmpty()
            ref.setValue(mapOf(
                "code" to pairingCode,
                "active" to true,
                "paired" to false,
                "deviceName" to Build.MODEL,
                "timestamp" to System.currentTimeMillis(),
                "userId" to authUid,
            )).addOnSuccessListener {
                Log.d(TAG, "Codigo actualizado en Firebase: $pairingCode")
            }.addOnFailureListener { e ->
                Log.e(TAG, "Error actualizando codigo en Firebase", e)
            }
        }
    }

    // Escuchar cambios de emparejamiento
    DisposableEffect(pairingCode, isPaired) {
        if (pairingCode.isNotEmpty() && !isPaired) {
            val ref = database.getReference("patients/pairing_codes").child(pairingCode)
            var listener: ValueEventListener? = null
            listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.exists()) {
                        return
                    }
                    
                    val paired = snapshot.child("paired").getValue(Boolean::class.java) ?: false
                    val remoteUid = snapshot.child("userId").getValue(String::class.java) ?: "global"
                    
                    if (paired && !isPaired) {
                        Log.d(TAG, "¡Emparejamiento detectado! UID remoto: $remoteUid")
                        prefs.edit()
                            .putBoolean(KEY_PAIRED, true)
                            .putString(KEY_USER_ID, remoteUid)
                            .apply()
                        isPaired = true
                        showSuccessScreen = true
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.w(TAG, "Error escuchando emparejamiento: ${error.message}")
                }
            }
            ref.addValueEventListener(listener)
            onDispose { ref.removeEventListener(listener) }
        } else {
            onDispose {}
        }
    }

    LaunchedEffect(showSuccessScreen) {
        if (showSuccessScreen) {
            delay(3000)
            showSuccessScreen = false
        }
    }
}

@Composable
fun CodeScreen(code: String) {
    var currentTime by remember { mutableStateOf(LocalTime.now()) }
    val timeFormatter = DateTimeFormatter.ofPattern("H:mm")

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = LocalTime.now()
            delay(1000L)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(16.dp)
    ) {
        // Hora superior derecha (color negro porque el fondo es blanco)
        Text(
            text = currentTime.format(timeFormatter),
            color = Color.Black,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 16.dp, top = 12.dp)
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(Alignment.Center)
        ) {
            // Logo VitalSense
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Vital",
                    color = Color(0xFF1E293B), // Slate 800
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Sense",
                    color = Color(0xFF3B82F6), // Blue 500
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(Modifier.height(16.dp))
            
            // Código espaciado
            val formattedCode = code.chunked(1).joinToString(" ")
            Text(
                text = formattedCode,
                color = Color(0xFF3B82F6), // Blue 500
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp
            )
            
            Spacer(Modifier.height(16.dp))
            
            Text(
                text = "El Código Expira\nEn 5 Minutos", 
                textAlign = TextAlign.Center, 
                color = Color(0xFF475569), // Slate 600
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun SuccessScreen() {
    var currentTime by remember { mutableStateOf(LocalTime.now()) }
    val timeFormatter = DateTimeFormatter.ofPattern("H:mm")

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = LocalTime.now()
            delay(1000L)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(16.dp)
    ) {
        // Hora superior derecha
        Text(
            text = currentTime.format(timeFormatter),
            color = Color.Black,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 16.dp, top = 12.dp)
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(Alignment.Center)
        ) {
            // Logo VitalSense
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Vital",
                    color = Color(0xFF1E293B), // Slate 800
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Sense",
                    color = Color(0xFF3B82F6), // Blue 500
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(Modifier.height(16.dp))
            
            // Green Checkmark icon
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .background(Color(0xFF34C759), shape = CircleShape), // Verde
                contentAlignment = Alignment.Center
            ) {
                androidx.wear.compose.material.Text(
                    text = "✓",
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Spacer(Modifier.height(16.dp))
            
            Text(
                text = "Verificación Exitosa", 
                textAlign = TextAlign.Center, 
                color = Color(0xFF475569), // Slate 600
                fontSize = 12.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun PermissionScreen(onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
        Text("Permisos Requeridos", color = Color(0xFF3B82F6), fontSize = 14.sp)
        Text("Se necesita acceso a los sensores para el monitoreo continuo.", 
            textAlign = TextAlign.Center, fontSize = 10.sp, color = Color.Gray)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onClick, colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF3B82F6))) {
            Text("Conceder")
        }
    }
}

@Composable
fun MonitoringScreen(
    isAmbient: Boolean,
    heartRate: Int,
    spo2: Int,
    isSpO2Supported: Boolean,
    sosSent: Boolean,
    onSosTriggered: () -> Unit,
) {
    var currentTime by remember { mutableStateOf(LocalTime.now()) }
    var sosConfirming by remember { mutableStateOf(false) }

    LaunchedEffect(sosSent) {
        if (!sosSent) {
            sosConfirming = false
        }
    }

    LaunchedEffect(isAmbient) {
        while (true) {
            currentTime = LocalTime.now()
            delay(if (isAmbient) 60000L else 1000L)
        }
    }

    val timeFormatter = DateTimeFormatter.ofPattern("H:mm")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        // Hora en la esquina superior derecha
        Text(
            text = currentTime.format(timeFormatter),
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 12.dp, end = 12.dp)
        )

        // Círculo verde y BPM en el centro
        Row(
            modifier = Modifier.align(Alignment.Center).offset(y = (-8).dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .background(Color(0xFF34C759), shape = CircleShape) // Verde de actividad
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${if (heartRate > 0) heartRate else "--"} bpm",
                color = Color.White,
                fontSize = 38.sp,
                fontWeight = FontWeight.Medium
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = 28.dp)  // sits just below the BPM row
                .background(
                    Color(0xFF0F172A).copy(alpha = 0.7f),
                    RoundedCornerShape(999.dp),
                )
                .padding(horizontal = 10.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = when {
                    !isSpO2Supported -> "SpO₂ --"
                    spo2 > 0         -> "SpO₂ $spo2%"
                    else             -> "SpO₂ …"
                },
                color = when {
                    !isSpO2Supported -> Color(0xFF94A3B8)
                    spo2 > 0         -> Color(0xFFA7F3D0)
                    else             -> Color(0xFF64748B)
                },
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }

        // Botón SOS en la parte inferior
        if (!sosSent) {
            val btnColor = if (sosConfirming) Color(0xFFFF9800) else Color(0xFFE74C3C)
            val btnText = if (sosConfirming) "Agita para confirmar" else "SOS"

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(0.9f)
                    .height(44.dp)
                    .padding(bottom = 6.dp)
                    .background(btnColor, shape = RoundedCornerShape(8.dp))
                    .clickable {
                        if (sosConfirming) {
                            onSosTriggered()
                        } else {
                            sosConfirming = true
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = btnText,
                    color = Color.White,
                    fontSize = if (sosConfirming) 13.sp else 18.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(0.8f)
                    .height(44.dp)
                    .padding(bottom = 6.dp)
                    .background(Color(0xFF34C759), shape = RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Alerta Enviada",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun SosQrScreen(
    sosId: String,
    userId: String,
    onDismiss: () -> Unit,
) {
    val database = remember { FirebaseDatabase.getInstance("https://vitalsenseai-1cb9f-default-rtdb.firebaseio.com") }
    // El QR codifica userId + sosId para que el socorrista pueda ver los datos de la alerta
    val qrData   = "vitalsense://sos/$userId/$sosId"
    val qrBitmap = remember(qrData) { generateZxingQr(qrData) }

    LaunchedEffect(sosId, userId) {
        val ref = database.getReference("alerts").child(userId).child(sosId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) { onDismiss(); return }
                val status = snapshot.child("status").getValue(String::class.java) ?: "active"
                if (status == "resolved") onDismiss()
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addValueEventListener(listener)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("SOS Activo", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(8.dp))
            if (qrBitmap != null) {
                Image(
                    bitmap = qrBitmap.asImageBitmap(),
                    contentDescription = "SOS QR",
                    modifier = Modifier.size(120.dp).background(Color.White)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text("Escanea para ayudar", color = Color.Black, fontSize = 12.sp)
        }
    }
}

/**
 * Pantalla de emergencia del reloj: muestra el QR (con el deep link del token)
 * y el PIN de 4 dígitos que el paramédico debe ingresar.
 *
 * Se activa automáticamente cuando el smartphone detecta HR > 150 BPM en reposo
 * y escribe el nodo patients/{userId}/activeEmergency en Firebase.
 */
@Composable
fun EmergencyQrWearScreen(
    tokenId: String,
    pin: String,
    anomalyType: String,
    expiresAt: Long,
) {
    val deepLink = "vitalsense://emergency/$tokenId"
    val qrBitmap = remember(deepLink) { generateZxingQr(deepLink, size = 200) }

    var secondsLeft by remember { mutableStateOf(((expiresAt - System.currentTimeMillis()) / 1000L).toInt().coerceAtLeast(0)) }
    LaunchedEffect(expiresAt) {
        while (secondsLeft > 0) {
            delay(1_000L)
            secondsLeft = ((expiresAt - System.currentTimeMillis()) / 1000L).toInt().coerceAtLeast(0)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFB71C1C)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(8.dp),
        ) {
            Text(
                text       = "EMERGENCIA",
                color      = Color.White,
                fontSize   = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
            )
            Text(
                text     = anomalyType,
                color    = Color(0xFFFFCDD2),
                fontSize = 9.sp,
            )

            // QR Code
            if (qrBitmap != null) {
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .background(Color.White, RoundedCornerShape(8.dp))
                        .padding(4.dp),
                ) {
                    Image(
                        bitmap             = qrBitmap.asImageBitmap(),
                        contentDescription = "QR de emergencia",
                        modifier           = Modifier.fillMaxSize(),
                    )
                }
            }

            // PIN destacado
            val pinFormatted = pin.chunked(1).joinToString(" – ")
            Text(
                text       = "PIN: $pinFormatted",
                color      = Color.White,
                fontSize   = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp,
            )

            // Countdown
            val mins = secondsLeft / 60
            val secs = secondsLeft % 60
            Text(
                text     = "%02d:%02d".format(mins, secs),
                color    = if (secondsLeft < 300) Color(0xFFFFD600) else Color(0xFFFFCDD2),
                fontSize = 10.sp,
            )
        }
    }
}

fun generateZxingQr(data: String, size: Int = 300): android.graphics.Bitmap? {
    return try {
        val writer = com.google.zxing.qrcode.QRCodeWriter()
        val bitMatrix = writer.encode(data, com.google.zxing.BarcodeFormat.QR_CODE, size, size)
        val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.RGB_565)
        for (x in 0 until size) {
            for (y in 0 until size) {
                bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        bitmap
    } catch (e: Exception) {
        null
    }
}
