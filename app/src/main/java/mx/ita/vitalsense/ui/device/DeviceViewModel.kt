package mx.ita.vitalsense.ui.device

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import mx.ita.vitalsense.data.ble.BleConnectionState
import mx.ita.vitalsense.data.ble.BleDevice
import mx.ita.vitalsense.data.ble.BleRepository
import mx.ita.vitalsense.data.ble.BleVitals
import mx.ita.vitalsense.data.health.HealthConnectRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import mx.ita.vitalsense.data.model.SleepData
import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.tasks.await
import mx.ita.vitalsense.data.model.VitalsData
import mx.ita.vitalsense.data.repository.VitalsRepository

class DeviceViewModel(app: Application) : AndroidViewModel(app) {
    companion object {
        const val TAG = "DeviceViewModel"
        const val PREFS_NAME = "vitalsense_watch_prefs"
        const val KEY_CODE_PAIRED = "code_paired"
        const val KEY_PAIRED_CODE = "paired_code"
        const val KEY_PAIRED_DEVICE_NAME = "paired_device_name"
    }


    val repo = BleRepository(app.applicationContext)
    private val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val db = FirebaseDatabase.getInstance()
    private val vitalsRepo = VitalsRepository()

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

    private val _pairedDeviceName = MutableStateFlow("Wearable")
    val pairedDeviceName: StateFlow<String> = _pairedDeviceName.asStateFlow()
    // ID del paciente al que se asocian las lecturas BLE
    private val _selectedPatientId = MutableStateFlow("")
    val selectedPatientId: StateFlow<String> = _selectedPatientId.asStateFlow()

    private var scanJob: Job? = null
    private var snapshotJob: Job? = null
    private var vitalsJob: Job? = null

    private var healthConnectRepo: HealthConnectRepository? = null
    private var lastHistoryTimestamp: Long = 0L

    init {
        _isCodePaired.value = prefs.getBoolean(KEY_CODE_PAIRED, false)
        _pairedDeviceName.value = prefs.getString(KEY_PAIRED_DEVICE_NAME, "Wearable") ?: "Wearable"

        try {
            if (HealthConnectRepository.isAvailable(app)) {
                healthConnectRepo = HealthConnectRepository(app)
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
        startSnapshotSaving()
    }

    fun connectWithCode(code: String) {
        stopScan()
        _codeError.value = null
        repo.setConnecting()

        viewModelScope.launch {
            try {
                val upperCode = code.uppercase().trim()
                val ref = db.getReference("patients/pairing_codes").child(upperCode)
                val userId = FirebaseAuth.getInstance().currentUser?.uid
                if (userId.isNullOrBlank()) {
                    _codeError.value = "Debes iniciar sesion para vincular el reloj."
                    repo.setDisconnected()
                    return@launch
                }
                val snapshot = ref.get().await()

                if (snapshot.exists()) {
                    val deviceName = snapshot.child("deviceName").getValue(String::class.java) ?: "Wearable"
                    ref.updateChildren(mapOf("paired" to true, "userId" to userId)).await()
                    pairSuccessfully(upperCode, deviceName)
                } else {
                    _codeError.value = "Código inválido. Verifica el código en tu reloj e inténtalo de nuevo."
                    repo.setDisconnected()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error al vincular reloj con Firebase", e)
                _codeError.value = "No se pudo validar el codigo con el servidor. Intentalo de nuevo."
                repo.setDisconnected()
            }
        }
    }

    private fun pairSuccessfully(code: String, deviceName: String) {
        prefs.edit()
            .putBoolean(KEY_CODE_PAIRED, true)
            .putString(KEY_PAIRED_CODE, code)
            .putString(KEY_PAIRED_DEVICE_NAME, deviceName)
            .apply()
        _isCodePaired.value = true
        _pairedDeviceName.value = deviceName
        repo.connectWithCode(code, deviceName)

        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            FirebaseDatabase.getInstance().getReference("patients/$uid/watch")
                .setValue(mapOf("code" to code, "deviceName" to deviceName, "paired" to true))
        }

        startWatchDataReading()
    }

    fun disconnectWatch() {
        vitalsJob?.cancel()
        vitalsJob = null
        viewModelScope.launch {
            try {
                val pairedCode = prefs.getString(KEY_PAIRED_CODE, null)
                val userId = FirebaseAuth.getInstance().currentUser?.uid
                if (userId != null) {
                    db.getReference("patients/$userId/watch").removeValue()
                }

                if (pairedCode != null) {
                    db.getReference("patients/pairing_codes").child(pairedCode)
                        .updateChildren(mapOf("paired" to false))
                        .await()
                }
                prefs.edit().putBoolean(KEY_CODE_PAIRED, false).remove(KEY_PAIRED_CODE).remove(KEY_PAIRED_DEVICE_NAME).apply()
                if (userId != null) db.getReference("vitals/current/$userId").removeValue()
                _isCodePaired.value = false
                _pairedDeviceName.value = "Wearable"
                repo.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Error disconnecting watch", e)
            }
        }
    }

    private fun startWatchDataReading() {
        vitalsJob?.cancel()
        val deviceName = _pairedDeviceName.value
        vitalsJob = viewModelScope.launch {
            repo.setConnected(deviceName)

            // 1. OBSERVAR FIREBASE (Signos Vitales en Tiempo Real)
            launch {
                vitalsRepo.observeVitals().collect { result: Result<VitalsData> ->
                    result.onSuccess { vitals: VitalsData ->
                        val ble = BleVitals(
                            heartRate = vitals.heartRate,
                            glucose = vitals.glucose,
                            spo2 = vitals.spo2,
                            timestamp = vitals.timestamp
                        )
                        repo.updateVitals(ble)
                    }
                }
            }

            // 2. POLLING HEALTH CONNECT — Signos vitales cada 60 segundos
            launch {
                val hcRepo = healthConnectRepo ?: return@launch
                while (isActive) {
                    try {
                        if (hcRepo.hasPermissions()) {
                            val hcVitals = hcRepo.readLatestVitals()
                            val now = System.currentTimeMillis()
                            val hrSampleTs = hcVitals.heartRateSampleTimestamp
                            val isHeartRateFresh = hrSampleTs != null && (now - hrSampleTs) <= 2 * 60_000L

                            if (!isHeartRateFresh) {
                                delay(60_000L)
                                continue
                            }

                            val ble = BleVitals(
                                heartRate = hcVitals.heartRate ?: 0,
                                glucose   = hcVitals.glucose   ?: 0.0,
                                spo2      = hcVitals.spo2?.toInt() ?: 0,
                                timestamp = System.currentTimeMillis()
                            )
                            if ((ble.heartRate ?: 0) > 0) {
                                repo.updateVitals(ble)
                                writeVitalsToFirebase(ble, hrSampleTs)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "HC vitals poll error", e)
                    }
                    delay(60_000L)
                }
            }

            // 3. POLLING HEALTH CONNECT — Datos de Sueño cada 30 min
            launch {
                while (isActive) {
                    readAndSyncSleepData()
                    delay(30 * 60 * 1000)
                }
            }
        }
    }

    private suspend fun readAndSyncSleepData() {
        val hcRepo = healthConnectRepo ?: return
        try {
            if (hcRepo.hasPermissions()) {
                val end = Instant.now()
                val start = end.minus(java.time.Duration.ofDays(1))
                val sleepData = hcRepo.readSleepData(start, end, getApplication())
                if (sleepData != null) {
                    writeSleepToFirebase(sleepData)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing sleep data", e)
        }
    }

    fun disconnect() {
        vitalsJob?.cancel()
        snapshotJob?.cancel()
        repo.disconnect()
    }

    fun setPatientId(patientId: String) {
        _selectedPatientId.value = patientId
    }

    /** Guarda cada lectura BLE como snapshot histórico en Firebase. */
    private fun startSnapshotSaving() {
        snapshotJob?.cancel()
        snapshotJob = viewModelScope.launch {
            vitals.collect { ble ->
                val patientId = _selectedPatientId.value
                if (patientId.isNotEmpty() && (ble.heartRate != null || ble.glucose != null || ble.spo2 != null)) {
                    val vitalsData = VitalsData(
                        patientId = patientId,
                        heartRate = ble.heartRate ?: 0,
                        glucose   = ble.glucose   ?: 0.0,
                        spo2      = ble.spo2       ?: 0,
                        timestamp = System.currentTimeMillis(),
                    )
                    vitalsRepo.saveSnapshot(patientId, vitalsData)
                }
            }
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

    private fun writeVitalsToFirebase(vitals: BleVitals, heartRateSampleTimestamp: Long? = null) {
        try {
            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: "global"
            val ref = db.getReference("vitals/current/$userId")
            val data = mutableMapOf<String, Any>(
                "heartRate" to (vitals.heartRate ?: 0),
                "spo2" to (vitals.spo2 ?: 0),
                "glucose" to (vitals.glucose ?: 0.0),
                "timestamp" to System.currentTimeMillis()
            )
            if (heartRateSampleTimestamp != null) {
                data["heartRateSampleTimestamp"] = heartRateSampleTimestamp
            }
            ref.setValue(data)
        } catch (e: Exception) {
            Log.e(TAG, "Error writing vitals to Firebase", e)
        }
    }

    private fun writeVitalsToHistory(vitals: BleVitals) {
        try {
            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
            val ref = db.getReference("patients/$userId/history").push()
            val data = mapOf(
                "heartRate" to (vitals.heartRate ?: 0),
                "spo2" to (vitals.spo2 ?: 0),
                "glucose" to (vitals.glucose ?: 0.0),
                "timestamp" to System.currentTimeMillis()
            )
            ref.setValue(data)
        } catch (e: Exception) {
            Log.e(TAG, "Error writing history to Firebase", e)
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopScan()
        vitalsJob?.cancel()
        snapshotJob?.cancel()
        repo.disconnect()
    }
}
