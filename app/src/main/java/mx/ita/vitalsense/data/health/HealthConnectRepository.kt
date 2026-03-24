package mx.ita.vitalsense.data.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
import java.time.Duration
import java.time.temporal.ChronoUnit

data class HealthConnectVitals(
    val heartRate: Int? = null,
    val glucose: Double? = null,   // mg/dL
    val spo2: Double? = null,      // %
)

class HealthConnectRepository(private val context: Context) {

    private val client by lazy { HealthConnectClient.getOrCreate(context) }

    companion object {
        val PERMISSIONS = setOf(
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getReadPermission(OxygenSaturationRecord::class),
            HealthPermission.getReadPermission(BloodGlucoseRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class),
        )

        fun isAvailable(context: Context): Boolean =
            HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
    }

    suspend fun hasPermissions(): Boolean {
        val granted = client.permissionController.getGrantedPermissions()
        return granted.containsAll(PERMISSIONS)
    }

    /** Lee las últimas lecturas de HR, SpO2 y glucosa de las últimas 24 horas. */
    suspend fun readLatestVitals(): HealthConnectVitals {
        val end = Instant.now()
        val start = end.minus(24, ChronoUnit.HOURS)
        val range = TimeRangeFilter.between(start, end)

        val hr = readLatestHeartRate(range)
        val spo2 = readLatestSpo2(range)
        val glucose = readLatestGlucose(range)

        return HealthConnectVitals(heartRate = hr, glucose = glucose, spo2 = spo2)
    }

    private suspend fun readLatestHeartRate(range: TimeRangeFilter): Int? {
        val response = client.readRecords(
            ReadRecordsRequest(HeartRateRecord::class, timeRangeFilter = range)
        )
        return response.records
            .flatMap { it.samples }
            .maxByOrNull { it.time }
            ?.beatsPerMinute
            ?.toInt()
    }

    private suspend fun readLatestSpo2(range: TimeRangeFilter): Double? {
        val response = client.readRecords(
            ReadRecordsRequest(OxygenSaturationRecord::class, timeRangeFilter = range)
        )
        return response.records
            .maxByOrNull { it.time }
            ?.percentage
            ?.value
    }

    private suspend fun readLatestGlucose(range: TimeRangeFilter): Double? {
        val response = client.readRecords(
            ReadRecordsRequest(BloodGlucoseRecord::class, timeRangeFilter = range)
        )
        // Health Connect almacena glucosa en mmol/L — convertir a mg/dL
        val mmolPerL = response.records
            .maxByOrNull { it.time }
            ?.level
            ?.inMillimolesPerLiter
            ?: return null
        return mmolPerL * 18.0
    }

    suspend fun readSleepData(start: Instant, end: Instant, context: Context): mx.ita.vitalsense.data.model.SleepData? {
        val response = client.readRecords(
            ReadRecordsRequest(SleepSessionRecord::class, timeRangeFilter = TimeRangeFilter.between(start, end))
        )

        if (response.records.isEmpty()) {
            return mx.ita.vitalsense.data.model.SleepData(score = 0, horas = 0f, estado = "No durmió")
        }

        val totalMinutes = response.records.sumOf { session ->
            Duration.between(session.startTime, session.endTime).toMinutes().coerceAtLeast(0)
        }

        val hours = (totalMinutes / 60f)
        if (hours <= 0f) {
            return mx.ita.vitalsense.data.model.SleepData(score = 0, horas = 0f, estado = "No durmió")
        }

        val score = ((hours / 8f) * 100f).toInt().coerceIn(0, 100)
        val estado = when {
            hours < 5f -> "Malo"
            hours < 7f -> "Regular"
            else -> "Bueno"
        }

        return mx.ita.vitalsense.data.model.SleepData(
            score = score,
            horas = (kotlin.math.round(hours * 10f) / 10f),
            estado = estado,
        )
    }
}
