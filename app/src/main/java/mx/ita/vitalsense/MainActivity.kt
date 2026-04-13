package mx.ita.vitalsense

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import mx.ita.vitalsense.data.ble.FreestyleLibreReader
import mx.ita.vitalsense.data.health.HealthConnectRepository
import mx.ita.vitalsense.ui.health.HealthConnectViewModel
import mx.ita.vitalsense.ui.navigation.AppNavigation
import mx.ita.vitalsense.ui.theme.VitalSenseTheme

data class NotificationOpenRequest(
    val alertId: String,
    val lat: Double,
    val lng: Double,
)

class MainActivity : FragmentActivity() {

    val healthConnectViewModel: HealthConnectViewModel by viewModels()
    var pendingNotificationOpen by mutableStateOf<NotificationOpenRequest?>(null)
        private set
    private val libreReader by lazy { FreestyleLibreReader(this) }

    // Use the official Health Connect permission contract
    private val hcPermissionLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { _ ->
        healthConnectViewModel.refreshPairedState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
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
                AppNavigation()

            }
        }
        pendingNotificationOpen = parseNotificationOpenRequest(intent)
        // Request Health Connect permissions AFTER setContent to avoid blank-screen freeze
        if (HealthConnectClient.getSdkStatus(this) == HealthConnectClient.SDK_AVAILABLE) {
            hcPermissionLauncher.launch(HealthConnectRepository.PERMISSIONS)
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingNotificationOpen = parseNotificationOpenRequest(intent)

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
        db.getReference("patients/$uid/history").push().setValue(
            mapOf(
                "glucose" to glucoseMgDl.toDouble(),
                "heartRate" to 0,
                "spo2" to 0,
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
}
