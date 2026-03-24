package mx.ita.vitalsense.wear

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class SpO2Manager(context: Context) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    
    // Intenta obtener sensor de SpO2 si existe (algunos fabricantes lo proporcionan)
    // Si no, devuelve null y isSupported() retornará false
    private val oxygenSensor: Sensor? = try {
        sensorManager.getDefaultSensor(65536) // TYPE_OXYGEN_SATURATION en algunos dispositivos
    } catch (e: Exception) {
        null
    }

    fun isSupported(): Boolean = oxygenSensor != null

    fun observeSpO2(): Flow<Int> = callbackFlow {
        val sensor = oxygenSensor
        if (sensor == null) {
            close()
            return@callbackFlow
        }

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val raw = event.values.firstOrNull() ?: return
                if (!raw.isFinite() || raw <= 0f) return

                // Some implementations report 0..1 while others report 0..100.
                val percent = if (raw <= 1f) raw * 100f else raw
                val normalized = percent.toInt().coerceIn(1, 100)
                trySend(normalized)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)

        awaitClose {
            sensorManager.unregisterListener(listener)
        }
    }
}
