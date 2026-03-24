package mx.ita.vitalsense.wear.presentation

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
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
import com.google.android.gms.location.LocationServices
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
import mx.ita.vitalsense.wear.ShakeDetector

private const val TAG = "WearApp"
private const val DB_URL = "https://vitalsenseai-1cb9f-default-rtdb.firebaseio.com"
private const val PREFS_NAME = "vitalsense_wear_prefs"
private const val KEY_PAIRED = "is_paired"
private const val KEY_PAIRING_CODE = "pairing_code"
private const val KEY_USER_ID = "user_id"

@Composable
fun WearApp(isAmbient: Boolean = false) {
    val context = LocalContext.current
    val database = remember { FirebaseDatabase.getInstance(DB_URL) }
    val auth = remember { FirebaseAuth.getInstance() }
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    // --- 1. Permisos ---
    val permissions = arrayOf(
        Manifest.permission.BODY_SENSORS,
        Manifest.permission.ACTIVITY_RECOGNITION,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.POST_NOTIFICATIONS else Manifest.permission.INTERNET
    )

    var hasPermission by remember {
        mutableStateOf(permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        })
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasPermission = result.all { it.value }
    }

    // --- 2. Estado ---
    var isPaired by remember { mutableStateOf(prefs.getBoolean(KEY_PAIRED, false)) }
    var pairingCode by remember { mutableStateOf(prefs.getString(KEY_PAIRING_CODE, "") ?: "") }
    var isAuthenticated by remember { mutableStateOf(auth.currentUser != null) }
    var showSuccessScreen by remember { mutableStateOf(false) }
    
    // Auth Anónima
    LaunchedEffect(Unit) {
        if (auth.currentUser == null) {
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

    LaunchedEffect(sosSent) {
        if (sosSent) {
            delay(5000) // 5 Segundos
            sosSent = false
        }
    }

    val triggerSosUI = {
        if (!sosSent) {
            sosSent = true
            vitalSignsService?.triggerSosAlert()
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
                PermissionScreen { launcher.launch(permissions) }
            } else if (activeSosId != null) {
                SosQrScreen(
                    sosId = activeSosId!!,
                    userId = prefs.getString(KEY_USER_ID, "global") ?: "global",
                    onDismiss = { vitalSignsService?.clearSos() }
                )
            } else {
                MonitoringScreen(
                    isAmbient = isAmbient,
                    heartRate = currentHeartRate.toInt(),
                    sosSent = sosSent,
                    onSosTriggered = { triggerSosUI() }
                )
            }
        }
    }

    // Vinculación mejorada con persistencia de USER_ID
    LaunchedEffect(pairingCode, isAuthenticated) {
        if (isAuthenticated && pairingCode.isNotEmpty()) {
            val ref = database.getReference("patients/pairing_codes").child(pairingCode)
            
            // Si no está emparejado, inicializamos el código en Firebase
            if (!isPaired) {
                ref.setValue(mapOf(
                    "code" to pairingCode, "active" to true, "paired" to false, 
                    "deviceName" to Build.MODEL, "timestamp" to System.currentTimeMillis()
                ))
            }
            
            ref.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!snapshot.exists()) {
                        if (isPaired) {
                            unpairWatch()
                        }
                        return
                    }
                    
                    val paired = snapshot.child("paired").getValue(Boolean::class.java) ?: false
                    val remoteUid = snapshot.child("userId").getValue(String::class.java) ?: "global"
                    
                    if (paired && !isPaired) {
                        prefs.edit()
                            .putBoolean(KEY_PAIRED, true)
                            .putString(KEY_USER_ID, remoteUid)
                            .apply()
                        isPaired = true
                        showSuccessScreen = true
                    } else if (!paired && isPaired) {
                        unpairWatch()
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
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
fun MonitoringScreen(isAmbient: Boolean, heartRate: Int, sosSent: Boolean, onSosTriggered: () -> Unit) {
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
fun SosQrScreen(sosId: String, userId: String, onDismiss: () -> Unit) {
    val database = remember { FirebaseDatabase.getInstance("https://vitalsenseai-1cb9f-default-rtdb.firebaseio.com") }
    val qrBitmap = remember(sosId) { generateZxingQr("vitalsense://sos/$sosId") }

    LaunchedEffect(sosId, userId) {
        val ref = database.getReference("alerts").child(userId).child(sosId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    onDismiss()
                    return
                }
                val read = snapshot.child("read").getValue(Boolean::class.java) ?: false
                val status = snapshot.child("status").getValue(String::class.java) ?: "active"
                if (read || status == "accepted" || status == "resolved") {
                    onDismiss()
                }
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
            Text("Esperando ayuda...", color = Color.Black, fontSize = 12.sp)
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
