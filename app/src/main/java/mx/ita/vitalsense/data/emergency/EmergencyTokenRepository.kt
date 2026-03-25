package mx.ita.vitalsense.data.emergency

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await
import mx.ita.vitalsense.data.model.MedicalProfile
import java.util.UUID

/**
 * Datos que se almacenan en emergency_tokens/{tokenId} de Firebase.
 * Son un snapshot del perfil médico en el momento de la emergencia.
 *
 * Firebase Realtime Database Security Rules requeridas:
 * "emergency_tokens": {
 *   "$tokenId": {
 *     ".read": true,
 *     ".write": "auth != null && auth.uid === newData.child('userId').val()"
 *   }
 * }
 *
 * Firebase Storage Security Rules requeridas:
 * rules_version = '2';
 * service firebase.storage {
 *   match /b/{bucket}/o {
 *     match /documents/{userId}/{filename} {
 *       // Lectura pública para acceso de paramédicos desde QR (NOM-024-SSA3-2012)
 *       allow read: if true;
 *       // Solo el propietario autenticado puede subir sus documentos
 *       allow write: if request.auth != null && request.auth.uid == userId;
 *     }
 *   }
 * }
 */

/**
 * Documento médico almacenado en Firebase Storage.
 * @param nombre  Nombre del archivo (ej. "radiografia.jpg")
 * @param url     Download URL de Firebase Storage
 * @param tipo    "pdf" o "imagen"
 */
data class StorageDoc(
    val nombre: String = "",
    val url: String = "",
    val tipo: String = "",
)

data class EmergencyTokenData(
    val tokenId: String = "",
    val userId: String = "",
    val nombre: String = "",
    val apellidos: String = "",
    val tipoSangre: String = "",
    val alergias: String = "",
    val padecimientos: String = "",
    val medicamentos: String = "",
    val contactoEmergencia: String = "",
    val telefonoEmergencia: String = "",
    val anomalyType: String = "",
    val heartRateAtAlert: Int = 0,
    val createdAt: Long = 0L,
    val expiresAt: Long = 0L,
    val active: Boolean = true,
    val documentos: List<Map<String, String>> = emptyList(),
)

class EmergencyTokenRepository {

    private val db = FirebaseDatabase.getInstance()
    private val auth = FirebaseAuth.getInstance()

    /** Duración del token: 30 minutos */
    private val ttlMs = 30 * 60 * 1000L

    /**
     * Lee el MedicalProfile del usuario autenticado desde Firebase,
     * crea un token temporal en emergency_tokens/{uuid} y devuelve el tokenId.
     *
     * @param anomalyType  Descripción de la anomalía detectada por la IA (ej. "Taquicardia")
     * @param heartRate    Frecuencia cardíaca en el momento de la alerta
     */
    suspend fun createToken(anomalyType: String, heartRate: Int): Result<String> = runCatching {
        val userId = auth.currentUser?.uid
            ?: error("Usuario no autenticado — no se puede crear token de emergencia")

        // 1. Leer perfil médico del paciente
        val profileSnap = db.getReference("users/$userId/datosMedicos").get().await()
        val profile = profileSnap.getValue(MedicalProfile::class.java) ?: MedicalProfile()

        // 1b. Leer documentos de Firebase Storage
        val storageDocsSnap = db.getReference("patients/$userId/profile/storageDocuments").get().await()
        val storageDocsList = storageDocsSnap.children.mapNotNull { child ->
            val nombre = child.child("nombre").getValue(String::class.java) ?: return@mapNotNull null
            val url    = child.child("url").getValue(String::class.java)    ?: return@mapNotNull null
            val tipo   = child.child("tipo").getValue(String::class.java)   ?: "pdf"
            mapOf("nombre" to nombre, "url" to url, "tipo" to tipo)
        }

        // 2. Generar UUID como token
        val tokenId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        // 3. Construir el nodo del token (Map para Firebase)
        val tokenData = mapOf(
            "tokenId"            to tokenId,
            "userId"             to userId,
            "nombre"             to profile.nombre,
            "apellidos"          to profile.apellidos,
            "tipoSangre"         to profile.tipoSangre,
            "alergias"           to profile.alergias,
            "padecimientos"      to profile.padecimientos,
            "medicamentos"       to profile.medicamentos,
            "contactoEmergencia" to profile.contactoEmergencia,
            "telefonoEmergencia" to profile.telefonoEmergencia,
            "anomalyType"        to anomalyType,
            "heartRateAtAlert"   to heartRate,
            "createdAt"          to now,
            "expiresAt"          to (now + ttlMs),
            "active"             to true,
            "documentos"         to storageDocsList,
        )

        // 4. Escribir en Firebase
        db.getReference("emergency_tokens/$tokenId").setValue(tokenData).await()

        tokenId
    }

    /**
     * Lee los datos de un token de emergencia para mostrarlos al paramédico.
     * Valida que el token exista, esté activo y no haya expirado.
     */
    suspend fun readToken(tokenId: String): Result<EmergencyTokenData> = runCatching {
        val snap = db.getReference("emergency_tokens/$tokenId").get().await()
        check(snap.exists()) { "Token de emergencia no encontrado o ya expirado" }

        val active = snap.child("active").getValue(Boolean::class.java) ?: false
        check(active) { "Este token de emergencia ya fue revocado" }

        val expiresAt = snap.child("expiresAt").getValue(Long::class.java) ?: 0L
        check(System.currentTimeMillis() < expiresAt) { "El QR de emergencia ha expirado" }

        val documentos = snap.child("documentos").children.mapNotNull { child ->
            val nombre = child.child("nombre").getValue(String::class.java) ?: return@mapNotNull null
            val url    = child.child("url").getValue(String::class.java)    ?: return@mapNotNull null
            val tipo   = child.child("tipo").getValue(String::class.java)   ?: "pdf"
            mapOf("nombre" to nombre, "url" to url, "tipo" to tipo)
        }

        EmergencyTokenData(
            tokenId            = tokenId,
            userId             = snap.child("userId").getValue(String::class.java) ?: "",
            nombre             = snap.child("nombre").getValue(String::class.java) ?: "",
            apellidos          = snap.child("apellidos").getValue(String::class.java) ?: "",
            tipoSangre         = snap.child("tipoSangre").getValue(String::class.java) ?: "",
            alergias           = snap.child("alergias").getValue(String::class.java) ?: "",
            padecimientos      = snap.child("padecimientos").getValue(String::class.java) ?: "",
            medicamentos       = snap.child("medicamentos").getValue(String::class.java) ?: "",
            contactoEmergencia = snap.child("contactoEmergencia").getValue(String::class.java) ?: "",
            telefonoEmergencia = snap.child("telefonoEmergencia").getValue(String::class.java) ?: "",
            anomalyType        = snap.child("anomalyType").getValue(String::class.java) ?: "",
            heartRateAtAlert   = snap.child("heartRateAtAlert").getValue(Int::class.java) ?: 0,
            createdAt          = snap.child("createdAt").getValue(Long::class.java) ?: 0L,
            expiresAt          = expiresAt,
            active             = active,
            documentos         = documentos,
        )
    }

    /**
     * Revoca el token activo: lo marca como inactivo y lo elimina de Firebase.
     * Llamar cuando la emergencia sea resuelta o el usuario cierre la pantalla de QR.
     */
    suspend fun revokeToken(tokenId: String) {
        runCatching {
            db.getReference("emergency_tokens/$tokenId/active").setValue(false).await()
            db.getReference("emergency_tokens/$tokenId").removeValue().await()
        }
    }

    /** Calcula los segundos que quedan hasta que expire el token. */
    fun remainingSeconds(expiresAt: Long): Int =
        ((expiresAt - System.currentTimeMillis()) / 1000L).toInt().coerceAtLeast(0)
}