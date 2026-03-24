package mx.ita.vitalsense.ui.dashboard

import android.app.Application
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import mx.ita.vitalsense.HealthSensorApp
import mx.ita.vitalsense.MainActivity
import mx.ita.vitalsense.R
import mx.ita.vitalsense.data.model.Medication
import mx.ita.vitalsense.data.model.SleepData
import mx.ita.vitalsense.data.model.VitalsData
import mx.ita.vitalsense.data.model.computeAlerts
import mx.ita.vitalsense.data.repository.VitalsRepository
import mx.ita.vitalsense.data.test.TestDataSeeder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// Sealed interface — keeps Justin's patient-list approach;
// the extra Jonathan fields (isWatchPaired, currentVitals) are in the Success state.
sealed interface DashboardUiState {
    data object Loading : DashboardUiState
    data class Success(
        val patients: List<VitalsData>,
        val vitalsHistory: List<VitalsData> = emptyList(),
        val sleepData: SleepData? = null,
        val medications: List<Medication> = emptyList(),
        val isWatchPaired: Boolean = false,
        val pairedDeviceName: String = "Wearable",
        val currentVitals: VitalsData = VitalsData(),
        val isLoading: Boolean = false,
        val error: String? = null,
    ) : DashboardUiState
    data class Error(val message: String) : DashboardUiState
}

// Convenience extension so Jonathan's UI code can access fields directly
val DashboardUiState.isWatchPaired: Boolean get() =
    if (this is DashboardUiState.Success) isWatchPaired else false
val DashboardUiState.sleepData: SleepData? get() =
    if (this is DashboardUiState.Success) sleepData else null
val DashboardUiState.vitalsHistory: List<VitalsData> get() =
    if (this is DashboardUiState.Success) vitalsHistory else emptyList()
val DashboardUiState.medications: List<Medication> get() =
    if (this is DashboardUiState.Success) medications else emptyList()

class DashboardViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = VitalsRepository()
    private val prefs = app.getSharedPreferences("vitalsense_watch_prefs", Context.MODE_PRIVATE)

    private val lastKnownVitals = mutableMapOf<String, VitalsData>()
    private val notifiedPatients = mutableSetOf<String>()

    private val _uiState = MutableStateFlow<DashboardUiState>(DashboardUiState.Loading)
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    /**
     * Emite un VitalsData cuando la IA detecta una anomalía crítica que requiere
     * mostrar el QR de emergencia. Buffer = 1 para no perder el evento si la UI
     * aún no está suscrita.
     */
    private val _emergencyTrigger = MutableSharedFlow<VitalsData>(extraBufferCapacity = 1)
    val emergencyTrigger: SharedFlow<VitalsData> = _emergencyTrigger.asSharedFlow()

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance()

    init {
        observePatients()
        loadAdditionalData()
        // Ensure paired state is always up to date
        val isPaired = prefs.getBoolean("code_paired", false)
        viewModelScope.launch {
            val current = _uiState.value
            if (current is DashboardUiState.Success && current.isWatchPaired != isPaired) {
                _uiState.value = current.copy(isWatchPaired = isPaired)
            }
        }
    }

    fun disconnectWatch() {
        viewModelScope.launch {
            try {
                prefs.edit().putBoolean("code_paired", false).apply()
                prefs.edit().remove("paired_device_name").apply()
                val userId = auth.currentUser?.uid
                if (userId != null) {
                    db.getReference("vitals/current/$userId").removeValue()
                }
                val current = _uiState.value
                if (current is DashboardUiState.Success) {
                    _uiState.value = current.copy(isWatchPaired = false, currentVitals = VitalsData())
                }
            } catch (e: Exception) {
                val current = _uiState.value
                if (current is DashboardUiState.Success) {
                    _uiState.value = current.copy(error = "Error al desvincular: ${e.message}")
                }
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
                val vitalsSnapshot = db.getReference("patients").limitToFirst(1).get().await()
                val patientId = vitalsSnapshot.children.firstOrNull()?.key
                val historyList = mutableListOf<VitalsData>()
                if (patientId != null) {
                    val historySnapshot = db.getReference("patients/$patientId/history").limitToLast(10).get().await()
                    historySnapshot.children.forEach {
                        it.getValue(VitalsData::class.java)?.let { v -> historyList.add(v) }
                    }
                }

                // 3. Medications
                val medsSnapshot = db.getReference("medications/$userId").get().await()
                val medsList = medsSnapshot.children.mapNotNull {
                    it.getValue(Medication::class.java)
                }.filter { it.activo }

                val current = _uiState.value
                _uiState.value = when (current) {
                    is DashboardUiState.Success -> current.copy(
                        sleepData = sleep,
                        vitalsHistory = historyList,
                        medications = medsList,
                    )
                    else -> DashboardUiState.Success(
                        patients = emptyList(),
                        sleepData = sleep,
                        vitalsHistory = historyList,
                        medications = medsList,
                        isWatchPaired = prefs.getBoolean("code_paired", false),
                    )
                }
            } catch (e: Exception) {
                // Non-fatal: additional data failed, keep existing state
            }
        }
    }

    private fun observePatients() {
        viewModelScope.launch {
            // Si Firebase no responde en 5s, cargamos datos mock y seguimos esperando en background
            val firstEmit = withTimeoutOrNull(5_000) {
                repository.observePatients().collect { result ->
                    applyResult(result)
                    return@collect // sale del collect en cuanto llega el primer valor
                }
            }
            if (firstEmit == null) {
                // Timeout: Firebase sin respuesta → mostrar lista vacía
                _uiState.value = DashboardUiState.Success(
                    patients = emptyList(),
                    isWatchPaired = prefs.getBoolean("code_paired", false),
                    pairedDeviceName = prefs.getString("paired_device_name", "Wearable") ?: "Wearable",
                )
            }

            // Seguir escuchando Firebase en background (cuando llegue reemplaza el mock)
            repository.observePatients().collect { result -> applyResult(result) }
        }
        // Observar datos de sueño en tiempo real para el día actual
        viewModelScope.launch {
            val userId = auth.currentUser?.uid ?: return@launch
            val dateKey = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            val sleepRef = db.getReference("sleep/$userId/$dateKey")

            sleepRef.addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    val sleep = snapshot.getValue(SleepData::class.java)
                    val current = _uiState.value
                    if (current is DashboardUiState.Success) {
                        _uiState.value = current.copy(sleepData = sleep)
                    }
                }
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
            })
        }
    }

    private fun applyResult(result: Result<List<VitalsData>>) {
        val current = _uiState.value
        val (sleep, history, meds) = when (current) {
            is DashboardUiState.Success -> Triple(current.sleepData, current.vitalsHistory, current.medications)
            else -> Triple(null, emptyList(), emptyList())
        }
        val isWatchPaired = prefs.getBoolean("code_paired", false)
        val deviceName = prefs.getString("paired_device_name", "Wearable") ?: "Wearable"
        _uiState.value = result.fold(
            onSuccess = { patients ->
                patients.forEach { processPatientUpdate(it) }
                DashboardUiState.Success(patients, history, sleep, meds, isWatchPaired, deviceName)
            },
            onFailure = { e ->
                DashboardUiState.Error(e.message ?: "Error al cargar pacientes")
            },
        )
    }

    private fun processPatientUpdate(patient: VitalsData) {
        val previous = lastKnownVitals[patient.patientId]
        val hasNewData = previous == null || previous.timestamp != patient.timestamp

        if (hasNewData && patient.heartRate > 0) {
            if (patient.patientId.isNotEmpty()) {
                repository.saveSnapshot(patient.patientId, patient)
            }
            val alerts = patient.computeAlerts()
            if (alerts.isNotEmpty()) {
                val key = "${patient.patientId}:${patient.timestamp}"
                if (!notifiedPatients.contains(key)) {
                    notifiedPatients.add(key)
                    // Only alert if we already had previous data, avoiding alerts on initial load
                    if (!patient.patientId.startsWith("demo_") && previous != null) {
                        sendAlertNotification(patient, alerts.first().title)
                    }
                }
            }
            // Anomalía crítica → disparar pantalla de QR de emergencia
            if (patient.isCriticalEmergency()) {
                _emergencyTrigger.tryEmit(patient)
            }
            lastKnownVitals[patient.patientId] = patient
        }
    }

    /**
     * Umbrales críticos que justifican mostrar el QR de emergencia.
     * Son más extremos que los umbrales de notificación para evitar falsos positivos.
     *   - Taquicardia severa  : HR > 130 BPM
     *   - Bradicardia severa  : HR entre 1 y 39 BPM
     *   - Hipoxia crítica     : SpO2 entre 1 % y 84 %
     *   - Hiperglucemia grave : Glucosa > 300 mg/dL
     */
    private fun VitalsData.isCriticalEmergency(): Boolean =
        heartRate > 130 ||
        heartRate in 1..39 ||
        (spo2 in 1..84) ||
        glucose > 300.0

    private fun sendAlertNotification(patient: VitalsData, alertTitle: String) {
        val ctx = getApplication<Application>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) return
        }

        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("vitalsense://notifications"), ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            ctx, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(ctx, HealthSensorApp.CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("\u26A0\uFE0F Alerta: ${patient.patientName}")
            .setContentText(alertTitle)
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("$alertTitle\nHR: ${patient.heartRate} BPM \u00B7 Glucosa: ${"%.0f".format(patient.glucose)} mg/dL \u00B7 SpO\u2082: ${patient.spo2}%"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(patient.patientId.hashCode(), notification)
    }
}
