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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun DailyReportScreen(
    onBack: () -> Unit,
    onNavigateToDetailed: () -> Unit,
    onNavigateToSleepDetail: (mx.ita.vitalsense.data.model.SleepData?) -> Unit,
    vm: DailyReportViewModel = viewModel()
) {
    val state by vm.uiState.collectAsState()

    Scaffold(
        containerColor = NeomorphicBackground,
        topBar = {
            DailyReportTopBar(onBack = onBack)
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
            
            // --- Date Selector (Timeline) ---
            DateStrip(
                selectedDate = state.selectedDate,
                onDateSelected = { vm.onDateSelected(it) }
            )
            
            Spacer(Modifier.height(24.dp))
            
            // --- Health Pentagon (Radar Chart) ---
            Text(
                text = "Estado de Salud",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
            Spacer(Modifier.height(16.dp))
            
            val latestVitals = state.vitalsHistory.lastOrNull()
            val sleepPct = (state.sleepData?.score ?: 0) / 100f
            val glucosePct = latestVitals?.glucose?.toFloat()?.div(140f)?.coerceIn(0f, 1f) ?: 0.6f
            val oxigenoPct = latestVitals?.spo2?.toFloat()?.div(100f)?.coerceIn(0f, 1f) ?: 0.9f
            val presionPct = 0.7f // mock for now
            
            HealthRadarCard(
                score = state.sleepData?.score ?: 0,
                status = state.sleepData?.estado ?: "Sin datos",
                grade = vm.scoreToGrade(state.sleepData?.score ?: 0),
                sleepPct = sleepPct,
                glucosePct = glucosePct,
                presionPct = presionPct,
                oxigenoPct = oxigenoPct,
                onNavigate = onNavigateToDetailed
            )
            
            Spacer(Modifier.height(24.dp))
            
            // --- Metrics Chart Section ---
            Text(
                text = "Métricas de Salud",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
            Spacer(Modifier.height(16.dp))
            
            HeartRateTrendCard(
                history = state.vitalsHistory,
                currentBpm = state.currentVitals.heartRate
            )
            
            Spacer(Modifier.height(16.dp))
            
            // --- SpO2 and Glucose Cards ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                VitalMetricCard(
                    modifier = Modifier.weight(1f),
                    title = "SpO₂",
                    value = if (state.currentVitals.spo2 > 0) "${state.currentVitals.spo2}%" else "—",
                    subtitle = "Oxígeno en sangre",
                    color = Color(0xFF10B981),
                    icon = "🫁"
                )
                VitalMetricCard(
                    modifier = Modifier.weight(1f),
                    title = "Glucosa",
                    value = if (state.currentVitals.glucose > 0) "%.0f".format(state.currentVitals.glucose) else "—",
                    subtitle = "mg/dL",
                    color = Color(0xFFFF9800),
                    icon = "🩸"
                )
            }
            
            Spacer(Modifier.height(24.dp))
            
            SleepMetricCardDaily(
                sleepData = state.sleepData,
                onClick = { onNavigateToSleepDetail(state.sleepData) }
            )
            
            Spacer(Modifier.height(96.dp)) // Espacio para Global Nav Bar
        }
    }
}

@Composable
private fun DailyReportTopBar(onBack: () -> Unit) {
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
        IconButton(onClick = { /* Open Calendar */ }) {
            Icon(Icons.Rounded.CalendarMonth, contentDescription = "Calendario", tint = PrimaryBlue)
        }
    }
}

@Composable
private fun DateStrip(
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit
) {
    val dates = remember {
        (-3..3).map { LocalDate.now().plusDays(it.toLong()) }
    }

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        items(dates) { date ->
            val isToday = date == LocalDate.now()
            val isSelected = date == selectedDate
            val formatter = DateTimeFormatter.ofPattern("d")
            val dayName = if (isToday) "Hoy, " else ""
            val monthName = if (isToday) " " + date.format(DateTimeFormatter.ofPattern("MMM")) else ""
            
            val displayText = if (isToday) "Hoy, ${date.dayOfMonth} ${date.format(DateTimeFormatter.ofPattern("MMM"))}" 
                              else date.format(formatter)

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) PrimaryBlue else Color.Transparent)
                    .clickable { onDateSelected(date) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (isToday) "Hoy, ${date.dayOfMonth} ${date.format(DateTimeFormatter.ofPattern("MMM"))}" else date.dayOfMonth.toString(),
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
                
                Text("Feb, 2025", style = MaterialTheme.typography.labelSmall, color = TextMuted)
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
private fun HeartRateTrendCard(
    history: List<mx.ita.vitalsense.data.model.VitalsData>,
    currentBpm: Int = 0
) {
    val latestBpm = if (currentBpm > 0) currentBpm else (history.lastOrNull()?.heartRate ?: 0)
    
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
    onClick: () -> Unit
) {
    val progress = (sleepData?.score ?: 0) / 100f
    val scoreText = sleepData?.score?.toString() ?: "0"

    mx.ita.vitalsense.ui.components.NeuCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
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
                Text("Promedio de Hoy", fontSize = 11.sp, color = TextSecondary)
                Text(sleepData?.estado ?: "Sin Datos", color = Color(0xFF10B981), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun VitalMetricCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    subtitle: String,
    color: Color,
    icon: String
) {
    NeuCard(modifier = modifier) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(icon, fontSize = 28.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = value,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }
    }
}
