package mx.ita.vitalsense

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.google.firebase.database.FirebaseDatabase

class HealthSensorApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Firebase Realtime Database: URL explícita + caché offline
        FirebaseDatabase.getInstance("https://vitalsenseai-1cb9f-default-rtdb.firebaseio.com")
            .setPersistenceEnabled(true)

        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)

            // Canal principal de alertas críticas
            NotificationChannel(
                CHANNEL_ALERTS,
                "Alertas de Vitales",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Notificaciones de alertas críticas de pacientes"
                enableVibration(true)
            }.also { manager.createNotificationChannel(it) }

            // Canal informativo (nuevos datos, conexión BLE)
            NotificationChannel(
                CHANNEL_INFO,
                "Información",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Estado de conexión y actualizaciones generales"
            }.also { manager.createNotificationChannel(it) }
        }
    }

    companion object {
        const val CHANNEL_ALERTS = "vital_alerts"
        const val CHANNEL_INFO   = "vital_info"
    }
}
