package mx.ita.vitalsense.data.healthconnect

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import mx.ita.vitalsense.data.model.SleepData

data class HealthConnectVitals(
    val heartRate: Int? = null,
    val glucose: Double? = null,
    val spo2: Int? = null,
)

class HealthConnectRepository(private val context: Context) {

    private val healthConnectClient by lazy { HealthConnectClient.getOrCreate(context) }

    suspend fun hasPermissions(): Boolean {
        val granted = healthConnectClient.permissionController.getGrantedPermissions()
        return granted.containsAll(PERMISSIONS)
    }

    suspend fun readLatestVitals(): HealthConnectVitals? {
        if (!hasPermissions()) return null
        
        val now = Instant.now()
        val timeRange = TimeRangeFilter.between(now.minus(1, ChronoUnit.DAYS), now)
        
        val hrResponse = healthConnectClient.readRecords(
            ReadRecordsRequest(
                recordType = HeartRateRecord::class,
                timeRangeFilter = timeRange,
                ascendingOrder = false,
                pageSize = 1
            )
        )
        val hr = hrResponse.records.firstOrNull()?.samples?.firstOrNull()?.beatsPerMinute?.toInt()

        val spo2Response = healthConnectClient.readRecords(
            ReadRecordsRequest(
                recordType = OxygenSaturationRecord::class,
                timeRangeFilter = timeRange,
                ascendingOrder = false,
                pageSize = 1
            )
        )
        val spo2 = spo2Response.records.firstOrNull()?.percentage?.value?.times(100)?.toInt()

        val glucoseResponse = healthConnectClient.readRecords(
            ReadRecordsRequest(
                recordType = BloodGlucoseRecord::class,
                timeRangeFilter = timeRange,
                ascendingOrder = false,
                pageSize = 1
            )
        )
        val glucose = glucoseResponse.records.firstOrNull()?.level?.inMilligramsPerDeciliter

        return HealthConnectVitals(heartRate = hr, spo2 = spo2, glucose = glucose)
    }

    suspend fun readSleepData(): SleepData? {
        if (!hasPermissions()) return null
        
        val now = Instant.now()
        val startOfDay = now.truncatedTo(ChronoUnit.DAYS) // Hoy a las 00:00
        val timeRange = TimeRangeFilter.between(startOfDay.minus(24, ChronoUnit.HOURS), now)
        
        val sleepResponse = healthConnectClient.readRecords(
            ReadRecordsRequest(
                recordType = SleepSessionRecord::class,
                timeRangeFilter = timeRange,
                ascendingOrder = false
            )
        )
        
        if (sleepResponse.records.isEmpty()) return null
        
        // Sumar duración total de todas las sesiones de las últimas 24 horas
        var totalDuration = Duration.ZERO
        sleepResponse.records.forEach { session ->
            totalDuration = totalDuration.plus(Duration.between(session.startTime, session.endTime))
        }
        
        val totalHours = totalDuration.toMinutes().toFloat() / 60f
        
        // Calcular un score simple basado en horas (8h = 100)
        val score = ((totalHours / 8f) * 100).toInt().coerceAtMost(100)
        
        val estado = when {
            score >= 90 -> "Excelente"
            score >= 75 -> "Bueno"
            score >= 60 -> "Regular"
            else -> "Deficiente"
        }
        
        return SleepData(score = score, horas = totalHours, estado = estado)
    }

    companion object {
        val PERMISSIONS = setOf(
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getReadPermission(OxygenSaturationRecord::class),
            HealthPermission.getReadPermission(BloodGlucoseRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class)
        )

        fun isAvailable(context: Context): Boolean {
            return HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
        }
    }
}
