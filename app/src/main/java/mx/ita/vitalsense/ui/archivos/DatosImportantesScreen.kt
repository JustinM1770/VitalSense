package mx.ita.vitalsense.ui.archivos

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import mx.ita.vitalsense.ui.components.BottomNav
import mx.ita.vitalsense.ui.components.BottomNavTab
import mx.ita.vitalsense.ui.theme.DashBg
import mx.ita.vitalsense.ui.theme.DashBlue
import mx.ita.vitalsense.ui.theme.Manrope

@Composable
fun DatosImportantesScreen(
    onBack: () -> Unit = {},
    onHomeClick: () -> Unit = {},
    onHealthClick: () -> Unit = {},
    onNotifClick: () -> Unit = {},
) {
    val currentUser = FirebaseAuth.getInstance().currentUser
    val displayName = currentUser?.displayName ?: "Usuario"
    val initials = buildString {
        displayName.split(" ").forEach { word -> word.firstOrNull()?.let { append(it.uppercaseChar()) } }
    }.take(2).ifEmpty { "VS" }

    val qrBitmap = remember { generateQrBitmap(currentUser?.uid ?: "demo_user") }

    Box(modifier = Modifier.fillMaxSize().background(DashBg)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 90.dp),
        ) {
            Spacer(Modifier.height(52.dp))

            // Back
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

            // Avatar
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(DashBlue),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = initials,
                            fontFamily = Manrope,
                            fontWeight = FontWeight.Bold,
                            fontSize = 32.sp,
                            color = Color.White,
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .align(Alignment.BottomEnd),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.Edit, contentDescription = null, tint = DashBlue, modifier = Modifier.size(16.dp))
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // White card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(Color.White)
                    .padding(24.dp),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Datos Importantes",
                        fontFamily = Manrope,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Color(0xFF1A1A2E),
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(Modifier.height(24.dp))

                    // Document files row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        DocumentCard(modifier = Modifier.weight(1f), filename = "CURP.pdf")
                        DocumentCard(modifier = Modifier.weight(1f), filename = "INE.pdf")
                    }

                    Spacer(Modifier.height(28.dp))

                    // QR Code
                    if (qrBitmap != null) {
                        Image(
                            bitmap = qrBitmap.asImageBitmap(),
                            contentDescription = "QR Médico",
                            modifier = Modifier.size(160.dp),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(160.dp)
                                .border(2.dp, DashBlue, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("QR Médico", fontFamily = Manrope, color = DashBlue, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(Modifier.height(32.dp))

                    // Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        TextButton(
                            onClick = onBack,
                            modifier = Modifier.weight(1f).height(50.dp),
                        ) {
                            Text(
                                text = "Cancelar",
                                fontFamily = Manrope,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp,
                                color = DashBlue,
                            )
                        }
                        Button(
                            onClick = {},
                            modifier = Modifier.weight(1f).height(50.dp),
                            shape = RoundedCornerShape(25.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DashBlue),
                        ) {
                            Text("Guardar", fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = Color.White)
                        }
                    }
                }
            }
        }

        BottomNav(
            selected = BottomNavTab.PROFILE,
            onSelect = { tab ->
                when (tab) {
                    BottomNavTab.HOME   -> onHomeClick()
                    BottomNavTab.HEALTH -> onHealthClick()
                    BottomNavTab.CHAT   -> onNotifClick()
                    else -> {}
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun DocumentCard(modifier: Modifier = Modifier, filename: String) {
    Box(
        modifier = modifier
            .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(12.dp))
            .padding(14.dp),
    ) {
        Column {
            Text(
                text = "Nombre",
                fontFamily = Manrope,
                fontSize = 10.sp,
                color = Color(0xFFB0B0B0),
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = filename,
                    fontFamily = Manrope,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = Color(0xFF1A1A2E),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                // PDF icon
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFFFFEBEE)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "PDF",
                        fontFamily = Manrope,
                        fontWeight = FontWeight.Bold,
                        fontSize = 8.sp,
                        color = Color(0xFFE53935),
                    )
                }
            }
        }
    }
}

private fun generateQrBitmap(data: String): Bitmap? {
    return try {
        val size = 512
        val qrData = "vitalsense://patient/$data"
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        // Simple QR placeholder pattern
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.WHITE)
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.rgb(0x11, 0x69, 0xFF)
            isAntiAlias = true
        }
        val cellSize = size / 25
        for (row in 0 until 25) {
            for (col in 0 until 25) {
                val isDark = (row + col + row * col) % 3 == 0 ||
                    (row < 7 && col < 7) ||
                    (row < 7 && col >= 18) ||
                    (row >= 18 && col < 7)
                if (isDark) {
                    canvas.drawRect(
                        (col * cellSize).toFloat(),
                        (row * cellSize).toFloat(),
                        ((col + 1) * cellSize).toFloat(),
                        ((row + 1) * cellSize).toFloat(),
                        paint,
                    )
                }
            }
        }
        bitmap
    } catch (e: Exception) {
        null
    }
}
