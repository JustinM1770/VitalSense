package mx.ita.vitalsense.ui.reports

import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import mx.ita.vitalsense.data.model.SleepData
import mx.ita.vitalsense.data.model.VitalsData
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

enum class ReportRangeFilter(val label: String) {
    TODAY("Hoy"),
    YESTERDAY("Ayer"),
    LAST_7_DAYS("7 dias"),
    THIS_MONTH("Mes"),
    SELECTED_DAY("Dia")
}

data class DailyReportUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val selectedFilter: ReportRangeFilter = ReportRangeFilter.TODAY,
    val sleepData: SleepData? = null,
    val vitalsHistory: List<VitalsData> = emptyList(),
    val isLoading: Boolean = false
)

class DailyReportViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance()
    private var sleepRef: com.google.firebase.database.Query? = null
    private var historyRef: com.google.firebase.database.Query? = null
    private var sleepListener: ValueEventListener? = null
    private var historyListener: ValueEventListener? = null
    private var pendingLoads: Int = 0
    
    private val _uiState = MutableStateFlow(DailyReportUiState())
    val uiState: StateFlow<DailyReportUiState> = _uiState

    init {
        observeDataForSelection()
    }

    fun onDateSelected(date: LocalDate) {
        _uiState.value = _uiState.value.copy(
            selectedDate = date,
            selectedFilter = ReportRangeFilter.SELECTED_DAY,
        )
        observeDataForSelection()
    }

    fun onFilterSelected(filter: ReportRangeFilter) {
        val selectedDate = when (filter) {
            ReportRangeFilter.TODAY -> LocalDate.now()
            ReportRangeFilter.YESTERDAY -> LocalDate.now().minusDays(1)
            else -> _uiState.value.selectedDate
        }
        _uiState.value = _uiState.value.copy(
            selectedFilter = filter,
            selectedDate = selectedDate,
        )
        observeDataForSelection()
    }

    fun getRangeLabel(state: DailyReportUiState): String {
        return when (state.selectedFilter) {
            ReportRangeFilter.TODAY -> "Hoy"
            ReportRangeFilter.YESTERDAY -> "Ayer"
            ReportRangeFilter.LAST_7_DAYS -> "Ultimos 7 dias"
            ReportRangeFilter.THIS_MONTH -> {
                val start = state.selectedDate.withDayOfMonth(1)
                start.format(DateTimeFormatter.ofPattern("MMMM yyyy"))
            }
            ReportRangeFilter.SELECTED_DAY -> {
                state.selectedDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
            }
        }
    }

    private fun observeDataForSelection() {
        val state = _uiState.value
        val (startDate, endDate) = resolveRange(state.selectedFilter, state.selectedDate)
        observeDataForRange(startDate, endDate)
    }

    private fun resolveRange(filter: ReportRangeFilter, selectedDate: LocalDate): Pair<LocalDate, LocalDate> {
        return when (filter) {
            ReportRangeFilter.TODAY -> Pair(LocalDate.now(), LocalDate.now())
            ReportRangeFilter.YESTERDAY -> {
                val yesterday = LocalDate.now().minusDays(1)
                Pair(yesterday, yesterday)
            }
            ReportRangeFilter.LAST_7_DAYS -> Pair(LocalDate.now().minusDays(6), LocalDate.now())
            ReportRangeFilter.THIS_MONTH -> {
                val start = LocalDate.now().withDayOfMonth(1)
                val end = LocalDate.now()
                Pair(start, end)
            }
            ReportRangeFilter.SELECTED_DAY -> Pair(selectedDate, selectedDate)
        }
    }

    private fun observeDataForRange(startDate: LocalDate, endDate: LocalDate) {
        val userId = auth.currentUser?.uid ?: return
        val startKey = startDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val endKey = endDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

        sleepListener?.let { sleepRef?.removeEventListener(it) }
        historyListener?.let { historyRef?.removeEventListener(it) }

        pendingLoads = 2
        _uiState.value = _uiState.value.copy(isLoading = true)

        sleepRef = db.getReference("sleep/$userId")
            .orderByKey()
            .startAt(startKey)
            .endAt(endKey)

        sleepListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val sleepList = snapshot.children.mapNotNull { it.getValue(SleepData::class.java) }
                val sleep = mergeSleepData(sleepList)
                _uiState.value = _uiState.value.copy(
                    sleepData = sleep,
                )
                markLoadComplete()
            }

            override fun onCancelled(error: DatabaseError) {
                markLoadComplete()
            }
        }
        sleepRef?.addValueEventListener(sleepListener!!)

        val zone = ZoneId.systemDefault()
        val startMillis = startDate.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMillis = endDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1

        historyRef = db.getReference("patients/$userId/history")
            .orderByChild("timestamp")
            .startAt(startMillis.toDouble())
            .endAt(endMillis.toDouble())

        historyListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val history = snapshot.children.mapNotNull { child ->
                    val vitals = child.getValue(VitalsData::class.java)
                    if (vitals != null) {
                        vitals.copy(patientId = userId)
                    } else {
                        val hr = child.child("heartRate").getValue(Int::class.java) ?: return@mapNotNull null
                        val glucose = child.child("glucose").getValue(Double::class.java) ?: 0.0
                        val spo2 = child.child("spo2").getValue(Int::class.java) ?: 0
                        val ts = child.child("timestamp").getValue(Long::class.java) ?: 0L
                        VitalsData(patientId = userId, heartRate = hr, glucose = glucose, spo2 = spo2, timestamp = ts)
                    }
                }.sortedBy { it.timestamp }

                _uiState.value = _uiState.value.copy(
                    vitalsHistory = history,
                )
                markLoadComplete()
            }

            override fun onCancelled(error: DatabaseError) {
                markLoadComplete()
            }
        }
        historyRef?.addValueEventListener(historyListener!!)
    }

    private fun mergeSleepData(sleepList: List<SleepData>): SleepData {
        if (sleepList.isEmpty()) {
            return SleepData(score = 0, horas = 0f, estado = "No durmio")
        }

        val avgScore = sleepList.map { it.score }.average().roundToInt().coerceIn(0, 100)
        val avgHours = sleepList.map { it.horas.toDouble() }.average().toFloat().coerceAtLeast(0f)
        val status = when {
            avgScore >= 85 -> "Excelente"
            avgScore >= 70 -> "Bueno"
            avgScore >= 50 -> "Regular"
            else -> "Malo"
        }

        return SleepData(
            score = avgScore,
            horas = avgHours,
            estado = status,
        )
    }

    private fun markLoadComplete() {
        pendingLoads = (pendingLoads - 1).coerceAtLeast(0)
        if (pendingLoads == 0) {
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    override fun onCleared() {
        super.onCleared()
        sleepListener?.let { sleepRef?.removeEventListener(it) }
        historyListener?.let { historyRef?.removeEventListener(it) }
    }
    
    fun scoreToGrade(score: Int) = when {
        score >= 90 -> "A+"
        score >= 80 -> "A"
        score >= 70 -> "B+"
        score >= 60 -> "B"
        else -> "C"
    }
}
