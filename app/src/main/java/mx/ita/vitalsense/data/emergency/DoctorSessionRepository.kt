package mx.ita.vitalsense.data.emergency

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await
import mx.ita.vitalsense.data.model.MedicalProfile
import java.util.UUID

data class DoctorSessionCreated(
    val sessionId: String,
    val webUrl: String,
    val expiresAt: Long,
)

/**
 * Crea sesiones de "portal médico" en Firebase.
 * El UUID actúa como token — solo quien tenga el QR puede acceder.
 * TTL: 2 horas.
 */
class DoctorSessionRepository {

    private val db   = FirebaseDatabase.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val ttlMs = 2 * 60 * 60 * 1000L  // 2 hours

    suspend fun createSession(): Result<DoctorSessionCreated> = runCatching {
        val userId = auth.currentUser?.uid
            ?: error("Usuario no autenticado")

        // Perfil médico
        val profileSnap = db.getReference("users/$userId/datosMedicos").get().await()
        val profile = profileSnap.getValue(MedicalProfile::class.java) ?: MedicalProfile()

        // Últimas 20 lecturas de historial
        val historySnap = db.getReference("patients/$userId/history")
            .orderByKey().limitToLast(20)
            .get().await()

        val historyList = historySnap.children.mapNotNull { child ->
            val hr      = child.child("heartRate").getValue(Int::class.java)    ?: return@mapNotNull null
            val spo2    = child.child("spo2").getValue(Int::class.java)         ?: 0
            val glucose = child.child("glucose").getValue(Double::class.java)   ?: 0.0
            val ts      = child.child("timestamp").getValue(Long::class.java)   ?: 0L
            mapOf("heartRate" to hr, "spo2" to spo2, "glucose" to glucose, "timestamp" to ts)
        }

        val latestVitals = historyList.lastOrNull() ?: emptyMap<String, Any>()

        // Último insight de IA (si existe)
        val aiSnap     = db.getReference("patients/$userId/lastAiInsight").get().await()
        val aiInsight  = aiSnap.getValue(String::class.java) ?: ""

        val sessionId  = UUID.randomUUID().toString()
        val now        = System.currentTimeMillis()

        db.getReference("doctor_sessions/$sessionId").setValue(
            mapOf(
                "sessionId"           to sessionId,
                "patientId"           to userId,
                "patientName"         to "${profile.nombre} ${profile.apellidos}".trim(),
                "tipoSangre"          to profile.tipoSangre,
                "alergias"            to profile.alergias,
                "padecimientos"       to profile.padecimientos,
                "medicamentos"        to profile.medicamentos,
                "telefonoEmergencia"  to profile.telefonoEmergencia,
                "contactoEmergencia"  to profile.contactoEmergencia,
                "aiInsight"           to aiInsight,
                "vitals"              to latestVitals,
                "history"             to historyList,
                "createdAt"           to now,
                "expiresAt"           to (now + ttlMs),
            )
        ).await()

        DoctorSessionCreated(
            sessionId = sessionId,
            webUrl    = "$WEB_BASE_URL/medico.html?s=$sessionId",
            expiresAt = now + ttlMs,
        )
    }

    /** Actualiza los vitales en la sesión activa cuando llegan datos nuevos. */
    suspend fun updateVitals(sessionId: String, heartRate: Int, spo2: Int, glucose: Double) {
        runCatching {
            db.getReference("doctor_sessions/$sessionId/vitals").setValue(
                mapOf(
                    "heartRate" to heartRate,
                    "spo2"      to spo2,
                    "glucose"   to glucose,
                    "timestamp" to System.currentTimeMillis(),
                )
            ).await()
        }
    }

    /** Elimina la sesión (cuando el paciente cierra el portal). */
    suspend fun revokeSession(sessionId: String) {
        runCatching {
            db.getReference("doctor_sessions/$sessionId").removeValue().await()
        }
    }

    companion object {
        const val WEB_BASE_URL = "https://vitalsenseai-1cb9f.web.app"
    }
}
