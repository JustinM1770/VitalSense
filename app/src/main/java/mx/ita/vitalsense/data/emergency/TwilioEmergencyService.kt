package mx.ita.vitalsense.data.emergency

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Dispara la llamada de voz de emergencia a través de una Firebase Cloud Function
 * que usa la API de Twilio.
 *
 * ## Modo prueba (TEST_MODE = true)
 * No realiza ninguna llamada real ni gasta créditos de Twilio.
 * En su lugar escribe en `emergency_call_logs/{tokenId}` de Firebase el mensaje
 * completo que se habría pronunciado, y lo imprime en Logcat (tag: TwilioTest).
 * Útil para demostrar el flujo sin necesidad de cuenta Twilio activa.
 *
 * ## Modo producción (TEST_MODE = false)
 * POST a la Firebase Cloud Function (`functions/index.js`) que llama a Twilio.
 * Configurar `CLOUD_FUNCTION_URL` con la URL real tras hacer deploy.
 */
class TwilioEmergencyService {

    private val db   = FirebaseDatabase.getInstance()
    private val auth = FirebaseAuth.getInstance()

    /**
     * Llama a la Cloud Function (o simula la llamada en modo prueba).
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

            // Construir el mensaje exacto que pronunciaría Twilio
            val pinSpoken = pin.chunked(1).joinToString(", ")
            val locationText = if (lat != 0.0 && lng != 0.0)
                "Coordenadas GPS: latitud ${"%.4f".format(lat)}, longitud ${"%.4f".format(lng)}."
            else "Ubicación GPS no disponible."

            val spokenMessage = buildString {
                append("Alerta de BioMetric. ")
                append("El usuario $patientName presenta una anomalía de $anomalyType. ")
                append("Frecuencia cardíaca: $heartRate latidos por minuto. ")
                append("$locationText ")
                append("Para acceder a su historial clínico en el dispositivo, ")
                append("use el PIN: $pinSpoken.")
            }

            if (TEST_MODE) {
                // ── MODO PRUEBA ──────────────────────────────────────────────
                // Escribe el log en Firebase para que puedas verlo en la consola
                db.getReference("emergency_call_logs/$tokenId").setValue(
                    mapOf(
                        "timestamp"     to System.currentTimeMillis(),
                        "toPhone"       to emergencyPhone.ifEmpty { "SIN TELÉFONO REGISTRADO" },
                        "patientName"   to patientName,
                        "anomalyType"   to anomalyType,
                        "heartRate"     to heartRate,
                        "pin"           to pin,
                        "spokenMessage" to spokenMessage,
                        "testMode"      to true,
                    )
                ).await()

                // Imprime en Logcat: filtra por tag "TwilioTest" en Android Studio
                Log.w(TAG_TEST, "══════════════════════════════════════════════")
                Log.w(TAG_TEST, "  SIMULACIÓN DE LLAMADA TWILIO (modo prueba)")
                Log.w(TAG_TEST, "  Para: $emergencyPhone")
                Log.w(TAG_TEST, "  Mensaje (×3):")
                Log.w(TAG_TEST, "  $spokenMessage")
                Log.w(TAG_TEST, "══════════════════════════════════════════════")
            } else {
                // ── MODO PRODUCCIÓN ──────────────────────────────────────────
                if (emergencyPhone.isEmpty()) return@runCatching

                val payload = JSONObject().apply {
                    put("tokenId",      tokenId)
                    put("patientName",  patientName)
                    put("anomalyType",  anomalyType)
                    put("heartRate",    heartRate)
                    put("pin",          pin)
                    put("lat",          lat)
                    put("lng",          lng)
                    put("toPhone",      emergencyPhone)
                }

                val url  = URL(CLOUD_FUNCTION_URL)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput      = true
                conn.connectTimeout = 10_000
                conn.readTimeout    = 15_000

                OutputStreamWriter(conn.outputStream).use { it.write(payload.toString()) }
                conn.responseCode   // consume response
                conn.disconnect()
            }
        }
        // No relanzar: un fallo en Twilio no debe interrumpir el flujo de emergencia
    }

    companion object {
        /**
         * Cambia a `false` cuando tengas Twilio configurado y hayas hecho deploy
         * de la Cloud Function en `functions/index.js`.
         */
        const val TEST_MODE = false

        /**
         * URL de la Firebase Cloud Function que llama a Twilio.
         * Obtenerla tras ejecutar: `firebase deploy --only functions`
         */
        private const val CLOUD_FUNCTION_URL =
            "https://us-central1-vitalsenseai-1cb9f.cloudfunctions.net/triggerEmergencyCall"

        private const val TAG_TEST = "TwilioTest"
    }
}
