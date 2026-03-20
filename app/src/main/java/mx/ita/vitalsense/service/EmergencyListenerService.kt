package mx.ita.vitalsense.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import mx.ita.vitalsense.MainActivity
import mx.ita.vitalsense.R

class EmergencyListenerService : Service() {

    private val CHANNEL_ID = "vitalsense_sos_channel"
    private val FOREGROUND_CHANNEL_ID = "vitalsense_foreground_channel"
    private val database = FirebaseDatabase.getInstance("https://vitalsenseai-1cb9f-default-rtdb.firebaseio.com")
    private val auth = FirebaseAuth.getInstance()
    private var listener: ValueEventListener? = null

    override fun onCreate() {
        super.onCreate()
        createChannels()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1001, createForegroundNotification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(1001, createForegroundNotification())
        }
        startListening()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        val userId = auth.currentUser?.uid ?: "global"
        listener?.let {
            database.getReference("alerts").child(userId).removeEventListener(it)
        }
    }

    private fun startListening() {
        val userId = auth.currentUser?.uid ?: "global"
        val alertsRef = database.getReference("alerts").child(userId)

        listener = alertsRef.orderByChild("status").equalTo("active")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        for (child in snapshot.children) {
                            val data = child.value as? Map<String, Any>
                            if (data != null && data["type"] == "SOS") {
                                val lat = (data["lat"] as? Number)?.toDouble() ?: 0.0
                                val lng = (data["lng"] as? Number)?.toDouble() ?: 0.0
                                showSosNotification(lat, lng)
                                // Notificamos solo el más reciente
                                break
                            }
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("EmergencyService", "Error listening for SOS", error.toException())
                }
            })
    }

    private fun showSosNotification(lat: Double, lng: Double) {
        val intent = if (lat != 0.0 && lng != 0.0) {
            val uri = "geo:$lat,$lng?q=$lat,$lng(Emergencia+SOS)"
            val mapIntent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
            mapIntent.setPackage("com.google.android.apps.maps")
            mapIntent
        } else {
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("¡ALERTA DE EMERGENCIA (SOS)!")
            .setContentText("El paciente ha enviado un SOS (presionado o agitado)")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(pendingIntent, true)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
    }

    private fun createForegroundNotification(): android.app.Notification {
        val builder = NotificationCompat.Builder(this, FOREGROUND_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("VitalSense Activo")
            .setContentText("Escuchando alertas de emergencia en segundo plano")
            .setPriority(NotificationCompat.PRIORITY_MIN)

        return builder.build()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)

            val sosChannel = NotificationChannel(
                CHANNEL_ID,
                "Alertas SOS",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones de emergencia entrantes"
            }
            notificationManager.createNotificationChannel(sosChannel)

            val fgChannel = NotificationChannel(
                FOREGROUND_CHANNEL_ID,
                "Servicio VitalSense",
                NotificationManager.IMPORTANCE_MIN
            )
            notificationManager.createNotificationChannel(fgChannel)
        }
    }
}
