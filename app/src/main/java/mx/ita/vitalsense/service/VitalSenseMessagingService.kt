package mx.ita.vitalsense.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.compose.ui.graphics.toArgb
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import mx.ita.vitalsense.HealthSensorApp
import mx.ita.vitalsense.MainActivity
import mx.ita.vitalsense.R
import mx.ita.vitalsense.ui.theme.DashBlue

/**
 * Servicio FCM: recibe push notifications desde Firebase Cloud Functions.
 *
 * Para activar notificaciones del servidor:
 *  1. Firebase Console → Cloud Messaging → habilitar
 *  2. Desplegar Cloud Function que observe "patients/{id}" y envíe FCM
 *     cuando se detecten valores fuera de rango.
 */
class BioMetricAIMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
            FirebaseDatabase.getInstance()
                .getReference("patients/$uid/deviceToken")
                .setValue(token)
            FirebaseDatabase.getInstance()
                .getReference("patients/$uid/deviceTokenUpdatedAt")
                .setValue(System.currentTimeMillis())
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title
            ?: message.data["title"]
            ?: "⚠️ Alerta BioMetric AI"

        val body = message.notification?.body
            ?: message.data["body"]
            ?: "Un paciente requiere atención"

        val alertId = message.data["alertId"] ?: ""
        val lat = message.data["lat"]?.toDoubleOrNull() ?: 0.0
        val lng = message.data["lng"]?.toDoubleOrNull() ?: 0.0
        showNotification(title, body, alertId, lat, lng)
    }

    private fun showNotification(title: String, body: String, alertId: String, lat: Double, lng: Double) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("open_notifications", true)
            putExtra("alert_id", alertId)
            putExtra("alert_lat", lat)
            putExtra("alert_lng", lng)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(this, HealthSensorApp.CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setColor(DashBlue.toArgb())
            .setColorized(true)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(System.currentTimeMillis().toInt(), notification)
    }
}
