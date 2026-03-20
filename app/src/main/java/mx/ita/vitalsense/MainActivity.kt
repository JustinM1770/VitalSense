package mx.ita.vitalsense

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import mx.ita.vitalsense.ui.navigation.AppNavigation
import mx.ita.vitalsense.ui.theme.VitalSenseTheme
import androidx.fragment.app.FragmentActivity
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.ui.platform.LocalContext
import android.content.Context

class MainActivity : FragmentActivity() {

    private val database = FirebaseDatabase.getInstance("https://vitalsenseai-1cb9f-default-rtdb.firebaseio.com")
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must be called BEFORE super.onCreate() to intercept the system splash
        installSplashScreen()
        super.onCreate(savedInstanceState)
        
        val serviceIntent = Intent(this, mx.ita.vitalsense.service.EmergencyListenerService::class.java)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
        
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = androidx.activity.SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )
        
        setContent {
            VitalSenseTheme {
                val context = LocalContext.current
                val prefs = remember { context.getSharedPreferences("vitalsense_watch_prefs", Context.MODE_PRIVATE) }
                
                val requireBiometric = prefs.getBoolean("require_biometric", false)
                var isUnlocked by remember { mutableStateOf(!requireBiometric) }
                var biometricError by remember { mutableStateOf<String?>(null) }
                
                val triggerBiometric = {
                    val executor = androidx.core.content.ContextCompat.getMainExecutor(this@MainActivity)
                    val biometricPrompt = androidx.biometric.BiometricPrompt(this@MainActivity, executor,
                        object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                                super.onAuthenticationError(errorCode, errString)
                                biometricError = errString.toString()
                            }
                            override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) {
                                super.onAuthenticationSucceeded(result)
                                isUnlocked = true
                            }
                            override fun onAuthenticationFailed() {
                                super.onAuthenticationFailed()
                                biometricError = "Huella/Rostro no reconocido. Intenta de nuevo."
                            }
                        })

                    val promptInfo = androidx.biometric.BiometricPrompt.PromptInfo.Builder()
                        .setTitle("Desbloqueo Biométrico")
                        .setSubtitle("Toca el sensor de huella o mira a la cámara")
                        .setAllowedAuthenticators(
                            androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK or 
                            androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
                        )
                        .build()

                    biometricPrompt.authenticate(promptInfo)
                }

                LaunchedEffect(requireBiometric) {
                    if (requireBiometric && !isUnlocked) {
                        triggerBiometric()
                    }
                }

                if (!isUnlocked) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), 
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(16.dp))
                            Text("Aplicación Bloqueada", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = MaterialTheme.colorScheme.onBackground)
                            if (biometricError != null) {
                                Spacer(Modifier.height(8.dp))
                                Text(biometricError!!, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
                                Spacer(Modifier.height(24.dp))
                                Button(onClick = { 
                                    biometricError = null
                                    triggerBiometric()
                                }) {
                                    Text("Reintentar", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    return@VitalSenseTheme
                }

                val userId = auth.currentUser?.uid ?: "global"
                var sosAlert by remember { mutableStateOf<Map<String, Any>?>(null) }
                var alertId by remember { mutableStateOf<String?>(null) }

                LaunchedEffect(userId) {
                    val alertsRef = database.getReference("alerts").child(userId)
                    alertsRef.orderByChild("status").equalTo("active")
                        .addValueEventListener(object : ValueEventListener {
                            override fun onDataChange(snapshot: DataSnapshot) {
                                if (snapshot.exists()) {
                                    for (child in snapshot.children) {
                                        val data = child.value as? Map<String, Any>
                                        if (data != null && data["type"] == "SOS") {
                                            sosAlert = data
                                            alertId = child.key
                                            break // Solo mostramos el más reciente activo
                                        }
                                    }
                                } else {
                                    sosAlert = null
                                    alertId = null
                                }
                            }

                            override fun onCancelled(error: DatabaseError) {
                                Log.e("MainActivity", "Error listening for SOS", error.toException())
                            }
                        })
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    AppNavigation()

                    sosAlert?.let { alert ->
                        val lat = (alert["lat"] as? Number)?.toDouble() ?: 0.0
                        val lng = (alert["lng"] as? Number)?.toDouble() ?: 0.0

                        AlertDialog(
                            onDismissRequest = { /* Force acknowledge */ },
                            title = {
                                Text(
                                    text = "¡ALERTA DE EMERGENCIA (SOS)!",
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            },
                            text = {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "El paciente ha presionado el botón de pánico o agitado el reloj fuertemente.",
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(bottom = 8.dp)
                                    )
                                    if (lat != 0.0 && lng != 0.0) {
                                        Text(text = "Ubicación detectada:", fontWeight = FontWeight.Bold)
                                        Text(text = "Lat: $lat\nLng: $lng", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    } else {
                                        Text(text = "Ubicación no disponible.", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        if (lat != 0.0 && lng != 0.0) {
                                            val uri = "geo:$lat,$lng?q=$lat,$lng(Emergencia+SOS)"
                                            val mapIntent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                                            mapIntent.setPackage("com.google.android.apps.maps")
                                            if (mapIntent.resolveActivity(packageManager) != null) {
                                                startActivity(mapIntent)
                                            } else {
                                                // Fallback genérico si no tiene google maps
                                                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text("Ver en Mapa")
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = {
                                    alertId?.let { id ->
                                        database.getReference("alerts").child(userId).child(id).child("status").setValue("resolved")
                                    }
                                    sosAlert = null
                                }) {
                                    Text("Descartar / Atendido")
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
