package mx.ita.vitalsense.wear

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.wear.ambient.AmbientLifecycleObserver
import mx.ita.vitalsense.wear.presentation.WearApp

class MainActivity : ComponentActivity() {

    private val isAmbient = mutableStateOf(false)

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
        
        ambientObserver = AmbientLifecycleObserver(this, ambientCallback)
        lifecycle.addObserver(ambientObserver)

        setContent {
            WearApp(isAmbient.value)
        }
    }
}
