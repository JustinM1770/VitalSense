package mx.ita.vitalsense.wear

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
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
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class VitalSignsService : Service() {

    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var hrManager: HeartRateManager
    private val database = FirebaseDatabase.getInstance("https://vitalsenseai-1cb9f-default-rtdb.firebaseio.com")
    
    private val _currentHeartRate = MutableStateFlow(0.0)
    val currentHeartRate: StateFlow<Double> = _currentHeartRate.asStateFlow()

    private val _activeSosId = MutableStateFlow<String?>(null)
    val activeSosId: StateFlow<String?> = _activeSosId.asStateFlow()

    private var syncJob: Job? = null
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
        val notification = createNotification()
        startForeground(1, notification)
        
        val prefs = getSharedPreferences("vitalsense_wear_prefs", Context.MODE_PRIVATE)
        val userId = prefs.getString("user_id", "global") ?: "global"
        
        startHrMonitoring(userId)
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

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("vitals_channel", "Monitoreo VitalSense", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
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

        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        val prefs = getSharedPreferences("vitalsense_wear_prefs", Context.MODE_PRIVATE)
        val remoteUid = prefs.getString("user_id", "global") ?: "global"
        val database = FirebaseDatabase.getInstance("https://vitalsenseai-1cb9f-default-rtdb.firebaseio.com")

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            val locationRequest = com.google.android.gms.location.LocationRequest.Builder(
                com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY,
                1000L
            ).setMaxUpdates(1)
             .setDurationMillis(10000L)
             .build()

            val locationCallback = object : com.google.android.gms.location.LocationCallback() {
                override fun onLocationResult(result: com.google.android.gms.location.LocationResult) {
                    fusedLocationClient.removeLocationUpdates(this)
                    val loc = result.lastLocation
                    val alertRef = database.getReference("alerts").child(remoteUid).push()
                    _activeSosId.value = alertRef.key
                    alertRef.setValue(mapOf(
                        "timestamp" to System.currentTimeMillis(),
                        "type" to "SOS",
                        "lat" to (loc?.latitude ?: 0.0),
                        "lng" to (loc?.longitude ?: 0.0),
                        "status" to "active"
                    ))
                }
            }
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                android.os.Looper.getMainLooper()
            )
        } else {
            val alertRef = database.getReference("alerts").child(remoteUid).push()
            _activeSosId.value = alertRef.key
            alertRef.setValue(mapOf(
                "timestamp" to System.currentTimeMillis(),
                "type" to "SOS",
                "status" to "active"
            ))
        }
    }

    fun clearSos() {
        _activeSosId.value = null
    }
}
