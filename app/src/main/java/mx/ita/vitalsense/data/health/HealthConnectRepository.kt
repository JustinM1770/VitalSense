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
import kotlin.math.roundToInt

data class HealthConnectVitals(
    val heartRate: Int? = null,
    val glucose: Double? = null,   // mg/dL
    val spo2: Double? = null,      // %
    val heartRateSampleTimestamp: Long? = null,
    val glucoseSampleTimestamp: Long? = null,
    val spo2SampleTimestamp: Long? = null,
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

        return HealthConnectVitals(
            heartRate = hr?.first,
            glucose = glucose?.first,
            spo2 = spo2?.first,
            heartRateSampleTimestamp = hr?.second,
            glucoseSampleTimestamp = glucose?.second,
            spo2SampleTimestamp = spo2?.second,
        )
    }

    private suspend fun readLatestHeartRate(range: TimeRangeFilter): Pair<Int, Long>? {
        val response = client.readRecords(
            ReadRecordsRequest(HeartRateRecord::class, timeRangeFilter = range)
        )
        val sample = response.records
            .flatMap { it.samples }
            .maxByOrNull { it.time }
            ?: return null
        return sample.beatsPerMinute.toInt() to sample.time.toEpochMilli()
    }

    private suspend fun readLatestSpo2(range: TimeRangeFilter): Pair<Double, Long>? {
        val response = client.readRecords(
            ReadRecordsRequest(OxygenSaturationRecord::class, timeRangeFilter = range)
        )
        val record = response.records
            .maxByOrNull { it.time }
            ?: return null
        return record.percentage.value to record.time.toEpochMilli()
    }

    private suspend fun readLatestGlucose(range: TimeRangeFilter): Pair<Double, Long>? {
        val response = client.readRecords(
            ReadRecordsRequest(BloodGlucoseRecord::class, timeRangeFilter = range)
        )
        // Health Connect almacena glucosa en mmol/L — convertir a mg/dL
        val record = response.records
            .maxByOrNull { it.time }
            ?: return null
        val mmolPerL = record.level.inMillimolesPerLiter
        return (mmolPerL * 18.0) to record.time.toEpochMilli()
    }

    suspend fun readSleepData(start: Instant, end: Instant, context: Context): mx.ita.vitalsense.data.model.SleepData? {
        val response = client.readRecords(
            ReadRecordsRequest(SleepSessionRecord::class, timeRangeFilter = TimeRangeFilter.between(start, end))
        )

        if (response.records.isEmpty()) {
            return mx.ita.vitalsense.data.model.SleepData(score = 0, minutosTotales = 0, horasCompletas = 0, sleepStartMillis = 0L, sleepEndMillis = 0L, horas = 0f, estado = "No durmió")
        }

        val totalMinutes = response.records.sumOf { session ->
            Duration.between(session.startTime, session.endTime).toMinutes().coerceAtLeast(0)
        }
        val startMillis = response.records.minOfOrNull { it.startTime.toEpochMilli() } ?: 0L
        val endMillis = response.records.maxOfOrNull { it.endTime.toEpochMilli() } ?: 0L

        if (totalMinutes <= 0) {
            return mx.ita.vitalsense.data.model.SleepData(score = 0, minutosTotales = 0, horasCompletas = 0, sleepStartMillis = startMillis, sleepEndMillis = endMillis, horas = 0f, estado = "No durmió")
        }

        val completeHours = totalMinutes / 60
        val score = ((totalMinutes / 480f) * 100f).roundToInt().coerceIn(0, 100)
        val estado = when {
            totalMinutes < 5 * 60 -> "Malo"
            totalMinutes < 7 * 60 -> "Regular"
            else -> "Bueno"
        }

        return mx.ita.vitalsense.data.model.SleepData(
            score = score,
            minutosTotales = totalMinutes.toInt(),
            horasCompletas = completeHours.toInt(),
            sleepStartMillis = startMillis,
            sleepEndMillis = endMillis,
            horas = completeHours.toFloat(),
            estado = estado,
        )
    }
}
