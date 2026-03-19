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
import mx.ita.vitalsense.data.model.VitalsData

data class DetailedReportUiState(
    val vitalsHistory: List<VitalsData> = emptyList(),
    val currentVitals: VitalsData = VitalsData(),
    val isLoading: Boolean = false,
    val averageHeartRate: Int = 0
)

class DetailedReportViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance()
    
    private val _uiState = MutableStateFlow(DetailedReportUiState())
    val uiState: StateFlow<DetailedReportUiState> = _uiState

    private var vitalsListener: ValueEventListener? = null

    init {
        loadDetailedData()
        observeCurrentVitals()
    }

    private fun observeCurrentVitals() {
        val userId = auth.currentUser?.uid ?: "global"
        val ref = db.getReference("vitals/current/$userId")
        vitalsListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                snapshot.getValue(VitalsData::class.java)?.let { vitals ->
                    _uiState.value = _uiState.value.copy(currentVitals = vitals)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        ref.addValueEventListener(vitalsListener!!)
    }

    private fun loadDetailedData() {
        val userId = auth.currentUser?.uid ?: return
        
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                // Fetch more history points for the detailed chart (last 50)
                val historySnapshot = db.getReference("patients/$userId/history")
                    .limitToLast(50)
                    .get().await()
                
                val historyList = mutableListOf<VitalsData>()
                var totalHR = 0
                var count = 0
                
                historySnapshot.children.forEach {
                    it.getValue(VitalsData::class.java)?.let { vitals ->
                        historyList.add(vitals)
                        if (vitals.heartRate > 0) {
                            totalHR += vitals.heartRate
                            count++
                        }
                    }
                }

                _uiState.value = _uiState.value.copy(
                    vitalsHistory = historyList,
                    averageHeartRate = if (count > 0) totalHR / count else 0,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        vitalsListener?.let {
            val userId = auth.currentUser?.uid ?: "global"
            db.getReference("vitals/current/$userId").removeEventListener(it)
        }
    }
}
