package mx.ita.vitalsense.ui.documentos

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import mx.ita.vitalsense.ui.theme.DashBg
import mx.ita.vitalsense.ui.theme.DashBlue
import mx.ita.vitalsense.ui.theme.Manrope

@Composable
fun DocumentosScreen(
    patientName: String = "Jonathan Hdez",
    patientAge: Int = 72,
    bloodType: String = "O+",
    allergies: String = "Penicilina",
    emergencyPhone: String = "+52 449 1004533",
    onBack: () -> Unit = {},
) {
    val context = LocalContext.current
    val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    val prefs = remember { context.getSharedPreferences("vitalsense_profile", Context.MODE_PRIVATE) }
    val avatarUri = if (uid.isNotEmpty()) prefs.getString("avatar_uri_$uid", null) else null
    val uploadedDocs = remember(uid) {
        if (uid.isNotEmpty()) prefs.getStringSet("documents_$uid", emptySet())?.toList()?.sorted() ?: emptyList()
        else emptyList()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DashBg)
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(52.dp))

        // Back button
        Box(
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .size(36.dp)
                .clip(CircleShape)
                .background(DashBlue)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Regresar",
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }

        Spacer(Modifier.height(20.dp))

        // White card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.White)
                .padding(24.dp),
        ) {
            Column {
                // Patient name + age
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(DashBlue),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (!avatarUri.isNullOrBlank()) {
                            AsyncImage(
                                model = Uri.parse(avatarUri),
                                contentDescription = "Avatar",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Text(
                                text = patientName.split(" ").mapNotNull { it.firstOrNull()?.uppercaseChar() }.joinToString("").take(2),
                                fontFamily = Manrope,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = Color.White,
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = patientName,
                            fontFamily = Manrope,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color(0xFF1A1A2E),
                        )
                        Text(
                            text = "$patientAge años",
                            fontFamily = Manrope,
                            fontSize = 14.sp,
                            color = Color(0xFF8A8A8A),
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Blood type
                InfoRow(
                    icon = Icons.Outlined.LocalHospital,
                    iconColor = ChartRedLocal,
                    title = "Tipo de sangre",
                    value = bloodType,
                )

                Spacer(Modifier.height(16.dp))

                // Allergies
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFFFEBEE))
                        .padding(14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Warning,
                            contentDescription = null,
                            tint = Color(0xFFE53935),
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Alergias",
                                fontFamily = Manrope,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFFE53935),
                            )
                            Text(
                                text = allergies,
                                fontFamily = Manrope,
                                fontSize = 14.sp,
                                color = Color(0xFF1A1A2E),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Emergency phone
                InfoRow(
                    icon = Icons.Outlined.Phone,
                    iconColor = DashBlue,
                    title = "Telefono de contacto",
                    value = emergencyPhone,
                )

                Spacer(Modifier.height(20.dp))

                Text(
                    text = "Documentos personales:",
                    fontFamily = Manrope,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = Color(0xFF1A1A2E),
                )

                Spacer(Modifier.height(12.dp))

                if (uploadedDocs.isEmpty()) {
                    Text(
                        text = "Sin documentos subidos",
                        fontFamily = Manrope,
                        fontSize = 13.sp,
                        color = Color(0xFF8A8A8A),
                    )
                } else {
                    uploadedDocs.forEachIndexed { idx, filename ->
                        DocFileRow(filename = filename)
                        if (idx != uploadedDocs.lastIndex) {
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Warning disclaimer
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFFFF9C4))
                        .padding(14.dp),
                ) {
                    Row(verticalAlignment = Alignment.Top) {
                        Text("⚠️", fontSize = 14.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Esta información es confidencial y solo debe usarse en emergencias médicas",
                            fontFamily = Manrope,
                            fontSize = 12.sp,
                            color = Color(0xFF795548),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun InfoRow(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    value: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, fontFamily = Manrope, fontSize = 12.sp, color = Color(0xFF8A8A8A))
            Text(value, fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color(0xFF1A1A2E))
        }
    }
}

@Composable
private fun DocFileRow(filename: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(10.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = filename,
            fontFamily = Manrope,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = Color(0xFF1A1A2E),
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFFFFEBEE)),
            contentAlignment = Alignment.Center,
        ) {
            Text("PDF", fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 8.sp, color = Color(0xFFE53935))
        }
    }
}

private val ChartRedLocal = Color(0xFFE53935)
