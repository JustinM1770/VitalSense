package mx.ita.vitalsense.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import mx.ita.vitalsense.HealthSensorApp
import mx.ita.vitalsense.MainActivity
import mx.ita.vitalsense.R

class EmergencyListenerService : Service() {

    companion object {
        const val EXTRA_USER_ID = "extra_user_id"
        private const val CHANNEL_MONITORING = "emergency_monitoring"
        private const val FOREGROUND_ID = 9921
    }

    private val database = FirebaseDatabase.getInstance("https://vitalsenseai-1cb9f-default-rtdb.firebaseio.com")
    private var listener: ValueEventListener? = null
    private var ref: com.google.firebase.database.DatabaseReference? = null
    private var activeAlertId: String? = null
    private var lastNotifiedAlertId: String? = null
    private var userId: String = ""

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannels()
        startForeground(FOREGROUND_ID, buildMonitoringNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val incomingUserId = intent?.getStringExtra(EXTRA_USER_ID)
            ?: FirebaseAuth.getInstance().currentUser?.uid
            ?: ""

        if (incomingUserId.isBlank()) {
            Log.w("EmergencyListener", "No userId available; stopping listener")
            stopSelf()
            return START_NOT_STICKY
        }

        if (incomingUserId != userId) {
            userId = incomingUserId
            attachListener()
        }

        return START_STICKY
    }

    override fun onDestroy() {
        listener?.let { ref?.removeEventListener(it) }
        listener = null
        ref = null
        super.onDestroy()
    }

    private fun attachListener() {
        listener?.let { ref?.removeEventListener(it) }

        val alertsRef = database.getReference("alerts").child(userId)
        val newListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val latestActive = snapshot.children
                    .mapNotNull { child ->
                        val type = child.child("type").getValue(String::class.java) ?: ""
                        val status = child.child("status").getValue(String::class.java) ?: ""
                        val read = child.child("read").getValue(Boolean::class.java) ?: false
                        if (type != "SOS" || status != "active" || read) return@mapNotNull null
                        Triple(
                            child.key ?: return@mapNotNull null,
                            child.child("timestamp").getValue(Long::class.java) ?: 0L,
                            Pair(
                                child.child("lat").getValue(Double::class.java) ?: 0.0,
                                child.child("lng").getValue(Double::class.java) ?: 0.0,
                            ),
                        )
                    }
                    .maxByOrNull { it.second }

                val foundId = latestActive?.first
                val foundLat = latestActive?.third?.first ?: 0.0
                val foundLng = latestActive?.third?.second ?: 0.0

                if (foundId != activeAlertId) {
                    activeAlertId = foundId
                }

                if (!foundId.isNullOrBlank() && foundId != lastNotifiedAlertId) {
                    showAlertNotification(foundId, foundLat, foundLng)
                    lastNotifiedAlertId = foundId
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("EmergencyListener", "Listener cancelled: ${error.message}")
            }
        }

        listener = newListener
        ref = alertsRef
        alertsRef.addValueEventListener(newListener)
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val monitoring = NotificationChannel(
            CHANNEL_MONITORING,
            "Monitoreo SOS",
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(monitoring)
    }

    private fun buildMonitoringNotification() = NotificationCompat.Builder(this, CHANNEL_MONITORING)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle("BioMetric AI activo")
        .setContentText("Monitoreando alertas SOS del paciente")
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setOngoing(true)
        .build()

    private fun showAlertNotification(alertId: String, lat: Double, lng: Double) {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_notifications", true)
            putExtra("alert_id", alertId)
            putExtra("alert_lat", lat)
            putExtra("alert_lng", lng)
        }
        val pending = PendingIntent.getActivity(
            this,
            alertId.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val body = if (lat != 0.0 && lng != 0.0) {
            "SOS detectado. Toca para ver ubicación y detalles."
        } else {
            "SOS detectado. Toca para ver detalles de la alerta."
        }

        val notification = NotificationCompat.Builder(this, HealthSensorApp.CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("¡EMERGENCIA SOS!")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(alertId.hashCode(), notification)
    }
}