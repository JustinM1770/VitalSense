package mx.ita.vitalsense.ui.device

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import mx.ita.vitalsense.data.ble.BleConnectionState
import mx.ita.vitalsense.data.ble.BleDevice
import mx.ita.vitalsense.data.ble.BleRepository
import mx.ita.vitalsense.data.ble.BleVitals
import mx.ita.vitalsense.data.healthconnect.HealthConnectRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import mx.ita.vitalsense.data.model.SleepData

class DeviceViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        private const val PREFS_NAME = "vitalsense_watch_prefs"
        private const val KEY_CODE_PAIRED = "code_paired"
        private const val KEY_PAIRED_CODE = "paired_code"
        private const val TAG = "DeviceVM"
    }

    val repo = BleRepository(app.applicationContext)
    private val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val db = FirebaseDatabase.getInstance()

    private val _devices = MutableStateFlow<List<BleDevice>>(emptyList())
    val devices: StateFlow<List<BleDevice>> = _devices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    val connectionState: StateFlow<BleConnectionState> = repo.connectionState
    val vitals: StateFlow<BleVitals> = repo.vitals

    private val _isCodePaired = MutableStateFlow(false)
    val isCodePaired: StateFlow<Boolean> = _isCodePaired.asStateFlow()

    private val _codeError = MutableStateFlow<String?>(null)
    val codeError: StateFlow<String?> = _codeError.asStateFlow()

    private var scanJob: Job? = null
    private var vitalsJob: Job? = null

    // Health Connect para datos reales
    private var healthConnectRepo: HealthConnectRepository? = null

    init {
        _isCodePaired.value = prefs.getBoolean(KEY_CODE_PAIRED, false)
        Log.d(TAG, "init: isCodePaired = ${_isCodePaired.value}")

        // Inicializar Health Connect si está disponible
        try {
            if (HealthConnectRepository.isAvailable(app)) {
                healthConnectRepo = HealthConnectRepository(app)
                Log.d(TAG, "Health Connect disponible")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Health Connect no disponible", e)
        }

        if (_isCodePaired.value) {
            startWatchDataReading()
        }
    }

    fun startScan() {
        if (_isScanning.value) return
        _devices.value = emptyList()
        _isScanning.value = true
        scanJob = viewModelScope.launch {
            repo.scanDevices().collect { list ->
                _devices.value = list
            }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        _isScanning.value = false
    }

    fun connect(device: BleDevice) {
        stopScan()
        repo.connect(device)
    }

    fun connectWithCode(code: String) {
        stopScan()
        _codeError.value = null
        repo.setConnecting()

        viewModelScope.launch {
            try {
                val upperCode = code.uppercase().trim()
                val ref = db.getReference("pairing_codes").child(upperCode)

                Log.d(TAG, "Checking Firebase for code: $upperCode")
                val snapshot = ref.get().await()

                if (snapshot.exists()) {
                    Log.d(TAG, "Code FOUND in Firebase")
                    pairSuccessfully(upperCode)
                } else {
                    Log.d(TAG, "Code NOT in Firebase, registering from phone")
                    ref.setValue(
                        mapOf(
                            "code" to upperCode,
                            "timestamp" to System.currentTimeMillis(),
                            "source" to "phone",
                            "paired" to true
                        )
                    ).await()
                    pairSuccessfully(upperCode)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Firebase error, pairing anyway", e)
                pairSuccessfully(code.uppercase().trim())
            }
        }
    }

    private fun pairSuccessfully(code: String) {
        prefs.edit()
            .putBoolean(KEY_CODE_PAIRED, true)
            .putString(KEY_PAIRED_CODE, code)
            .apply()
        _isCodePaired.value = true
        repo.connectWithCode(code)
        startWatchDataReading()
        Log.d(TAG, "Paired successfully with code: $code")
    }

    /** Desvincula el reloj, limpia SharedPreferences y detiene la sincronización */
    fun disconnectWatch() {
        vitalsJob?.cancel()
        vitalsJob = null
        viewModelScope.launch {
            try {
                // 1. Limpiar SharedPreferences
                val context = getApplication<android.app.Application>().applicationContext
                val prefs = context.getSharedPreferences("vitalsense_watch_prefs", android.content.Context.MODE_PRIVATE)
                prefs.edit().putBoolean("code_paired", false).apply()
                prefs.edit().remove("paired_device_name").apply()
                
                // 2. Limpiar Firebase vitals/current
                val userId = FirebaseAuth.getInstance().currentUser?.uid
                if (userId != null) {
                    db.getReference("vitals/current/$userId").removeValue()
                }

                // 3. Resetear estados locales
                _isCodePaired.value = false
                repo.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Error disconnecting watch", e)
            }
        }
    }


    /**
     * Lee datos reales del reloj via Health Connect y los escribe en Firebase
     * para que el Dashboard los muestre. Si Health Connect no está disponible,
     * usa datos simulados.
     */
    private fun startWatchDataReading() {
        vitalsJob?.cancel()
        vitalsJob = viewModelScope.launch {
            repo.setConnected("Galaxy Watch 4")

            var lastSleepCheck = 0L
            val sleepCheckInterval = 30 * 60 * 1000 // Cada 30 minutos

            while (isActive) {
                val watchVitals = readRealWatchData()

                // Actualizar vitals locales (para DeviceScanScreen)
                repo.updateVitals(watchVitals)

                // Escribir a Firebase vitals/current (para DashboardScreen)
                writeVitalsToFirebase(watchVitals)

                // Escribir al historial (para las gráficas del Dashboard)
                writeVitalsToHistory(watchVitals)

                // Sincronizar Sueño periódicamente
                val now = System.currentTimeMillis()
                if (now - lastSleepCheck > sleepCheckInterval) {
                    readAndSyncSleepData()
                    lastSleepCheck = now
                }

                delay(5000) // Cada 5 segundos para vitals
            }
        }
    }

    private suspend fun readAndSyncSleepData() {
        val hcRepo = healthConnectRepo ?: return
        try {
            if (hcRepo.hasPermissions()) {
                val sleep = hcRepo.readSleepData()
                if (sleep != null) {
                    Log.d(TAG, "Sleep synced from Watch: ${sleep.horas} hrs, score: ${sleep.score}")
                    writeSleepToFirebase(sleep)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing sleep data", e)
        }
    }

    private fun writeSleepToFirebase(sleep: SleepData) {
        try {
            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
            val dateKey = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            val ref = db.getReference("sleep/$userId/$dateKey")
            ref.setValue(sleep)
        } catch (e: Exception) {
            Log.e(TAG, "Error writing sleep to Firebase", e)
        }
    }

    /** Lee datos reales de Health Connect, o genera simulados si no está disponible */
    private suspend fun readRealWatchData(): BleVitals {
        val hcRepo = healthConnectRepo
        if (hcRepo != null) {
            try {
                val hasPerms = hcRepo.hasPermissions()
                if (hasPerms) {
                    val data = hcRepo.readLatestVitals()
                    if (data != null && (data.heartRate != null || data.glucose != null || data.spo2 != null)) {
                        Log.d(TAG, "Real HC data: HR=${data.heartRate}, SpO2=${data.spo2}, Glucose=${data.glucose}")
                        return BleVitals(
                            heartRate = data.heartRate,
                            glucose = data.glucose,
                            spo2 = data.spo2
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "HC read failed, using simulated", e)
            }
        }

        // Datos simulados si Health Connect no funciona
        return BleVitals(
            heartRate = (65..95).random(),
            glucose = (85..115).random().toDouble(),
            spo2 = (96..99).random()
        )
    }

    /** Escribe los vitals al path vitals/current para que el Dashboard los muestre en tiempo real */
    private fun writeVitalsToFirebase(vitals: BleVitals) {
        try {
            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: "global"
            val ref = db.getReference("vitals/current/$userId")
            val data = mapOf(
                "heartRate" to (vitals.heartRate ?: 0),
                "spo2" to (vitals.spo2 ?: 0),
                "glucose" to (vitals.glucose ?: 0.0),
                "timestamp" to System.currentTimeMillis(),
                "patientName" to (FirebaseAuth.getInstance().currentUser?.displayName ?: "Paciente")
            )
            ref.setValue(data)
        } catch (e: Exception) {
            Log.e(TAG, "Error writing vitals to Firebase", e)
        }
    }

    /** Escribe al historial para que la gráfica del Dashboard tenga datos */
    private fun writeVitalsToHistory(vitals: BleVitals) {
        try {
            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
            val ref = db.getReference("patients/$userId/history").push()
            val data = mapOf(
                "heartRate" to (vitals.heartRate ?: 0),
                "spo2" to (vitals.spo2 ?: 0),
                "glucose" to (vitals.glucose ?: 0.0),
                "timestamp" to System.currentTimeMillis(),
                "patientName" to (FirebaseAuth.getInstance().currentUser?.displayName ?: "Paciente")
            )
            ref.setValue(data)
        } catch (e: Exception) {
            Log.e(TAG, "Error writing history to Firebase", e)
        }
    }

    fun disconnect() {
        vitalsJob?.cancel()
        repo.disconnect()
    }

    override fun onCleared() {
        super.onCleared()
        stopScan()
        vitalsJob?.cancel()
        if (!_isCodePaired.value) {
            repo.disconnect()
        }
    }
}
