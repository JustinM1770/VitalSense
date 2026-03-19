package mx.ita.vitalsense.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import mx.ita.vitalsense.HealthSensorApp
import mx.ita.vitalsense.MainActivity
import mx.ita.vitalsense.R

/**
 * Servicio FCM: recibe push notifications desde Firebase Cloud Functions.
 *
 * Para activar notificaciones del servidor:
 *  1. Firebase Console → Cloud Messaging → habilitar
 *  2. Desplegar Cloud Function que observe "patients/{id}" y envíe FCM
 *     cuando se detecten valores fuera de rango.
 */
class VitalSenseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        // Aquí podrías guardar el token en Firebase para el tutor actual
        // AuthRepository().saveDeviceToken(token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val title = message.notification?.title
            ?: message.data["title"]
            ?: "⚠️ Alerta HealthSensor"

        val body = message.notification?.body
            ?: message.data["body"]
            ?: "Un paciente requiere atención"

        val patientName = message.data["patientName"] ?: ""
        showNotification(title, body, patientName)
    }

    private fun showNotification(title: String, body: String, patientName: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
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
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(System.currentTimeMillis().toInt(), notification)
    }
}
