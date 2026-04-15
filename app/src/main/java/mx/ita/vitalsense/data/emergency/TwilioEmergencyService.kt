package mx.ita.vitalsense.data.emergency

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Dispara notificaciones de emergencia (llamada, SMS y WhatsApp)
 * a través de una Firebase Cloud Function que usa la API de Twilio.
 */
class TwilioEmergencyService {

    private val db   = FirebaseDatabase.getInstance()
    private val auth = FirebaseAuth.getInstance()

    /**
        * Invoca la Cloud Function de Twilio para notificar por múltiples canales.
     *
     * @param tokenId      UUID del token de emergencia
     * @param anomalyType  Tipo de anomalía (ej. "Taquicardia")
     * @param heartRate    BPM al momento de la alerta
     * @param pin          PIN de 4 dígitos para que el paramédico acceda al perfil
     * @param lat          Latitud GPS (opcional, 0.0 si no disponible)
     * @param lng          Longitud GPS (opcional, 0.0 si no disponible)
     */
    suspend fun triggerEmergencyCall(
        tokenId: String,
        anomalyType: String,
        heartRate: Int,
        pin: String,
        lat: Double = 0.0,
        lng: Double = 0.0,
    ) {
        runCatching {
            val userId = auth.currentUser?.uid ?: return@runCatching

            // Leer nombre del paciente y teléfono de emergencia desde Firebase
            val profileSnap = db.getReference("users/$userId/datosMedicos").get().await()
            val patientName = buildString {
                append(profileSnap.child("nombre").getValue(String::class.java) ?: "")
                val ap = profileSnap.child("apellidos").getValue(String::class.java) ?: ""
                if (ap.isNotEmpty()) append(" $ap")
            }.trim().ifEmpty { "el paciente" }

            val emergencyPhone = profileSnap
                .child("telefonoEmergencia").getValue(String::class.java) ?: ""

            val contacts = buildEmergencyContacts(profileSnap)

            if (emergencyPhone.isEmpty() && contacts.isEmpty()) return@runCatching

            val payload = JSONObject().apply {
                put("tokenId",      tokenId)
                put("patientName",  patientName)
                put("anomalyType",  anomalyType)
                put("heartRate",    heartRate)
                put("pin",          pin)
                put("webUrl",       "$WEB_BASE_URL/emergency.html?t=$tokenId")
                put("lat",          lat)
                put("lng",          lng)
                put("toPhone",      emergencyPhone)
                put("retryWaitSecs", 18)
                put(
                    "contacts",
                    org.json.JSONArray().apply {
                        contacts.forEach { contact ->
                            put(
                                JSONObject().apply {
                                    put("name", contact.name)
                                    put("phone", contact.phone)
                                }
                            )
                        }
                    }
                )
            }

            val url  = URL(CLOUD_FUNCTION_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput      = true
            conn.connectTimeout = 10_000
            conn.readTimeout    = 15_000

            OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }
            conn.responseCode
            conn.disconnect()
        }
        // No relanzar: un fallo en Twilio no debe interrumpir el flujo de emergencia
    }

    companion object {
        private const val WEB_BASE_URL = "https://vitalsenseai-1cb9f.web.app"

        /**
         * URL de la Firebase Cloud Function que llama a Twilio.
         * Obtenerla tras ejecutar: `firebase deploy --only functions`
         */
        private const val CLOUD_FUNCTION_URL =
            "https://us-central1-vitalsenseai-1cb9f.cloudfunctions.net/triggerEmergencyCall"
    }

    private data class EmergencyContact(
        val name: String,
        val phone: String,
    )

    private fun buildEmergencyContacts(profileSnap: com.google.firebase.database.DataSnapshot): List<EmergencyContact> {
        val listContacts = mutableListOf<EmergencyContact>()

        // Nuevo formato: emergencyContacts = [{ name, phone }, ...]
        profileSnap.child("emergencyContacts").children.forEachIndexed { index, child ->
            val name = child.child("name").getValue(String::class.java)
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: if (index == 0) "Contacto principal" else "Contacto respaldo ${index + 1}"
            val phone = child.child("phone").getValue(String::class.java).orEmpty().trim()
            if (phone.isNotBlank()) {
                listContacts += EmergencyContact(name = name, phone = phone)
            }
        }

        // Alternativa soportada: telefonosEmergencia = ["+52...", "+52..."]
        profileSnap.child("telefonosEmergencia").children.forEachIndexed { index, child ->
            val phone = child.getValue(String::class.java).orEmpty().trim()
            if (phone.isNotBlank()) {
                val name = if (index == 0) "Contacto principal" else "Contacto respaldo ${index + 1}"
                listContacts += EmergencyContact(name = name, phone = phone)
            }
        }

        if (listContacts.isNotEmpty()) {
            return listContacts
                .flatMap { contact ->
                    contact.phone
                        .split(",", ";", "|", " ")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .map { phone -> EmergencyContact(contact.name, phone) }
                }
                .distinctBy { it.phone }
        }

        // Formato legado (compatibilidad)
        val primaryName = profileSnap.child("contactoEmergencia").getValue(String::class.java).orEmpty().trim()
        val primaryPhone = profileSnap.child("telefonoEmergencia").getValue(String::class.java).orEmpty().trim()

        val secondaryName = sequenceOf(
            "contactoEmergenciaSecundario",
            "contactoEmergencia2",
            "contactoEmergenciaBackup"
        ).mapNotNull { key ->
            profileSnap.child(key).getValue(String::class.java)?.trim()?.takeIf { it.isNotEmpty() }
        }.firstOrNull().orEmpty()

        val secondaryPhone = sequenceOf(
            "telefonoEmergenciaSecundario",
            "telefonoEmergencia2",
            "telefonoEmergenciaBackup"
        ).mapNotNull { key ->
            profileSnap.child(key).getValue(String::class.java)?.trim()?.takeIf { it.isNotEmpty() }
        }.firstOrNull().orEmpty()

        val contacts = mutableListOf<EmergencyContact>()
        contacts += EmergencyContact(
            name = primaryName.ifBlank { "Contacto principal" },
            phone = primaryPhone,
        )
        if (secondaryPhone.isNotBlank()) {
            contacts += EmergencyContact(
                name = secondaryName.ifBlank { "Contacto de respaldo" },
                phone = secondaryPhone,
            )
        }

        return contacts
            .flatMap { contact ->
                contact.phone
                    .split(",", ";", "|", " ")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .map { phone -> EmergencyContact(contact.name, phone) }
            }
            .distinctBy { it.phone }
    }
}
