package mx.ita.vitalsense

import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.launch
import mx.ita.vitalsense.data.ble.FreestyleLibreReader
import mx.ita.vitalsense.data.health.HealthConnectRepository
import mx.ita.vitalsense.settings.AppSettings
import mx.ita.vitalsense.service.EmergencyListenerService
import mx.ita.vitalsense.ui.health.HealthConnectViewModel
import mx.ita.vitalsense.ui.navigation.AppNavigation
import mx.ita.vitalsense.ui.theme.VitalSenseTheme

data class NotificationOpenRequest(
    val alertId: String,
    val lat: Double,
    val lng: Double,
)

class MainActivity : AppCompatActivity() {

    val healthConnectViewModel: HealthConnectViewModel by viewModels()
    var pendingNotificationOpen by mutableStateOf<NotificationOpenRequest?>(null)
        private set
    var pendingMedicationOpen by mutableStateOf(false)
        private set
    private val libreReader by lazy { FreestyleLibreReader(this) }
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        firebaseAuth.currentUser?.uid?.let { uid ->
            syncPushToken(uid)
            startEmergencyListener(uid)
        } ?: stopEmergencyListener()
    }

    // Use the official Health Connect permission contract
    private val hcPermissionLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { _ ->
        healthConnectViewModel.refreshPairedState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppSettings.applySavedPreferences(this)
        super.onCreate(savedInstanceState)
        auth.addAuthStateListener(authListener)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            val themeMode by AppSettings.themeFlow.collectAsState()
            VitalSenseTheme(themeMode = themeMode) {
                val isDarkTheme = when (themeMode) {
                    "dark" -> true
                    "light" -> false
                    else -> isSystemInDarkTheme()
                }
                val colorScheme = androidx.compose.material3.MaterialTheme.colorScheme
                val view = LocalView.current
                SideEffect {
                    val window = (view.context as? android.app.Activity)?.window ?: return@SideEffect
                    window.statusBarColor = colorScheme.background.toArgb()
                    window.navigationBarColor = colorScheme.background.toArgb()
                    WindowCompat.getInsetsController(window, view).apply {
                        isAppearanceLightStatusBars = !isDarkTheme
                        isAppearanceLightNavigationBars = !isDarkTheme
                    }
                }
                AppNavigation()

            }
        }
        pendingNotificationOpen = parseNotificationOpenRequest(intent)
        pendingMedicationOpen = intent.getBooleanExtra("open_medications", false)
        // Request Health Connect permissions only if not already granted.
        // Launching the permission contract restarts MainActivity (via onActivityResult),
        // so we must guard against re-requesting and creating an infinite loop.
        if (HealthConnectClient.getSdkStatus(this) == HealthConnectClient.SDK_AVAILABLE
            && savedInstanceState == null) {
            requestHealthConnectPermissionsIfNeeded()
        }

        auth.currentUser?.uid?.let { uid ->
            syncPushToken(uid)
            startEmergencyListener(uid)
        }
    }

    override fun onDestroy() {
        stopEmergencyListener()
        auth.removeAuthStateListener(authListener)
        super.onDestroy()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingNotificationOpen = parseNotificationOpenRequest(intent)
        pendingMedicationOpen = intent.getBooleanExtra("open_medications", false)

        val glucose = libreReader.parseFreestyleLibre(intent)
        if (glucose != null) {
            saveLibreGlucose(glucose)
        }
    }

    override fun onResume() {
        super.onResume()
        if (libreReader.isNfcAvailable()) {
            libreReader.enableNfcReading(this)
        }
    }

    override fun onPause() {
        super.onPause()
        libreReader.disableNfcReading(this)
    }

    fun consumePendingNotificationOpen() {
        pendingNotificationOpen = null
    }

    fun consumePendingMedicationOpen() {
        pendingMedicationOpen = false
    }

    /**
     * Requests Health Connect permissions only when not already fully granted.
     * This prevents restarting the permission Activity on every MainActivity re-creation
     * (which would cause an infinite permission loop).
     */
    private fun requestHealthConnectPermissionsIfNeeded() {
        lifecycleScope.launch {
            try {
                val client = HealthConnectClient.getOrCreate(this@MainActivity)
                val granted = client.permissionController.getGrantedPermissions()
                if (!granted.containsAll(HealthConnectRepository.PERMISSIONS)) {
                    hcPermissionLauncher.launch(HealthConnectRepository.PERMISSIONS)
                }
            } catch (e: Exception) {
                // Health Connect not available or error — skip silently
            }
        }
    }

    private fun parseNotificationOpenRequest(intent: android.content.Intent?): NotificationOpenRequest? {
        val src = intent ?: return null
        if (!src.getBooleanExtra("open_notifications", false)) return null
        val alertId = src.getStringExtra("alert_id").orEmpty()
        if (alertId.isBlank()) return null
        val lat = src.getDoubleExtra("alert_lat", 0.0)
        val lng = src.getDoubleExtra("alert_lng", 0.0)
        return NotificationOpenRequest(alertId = alertId, lat = lat, lng = lng)
    }

    private fun saveLibreGlucose(glucoseMgDl: Float) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        if (uid.isBlank()) {
            Toast.makeText(this, "Lectura NFC detectada, inicia sesion para guardarla", Toast.LENGTH_LONG).show()
            return
        }

        val now = System.currentTimeMillis()
        val db = FirebaseDatabase.getInstance()
        val prefs = getSharedPreferences("vitalsense_profile", MODE_PRIVATE)

        val updates = mapOf(
            "glucose" to glucoseMgDl.toDouble(),
            "timestamp" to now,
            "glucoseSource" to "freestyle_libre_nfc",
        )

        db.getReference("patients/$uid").updateChildren(updates)
        db.getReference("patients/$uid/glucose_history").push().setValue(
            mapOf(
                "glucose" to glucoseMgDl.toDouble(),
                "timestamp" to now,
                "source" to "freestyle_libre_nfc",
            ),
        )

        prefs.edit()
            .putFloat("libre_last_glucose_$uid", glucoseMgDl)
            .putLong("libre_last_time_$uid", now)
            .putString("libre_last_source_$uid", "freestyle_libre_nfc")
            .putString("libre_last_confidence_$uid", "Compatibilidad NFC inicial (experimental)")
            .apply()

        Toast.makeText(this, "Glucosa NFC: ${"%.0f".format(glucoseMgDl)} mg/dL", Toast.LENGTH_LONG).show()
    }

    private fun syncPushToken(uid: String) {
        if (uid.isBlank()) return
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token ->
                FirebaseDatabase.getInstance()
                    .getReference("patients/$uid/deviceToken")
                    .setValue(token)
                FirebaseDatabase.getInstance()
                    .getReference("patients/$uid/deviceTokenUpdatedAt")
                    .setValue(System.currentTimeMillis())
            }
    }

    private fun startEmergencyListener(uid: String) {
        if (uid.isBlank()) return
        val intent = Intent(this, EmergencyListenerService::class.java).apply {
            putExtra(EmergencyListenerService.EXTRA_USER_ID, uid)
        }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopEmergencyListener() {
        stopService(Intent(this, EmergencyListenerService::class.java))
    }
}
