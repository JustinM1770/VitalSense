package mx.ita.vitalsense.ui.dashboard

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import mx.ita.vitalsense.data.model.Medication
import mx.ita.vitalsense.data.model.SleepData
import mx.ita.vitalsense.data.model.VitalsData
import mx.ita.vitalsense.data.repository.VitalsRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class DashboardUiState(
    val isWatchPaired: Boolean = false,
    val currentVitals: VitalsData = VitalsData(),
    val vitalsHistory: List<VitalsData> = emptyList(),
    val sleepData: SleepData? = null,
    val medications: List<Medication> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = VitalsRepository()
    private val prefs = application.getSharedPreferences("vitalsense_watch_prefs", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(DashboardUiState(
        isWatchPaired = prefs.getBoolean("code_paired", false),
        isLoading = true
    ))
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance()

    init {
        observeVitals()
        loadAdditionalData()
    }

    fun disconnectWatch() {
        viewModelScope.launch {
            try {
                // 1. Limpiar SharedPreferences local
                prefs.edit().putBoolean("code_paired", false).apply()
                prefs.edit().remove("paired_device_name").apply()
                
                // 2. Limpiar datos en tiempo real en Firebase
                val userId = auth.currentUser?.uid
                if (userId != null) {
                    db.getReference("vitals/current/$userId").removeValue()
                }

                // 3. Notificar a la UI
                _uiState.value = _uiState.value.copy(
                    isWatchPaired = false,
                    currentVitals = VitalsData()
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Error al desvincular: ${e.message}")
            }
        }
    }

    private fun loadAdditionalData() {
        val userId = auth.currentUser?.uid ?: return
        val dateKey = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        
        viewModelScope.launch {
            try {
                // 1. Sleep data
                val sleepSnapshot = db.getReference("sleep/$userId/$dateKey").get().await()
                val sleep = sleepSnapshot.getValue(SleepData::class.java)

                // 2. Vitals History (last 10)
                val historyList = mutableListOf<VitalsData>()
                val userHistorySnapshot = db.getReference("patients/$userId/history").limitToLast(10).get().await()
                
                userHistorySnapshot.children.forEach {
                    it.getValue(VitalsData::class.java)?.let { v -> historyList.add(v) }
                }

                if (historyList.isEmpty()) {
                    val vitalsSnapshot = db.getReference("patients").limitToFirst(1).get().await()
                    val patientId = vitalsSnapshot.children.firstOrNull()?.key
                    if (patientId != null && patientId != userId) {
                        val historySnapshot = db.getReference("patients/$patientId/history").limitToLast(10).get().await()
                        historySnapshot.children.forEach {
                            it.getValue(VitalsData::class.java)?.let { v -> historyList.add(v) }
                        }
                    }
                }

                // 3. Medications
                val medsSnapshot = db.getReference("medications/$userId").get().await()
                val medsList = medsSnapshot.children.mapNotNull { 
                    it.getValue(Medication::class.java) 
                }.filter { it.activo }

                _uiState.value = _uiState.value.copy(
                    sleepData = sleep,
                    vitalsHistory = historyList,
                    medications = medsList,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    private fun observeVitals() {
        viewModelScope.launch {
            repository.observeVitals().collect { result ->
                result.onSuccess {
                    _uiState.value = _uiState.value.copy(currentVitals = it)
                }.onFailure {
                    // Solo log error si realmente importa
                }
            }
        }
    }
}
