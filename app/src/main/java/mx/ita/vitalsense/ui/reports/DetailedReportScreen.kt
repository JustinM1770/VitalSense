package mx.ita.vitalsense.ui.reports

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import mx.ita.vitalsense.ui.components.NeuCard
import mx.ita.vitalsense.ui.theme.HeartRateRed
import mx.ita.vitalsense.ui.theme.NeomorphicBackground
import mx.ita.vitalsense.ui.theme.PrimaryBlue
import mx.ita.vitalsense.ui.theme.SpO2Green
import mx.ita.vitalsense.ui.theme.TextMuted
import mx.ita.vitalsense.ui.theme.TextPrimary
import mx.ita.vitalsense.ui.theme.TextSecondary

@Composable
fun DetailedReportScreen(
    onBack: () -> Unit,
    vm: DailyReportViewModel = viewModel(),
) {
    val state by vm.uiState.collectAsState()
    val hrValues = state.vitalsHistory.map { it.heartRate }.filter { it > 0 }
    val spo2Values = state.vitalsHistory.map { it.spo2 }.filter { it > 0 }
    val glucoseValues = state.vitalsHistory.map { it.glucose }.filter { it > 0.0 }

    val avgHeartRate = if (hrValues.isNotEmpty()) hrValues.average() else 0.0
    val avgSpo2 = if (spo2Values.isNotEmpty()) spo2Values.average() else 0.0
    val avgGlucose = if (glucoseValues.isNotEmpty()) glucoseValues.average() else 0.0
    val rangeLabel = vm.getRangeLabel(state)

    Scaffold(
        containerColor = NeomorphicBackground,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Regresar", tint = TextPrimary)
                }
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("Reporte Detallado", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text(
                        rangeLabel,
                        fontSize = 13.sp,
                        color = TextSecondary,
                    )
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PrimaryBlue)
                }
            }

            DetailedRangeFilterRow(
                selectedFilter = state.selectedFilter,
                onFilterSelected = { vm.onFilterSelected(it) }
            )

            Spacer(Modifier.height(16.dp))

            RealtimeLineMetricCard(
                title = "Ritmo Cardíaco",
                subtitle = "Promedio del periodo",
                value = if (avgHeartRate > 0) "${"%.0f".format(avgHeartRate)} BPM" else "-- BPM",
                color = HeartRateRed,
                points = state.vitalsHistory.takeLast(20).map { it.heartRate.toFloat() },
                icon = {
                    Icon(Icons.Rounded.Favorite, contentDescription = null, tint = HeartRateRed, modifier = Modifier.size(22.dp))
                },
            )

            Spacer(Modifier.height(16.dp))

            RealtimeLineMetricCard(
                title = "Oxigenación (SpO₂)",
                subtitle = "Promedio del periodo",
                value = if (avgSpo2 > 0) "${"%.0f".format(avgSpo2)}%" else "--%",
                color = SpO2Green,
                points = state.vitalsHistory.takeLast(20).map { it.spo2.toFloat() },
                icon = {
                    Icon(Icons.Rounded.MonitorHeart, contentDescription = null, tint = SpO2Green, modifier = Modifier.size(22.dp))
                },
            )

            Spacer(Modifier.height(16.dp))

            RealtimeLineMetricCard(
                title = "Glucosa",
                subtitle = "Promedio del periodo",
                value = if (avgGlucose > 0.0) "${"%.0f".format(avgGlucose)} mg/dL" else "-- mg/dL",
                color = Color(0xFF7B61FF),
                points = state.vitalsHistory.takeLast(20).map { it.glucose.toFloat() },
                icon = {
                    Text("G", color = Color(0xFF7B61FF), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                },
            )

            Spacer(Modifier.height(16.dp))

            SleepDetailCard(
                score = state.sleepData?.score ?: 0,
                status = state.sleepData?.estado ?: "No durmió",
                hours = state.sleepData?.horas ?: 0f,
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun DetailedRangeFilterRow(
    selectedFilter: ReportRangeFilter,
    onFilterSelected: (ReportRangeFilter) -> Unit,
) {
    val filters = listOf(
        ReportRangeFilter.TODAY,
        ReportRangeFilter.YESTERDAY,
        ReportRangeFilter.LAST_7_DAYS,
        ReportRangeFilter.THIS_MONTH,
    )

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 4.dp)
    ) {
        items(filters) { filter ->
            FilterChip(
                selected = selectedFilter == filter,
                onClick = { onFilterSelected(filter) },
                label = { Text(filter.label) },
            )
        }
    }
}

@Composable
private fun RealtimeLineMetricCard(
    title: String,
    subtitle: String,
    value: String,
    color: Color,
    points: List<Float>,
    icon: @Composable () -> Unit,
) {
    NeuCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    icon()
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text(subtitle, fontSize = 12.sp, color = TextSecondary)
                    }
                }
                Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }

            Spacer(Modifier.height(20.dp))

            LineChartReal(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp),
                color = color,
                points = points,
            )

            Spacer(Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("-20", "-15", "-10", "-5", "Ahora").forEach {
                    Text(it, fontSize = 10.sp, color = TextMuted)
                }
            }
        }
    }
}

@Composable
private fun LineChartReal(modifier: Modifier, color: Color, points: List<Float>) {
    Canvas(modifier = modifier) {
        val normalized = normalize(points)
        val safePoints = if (normalized.isEmpty()) listOf(0.5f, 0.5f) else normalized
        val width = size.width
        val height = size.height
        val stepX = width / (safePoints.size - 1)

        for (i in 0..3) {
            val y = i * (height / 3f)
            drawLine(
                color = TextMuted.copy(alpha = 0.18f),
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 1.dp.toPx(),
            )
        }

        val path = Path()
        safePoints.forEachIndexed { index, v ->
            val x = index * stepX
            val y = height - (v * height)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(path = path, color = color, style = Stroke(width = 3.dp.toPx()))
    }
}

private fun normalize(values: List<Float>): List<Float> {
    if (values.isEmpty()) return emptyList()
    val filtered = values.map { if (it.isFinite() && it > 0f) it else 0f }
    val min = filtered.minOrNull() ?: return emptyList()
    val max = filtered.maxOrNull() ?: return emptyList()
    if (max <= 0f) return emptyList()
    if (max == min) return filtered.map { if (it > 0f) 0.6f else 0.3f }
    return filtered.map { ((it - min) / (max - min)).coerceIn(0.05f, 0.95f) }
}

@Composable
private fun SleepDetailCard(score: Int, status: String, hours: Float) {
    val safeScore = score.coerceIn(0, 100)
    val safeHours = if (hours > 0f) hours else 0f
    val statusColor = if (safeHours > 0f) Color(0xFF10B981) else TextSecondary

    NeuCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { safeScore / 100f },
                    modifier = Modifier.size(68.dp),
                    color = Color(0xFF10B981),
                    strokeWidth = 6.dp,
                    trackColor = Color(0xFF10B981).copy(alpha = 0.15f),
                )
                Text("$safeScore%", fontWeight = FontWeight.Bold, color = TextPrimary)
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text("Sueño", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 16.sp)
                Text(if (safeHours > 0f) "${"%.1f".format(safeHours)} horas" else "0.0 horas", color = TextSecondary, fontSize = 12.sp)
                Text(status, color = statusColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}
