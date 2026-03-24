package mx.ita.vitalsense.data.emergency

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter

/**
 * Genera un Bitmap de código QR a partir de un String.
 * Usa solo el core de ZXing — sin dependencias de Activity ni View.
 */
object QrCodeGenerator {

    /**
     * @param content  Texto o URL a codificar (ej. "vitalsense://emergency/abc123")
     * @param sizePx   Tamaño del bitmap cuadrado en píxeles (default 800)
     * @return         Bitmap ARGB_8888 listo para mostrarse en un Image composable
     */
    fun generate(content: String, sizePx: Int = 800): Bitmap {
        val hints = mapOf(
            EncodeHintType.MARGIN to 2,
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.ERROR_CORRECTION to com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.M
        )
        val matrix = MultiFormatWriter().encode(
            content,
            BarcodeFormat.QR_CODE,
            sizePx,
            sizePx,
            hints
        )
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }
}