package mx.ita.vitalsense.ui.reports

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import mx.ita.vitalsense.ui.components.NeuCard
import mx.ita.vitalsense.ui.theme.NeomorphicBackground
import mx.ita.vitalsense.ui.theme.TextPrimary
import mx.ita.vitalsense.ui.theme.TextSecondary
import mx.ita.vitalsense.ui.theme.PrimaryBlue

@Composable
fun SleepDetailScreen(
    score: Int,
    horas: Float,
    estado: String,
    onBack: () -> Unit
) {
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
                Column {
                    Text("Detalle de Sueño", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Text("Análisis de tu descanso", fontSize = 14.sp, color = TextSecondary)
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(32.dp))
            
            // Score Circular
            NeuCard(modifier = Modifier.size(220.dp)) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    CircularProgressIndicator(
                        progress = { score / 100f },
                        modifier = Modifier.size(170.dp),
                        color = Color(0xFF10B981),
                        strokeWidth = 12.dp,
                        trackColor = Color(0xFF10B981).copy(alpha = 0.15f),
                        strokeCap = StrokeCap.Round
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.Bedtime, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(32.dp))
                        Spacer(Modifier.height(4.dp))
                        Text(score.toString(), fontSize = 48.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("Score", fontSize = 16.sp, color = TextSecondary)
                    }
                }
            }
            
            Spacer(Modifier.height(48.dp))
            
            // Cards for metrics
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                NeuCard(modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Horas de Sueño", fontSize = 14.sp, color = TextSecondary)
                        Spacer(Modifier.height(12.dp))
                        Text(String.format("%.1f h", horas), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = PrimaryBlue)
                    }
                }
                
                NeuCard(modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Estado", fontSize = 14.sp, color = TextSecondary)
                        Spacer(Modifier.height(12.dp))
                        Text(estado, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                    }
                }
            }
            
            Spacer(Modifier.height(32.dp))
            
            // Decorative chart (Mocked sleep cycle)
            NeuCard(modifier = Modifier.fillMaxWidth()) {
               Column(modifier = Modifier.padding(24.dp)) {
                   Text("Ciclos de Sueño", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                   Text("Patrón estimado basado en movimiento", fontSize = 12.sp, color = TextSecondary)
                   Spacer(Modifier.height(24.dp))
                   Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
                       val width = size.width
                       val height = size.height
                       val path = androidx.compose.ui.graphics.Path()
                       
                       path.moveTo(0f, height * 0.2f)
                       path.cubicTo(width * 0.1f, height, width * 0.3f, height * 0.1f, width * 0.5f, height * 0.9f)
                       path.cubicTo(width * 0.7f, height * 0.3f, width * 0.9f, height * 0.8f, width, height * 0.2f)
                       
                       drawPath(
                           path = path,
                           color = PrimaryBlue,
                           style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                       )
                       
                       // Draw stages lines
                       val dashEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                       drawLine(Color.Gray.copy(0.3f), androidx.compose.ui.geometry.Offset(0f, height*0.2f), androidx.compose.ui.geometry.Offset(width, height*0.2f), pathEffect = dashEffect)
                       drawLine(Color.Gray.copy(0.3f), androidx.compose.ui.geometry.Offset(0f, height*0.5f), androidx.compose.ui.geometry.Offset(width, height*0.5f), pathEffect = dashEffect)
                       drawLine(Color.Gray.copy(0.3f), androidx.compose.ui.geometry.Offset(0f, height*0.8f), androidx.compose.ui.geometry.Offset(width, height*0.8f), pathEffect = dashEffect)
                   }
                   Spacer(Modifier.height(12.dp))
                   Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                       Text("Despierto", fontSize = 10.sp, color = TextSecondary)
                       Text("Ligero", fontSize = 10.sp, color = TextSecondary)
                       Text("Profundo", fontSize = 10.sp, color = TextSecondary)
                   }
               }
            }
        }
    }
}
