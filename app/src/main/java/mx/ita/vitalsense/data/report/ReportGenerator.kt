package mx.ita.vitalsense.data.report

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import mx.ita.vitalsense.data.model.VitalsData
import mx.ita.vitalsense.data.model.VitalsSnapshot
import mx.ita.vitalsense.data.model.VitalsTrend
import mx.ita.vitalsense.data.model.TrendDirection
import mx.ita.vitalsense.data.model.computeAlerts
import mx.ita.vitalsense.data.model.computeStats
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ReportGenerator {

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale("es", "MX"))
    private val dateOnly   = SimpleDateFormat("dd/MM/yyyy",       Locale("es", "MX"))

    fun generate(
        context: Context,
        patient: VitalsData,
        history: List<VitalsSnapshot>,
        trend: VitalsTrend?,
    ): Uri {
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4
        val page = document.startPage(pageInfo)
        draw(page.canvas, patient, history, trend)
        document.finishPage(page)

        val dir = File(context.cacheDir, "reports").also { it.mkdirs() }
        val file = File(dir, "reporte_${patient.patientName.replace(" ", "_")}_${System.currentTimeMillis()}.pdf")
        document.writeTo(file.outputStream())
        document.close()

        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    private fun draw(
        canvas: Canvas,
        patient: VitalsData,
        history: List<VitalsSnapshot>,
        trend: VitalsTrend?,
    ) {
        // ── Paleta ────────────────────────────────────────────────────────────
        val blue        = Color.parseColor("#2D9CDB")
        val darkText    = Color.parseColor("#2C3E50")
        val grayText    = Color.parseColor("#7F8C8D")
        val redColor    = Color.parseColor("#E84C4C")
        val orangeColor = Color.parseColor("#FF9800")
        val greenColor  = Color.parseColor("#4CAF50")
        val alertYellow = Color.parseColor("#FFB300")

        val paintTitle = Paint().apply {
            color = darkText; textSize = 22f; isFakeBoldText = true; isAntiAlias = true
        }
        val paintSub = Paint().apply {
            color = grayText; textSize = 12f; isAntiAlias = true
        }
        val paintSection = Paint().apply {
            color = blue; textSize = 14f; isFakeBoldText = true; isAntiAlias = true
        }
        val paintBody = Paint().apply {
            color = darkText; textSize = 12f; isAntiAlias = true
        }
        val paintValue = Paint().apply {
            color = darkText; textSize = 20f; isFakeBoldText = true; isAntiAlias = true
        }
        val paintLine = Paint().apply {
            color = Color.parseColor("#E5E5E5"); strokeWidth = 1f; isAntiAlias = true
        }

        var y = 50f

        // ── Encabezado ────────────────────────────────────────────────────────
        val headerPaint = Paint().apply { color = blue }
        canvas.drawRect(0f, 0f, 595f, 80f, headerPaint)

        val titleWhite = Paint().apply {
            color = Color.WHITE; textSize = 24f; isFakeBoldText = true; isAntiAlias = true
        }
        canvas.drawText("HealthSensor", 30f, 35f, titleWhite)
        val subWhite = Paint().apply {
            color = Color.parseColor("#B6D8FF"); textSize = 11f; isAntiAlias = true
        }
        canvas.drawText("Reporte Clínico de Paciente · InnovaTecNM 2026", 30f, 55f, subWhite)
        canvas.drawText("Generado: ${dateFormat.format(Date())}", 30f, 70f, subWhite)

        y = 110f

        // ── Datos del paciente ────────────────────────────────────────────────
        canvas.drawText("Datos del Paciente", 30f, y, paintSection)
        y += 6f
        canvas.drawLine(30f, y, 565f, y, paintLine)
        y += 18f
        canvas.drawText("Nombre:", 30f, y, paintSub)
        canvas.drawText(patient.patientName, 150f, y, paintBody)
        y += 18f
        canvas.drawText("ID:", 30f, y, paintSub)
        canvas.drawText(patient.patientId.ifEmpty { "—" }, 150f, y, paintBody)
        y += 18f
        canvas.drawText("Última lectura:", 30f, y, paintSub)
        canvas.drawText(
            if (patient.timestamp > 0) dateFormat.format(Date(patient.timestamp)) else "—",
            150f, y, paintBody
        )

        y += 34f

        // ── Vitales actuales ──────────────────────────────────────────────────
        canvas.drawText("Vitales Actuales", 30f, y, paintSection)
        y += 6f
        canvas.drawLine(30f, y, 565f, y, paintLine)
        y += 22f

        // Tres cajas de vitales
        drawVitalBox(canvas, 30f, y, 165f, 80f,
            "Frecuencia Cardíaca", "${patient.heartRate}", "BPM",
            redColor, trendSymbol(trend?.heartRate))
        drawVitalBox(canvas, 210f, y, 165f, 80f,
            "Glucosa", "%.0f".format(patient.glucose), "mg/dL",
            orangeColor, trendSymbol(trend?.glucose))
        drawVitalBox(canvas, 390f, y, 165f, 80f,
            "Saturación O₂", "${patient.spo2}", "SpO₂ %",
            greenColor, trendSymbol(trend?.spo2))

        y += 100f

        // ── Estadísticas del historial ────────────────────────────────────────
        val stats = history.computeStats()
        if (stats != null) {
            canvas.drawText("Resumen Histórico (últimas ${history.size} lecturas)", 30f, y, paintSection)
            y += 6f
            canvas.drawLine(30f, y, 565f, y, paintLine)
            y += 18f

            val statPaint = Paint().apply { color = grayText; textSize = 11f; isAntiAlias = true }
            val statValPaint = Paint().apply { color = darkText; textSize = 11f; isFakeBoldText = true; isAntiAlias = true }

            // Encabezados
            canvas.drawText("Métrica", 30f, y, statPaint)
            canvas.drawText("Mínimo", 180f, y, statPaint)
            canvas.drawText("Promedio", 290f, y, statPaint)
            canvas.drawText("Máximo", 400f, y, statPaint)
            canvas.drawText("Rango Normal", 480f, y, statPaint)
            y += 6f
            canvas.drawLine(30f, y, 565f, y, paintLine)
            y += 16f

            canvas.drawText("Frec. Cardíaca", 30f, y, statValPaint)
            canvas.drawText("${stats.minHr}", 180f, y, statValPaint)
            canvas.drawText("${stats.avgHr}", 290f, y, statValPaint)
            canvas.drawText("${stats.maxHr}", 400f, y, statValPaint)
            canvas.drawText("60-100 BPM", 480f, y, statPaint)
            y += 16f

            canvas.drawText("Glucosa (mg/dL)", 30f, y, statValPaint)
            canvas.drawText("%.0f".format(stats.minGlucose), 180f, y, statValPaint)
            canvas.drawText("%.0f".format(stats.avgGlucose), 290f, y, statValPaint)
            canvas.drawText("%.0f".format(stats.maxGlucose), 400f, y, statValPaint)
            canvas.drawText("70-150 mg/dL", 480f, y, statPaint)
            y += 16f

            canvas.drawText("SpO₂ (%)", 30f, y, statValPaint)
            canvas.drawText("${stats.minSpo2}", 180f, y, statValPaint)
            canvas.drawText("${stats.avgSpo2}", 290f, y, statValPaint)
            canvas.drawText("${stats.maxSpo2}", 400f, y, statValPaint)
            canvas.drawText("≥ 90%", 480f, y, statPaint)

            y += 30f
        }

        // ── Alertas activas ───────────────────────────────────────────────────
        val alerts = patient.computeAlerts()
        if (alerts.isNotEmpty()) {
            canvas.drawText("Alertas Activas", 30f, y, paintSection)
            y += 6f
            canvas.drawLine(30f, y, 565f, y, paintLine)
            y += 18f
            alerts.forEach { alert ->
                val alertBgPaint = Paint().apply { color = Color.parseColor("#FFF8E1") }
                canvas.drawRect(30f, y - 12f, 565f, y + 8f, alertBgPaint)
                val alertPaint = Paint().apply { color = alertYellow; textSize = 11f; isFakeBoldText = true; isAntiAlias = true }
                val advicePaint = Paint().apply { color = Color.parseColor("#795548"); textSize = 11f; isAntiAlias = true }
                canvas.drawText("⚠ ${alert.title}", 36f, y, alertPaint)
                canvas.drawText(alert.advice, 240f, y, advicePaint)
                y += 24f
            }
            y += 6f
        } else {
            val okBg = Paint().apply { color = Color.parseColor("#E8F5E9") }
            canvas.drawRect(30f, y - 12f, 565f, y + 10f, okBg)
            val okPaint = Paint().apply { color = Color.parseColor("#4CAF50"); textSize = 12f; isFakeBoldText = true; isAntiAlias = true }
            canvas.drawText("✓ Todos los vitales dentro de rangos normales", 36f, y, okPaint)
            y += 30f
        }

        // ── Gráfica de texto del historial (sparkline textual) ────────────────
        if (history.isNotEmpty()) {
            y += 10f
            canvas.drawText("Tendencia de Glucosa (últimas lecturas)", 30f, y, paintSection)
            y += 6f
            canvas.drawLine(30f, y, 565f, y, paintLine)
            y += 18f
            drawTextSparkline(canvas, history.map { it.glucose }, 30f, y, 535f, 60f, orangeColor)
            y += 80f
        }

        // ── Pie de página ─────────────────────────────────────────────────────
        val footerY = 810f
        canvas.drawLine(30f, footerY, 565f, footerY, paintLine)
        val footerPaint = Paint().apply { color = grayText; textSize = 9f; isAntiAlias = true }
        canvas.drawText(
            "HealthSensor · Plataforma de Telemedicina · InnovaTecNM 2026 · Instituto Tecnológico Nacional de México",
            30f, footerY + 15f, footerPaint
        )
        canvas.drawText("Este reporte es generado automáticamente. Consulte a un médico para diagnóstico.", 30f, footerY + 28f, footerPaint)
    }

    private fun drawVitalBox(
        canvas: Canvas,
        x: Float, y: Float, w: Float, h: Float,
        label: String, value: String, unit: String,
        color: Int, trend: String,
    ) {
        val bgPaint = Paint().apply { this.color = Color.parseColor("#F9F9FB") }
        val borderPaint = Paint().apply { this.color = color; style = Paint.Style.STROKE; strokeWidth = 2f }
        canvas.drawRoundRect(x, y, x + w, y + h, 10f, 10f, bgPaint)
        canvas.drawRoundRect(x, y, x + w, y + h, 10f, 10f, borderPaint)

        val labelPaint = Paint().apply { this.color = Color.parseColor("#7F8C8D"); textSize = 9f; isAntiAlias = true }
        val valuePaint = Paint().apply { this.color = color; textSize = 26f; isFakeBoldText = true; isAntiAlias = true }
        val unitPaint  = Paint().apply { this.color = Color.parseColor("#7F8C8D"); textSize = 10f; isAntiAlias = true }
        val trendPaint = Paint().apply { this.color = color; textSize = 14f; isAntiAlias = true }

        canvas.drawText(label, x + 10f, y + 18f, labelPaint)
        canvas.drawText(value, x + 10f, y + 50f, valuePaint)
        canvas.drawText(unit, x + 10f, y + 65f, unitPaint)
        canvas.drawText(trend, x + w - 25f, y + 20f, trendPaint)
    }

    private fun drawTextSparkline(
        canvas: Canvas,
        values: List<Double>,
        x: Float, y: Float, w: Float, h: Float,
        color: Int,
    ) {
        if (values.size < 2) return
        val minV = values.min()
        val maxV = values.max()
        val range = maxV - minV
        if (range == 0.0) return

        val linePaint = Paint().apply {
            this.color = color; strokeWidth = 2f; isAntiAlias = true; style = Paint.Style.STROKE
        }
        val dotPaint = Paint().apply { this.color = color; isAntiAlias = true }

        val stepX = w / (values.size - 1)
        val points = values.mapIndexed { i, v ->
            Pair(x + i * stepX, y + h - ((v - minV) / range * h).toFloat())
        }

        for (i in 0 until points.size - 1) {
            canvas.drawLine(points[i].first, points[i].second, points[i + 1].first, points[i + 1].second, linePaint)
        }
        points.forEach { (px, py) -> canvas.drawCircle(px, py, 3f, dotPaint) }

        // Etiquetas min/max
        val labelP = Paint().apply { this.color = Color.parseColor("#7F8C8D"); textSize = 9f; isAntiAlias = true }
        canvas.drawText("%.0f".format(maxV), x + w + 4f, y + 4f, labelP)
        canvas.drawText("%.0f".format(minV), x + w + 4f, y + h, labelP)
    }

    private fun trendSymbol(dir: TrendDirection?) = when (dir) {
        TrendDirection.RISING  -> "↑"
        TrendDirection.FALLING -> "↓"
        else -> "→"
    }
}
