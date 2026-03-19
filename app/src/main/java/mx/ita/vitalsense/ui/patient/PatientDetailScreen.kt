package mx.ita.vitalsense.ui.patient

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import mx.ita.vitalsense.data.model.Medication
import mx.ita.vitalsense.data.model.OverallStatus
import mx.ita.vitalsense.data.model.TrendDirection
import mx.ita.vitalsense.data.model.VitalAlert
import mx.ita.vitalsense.data.model.VitalsData
import mx.ita.vitalsense.data.model.VitalsSnapshot
import mx.ita.vitalsense.data.model.VitalsTrend
import mx.ita.vitalsense.data.model.analyzeTrends
import mx.ita.vitalsense.data.model.computeAlerts
import mx.ita.vitalsense.data.model.overallStatus
import mx.ita.vitalsense.data.report.ReportGenerator
import mx.ita.vitalsense.data.repository.VitalsRepository
import mx.ita.vitalsense.data.test.TestDataSeeder
import mx.ita.vitalsense.ui.components.ChartMetric
import mx.ita.vitalsense.ui.components.NeuCard
import mx.ita.vitalsense.ui.components.VitalsLineChart
import mx.ita.vitalsense.ui.theme.AlertBackground
import mx.ita.vitalsense.ui.theme.AlertBorder
import mx.ita.vitalsense.ui.theme.AlertText
import mx.ita.vitalsense.ui.theme.GlucoseOrange
import mx.ita.vitalsense.ui.theme.GlucoseSoft
import mx.ita.vitalsense.ui.theme.HeartRateRed
import mx.ita.vitalsense.ui.theme.HeartRateSoft
import mx.ita.vitalsense.ui.theme.Manrope
import mx.ita.vitalsense.ui.theme.NeomorphicBackground
import mx.ita.vitalsense.ui.theme.PrimaryBlue
import mx.ita.vitalsense.ui.theme.SpO2Green
import mx.ita.vitalsense.ui.theme.SpO2Soft
import mx.ita.vitalsense.ui.theme.TextMuted
import mx.ita.vitalsense.ui.theme.TextPrimary
import mx.ita.vitalsense.ui.theme.TextSecondary

// ─── ViewModel ────────────────────────────────────────────────────────────────

sealed interface PatientDetailUiState {
    data object Loading : PatientDetailUiState
    data class Success(val patient: VitalsData) : PatientDetailUiState
    data class Error(val message: String) : PatientDetailUiState
}

class PatientDetailViewModel(patientId: String = "") : ViewModel() {

    private val repo = VitalsRepository()

    private val _uiState = MutableStateFlow<PatientDetailUiState>(PatientDetailUiState.Loading)
    val uiState: StateFlow<PatientDetailUiState> = _uiState.asStateFlow()

    val history: StateFlow<List<VitalsSnapshot>> = repo
        .observeHistory(patientId)
        .map { list -> list.ifEmpty { TestDataSeeder.mockHistory(patientId) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TestDataSeeder.mockHistory(patientId))

    // Expose patient name for Jonathan's Scaffold topBar
    val patientName: String
        get() = (_uiState.value as? PatientDetailUiState.Success)?.patient?.patientName ?: ""

    // Heart rate history for monthly chart (Jonathan's ExpandedHeartRateCard)
    val heartRateHistory: Map<String, Float>
        get() {
            val snapshots = history.value
            val months = listOf("Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio", "Julio")
            return months.associateWith { month ->
                snapshots.filter { it.timestamp > 0 }.map { it.heartRate.toFloat() }.average().toFloat().takeIf { !it.isNaN() } ?: 0f
            }
        }

    val medications: List<Medication> = emptyList()

    init {
        if (patientId.isNotEmpty()) {
            viewModelScope.launch {
                repo.observePatient(patientId).collect { result ->
                    _uiState.value = result.fold(
                        onSuccess = { PatientDetailUiState.Success(it) },
                        onFailure = { PatientDetailUiState.Error(it.message ?: "Error") },
                    )
                }
            }
        } else {
            // No patientId: load first available patient
            viewModelScope.launch {
                repo.observePatients().collect { result ->
                    result.onSuccess { patients ->
                        patients.firstOrNull()?.let {
                            _uiState.value = PatientDetailUiState.Success(it)
                        }
                    }
                }
            }
        }
    }

    class Factory(private val patientId: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            PatientDetailViewModel(patientId) as T
    }
}

// ─── Screen — overloaded to support both patientId and no-arg signatures ──────

private val TextDark = Color(0xFF221F1F)

/** Full patient detail screen — used from patient list (with patientId). */
@Composable
fun PatientDetailScreen(
    patientId: String,
    onBack: () -> Unit,
) {
    val vm: PatientDetailViewModel = viewModel(factory = PatientDetailViewModel.Factory(patientId))
    PatientDetailScreenContent(vm = vm, onBack = onBack)
}

/** Simplified signature used from global nav (no patientId, loads first patient). */
@Composable
fun PatientDetailScreen(
    onBack: () -> Unit,
    vm: PatientDetailViewModel = viewModel(),
) {
    PatientDetailScreenContent(vm = vm, onBack = onBack)
}

@Composable
private fun PatientDetailScreenContent(
    vm: PatientDetailViewModel,
    onBack: () -> Unit,
) {
    val uiState by vm.uiState.collectAsState()
    val history by vm.history.collectAsState()
    val context = LocalContext.current

    var selectedMetric by remember { mutableStateOf(ChartMetric.GLUCOSE) }
    val trend = remember(history) { history.analyzeTrends() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NeomorphicBackground)
            .padding(top = 52.dp),
    ) {
        // ── Header ────────────────────────────────────────────────────────────
        val patientName = (uiState as? PatientDetailUiState.Success)?.patient?.patientName ?: "Reporte Detallado"
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .height(40.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onBack)
                    .align(Alignment.CenterStart),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Regresar", tint = TextDark, modifier = Modifier.size(24.dp))
            }
            Text(
                text = patientName,
                fontFamily = Manrope,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = TextDark,
                modifier = Modifier.align(Alignment.Center),
            )
            // Botón exportar PDF (only when patient is loaded)
            if (uiState is PatientDetailUiState.Success) {
                Icon(
                    imageVector = Icons.Outlined.PictureAsPdf,
                    contentDescription = "Exportar PDF",
                    tint = PrimaryBlue,
                    modifier = Modifier
                        .size(28.dp)
                        .align(Alignment.CenterEnd)
                        .clickable {
                            val patient = (uiState as PatientDetailUiState.Success).patient
                            val uri = ReportGenerator.generate(context, patient, history, trend)
                            val share = Intent(Intent.ACTION_SEND).apply {
                                type = "application/pdf"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                putExtra(Intent.EXTRA_SUBJECT, "Reporte HealthSensor — ${patient.patientName}")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(share, "Compartir reporte"))
                        },
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        when (val state = uiState) {
            is PatientDetailUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PrimaryBlue, strokeWidth = 3.dp)
            }
            is PatientDetailUiState.Error -> ErrorDetail(state.message)
            is PatientDetailUiState.Success -> DetailContent(
                patient = state.patient,
                history = history,
                trend = trend,
                selectedMetric = selectedMetric,
                onMetricSelected = { selectedMetric = it },
                medications = vm.medications,
                heartRateHistory = vm.heartRateHistory,
            )
        }
    }
}

// ─── Error ────────────────────────────────────────────────────────────────────

@Composable
private fun ErrorDetail(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        NeuCard(modifier = Modifier.padding(32.dp)) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Rounded.Warning, null, tint = HeartRateRed, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(12.dp))
                Text("Error al cargar", fontWeight = FontWeight.Bold, color = TextPrimary, fontSize = 18.sp)
                Spacer(Modifier.height(6.dp))
                Text(message, color = TextSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)
            }
        }
    }
}

// ─── Contenido principal ──────────────────────────────────────────────────────

@Composable
private fun DetailContent(
    patient: VitalsData,
    history: List<VitalsSnapshot>,
    trend: VitalsTrend,
    selectedMetric: ChartMetric,
    onMetricSelected: (ChartMetric) -> Unit,
    medications: List<Medication>,
    heartRateHistory: Map<String, Float>,
) {
    val alerts = patient.computeAlerts()
    val status = patient.overallStatus()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
    ) {
        // ── Badge de estado ───────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusBadge(status)
            Text(
                text = if (patient.timestamp > 0L) "En tiempo real · Firebase" else "Sin datos recientes",
                fontFamily = Manrope, fontSize = 11.sp, color = TextMuted,
            )
        }

        Spacer(Modifier.height(20.dp))

        // ── Tarjetas de vitales ───────────────────────────────────────────────
        VitalDetailCard(
            icon = Icons.Rounded.Favorite,
            label = "Frecuencia Cardíaca",
            value = "${patient.heartRate}",
            unit = "BPM",
            accentColor = HeartRateRed,
            softColor = HeartRateSoft,
            rangeLabel = "Normal: 60 – 100 BPM",
            inRange = patient.heartRate in 60..100,
            trend = trend.heartRate,
        )

        Spacer(Modifier.height(14.dp))

        VitalDetailCard(
            icon = Icons.Rounded.WaterDrop,
            label = "Glucosa",
            value = "%.0f".format(patient.glucose),
            unit = "mg/dL",
            accentColor = GlucoseOrange,
            softColor = GlucoseSoft,
            rangeLabel = "Normal: 70 – 150 mg/dL",
            inRange = patient.glucose in 70.0..150.0,
            trend = trend.glucose,
        )

        Spacer(Modifier.height(14.dp))

        VitalDetailCard(
            icon = Icons.Rounded.MonitorHeart,
            label = "Saturación de Oxígeno",
            value = "${patient.spo2}",
            unit = "SpO₂ %",
            accentColor = SpO2Green,
            softColor = SpO2Soft,
            rangeLabel = "Normal: ≥ 90%",
            inRange = patient.spo2 >= 90,
            trend = trend.spo2,
        )

        Spacer(Modifier.height(24.dp))

        // ── Gráfica expandida de Ritmo Cardíaco (Jonathan) ───────────────────
        ExpandedHeartRateCard(history = heartRateHistory)

        Spacer(Modifier.height(24.dp))

        // ── Gráfica de tendencia con selector de métrica (Justin) ────────────
        NeuCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Tendencia Histórica",
                    fontFamily = Manrope,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = TextPrimary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${history.size} lecturas registradas",
                    fontFamily = Manrope,
                    fontSize = 11.sp,
                    color = TextMuted,
                )

                Spacer(Modifier.height(12.dp))

                // Selector de métrica
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricChip(
                        label = "Glucosa",
                        selected = selectedMetric == ChartMetric.GLUCOSE,
                        color = GlucoseOrange,
                        onClick = { onMetricSelected(ChartMetric.GLUCOSE) },
                    )
                    MetricChip(
                        label = "Cardíaca",
                        selected = selectedMetric == ChartMetric.HEART_RATE,
                        color = HeartRateRed,
                        onClick = { onMetricSelected(ChartMetric.HEART_RATE) },
                    )
                    MetricChip(
                        label = "SpO₂",
                        selected = selectedMetric == ChartMetric.SPO2,
                        color = SpO2Green,
                        onClick = { onMetricSelected(ChartMetric.SPO2) },
                    )
                }

                Spacer(Modifier.height(8.dp))

                VitalsLineChart(
                    snapshots = history,
                    modifier = Modifier.fillMaxWidth(),
                    activeMetric = selectedMetric,
                )
            }
        }

        // ── Medicamentos (Jonathan) ───────────────────────────────────────────
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Medicamentos",
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
            fontFamily = Manrope,
        )
        Spacer(Modifier.height(16.dp))
        MedicationsList(medications)

        // ── Alertas ───────────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = alerts.isNotEmpty(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column {
                Spacer(Modifier.height(24.dp))
                Text(
                    "Alertas Activas",
                    fontFamily = Manrope, fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp, color = TextMuted,
                )
                Spacer(Modifier.height(10.dp))
                alerts.forEach { alert ->
                    AlertDetailRow(alert)
                    if (alert != alerts.last()) Spacer(Modifier.height(8.dp))
                }
            }
        }

        Spacer(Modifier.height(96.dp))
    }
}

// ─── Expanded Heart Rate Card (Jonathan) ─────────────────────────────────────

@Composable
private fun ExpandedHeartRateCard(history: Map<String, Float>) {
    NeuCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Favorite, contentDescription = null, tint = HeartRateRed, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Ritmo Cardíaco", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("Promedio Mensual", fontSize = 12.sp, color = TextSecondary)
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            val months = listOf("Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio", "Julio")
            val points = months.map { history[it] ?: 0f }

            ExpandedLineChart(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                color = HeartRateRed,
                points = points.map { it / 200f },
            )

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                months.forEach { month ->
                    Text(month.take(3), style = MaterialTheme.typography.labelSmall, color = TextMuted)
                }
            }
        }
    }
}

@Composable
private fun ExpandedLineChart(modifier: Modifier = Modifier, color: Color, points: List<Float>) {
    Canvas(modifier = modifier) {
        val path = Path()
        val width = size.width
        val height = size.height
        if (points.size < 2) return@Canvas
        val stepX = width / (points.size - 1)

        points.forEachIndexed { index, value ->
            val x = index * stepX
            val y = height - (value * height).coerceIn(0f, height)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 4.dp.toPx())
        )

        // Horizontal grid lines
        for (i in 0..3) {
            val y = height - (i * height / 3)
            drawLine(
                color = TextMuted.copy(alpha = 0.1f),
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 1.dp.toPx()
            )
        }
    }
}

// ─── Medications list (Jonathan) ─────────────────────────────────────────────

@Composable
private fun MedicationsList(meds: List<Medication>) {
    if (meds.isEmpty()) {
        NeuCard(modifier = Modifier.fillMaxWidth().height(100.dp)) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text("No hay medicamentos activos", style = MaterialTheme.typography.bodyLarge, color = TextMuted)
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            meds.forEach { med ->
                MedicationItem(med)
            }
        }
    }
}

@Composable
private fun MedicationItem(med: Medication) {
    NeuCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(PrimaryBlue.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Medication, contentDescription = null, tint = PrimaryBlue)
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(med.nombre, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(med.dosis, fontSize = 12.sp, color = TextSecondary)
            }
            Text(med.horario, style = MaterialTheme.typography.labelLarge, color = PrimaryBlue)
        }
    }
}

// ─── Chip selector de métrica ─────────────────────────────────────────────────

@Composable
private fun MetricChip(
    label: String,
    selected: Boolean,
    color: Color,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(label, fontFamily = Manrope, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = color.copy(alpha = 0.15f),
            selectedLabelColor = color,
            labelColor = TextSecondary,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = selected,
            selectedBorderColor = color,
            borderColor = Color(0xFFE5E5E5),
        ),
    )
}

// ─── Tarjeta de vital expandida ───────────────────────────────────────────────

@Composable
private fun VitalDetailCard(
    icon: ImageVector,
    label: String,
    value: String,
    unit: String,
    accentColor: Color,
    softColor: Color,
    rangeLabel: String,
    inRange: Boolean,
    trend: TrendDirection,
) {
    val trendText = when (trend) {
        TrendDirection.RISING  -> "↑ Tendencia ascendente"
        TrendDirection.FALLING -> "↓ Tendencia descendente"
        TrendDirection.STABLE  -> "→ Estable"
    }
    val trendColor = when (trend) {
        TrendDirection.RISING  -> GlucoseOrange
        TrendDirection.FALLING -> PrimaryBlue
        TrendDirection.STABLE  -> SpO2Green
    }

    NeuCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(56.dp).clip(CircleShape).background(softColor),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = accentColor, modifier = Modifier.size(28.dp))
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(label, fontFamily = Manrope, fontSize = 12.sp, color = TextSecondary)
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        value,
                        fontFamily = Manrope, fontWeight = FontWeight.Bold,
                        fontSize = 36.sp, color = accentColor, lineHeight = 36.sp,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(unit, fontFamily = Manrope, fontSize = 14.sp, color = TextSecondary,
                        modifier = Modifier.padding(bottom = 4.dp))
                }
                Spacer(Modifier.height(4.dp))
                // Rango normal
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(8.dp).clip(CircleShape)
                            .background(if (inRange) SpO2Green else HeartRateRed),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(rangeLabel, fontFamily = Manrope, fontSize = 11.sp, color = TextMuted)
                }
                Spacer(Modifier.height(4.dp))
                // Tendencia IA
                Text(trendText, fontFamily = Manrope, fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold, color = trendColor)
            }
        }
    }
}

// ─── Fila de alerta ───────────────────────────────────────────────────────────

@Composable
private fun AlertDetailRow(alert: VitalAlert) {
    Box(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AlertBackground),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape)
                    .background(AlertBorder.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Warning, null, tint = AlertBorder, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(alert.title, fontFamily = Manrope, fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp, color = AlertBorder)
                Spacer(Modifier.height(2.dp))
                Text(alert.advice, fontFamily = Manrope, fontSize = 12.sp, color = AlertText)
            }
        }
    }
}

// ─── Badge de estado ──────────────────────────────────────────────────────────

@Composable
private fun StatusBadge(status: OverallStatus) {
    val (text, bg, fg) = when (status) {
        OverallStatus.STABLE -> Triple("Estable", SpO2Soft, SpO2Green)
        OverallStatus.ALERT  -> Triple("Alerta", AlertBackground, AlertBorder)
    }
    Box(
        modifier = Modifier.clip(RoundedCornerShape(50)).background(bg)
            .padding(horizontal = 14.dp, vertical = 5.dp),
    ) {
        Text(text, fontFamily = Manrope, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = fg)
    }
}
