package mx.ita.vitalsense.ui.patient

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import mx.ita.vitalsense.data.model.Medication
import mx.ita.vitalsense.data.model.VitalsData
import java.util.*

data class PatientDetailUiState(
    val patientName: String = "",
    val heartRateHistory: Map<String, Float> = emptyMap(), // Month -> Avg BPM
    val medications: List<Medication> = emptyList(),
    val isLoading: Boolean = false
)

class PatientDetailViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance()
    
    private val _uiState = MutableStateFlow(PatientDetailUiState())
    val uiState: StateFlow<PatientDetailUiState> = _uiState

    init {
        loadDetailedData()
    }

    private fun loadDetailedData() {
        val userId = auth.currentUser?.uid ?: return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                patientName = auth.currentUser?.displayName ?: ""
            )
            try {
                // 1. Historial del usuario actual
                val historySnapshot = db.getReference("patients/$userId/history").get().await()
                val monthlyData = mutableMapOf<Int, MutableList<Int>>()

                historySnapshot.children.forEach {
                    val vitals = it.getValue(VitalsData::class.java)
                    if (vitals != null && vitals.timestamp > 0) {
                        val calendar = Calendar.getInstance().apply { timeInMillis = vitals.timestamp }
                        val month = calendar.get(Calendar.MONTH)
                        monthlyData.getOrPut(month) { mutableListOf() }.add(vitals.heartRate)
                    }
                }

                val months = listOf("Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio", "Julio")
                val chartData = mutableMapOf<String, Float>()
                months.forEachIndexed { index, name ->
                    val avg = monthlyData[index]?.average()?.toFloat() ?: 0f
                    chartData[name] = avg
                }

                _uiState.value = _uiState.value.copy(heartRateHistory = chartData)

                // 2. Load Medications
                val medsSnapshot = db.getReference("medications/$userId").get().await()
                val medsList = medsSnapshot.children.mapNotNull { 
                    it.getValue(Medication::class.java) 
                }.filter { it.activo }

                _uiState.value = _uiState.value.copy(
                    medications = medsList,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }
}
