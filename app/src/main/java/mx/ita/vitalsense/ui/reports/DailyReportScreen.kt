package mx.ita.vitalsense.ui.reports

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import mx.ita.vitalsense.ui.components.NeuCard
import mx.ita.vitalsense.ui.theme.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.cos
import kotlin.math.sin
import java.util.Locale

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun DailyReportScreen(
    onBack: () -> Unit,
    onNavigateToDetailed: () -> Unit,
    onNavigateToSleepDetail: (Int, Float, String) -> Unit = { _, _, _ -> },
    vm: DailyReportViewModel = viewModel()
) {
    val state by vm.uiState.collectAsState()
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = state.selectedDate
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli(),
    )

    Scaffold(
        containerColor = Color.White,
        topBar = {
            DailyReportTopBar(
                onBack = onBack,
                onCalendarClick = { showDatePicker = true },
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(16.dp))

            ReportRangeFilterRow(
                selectedFilter = state.selectedFilter,
                onFilterSelected = { vm.onFilterSelected(it) }
            )

            if (state.selectedFilter == ReportRangeFilter.SELECTED_DAY) {
                Spacer(Modifier.height(12.dp))
                DateStrip(
                    selectedDate = state.selectedDate,
                    onDateSelected = { vm.onDateSelected(it) }
                )
            }
            
            Spacer(Modifier.height(24.dp))
            
            // --- Health Pentagon (Radar Chart) ---
            Text(
                text = "Estado de Salud",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
            Spacer(Modifier.height(16.dp))
            
            val sleepPct = (state.sleepData?.score ?: 0) / 100f

            val hrValues = state.vitalsHistory.map { it.heartRate }.filter { it > 0 }
            val spo2Values = state.vitalsHistory.map { it.spo2 }.filter { it > 0 }
            val glucoseValues = state.vitalsHistory.map { it.glucose }.filter { it > 0.0 }

            val avgHeartRate = if (hrValues.isNotEmpty()) hrValues.average() else 0.0
            val avgSpo2 = if (spo2Values.isNotEmpty()) spo2Values.average() else 0.0
            val avgGlucose = if (glucoseValues.isNotEmpty()) glucoseValues.average() else 0.0
            val glucosePct = if (avgGlucose > 0.0) (avgGlucose / 140.0).toFloat().coerceIn(0f, 1f) else 0f
            val oxigenoPct = if (avgSpo2 > 0.0) (avgSpo2 / 100.0).toFloat().coerceIn(0f, 1f) else 0f
            val presionPct = if (avgHeartRate > 0.0) (avgHeartRate / 160.0).toFloat().coerceIn(0f, 1f) else 0f
            val rangeLabel = vm.getRangeLabel(state)
            
            HealthRadarCard(
                reportLabel = rangeLabel,
                score = state.sleepData?.score ?: 0,
                status = state.sleepData?.estado ?: "Sin datos",
                grade = vm.scoreToGrade(state.sleepData?.score ?: 0),
                sleepPct = sleepPct,
                glucosePct = glucosePct,
                presionPct = presionPct,
                oxigenoPct = oxigenoPct,
                onNavigate = onNavigateToDetailed
            )

            Spacer(Modifier.height(12.dp))

            PeriodAverageCard(
                rangeLabel = rangeLabel,
                avgHeartRate = avgHeartRate,
                avgSpo2 = avgSpo2,
                avgGlucose = avgGlucose,
                avgSleepScore = state.sleepData?.score ?: 0,
            )
            
            Spacer(Modifier.height(24.dp))
            
            // --- Metrics Chart Section ---
            Text(
                text = "Métricas de Salud",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
            Spacer(Modifier.height(16.dp))
            
            HeartRateTrendCard(history = state.vitalsHistory)
            
            Spacer(Modifier.height(16.dp))

            // --- SpO2 Card ---
            val latestSpo2 = state.vitalsHistory.lastOrNull()?.spo2 ?: 0
            if (latestSpo2 > 0) {
                NeuCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            androidx.compose.material.icons.Icons.Rounded.MonitorHeart,
                            contentDescription = null,
                            tint = SpO2Green,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Oxigenación SpO₂", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                            Text("$latestSpo2%", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = SpO2Green)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            SleepMetricCardDaily(
                sleepData = state.sleepData,
                rangeLabel = rangeLabel,
                onClick = {
                    onNavigateToSleepDetail(
                        state.sleepData?.score ?: 0,
                        state.sleepData?.horas ?: 0f,
                        state.sleepData?.estado ?: "Sin Datos",
                    )
                },
            )
            
            Spacer(Modifier.height(96.dp))
        }

        if (showDatePicker) {
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        val millis = datePickerState.selectedDateMillis
                        if (millis != null) {
                            val selected = Instant.ofEpochMilli(millis)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                            vm.onDateSelected(selected)
                        }
                        showDatePicker = false
                    }) {
                        Text("Aceptar")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) {
                        Text("Cancelar")
                    }
                },
            ) {
                DatePicker(state = datePickerState)
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun ReportRangeFilterRow(
    selectedFilter: ReportRangeFilter,
    onFilterSelected: (ReportRangeFilter) -> Unit,
) {
    val filters = listOf(
        ReportRangeFilter.TODAY,
        ReportRangeFilter.YESTERDAY,
        ReportRangeFilter.LAST_7_DAYS,
        ReportRangeFilter.THIS_MONTH,
        ReportRangeFilter.SELECTED_DAY,
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
private fun DailyReportTopBar(
    onBack: () -> Unit,
    onCalendarClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Regresar", tint = TextPrimary)
        }
        Text(
            text = "Reporte Diario",
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary
        )
        IconButton(onClick = onCalendarClick) {
            Icon(Icons.Rounded.CalendarMonth, contentDescription = "Calendario", tint = PrimaryBlue)
        }
    }
}

@Composable
private fun DateStrip(
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit
) {
    val dates = remember(selectedDate) {
        (-3..3).map { selectedDate.plusDays(it.toLong()) }
    }

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(dates) { date ->
            val isToday = date == LocalDate.now()
            val isSelected = date == selectedDate
            val monthEs = date.format(DateTimeFormatter.ofPattern("MMM", Locale.forLanguageTag("es")))
                .replaceFirstChar { it.uppercase() }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) PrimaryBlue else Color.Transparent)
                    .clickable { onDateSelected(date) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isToday) "Hoy, ${date.dayOfMonth} $monthEs" else date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) Color.White else TextSecondary
                )
            }
        }
    }
}

@Composable
private fun HealthRadarCard(
    reportLabel: String,
    score: Int,
    status: String,
    grade: String,
    sleepPct: Float,
    glucosePct: Float,
    presionPct: Float,
    oxigenoPct: Float,
    onNavigate: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onNavigate() },
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFBDD9F2)) // DashBg
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Status Badge
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(SpO2Green.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(grade, color = SpO2Green, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Salud", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text(status, color = SpO2Green, fontSize = 12.sp)
                    }
                }
                
                Text(
                    reportLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                )
            }
            
            Spacer(Modifier.height(24.dp))
            
            // Radar Chart with 4 axis as requested
            HealthRadarChart(
                modifier = Modifier
                    .size(160.dp)
                    .padding(16.dp),
                sleepPct = sleepPct,
                glucosePct = glucosePct,
                presionPct = presionPct,
                oxigenoPct = oxigenoPct
            )
            
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PeriodAverageCard(
    rangeLabel: String,
    avgHeartRate: Double,
    avgSpo2: Double,
    avgGlucose: Double,
    avgSleepScore: Int,
) {
    NeuCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Promedios: $rangeLabel",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = "HR ${if (avgHeartRate > 0) "%.0f".format(avgHeartRate) else "--"}",
                    color = HeartRateRed,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "SpO2 ${if (avgSpo2 > 0) "%.0f%%".format(avgSpo2) else "--"}",
                    color = SpO2Green,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Glu ${if (avgGlucose > 0) "%.0f".format(avgGlucose) else "--"}",
                    color = PrimaryBlue,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Sueno $avgSleepScore%",
                    color = Color(0xFF10B981),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun HealthRadarChart(
    modifier: Modifier = Modifier,
    sleepPct: Float,
    glucosePct: Float,
    presionPct: Float,
    oxigenoPct: Float
) {
    // Ejes: Sueño, Glucosa, Presión arterial, Oxígeno
    val values = listOf(sleepPct, glucosePct, presionPct, oxigenoPct)
    
    Canvas(modifier = modifier) {
        val centerX = size.width / 2
        val centerY = size.height / 2
        val radius = size.minDimension / 2 * 0.8f
        val angles = listOf(270f, 0f, 90f, 180f) // arriba, der, abajo, izq
        
        // Fondo gris (Polígono base 100%)
        val bgPath = Path()
        angles.forEachIndexed { index, angleDeg ->
            val angleRad = Math.toRadians(angleDeg.toDouble()).toFloat()
            val x = centerX + radius * cos(angleRad)
            val y = centerY + radius * sin(angleRad)
            if (index == 0) bgPath.moveTo(x, y) else bgPath.lineTo(x, y)
        }
        bgPath.close()
        drawPath(bgPath, color = Color(0xFFF0F2F5), style = Fill) // InputBg gris claro
        drawPath(bgPath, color = Color.Gray.copy(alpha = 0.3f), style = Stroke(width = 1.dp.toPx()))

        // Ejes (Background lines)
        angles.forEach { angleDeg ->
            val angleRad = Math.toRadians(angleDeg.toDouble()).toFloat()
            drawLine(
                color = Color.Gray.copy(alpha = 0.3f),
                start = Offset(centerX, centerY),
                end = Offset(
                    centerX + radius * cos(angleRad),
                    centerY + radius * sin(angleRad)
                ),
                strokeWidth = 1.dp.toPx()
            )
        }

        // Data polygon verde teal
        val path = Path()
        values.forEachIndexed { index, pct ->
            val angleRad = Math.toRadians(angles[index].toDouble()).toFloat()
            val x = centerX + radius * pct * cos(angleRad)
            val y = centerY + radius * pct * sin(angleRad)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        
        drawPath(path, color = Color(0xFF00C48C).copy(alpha = 0.3f), style = Fill) // SleepGreen area
        drawPath(path, color = Color(0xFF00C48C), style = Stroke(width = 2.dp.toPx())) // Verde teal borde
        
        // Data points
        values.forEachIndexed { index, pct ->
            val angleRad = Math.toRadians(angles[index].toDouble()).toFloat()
            drawCircle(
                color = Color(0xFF00C48C),
                radius = 4.dp.toPx(),
                center = Offset(
                    centerX + radius * pct * cos(angleRad),
                    centerY + radius * pct * sin(angleRad)
                )
            )
        }
    }
}

@Composable
private fun HeartRateTrendCard(history: List<mx.ita.vitalsense.data.model.VitalsData>) {
    val latestBpm = history.lastOrNull()?.heartRate ?: 0
    
    NeuCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Favorite, contentDescription = null, tint = HeartRateRed, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Ritmo Cardíaco", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = TextPrimary)
                }
                Text("$latestBpm BPM", color = HeartRateRed, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            
            Spacer(Modifier.height(24.dp))
            
            LineChart(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                color = HeartRateRed,
                points = history.takeLast(10).map { it.heartRate.toFloat() / 200f } // Normalized
            )
            
            Spacer(Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf("00:00", "06:00", "12:00", "18:00", "23:59").forEach { time ->
                    Text(time, style = MaterialTheme.typography.labelSmall, color = TextMuted)
                }
            }
        }
    }
}

@Composable
fun LineChart(modifier: Modifier = Modifier, color: Color, points: List<Float>) {
    val displayPoints = if (points.isEmpty()) listOf(0.5f, 0.5f) else points
    
    Canvas(modifier = modifier) {
        val path = Path()
        val width = size.width
        val height = size.height
        val stepX = width / (displayPoints.size - 1)
        
        displayPoints.forEachIndexed { index, value ->
            val x = index * stepX
            val y = height - (value * height).coerceIn(0f, height)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 3.dp.toPx())
        )
    }
}

@Composable
fun SleepMetricCardDaily(
    sleepData: mx.ita.vitalsense.data.model.SleepData?,
    rangeLabel: String,
    onClick: () -> Unit,
) {
    val progress = (sleepData?.score ?: 0) / 100f
    val scoreText = sleepData?.score?.toString() ?: "0"

    mx.ita.vitalsense.ui.components.NeuCard(modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Circular progress
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(60.dp),
                    color = Color(0xFF10B981), // SuccessGreen
                    strokeWidth = 6.dp,
                    trackColor = Color(0xFF10B981).copy(alpha = 0.1f)
                )
                Text("$scoreText%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            }
            Spacer(Modifier.width(16.dp))
            Text("Sueño", color = Color(0xFF10B981), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text("Promedio: $rangeLabel", fontSize = 11.sp, color = TextSecondary)
                Text(sleepData?.estado ?: "Sin Datos", color = Color(0xFF10B981), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
