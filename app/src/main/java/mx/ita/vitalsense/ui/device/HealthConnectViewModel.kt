package mx.ita.vitalsense.ui.device

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import mx.ita.vitalsense.data.healthconnect.HealthConnectRepository
import mx.ita.vitalsense.data.healthconnect.HealthConnectVitals
import mx.ita.vitalsense.data.model.SleepData
import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class HealthConnectState {
    IDLE,
    LOADING,
    SUCCESS,
    NO_DATA,
    SDK_NOT_AVAILABLE,
    PERMISSION_DENIED,
    ERROR
}

class HealthConnectViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        private const val PREFS_NAME = "vitalsense_watch_prefs"
        private const val KEY_PAIRED = "galaxy_watch_paired"
        private const val TAG = "HealthConnectVM"
        private const val REFRESH_INTERVAL_MS = 30_000L // 30 segundos
    }

    private val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _vitals = MutableStateFlow<HealthConnectVitals?>(null)
    val vitals: StateFlow<HealthConnectVitals?> = _vitals.asStateFlow()

    private val _state = MutableStateFlow(HealthConnectState.IDLE)
    val state: StateFlow<HealthConnectState> = _state.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isPaired = MutableStateFlow(false)
    val isPaired: StateFlow<Boolean> = _isPaired.asStateFlow()

    private var refreshJob: Job? = null

    init {
        // Cargar estado de emparejamiento guardado
        _isPaired.value = prefs.getBoolean(KEY_PAIRED, false)
        Log.d(TAG, "init: isPaired = ${_isPaired.value}")

        // Si ya está emparejado, empezar a leer datos automáticamente
        if (_isPaired.value) {
            startPeriodicRefresh()
        }
    }

    private fun savePairedState(paired: Boolean) {
        prefs.edit().putBoolean(KEY_PAIRED, paired).apply()
        _isPaired.value = paired
        Log.d(TAG, "savePairedState: $paired")
    }

    private fun getRepo(): HealthConnectRepository? {
        return try {
            HealthConnectRepository(getApplication())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create HealthConnectRepository", e)
            null
        }
    }

    private fun isSdkAvailable(): Boolean {
        return try {
            val status = HealthConnectClient.getSdkStatus(getApplication())
            Log.d(TAG, "SDK status = $status")
            status == HealthConnectClient.SDK_AVAILABLE
        } catch (e: Exception) {
            Log.e(TAG, "getSdkStatus threw", e)
            false
        }
    }

    /** Inicia refresco periódico de datos cada 30 segundos */
    private fun startPeriodicRefresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            while (isActive) {
                refreshData()
                delay(REFRESH_INTERVAL_MS)
            }
        }
    }

    private fun writeSleepToFirebase(sleep: SleepData) {
        try {
            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
            val dateKey = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            val ref = FirebaseDatabase.getInstance("https://vitalsenseai-1cb9f-default-rtdb.firebaseio.com").getReference("sleep/$userId/$dateKey")
            ref.setValue(sleep)
        } catch (e: Exception) {
            Log.e(TAG, "Error writing sleep to Firebase", e)
        }
    }

    /** Lee los datos más recientes sin cambiar el estado de emparejamiento */
    private suspend fun refreshData() {
        try {
            val repo = getRepo() ?: return
            
            if (!repo.hasPermissions()) {
                _state.value = HealthConnectState.PERMISSION_DENIED
                _errorMessage.value = "Faltan permisos. Por favor, asegúrate de permitir el acceso a TODOS los datos (Ritmo Cardíaco, SpO2, Glucosa y Sueño)."
                return
            }

            val data = repo.readLatestVitals()
            val sleepData = repo.readSleepData()
            
            if (sleepData != null) {
                writeSleepToFirebase(sleepData)
            }
            
            val hasVitals = data != null && (data.heartRate != null || data.glucose != null || data.spo2 != null)
            val hasSleep = sleepData != null

            if (hasVitals || hasSleep) {
                if (hasVitals) {
                    _vitals.value = data
                }
                _state.value = HealthConnectState.SUCCESS
                Log.d(TAG, "refreshData: HR=${data?.heartRate}, SpO2=${data?.spo2}, Glucose=${data?.glucose}, Sleep=${sleepData?.score}")
            } else {
                // Si ya está emparejado pero no hay datos recientes, mantener SUCCESS con los últimos datos
                if (_vitals.value == null) {
                    _state.value = HealthConnectState.NO_DATA
                    _errorMessage.value = "Esperando datos nuevos de tu Galaxy Watch..."
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "refreshData error", e)
            // No cambiar a ERROR si ya está emparejado, solo loguear
        }
    }

    /** Primera sincronización — empareja permanentemente si tiene éxito */
    fun loadHealthConnectData() {
        viewModelScope.launch {
            _state.value = HealthConnectState.LOADING
            _errorMessage.value = null
            try {
                val repo = getRepo()
                if (repo == null) {
                    _state.value = HealthConnectState.SDK_NOT_AVAILABLE
                    _errorMessage.value = "No se pudo inicializar Health Connect. Verifica que esté instalado."
                    return@launch
                }
                
                if (!repo.hasPermissions()) {
                    _state.value = HealthConnectState.PERMISSION_DENIED
                    _errorMessage.value = "Faltan permisos. Por favor, asegúrate de permitir el acceso a TODOS los datos (Ritmo Cardíaco, SpO2, Glucosa y Sueño)."
                    return@launch
                }

                val data = repo.readLatestVitals()
                val sleepData = repo.readSleepData()
                
                if (sleepData != null) {
                    writeSleepToFirebase(sleepData)
                }
                
                val hasVitals = data != null && (data.heartRate != null || data.glucose != null || data.spo2 != null)
                val hasSleep = sleepData != null

                if (hasVitals || hasSleep) {
                    if (hasVitals) {
                        _vitals.value = data
                    }
                    _state.value = HealthConnectState.SUCCESS
                    // ¡Emparejar permanentemente!
                    savePairedState(true)
                    startPeriodicRefresh()
                } else {
                    _vitals.value = null
                    _state.value = HealthConnectState.NO_DATA
                    _errorMessage.value = "No se encontraron datos recientes. Asegúrate de que Samsung Health esté sincronizando con Health Connect."
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading vitals", e)
                _state.value = HealthConnectState.ERROR
                _errorMessage.value = "Error al leer datos: ${e.localizedMessage ?: e.toString()}"
            }
        }
    }

    fun checkAndRequestPermissions(launcher: ActivityResultLauncher<Set<String>>) {
        _state.value = HealthConnectState.LOADING
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                if (!isSdkAvailable()) {
                    _state.value = HealthConnectState.SDK_NOT_AVAILABLE
                    _errorMessage.value = "Health Connect no está disponible. Instala la app 'Health Connect' desde Google Play Store."
                    return@launch
                }

                val repo = getRepo()
                if (repo == null) {
                    _state.value = HealthConnectState.SDK_NOT_AVAILABLE
                    _errorMessage.value = "No se pudo conectar con Health Connect."
                    return@launch
                }

                val hasPerms = try {
                    repo.hasPermissions()
                } catch (e: Exception) {
                    Log.e(TAG, "hasPermissions threw", e)
                    false
                }

                if (!hasPerms) {
                    _state.value = HealthConnectState.IDLE
                    launcher.launch(HealthConnectRepository.PERMISSIONS)
                } else {
                    loadHealthConnectData()
                }
            } catch (e: Exception) {
                Log.e(TAG, "checkAndRequestPermissions error", e)
                _state.value = HealthConnectState.ERROR
                _errorMessage.value = "Error: ${e.localizedMessage ?: e.toString()}"
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        refreshJob?.cancel()
    }
}
