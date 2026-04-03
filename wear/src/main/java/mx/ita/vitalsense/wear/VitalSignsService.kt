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
import androidx.health.services.client.HealthServices
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
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

    // Conservative default to avoid false negatives on OEMs that expose SpO2 via proprietary paths.
    private val _isSpO2Supported = MutableStateFlow(true)
    val isSpO2Supported: StateFlow<Boolean> = _isSpO2Supported.asStateFlow()

    private val _activeSosId = MutableStateFlow<String?>(null)
    val activeSosId: StateFlow<String?> = _activeSosId.asStateFlow()

    private var syncJob: Job? = null
    private var spo2Job: Job? = null
    private var sleepJob: Job? = null
    private var medicationJob: Job? = null
    private var keepAliveJob: Job? = null
    private var activeSosRef: DatabaseReference? = null
    private var activeSosListener: ValueEventListener? = null

    private var wakeLock: PowerManager.WakeLock? = null
    private var shakeDetector: ShakeDetector? = null
    private var passiveRegistered = false

    // Last known location — updated continuously in background so SOS always has coords.
    @Volatile private var cachedLat: Double = 0.0
    @Volatile private var cachedLng: Double = 0.0
    private var locationJob: Job? = null

    inner class LocalBinder : Binder() {
        fun getService(): VitalSignsService = this@VitalSignsService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        hrManager = HeartRateManager(this)
        spo2Manager = SpO2Manager(this)
        val supportsBySensorProbe = spo2Manager.isSupported()
        val supportsBySystemFeature = packageManager.hasSystemFeature("android.hardware.sensor.oxygen_saturation")
        _isSpO2Supported.value = supportsBySensorProbe || supportsBySystemFeature
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
        startMedicationMonitoring(userId)
        observeActiveSosState(userId)
        registerPassiveMonitoring(userId)
        startKeepAliveSync(userId)
        startLocationTracking()  // keep a fresh location cached for instant SOS delivery

        return START_STICKY
    }

    private fun startHrMonitoring(userId: String) {
        syncJob?.cancel()
        syncJob = serviceScope.launch {
            hrManager.observeHeartRate().collect { hr ->
                val sampleTs = System.currentTimeMillis()
                _currentHeartRate.value = hr
                database.getReference("vitals/current").child(userId)
                    .updateChildren(
                        mapOf(
                            "heartRate" to hr.toInt(),
                            "heartRateSampleTimestamp" to sampleTs,
                            "timestamp" to sampleTs,
                        ),
                    )
            }
        }
    }

    private fun startSpO2Monitoring(userId: String) {
        spo2Job?.cancel()
        spo2Job = serviceScope.launch {
            val supported = spo2Manager.isSupported()
            _isSpO2Supported.value = supported
            Log.d("VitalSignsService", "SpO2 supported (HC or Sensor): $supported")

            if (!supported) {
                Log.w("VitalSignsService", "SpO2 not supported on this device")
                return@launch
            }

            spo2Manager.observeSpO2().collect { spo2 ->
                _currentSpO2.value = spo2
                database.getReference("vitals/current").child(userId)
                    .updateChildren(mapOf("spo2" to spo2, "timestamp" to System.currentTimeMillis()))
            }
        }
    }

    /**
     * Registers Wear Health Services PassiveMonitoringClient.
     * This delivers HR and SpO2 data via PassiveDataReceiver even when the
     * watch screen is off and this foreground service is in the background.
     */
    private fun registerPassiveMonitoring(userId: String) {
        if (passiveRegistered) return
        try {
            val config = PassiveDataReceiver.buildPassiveConfig()
            val passiveClient = HealthServices.getClient(this@VitalSignsService).passiveMonitoringClient
            passiveClient.setPassiveListenerServiceAsync(
                PassiveDataReceiver::class.java,
                config,
            ).addListener({
                passiveRegistered = true
                Log.d("VitalSignsService", "PassiveMonitoringClient registered for HR background delivery")
            }, { it.run() })
        } catch (e: Exception) {
            Log.e("VitalSignsService", "Failed to register PassiveMonitoringClient", e)
        }
        // Also start SensorManager listener as a side-channel for OEM SpO2 sensors
        spo2Manager.startSensorListener()
    }

    /**
     * Every 30 seconds, re-uploads the last known HR and SpO2 to Firebase.
     * This acts as a heartbeat so the phone always sees fresh-ish values even
     * if no new sensor event has fired recently.
     */
    private fun startKeepAliveSync(userId: String) {
        keepAliveJob?.cancel()
        keepAliveJob = serviceScope.launch {
            while (isActive) {
                delay(30_000L)
                val hr = _currentHeartRate.value
                val spo2 = _currentSpO2.value
                if (hr > 0 || spo2 > 0) {
                    val updates = mutableMapOf<String, Any>(
                        "timestamp" to System.currentTimeMillis()
                    )
                    if (hr > 0) updates["heartRate"] = hr.toInt()
                    if (spo2 > 0) updates["spo2"] = spo2
                    runCatching {
                        database.getReference("vitals/current/$userId").updateChildren(updates)
                    }
                }
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
            val medicationChannel = NotificationChannel(
                "medications_channel",
                "Recordatorios de medicamentos",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Recordatorios para tomar medicamentos"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
            manager.createNotificationChannel(sosChannel)
            manager.createNotificationChannel(medicationChannel)
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
        locationJob?.cancel()
        // Unregister passive monitoring so it doesn't fire after the service stops.
        if (passiveRegistered) {
            runCatching {
                HealthServices.getClient(this@VitalSignsService)
                    .passiveMonitoringClient
                    .clearPassiveListenerServiceAsync()
                    .addListener({ Log.d("VitalSignsService", "PassiveMonitoringClient cleared") }, { it.run() })
            }
        }
        serviceScope.cancel()
        shakeDetector?.stop()
        activeSosListener?.let { listener ->
            activeSosRef?.removeEventListener(listener)
        }
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

            if (hasBlockingEmergency(remoteUid)) {
                Log.d("VitalSense", "SOS bloqueado: ya existe una alerta activa para $remoteUid")
                return@launch
            }

            withContext(Dispatchers.Main) {
                sendSosWithBestLocation(remoteUid)
            }
        }
    }

    private fun startMedicationMonitoring(userId: String) {
        medicationJob?.cancel()
        if (userId.isBlank() || userId == "global") return

        medicationJob = serviceScope.launch {
            val prefs = getSharedPreferences("vitalsense_wear_prefs", Context.MODE_PRIVATE)
            while (isActive) {
                runCatching {
                    val snapshot = database.getReference("medications/$userId").get().await()
                    val now = System.currentTimeMillis()
                    snapshot.children.forEach { child ->
                        val id = child.key.orEmpty()
                        val name = child.child("nombre").getValue(String::class.java).orEmpty()
                        val active = child.child("activo").getValue(Boolean::class.java) ?: true
                        val reminderEnabled = child.child("reminderEnabled").getValue(Boolean::class.java) ?: true
                        val nextReminderAt = child.child("nextReminderAt").getValue(Long::class.java) ?: 0L
                        if (!active || !reminderEnabled || id.isBlank() || name.isBlank()) return@forEach

                        val lastNotified = prefs.getLong("med_reminder_notified_$id", 0L)
                        if (nextReminderAt > 0L && now >= nextReminderAt && nextReminderAt > lastNotified) {
                            notifyMedicationReminder(name, child.child("dosis").getValue(String::class.java).orEmpty())
                            prefs.edit().putLong("med_reminder_notified_$id", nextReminderAt).apply()
                        }
                    }
                }
                delay(60_000L)
            }
        }
    }

    private fun observeActiveSosState(userId: String) {
        if (userId.isBlank() || userId == "global") {
            _activeSosId.value = null
            return
        }

        activeSosListener?.let { listener ->
            activeSosRef?.removeEventListener(listener)
        }

        val ref = database.getReference("alerts").child(userId)
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                var foundId: String? = null
                for (child in snapshot.children) {
                    val read = child.child("read").getValue(Boolean::class.java) ?: false
                    val status = child.child("status").getValue(String::class.java) ?: ""
                    val type = child.child("type").getValue(String::class.java) ?: ""
                    if (type == "SOS" && !read && status == "active") {
                        foundId = child.key
                        break
                    }
                }
                _activeSosId.value = foundId
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        }

        activeSosRef = ref
        activeSosListener = listener
        ref.addValueEventListener(listener)
    }

    private suspend fun hasBlockingEmergency(userId: String): Boolean {
        if (userId.isBlank() || userId == "global") {
            return false
        }

        val alertsSnapshot = runCatching { database.getReference("alerts").child(userId).get().await() }
            .getOrNull() ?: return false

        for (child in alertsSnapshot.children) {
            val read = child.child("read").getValue(Boolean::class.java) ?: false
            val status = child.child("status").getValue(String::class.java) ?: ""
            val type = child.child("type").getValue(String::class.java) ?: ""
            if (type == "SOS" && !read && status == "active") {
                _activeSosId.value = child.key
                return true
            }
        }

        val emergencySnapshot = runCatching { database.getReference("patients/$userId/activeEmergency").get().await() }
            .getOrNull() ?: return false

        val expiresAt = emergencySnapshot.child("expiresAt").getValue(Long::class.java) ?: 0L
        return expiresAt > System.currentTimeMillis()
    }

    private fun notifyMedicationReminder(name: String, dose: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) return
        }

        val text = if (dose.isBlank()) "Es hora de tomar tu medicamento" else "Dosis: $dose"
        val notification = NotificationCompat.Builder(this, "medications_channel")
            .setContentTitle("Recordatorio: $name")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText("$name\n$text"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(name.hashCode(), notification)
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

    /**
     * Continuously keeps [cachedLat]/[cachedLng] fresh using balanced-power location updates.
     * This ensures SOS always has a location ready even when the screen is off.
     */
    @android.annotation.SuppressLint("MissingPermission")
    private fun startLocationTracking() {
        locationJob?.cancel()
        val hasLocation = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasLocation) {
            Log.w("VitalSignsService", "Location permission not granted — SOS will fire without coords")
            return
        }

        val client = LocationServices.getFusedLocationProviderClient(this)

        // Seed with last known location immediately
        client.lastLocation.addOnSuccessListener { loc ->
            if (loc != null) {
                cachedLat = loc.latitude
                cachedLng = loc.longitude
                Log.d("VitalSignsService", "Seeded location: $cachedLat, $cachedLng")
            }
        }

        // Request periodic updates every 60 s to keep cache fresh
        val locationRequest = com.google.android.gms.location.LocationRequest.Builder(
            com.google.android.gms.location.Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            60_000L,
        ).setMinUpdateIntervalMillis(30_000L)
            .build()

        val locationCallback = object : com.google.android.gms.location.LocationCallback() {
            override fun onLocationResult(result: com.google.android.gms.location.LocationResult) {
                val loc = result.lastLocation ?: return
                cachedLat = loc.latitude
                cachedLng = loc.longitude
                Log.d("VitalSignsService", "Location updated: $cachedLat, $cachedLng (acc=${loc.accuracy}m)")
            }
        }

        client.requestLocationUpdates(
            locationRequest,
            locationCallback,
            android.os.Looper.getMainLooper(),
        )

        // Cancel via coroutine job on service destroy
        locationJob = serviceScope.launch {
            try {
                kotlinx.coroutines.awaitCancellation()
            } finally {
                client.removeLocationUpdates(locationCallback)
            }
        }
    }

    /**
     * Sends the SOS alert immediately using [cachedLat]/[cachedLng] (available instantly).
     * Then tries to get a fresher high-accuracy fix within 10 s and updates Firebase if found.
     */
    private fun sendSosWithBestLocation(remoteUid: String) {
        // Fire SOS immediately with whatever we have (even 0,0 is better than waiting)
        pushSosAlert(remoteUid, cachedLat, cachedLng)

        // If we already have a valid cached location, try to refine it in the background
        val hasFinePermission = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasFinePermission || (cachedLat != 0.0 && cachedLng != 0.0)) {
            // Already have a good location or can't get a better one — nothing more to do
            return
        }

        // No cached location yet — try to get a fresh one and update Firebase
        @android.annotation.SuppressLint("MissingPermission")
        val client = LocationServices.getFusedLocationProviderClient(this)
        val locationRequest = com.google.android.gms.location.LocationRequest.Builder(
            com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
            1_000L,
        ).setMaxUpdates(1)
            .setDurationMillis(10_000L)
            .build()

        val alertRef = database.getReference("alerts").child(remoteUid)
        val callback = object : com.google.android.gms.location.LocationCallback() {
            override fun onLocationResult(result: com.google.android.gms.location.LocationResult) {
                client.removeLocationUpdates(this)
                val loc = result.lastLocation ?: return
                cachedLat = loc.latitude
                cachedLng = loc.longitude
                // Update the most recent alert with the real coordinates
                val sosId = _activeSosId.value ?: return
                alertRef.child(sosId).updateChildren(
                    mapOf("lat" to loc.latitude, "lng" to loc.longitude)
                )
                Log.d("VitalSignsService", "SOS location refined: ${loc.latitude}, ${loc.longitude}")
            }
        }
        client.requestLocationUpdates(locationRequest, callback, android.os.Looper.getMainLooper())
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
