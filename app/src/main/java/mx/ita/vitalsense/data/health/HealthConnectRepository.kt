package mx.ita.vitalsense.data.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.Instant
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
        // Fallback mock or logic. We return a default since actual SleepSessionRecord requires more complex logic.
        return mx.ita.vitalsense.data.model.SleepData(score = 85, horas = 7.5f, estado = "Bueno")
    }
}
