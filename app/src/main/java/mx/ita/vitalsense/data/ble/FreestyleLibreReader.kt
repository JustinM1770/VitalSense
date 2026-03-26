package mx.ita.vitalsense.data.ble

import android.app.Activity
import android.app.PendingIntent
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.NfcV
import android.os.Build

class FreestyleLibreReader(private val context: Context) {

    fun isNfcSupported(): Boolean {
        return NfcAdapter.getDefaultAdapter(context) != null
    }

    fun isNfcEnabled(): Boolean {
        val nfcAdapter = NfcAdapter.getDefaultAdapter(context) ?: return false
        return nfcAdapter.isEnabled
    }

    fun isNfcAvailable(): Boolean {
        return isNfcSupported() && isNfcEnabled()
    }

    fun openNfcSettings() {
        val intent = Intent(android.provider.Settings.ACTION_NFC_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            val fallback = Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(fallback)
        }
    }

    fun enableNfcReading(activity: Activity) {
        val nfcAdapter = NfcAdapter.getDefaultAdapter(context) ?: return
        val pendingIntent = PendingIntent.getActivity(
            activity, 0,
            Intent(activity, activity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val filters = arrayOf(IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED))
        val techList = arrayOf(arrayOf(NfcV::class.java.name)) // Freestyle Libre usa NfcV (ISO 15693)
        nfcAdapter.enableForegroundDispatch(activity, pendingIntent, filters, techList)
    }

    fun disableNfcReading(activity: Activity) {
        NfcAdapter.getDefaultAdapter(context)?.disableForegroundDispatch(activity)
    }

    // Llamar desde onNewIntent de MainActivity cuando llega tag NFC
    fun parseFreestyleLibre(intent: Intent): Float? {
        if (intent.action != NfcAdapter.ACTION_TECH_DISCOVERED) return null
        val tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        } ?: return null
        val nfcV = NfcV.get(tag) ?: return null

        return try {
            nfcV.connect()
            // Leer 40 bloques del sensor (cada bloque = 8 bytes)
            val readCmd = byteArrayOf(0x60.toByte(), 0x01, 0, 0, 0, 0, 0, 0, 0x27, 0)
            val response = nfcV.transceive(readCmd)
            nfcV.close()
            parseGlucoseFromResponse(response)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Extrae la glucosa en mg/dL del frame NFC del sensor Freestyle Libre 1.
     *
     * Estructura de la respuesta NfcV (ISO 15693):
     * - Byte 0    : flags de respuesta
     * - Bytes 1–8 : Bloque 0 (8 bytes) — contiene índices de frame y metadatos del sensor
     * - Bytes 9+  : Bloques 1–N de datos de tendencia e historial
     *
     * Posición del valor de glucosa actual:
     * - FRAM[4..5] (= response[5..6]): valor crudo de 12 bits de la lectura actual.
     *   Formato little-endian: byte bajo en [4], nibble alto en bits 0–3 de [5].
     *   Unidades: mg/dL (factor de escala ~0.18 respecto al ADC interno del sensor).
     *
     * Referencias comunitarias (protocolo no oficial, sin publicación Abbott):
     * - LibreMonitor (GitHub: UPetersen/LibreMonitor) — especificación FRAM Libre 1
     * - xDrip+ (GitHub: NightscoutFoundation/xDrip) — NFC reader Libre 1
     *
     * IMPORTANTE: El factor de conversión y los offsets pueden diferir entre
     * generaciones del sensor (Libre 1, 2 y 3). Se recomienda validar con
     * hardware físico antes de uso clínico.
     *
     * @param data Respuesta raw de [NfcV.transceive]; debe incluir el byte de flags.
     * @return Glucosa en mg/dL si el valor está en rango fisiológico plausible (20–600),
     *         o `null` si la respuesta es inválida o el valor está fuera de rango.
     */
    private fun parseGlucoseFromResponse(data: ByteArray): Float? {
        // Mínimo esperado: 1 byte flags + 40 bloques × 8 bytes = 321 bytes
        if (data.size < 321) return null

        // Valor crudo de 12 bits: byte 5 (bits 0–7) + nibble bajo de byte 6 (bits 8–11)
        val rawGlucose = (data[5].toInt() and 0xFF) or ((data[6].toInt() and 0x0F) shl 8)

        // Conversión a mg/dL (factor empírico documentado para Libre 1)
        val glucoseMgdL = rawGlucose * 0.18f

        // Validación de rango fisiológico plausible (ADA: emergencia < 20 o > 600 mg/dL
        // indica error de lectura, no valor real)
        return if (glucoseMgdL in 20f..600f) glucoseMgdL else null
    }
}