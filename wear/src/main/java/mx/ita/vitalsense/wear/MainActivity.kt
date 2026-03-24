package mx.ita.vitalsense.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.wear.ambient.AmbientLifecycleObserver
import mx.ita.vitalsense.wear.presentation.WearApp

class MainActivity : ComponentActivity() {

    private val isAmbient = mutableStateOf(false)
    private val openSosFromNotification = mutableStateOf(false)
    private val pendingSosId = mutableStateOf<String?>(null)
    private val pendingSosUserId = mutableStateOf<String?>(null)

    private val ambientCallback = object : AmbientLifecycleObserver.AmbientLifecycleCallback {
        override fun onEnterAmbient(ambientDetails: AmbientLifecycleObserver.AmbientDetails) {
            isAmbient.value = true
        }

        override fun onExitAmbient() {
            isAmbient.value = false
        }

        override fun onUpdateAmbient() {
            // Se invoca ocasionalmente para actualizar la pantalla en modo ambiente
        }
    }

    private lateinit var ambientObserver: AmbientLifecycleObserver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        readSosIntent(intent)
        
        ambientObserver = AmbientLifecycleObserver(this, ambientCallback)
        lifecycle.addObserver(ambientObserver)

        setContent {
            WearApp(
                isAmbient = isAmbient.value,
                openSosFromNotification = openSosFromNotification.value,
                initialSosId = pendingSosId.value,
                initialSosUserId = pendingSosUserId.value,
                onSosNotificationConsumed = {
                    openSosFromNotification.value = false
                    pendingSosId.value = null
                    pendingSosUserId.value = null
                },
            )
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readSosIntent(intent)
    }

    private fun readSosIntent(intent: android.content.Intent?) {
        val src = intent ?: return
        if (!src.getBooleanExtra("open_sos", false)) return
        openSosFromNotification.value = true
        pendingSosId.value = src.getStringExtra("sos_id")
        pendingSosUserId.value = src.getStringExtra("sos_user_id")
    }
}
