package mx.ita.vitalsense.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import mx.ita.vitalsense.data.model.SleepData
import mx.ita.vitalsense.data.model.VitalsData
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class DailyReportUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val sleepData: SleepData? = null,
    val vitalsHistory: List<VitalsData> = emptyList(),
    val isLoading: Boolean = false
)

class DailyReportViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance()
    
    private val _uiState = MutableStateFlow(DailyReportUiState())
    val uiState: StateFlow<DailyReportUiState> = _uiState

    init {
        loadDataForDate(LocalDate.now())
    }

    fun onDateSelected(date: LocalDate) {
        _uiState.value = _uiState.value.copy(selectedDate = date)
        loadDataForDate(date)
    }

    private fun loadDataForDate(date: LocalDate) {
        val userId = auth.currentUser?.uid ?: return
        val dateKey = date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                // Load Sleep Data
                val sleepSnapshot = db.getReference("sleep/$userId/$dateKey").get().await()
                val sleep = sleepSnapshot.getValue(SleepData::class.java)
                
                // Load Vitals History (Simplified for demo)
                // In a real app, we would filter by date. For demo, we might show the latest entries.
                val vitalsSnapshot = db.getReference("patients").limitToFirst(1).get().await()
                val patientId = vitalsSnapshot.children.firstOrNull()?.key
                
                val historyList = mutableListOf<VitalsData>()
                if (patientId != null) {
                    val historySnapshot = db.getReference("patients/$patientId/history").get().await()
                    historySnapshot.children.forEach {
                        it.getValue(VitalsData::class.java)?.let { vitals -> historyList.add(vitals) }
                    }
                }

                _uiState.value = _uiState.value.copy(
                    sleepData = sleep,
                    vitalsHistory = historyList,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }
    
    fun scoreToGrade(score: Int) = when {
        score >= 90 -> "A+"
        score >= 80 -> "A"
        score >= 70 -> "B+"
        score >= 60 -> "B"
        else -> "C"
    }
}
