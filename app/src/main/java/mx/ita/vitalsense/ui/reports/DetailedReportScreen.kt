package mx.ita.vitalsense.ui.reports

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.ita.vitalsense.ui.components.NeuCard
import mx.ita.vitalsense.ui.theme.*

@Composable
fun DetailedReportScreen(
    onBack: () -> Unit
) {
    Scaffold(
        containerColor = NeomorphicBackground,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Regresar", tint = TextPrimary)
                }
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("Reporte Detallado", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("Jonathan Hdez", fontSize = 14.sp, color = TextSecondary)
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
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(Modifier.height(16.dp))
            
            ExpandedHeartRateCard()
            
            Spacer(Modifier.height(24.dp))
            
            // Placeholder for other detailed sections (Medications, etc.)
            Text(
                text = "Medicamentos",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(Modifier.height(16.dp))
            
            NeuCard(modifier = Modifier.fillMaxWidth().height(100.dp)) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text("Próximas tomas...", color = TextMuted)
                }
            }
            
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ExpandedHeartRateCard() {
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
                        Text("Ritmo Cardíaco", fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("Promedio: 110 BPM", fontSize = 12.sp, color = TextSecondary)
                    }
                }
            }
            
            Spacer(Modifier.height(32.dp))
            
            // Expanded Chart (Jan -> July)
            ExpandedLineChart(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                color = HeartRateRed
            )
            
            Spacer(Modifier.height(16.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val months = listOf("Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio", "Julio")
                months.forEach { month ->
                    Text(month, fontSize = 9.sp, color = TextMuted)
                }
            }
        }
    }
}

@Composable
private fun ExpandedLineChart(modifier: Modifier = Modifier, color: Color) {
    Canvas(modifier = modifier) {
        val points = listOf(0.4f, 0.55f, 0.45f, 0.75f, 0.65f, 0.45f, 0.4f, 0.5f, 0.45f, 0.6f)
        val path = Path()
        val width = size.width
        val height = size.height
        val stepX = width / (points.size - 1)
        
        points.forEachIndexed { index, value ->
            val x = index * stepX
            val y = height - (value * height)
            if (index == 0) path.moveTo(x, y) else {
                // Smooth cubic curve would be better, but simple line for now
                path.lineTo(x, y)
            }
        }
        
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 4.dp.toPx())
        )
        
        // Draw Y axis labels (simulated)
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

private fun Offset(x: Float, y: Float) = androidx.compose.ui.geometry.Offset(x, y)
