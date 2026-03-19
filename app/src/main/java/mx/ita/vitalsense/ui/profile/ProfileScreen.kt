package mx.ita.vitalsense.ui.profile

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import mx.ita.vitalsense.ui.theme.*

private val SuccessGreen = Color(0xFF4CAF50)

@Composable
fun ProfileScreen(
    onDeviceClick: () -> Unit,
    onBack: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("vitalsense_watch_prefs", Context.MODE_PRIVATE) }
    val isWatchPaired = remember { mutableStateOf(prefs.getBoolean("code_paired", false)) }
    val user = FirebaseAuth.getInstance().currentUser
    val userName = user?.displayName ?: "Usuario VitalSense"

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
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Atrás", tint = TextPrimary)
                }
                Text(
                    "Mi Perfil",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    modifier = Modifier.padding(start = 8.dp)
                )
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
            Spacer(Modifier.height(20.dp))

            // User Info Header
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(PrimaryBlue.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = userName.take(1).uppercase(),
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryBlue
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(userName, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(user?.email ?: "", fontSize = 14.sp, color = TextSecondary)

            Spacer(Modifier.height(40.dp))

            // Watch Status Section
            Text(
                "Dispositivos Vinculados",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(12.dp))

            // Tarjeta de Reloj
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onDeviceClick() },
                shape = RoundedCornerShape(20.dp),
                color = Color.White,
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isWatchPaired.value) SuccessGreen.copy(alpha = 0.1f) else Color.LightGray.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Watch,
                            contentDescription = null,
                            tint = if (isWatchPaired.value) SuccessGreen else Color.Gray
                        )
                    }

                    Spacer(Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Galaxy Watch 4",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            color = TextPrimary
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(if (isWatchPaired.value) SuccessGreen else Color.Gray)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (isWatchPaired.value) "Sincronizado" else "No vinculado",
                                fontSize = 12.sp,
                                color = if (isWatchPaired.value) SuccessGreen else Color.Gray
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // Logout Button
            Button(
                onClick = onLogout,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFFFFEAEA))
            ) {
                Icon(Icons.Rounded.Logout, null, tint = Color.Red)
                Spacer(Modifier.width(8.dp))
                Text("Cerrar Sesión", color = Color.Red, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(100.dp))
        }
    }
}
