package mx.ita.vitalsense.data.sos

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.database.database
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock

object SosManager {

    private val db = Firebase.database
    private val auth = Firebase.auth

    suspend fun enviarSOS(patientName: String) {
        val userId = auth.currentUser?.uid ?: return
        val timestamp = Clock.System.now().toEpochMilliseconds()

        // Escribir en Firebase — el tutor escucha este nodo
        val sosRef = db.reference("sos/$userId")
        sosRef.setValue(mapOf(
            "activo" to true,
            "timestamp" to timestamp,
            "paciente" to patientName,
            "mensaje" to "⚠️ SOS activado por $patientName"
        ))

        // También crear notificación en el historial
        db.reference("users/$userId/notificaciones").push().setValue(mapOf(
            "titulo" to "🆘 SOS Activado",
            "descripcion" to "$patientName necesita ayuda inmediata",
            "tipo" to "SOS",
            "timestamp" to timestamp,
            "leida" to false
        ))
    }

    // El tutor escucha este nodo en tiempo real
    fun escucharSOS(userId: String): Flow<Boolean> {
        return db.reference("sos/$userId/activo").valueEvents.map { snapshot ->
            snapshot.value<Boolean?>() ?: false
        }
    }
}
