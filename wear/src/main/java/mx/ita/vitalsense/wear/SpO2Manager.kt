package mx.ita.vitalsense.wear

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.OxygenSaturationRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import java.time.Instant

/**
 * Reads SpO₂ using a two-tier strategy:
 *
 * 1. **Health Connect** (preferred): poll OxygenSaturationRecord every 30 s.
 *    The OEM watch app (Samsung Health, Fitbit, etc.) writes SpO₂ readings here,
 *    so this works even on watches that hide the sensor behind proprietary APIs.
 *
 * 2. **SensorManager fallback**: if Health Connect has no data and the device
 *    exposes a TYPE_HEART_RATE–like SpO₂ sensor, use it directly.
 */
class SpO2Manager(private val context: Context) {

    companion object {
        private const val TAG = "SpO2Manager"
        // Some OEMs expose SpO2 as sensor type 65572 or similar.
        // TYPE_HEART_RATE = 21 on standard Android; OEM SpO2 types vary.
        private val KNOWN_SPO2_SENSOR_TYPES = setOf(
            65572,       // Samsung Galaxy Watch
            65633,       // Pixel Watch
            Sensor.TYPE_HEART_RATE + 1, // Generic offset sometimes used
        )
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    // ── Health Connect ─────────────────────────────────────────────────────────

    private val hcAvailable: Boolean by lazy {
        try {
            HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
        } catch (e: Exception) {
            false
        }
    }

    private val hcClient: HealthConnectClient? by lazy {
        try { if (hcAvailable) HealthConnectClient.getOrCreate(context) else null }
        catch (e: Exception) { null }
    }

    /**
     * Returns true to allow the SpO2 data flow to start.
     *
     * We cannot reliably detect SpO2 support upfront on Wear OS:
     * - Many OEMs (Samsung, Pixel Watch, Fossil, etc.) hide the sensor behind proprietary APIs.
     * - Health Connect SDK availability on the watch is device-dependent.
     * The real proof of support comes from whether data actually arrives via
     * [observeSpO2] (Health Connect records) or the SensorManager listener.
     * Returning true here avoids falsely blocking the sensor flow.
     */
    fun isSupported(): Boolean = true

    /**
     * Emits SpO₂ % values as a Flow.
     * - Polls Health Connect every 30 s for recent OxygenSaturationRecord entries.
     * - Falls back to SensorManager if Health Connect has no entry.
     */
    fun observeSpO2(): Flow<Int> = flow {
        val client = hcClient
        while (true) {
            var value: Int? = null

            // 1. Try Health Connect
            if (client != null) {
                value = readSpO2FromHealthConnect(client)
            }

            // 2. Sensor fallback (latest reading stored from SensorManager listener)
            if (value == null || value == 0) {
                value = lastSensorValue.takeIf { it > 0 }
            }

            if (value != null && value > 0) {
                Log.d(TAG, "SpO2 emitting: $value%")
                emit(value)
            }

            delay(30_000L) // re-poll every 30 s
        }
    }

    private suspend fun readSpO2FromHealthConnect(client: HealthConnectClient): Int? {
        return try {
            val end = Instant.now()
            val start = end.minusSeconds(7200) // last 2 hours
            val request = ReadRecordsRequest(
                recordType = OxygenSaturationRecord::class,
                timeRangeFilter = TimeRangeFilter.between(start, end),
            )
            val response = client.readRecords(request)
            val latest = response.records.maxByOrNull { it.time }
            val value = latest?.percentage?.value?.toInt()
            if (value != null) Log.d(TAG, "HC SpO2 = $value% at ${latest?.time}")
            value
        } catch (e: Exception) {
            Log.w(TAG, "Could not read SpO2 from Health Connect: ${e.message}")
            null
        }
    }

    // ── SensorManager (fallback for devices that still expose it) ───────────────

    @Volatile private var lastSensorValue: Int = 0

    private fun findSpo2Sensor(): Sensor? {
        // Try well-known OEM sensor types first
        KNOWN_SPO2_SENSOR_TYPES.forEach { type ->
            sensorManager.getDefaultSensor(type)?.let { return it }
        }
        // Scan all sensors for any that mention SpO2/oxygen in their name
        return sensorManager.getSensorList(Sensor.TYPE_ALL).firstOrNull { sensor ->
            val name = sensor.name.lowercase()
            name.contains("spo2") || name.contains("oxygen") || name.contains("oximetry")
        }
    }

    /** Call this once to start receiving background SensorManager SpO2 events. */
    fun startSensorListener() {
        val sensor = findSpo2Sensor() ?: return
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val value = event.values[0].toInt().coerceIn(1, 100)
                if (value in 50..100) {
                    Log.d(TAG, "Sensor SpO2 = $value%")
                    lastSensorValue = value
                }
            }
            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
        }
        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        Log.d(TAG, "SensorManager SpO2 listener registered for: ${sensor.name}")
    }
}
