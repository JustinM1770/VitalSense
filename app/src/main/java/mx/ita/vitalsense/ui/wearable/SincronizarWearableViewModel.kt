package mx.ita.vitalsense.ui.wearable

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import mx.ita.vitalsense.data.health.HealthConnectRepository

sealed interface SyncState {
    data object Idle : SyncState
    data object Loading : SyncState
    data class Success(val heartRate: Int?, val spo2: Double?, val glucose: Double?) : SyncState
    data class Error(val message: String) : SyncState
}

data class SincronizarUiState(
    val code: String = "",
    val syncState: SyncState = SyncState.Idle,
)

class SincronizarWearableViewModel : ViewModel() {

    private companion object {
        const val REQUIRED_PAIRING_CODE_LENGTH = 8
    }

    private val _uiState = MutableStateFlow(SincronizarUiState())
    val uiState: StateFlow<SincronizarUiState> = _uiState.asStateFlow()

    fun onCodeChange(newCode: String) {
        // Solo alfanumerico, maximo 8 chars, en mayusculas.
        val filtered = newCode.filter { it.isLetterOrDigit() }.uppercase().take(REQUIRED_PAIRING_CODE_LENGTH)
        _uiState.update { it.copy(code = filtered) }
    }

    /**
     * Verifica permisos y lee los signos vitales de Health Connect.
     * Si los permisos no están otorgados, llama [onNeedPermissions] con el set de permisos requerido.
     * Si HC no está disponible en el dispositivo, reporta un error.
     */
    fun sincronizar(
        context: Context,
        onNeedPermissions: (Set<String>) -> Unit,
    ) {
        if (_uiState.value.code.length < REQUIRED_PAIRING_CODE_LENGTH) {
            _uiState.update { it.copy(syncState = SyncState.Error("Ingresa los 8 caracteres del codigo")) }
            return
        }

        _uiState.update { it.copy(syncState = SyncState.Loading) }

        viewModelScope.launch {
            if (!HealthConnectRepository.isAvailable(context)) {
                _uiState.update {
                    it.copy(syncState = SyncState.Error("Health Connect no está disponible en este dispositivo"))
                }
                return@launch
            }

            val repo = HealthConnectRepository(context)

            if (!repo.hasPermissions()) {
                _uiState.update { it.copy(syncState = SyncState.Idle) }
                onNeedPermissions(HealthConnectRepository.PERMISSIONS)
                return@launch
            }

            try {
                val vitals = repo.readLatestVitals()
                saveToFirebase(vitals.heartRate, vitals.spo2, vitals.glucose)
                _uiState.update {
                    it.copy(
                        syncState = SyncState.Success(
                            heartRate = vitals.heartRate,
                            spo2 = vitals.spo2,
                            glucose = vitals.glucose,
                        )
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(syncState = SyncState.Error(e.message ?: "Error al sincronizar"))
                }
            }
        }
    }

    /** Reanuda sincronización después de que el usuario otorga permisos. */
    fun onPermissionsGranted(context: Context) {
        viewModelScope.launch {
            _uiState.update { it.copy(syncState = SyncState.Loading) }
            try {
                val vitals = HealthConnectRepository(context).readLatestVitals()
                saveToFirebase(vitals.heartRate, vitals.spo2, vitals.glucose)
                _uiState.update {
                    it.copy(
                        syncState = SyncState.Success(
                            heartRate = vitals.heartRate,
                            spo2 = vitals.spo2,
                            glucose = vitals.glucose,
                        )
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(syncState = SyncState.Error(e.message ?: "Error al leer datos"))
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(syncState = SyncState.Idle) }
    }

    private fun saveToFirebase(heartRate: Int?, spo2: Double?, glucose: Double?) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val db = FirebaseDatabase.getInstance().reference
        val updates = mutableMapOf<String, Any>()
        heartRate?.let { updates["patients/$uid/heartRate"] = it }
        spo2?.let     { updates["patients/$uid/spo2"]      = it }
        glucose?.let  { updates["patients/$uid/glucose"]   = it }
        updates["patients/$uid/timestamp"] = System.currentTimeMillis()
        updates["patients/$uid/source"]    = "health_connect"
        if (updates.isNotEmpty()) db.updateChildren(updates)
    }
}
