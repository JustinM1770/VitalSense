package mx.ita.vitalsense.data.sos

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.database.database
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

actual class SosManager actual constructor() {

    private val db = Firebase.database

    actual fun enviarSOS(userId: String, patientName: String) {
        GlobalScope.launch {
            val sosRef = db.reference("sos/$userId")
            sosRef.setValue(mapOf(
                "activo" to true,
                "timestamp" to kotlinx.datetime.Clock.System.now().toEpochMilliseconds(),
                "paciente" to patientName,
                "mensaje" to "SOS activado por $patientName"
            ))

            db.reference("users/$userId/notificaciones").push().setValue(mapOf(
                "titulo" to "SOS Activado",
                "descripcion" to "$patientName necesita ayuda inmediata",
                "tipo" to "SOS",
                "leida" to false
            ))
        }
    }

    actual fun escucharSOS(userId: String, onAlerta: (String) -> Unit) {
        GlobalScope.launch {
            db.reference("sos/$userId/activo")
                .valueEvents
                .collect { snapshot ->
                    val activo = snapshot.value<Boolean?>() ?: false
                    if (activo) onAlerta("SOS activado")
                }
        }
    }
}
