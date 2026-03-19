package mx.ita.vitalsense.ui.dashboard

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import mx.ita.vitalsense.R
import mx.ita.vitalsense.data.model.VitalsData

// ─── Figma / Image design tokens ──────────────────────────────────────────────
private val DashboardBg    = Color(0xFFF7F9FC)
private val BlueCardBg     = Color(0xFF90C2F9)
private val PrimaryBlue    = Color(0xFF1169FF)
private val TextDark       = Color(0xFF221F1F)
private val TextGray       = Color(0xFF7F8C8D)
private val SuccessGreen   = Color(0xFF10B981)
private val HeartRateCurve = Color(0xFFEF4444)

@Composable
fun DashboardScreen(
    onNavigateToNotifications: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onNavigateToDetailed: () -> Unit = {},
    onNavigateToReports: () -> Unit = {},
    onNavigateToHome: () -> Unit = {},
    onNavigateToChat: () -> Unit = {},
    onConnectDevice: () -> Unit = {},
    vm: DashboardViewModel = viewModel(),
) {
    val uiState by vm.uiState.collectAsState()
    val currentUser = FirebaseAuth.getInstance().currentUser
    val userName = currentUser?.displayName ?: "Usuario"

    Scaffold(
        bottomBar = { 
            BottomNavigationBar(
                onHomeClick = onNavigateToHome,
                onFavoriteClick = onNavigateToReports,
                onChatClick = onNavigateToChat,
                onProfileClick = onNavigateToProfile
            ) 
        },
        containerColor = DashboardBg
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(24.dp))
            
            // 1. User Header
            UserHeader(
                name = userName,
                onNotificationClick = onNavigateToNotifications
            )

            Spacer(Modifier.height(24.dp))

            // 2. Search Bar
            val snackbarHostState = remember { SnackbarHostState() }
            val coroutineScope = rememberCoroutineScope()
            
            SearchBar(
                onSearchClick = {
                    coroutineScope.launch { snackbarHostState.showSnackbar("Función de búsqueda en desarrollo") }
                },
                onSettingsClick = onNavigateToProfile
            )
            
            Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                SnackbarHost(hostState = snackbarHostState)
            }

            Spacer(Modifier.height(24.dp))

            // 3. Blue Container with Cards
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp))
                    .background(BlueCardBg)
                    .padding(24.dp)
            ) {
                // Sleep Card
                SectionHeader(title = "Esta semana", showArrow = true)
                Spacer(Modifier.height(16.dp))
                SleepMetricCard(
                    sleepData = uiState.sleepData,
                    onClick = onNavigateToReports
                )

                Spacer(Modifier.height(24.dp))

                // Health Metrics Card (Graph)
                HealthMetricsGraphCard(
                    history = uiState.vitalsHistory,
                    onSeeAllClick = onNavigateToDetailed
                )
                
                Spacer(Modifier.height(24.dp))

                // Medications Card
                MedicationsCard(
                    medications = uiState.medications,
                    onSeeAllClick = onNavigateToDetailed
                )
                
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

@Composable
private fun UserHeader(name: String, onNotificationClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground), // Sustituir por avatar real
            contentDescription = null,
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF1169FF)),
            contentScale = ContentScale.Crop
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text("Bienvenido", color = TextGray, fontSize = 14.sp)
            Text(name, color = TextDark, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        // Notification bell with dot
        Box(modifier = Modifier.clickable { onNotificationClick() }) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                shadowElevation = 2.dp
            ) {
                Icon(
                    Icons.Default.Notifications,
                    contentDescription = null,
                    modifier = Modifier.padding(12.dp),
                    tint = TextDark
                )
            }
            // Red dot
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(Color.Red, CircleShape)
                    .align(Alignment.TopEnd)
                    .offset(x = (-10).dp, y = 10.dp)
            )
        }
    }
}

@Composable
private fun SearchBar(onSearchClick: () -> Unit, onSettingsClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier
                .weight(1f)
                .height(56.dp)
                .clickable { onSearchClick() },
            shape = RoundedCornerShape(16.dp),
            color = Color.White.copy(alpha = 0.5f)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Icon(Icons.Default.Search, contentDescription = null, tint = TextGray)
                Spacer(Modifier.width(8.dp))
                Text("Buscar paciente...", color = TextGray)
            }
        }
        Spacer(Modifier.width(16.dp))
        Surface(
            modifier = Modifier.size(56.dp).clickable { onSettingsClick() },
            shape = RoundedCornerShape(16.dp),
            color = PrimaryBlue
        ) {
            Icon(
                Icons.Default.Tune,
                contentDescription = "Configuración",
                tint = Color.White,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String, showArrow: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = TextDark, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        if (showArrow) {
            Spacer(Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, modifier = Modifier.size(16.dp), tint = TextDark)
        }
    }
}

@Composable
private fun SleepMetricCard(
    sleepData: mx.ita.vitalsense.data.model.SleepData?,
    onClick: () -> Unit
) {
    val progress = (sleepData?.score ?: 0) / 100f
    val scoreText = sleepData?.score?.toString() ?: "0"

    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = Color.White
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Circular progress
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(60.dp),
                    color = SuccessGreen,
                    strokeWidth = 6.dp,
                    trackColor = SuccessGreen.copy(0.1f)
                )
                Text("$scoreText%", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TextDark)
            }
            Spacer(Modifier.width(16.dp))
            Text("Sueño", color = SuccessGreen, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text("Promedio de Hoy", fontSize = 11.sp, color = TextGray)
                Text(sleepData?.estado ?: "Sin Datos", color = SuccessGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun HealthMetricsGraphCard(
    history: List<VitalsData>,
    onSeeAllClick: () -> Unit
) {
    val displayBpm = history.lastOrNull()?.heartRate ?: 0

    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onSeeAllClick() },
        shape = RoundedCornerShape(24.dp),
        color = Color.White
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Metricas de Salud", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.weight(1f))
                Text("Ver todo", color = TextGray, fontSize = 12.sp, modifier = Modifier.clickable { onSeeAllClick() })
                Spacer(Modifier.width(4.dp))
                IconButton(
                    onClick = onSeeAllClick,
                    modifier = Modifier.size(24.dp).background(PrimaryBlue, CircleShape)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = Color.White, modifier = Modifier.size(12.dp))
                }
            }
            
            Spacer(Modifier.height(20.dp))
            
            // Gráfico de Ritmo Cardíaco
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .padding(bottom = 16.dp),
                contentAlignment = Alignment.BottomCenter
            ) {
                val points = history.takeLast(10).map { it.heartRate.toFloat() / 200f }
                if (points.isNotEmpty()) {
                    mx.ita.vitalsense.ui.reports.LineChart(
                        modifier = Modifier.fillMaxSize(),
                        color = HeartRateCurve,
                        points = points
                    )
                } else {
                    Text("No hay datos de ritmo cardíaco", color = HeartRateCurve.copy(0.5f))
                }
                
                // Tooltip
                Surface(
                    modifier = Modifier.align(Alignment.TopEnd).padding(end = 10.dp, top = 10.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White,
                    shadowElevation = 4.dp
                ) {
                    Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Heart Rate", fontSize = 10.sp, color = TextGray)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Favorite, null, tint = HeartRateCurve, modifier = Modifier.size(12.dp))
                            Text(" $displayBpm", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MedicationsCard(
    medications: List<mx.ita.vitalsense.data.model.Medication>,
    onSeeAllClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onSeeAllClick() },
        shape = RoundedCornerShape(24.dp),
        color = Color.White
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Medicamentos", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.weight(1f))
                Text("Ver todo", color = TextGray, fontSize = 12.sp)
                Spacer(Modifier.width(4.dp))
                IconButton(
                    onClick = onSeeAllClick,
                    modifier = Modifier.size(24.dp).background(PrimaryBlue, CircleShape)
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = Color.White, modifier = Modifier.size(12.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
            
            if (medications.isEmpty()) {
                Text("No hay medicamentos activos", color = TextGray, fontSize = 14.sp)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    medications.take(3).forEach { med ->
                        Surface(color = Color(0xFFE3F2FD), shape = RoundedCornerShape(12.dp)) {
                            Text(med.nombre, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DotsIndicator(selected: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(3) { index ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(width = if (index == selected) 16.dp else 8.dp, height = 8.dp)
                    .clip(CircleShape)
                    .background(if (index == selected) PrimaryBlue else Color.White.copy(0.5f))
            )
        }
    }
}

@Composable
private fun BottomNavigationBar(
    onHomeClick: () -> Unit,
    onFavoriteClick: () -> Unit,
    onChatClick: () -> Unit,
    onProfileClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .height(72.dp),
        shape = RoundedCornerShape(24.dp),
        color = PrimaryBlue,
        shadowElevation = 8.dp
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val icons = listOf(
                Icons.Rounded.Home to onHomeClick,
                Icons.Rounded.Favorite to onFavoriteClick,
                Icons.Rounded.ChatBubbleOutline to onChatClick,
                Icons.Rounded.Person to onProfileClick
            )
            
            icons.forEachIndexed { index, (icon, onClick) ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onClick() }
                ) {
                    Icon(icon, null, tint = Color.White, modifier = Modifier.size(28.dp))
                    if (index == 0) {
                        Spacer(Modifier.height(4.dp))
                        Box(Modifier.size(width = 16.dp, height = 2.dp).background(Color.White))
                    }
                }
            }
        }
    }
}
