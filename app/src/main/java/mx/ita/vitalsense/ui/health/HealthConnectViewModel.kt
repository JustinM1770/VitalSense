package mx.ita.vitalsense.ui.health

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import mx.ita.vitalsense.data.health.HealthConnectRepository
import mx.ita.vitalsense.data.health.HealthConnectVitals

/**
 * Application-scoped ViewModel that continuously polls Health Connect every 60 seconds.
 * Scope: scoped to MainActivity so all composables share a single instance.
 */
class HealthConnectViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        const val TAG = "HealthConnectVM"
        const val POLL_INTERVAL_MS = 60_000L
    }

    private val prefs = app.getSharedPreferences("vitalsense_watch_prefs", Context.MODE_PRIVATE)
    private val _vitals = MutableStateFlow(HealthConnectVitals())
    val vitals: StateFlow<HealthConnectVitals> = _vitals.asStateFlow()

    private val _isAvailable = MutableStateFlow(false)
    val isAvailable: StateFlow<Boolean> = _isAvailable.asStateFlow()

    private val _hasPermissions = MutableStateFlow(false)
    val hasPermissions: StateFlow<Boolean> = _hasPermissions.asStateFlow()

    private val _isPaired = MutableStateFlow(false)
    val isPaired: StateFlow<Boolean> = _isPaired.asStateFlow()

    private val db = FirebaseDatabase.getInstance()
    private val auth = FirebaseAuth.getInstance()

    init {
        val available = HealthConnectRepository.isAvailable(app)
        _isAvailable.value = available
        _isPaired.value = prefs.getBoolean("code_paired", false)

        if (available) {
            startPolling(app)
        }
    }

    /** Call this whenever SharedPrefs "code_paired" changes (e.g., after pairing). */
    fun refreshPairedState() {
        _isPaired.value = prefs.getBoolean("code_paired", false)
    }

    private fun startPolling(context: Context) {
        viewModelScope.launch {
            val repo = try { HealthConnectRepository(context) } catch (e: Exception) { return@launch }
            while (isActive) {
                try {
                    val granted = repo.hasPermissions()
                    _hasPermissions.value = granted
                    if (granted) {
                        val vitals = repo.readLatestVitals()
                        _vitals.value = vitals
                        saveToFirebase(vitals)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading Health Connect", e)
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private fun saveToFirebase(v: HealthConnectVitals) {
        val uid = auth.currentUser?.uid ?: return
        val updates = mutableMapOf<String, Any>()
        v.heartRate?.let { updates["patients/$uid/heartRate"] = it }
        v.spo2?.let     { updates["patients/$uid/spo2"]      = it.toInt() }
        v.glucose?.let  { updates["patients/$uid/glucose"]   = it }
        if (updates.isNotEmpty()) {
            updates["patients/$uid/timestamp"] = System.currentTimeMillis()
            updates["patients/$uid/source"]    = "health_connect"
            db.reference.updateChildren(updates)
        }
    }

    /** Force an immediate sync (e.g., called from the settings screen). */
    fun syncNow(context: Context) {
        viewModelScope.launch {
            try {
                val repo = HealthConnectRepository(context)
                if (repo.hasPermissions()) {
                    val v = repo.readLatestVitals()
                    _vitals.value = v
                    saveToFirebase(v)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error on manual sync", e)
            }
        }
    }
}
