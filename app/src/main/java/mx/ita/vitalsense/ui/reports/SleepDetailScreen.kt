package mx.ita.vitalsense.ui.reports

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

@Composable
fun SleepDetailScreen(
    score: Int,
    horas: Float,
    estado: String,
    onBack: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Sleep Detail Screen. Score: $score, Horas: $horas, Estado: $estado")
    }
}
