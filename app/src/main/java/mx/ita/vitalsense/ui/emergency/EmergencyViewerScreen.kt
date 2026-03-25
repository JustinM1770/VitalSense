package mx.ita.vitalsense.ui.emergency

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import mx.ita.vitalsense.data.emergency.EmergencyTokenData
import mx.ita.vitalsense.data.emergency.EmergencyTokenRepository

// ─── Paleta de la pantalla de visualización ───────────────────────────────────
private val ViewerBg       = Color(0xFFF5F5F5)
private val EmergencyRed   = Color(0xFFD32F2F)
private val SectionBg      = Color(0xFFFFFFFF)
private val HeaderBg       = Color(0xFFB71C1C)
private val BloodTypeColor = Color(0xFFD32F2F)
private val TextDark       = Color(0xFF212121)
private val TextGray       = Color(0xFF757575)
private val GreenCall      = Color(0xFF2E7D32)

/**
 * Pantalla que muestra el perfil médico de emergencia al paramédico.
 *
 * Se abre automáticamente cuando alguien escanea el QR con el deep link
 * `vitalsense://emergency/{tokenId}` y la app está instalada en su teléfono.
 *
 * @param tokenId  UUID del token de emergencia (viene del deep link)
 * @param onBack   Callback para cerrar la pantalla
 */
@Composable
fun EmergencyViewerScreen(
    tokenId: String,
    onBack: () -> Unit = {},
) {
    val repository = remember { EmergencyTokenRepository() }
    var tokenData by remember { mutableStateOf<EmergencyTokenData?>(null) }
    var error     by remember { mutableStateOf<String?>(null) }
    var loading   by remember { mutableStateOf(true) }

    // Cargar datos del token desde Firebase
    LaunchedEffect(tokenId) {
        repository.readToken(tokenId)
            .onSuccess { data ->
                tokenData = data
                loading   = false
            }
            .onFailure { e ->
                error   = e.message ?: "No se pudo cargar el perfil de emergencia"
                loading = false
            }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ViewerBg),
    ) {
        when {
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(color = EmergencyRed)
                    Text("Cargando perfil de emergencia...", color = TextGray, fontSize = 14.sp)
                }
            }

            error != null -> ErrorView(message = error!!, onBack = onBack)

            tokenData != null -> ProfileView(data = tokenData!!, onBack = onBack)
        }
    }
}

// ─── Vista principal del perfil médico ───────────────────────────────────────

@Composable
private fun ProfileView(data: EmergencyTokenData, onBack: () -> Unit) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
    ) {
        // — Encabezado rojo —
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(HeaderBg)
                .statusBarsPadding()
                .padding(24.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                // Avatar circular con iniciales
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(Color.White.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text       = initials(data.nombre, data.apellidos),
                        color      = Color.White,
                        fontSize   = 28.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    text       = "${data.nombre} ${data.apellidos}".trim().ifEmpty { "Paciente" },
                    color      = Color.White,
                    fontSize   = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign  = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                // Tipo de sangre destacado
                if (data.tipoSangre.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .background(Color.White, RoundedCornerShape(20.dp))
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text       = data.tipoSangre,
                            color      = BloodTypeColor,
                            fontSize   = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                // Alerta de anomalía
                if (data.anomalyType.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Warning, null, tint = Color(0xFFFFD600), modifier = Modifier.size(16.dp))
                        Text(
                            text     = "${data.anomalyType} • ${data.heartRateAtAlert} BPM",
                            color    = Color(0xFFFFCDD2),
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        }

        Column(
            modifier            = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // — Alergias (crítico: primera sección) —
            if (data.alergias.isNotEmpty()) {
                InfoCard(
                    icon       = Icons.Filled.Warning,
                    iconTint   = EmergencyRed,
                    title      = "Alergias",
                    content    = data.alergias,
                    highlight  = true,
                )
            }

            // — Padecimientos —
            if (data.padecimientos.isNotEmpty()) {
                InfoCard(
                    icon    = Icons.Filled.Favorite,
                    title   = "Padecimientos",
                    content = data.padecimientos,
                )
            }

            // — Medicamentos —
            if (data.medicamentos.isNotEmpty()) {
                InfoCard(
                    icon    = Icons.Filled.Person,
                    title   = "Medicamentos actuales",
                    content = data.medicamentos,
                )
            }

            // — Contacto de emergencia —
            if (data.contactoEmergencia.isNotEmpty() || data.telefonoEmergencia.isNotEmpty()) {
                Card(
                    shape  = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SectionBg),
                    elevation = CardDefaults.cardElevation(2.dp),
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text       = "Contacto de Emergencia",
                            fontSize   = 13.sp,
                            color      = TextGray,
                            fontWeight = FontWeight.Medium,
                        )
                        if (data.contactoEmergencia.isNotEmpty()) {
                            Text(
                                text       = data.contactoEmergencia,
                                fontSize   = 16.sp,
                                color      = TextDark,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        if (data.telefonoEmergencia.isNotEmpty()) {
                            Button(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${data.telefonoEmergencia}"))
                                    context.startActivity(intent)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = GreenCall),
                                shape  = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Icon(Icons.Filled.Call, null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(8.dp))
                                Text(data.telefonoEmergencia, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // — Documentos médicos —
            if (data.documentos.isNotEmpty()) {
                MedicalDocumentsSection(documentos = data.documentos)
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ─── Sección de documentos médicos ───────────────────────────────────────────

@Composable
private fun MedicalDocumentsSection(documentos: List<Map<String, String>>) {
    val context = LocalContext.current
    Card(
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = SectionBg),
        elevation = CardDefaults.cardElevation(2.dp),
    ) {
        Column(
            modifier            = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text       = "Documentos médicos",
                fontSize   = 13.sp,
                color      = TextGray,
                fontWeight = FontWeight.Medium,
            )
            documentos.forEach { doc ->
                val nombre = doc["nombre"] ?: "Documento"
                val url    = doc["url"]    ?: return@forEach
                val tipo   = doc["tipo"]   ?: "pdf"
                if (tipo == "imagen") {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        AsyncImage(
                            model              = url,
                            contentDescription = nombre,
                            contentScale       = ContentScale.Crop,
                            modifier           = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(8.dp)),
                        )
                        Text(nombre, fontSize = 12.sp, color = TextDark)
                    }
                } else {
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            context.startActivity(intent)
                        },
                        colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF1169FF)),
                        shape    = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Filled.Description, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(nombre, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

// ─── Card de información ──────────────────────────────────────────────────────

@Composable
private fun InfoCard(
    icon: ImageVector,
    iconTint: Color = Color(0xFF1169FF),
    title: String,
    content: String,
    highlight: Boolean = false,
) {
    Card(
        shape  = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (highlight) Color(0xFFFFF8F8) else SectionBg,
        ),
        elevation = CardDefaults.cardElevation(2.dp),
        border = if (highlight) androidx.compose.foundation.BorderStroke(1.dp, EmergencyRed.copy(alpha = 0.3f)) else null,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, null, tint = iconTint, modifier = Modifier.size(22.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, fontSize = 12.sp, color = if (highlight) EmergencyRed else TextGray, fontWeight = FontWeight.Medium)
                Text(content, fontSize = 15.sp, color = TextDark, fontWeight = FontWeight.Normal)
            }
        }
    }
}

// ─── Vista de error ───────────────────────────────────────────────────────────

@Composable
private fun ErrorView(message: String, onBack: () -> Unit) {
    Column(
        modifier            = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.Warning, null, tint = EmergencyRed, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(16.dp))
        Text("Acceso no disponible", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextDark)
        Spacer(Modifier.height(8.dp))
        Text(message, fontSize = 14.sp, color = TextGray, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onBack, colors = ButtonDefaults.buttonColors(containerColor = EmergencyRed)) {
            Text("Cerrar", color = Color.White)
        }
    }
}

// ─── Utilidades ───────────────────────────────────────────────────────────────

private fun initials(nombre: String, apellidos: String): String {
    val n = nombre.firstOrNull()?.uppercaseChar() ?: ""
    val a = apellidos.firstOrNull()?.uppercaseChar() ?: ""
    return "$n$a".ifEmpty { "?" }
}
