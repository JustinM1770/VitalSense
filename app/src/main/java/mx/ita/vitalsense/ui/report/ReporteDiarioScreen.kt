package mx.ita.vitalsense.ui.report

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.ita.vitalsense.ui.components.BottomNav
import mx.ita.vitalsense.ui.components.BottomNavTab
import mx.ita.vitalsense.ui.dashboard.DashWhiteCard
import mx.ita.vitalsense.ui.theme.ChartRed
import mx.ita.vitalsense.ui.theme.DashBg
import mx.ita.vitalsense.ui.theme.DashBlue
import mx.ita.vitalsense.ui.theme.Manrope
import mx.ita.vitalsense.ui.theme.SleepGreen
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun ReporteDiarioScreen(
    onBack: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onHomeClick: () -> Unit = {},
    onNotifClick: () -> Unit = {},
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 90.dp),
        ) {
            Spacer(Modifier.height(52.dp))

            // ── Header ─────────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column {
                    Text(
                        text = "Reporte Diario",
                        fontFamily = Manrope,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        color = Color(0xFF1A1A2E),
                    )
                    val today = LocalDate.now()
                    Text(
                        text = "${today.month.getDisplayName(TextStyle.SHORT, Locale.forLanguageTag("es")).replaceFirstChar { it.uppercase() }}, ${today.year}",
                        fontFamily = Manrope,
                        fontSize = 14.sp,
                        color = Color(0xFF8A8A8A),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Regresar",
                        fontFamily = Manrope,
                        fontSize = 13.sp,
                        color = DashBlue,
                        modifier = Modifier.clickable(onClick = onBack),
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(DashBlue)
                            .clickable(onClick = onBack),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Regresar",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Date strip ─────────────────────────────────────────────────────
            DateStrip()

            Spacer(Modifier.height(20.dp))

            // ── Health Radar Card ──────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(DashBg),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    DashWhiteCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                // Grade ring
                                Box(
                                    modifier = Modifier.size(56.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        drawArc(
                                            color = Color(0xFFE0E0E0),
                                            startAngle = -90f,
                                            sweepAngle = 360f,
                                            useCenter = false,
                                            style = Stroke(6.dp.toPx(), cap = StrokeCap.Round),
                                        )
                                        drawArc(
                                            color = SleepGreen,
                                            startAngle = -90f,
                                            sweepAngle = 300f,
                                            useCenter = false,
                                            style = Stroke(6.dp.toPx(), cap = StrokeCap.Round),
                                        )
                                    }
                                    Text(
                                        text = "A+",
                                        fontFamily = Manrope,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color(0xFF1A1A2E),
                                    )
                                }
                                Spacer(Modifier.width(14.dp))
                                Text(
                                    text = "Salud",
                                    fontFamily = Manrope,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 22.sp,
                                    color = Color(0xFF1A1A2E),
                                )
                                Spacer(Modifier.weight(1f))
                                Text(
                                    text = "Bueno",
                                    fontFamily = Manrope,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = SleepGreen,
                                )
                            }

                            Spacer(Modifier.height(16.dp))

                            Row(modifier = Modifier.fillMaxWidth()) {
                                // Legend
                                Column(
                                    modifier = Modifier.width(120.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    LegendItem(color = SleepGreen, label = "Sueño")
                                    LegendItem(color = Color(0xFF90CAF9), label = "Glucosa")
                                    LegendItem(color = ChartRed.copy(alpha = 0.7f), label = "Presión arterial")
                                    LegendItem(color = Color(0xFF9C27B0).copy(alpha = 0.6f), label = "Oxigeno")
                                }
                                // Radar chart
                                RadarChart(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(160.dp),
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // HR line chart in blue card
                    DashWhiteCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = "Metricas de salud",
                                fontFamily = Manrope,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color(0xFF1A1A2E),
                            )
                            Spacer(Modifier.height(12.dp))
                            MiniHrChart()
                            DayLabels()
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── SpO2 bar chart ─────────────────────────────────────────────────
            DashWhiteCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Oxígeno (SpO₂)",
                        fontFamily = Manrope,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color(0xFF1A1A2E),
                    )
                    Spacer(Modifier.height(12.dp))
                    SpO2BarChart()
                    DayLabels()
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── Glucose bar chart ──────────────────────────────────────────────
            DashWhiteCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Glucosa",
                        fontFamily = Manrope,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color(0xFF1A1A2E),
                    )
                    Spacer(Modifier.height(12.dp))
                    GlucoseBarChart()
                    DayLabels()
                }
            }

            Spacer(Modifier.height(20.dp))
        }

        BottomNav(
            selected = BottomNavTab.HEALTH,
            onSelect = { tab ->
                when (tab) {
                    BottomNavTab.HOME    -> onHomeClick()
                    BottomNavTab.PROFILE -> onProfileClick()
                    BottomNavTab.CHAT    -> onNotifClick()
                    else -> {}
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

// ─── Date strip ───────────────────────────────────────────────────────────────

@Composable
private fun DateStrip() {
    val today = LocalDate.now()
    val days = (-3..3).map { today.plusDays(it.toLong()) }
    var selectedDay by remember { mutableIntStateOf(3) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        days.forEachIndexed { idx, day ->
            val isSelected = idx == selectedDay
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isSelected) DashBg else Color.Transparent)
                    .clickable { selectedDay = idx }
                    .padding(horizontal = if (isSelected) 12.dp else 8.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    Text(
                        text = "Hoy, ${day.dayOfMonth} ${day.month.getDisplayName(TextStyle.SHORT, Locale.forLanguageTag("es")).replaceFirstChar { it.uppercase() }}",
                        fontFamily = Manrope,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color(0xFF1A1A2E),
                    )
                } else {
                    Text(
                        text = "${day.dayOfMonth}",
                        fontFamily = Manrope,
                        fontSize = 14.sp,
                        color = Color(0xFFB0B0B0),
                    )
                }
            }
        }
    }
}

// ─── Legend item ─────────────────────────────────────────────────────────────

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(width = 20.dp, height = 8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            fontFamily = Manrope,
            fontSize = 11.sp,
            color = Color(0xFF6A6A6A),
        )
    }
}

// ─── Radar chart ─────────────────────────────────────────────────────────────

@Composable
private fun RadarChart(modifier: Modifier = Modifier) {
    val metrics = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul")
    val datasets = listOf(
        listOf(0.7f, 0.85f, 0.65f, 0.5f, 0.6f, 0.8f, 0.75f) to SleepGreen.copy(alpha = 0.5f),
        listOf(0.5f, 0.6f, 0.8f, 0.7f, 0.55f, 0.65f, 0.6f) to Color(0xFF90CAF9).copy(alpha = 0.5f),
        listOf(0.6f, 0.4f, 0.5f, 0.65f, 0.7f, 0.45f, 0.5f) to ChartRed.copy(alpha = 0.4f),
        listOf(0.8f, 0.7f, 0.6f, 0.55f, 0.65f, 0.75f, 0.7f) to Color(0xFF9C27B0).copy(alpha = 0.4f),
    )
    Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = minOf(cx, cy) * 0.85f
        val n = metrics.size

        // Grid rings
        for (ring in 1..4) {
            val ringPath = Path()
            for (i in 0 until n) {
                val angle = Math.PI * 2 * i / n - Math.PI / 2
                val x = cx + r * ring / 4f * cos(angle).toFloat()
                val y = cy + r * ring / 4f * sin(angle).toFloat()
                if (i == 0) ringPath.moveTo(x, y) else ringPath.lineTo(x, y)
            }
            ringPath.close()
            drawPath(ringPath, color = Color(0xFFE0E0E0), style = Stroke(0.5.dp.toPx()))
        }

        // Axes
        for (i in 0 until n) {
            val angle = Math.PI * 2 * i / n - Math.PI / 2
            drawLine(
                Color(0xFFE0E0E0),
                Offset(cx, cy),
                Offset(cx + r * cos(angle).toFloat(), cy + r * sin(angle).toFloat()),
                0.5.dp.toPx(),
            )
        }

        // Datasets
        datasets.forEach { (values, color) ->
            val p = Path()
            values.forEachIndexed { i, v ->
                val angle = Math.PI * 2 * i / n - Math.PI / 2
                val x = cx + r * v * cos(angle).toFloat()
                val y = cy + r * v * sin(angle).toFloat()
                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            p.close()
            drawPath(p, color = color)
            drawPath(p, color = color.copy(alpha = 1f), style = Stroke(1.dp.toPx()))
        }
    }
}

// ─── Mini HR line chart ───────────────────────────────────────────────────────

@Composable
private fun MiniHrChart() {
    val values = listOf(118, 125, 132, 122, 115, 118, 114)
    Row(modifier = Modifier.fillMaxWidth().height(100.dp)) {
        Column(
            modifier = Modifier.padding(end = 4.dp).height(90.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            listOf(140, 130, 120, 110, 100).forEach {
                Text("$it", fontFamily = Manrope, fontSize = 9.sp, color = Color(0xFFB0B0B0))
            }
        }
        Canvas(modifier = Modifier.weight(1f).height(90.dp)) {
            val w = size.width; val h = size.height
            val min = 95f; val max = 145f
            fun xOf(i: Int) = i * w / (values.size - 1)
            fun yOf(v: Int) = h - (v - min) / (max - min) * h

            val path = Path().apply {
                moveTo(xOf(0), yOf(values[0]))
                for (i in 1 until values.size) {
                    val cx2 = (xOf(i - 1) + xOf(i)) / 2f
                    cubicTo(cx2, yOf(values[i - 1]), cx2, yOf(values[i]), xOf(i), yOf(values[i]))
                }
            }
            drawPath(
                Path().apply {
                    addPath(path); lineTo(xOf(values.size - 1), h); lineTo(0f, h); close()
                },
                brush = Brush.verticalGradient(
                    listOf(ChartRed.copy(0.2f), Color.Transparent), 0f, h,
                ),
            )
            drawPath(path, ChartRed, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
            val dotX = xOf(5); val dotY = yOf(values[5])
            drawCircle(ChartRed, 5.dp.toPx(), Offset(dotX, dotY))
            drawCircle(Color.White, 3.dp.toPx(), Offset(dotX, dotY))
            drawLine(ChartRed.copy(0.4f), Offset(dotX, dotY), Offset(dotX, h), 1.dp.toPx())
        }
    }
}

// ─── SpO2 bar chart ───────────────────────────────────────────────────────────

@Composable
private fun SpO2BarChart() {
    val values = listOf(97f, 98f, 97f, 98.5f, 99f, 99f, 97f)
    val min = 96f; val max = 101f
    Row(
        modifier = Modifier.fillMaxWidth().height(100.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(
            modifier = Modifier.padding(end = 4.dp).height(90.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            listOf(101, 100, 99, 98, 97).forEach {
                Text("$it", fontFamily = Manrope, fontSize = 9.sp, color = Color(0xFFB0B0B0))
            }
        }
        Canvas(modifier = Modifier.weight(1f).height(90.dp)) {
            val w = size.width; val h = size.height
            val barW = w / (values.size * 2f)
            values.forEachIndexed { i, v ->
                val cx = (i + 0.5f) * w / values.size
                val barH = (v - min) / (max - min) * h
                val activeColor = ChartRed
                val bgColor = ChartRed.copy(alpha = 0.15f)
                // Background bar (full height)
                drawRoundRect(
                    color = bgColor,
                    topLeft = Offset(cx - barW / 2, 0f),
                    size = androidx.compose.ui.geometry.Size(barW, h),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(barW / 2),
                )
                // Active bar
                drawRoundRect(
                    color = activeColor,
                    topLeft = Offset(cx - barW / 2, h - barH),
                    size = androidx.compose.ui.geometry.Size(barW, barH),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(barW / 2),
                )
            }
        }
    }
}

// ─── Glucose bar chart ────────────────────────────────────────────────────────

@Composable
private fun GlucoseBarChart() {
    val values = listOf(68f, 70f, 74f, 48f, 52f, 72f, 78f)
    val min = 35f; val max = 85f
    Row(
        modifier = Modifier.fillMaxWidth().height(100.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(
            modifier = Modifier.padding(end = 4.dp).height(90.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            listOf(80, 70, 60, 50, 40).forEach {
                Text("$it", fontFamily = Manrope, fontSize = 9.sp, color = Color(0xFFB0B0B0))
            }
        }
        Canvas(modifier = Modifier.weight(1f).height(90.dp)) {
            val w = size.width; val h = size.height
            val barW = w / (values.size * 2f)
            values.forEachIndexed { i, v ->
                val cx = (i + 0.5f) * w / values.size
                val barH = (v - min) / (max - min) * h
                val activeColor = DashBlue
                val bgColor = DashBlue.copy(alpha = 0.15f)
                drawRoundRect(
                    bgColor,
                    Offset(cx - barW / 2, 0f),
                    androidx.compose.ui.geometry.Size(barW, h),
                    androidx.compose.ui.geometry.CornerRadius(barW / 2),
                )
                drawRoundRect(
                    activeColor,
                    Offset(cx - barW / 2, h - barH),
                    androidx.compose.ui.geometry.Size(barW, barH),
                    androidx.compose.ui.geometry.CornerRadius(barW / 2),
                )
            }
        }
    }
}

@Composable
private fun DayLabels() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        listOf("0", "Sun", "Mon", "Tue", "Wed", "Thru", "Fri", "Sat").forEach {
            Text(it, fontFamily = Manrope, fontSize = 9.sp, color = Color(0xFFB0B0B0))
        }
    }
}
