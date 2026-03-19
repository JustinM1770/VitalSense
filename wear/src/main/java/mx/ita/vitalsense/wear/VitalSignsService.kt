package mx.ita.vitalsense.wear

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
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

    private var syncJob: Job? = null
    private var sleepJob: Job? = null

    inner class LocalBinder : Binder() {
        fun getService(): VitalSignsService = this@VitalSignsService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        hrManager = HeartRateManager(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = createNotification()
        startForeground(1, notification)
        
        val prefs = getSharedPreferences("vitalsense_wear_prefs", Context.MODE_PRIVATE)
        val userId = prefs.getString("user_id", "global") ?: "global"
        
        startHrMonitoring(userId)
        startSleepSync(userId)
        
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

    private fun startSleepSync(userId: String) {
        sleepJob?.cancel()
        sleepJob = serviceScope.launch {
            while (isActive) {
                // Simulación de sueño (85%, 7.5h) para demostración
                val dateKey = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                } else {
                    "2026-03-19"
                }
                
                val sleepMap = mapOf(
                    "score" to 85,
                    "horas" to 7.5,
                    "estado" to "Bueno"
                )
                
                database.getReference("sleep/$userId/$dateKey").setValue(sleepMap)
                delay(15 * 60 * 1000) // Cada 15 min
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
    }
}
