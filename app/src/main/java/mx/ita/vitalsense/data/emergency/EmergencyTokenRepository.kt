package mx.ita.vitalsense.data.emergency

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await
import mx.ita.vitalsense.data.model.MedicalProfile
import java.security.MessageDigest
import java.util.UUID

/**
 * Resultado de crear un token de emergencia.
 * @param tokenId  UUID del token en Firebase
 * @param pin      PIN de 4 dígitos para el paramédico
 * @param webUrl   URL de la web de emergencia con el tokenId incluido
 */
data class EmergencyCreated(
    val tokenId: String,
    val pin: String,
    val webUrl: String,
)

/**
 * Datos que se almacenan en emergency_tokens/{tokenId} de Firebase.
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
 *       allow read: if true;
 *       allow write: if request.auth != null && request.auth.uid == userId;
 *     }
 *   }
 * }
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
    val nacimiento: String = "",
    val genero: String = "",
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
    val pin: String = "",
)

class EmergencyTokenRepository {

    private val db   = FirebaseDatabase.getInstance()
    private val auth = FirebaseAuth.getInstance()

    /** Duración del token: 30 minutos */
    private val ttlMs = 30 * 60 * 1000L

    /**
     * Crea un token de emergencia en Firebase y devuelve la URL web + PIN.
     * El QR codifica la URL web — cualquier navegador la abre sin app.
     */
    suspend fun createToken(anomalyType: String, heartRate: Int): Result<EmergencyCreated> = runCatching {
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

        // 2. Generar token + PIN
        val tokenId = UUID.randomUUID().toString()
        val pin     = (1000..9999).random().toString()
        val now     = System.currentTimeMillis()
        // SHA-256(pin + tokenId) — el tokenId actúa como sal única por token
        val pinHash = sha256(pin + tokenId)

        // 3. Escribir token en Firebase — pinHash en lugar de pin en texto plano
        db.getReference("emergency_tokens/$tokenId").setValue(
            mapOf(
                "tokenId"            to tokenId,
                "userId"             to userId,
                "nombre"             to profile.nombre,
                "apellidos"          to profile.apellidos,
                "nacimiento"         to profile.nacimiento,
                "genero"             to profile.genero,
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
                "pinHash"            to pinHash,   // ← nunca el PIN en claro
            )
        ).await()

        // 4. Escribir nodo activeEmergency para el reloj (sin PIN)
        db.getReference("patients/$userId/activeEmergency").setValue(
            mapOf(
                "tokenId"     to tokenId,
                "expiresAt"   to (now + ttlMs),
                "anomalyType" to anomalyType,
                "heartRate"   to heartRate,
            )
        ).await()

        EmergencyCreated(
            tokenId = tokenId,
            pin     = pin,
            webUrl  = "$WEB_BASE_URL/emergency.html?t=$tokenId",
        )
    }

    /** Lee los datos de un token para mostrarlos al paramédico (app Android). */
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
            nacimiento         = snap.child("nacimiento").getValue(String::class.java) ?: "",
            genero             = snap.child("genero").getValue(String::class.java) ?: "",
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
            pin                = snap.child("pin").getValue(String::class.java) ?: "",
        )
    }

    /** Verifica el PIN contra el hash almacenado en Firebase (sin leer el PIN en claro). */
    suspend fun verifyPin(tokenId: String, enteredPin: String): Result<Boolean> = runCatching {
        val snap = db.getReference("emergency_tokens/$tokenId/pinHash").get().await()
        val storedHash = snap.getValue(String::class.java) ?: return@runCatching false
        sha256(enteredPin + tokenId) == storedHash
    }

    /** Revoca el token y limpia el nodo del reloj. */
    suspend fun revokeToken(tokenId: String) {
        runCatching {
            db.getReference("emergency_tokens/$tokenId/active").setValue(false).await()
            val userId = db.getReference("emergency_tokens/$tokenId/userId")
                .get().await().getValue(String::class.java)
            if (!userId.isNullOrEmpty()) {
                db.getReference("patients/$userId/activeEmergency").removeValue().await()
            }
            db.getReference("emergency_tokens/$tokenId").removeValue().await()
        }
    }

    fun remainingSeconds(expiresAt: Long): Int =
        ((expiresAt - System.currentTimeMillis()) / 1000L).toInt().coerceAtLeast(0)

    private fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }

    companion object {
        /** URL base de Firebase Hosting. Actualizar después de `firebase deploy --only hosting`. */
        const val WEB_BASE_URL = "https://vitalsenseai-1cb9f.web.app"
    }
}
