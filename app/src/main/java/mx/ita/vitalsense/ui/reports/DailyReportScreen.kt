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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import mx.ita.vitalsense.R
import mx.ita.vitalsense.ui.common.LockLandscapeOrientation
import mx.ita.vitalsense.ui.components.NeuCard
import mx.ita.vitalsense.ui.theme.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.cos
import kotlin.math.sin
import java.util.Locale
import androidx.compose.foundation.isSystemInDarkTheme

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun DailyReportScreen(
    onBack: () -> Unit,
    onNavigateToDetailed: () -> Unit,
    onNavigateToSleepDetail: (Int, Int, Long, Long, String) -> Unit = { _, _, _, _, _ -> },
    vm: DailyReportViewModel = viewModel()
) {
    LockLandscapeOrientation()
    val state by vm.uiState.collectAsState()
    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = state.selectedDate
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli(),
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
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
                text = stringResource(R.string.report_health_status_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
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
            val sleepNoDataLabel = stringResource(R.string.sleep_no_data)
            val sleepStatusLabel = state.sleepData?.estado
                ?.takeUnless { status -> status.equals("Sin datos", ignoreCase = true) || status.equals("Sin Datos", ignoreCase = true) || status.equals("No data", ignoreCase = true) || status.equals("Sem dados", ignoreCase = true) }
                ?: sleepNoDataLabel
            
            HealthRadarCard(
                reportLabel = rangeLabel,
                score = state.sleepData?.score ?: 0,
                status = sleepStatusLabel,
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
                text = stringResource(R.string.report_health_metrics_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
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
                            Text(stringResource(R.string.report_spo2_oxygenation), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("$latestSpo2%", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = SpO2Green)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            SleepMetricCardDaily(
                sleepData = state.sleepData,
                rangeLabel = rangeLabel,
                durationLabel = state.sleepData?.durationLabel() ?: stringResource(R.string.sleep_no_data),
                onClick = {
                    onNavigateToSleepDetail(
                        state.sleepData?.score ?: 0,
                        state.sleepData?.totalMinutes ?: 0,
                        state.sleepData?.sleepStartMillis ?: 0L,
                        state.sleepData?.sleepEndMillis ?: 0L,
                        sleepStatusLabel,
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
                        Text(stringResource(R.string.common_ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) {
                        Text(stringResource(R.string.common_cancel))
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
                label = { Text(stringResource(filter.labelRes)) },
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
            Icon(
                Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(R.string.back),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = stringResource(R.string.report_daily_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        IconButton(onClick = onCalendarClick) {
            Icon(
                Icons.Rounded.CalendarMonth,
                contentDescription = stringResource(R.string.report_calendar),
                tint = MaterialTheme.colorScheme.primary,
            )
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
            val monthEs = date.format(DateTimeFormatter.ofPattern("MMM", Locale.getDefault()))
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
                    text = if (isToday) stringResource(R.string.dashboard_today_date, date.dayOfMonth, monthEs) else date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
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
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
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
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(grade, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(stringResource(R.string.report_health_short), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                }
                
                Text(
                    reportLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                text = stringResource(R.string.report_averages_period, rangeLabel),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = stringResource(R.string.report_hr_abbrev, if (avgHeartRate > 0) "%.0f".format(avgHeartRate) else "--"),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.report_spo2_abbrev, if (avgSpo2 > 0) "%.0f".format(avgSpo2) else "--"),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.report_glucose_abbrev, if (avgGlucose > 0) "%.0f".format(avgGlucose) else "--"),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(R.string.report_sleep_abbrev, avgSleepScore),
                    color = MaterialTheme.colorScheme.onSurface,
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
    val colorScheme = MaterialTheme.colorScheme
    val values = listOf(sleepPct, glucosePct, presionPct, oxigenoPct)
    val bgColor = colorScheme.surface
    val gridColor = colorScheme.onSurfaceVariant.copy(alpha = if (isSystemInDarkTheme()) 0.30f else 0.22f)
    val dataColor = colorScheme.primary
    val dataFillColor = colorScheme.primary.copy(alpha = 0.28f)
    
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
        drawPath(bgPath, color = bgColor, style = Fill)
        drawPath(bgPath, color = gridColor, style = Stroke(width = 1.dp.toPx()))

        // Ejes (Background lines)
        angles.forEach { angleDeg ->
            val angleRad = Math.toRadians(angleDeg.toDouble()).toFloat()
            drawLine(
                color = gridColor,
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
        
        drawPath(path, color = dataFillColor, style = Fill)
        drawPath(path, color = dataColor, style = Stroke(width = 2.dp.toPx()))
        
        // Data points
        values.forEachIndexed { index, pct ->
            val angleRad = Math.toRadians(angles[index].toDouble()).toFloat()
            drawCircle(
                color = dataColor,
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
                    Icon(Icons.Rounded.Favorite, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.report_heart_rate_title), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                }
                Text("$latestBpm BPM", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            
            Spacer(Modifier.height(24.dp))
            
            LineChart(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                color = MaterialTheme.colorScheme.primary,
                points = history.takeLast(10).map { it.heartRate.toFloat() / 200f } // Normalized
            )
            
            Spacer(Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf("00:00", "06:00", "12:00", "18:00", "23:59").forEach { time ->
                    Text(time, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    durationLabel: String,
    onClick: () -> Unit,
) {
    val progress = (sleepData?.score ?: 0) / 100f
    val scoreText = sleepData?.score?.toString() ?: "0"
    val colorScheme = MaterialTheme.colorScheme
    val successColor = colorScheme.primary

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
                    color = successColor,
                    strokeWidth = 6.dp,
                    trackColor = successColor.copy(alpha = 0.18f)
                )
                Text("$scoreText%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = colorScheme.onSurface)
            }
            Spacer(Modifier.width(16.dp))
            Text(stringResource(R.string.sleep_title), color = colorScheme.onSurface, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(stringResource(R.string.sleep_average_label, rangeLabel), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(durationLabel, fontSize = 12.sp, color = colorScheme.onSurface, fontWeight = FontWeight.Bold)
                Text(sleepData?.estado ?: stringResource(R.string.sleep_no_data), color = colorScheme.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
