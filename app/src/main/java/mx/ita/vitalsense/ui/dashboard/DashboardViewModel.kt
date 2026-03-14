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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import mx.ita.vitalsense.HealthSensorApp
import mx.ita.vitalsense.MainActivity
import mx.ita.vitalsense.R
import mx.ita.vitalsense.data.model.VitalsData
import mx.ita.vitalsense.data.model.computeAlerts
import mx.ita.vitalsense.data.repository.VitalsRepository
import mx.ita.vitalsense.data.test.TestDataSeeder

sealed interface DashboardUiState {
    data object Loading : DashboardUiState
    data class Success(val patients: List<VitalsData>) : DashboardUiState
    data class Error(val message: String) : DashboardUiState
}

class DashboardViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = VitalsRepository()

    private val _uiState = MutableStateFlow<DashboardUiState>(DashboardUiState.Loading)
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    // Rastreo de qué pacientes ya recibieron notificación para no repetir
    private val notifiedPatients = mutableSetOf<String>()
    // Último estado conocido para detectar cambios reales
    private val lastKnownVitals = mutableMapOf<String, VitalsData>()

    init {
        observePatients()
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
                // Timeout: Firebase sin respuesta → mostrar mock inmediatamente
                val mock = TestDataSeeder.mockPatients
                mock.forEach { processPatientUpdate(it) }
                _uiState.value = DashboardUiState.Success(mock)
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
