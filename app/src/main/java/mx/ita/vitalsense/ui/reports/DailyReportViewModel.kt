package mx.ita.vitalsense.ui.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
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
    val currentVitals: VitalsData = VitalsData(),
    val isLoading: Boolean = false
)

class DailyReportViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance()
    
    private val _uiState = MutableStateFlow(DailyReportUiState())
    val uiState: StateFlow<DailyReportUiState> = _uiState

    private var vitalsListener: ValueEventListener? = null

    init {
        loadDataForDate(LocalDate.now())
        observeCurrentVitals()
    }

    fun onDateSelected(date: LocalDate) {
        _uiState.value = _uiState.value.copy(selectedDate = date)
        loadDataForDate(date)
    }

    /** Escucha los datos en tiempo real del reloj desde vitals/current/$userId */
    private fun observeCurrentVitals() {
        val userId = auth.currentUser?.uid ?: "global"
        val ref = db.getReference("vitals/current/$userId")
        vitalsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val vitals = snapshot.getValue(VitalsData::class.java)
                if (vitals != null) {
                    _uiState.value = _uiState.value.copy(currentVitals = vitals)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addValueEventListener(vitalsListener!!)
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
                
                // Load Vitals History —  primero del usuario, luego de patients genérico
                val historyList = mutableListOf<VitalsData>()
                
                // Intentar leer del historial del usuario actual
                val userHistorySnapshot = db.getReference("patients/$userId/history")
                    .limitToLast(20)
                    .get().await()
                userHistorySnapshot.children.forEach {
                    it.getValue(VitalsData::class.java)?.let { vitals -> historyList.add(vitals) }
                }

                // Si no hay datos del usuario, buscar en patients genérico
                if (historyList.isEmpty()) {
                    val vitalsSnapshot = db.getReference("patients").limitToFirst(1).get().await()
                    val patientId = vitalsSnapshot.children.firstOrNull()?.key
                    if (patientId != null) {
                        val historySnapshot = db.getReference("patients/$patientId/history")
                            .limitToLast(20)
                            .get().await()
                        historySnapshot.children.forEach {
                            it.getValue(VitalsData::class.java)?.let { vitals -> historyList.add(vitals) }
                        }
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

    override fun onCleared() {
        super.onCleared()
        vitalsListener?.let {
            val userId = auth.currentUser?.uid ?: "global"
            db.getReference("vitals/current/$userId").removeEventListener(it)
        }
    }
}
