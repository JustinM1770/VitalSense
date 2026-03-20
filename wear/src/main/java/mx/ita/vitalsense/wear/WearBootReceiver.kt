package mx.ita.vitalsense.wear

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class WearBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences("vitalsense_wear_prefs", Context.MODE_PRIVATE)
            val isPaired = prefs.getBoolean("is_paired", false)
            if (isPaired) {
                val serviceIntent = Intent(context, VitalSignsService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            }
        }
    }
}
