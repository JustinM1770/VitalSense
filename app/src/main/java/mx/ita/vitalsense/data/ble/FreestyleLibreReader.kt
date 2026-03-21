package mx.ita.vitalsense.data.ble

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.NfcV
import android.os.Build

class FreestyleLibreReader(private val context: Context) {

    fun isNfcAvailable(): Boolean {
        val nfcAdapter = NfcAdapter.getDefaultAdapter(context)
        return nfcAdapter != null && nfcAdapter.isEnabled
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

    private fun parseGlucoseFromResponse(data: ByteArray): Float? {
        // El Freestyle Libre codifica la glucosa en mg/dL en bytes específicos
        // Byte 5 y 6 del bloque de datos actual contienen la lectura
        if (data.size < 344) return null
        val rawGlucose = ((data[5].toInt() and 0xFF) or ((data[6].toInt() and 0x0F) shl 8))
        return rawGlucose * 0.18f // conversión a mg/dL aproximada
    }
}