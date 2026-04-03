package mx.ita.vitalsense.data.medications

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import mx.ita.vitalsense.HealthSensorApp
import mx.ita.vitalsense.MainActivity
import mx.ita.vitalsense.R
import mx.ita.vitalsense.data.model.Medication
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

class MedicationReminderWorker(
    private val ctx: Context,
    params: WorkerParameters,
) : CoroutineWorker(ctx, params) {

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance()

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val userId = auth.currentUser?.uid ?: return@withContext Result.success()
        val now = System.currentTimeMillis()

        val snapshot = runCatching {
            db.getReference("medications/$userId").get().await()
        }.getOrNull() ?: return@withContext Result.success()

        snapshot.children.forEach { child ->
            val med = child.getValue(Medication::class.java)?.let { current ->
                current.copy(id = child.key ?: current.id)
            } ?: return@forEach

            if (!med.activo || !med.reminderEnabled) return@forEach
            if (med.id.isBlank()) return@forEach

            val nextReminderAt = resolveNextReminderAt(med, now)
            if (nextReminderAt <= 0L) return@forEach

            if (now >= nextReminderAt) {
                notifyReminder(med)

                val nextRun = nextDueAfter(med, nextReminderAt)
                db.getReference("medications/$userId/${med.id}")
                    .updateChildren(
                        mapOf(
                            "lastReminderAt" to now,
                            "nextReminderAt" to nextRun,
                        )
                    )
                    .await()
            } else if (med.nextReminderAt <= 0L) {
                db.getReference("medications/$userId/${med.id}")
                    .child("nextReminderAt")
                    .setValue(nextReminderAt)
                    .await()
            }
        }

        Result.success()
    }

    private fun notifyReminder(medication: Medication) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    ctx,
                    android.Manifest.permission.POST_NOTIFICATIONS,
                ) != PackageManager.PERMISSION_GRANTED
            ) return
        }

        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_medications", true)
        }
        val pending = PendingIntent.getActivity(
            ctx,
            medication.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(ctx, HealthSensorApp.CHANNEL_MEDICATIONS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Recordatorio: ${medication.nombre}")
            .setContentText("Es hora de tomar tu medicamento")
            .setStyle(NotificationCompat.BigTextStyle().bigText(buildReminderText(medication)))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()

        val manager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(medication.id.hashCode(), notification)
    }

    private fun buildReminderText(medication: Medication): String {
        val parts = buildList {
            add(medication.nombre)
            if (medication.dosis.isNotBlank()) add("Dosis: ${medication.dosis}")
            if (medication.cadaCuanto.isNotBlank()) add("Frecuencia: ${medication.cadaCuanto}")
        }
        return parts.joinToString("\n")
    }

    private fun resolveNextReminderAt(medication: Medication, now: Long): Long {
        if (medication.nextReminderAt > 0L) return medication.nextReminderAt

        val initial = medication.recordatorioHora.trim()
        if (initial.matches(Regex("^([01]\\d|2[0-3]):([0-5]\\d)$"))) {
            val parts = initial.split(":")
            val hour = parts[0].toIntOrNull() ?: return now + intervalMillis(medication)
            val minute = parts[1].toIntOrNull() ?: return now + intervalMillis(medication)
            val candidate = LocalDate.now(ZoneId.systemDefault())
                .atTime(LocalTime.of(hour, minute))
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
            return if (candidate >= now) candidate else candidate + intervalMillis(medication)
        }

        return if (medication.createdAt > 0L) {
            medication.createdAt + intervalMillis(medication)
        } else {
            now + intervalMillis(medication)
        }
    }

    private fun nextDueAfter(medication: Medication, referenceTime: Long): Long {
        val interval = intervalMillis(medication)
        return if (interval > 0L) referenceTime + interval else referenceTime + TimeUnit.DAYS.toMillis(1)
    }

    private fun intervalMillis(medication: Medication): Long {
        val text = medication.cadaCuanto.lowercase().trim()
        val match = Regex("(\\d+)").find(text)
        val value = match?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 24L
        return when {
            text.contains("min") || text.contains("minute") -> TimeUnit.MINUTES.toMillis(value)
            text.contains("hora") || text.contains("hour") -> TimeUnit.HOURS.toMillis(value)
            text.contains("día") || text.contains("dia") || text.contains("day") -> TimeUnit.DAYS.toMillis(value)
            else -> TimeUnit.HOURS.toMillis(24L)
        }
    }

    companion object {
        private const val WORK_NAME = "vitalsense_medication_reminders"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<MedicationReminderWorker>(15, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }
    }
}