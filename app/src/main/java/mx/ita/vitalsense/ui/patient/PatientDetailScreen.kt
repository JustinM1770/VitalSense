package mx.ita.vitalsense.ui.patient

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import mx.ita.vitalsense.data.model.Medication
import mx.ita.vitalsense.ui.components.NeuCard
import mx.ita.vitalsense.ui.theme.*

@Composable
fun PatientDetailScreen(
    onBack: () -> Unit,
    vm: PatientDetailViewModel = viewModel()
) {
    val state by vm.uiState.collectAsState()

    Scaffold(
        containerColor = NeomorphicBackground,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Regresar", tint = TextPrimary)
                }
                Spacer(Modifier.width(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Reporte Detallado", style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                    Text(state.patientName, style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            Text(
                text = "Métricas de Salud",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
            Spacer(Modifier.height(16.dp))
            
            ExpandedHeartRateCard(history = state.heartRateHistory)
            
            Spacer(Modifier.height(24.dp))
            
            Text(
                text = "Medicamentos",
                style = MaterialTheme.typography.titleLarge,
                color = TextPrimary
            )
            Spacer(Modifier.height(16.dp))
            
            MedicationsList(state.medications)
            
            Spacer(Modifier.height(96.dp)) // Espacio para nav bar global
        }
    }
}

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
                points = points.map { it / 200f } // Normalized for chart
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
