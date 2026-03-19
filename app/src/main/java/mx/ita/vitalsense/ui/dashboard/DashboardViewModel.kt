package mx.ita.vitalsense.ui.dashboard

import android.app.Application
<<<<<<< HEAD
import android.content.Context
=======
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
>>>>>>> 5b14bb15ac5277f3be8467bf84e007c83ca41308
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
<<<<<<< HEAD
=======
import kotlinx.coroutines.withTimeoutOrNull
import mx.ita.vitalsense.HealthSensorApp
import mx.ita.vitalsense.MainActivity
import mx.ita.vitalsense.R
import mx.ita.vitalsense.data.model.VitalsData
import mx.ita.vitalsense.data.model.computeAlerts
import mx.ita.vitalsense.data.repository.VitalsRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
>>>>>>> 5b14bb15ac5277f3be8467bf84e007c83ca41308
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

<<<<<<< HEAD
class DashboardViewModel(application: Application) : AndroidViewModel(application) {
=======
class DashboardViewModel(app: Application) : AndroidViewModel(app) {
>>>>>>> 5b14bb15ac5277f3be8467bf84e007c83ca41308

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

    private fun observePatients() {
        viewModelScope.launch {
            repository.observeVitals().collect { result ->
                result.onSuccess {
                    _uiState.value = _uiState.value.copy(currentVitals = it)
                }.onFailure {
                    // Solo log error si realmente importa
                }
            }

            // Seguir escuchando Firebase en background (cuando llegue reemplaza el mock)
            repository.observePatients().collect { result -> applyResult(result) }
        }
    }

    private fun applyResult(result: Result<List<VitalsData>>) {
        _uiState.value = result.fold(
            onSuccess = { patients ->
                val finalList = patients.ifEmpty { TestDataSeeder.mockPatients }
                finalList.forEach { processPatientUpdate(it) }
                DashboardUiState.Success(finalList)
            },
            onFailure = {
                val mock = TestDataSeeder.mockPatients
                mock.forEach { processPatientUpdate(it) }
                DashboardUiState.Success(mock)
            },
        )
    }

    private fun processPatientUpdate(patient: VitalsData) {
        val previous = lastKnownVitals[patient.patientId]
        val hasNewData = previous == null || previous.timestamp != patient.timestamp

        if (hasNewData && patient.heartRate > 0) {
            // Guardar snapshot histórico automáticamente
            if (patient.patientId.isNotEmpty()) {
                repository.saveSnapshot(patient.patientId, patient)
            }

            // Evaluar alertas y notificar
            val alerts = patient.computeAlerts()
            if (alerts.isNotEmpty()) {
                val key = "${patient.patientId}:${patient.timestamp}"
                if (!notifiedPatients.contains(key)) {
                    notifiedPatients.add(key)
                    sendAlertNotification(patient, alerts.first().title)
                }
            }

            lastKnownVitals[patient.patientId] = patient
        }
    }

    private fun sendAlertNotification(patient: VitalsData, alertTitle: String) {
        val ctx = getApplication<Application>()

        // Verificar permiso POST_NOTIFICATIONS en Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) return
        }

        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            ctx, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(ctx, HealthSensorApp.CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("⚠️ Alerta: ${patient.patientName}")
            .setContentText(alertTitle)
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("$alertTitle\nHR: ${patient.heartRate} BPM · Glucosa: ${"%.0f".format(patient.glucose)} mg/dL · SpO₂: ${patient.spo2}%"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(patient.patientId.hashCode(), notification)
    }
}
