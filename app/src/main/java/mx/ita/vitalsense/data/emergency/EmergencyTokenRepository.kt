package mx.ita.vitalsense.data.emergency

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await
import mx.ita.vitalsense.data.model.MedicalProfile
import java.util.UUID

/** Resultado de crear un token: tokenId + PIN de 4 dígitos para el paramédico. */
data class EmergencyCreated(val tokenId: String, val pin: String)

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
    /** PIN de 4 dígitos generado al crear el token. Necesario para acceder al perfil médico. */
    val pin: String = "",
)

class EmergencyTokenRepository {

    private val db = FirebaseDatabase.getInstance()
    private val auth = FirebaseAuth.getInstance()

    /** Duración del token: 30 minutos */
    private val ttlMs = 30 * 60 * 1000L

    /**
     * Lee el MedicalProfile del usuario autenticado desde Firebase, crea un token temporal
     * en emergency_tokens/{uuid} con un PIN de 4 dígitos, escribe el nodo activeEmergency
     * para que el reloj muestre el QR automáticamente, y devuelve [EmergencyCreated].
     *
     * @param anomalyType  Descripción de la anomalía (ej. "Taquicardia")
     * @param heartRate    Frecuencia cardíaca en el momento de la alerta
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

        // 2. Generar UUID como token + PIN de 4 dígitos
        val tokenId = UUID.randomUUID().toString()
        val pin = (1000..9999).random().toString()
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
            // El PIN se almacena en el token para que el servidor de Twilio pueda leerlo.
            // La app del paramédico solo puede ingresar el PIN; no lo lee directamente de aquí.
            "pin"                to pin,
        )

        // 4. Escribir token de emergencia en Firebase
        db.getReference("emergency_tokens/$tokenId").setValue(tokenData).await()

        // 5. Escribir nodo activeEmergency para que el reloj muestre el QR automáticamente
        db.getReference("patients/$userId/activeEmergency").setValue(
            mapOf(
                "tokenId"     to tokenId,
                "pin"         to pin,
                "expiresAt"   to (now + ttlMs),
                "anomalyType" to anomalyType,
                "heartRate"   to heartRate,
            )
        ).await()

        EmergencyCreated(tokenId = tokenId, pin = pin)
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
            pin                = snap.child("pin").getValue(String::class.java) ?: "",
        )
    }

    /**
     * Verifica que el PIN ingresado por el paramédico coincida con el del token.
     * Devuelve true si es correcto.
     */
    suspend fun verifyPin(tokenId: String, enteredPin: String): Result<Boolean> = runCatching {
        val snap = db.getReference("emergency_tokens/$tokenId/pin").get().await()
        val storedPin = snap.getValue(String::class.java) ?: return@runCatching false
        storedPin == enteredPin
    }

    /**
     * Revoca el token activo: lo marca como inactivo y lo elimina de Firebase.
     * También elimina el nodo activeEmergency del reloj.
     * Llamar cuando la emergencia sea resuelta o el usuario cierre la pantalla de QR.
     */
    suspend fun revokeToken(tokenId: String) {
        runCatching {
            db.getReference("emergency_tokens/$tokenId/active").setValue(false).await()

            // Leer userId para limpiar el nodo del reloj
            val userId = db.getReference("emergency_tokens/$tokenId/userId")
                .get().await().getValue(String::class.java)
            if (!userId.isNullOrEmpty()) {
                db.getReference("patients/$userId/activeEmergency").removeValue().await()
            }

            db.getReference("emergency_tokens/$tokenId").removeValue().await()
        }
    }

    /** Calcula los segundos que quedan hasta que expire el token. */
    fun remainingSeconds(expiresAt: Long): Int =
        ((expiresAt - System.currentTimeMillis()) / 1000L).toInt().coerceAtLeast(0)
}