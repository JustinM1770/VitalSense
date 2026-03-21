package mx.ita.vitalsense.wear

import android.content.Context
import android.util.Log
import androidx.health.services.client.HealthServices
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DeltaDataType
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class HeartRateManager(context: Context) {
    private val measureClient = HealthServices.getClient(context).measureClient
    private val TAG = "HeartRateManager"

    fun observeHeartRate(): Flow<Double> = callbackFlow {
        val callback = object : MeasureCallback {
            override fun onAvailabilityChanged(
                dataType: DeltaDataType<*, *>,
                availability: Availability
            ) {
                Log.d(TAG, "Availability changed: $availability for $dataType")
            }

            override fun onDataReceived(data: DataPointContainer) {
                val heartRateDataPoints = data.getData(DataType.HEART_RATE_BPM)
                Log.d(TAG, "Data received: ${heartRateDataPoints.size} points")
                
                if (heartRateDataPoints.isNotEmpty()) {
                    val latestBpm = heartRateDataPoints.last().value
                    Log.d(TAG, "HR Received: $latestBpm BPM")
                    trySend(latestBpm)
                } else {
                    Log.d(TAG, "No HR data points in container")
                }
            }
        }

        Log.d(TAG, "Registering HR callback for MeasureClient")
        try {
            measureClient.registerMeasureCallback(DataType.HEART_RATE_BPM, callback)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register HR callback", e)
        }

        awaitClose {
            Log.d(TAG, "Unregistering HR callback")
            measureClient.unregisterMeasureCallbackAsync(DataType.HEART_RATE_BPM, callback)
        }
    }
}
