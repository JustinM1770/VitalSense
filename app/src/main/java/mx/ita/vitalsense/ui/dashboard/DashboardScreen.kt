package mx.ita.vitalsense.ui.dashboard

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import mx.ita.vitalsense.R
import mx.ita.vitalsense.data.model.Medication
import mx.ita.vitalsense.data.model.SleepData
import mx.ita.vitalsense.data.model.VitalsData
import mx.ita.vitalsense.ui.components.BottomNav
import mx.ita.vitalsense.ui.components.BottomNavTab
import mx.ita.vitalsense.ui.theme.ChartRed
import mx.ita.vitalsense.ui.theme.DashBg
import mx.ita.vitalsense.ui.theme.DashBlue
import mx.ita.vitalsense.ui.theme.DashCard
import mx.ita.vitalsense.ui.theme.Manrope
import mx.ita.vitalsense.ui.theme.SleepGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

private enum class LibreProfileMode(val key: String, val label: String) {
    AUTO("auto", "Auto"),
    GENERAL("general", "General"),
    DIABETES("diabetes", "Diabetes"),
}

private enum class LibreContextMode(val key: String, val label: String) {
    AUTO("auto", "Auto"),
    AYUNO("ayuno", "Ayuno"),
    POSTPRANDIAL("postprandial", "Postprandial"),
}

// ─── Figma / Image design tokens ──────────────────────────────────────────────
private val BlueCardBg     = Color(0xFF90C2F9)
private val PrimaryBlue    = Color(0xFF1169FF)
private val TextDark       = Color(0xFF221F1F)
private val TextGray       = Color(0xFF7F8C8D)
private val SuccessGreen   = Color(0xFF10B981)
private val HeartRateCurve = Color(0xFFEF4444)

private enum class MetricCardType { SLEEP, SPO2, HR, KCAL }

@Composable
fun DashboardScreen(
    onNavigateToNotifications: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {},
    onNavigateToDetailed: () -> Unit = {},
    onNavigateToReports: () -> Unit = {},
    onNavigateToHome: () -> Unit = {},
    onNavigateToChat: () -> Unit = {},
    onMedicationClick: () -> Unit = {},
    onLibreScanClick: () -> Unit = {},
    onConnectDevice: () -> Unit = {},
    onPatientClick: (String) -> Unit = {},
    onProfileClick: () -> Unit = {},
    onReportClick: () -> Unit = {},
    onNotifClick: () -> Unit = {},
    onEmergency: (VitalsData) -> Unit = {},
    vm: DashboardViewModel = viewModel(),
) {
    // Observar anomalías críticas detectadas por el ViewModel y disparar la pantalla de QR
    LaunchedEffect(vm) {
        vm.emergencyTrigger.collect { vitals ->
            onEmergency(vitals)
        }
    }

    var showSettingsDialog by remember { mutableStateOf(false) }
    val uiState by vm.uiState.collectAsState()
    val currentUser = FirebaseAuth.getInstance().currentUser
    val context = LocalContext.current
    val profilePrefs = remember { context.getSharedPreferences("vitalsense_profile", Context.MODE_PRIVATE) }
    val uid = currentUser?.uid.orEmpty()
    var libreGlucoseProfile by remember {
        mutableStateOf(
            if (uid.isNotEmpty()) profilePrefs.getString("libre_glucose_profile_$uid", "auto") ?: "auto" else "auto"
        )
    }
    var libreGlucoseContext by remember {
        mutableStateOf(
            if (uid.isNotEmpty()) profilePrefs.getString("libre_glucose_context_$uid", "auto") ?: "auto" else "auto"
        )
    }

    LaunchedEffect(uid) {
        if (uid.isEmpty()) return@LaunchedEffect
        try {
            val snapshot = com.google.firebase.database.FirebaseDatabase.getInstance()
                .getReference("patients/$uid/profile")
                .get()
                .await()
            val remoteProfile = snapshot.child("glucoseProfileMode").getValue(String::class.java)
            val remoteContext = snapshot.child("glucoseContextMode").getValue(String::class.java)
            if (!remoteProfile.isNullOrBlank()) {
                libreGlucoseProfile = remoteProfile
                profilePrefs.edit().putString("libre_glucose_profile_$uid", remoteProfile).apply()
            }
            if (!remoteContext.isNullOrBlank()) {
                libreGlucoseContext = remoteContext
                profilePrefs.edit().putString("libre_glucose_context_$uid", remoteContext).apply()
            }
        } catch (_: Exception) {
            // Keep local preferences if cloud is unavailable.
        }
    }

    val userName = currentUser?.displayName ?: "Usuario"
    val userAvatarUri = if (uid.isNotEmpty()) profilePrefs.getString("avatar_uri_$uid", null) else null
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    Box(modifier = Modifier.fillMaxSize().background(DashBg)) {
        when (val state = uiState) {
            is DashboardUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = DashBlue, strokeWidth = 3.dp)
            }
            else -> {
                val patients = if (state is DashboardUiState.Success) state.patients else emptyList()
                val sleepData = if (state is DashboardUiState.Success) state.sleepData else null
                val vitalsHistory = if (state is DashboardUiState.Success) state.vitalsHistory else emptyList()
                val medications = if (state is DashboardUiState.Success) state.medications else emptyList()
                val libreLastGlucose = if (uid.isNotEmpty()) profilePrefs.getFloat("libre_last_glucose_$uid", 0f).toDouble() else 0.0
                val libreLastTime = if (uid.isNotEmpty()) profilePrefs.getLong("libre_last_time_$uid", 0L) else 0L
                val libreLastSource = if (uid.isNotEmpty()) profilePrefs.getString("libre_last_source_$uid", "") ?: "" else ""
                DashboardContent(
                    userName = userName,
                    userAvatarUri = userAvatarUri,
                    patients = patients,
                    sleepData = sleepData,
                    vitalsHistory = vitalsHistory,
                    medications = medications,
                    onPatientClick = onPatientClick,
                    onReportClick = onReportClick,
                    onProfileClick = { onNavigateToProfile(); onProfileClick() },
                    onNotifClick = { onNavigateToNotifications(); onNotifClick() },
                    onDetailedClick = onNavigateToDetailed,
                    onMedicationClick = onMedicationClick,
                    onLibreScanClick = onLibreScanClick,
                    libreLastGlucose = libreLastGlucose,
                    libreLastTime = libreLastTime,
                    libreLastSource = libreLastSource,
                    libreGlucoseProfile = libreGlucoseProfile,
                    libreGlucoseContext = libreGlucoseContext,
                    snackbarHostState = snackbarHostState,
                    onSearchClick = {
                        coroutineScope.launch { snackbarHostState.showSnackbar("Función de búsqueda en desarrollo") }
                    },
                    onSettingsClick = { showSettingsDialog = true },
                )
            }
        }

        BottomNav(
            selected = BottomNavTab.HOME,
            onSelect = { tab ->
                when (tab) {
                    BottomNavTab.HEALTH  -> { onReportClick(); onNavigateToReports() }
                    BottomNavTab.PROFILE -> { onProfileClick(); onNavigateToProfile() }
                    BottomNavTab.CHAT    -> { onNotifClick(); onNavigateToChat() }
                    else -> {}
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp),
        )

        if (showSettingsDialog) {
            SettingsDialog(
                isWatchPaired = uiState.isWatchPaired,
                libreProfileMode = libreGlucoseProfile,
                libreContextMode = libreGlucoseContext,
                onDismiss = { showSettingsDialog = false },
                onWearableClick = {
                    showSettingsDialog = false
                    onConnectDevice()
                },
                onLibreClick = {
                    showSettingsDialog = false
                    onLibreScanClick()
                },
                onLibreModeChanged = { profileMode, contextMode ->
                    if (uid.isNotEmpty()) {
                        profilePrefs.edit()
                            .putString("libre_glucose_profile_$uid", profileMode)
                            .putString("libre_glucose_context_$uid", contextMode)
                            .apply()
                        com.google.firebase.database.FirebaseDatabase.getInstance()
                            .getReference("patients/$uid/profile")
                            .updateChildren(
                                mapOf(
                                    "glucoseProfileMode" to profileMode,
                                    "glucoseContextMode" to contextMode,
                                )
                            )
                    }
                    libreGlucoseProfile = profileMode
                    libreGlucoseContext = contextMode
                },
                onRestoreBackupClick = {
                    if (uid.isBlank()) {
                        coroutineScope.launch { snackbarHostState.showSnackbar("Inicia sesión para restaurar respaldo") }
                    } else {
                        coroutineScope.launch {
                            val result = restoreBackupFromFirebase(
                                uid = uid,
                                profilePrefs = profilePrefs,
                                watchPrefs = context.getSharedPreferences("vitalsense_watch_prefs", Context.MODE_PRIVATE),
                            )

                            if (result.success) {
                                result.profileMode?.let { libreGlucoseProfile = it }
                                result.contextMode?.let { libreGlucoseContext = it }
                            }

                            snackbarHostState.showSnackbar(result.message)
                        }
                    }
                }
            )
        }
    }
}

private data class RestoreBackupResult(
    val success: Boolean,
    val message: String,
    val profileMode: String? = null,
    val contextMode: String? = null,
)

private suspend fun restoreBackupFromFirebase(
    uid: String,
    profilePrefs: SharedPreferences,
    watchPrefs: SharedPreferences,
): RestoreBackupResult {
    return try {
        val db = com.google.firebase.database.FirebaseDatabase.getInstance()

        val profileSnapshot = db.getReference("patients/$uid/profile").get().await()
        val patientSnapshot = db.getReference("patients/$uid").get().await()
        val watchSnapshot = db.getReference("patients/$uid/watch").get().await()

        val editor = profilePrefs.edit()

        fun putStringIfExists(localKey: String, remoteKey: String) {
            profileSnapshot.child(remoteKey).getValue(String::class.java)?.let { value ->
                if (value.isNotBlank()) editor.putString("${localKey}_$uid", value)
            }
        }

        putStringIfExists("nombre", "nombre")
        putStringIfExists("apellidos", "apellidos")
        putStringIfExists("nacimiento", "nacimiento")
        putStringIfExists("celular", "celular")
        putStringIfExists("genero", "genero")
        putStringIfExists("frecuencia", "frecuencia")
        profileSnapshot.child("tipoSangre").getValue(String::class.java)?.let { value ->
            if (value.isNotBlank()) editor.putString("tipo_sangre_$uid", value)
        }
        profileSnapshot.child("avatarUri").getValue(String::class.java)?.let { value ->
            if (value.isNotBlank()) editor.putString("avatar_uri_$uid", value)
        }

        val glucoseProfileMode = profileSnapshot.child("glucoseProfileMode").getValue(String::class.java)
        val glucoseContextMode = profileSnapshot.child("glucoseContextMode").getValue(String::class.java)
        if (!glucoseProfileMode.isNullOrBlank()) editor.putString("libre_glucose_profile_$uid", glucoseProfileMode)
        if (!glucoseContextMode.isNullOrBlank()) editor.putString("libre_glucose_context_$uid", glucoseContextMode)

        val documents = profileSnapshot.child("documents").children.mapNotNull { it.getValue(String::class.java) }
        if (documents.isNotEmpty()) editor.putStringSet("documents_$uid", documents.toSet())

        profileSnapshot.child("driveTreeUri").getValue(String::class.java)?.let { editor.putString("drive_tree_uri_$uid", it) }
        profileSnapshot.child("driveFolderUrl").getValue(String::class.java)?.let { editor.putString("drive_folder_url_$uid", it) }

        editor.putBoolean("cuestionario_completed_$uid", true)

        patientSnapshot.child("glucose").getValue(Double::class.java)?.let { glucose ->
            editor.putFloat("libre_last_glucose_$uid", glucose.toFloat())
        }
        patientSnapshot.child("timestamp").getValue(Long::class.java)?.let { ts ->
            editor.putLong("libre_last_time_$uid", ts)
        }
        patientSnapshot.child("glucoseSource").getValue(String::class.java)?.let { src ->
            editor.putString("libre_last_source_$uid", src)
        }

        editor.apply()

        val watchPaired = watchSnapshot.child("paired").getValue(Boolean::class.java) ?: false
        val watchCode = watchSnapshot.child("code").getValue(String::class.java).orEmpty()
        val watchName = watchSnapshot.child("deviceName").getValue(String::class.java) ?: "Wearable"
        watchPrefs.edit()
            .putBoolean("code_paired", watchPaired)
            .putString("paired_code", watchCode)
            .putString("paired_device_name", watchName)
            .apply()

        RestoreBackupResult(
            success = true,
            message = "Respaldo restaurado desde Firebase",
            profileMode = glucoseProfileMode,
            contextMode = glucoseContextMode,
        )
    } catch (e: Exception) {
        RestoreBackupResult(
            success = false,
            message = "No se pudo restaurar: ${e.message ?: "sin conexión"}",
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DashboardContent(
    userName: String,
    userAvatarUri: String?,
    patients: List<VitalsData>,
    sleepData: SleepData?,
    vitalsHistory: List<VitalsData>,
    medications: List<Medication>,
    onPatientClick: (String) -> Unit,
    onReportClick: () -> Unit,
    onProfileClick: () -> Unit,
    onNotifClick: () -> Unit,
    onDetailedClick: () -> Unit,
    onMedicationClick: () -> Unit,
    onLibreScanClick: () -> Unit,
    libreLastGlucose: Double,
    libreLastTime: Long,
    libreLastSource: String,
    libreGlucoseProfile: String,
    libreGlucoseContext: String,
    snackbarHostState: SnackbarHostState,
    onSearchClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 90.dp),
    ) {
        Spacer(Modifier.height(24.dp))

        // ── Header: avatar + saludo + campana ────────────────────────────────
        UserHeader(
            name = userName,
            avatarUri = userAvatarUri,
            onNotificationClick = onNotifClick,
            onProfileClick = onProfileClick,
        )

        Spacer(Modifier.height(24.dp))

        // ── Search bar ───────────────────────────────────────────────────────
        SearchBar(
            onSearchClick = onSearchClick,
            onSettingsClick = onSettingsClick,
        )

        Spacer(Modifier.height(24.dp))

        // ── Blue rounded container ────────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp))
                .background(BlueCardBg)
                .padding(24.dp)
        ) {
            // ── "Esta semana" — sleep / HR / Kcal pager ───────────────────────
            val visibleCards = remember {
                mutableStateListOf(MetricCardType.SLEEP, MetricCardType.SPO2)
            }
            val dashboardScope = rememberCoroutineScope()

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SectionHeader(title = "Esta semana", showArrow = true)
                Spacer(Modifier.weight(1f))
                Surface(
                    modifier = Modifier.size(30.dp).clickable {
                        val next = listOf(MetricCardType.HR, MetricCardType.KCAL)
                            .firstOrNull { it !in visibleCards }
                        if (next != null) {
                            visibleCards.add(next)
                        } else {
                            dashboardScope.launch { snackbarHostState.showSnackbar("Ya agregaste todas las tarjetas") }
                        }
                    },
                    shape = CircleShape,
                    color = Color.White,
                    shadowElevation = 2.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(text = "+", color = DashBlue, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            val pagerState = rememberPagerState(pageCount = { visibleCards.size })

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth(),
            ) { page ->
                when (visibleCards.getOrElse(page) { MetricCardType.SLEEP }) {
                    MetricCardType.SLEEP -> SleepMetricCard(sleepData = sleepData, onClick = onReportClick)
                    MetricCardType.SPO2 -> Spo2MiniCard(patients)
                    MetricCardType.HR -> HrMiniCard(patients)
                    MetricCardType.KCAL -> KcalMiniCard(patients)
                }
            }

            Spacer(Modifier.height(12.dp))
            PagerDots(count = visibleCards.size, selected = pagerState.currentPage)

            Spacer(Modifier.height(24.dp))

            // ── Libre resumen rápido ────────────────────────────────────────
            LibreQuickCard(
                glucose = libreLastGlucose,
                timestamp = libreLastTime,
                source = libreLastSource,
                profileMode = libreGlucoseProfile,
                contextMode = libreGlucoseContext,
                onScanClick = onLibreScanClick,
            )

            Spacer(Modifier.height(16.dp))

            // ── Extra UX: acciones rápidas ─────────────────────────────────
            QuickActionsCard(
                onLibreScanClick = onLibreScanClick,
                onMedicationClick = onMedicationClick,
            )

            Spacer(Modifier.height(24.dp))

            // ── Métricas de Salud ─────────────────────────────────────────────
            HealthMetricsGraphCard(
                patients = patients,
                vitalsHistory = vitalsHistory,
                onSeeAllClick = onDetailedClick,
            )

            Spacer(Modifier.height(24.dp))

            // ── Medicamentos ──────────────────────────────────────────────────
            MedicationsCard(
                medications = medications,
                onSeeAllClick = onMedicationClick,
            )

            Spacer(Modifier.height(24.dp))

            Spacer(Modifier.height(16.dp))
        }
    }
}

// ─── User Header ──────────────────────────────────────────────────────────────

@Composable
private fun UserHeader(
    name: String,
    avatarUri: String?,
    onNotificationClick: () -> Unit,
    onProfileClick: () -> Unit,
) {
    // Dynamic badge: check Firebase for unread alerts
    var hasUnread by remember { mutableStateOf(false) }
    val auth = FirebaseAuth.getInstance()
    DisposableEffect(Unit) {
        val userId = auth.currentUser?.uid ?: "global"
        val db = com.google.firebase.database.FirebaseDatabase.getInstance()
        val ref = db.getReference("alerts").child(userId)
        val listener = object : com.google.firebase.database.ValueEventListener {
            override fun onDataChange(snapshot: com.google.firebase.database.DataSnapshot) {
                hasUnread = snapshot.children.any { child ->
                    @Suppress("UNCHECKED_CAST")
                    val data = child.value as? Map<String, Any>
                    data != null && data["read"] != true
                }
            }
            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {}
        }
        ref.addValueEventListener(listener)
        onDispose { ref.removeEventListener(listener) }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar circular con inicial
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(DashBlue)
                .clickable { onProfileClick() },
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
                    text = name.firstOrNull()?.uppercaseChar()?.toString() ?: "U",
                    fontFamily = Manrope,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Color.White,
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text("Bienvenido", color = TextGray, fontSize = 14.sp, fontFamily = Manrope)
            Text(name, color = TextDark, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = Manrope)
        }

        // Campana de notificaciones con punto rojo
        Box(modifier = Modifier.clickable { onNotificationClick() }) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                shadowElevation = 2.dp
            ) {
                Icon(
                    Icons.Filled.Notifications,
                    contentDescription = "Notificaciones",
                    modifier = Modifier.padding(12.dp),
                    tint = TextDark
                )
            }
            // Red dot
            if (hasUnread) {
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
}

// ─── Search bar ───────────────────────────────────────────────────────────────

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
                Icon(Icons.Filled.Search, contentDescription = null, tint = TextGray)
                Spacer(Modifier.width(8.dp))
                Text("Buscar paciente...", color = TextGray, fontFamily = Manrope)
            }
        }
        Spacer(Modifier.width(16.dp))
        Surface(
            modifier = Modifier.size(56.dp).clickable { onSettingsClick() },
            shape = RoundedCornerShape(16.dp),
            color = PrimaryBlue
        ) {
            Icon(
                Icons.Filled.Tune,
                contentDescription = "Configuración",
                tint = Color.White,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

// ─── Device Connection Card ───────────────────────────────────────────────────

@Composable
private fun DeviceConnectionCard(onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        color = PrimaryBlue,
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color.White.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Bluetooth, contentDescription = null, tint = Color.White)
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Vincular Reloj / Sensor", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Sincroniza tus signos vitales", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
            }
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = Color.White)
        }
    }
}

// ─── Watch Status Card ────────────────────────────────────────────────────────

@Composable
private fun WatchStatusCard(
    deviceName: String,
    onDisconnect: () -> Unit,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        shadowElevation = 2.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(SuccessGreen, CircleShape)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "$deviceName vinculado",
                    color = SuccessGreen,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Spacer(Modifier.weight(1f))
                Text("Sincronizado", color = SuccessGreen, fontSize = 11.sp)
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "El reloj está enviando datos en tiempo real.",
                    fontSize = 12.sp,
                    color = TextGray,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = onDisconnect,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                ) {
                    Text("Desvincular", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ─── Section header ───────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String, showArrow: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = TextDark, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = Manrope)
        if (showArrow) {
            Spacer(Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, modifier = Modifier.size(16.dp), tint = TextDark)
        }
    }
}

// ─── Sleep card ───────────────────────────────────────────────────────────────

@Composable
private fun SleepMetricCard(sleepData: SleepData?, onClick: () -> Unit) {
    val hasSleepData = sleepData != null && sleepData.horas > 0f
    val score = if (hasSleepData) sleepData?.score?.coerceIn(0, 100) ?: 0 else 0
    val progress = score / 100f
    val scoreText = score.toString()
    val sleepStatus = if (hasSleepData) {
        sleepData?.estado?.takeIf { it.isNotBlank() } ?: "Regular"
    } else {
        "No durmió"
    }
    val sleepStatusColor = if (hasSleepData) SuccessGreen else TextGray

    WhiteCard(modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(72.dp),
                    color = SuccessGreen,
                    strokeWidth = 6.dp,
                    trackColor = SuccessGreen.copy(0.1f)
                )
                Text("$scoreText%", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1A1A2E), fontFamily = Manrope)
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Sueño", color = SleepGreen, fontWeight = FontWeight.Bold, fontSize = 18.sp, fontFamily = Manrope)
                val today = LocalDate.now()
                Text(
                    text = "${today.dayOfMonth} ${today.month.getDisplayName(TextStyle.FULL, Locale.forLanguageTag("es")).replaceFirstChar { it.uppercase() }} ${today.year}",
                    fontFamily = Manrope, fontSize = 12.sp, color = Color(0xFF8A8A8A),
                )
                Text(sleepStatus, color = sleepStatusColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = Manrope)
            }
        }
    }
}

@Composable
private fun HrMiniCard(patients: List<VitalsData>) {
    val hr = patients.firstOrNull()?.heartRate?.takeIf { it > 0 }
    WhiteCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Favorite, contentDescription = null, tint = HeartRateCurve, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Ritmo cardiaco", fontFamily = Manrope, fontSize = 13.sp, color = Color(0xFF8A8A8A))
                Text(if (hr != null) "$hr BPM" else "-- BPM", fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Color(0xFF1A1A2E))
            }
        }
    }
}

@Composable
private fun KcalMiniCard(patients: List<VitalsData>) {
    WhiteCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("--", fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 36.sp, color = Color(0xFF1A1A2E))
            Text("Kcal hoy", fontFamily = Manrope, fontSize = 14.sp, color = Color(0xFF8A8A8A))
        }
    }
}

@Composable
private fun Spo2MiniCard(patients: List<VitalsData>) {
    val spo2 = patients.firstOrNull()?.spo2 ?: 98
    WhiteCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.MonitorHeart, contentDescription = null, tint = mx.ita.vitalsense.ui.theme.SpO2Green, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Column {
                Text("SpO₂", fontFamily = Manrope, fontSize = 13.sp, color = Color(0xFF8A8A8A))
                Text("$spo2%", fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Color(0xFF1A1A2E))
            }
        }
    }
}

// ─── Health Metrics Graph Card ─────────────────────────────────────────────────

@Composable
private fun HealthMetricsGraphCard(
    patients: List<VitalsData>,
    vitalsHistory: List<VitalsData>,
    onSeeAllClick: () -> Unit,
) {
    val displayBpm = vitalsHistory.lastOrNull()?.heartRate ?: patients.firstOrNull()?.heartRate ?: 0

    WhiteCard(modifier = Modifier.fillMaxWidth().clickable { onSeeAllClick() }) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Metricas de Salud",
                    fontFamily = Manrope,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color(0xFF1A1A2E),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Ver todo",
                        color = TextGray,
                        fontSize = 12.sp,
                        fontFamily = Manrope,
                        modifier = Modifier.clickable { onSeeAllClick() }
                    )
                    Spacer(Modifier.width(8.dp))
                    ArrowCircle(onClick = onSeeAllClick)
                }
            }

            Spacer(Modifier.height(16.dp))

            // Chart using Canvas (bezier implementation)
            WeeklyHrChart(vitalsHistory = vitalsHistory)

            // Tooltip overlay
            if (displayBpm > 0) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Favorite, null, tint = HeartRateCurve, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("$displayBpm BPM", fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = Manrope, color = Color(0xFF1A1A2E))
                    Spacer(Modifier.width(4.dp))
                    Text("Heart Rate", fontSize = 11.sp, color = TextGray, fontFamily = Manrope)
                }
            }
        }
    }
}

// ─── Medications card ─────────────────────────────────────────────────────────

@Composable
private fun MedicationsCard(
    medications: List<Medication>,
    onSeeAllClick: () -> Unit,
) {
    WhiteCard(modifier = Modifier.fillMaxWidth().clickable { onSeeAllClick() }) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Medicamentos",
                        fontFamily = Manrope,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color(0xFF1A1A2E),
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowForward,
                        contentDescription = null,
                        tint = Color(0xFF1A1A2E),
                        modifier = Modifier.size(16.dp),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Ver todo", fontFamily = Manrope, fontSize = 13.sp, color = Color(0xFF1A1A2E))
                    Spacer(Modifier.width(8.dp))
                    ArrowCircle(onClick = onSeeAllClick)
                }
            }

            Spacer(Modifier.height(16.dp))
            DateStrip()
            Spacer(Modifier.height(20.dp))

            if (medications.isEmpty()) {
                Text(
                    text = "Sin medicamentos activos",
                    fontFamily = Manrope,
                    fontSize = 13.sp,
                    color = Color(0xFF8A8A8A),
                )
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    medications.take(3).forEach { med ->
                        Surface(color = Color(0xFFE3F2FD), shape = RoundedCornerShape(12.dp)) {
                            Text(
                                med.nombre,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                fontFamily = Manrope,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ─── Weekly HR Chart ──────────────────────────────────────────────────────────

@Composable
private fun WeeklyHrChart(vitalsHistory: List<VitalsData>) {
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    val last7Dates = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val dayLabels = last7Dates.map {
        it.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.forLanguageTag("es"))
            .replaceFirstChar { c -> c.uppercase() }
    }

    val grouped = vitalsHistory
        .filter { it.heartRate > 0 && it.timestamp > 0 }
        .groupBy {
            java.time.Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate()
        }
    val values = last7Dates.map { day ->
        grouped[day]?.map { it.heartRate }?.average()?.toInt() ?: 0
    }
    val hasData = values.any { it > 0 }
    val yLabels = listOf(100, 110, 120, 130, 140)

    Row(modifier = Modifier.fillMaxWidth().height(120.dp)) {
        Column(
            modifier = Modifier.padding(end = 4.dp).height(100.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            yLabels.reversed().forEach { Text("$it", fontFamily = Manrope, fontSize = 9.sp, color = Color(0xFFB0B0B0)) }
        }
        Canvas(modifier = Modifier.weight(1f).height(100.dp)) {
            val w = size.width; val h = size.height
            val min = 50f; val max = 180f
            fun xOf(i: Int) = i * w / (values.size - 1)
            fun yOf(v: Int) = h - ((v - min) / (max - min) * h).coerceIn(0f, h)

            // grid
            yLabels.forEach { y -> drawLine(Color(0xFFEEEEEE), Offset(0f, yOf(y)), Offset(w, yOf(y)), 1.dp.toPx()) }
            dayLabels.indices.forEach { i -> drawLine(Color(0xFFEEEEEE), Offset(xOf(i), 0f), Offset(xOf(i), h), 0.5.dp.toPx()) }

            if (!hasData) return@Canvas

            val nonZeroIndices = values.indices.filter { values[it] > 0 }
            if (nonZeroIndices.size < 2) return@Canvas

            val path = Path().apply {
                var started = false
                values.forEachIndexed { i, v ->
                    if (v > 0) {
                        if (!started) { moveTo(xOf(i), yOf(v)); started = true }
                        else lineTo(xOf(i), yOf(v))
                    }
                }
            }
            drawPath(Path().apply {
                addPath(path)
                lineTo(xOf(nonZeroIndices.last()), h); lineTo(xOf(nonZeroIndices.first()), h); close()
            }, Brush.verticalGradient(listOf(ChartRed.copy(0.25f), Color.Transparent), 0f, h))
            drawPath(path, ChartRed, style = Stroke(2.5.dp.toPx(), cap = StrokeCap.Round))

            // Dot en el último día con dato real
            val lastIdx = nonZeroIndices.last()
            val dotX = xOf(lastIdx); val dotY = yOf(values[lastIdx])
            drawCircle(ChartRed, 6.dp.toPx(), Offset(dotX, dotY))
            drawCircle(Color.White, 3.dp.toPx(), Offset(dotX, dotY))
            drawLine(ChartRed.copy(0.4f), Offset(dotX, dotY), Offset(dotX, h), 1.dp.toPx())
        }
    }

    // Day labels
    Row(modifier = Modifier.fillMaxWidth().padding(start = 24.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        dayLabels.forEach { Text(it, fontFamily = Manrope, fontSize = 9.sp, color = Color(0xFFB0B0B0)) }
    }
}

// ─── Date strip ───────────────────────────────────────────────────────────────

@Composable
private fun DateStrip() {
    val today = LocalDate.now()
    val days = (-3..3).map { today.plusDays(it.toLong()) }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        days.forEach { day ->
            val isToday = day == today
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (isToday) DashBg.copy(alpha = 0.4f) else Color.Transparent)
                    .padding(horizontal = if (isToday) 8.dp else 4.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (isToday) {
                    Text(
                        text = "Today, ${day.dayOfMonth} ${day.month.getDisplayName(TextStyle.SHORT, Locale.forLanguageTag("es")).replaceFirstChar { it.uppercase() }}",
                        fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF1A1A2E),
                    )
                } else {
                    Text("${day.dayOfMonth}", fontFamily = Manrope, fontSize = 13.sp, color = Color(0xFFB0B0B0))
                }
            }
        }
    }
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

@Composable
private fun PagerDots(count: Int, selected: Int) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        (0 until count).forEach { i ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .size(if (i == selected) 16.dp else 8.dp, 8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (i == selected) DashBlue else Color.White.copy(0.5f)),
            )
        }
    }
}

@Composable
private fun ArrowCircle(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(DashBlue)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.AutoMirrored.Outlined.ArrowForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun WhiteCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(modifier = modifier.clip(RoundedCornerShape(20.dp)).background(DashCard)) { content() }
}

@Composable
private fun LibreQuickCard(
    glucose: Double,
    timestamp: Long,
    source: String,
    profileMode: String,
    contextMode: String,
    onScanClick: () -> Unit,
) {
    val clinical = evaluateLibreClinicalStatus(
        glucose = glucose,
        timestamp = timestamp,
        source = source,
        profileMode = profileMode,
        contextMode = contextMode,
    )

    WhiteCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.MonitorHeart, contentDescription = null, tint = DashBlue)
                    Spacer(Modifier.width(8.dp))
                    Text("Sensor de glucosa", fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF1A1A2E))
                }
                Spacer(Modifier.weight(1f))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DashBlue,
                    modifier = Modifier.clickable { onScanClick() },
                ) {
                    Text(
                        "Escanear",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        color = Color.White,
                        fontFamily = Manrope,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(clinical.statusColor),
                )
                Text(
                    text = clinical.statusText,
                    fontFamily = Manrope,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = clinical.statusColor,
                )
            }

            Text(
                text = "Objetivo ${clinical.contextLabel}: ${clinical.targetRange}",
                fontFamily = Manrope,
                fontSize = 11.sp,
                color = Color(0xFF6B7280),
            )
            Text(
                text = "Perfil clinico: ${clinical.profileLabel}",
                fontFamily = Manrope,
                fontSize = 11.sp,
                color = Color(0xFF6B7280),
            )

            Text(
                text = if (glucose > 0.0) "${"%.0f".format(glucose)} mg/dL" else "Sin lectura reciente",
                fontFamily = Manrope,
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
                color = if (glucose > 0.0) DashBlue else Color(0xFF8A8A8A),
            )
            Text(
                text = if (timestamp > 0L) {
                    "Actualizado: ${SimpleDateFormat("dd/MM HH:mm", Locale.forLanguageTag("es")).format(Date(timestamp))}"
                } else {
                    "Acerca el sensor al telefono para guardar lectura"
                },
                fontFamily = Manrope,
                fontSize = 12.sp,
                color = Color(0xFF8A8A8A),
            )
            if (source.isNotBlank()) {
                Text(
                    text = "Fuente: $source",
                    fontFamily = Manrope,
                    fontSize = 11.sp,
                    color = Color(0xFF6B7280),
                )
            }
        }
    }
}

private data class LibreClinicalStatus(
    val statusText: String,
    val statusColor: Color,
    val targetRange: String,
    val profileLabel: String,
    val contextLabel: String,
)

private fun evaluateLibreClinicalStatus(
    glucose: Double,
    timestamp: Long,
    source: String,
    profileMode: String,
    contextMode: String,
): LibreClinicalStatus {
    if (glucose <= 0.0) {
        return LibreClinicalStatus(
            statusText = "Sin lectura",
            statusColor = Color(0xFF9CA3AF),
            targetRange = "-",
            profileLabel = "General",
            contextLabel = "actual",
        )
    }

    val resolvedProfile = when (profileMode.lowercase(Locale.ROOT)) {
        "diabetes", "diabetico", "diabetica", "dm", "dm2", "dm1" -> "diabetes"
        "general", "normal", "no_diabetes" -> "general"
        else -> if (source.contains("libre", ignoreCase = true)) "diabetes" else "general"
    }

    val resolvedContext = when (contextMode.lowercase(Locale.ROOT)) {
        "ayuno", "fasting", "preprandial" -> "ayuno"
        "post", "postprandial", "despues_comer", "despues de comer" -> "postprandial"
        else -> inferGlucoseContext(timestamp)
    }

    val profileLabel = if (resolvedProfile == "diabetes") "Diabetes" else "General"
    val contextLabel = if (resolvedContext == "postprandial") "postprandial" else "ayuno"

    val (targetMin, targetMax) = when {
        resolvedProfile == "diabetes" && resolvedContext == "postprandial" -> Pair(70.0, 180.0)
        resolvedProfile == "diabetes" -> Pair(80.0, 130.0)
        resolvedContext == "postprandial" -> Pair(70.0, 140.0)
        else -> Pair(70.0, 99.0)
    }

    val isCriticalHigh = glucose >= if (resolvedProfile == "diabetes") 300.0 else 250.0
    val isCriticalLow = glucose < 54.0

    val (statusText, statusColor) = when {
        isCriticalLow -> Pair("Hipoglucemia severa", Color(0xFFB91C1C))
        glucose < 70.0 -> Pair("Bajo", Color(0xFFEF4444))
        glucose in targetMin..targetMax -> Pair("En rango", Color(0xFF10B981))
        isCriticalHigh -> Pair("Muy alto", Color(0xFFB91C1C))
        glucose > targetMax -> Pair("Alto", Color(0xFFF59E0B))
        else -> Pair("Vigilar", Color(0xFFF59E0B))
    }

    return LibreClinicalStatus(
        statusText = statusText,
        statusColor = statusColor,
        targetRange = "${targetMin.toInt()}-${targetMax.toInt()} mg/dL",
        profileLabel = profileLabel,
        contextLabel = contextLabel,
    )
}

private fun inferGlucoseContext(timestamp: Long): String {
    if (timestamp <= 0L) return "ayuno"
    val calendar = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
    val hour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
    return if (hour in 10..21) "postprandial" else "ayuno"
}

@Composable
private fun QuickActionsCard(
    onLibreScanClick: () -> Unit,
    onMedicationClick: () -> Unit,
) {
    WhiteCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Acciones rápidas",
                fontFamily = Manrope,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = Color(0xFF1A1A2E),
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    modifier = Modifier.weight(1f).clickable { onLibreScanClick() },
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFE8F1FF),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.MonitorHeart, null, tint = DashBlue)
                        Spacer(Modifier.width(8.dp))
                        Text("Escanear glucosa", fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = DashBlue)
                    }
                }

                Surface(
                    modifier = Modifier.weight(1f).clickable { onMedicationClick() },
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFFFF4E5),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Medication, null, tint = Color(0xFFB45309))
                        Spacer(Modifier.width(8.dp))
                        Text("Agregar medicamento", fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color(0xFFB45309))
                    }
                }
            }
        }
    }
}

// Keep DashWhiteCard as alias for backward compatibility
@Composable
fun DashWhiteCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) = WhiteCard(modifier, content)

// ─── Settings Dialog ──────────────────────────────────────────────────────────

@Composable
fun SettingsDialog(
    isWatchPaired: Boolean,
    libreProfileMode: String,
    libreContextMode: String,
    onDismiss: () -> Unit,
    onWearableClick: () -> Unit,
    onLibreClick: () -> Unit,
    onLibreModeChanged: (String, String) -> Unit,
    onRestoreBackupClick: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val prefs = remember { context.getSharedPreferences("vitalsense_watch_prefs", android.content.Context.MODE_PRIVATE) }
    var requireBiometric by remember { mutableStateOf(prefs.getBoolean("require_biometric", false)) }
    var selectedProfile by remember { mutableStateOf(libreProfileMode) }
    var selectedContext by remember { mutableStateOf(libreContextMode) }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.health.connect.client.PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        if (granted.containsAll(mx.ita.vitalsense.data.health.HealthConnectRepository.PERMISSIONS)) {
            android.widget.Toast.makeText(context, "Health Connect vinculado exitosamente", android.widget.Toast.LENGTH_SHORT).show()
        } else {
            android.widget.Toast.makeText(context, "Permisos de Health Connect denegados", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Configuración", fontWeight = FontWeight.Bold, color = TextDark)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.fillMaxWidth()) {
                // Wearable
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable {
                    onWearableClick()
                }) {
                    Box(modifier = Modifier.size(40.dp).background(PrimaryBlue.copy(0.1f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Watch, contentDescription = null, tint = PrimaryBlue, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Wearable", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = TextDark)
                        Text(if (isWatchPaired) "Conectado y enviando datos" else "Desconectado", fontSize = 12.sp, color = TextGray)
                    }
                }

                // Health Connect
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable {
                    if (mx.ita.vitalsense.data.health.HealthConnectRepository.isAvailable(context)) {
                        permissionLauncher.launch(mx.ita.vitalsense.data.health.HealthConnectRepository.PERMISSIONS)
                    } else {
                        android.widget.Toast.makeText(context, "Health Connect no disponible en este dispositivo", android.widget.Toast.LENGTH_LONG).show()
                    }
                }) {
                    Box(modifier = Modifier.size(40.dp).background(SuccessGreen.copy(0.1f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Favorite, contentDescription = null, tint = SuccessGreen, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Sincronizar Sueño", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = TextDark)
                        Text("Vincular con Health Connect", fontSize = 12.sp, color = TextGray)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable {
                    onRestoreBackupClick()
                }) {
                    Box(modifier = Modifier.size(40.dp).background(Color(0xFFEEF2FF), CircleShape), contentAlignment = Alignment.Center) {
                        Text("R", color = PrimaryBlue, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Restaurar respaldo", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = TextDark)
                        Text("Forzar sincronización desde Firebase", fontSize = 12.sp, color = TextGray)
                    }
                }

                // Biometrics
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(40.dp).background(Color(0xFFE0E0E0), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Fingerprint, contentDescription = null, tint = TextDark, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Seguridad Biométrica", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = TextDark)
                        Text("Huella o rostro al iniciar app", fontSize = 12.sp, color = TextGray)
                    }
                    Switch(
                        checked = requireBiometric,
                        onCheckedChange = {
                            requireBiometric = it
                            prefs.edit().putBoolean("require_biometric", it).apply()
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = PrimaryBlue, checkedTrackColor = PrimaryBlue.copy(alpha = 0.5f))
                    )
                }

                // Libre NFC
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable {
                    onLibreClick()
                }) {
                    Box(modifier = Modifier.size(40.dp).background(Color(0xFFFFF3E0), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.MonitorHeart, contentDescription = null, tint = Color(0xFFF57C00), modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Escanear glucosa", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = TextDark)
                        Text("Lectura NFC sin app del fabricante", fontSize = 12.sp, color = TextGray)
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Objetivo clínico de glucosa", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = TextDark)
                    Text(
                        "Define como interpretar lecturas: perfil del paciente y momento de medicion.",
                        fontSize = 11.sp,
                        color = TextGray,
                    )

                    Text("Perfil", fontSize = 12.sp, color = TextGray)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    ) {
                        LibreProfileMode.entries.forEach { mode ->
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = if (selectedProfile == mode.key) PrimaryBlue else Color(0xFFEAF2FF),
                                modifier = Modifier.clickable {
                                    selectedProfile = mode.key
                                    onLibreModeChanged(selectedProfile, selectedContext)
                                },
                            ) {
                                Text(
                                    text = mode.label,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    color = if (selectedProfile == mode.key) Color.White else PrimaryBlue,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }

                    Text("Contexto de lectura", fontSize = 12.sp, color = TextGray)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    ) {
                        LibreContextMode.entries.forEach { mode ->
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = if (selectedContext == mode.key) PrimaryBlue else Color(0xFFEAF2FF),
                                modifier = Modifier.clickable {
                                    selectedContext = mode.key
                                    onLibreModeChanged(selectedProfile, selectedContext)
                                },
                            ) {
                                Text(
                                    text = mode.label,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    color = if (selectedContext == mode.key) Color.White else PrimaryBlue,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Listo", color = PrimaryBlue, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = Color.White
    )
}
