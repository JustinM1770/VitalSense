package mx.ita.vitalsense.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.ita.vitalsense.data.model.VitalsSnapshot
import mx.ita.vitalsense.ui.theme.GlucoseOrange
import mx.ita.vitalsense.ui.theme.HeartRateRed
import mx.ita.vitalsense.ui.theme.Manrope
import mx.ita.vitalsense.ui.theme.NeomorphicBackground
import mx.ita.vitalsense.ui.theme.SpO2Green
import mx.ita.vitalsense.ui.theme.TextMuted
import mx.ita.vitalsense.ui.theme.TextSecondary

enum class ChartMetric { HEART_RATE, GLUCOSE, SPO2 }

private data class ChartData(
    val values: List<Float>,
    val color: Color,
    val label: String,
    val unit: String,
)

@Composable
fun VitalsLineChart(
    snapshots: List<VitalsSnapshot>,
    modifier: Modifier = Modifier,
    activeMetric: ChartMetric = ChartMetric.GLUCOSE,
) {
    if (snapshots.isEmpty()) {
        val textColor = MaterialTheme.colorScheme.onSurfaceVariant
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(160.dp)
                .background(if (isSystemInDarkTheme()) MaterialTheme.colorScheme.surface else NeomorphicBackground),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "Sin historial aún — conecta un wearable o espera lecturas de Firebase",
                fontFamily = Manrope,
                fontSize = 12.sp,
                color = textColor,
                modifier = Modifier.padding(16.dp),
            )
        }
        return
    }

    val chartData = when (activeMetric) {
        ChartMetric.HEART_RATE -> ChartData(
            values = snapshots.map { it.heartRate.toFloat() },
            color  = HeartRateRed,
            label  = "Frec. Cardíaca",
            unit   = "BPM",
        )
        ChartMetric.GLUCOSE -> ChartData(
            values = snapshots.map { it.glucose.toFloat() },
            color  = GlucoseOrange,
            label  = "Glucosa",
            unit   = "mg/dL",
        )
        ChartMetric.SPO2 -> ChartData(
            values = snapshots.map { it.spo2.toFloat() },
            color  = SpO2Green,
            label  = "SpO₂",
            unit   = "%",
        )
    }

    val values = chartData.values
    val color  = chartData.color
    val label  = chartData.label
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant

    val minVal = values.min()
    val maxVal = values.max()
    val range  = if (maxVal - minVal > 0f) maxVal - minVal else 1f

    Column(modifier = modifier) {

        // ── Leyenda superior ──────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(color),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    label,
                    fontFamily = Manrope,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = labelColor,
                )
            }
            Text(
                "${snapshots.size} lecturas",
                fontFamily = Manrope,
                fontSize = 11.sp,
                color = mutedColor,
            )
        }

        // ── Gráfica: etiquetas Y + Canvas ────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp),
        ) {
            // Etiquetas del eje Y (fuera del Canvas, puro Compose Text)
            Column(
                modifier = Modifier
                    .width(32.dp)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("%.0f".format(maxVal), fontFamily = Manrope, fontSize = 9.sp, color = mutedColor)
                Text("%.0f".format(minVal), fontFamily = Manrope, fontSize = 9.sp, color = mutedColor)
            }

            // Canvas de la línea
            Canvas(modifier = Modifier.weight(1f).fillMaxHeight()) {
                val w = size.width
                val h = size.height
                val padLeft  = 4f
                val padRight = 8f
                val padTop   = 8f
                val padBot   = 8f
                val chartW = w - padLeft - padRight
                val chartH = h - padTop - padBot

                val stepX = if (values.size > 1) chartW / (values.size - 1) else chartW

                fun xOf(i: Int)   = padLeft + i * stepX
                fun yOf(v: Float) = padTop + chartH - ((v - minVal) / range * chartH)

                // Líneas de fondo
                repeat(4) { i ->
                    val lineY = padTop + (chartH / 3f) * i
                    drawLine(
                        color = Color(0xFFE5E7EB),
                        start = Offset(padLeft, lineY),
                        end   = Offset(w - padRight, lineY),
                        strokeWidth = 1f,
                    )
                }

                if (values.size > 1) {
                    // Área bajo la curva con gradiente
                    val areaPath = Path().apply {
                        moveTo(xOf(0), yOf(values[0]))
                        for (i in 1 until values.size) {
                            val cpX = (xOf(i - 1) + xOf(i)) / 2f
                            cubicTo(cpX, yOf(values[i - 1]), cpX, yOf(values[i]), xOf(i), yOf(values[i]))
                        }
                        lineTo(xOf(values.size - 1), padTop + chartH)
                        lineTo(padLeft, padTop + chartH)
                        close()
                    }
                    drawPath(
                        path  = areaPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(color.copy(alpha = 0.22f), color.copy(alpha = 0.01f)),
                            startY = padTop,
                            endY   = padTop + chartH,
                        ),
                    )

                    // Línea bezier suave
                    val linePath = Path().apply {
                        moveTo(xOf(0), yOf(values[0]))
                        for (i in 1 until values.size) {
                            val cpX = (xOf(i - 1) + xOf(i)) / 2f
                            cubicTo(cpX, yOf(values[i - 1]), cpX, yOf(values[i]), xOf(i), yOf(values[i]))
                        }
                    }
                    drawPath(linePath, color = color, style = Stroke(width = 2.5f))
                }

                // Puntos en cada lectura
                values.forEachIndexed { i, v ->
                    drawCircle(color = color,       radius = 4f, center = Offset(xOf(i), yOf(v)))
                    drawCircle(color = Color.White, radius = 2f, center = Offset(xOf(i), yOf(v)))
                }
            }
        }
    }
}
