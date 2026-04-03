package mx.ita.vitalsense.wear

import android.content.Context
import android.util.Log
import androidx.health.services.client.PassiveListenerService
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.PassiveListenerConfig
import com.google.firebase.database.FirebaseDatabase

/**
 * Receives passive HR data from Wear Health Services when the watch screen is OFF.
 *
 * Note: DataType.SPO2 does not exist in Health Services 1.0.0.
 * SpO2 is read separately via Health Connect polling in SpO2Manager / VitalSignsService.
 */
class PassiveDataReceiver : PassiveListenerService() {

    companion object {
        private const val TAG = "PassiveDataReceiver"
        private const val DB_URL = "https://vitalsenseai-1cb9f-default-rtdb.firebaseio.com"
        private const val PREFS_NAME = "vitalsense_wear_prefs"
        private const val KEY_USER_ID = "user_id"

        /** Build the passive config — HR only, since DataType.SPO2 is not in Health Services 1.0.0. */
        fun buildPassiveConfig(): PassiveListenerConfig {
            return PassiveListenerConfig.builder()
                .setDataTypes(setOf(DataType.HEART_RATE_BPM))
                .build()
        }
    }

    private val db by lazy { FirebaseDatabase.getInstance(DB_URL) }

    override fun onNewDataPointsReceived(dataPoints: androidx.health.services.client.data.DataPointContainer) {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val userId = prefs.getString(KEY_USER_ID, null).orEmpty()
        if (userId.isBlank()) {
            Log.w(TAG, "No userId stored yet — skipping Firebase write")
            return
        }

        val updates = mutableMapOf<String, Any>()
        val now = System.currentTimeMillis()

        val hrPoints = dataPoints.getData(DataType.HEART_RATE_BPM)
        if (hrPoints.isNotEmpty()) {
            val hr = hrPoints.last().value.toInt()
            Log.d(TAG, "Passive HR: $hr bpm")
            updates["heartRate"] = hr
            updates["heartRateSampleTimestamp"] = now
        }

        if (updates.isNotEmpty()) {
            updates["timestamp"] = now
            db.getReference("vitals/current/$userId").updateChildren(updates)
            Log.d(TAG, "Firebase HR updated for $userId")
        }
    }
}
