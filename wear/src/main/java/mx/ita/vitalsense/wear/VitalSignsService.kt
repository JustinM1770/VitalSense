package mx.ita.vitalsense.wear

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.Manifest
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class VitalSignsService : Service() {
    companion object {
        private const val ACTION_CANCEL_SOS = "mx.ita.vitalsense.wear.action.CANCEL_SOS"
        private const val EXTRA_SOS_ID = "extra_sos_id"
        private const val EXTRA_SOS_USER_ID = "extra_sos_user_id"
    }

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var hrManager: HeartRateManager
    private lateinit var spo2Manager: SpO2Manager
    private val database = FirebaseDatabase.getInstance("https://vitalsenseai-1cb9f-default-rtdb.firebaseio.com")
    
    private val _currentHeartRate = MutableStateFlow(0.0)
    val currentHeartRate: StateFlow<Double> = _currentHeartRate.asStateFlow()

    private val _currentSpO2 = MutableStateFlow(0)
    val currentSpO2: StateFlow<Int> = _currentSpO2.asStateFlow()

    private val _activeSosId = MutableStateFlow<String?>(null)
    val activeSosId: StateFlow<String?> = _activeSosId.asStateFlow()

    private var syncJob: Job? = null
    private var spo2Job: Job? = null
    private var sleepJob: Job? = null

    private var wakeLock: PowerManager.WakeLock? = null
    private var shakeDetector: ShakeDetector? = null

    inner class LocalBinder : Binder() {
        fun getService(): VitalSignsService = this@VitalSignsService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        hrManager = HeartRateManager(this)
        spo2Manager = SpO2Manager(this)
        createNotificationChannel()

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "VitalSense:BackgroundSyncWakeLock")
        wakeLock?.acquire()

        shakeDetector = ShakeDetector(this) {
            triggerSosAlert()
        }
        shakeDetector?.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL_SOS) {
            val sosId = intent.getStringExtra(EXTRA_SOS_ID).orEmpty()
            val userId = intent.getStringExtra(EXTRA_SOS_USER_ID).orEmpty()
            if (sosId.isNotBlank() && userId.isNotBlank()) {
                resolveSosFromNotification(sosId = sosId, userId = userId)
            }
            return START_STICKY
        }

        val notification = createNotification()
        startForeground(1, notification)
        
        val prefs = getSharedPreferences("vitalsense_wear_prefs", Context.MODE_PRIVATE)
        val userId = prefs.getString("user_id", "global") ?: "global"
        
        startHrMonitoring(userId)
        startSpO2Monitoring(userId)
        // La sincronización de sueño fue removida de aquí.
        // Ahora la aplicación del teléfono se encarga de leer los datos reales
        // del sueño a través de Health Connect.
        
        return START_STICKY
    }

    private fun startHrMonitoring(userId: String) {
        syncJob?.cancel()
        syncJob = serviceScope.launch {
            hrManager.observeHeartRate().collect { hr ->
                _currentHeartRate.value = hr
                database.getReference("vitals/current").child(userId)
                    .updateChildren(mapOf("heartRate" to hr.toInt(), "timestamp" to System.currentTimeMillis()))
            }
        }
    }

    private fun startSpO2Monitoring(userId: String) {
        spo2Job?.cancel()

        if (!spo2Manager.isSupported()) {
            Log.w("VitalSignsService", "SpO2 sensor not supported on this watch")
            return
        }

        spo2Job = serviceScope.launch {
            spo2Manager.observeSpO2().collect { spo2 ->
                _currentSpO2.value = spo2
                database.getReference("vitals/current").child(userId)
                    .updateChildren(mapOf("spo2" to spo2, "timestamp" to System.currentTimeMillis()))
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("vitals_channel", "Monitoreo VitalSense", NotificationManager.IMPORTANCE_LOW)
            val sosChannel = NotificationChannel(
                "sos_channel",
                "Alertas SOS",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Alertas SOS activadas desde el reloj"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
            manager.createNotificationChannel(sosChannel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, "vitals_channel")
            .setContentTitle("VitalSense")
            .setContentText("Monitoreando salud en tiempo real...")
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        shakeDetector?.stop()
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
    }

    fun triggerSosAlert() {
        if (_activeSosId.value != null) {
            Log.d("VitalSense", "Ignorando SOS: ya hay una alerta activa (${_activeSosId.value})")
            return
        }

        serviceScope.launch {
            val prefs = getSharedPreferences("vitalsense_wear_prefs", Context.MODE_PRIVATE)
            val remoteUid = resolveTargetUserId(prefs)

            if (remoteUid == "global") {
                Log.w("VitalSense", "SOS enviado con user_id global (revisa emparejamiento)")
            }

            withContext(Dispatchers.Main) {
                sendSosWithBestLocation(remoteUid)
            }
        }
    }

    private suspend fun resolveTargetUserId(prefs: android.content.SharedPreferences): String {
        val savedUserId = prefs.getString("user_id", "")?.trim().orEmpty()
        if (savedUserId.isNotBlank() && savedUserId != "global") {
            return savedUserId
        }

        val pairingCode = prefs.getString("pairing_code", "")?.trim().orEmpty()
        if (pairingCode.isBlank()) return "global"

        return runCatching {
            val resolved = database
                .getReference("patients/pairing_codes")
                .child(pairingCode)
                .child("userId")
                .get()
                .await()
                .getValue(String::class.java)
                .orEmpty()
                .trim()

            if (resolved.isNotBlank() && resolved != "global") {
                prefs.edit().putString("user_id", resolved).apply()
                resolved
            } else {
                "global"
            }
        }.getOrDefault("global")
    }

    private fun sendSosWithBestLocation(remoteUid: String) {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            val locationRequest = com.google.android.gms.location.LocationRequest.Builder(
                com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
                1000L,
            ).setMaxUpdates(1)
                .setDurationMillis(10000L)
                .build()

            val locationCallback = object : com.google.android.gms.location.LocationCallback() {
                override fun onLocationResult(result: com.google.android.gms.location.LocationResult) {
                    fusedLocationClient.removeLocationUpdates(this)
                    val loc = result.lastLocation
                    pushSosAlert(remoteUid, loc?.latitude ?: 0.0, loc?.longitude ?: 0.0)
                }
            }

            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                android.os.Looper.getMainLooper(),
            )
        } else {
            pushSosAlert(remoteUid, 0.0, 0.0)
        }
    }

    private fun pushSosAlert(remoteUid: String, lat: Double, lng: Double) {
        val alertRef = database.getReference("alerts").child(remoteUid).push()
        val sosId = alertRef.key ?: System.currentTimeMillis().toString()
        _activeSosId.value = sosId
        alertRef.setValue(
            mapOf(
                "timestamp" to System.currentTimeMillis(),
                "type" to "SOS",
                "lat" to lat,
                "lng" to lng,
                "status" to "active",
                "read" to false,
                "source" to "wear_internet",
            ),
        )

        showWatchSosNotification(
            sosId = sosId,
            remoteUid = remoteUid,
            lat = lat,
            lng = lng,
        )
    }

    private fun showWatchSosNotification(
        sosId: String,
        remoteUid: String,
        lat: Double,
        lng: Double,
    ) {
        val hasLocation = lat != 0.0 && lng != 0.0
        val text = if (hasLocation) {
            "SOS enviado con ubicacion. Esperando respuesta."
        } else {
            "SOS enviado. Ubicacion no disponible."
        }

        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_sos", true)
            putExtra("sos_id", sosId)
            putExtra("sos_user_id", remoteUid)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            sosId.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val cancelIntent = Intent(this, VitalSignsService::class.java).apply {
            action = ACTION_CANCEL_SOS
            putExtra(EXTRA_SOS_ID, sosId)
            putExtra(EXTRA_SOS_USER_ID, remoteUid)
        }
        val cancelPendingIntent = PendingIntent.getService(
            this,
            sosId.hashCode() + 10_000,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, "sos_channel")
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Alerta SOS enviada")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Cancelar SOS",
                cancelPendingIntent,
            )
            .setAutoCancel(true)
            .build()

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(sosId.hashCode(), notification)
    }

    private fun resolveSosFromNotification(sosId: String, userId: String) {
        serviceScope.launch {
            runCatching {
                database.getReference("alerts")
                    .child(userId)
                    .child(sosId)
                    .updateChildren(
                        mapOf(
                            "status" to "resolved",
                            "read" to true,
                            "resolvedBy" to "wear_cancel_action",
                            "resolvedAt" to System.currentTimeMillis(),
                        ),
                    )
                    .await()

                withContext(Dispatchers.Main) {
                    _activeSosId.value = null
                    val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    nm.cancel(sosId.hashCode())
                }
            }.onFailure {
                Log.e("VitalSense", "No se pudo cancelar SOS desde notificacion", it)
            }
        }
    }

    fun clearSos() {
        _activeSosId.value = null
    }
}
