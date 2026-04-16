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
import mx.ita.vitalsense.data.model.MedicalProfile
import mx.ita.vitalsense.data.model.Medication
import mx.ita.vitalsense.data.model.PatientThresholds
import mx.ita.vitalsense.data.model.RapidDegradationEvent
import mx.ita.vitalsense.data.model.SleepData
import mx.ita.vitalsense.data.model.VitalsData
import mx.ita.vitalsense.data.model.VitalsSnapshot
import mx.ita.vitalsense.data.model.computeAlerts
import mx.ita.vitalsense.data.model.computePersonalizedThresholds
import mx.ita.vitalsense.data.model.detectRapidDegradation
import mx.ita.vitalsense.data.repository.VitalsRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.time.ZoneId
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

    private val lastKnownVitals  = mutableMapOf<String, VitalsData>()
    private val notifiedPatients = mutableSetOf<String>()
    private val lastSavedHrSampleTsByUser = mutableMapOf<String, Long>()
    private var medicationsListener: com.google.firebase.database.ValueEventListener? = null
    private var medicationsRef: com.google.firebase.database.DatabaseReference? = null

    /**
     * Umbrales clínicos del paciente autenticado, personalizados por edad y diagnósticos.
     * Se inicializan con los valores estándar AHA/ADA/WHO y se actualizan al cargar el perfil.
     */
    @Volatile
    private var patientThresholds = PatientThresholds()

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
    private val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        firebaseAuth.currentUser?.uid?.let { userId ->
            ensureDashboardLoaded(userId)
        }
    }
    private var loadedUserId: String? = null

    init {
        auth.addAuthStateListener(authListener)
        auth.currentUser?.uid?.let { userId ->
            ensureDashboardLoaded(userId)
        }
        // Ensure paired state is always up to date
        val isPaired = prefs.getBoolean("code_paired", false)
        viewModelScope.launch {
            val current = _uiState.value
            if (current is DashboardUiState.Success && current.isWatchPaired != isPaired) {
                _uiState.value = current.copy(isWatchPaired = isPaired)
            }
        }
    }

    /**
     * Carga el perfil médico del paciente desde Firebase y calcula los umbrales
     * personalizados (edad, EPOC, diabetes, etc.) según AHA/ADA/BTS/WHO.
     * Opera en background; si falla se mantienen los umbrales estándar.
     */
    private fun loadPatientProfile(userId: String) {
        viewModelScope.launch {
            try {
                val snap = db.getReference("users/$userId/datosMedicos").get().await()
                val profile = snap.getValue(MedicalProfile::class.java)
                if (profile != null) {
                    patientThresholds = profile.computePersonalizedThresholds()
                }
            } catch (_: Exception) {
                // Mantener umbrales estándar si el perfil no está disponible
            }
        }
    }

    private fun ensureDashboardLoaded(userId: String) {
        if (loadedUserId == userId) return
        loadedUserId = userId
        loadPatientProfile(userId)
        observePatients(userId)
        loadAdditionalData(userId)
    }

    fun disconnectWatch() {
        viewModelScope.launch {
            try {
                val userId = auth.currentUser?.uid
                val pairedCode = prefs.getString("paired_code", null)

                prefs.edit()
                    .putBoolean("code_paired", false)
                    .remove("paired_code")
                    .remove("paired_device_name")
                    .apply()

                if (userId != null) {
                    db.getReference("patients/$userId/watch").removeValue().await()
                    db.getReference("vitals/current/$userId").removeValue()
                }

                if (!pairedCode.isNullOrBlank()) {
                    db.getReference("patients/pairing_codes").child(pairedCode)
                        .updateChildren(mapOf("paired" to false))
                        .await()
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

    private fun loadAdditionalData(userId: String) {
        val today = LocalDate.now()
        val dateKey = today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
        val yesterdayKey = today.minusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

        viewModelScope.launch {
            try {
                // 1. Sleep data (hoy + ayer para evitar falsos "No durmió" por desfase de fecha)
                val sleepSnapshot = db.getReference("sleep/$userId")
                    .orderByKey()
                    .startAt(yesterdayKey)
                    .endAt(dateKey)
                    .get()
                    .await()
                val sleepByDate = sleepSnapshot.children.associate { it.key.orEmpty() to it.getValue(SleepData::class.java) }
                val sleep = selectLatestSleepData(sleepByDate)

                // 2. Vitals History (últimos 30 días calendario para soportar resumen 24h/7d/30d)
                val zone = ZoneId.systemDefault()
                val startDate = LocalDate.now(zone).minusDays(29)
                val endDate = LocalDate.now(zone)
                val startMillis = startDate.atStartOfDay(zone).toInstant().toEpochMilli()
                val endMillis = endDate.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
                val historyList = mutableListOf<VitalsData>()

                val historySnapshot = db.getReference("patients/$userId/history")
                    .orderByChild("timestamp")
                    .startAt(startMillis.toDouble())
                    .endAt(endMillis.toDouble())
                    .get()
                    .await()

                historySnapshot.children.forEach { child ->
                    val vitals = child.getValue(VitalsData::class.java)
                    if (vitals != null) {
                        historyList.add(vitals.copy(patientId = userId))
                    } else {
                        val hr = child.child("heartRate").getValue(Number::class.java)?.toInt() ?: 0
                        val glucose = child.child("glucose").getValue(Number::class.java)?.toDouble() ?: 0.0
                        val spo2 = child.child("spo2").getValue(Number::class.java)?.toInt() ?: 0
                        val ts = child.child("timestamp").getValue(Number::class.java)?.toLong() ?: 0L
                        if (ts > 0L) {
                            historyList.add(
                                VitalsData(
                                    patientId = userId,
                                    heartRate = hr,
                                    glucose = glucose,
                                    spo2 = spo2,
                                    timestamp = ts,
                                ),
                            )
                        }
                    }
                }
                val compactedHistory = historyList
                    .distinctBy {
                        val minuteBucket = it.timestamp / 60_000L
                        Triple(minuteBucket, it.heartRate, it.spo2)
                    }
                    .sortedBy { it.timestamp }

                val current = _uiState.value
                _uiState.value = when (current) {
                    is DashboardUiState.Success -> current.copy(
                        sleepData = sleep ?: current.sleepData,
                        vitalsHistory = compactedHistory,
                    )
                    else -> DashboardUiState.Success(
                        patients = emptyList(),
                        sleepData = sleep,
                        vitalsHistory = compactedHistory,
                        isWatchPaired = prefs.getBoolean("code_paired", false),
                    )
                }

                medicationsRef?.removeEventListener(medicationsListener ?: return@launch)
                medicationsRef = db.getReference("medications/$userId")
                medicationsListener = object : com.google.firebase.database.ValueEventListener {
                    override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                        val medsList = snapshot.children.mapNotNull { child ->
                            child.getValue(Medication::class.java)?.copy(id = child.key ?: "")
                        }.filter { med ->
                            med.nombre.isNotBlank() && (med.activo || med.reminderEnabled || med.nextReminderAt > 0L)
                        }.sortedByDescending { it.createdAt }

                        val currentState = _uiState.value
                        if (currentState is DashboardUiState.Success) {
                            _uiState.value = currentState.copy(medications = medsList)
                        }
                    }

                    override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
                }
                medicationsRef?.addValueEventListener(medicationsListener!!)
            } catch (e: Exception) {
                // Non-fatal: additional data failed, keep existing state
            }
        }
    }

    private fun observePatients(userId: String) {
        viewModelScope.launch {
            val firstEmit = withTimeoutOrNull(5_000) {
                repository.observePatients().collect { result ->
                    applyResult(result)
                    return@collect
                }
            }
            if (firstEmit == null) {
                _uiState.value = DashboardUiState.Success(
                    patients = emptyList(),
                    isWatchPaired = prefs.getBoolean("code_paired", false),
                    pairedDeviceName = prefs.getString("paired_device_name", "Wearable") ?: "Wearable",
                )
            }

            // Seguir escuchando Firebase en background (cuando llegue reemplaza la lista vacía)
            repository.observePatients().collect { result -> applyResult(result) }
        }
        
        // Observar datos del reloj en vitals/current/<userId>
        viewModelScope.launch {
            val vitalsRef = db.getReference("vitals/current/$userId")
            
            vitalsRef.addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    try {
                        val vitals = snapshot.getValue(VitalsData::class.java) ?: return
                        val hrSampleTs = snapshot.child("heartRateSampleTimestamp").getValue(Long::class.java)
                            ?: vitals.timestamp
                        val previousSampleTs = lastSavedHrSampleTsByUser[userId] ?: 0L
                        val shouldSaveHrSnapshot = vitals.heartRate > 0 && hrSampleTs > previousSampleTs

                        if (shouldSaveHrSnapshot) {
                            // Sincronizar al historial solo cuando llegue una nueva muestra real de HR
                            repository.saveSnapshot(userId, vitals.copy(patientId = userId, timestamp = hrSampleTs))
                            lastSavedHrSampleTsByUser[userId] = hrSampleTs

                            // Agregar al historial actual en memoria
                            val current = _uiState.value
                            if (current is DashboardUiState.Success) {
                                val updated = current.vitalsHistory.toMutableList()
                                updated.add(vitals.copy(patientId = userId, timestamp = hrSampleTs))
                                // Mantener solo últimas 100 entradas
                                val trimmed = if (updated.size > 100) updated.drop(updated.size - 100) else updated
                                _uiState.value = current.copy(vitalsHistory = trimmed)
                            }
                        }
                    } catch (e: Exception) {
                        // Ignorar errores de parseo
                    }
                }
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
            })
        }
        
        // Observar datos de sueño en tiempo real para el día actual
        viewModelScope.launch {
            val today = LocalDate.now()
            val dateKey = today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            val yesterdayKey = today.minusDays(1).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            val sleepRef = db.getReference("sleep/$userId")
                .orderByKey()
                .startAt(yesterdayKey)
                .endAt(dateKey)

            sleepRef.addValueEventListener(object : com.google.firebase.database.ValueEventListener {
                override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                    val sleepByDate = snapshot.children.associate {
                        it.key.orEmpty() to it.getValue(SleepData::class.java)
                    }
                    val sleep = selectLatestSleepData(sleepByDate)
                    val current = _uiState.value
                    if (current is DashboardUiState.Success) {
                        if (sleep != null && sleep.hasSleep) {
                            _uiState.value = current.copy(sleepData = sleep)
                        }
                    }
                }
                override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
            })
        }
    }

    private fun applyResult(result: Result<List<VitalsData>>) {
        val current = _uiState.value
        val currentSuccess = current as? DashboardUiState.Success
        val sleep = currentSuccess?.sleepData
        val history = currentSuccess?.vitalsHistory.orEmpty()
        val meds = currentSuccess?.medications.orEmpty()
        val isWatchPaired = prefs.getBoolean("code_paired", false)
        val deviceName = prefs.getString("paired_device_name", "Wearable") ?: "Wearable"
        _uiState.value = result.fold(
            onSuccess = { patients ->
                patients.forEach { processPatientUpdate(it) }
                DashboardUiState.Success(patients, history, sleep, meds, isWatchPaired, deviceName)
            },
            onFailure = { e ->
                if (currentSuccess != null) {
                    currentSuccess.copy(error = e.message ?: "Error al cargar pacientes")
                } else {
                    DashboardUiState.Error(e.message ?: "Error al cargar pacientes")
                }
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

            // Evaluar alertas con umbrales personalizados del paciente
            val alerts = patient.computeAlerts(patientThresholds)
            if (alerts.isNotEmpty()) {
                val key = "${patient.patientId}:${patient.timestamp}"
                if (!notifiedPatients.contains(key)) {
                    notifiedPatients.add(key)
                    if (previous != null) {
                        sendAlertNotification(patient, alerts.first().title)
                    }
                }
            }

            // Detectar deterioro agudo en el historial reciente (p. ej. caída SpO₂ > 5 % en 10 min)
            val currentState = _uiState.value
            if (currentState is DashboardUiState.Success) {
                val historySnapshots = currentState.vitalsHistory.map { v ->
                    VitalsSnapshot(v.heartRate, v.glucose, v.spo2, v.timestamp)
                }
                val degradation = historySnapshots.detectRapidDegradation()
                if (degradation != null && previous != null &&
                    patient.patientId.isNotBlank()) {
                    sendAlertNotification(patient, degradation.message)
                }
            }

            // Emergencia crítica → mostrar QR (umbrales personalizados + hipoglucemia severa)
            if (patient.isCriticalEmergency(patientThresholds)) {
                _emergencyTrigger.tryEmit(patient)
            }
            lastKnownVitals[patient.patientId] = patient
        }
    }

    /**
     * Prioriza hoy cuando tiene horas válidas; si no, usa ayer para reflejar el sueño nocturno.
     */
    private fun selectRecentSleepData(today: SleepData?, yesterday: SleepData?): SleepData? {
        return when {
            today?.hasSleep == true -> today
            yesterday?.hasSleep == true -> yesterday
            else -> null
        }
    }

    private fun selectLatestSleepData(sleepMap: Map<String, SleepData?>): SleepData? {
        return sleepMap.entries
            .sortedByDescending { it.key }
            .mapNotNull { (_, value) -> value?.takeIf { it.hasSleep } }
            .firstOrNull()
    }

    /**
     * Determina si los signos vitales requieren activar el protocolo de emergencia QR.
     *
     * Umbrales aplicados (personalizados según perfil del paciente):
     * - Taquicardia severa  : FC ≥ umbral ESC 2019 (default 150 BPM)
     * - Bradicardia severa  : FC < umbral ESC 2019 (default 40 BPM)
     * - Hipoxemia crítica   : SpO₂ ≤ umbral WHO 2011 (default 85 %)
     * - Hipoglucemia L2     : Glucosa < 54 mg/dL (ADA 2024 — riesgo de inconsciencia)
     * - Hiperglucemia crisis: Glucosa > umbral ADA 2024 (default 300 mg/dL — riesgo CAD/SHH)
     */
    private fun VitalsData.isCriticalEmergency(thresholds: PatientThresholds): Boolean =
        (heartRate >= thresholds.hrTachycardiaSevere) ||
        (heartRate in 1 until thresholds.hrBradycardiaSevere) ||
        (spo2 in 1..thresholds.spo2HypoxemiaCritical) ||
        (glucose > 0.0 && glucose < thresholds.glucoseHypoL2) ||
        (glucose >= thresholds.glucoseHyperCrisis)

    override fun onCleared() {
        medicationsListener?.let { medicationsRef?.removeEventListener(it) }
        auth.removeAuthStateListener(authListener)
        super.onCleared()
    }

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
