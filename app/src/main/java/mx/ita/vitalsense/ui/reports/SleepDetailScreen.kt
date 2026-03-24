package mx.ita.vitalsense.ui.reports

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SleepDetailScreen(
    score: Int,
    horas: Float,
    estado: String,
    onBack: () -> Unit
) {
    val safeScore = score.coerceIn(0, 100)
    val safeHours = horas.coerceAtLeast(0f)
    val qualityColor = when {
        safeScore >= 85 -> Color(0xFF10B981)
        safeScore >= 70 -> Color(0xFF22C55E)
        safeScore >= 50 -> Color(0xFFF59E0B)
        else -> Color(0xFFEF4444)
    }

    val deepHours = (safeHours * 0.25f).coerceAtMost(safeHours)
    val remHours = (safeHours * 0.20f).coerceAtMost((safeHours - deepHours).coerceAtLeast(0f))
    val lightHours = (safeHours - deepHours - remHours).coerceAtLeast(0f)

    Scaffold(
        containerColor = Color(0xFFF8FAFC),
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Regresar")
                }
                Spacer(Modifier.width(8.dp))
                Text("Sueño detallado", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(80.dp)) {
                        CircularProgressIndicator(
                            progress = { safeScore / 100f },
                            modifier = Modifier.fillMaxSize(),
                            color = qualityColor,
                            strokeWidth = 7.dp,
                            trackColor = qualityColor.copy(alpha = 0.2f),
                        )
                        Text("$safeScore%", fontWeight = FontWeight.Bold, color = qualityColor)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text("Calidad del sueño", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text(estado.ifBlank { "Sin datos" }, color = qualityColor, fontWeight = FontWeight.SemiBold)
                        Text("Duración: ${"%.1f".format(safeHours)} h", color = Color(0xFF64748B), fontSize = 12.sp)
                    }
                }
            }

            StageCard(
                title = "Etapas estimadas",
                deepHours = deepHours,
                remHours = remHours,
                lightHours = lightHours,
                totalHours = safeHours,
            )

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Resumen clínico", color = Color(0xFF0F172A), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("• Tu puntaje indica ${sleepInterpretation(safeScore)}.", color = Color(0xFF334155), fontSize = 13.sp)
                    Text("• La duración ideal recomendada para adultos es 7-9 horas.", color = Color(0xFF334155), fontSize = 13.sp)
                    Text("• Prioriza horario constante y evita pantallas 60 min antes de dormir.", color = Color(0xFF334155), fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun StageCard(
    title: String,
    deepHours: Float,
    remHours: Float,
    lightHours: Float,
    totalHours: Float,
) {
    val safeTotal = totalHours.coerceAtLeast(0.1f)
    val deepPct = (deepHours / safeTotal).coerceIn(0f, 1f)
    val remPct = (remHours / safeTotal).coerceIn(0f, 1f)
    val lightPct = (lightHours / safeTotal).coerceIn(0f, 1f)

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, color = Color(0xFF0F172A), fontWeight = FontWeight.Bold, fontSize = 16.sp)

            SleepStageRow("Profundo", deepHours, deepPct, Color(0xFF1D4ED8))
            SleepStageRow("REM", remHours, remPct, Color(0xFF7C3AED))
            SleepStageRow("Ligero", lightHours, lightPct, Color(0xFF0EA5E9))
        }
    }
}

@Composable
private fun SleepStageRow(label: String, hours: Float, percent: Float, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Color(0xFF334155), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            Text("${"%.1f".format(hours)} h", color = Color(0xFF334155), fontSize = 12.sp)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(Color(0xFFE2E8F0), RoundedCornerShape(999.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(percent)
                    .height(8.dp)
                    .background(color, RoundedCornerShape(999.dp))
            )
        }
    }
}

private fun sleepInterpretation(score: Int): String {
    return when {
        score >= 85 -> "descanso excelente"
        score >= 70 -> "descanso adecuado"
        score >= 50 -> "descanso regular"
        else -> "descanso insuficiente"
    }
}
