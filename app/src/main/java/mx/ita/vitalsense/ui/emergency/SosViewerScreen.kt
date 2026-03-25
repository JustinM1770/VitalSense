package mx.ita.vitalsense.ui.emergency

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val SosRed      = Color(0xFFD32F2F)
private val SosDarkRed  = Color(0xFFB71C1C)
private val SosLightRed = Color(0xFFFFEBEE)
private val TextDark    = Color(0xFF212121)
private val TextGray    = Color(0xFF757575)

private data class SosAlertData(
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val timestamp: Long = 0L,
    val status: String = "active",
    val patientName: String = "",
)

/**
 * Pantalla que se abre cuando el socorrista escanea el QR del reloj.
 * Muestra la ubicación GPS y datos del paciente. Permite abrir Maps y
 * marcar la alerta como atendida.
 *
 * Deep link: vitalsense://sos/{userId}/{sosId}
 */
@Composable
fun SosViewerScreen(
    userId: String,
    sosId: String,
    onBack: () -> Unit = {},
) {
    val context  = LocalContext.current
    val db       = remember { FirebaseDatabase.getInstance("https://vitalsenseai-1cb9f-default-rtdb.firebaseio.com") }
    var alert    by remember { mutableStateOf<SosAlertData?>(null) }
    var loading  by remember { mutableStateOf(true) }
    var resolved by remember { mutableStateOf(false) }

    // Escuchar la alerta en tiempo real
    LaunchedEffect(userId, sosId) {
        val alertRef   = db.getReference("alerts/$userId/$sosId")
        val profileRef = db.getReference("users/$userId/datosMedicos")

        profileRef.get().addOnSuccessListener { profileSnap ->
            val nombre    = profileSnap.child("nombre").getValue(String::class.java) ?: ""
            val apellidos = profileSnap.child("apellidos").getValue(String::class.java) ?: ""
            val name      = "$nombre $apellidos".trim()

            alertRef.addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snap: DataSnapshot) {
                    if (!snap.exists()) { loading = false; return }
                    alert = SosAlertData(
                        lat         = snap.child("lat").getValue(Double::class.java) ?: 0.0,
                        lng         = snap.child("lng").getValue(Double::class.java) ?: 0.0,
                        timestamp   = snap.child("timestamp").getValue(Long::class.java) ?: 0L,
                        status      = snap.child("status").getValue(String::class.java) ?: "active",
                        patientName = name,
                    )
                    resolved = alert?.status == "resolved" || alert?.status == "accepted"
                    loading  = false

                    // Marcar como leído automáticamente
                    snap.ref.child("read").ref.setValue(true)
                }
                override fun onCancelled(e: DatabaseError) { loading = false }
            })
        }.addOnFailureListener { loading = false }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(SosDarkRed, SosRed)))
            .statusBarsPadding(),
    ) {
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(color = Color.White)
                    Text("Cargando alerta SOS...", color = Color.White.copy(alpha = 0.8f))
                }
            }

            alert == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Filled.Warning, null, tint = Color.White, modifier = Modifier.size(64.dp))
                    Text("Alerta no encontrada", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Puede haber expirado o ya fue resuelta.", color = Color.White.copy(0.7f), textAlign = TextAlign.Center)
                    OutlinedButton(onClick = onBack, colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) {
                        Text("Cerrar")
                    }
                }
            }

            else -> {
                val data = alert!!
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // — Encabezado —
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            modifier = Modifier.size(52.dp).background(Color.White.copy(0.2f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.Warning, null, tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                        Column {
                            Text("ALERTA SOS", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                            if (data.timestamp > 0L) {
                                Text(
                                    SimpleDateFormat("HH:mm:ss · dd/MM/yyyy", Locale.getDefault()).format(Date(data.timestamp)),
                                    color = Color.White.copy(0.7f), fontSize = 12.sp
                                )
                            }
                        }
                    }

                    // — Paciente —
                    if (data.patientName.isNotEmpty()) {
                        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White.copy(0.15f))) {
                            Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Person, null, tint = Color.White, modifier = Modifier.size(22.dp))
                                Column {
                                    Text("Paciente", color = Color.White.copy(0.7f), fontSize = 11.sp)
                                    Text(data.patientName, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }

                    // — Ubicación —
                    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White.copy(0.15f))) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.LocationOn, null, tint = Color.White, modifier = Modifier.size(22.dp))
                                Text("Ubicación GPS", color = Color.White.copy(0.7f), fontSize = 11.sp)
                            }
                            if (data.lat != 0.0 && data.lng != 0.0) {
                                Text(
                                    "${"%.5f".format(data.lat)}, ${"%.5f".format(data.lng)}",
                                    color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                                )
                                Button(
                                    onClick = {
                                        val uri = Uri.parse("geo:${data.lat},${data.lng}?q=${data.lat},${data.lng}(SOS)")
                                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                                    shape  = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Icon(Icons.Filled.LocationOn, null, tint = SosRed, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.size(8.dp))
                                    Text("Abrir en Maps", color = SosRed, fontWeight = FontWeight.Bold)
                                }
                            } else {
                                Text("Ubicación GPS no disponible", color = Color.White.copy(0.6f), fontSize = 13.sp)
                            }
                        }
                    }

                    // — Estado —
                    if (resolved) {
                        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF2E7D32).copy(0.8f))) {
                            Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                                Text("✓  Alerta atendida", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    // — Botones —
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = onBack,
                            colors  = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border  = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(0.5f)),
                            modifier = Modifier.weight(1f),
                        ) { Text("Cerrar") }

                        if (!resolved) {
                            Button(
                                onClick = {
                                    db.getReference("alerts/$userId/$sosId/status").setValue("accepted")
                                    db.getReference("alerts/$userId/$sosId/read").setValue(true)
                                    resolved = true
                                },
                                colors   = ButtonDefaults.buttonColors(containerColor = Color.White),
                                shape    = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("En camino", color = SosRed, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
