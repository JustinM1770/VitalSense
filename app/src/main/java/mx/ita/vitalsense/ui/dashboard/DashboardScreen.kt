package mx.ita.vitalsense.ui.dashboard

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Message
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import mx.ita.vitalsense.R
import mx.ita.vitalsense.data.model.Medication
import mx.ita.vitalsense.data.model.SleepData
import mx.ita.vitalsense.data.model.VitalsData
import mx.ita.vitalsense.settings.AppSettings
import mx.ita.vitalsense.ui.common.LockLandscapeOrientation
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

private enum class LanguageOption(val key: String, val labelRes: Int) {
    ES("es", R.string.language_es),
    EN("en", R.string.language_en),
    PT("pt", R.string.language_pt),
}

private enum class ThemeOption(val key: String, val labelRes: Int) {
    SYSTEM("system", R.string.theme_system),
    LIGHT("light", R.string.theme_light),
    DARK("dark", R.string.theme_dark),
}

// ─── Figma / Image design tokens ──────────────────────────────────────────────
private val BlueCardBg     = Color(0xFF90C2F9)
private val PrimaryBlue    = Color(0xFF1169FF)
private val TextDark       = Color(0xFF221F1F)
private val TextGray       = Color(0xFF7F8C8D)
private val SuccessGreen   = Color(0xFF10B981)
private val HeartRateCurve = Color(0xFFEF4444)

private enum class MetricCardType { SLEEP, SPO2, HR, KCAL }

private fun loadVisibleMetricCards(prefs: SharedPreferences): List<MetricCardType> {
    val stored = prefs.getString("dashboard_visible_cards", null).orEmpty()
    val parsed = stored.split(',')
        .mapNotNull { name -> runCatching { MetricCardType.valueOf(name) }.getOrNull() }
        .distinct()
    return if (parsed.isNotEmpty()) parsed else listOf(MetricCardType.SLEEP, MetricCardType.SPO2)
}

private fun saveVisibleMetricCards(prefs: SharedPreferences, cards: List<MetricCardType>) {
    prefs.edit().putString("dashboard_visible_cards", cards.joinToString(",") { it.name }).apply()
}

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
    onContactClick: () -> Unit = {},
    onLegalHelpClick: () -> Unit = {},
    onRatingClick: () -> Unit = {},
    onSummaryClick: () -> Unit = {},
    onEmergency: (VitalsData) -> Unit = {},
    vm: DashboardViewModel = viewModel(),
) {
    LockLandscapeOrientation()
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
    val dashboardPrefs = remember(uid) { context.getSharedPreferences("vitalsense_dashboard_${uid.ifBlank { "guest" }}", Context.MODE_PRIVATE) }
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
            val snapshot = withContext(Dispatchers.IO) {
                com.google.firebase.database.FirebaseDatabase.getInstance()
                    .getReference("patients/$uid/profile")
                    .get()
                    .await()
            }
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

    val isDarkTheme = isSystemInDarkTheme()
    Box(modifier = Modifier.fillMaxSize().background(if (isDarkTheme) MaterialTheme.colorScheme.background else DashBg)) {
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
                    userId = uid,
                    dashboardPrefs = dashboardPrefs,
                    userName = userName,
                    userAvatarUri = userAvatarUri,
                    patients = patients,
                    sleepData = sleepData,
                    vitalsHistory = vitalsHistory,
                    medications = medications,
                    onPatientClick = onPatientClick,
                    onReportClick = onReportClick,
                    onProfileClick = { onNavigateToProfile(); onProfileClick() },
                    onNotifClick = { onNotifClick() },
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
                        coroutineScope.launch { snackbarHostState.showSnackbar(context.getString(R.string.dashboard_search_in_development)) }
                    },
                    onSettingsClick = { showSettingsDialog = true },
                    onSummaryClick = onSummaryClick,
                )
            }
        }

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
                        coroutineScope.launch { snackbarHostState.showSnackbar(context.getString(R.string.dashboard_login_restore_backup)) }
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
                },
                onContactClick = {
                    showSettingsDialog = false
                    onContactClick()
                },
                onLegalHelpClick = {
                    showSettingsDialog = false
                    onLegalHelpClick()
                },
                onRatingClick = {
                    showSettingsDialog = false
                    onRatingClick()
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
    return withContext(Dispatchers.IO) {
        try {
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
}

@Composable
private fun DashboardContent(
    userId: String,
    dashboardPrefs: SharedPreferences,
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
    onSummaryClick: () -> Unit,
) {
    val initialCards = remember(userId) { loadVisibleMetricCards(dashboardPrefs) }
    val visibleCards = remember(userId) { mutableStateListOf<MetricCardType>().apply { addAll(initialCards) } }
    val dashboardScope = rememberCoroutineScope()

    LaunchedEffect(visibleCards.size) {
        saveVisibleMetricCards(dashboardPrefs, visibleCards.toList())
    }

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
                .background(if (isSystemInDarkTheme()) MaterialTheme.colorScheme.surfaceVariant else BlueCardBg)
                .padding(24.dp)
        ) {
            // ── "Esta semana" — sleep / HR / Kcal pager ───────────────────────
            val addableCards = listOf(MetricCardType.SLEEP, MetricCardType.SPO2, MetricCardType.HR, MetricCardType.KCAL)
                .filter { it !in visibleCards }

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SectionHeader(
                    title = stringResource(R.string.dashboard_this_week),
                    showArrow = true,
                    onClick = onSummaryClick,
                )
                Spacer(Modifier.weight(1f))
                if (addableCards.isNotEmpty()) {
                    Surface(
                        modifier = Modifier.size(30.dp).clickable {
                            val next = addableCards.firstOrNull()
                            if (next != null) {
                                visibleCards.add(next)
                                saveVisibleMetricCards(dashboardPrefs, visibleCards.toList())
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
            }
            Spacer(Modifier.height(16.dp))

            val pagerState = rememberPagerState(pageCount = { visibleCards.size })

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxWidth(),
            ) { page ->
                val cardType = visibleCards.getOrElse(page) { MetricCardType.SLEEP }
                Box(modifier = Modifier.fillMaxWidth()) {
                    when (cardType) {
                        MetricCardType.SLEEP -> SleepMetricCard(sleepData = sleepData, onClick = onReportClick)
                        MetricCardType.SPO2 -> Spo2MiniCard(patients)
                        MetricCardType.HR -> HrMiniCard(patients)
                        MetricCardType.KCAL -> KcalMiniCard(patients)
                    }
                    if (visibleCards.size > 1) {
                        IconButton(
                            onClick = {
                                if (visibleCards.size > 1) {
                                    visibleCards.remove(cardType)
                                    saveVisibleMetricCards(dashboardPrefs, visibleCards.toList())
                                    dashboardScope.launch {
                                        val safeIndex = pagerState.currentPage.coerceAtMost(maxOf(0, visibleCards.lastIndex))
                                        pagerState.scrollToPage(safeIndex)
                                    }
                                }
                            },
                            modifier = Modifier.align(Alignment.TopEnd),
                        ) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.dashboard_remove_card), tint = DashBlue)
                        }
                    }
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
    val colorScheme = MaterialTheme.colorScheme
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
                    contentDescription = stringResource(R.string.dashboard_avatar),
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
            Text(stringResource(R.string.dashboard_welcome), color = colorScheme.onSurfaceVariant, fontSize = 14.sp, fontFamily = Manrope)
            Text(name, color = colorScheme.onSurface, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontFamily = Manrope)
        }

        // Campana de notificaciones con punto rojo
        Box(modifier = Modifier.clickable { onNotificationClick() }) {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = RoundedCornerShape(16.dp),
                color = colorScheme.surface,
                shadowElevation = 2.dp
            ) {
                Icon(
                    Icons.Filled.Notifications,
                    contentDescription = stringResource(R.string.dashboard_notifications),
                    modifier = Modifier.padding(12.dp),
                    tint = colorScheme.onSurface
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
    val colorScheme = MaterialTheme.colorScheme
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
                color = colorScheme.surface,
                tonalElevation = 2.dp,
                shadowElevation = 0.dp,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.dashboard_search_patient), color = colorScheme.onSurfaceVariant, fontFamily = Manrope)
            }
        }
        Spacer(Modifier.width(16.dp))
        Surface(
            modifier = Modifier.size(56.dp).clickable { onSettingsClick() },
            shape = RoundedCornerShape(16.dp),
            color = colorScheme.primary,
        ) {
            Icon(
                Icons.Filled.Tune,
                contentDescription = stringResource(R.string.settings_title),
                tint = colorScheme.onPrimary,
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
                Text(stringResource(R.string.dashboard_pair_watch_sensor), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(stringResource(R.string.dashboard_sync_vitals), color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
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
                    stringResource(R.string.dashboard_device_linked, deviceName),
                    color = SuccessGreen,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Spacer(Modifier.weight(1f))
                Text(stringResource(R.string.dashboard_synced), color = SuccessGreen, fontSize = 11.sp)
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    stringResource(R.string.dashboard_watch_realtime),
                    fontSize = 12.sp,
                    color = TextGray,
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = onDisconnect,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                ) {
                    Text(stringResource(R.string.dashboard_unpair), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ─── Section header ───────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String, showArrow: Boolean = false, onClick: (() -> Unit)? = null) {
    val colorScheme = MaterialTheme.colorScheme
    val rowModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Row(modifier = rowModifier, verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = Manrope)
        if (showArrow) {
            Spacer(Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, modifier = Modifier.size(16.dp), tint = colorScheme.onSurface)
        }
    }
}

@Composable
private fun SummaryPreviewCard(onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = DashBlue.copy(alpha = 0.14f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.MonitorHeart,
                contentDescription = null,
                tint = DashBlue,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(R.string.dashboard_open_summary),
                fontFamily = Manrope,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                contentDescription = null,
                tint = DashBlue,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

// ─── Sleep card ───────────────────────────────────────────────────────────────

@Composable
private fun SleepMetricCard(sleepData: SleepData?, onClick: () -> Unit) {
    val hasSleepData = sleepData?.hasSleep == true
    val score = if (hasSleepData) sleepData?.score?.coerceIn(0, 100) ?: 0 else 0
    val progress = score / 100f
    val scoreText = score.toString()
    val sleepStatus = if (hasSleepData) {
        val duration = sleepData?.durationLabel().orEmpty()
        val state = sleepData?.estado?.takeIf { it.isNotBlank() } ?: "Regular"
        "$duration · $state"
    } else {
        stringResource(R.string.sleep_not_slept)
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
                Text("$scoreText%", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontFamily = Manrope)
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.sleep_title), color = SleepGreen, fontWeight = FontWeight.Bold, fontSize = 18.sp, fontFamily = Manrope)
                val today = LocalDate.now()
                Text(
                    text = "${today.dayOfMonth} ${today.month.getDisplayName(TextStyle.FULL, Locale.getDefault()).replaceFirstChar { it.uppercase() }} ${today.year}",
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
                Text(stringResource(R.string.dashboard_heart_rate_label), fontFamily = Manrope, fontSize = 13.sp, color = Color(0xFF8A8A8A))
                Text(if (hr != null) "$hr BPM" else "-- BPM", fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun KcalMiniCard(patients: List<VitalsData>) {
    WhiteCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("--", fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 36.sp, color = MaterialTheme.colorScheme.onSurface)
            Text(stringResource(R.string.dashboard_kcal_today), fontFamily = Manrope, fontSize = 14.sp, color = Color(0xFF8A8A8A))
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
                Text("$spo2%", fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 24.sp, color = MaterialTheme.colorScheme.onSurface)
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
    val recentHrSamples = vitalsHistory
        .asReversed()
        .map { it.heartRate }
        .filter { it > 0 }
        .take(5)
    val displayBpm = when {
        recentHrSamples.isNotEmpty() -> recentHrSamples.average().toInt()
        else -> patients.firstOrNull()?.heartRate ?: 0
    }

    WhiteCard(modifier = Modifier.fillMaxWidth().clickable { onSeeAllClick() }) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.dashboard_health_metrics),
                    fontFamily = Manrope,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.dashboard_view_all),
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
                    Text("$displayBpm BPM", fontWeight = FontWeight.Bold, fontSize = 13.sp, fontFamily = Manrope, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.dashboard_heart_rate_label), fontSize = 11.sp, color = TextGray, fontFamily = Manrope)
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
                        text = stringResource(R.string.dashboard_medications),
                        fontFamily = Manrope,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowForward,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.dashboard_view_all), fontFamily = Manrope, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.width(8.dp))
                    ArrowCircle(onClick = onSeeAllClick)
                }
            }

            Spacer(Modifier.height(16.dp))
            DateStrip()
            Spacer(Modifier.height(20.dp))

            if (medications.isEmpty()) {
                Text(
                    text = stringResource(R.string.dashboard_no_active_medications),
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
    val daySet = last7Dates.toSet()
    val dayLabels = last7Dates.map {
        it.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            .replaceFirstChar { c -> c.uppercase() }
    }

    val grouped = vitalsHistory
        .filter { it.heartRate > 0 && it.timestamp > 0 }
        .groupBy {
            java.time.Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate()
        }
        .filterKeys { it in daySet }
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
            if (nonZeroIndices.size == 1) {
                val idx = nonZeroIndices.first()
                val dotX = xOf(idx)
                val dotY = yOf(values[idx])
                drawCircle(ChartRed, 6.dp.toPx(), Offset(dotX, dotY))
                drawCircle(Color.White, 3.dp.toPx(), Offset(dotX, dotY))
                drawLine(ChartRed.copy(0.4f), Offset(dotX, dotY), Offset(dotX, h), 1.dp.toPx())
                return@Canvas
            }
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
                    .background(
                        if (isToday) {
                            if (isSystemInDarkTheme()) MaterialTheme.colorScheme.surfaceVariant else DashBg.copy(alpha = 0.4f)
                        } else {
                            Color.Transparent
                        }
                    )
                    .padding(horizontal = if (isToday) 8.dp else 4.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (isToday) {
                    Text(
                        text = stringResource(
                            R.string.dashboard_today_date,
                            day.dayOfMonth,
                            day.month.getDisplayName(TextStyle.SHORT, Locale.getDefault()).replaceFirstChar { it.uppercase() }
                        ),
                        fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface,
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
    val surfaceColor = if (isSystemInDarkTheme()) MaterialTheme.colorScheme.surface else DashCard
    Box(modifier = modifier.clip(RoundedCornerShape(20.dp)).background(surfaceColor)) { content() }
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
    val libreLabels = LibreClinicalLabels(
        noReading = stringResource(R.string.dashboard_glucose_status_no_reading),
        generalProfile = stringResource(R.string.dashboard_glucose_profile_general),
        diabetesProfile = stringResource(R.string.dashboard_glucose_profile_diabetes),
        currentContext = stringResource(R.string.dashboard_glucose_context_current),
        fastingContext = stringResource(R.string.dashboard_glucose_context_fasting),
        postprandialContext = stringResource(R.string.dashboard_glucose_context_postprandial),
        severeHypo = stringResource(R.string.dashboard_glucose_status_severe_hypo),
        low = stringResource(R.string.dashboard_glucose_status_low),
        inRange = stringResource(R.string.dashboard_glucose_status_in_range),
        high = stringResource(R.string.dashboard_glucose_status_high),
        veryHigh = stringResource(R.string.dashboard_glucose_status_very_high),
        watch = stringResource(R.string.dashboard_glucose_status_watch),
    )
    val clinical = evaluateLibreClinicalStatus(
        glucose = glucose,
        timestamp = timestamp,
        source = source,
        profileMode = profileMode,
        contextMode = contextMode,
        labels = libreLabels,
    )

    WhiteCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.MonitorHeart, contentDescription = null, tint = DashBlue)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.dashboard_glucose_sensor), fontFamily = Manrope, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                }
                Spacer(Modifier.weight(1f))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = DashBlue,
                    modifier = Modifier.clickable { onScanClick() },
                ) {
                    Text(
                        stringResource(R.string.dashboard_scan),
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
                text = stringResource(R.string.dashboard_glucose_target, clinical.contextLabel, clinical.targetRange),
                fontFamily = Manrope,
                fontSize = 11.sp,
                color = Color(0xFF6B7280),
            )
            Text(
                text = stringResource(R.string.dashboard_clinical_profile, clinical.profileLabel),
                fontFamily = Manrope,
                fontSize = 11.sp,
                color = Color(0xFF6B7280),
            )

            Text(
                text = if (glucose > 0.0) "${"%.0f".format(glucose)} mg/dL" else stringResource(R.string.dashboard_no_recent_reading),
                fontFamily = Manrope,
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
                color = if (glucose > 0.0) DashBlue else Color(0xFF8A8A8A),
            )
            Text(
                text = if (timestamp > 0L) {
                    stringResource(
                        R.string.dashboard_updated_at,
                        SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(timestamp))
                    )
                } else {
                    stringResource(R.string.dashboard_sensor_prompt)
                },
                fontFamily = Manrope,
                fontSize = 12.sp,
                color = Color(0xFF8A8A8A),
            )
            if (source.isNotBlank()) {
                Text(
                    text = stringResource(R.string.dashboard_source, source),
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

private data class LibreClinicalLabels(
    val noReading: String,
    val generalProfile: String,
    val diabetesProfile: String,
    val currentContext: String,
    val fastingContext: String,
    val postprandialContext: String,
    val severeHypo: String,
    val low: String,
    val inRange: String,
    val high: String,
    val veryHigh: String,
    val watch: String,
)

private fun evaluateLibreClinicalStatus(
    glucose: Double,
    timestamp: Long,
    source: String,
    profileMode: String,
    contextMode: String,
    labels: LibreClinicalLabels,
): LibreClinicalStatus {
    if (glucose <= 0.0) {
        return LibreClinicalStatus(
            statusText = labels.noReading,
            statusColor = Color(0xFF9CA3AF),
            targetRange = "-",
            profileLabel = labels.generalProfile,
            contextLabel = labels.currentContext,
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

    val profileLabel = if (resolvedProfile == "diabetes") labels.diabetesProfile else labels.generalProfile
    val contextLabel = if (resolvedContext == "postprandial") labels.postprandialContext else labels.fastingContext

    val (targetMin, targetMax) = when {
        resolvedProfile == "diabetes" && resolvedContext == "postprandial" -> Pair(70.0, 180.0)
        resolvedProfile == "diabetes" -> Pair(80.0, 130.0)
        resolvedContext == "postprandial" -> Pair(70.0, 140.0)
        else -> Pair(70.0, 99.0)
    }

    val isCriticalHigh = glucose >= if (resolvedProfile == "diabetes") 300.0 else 250.0
    val isCriticalLow = glucose < 54.0

    val (statusText, statusColor) = when {
        isCriticalLow -> Pair(labels.severeHypo, Color(0xFFB91C1C))
        glucose < 70.0 -> Pair(labels.low, Color(0xFFEF4444))
        glucose in targetMin..targetMax -> Pair(labels.inRange, Color(0xFF10B981))
        isCriticalHigh -> Pair(labels.veryHigh, Color(0xFFB91C1C))
        glucose > targetMax -> Pair(labels.high, Color(0xFFF59E0B))
        else -> Pair(labels.watch, Color(0xFFF59E0B))
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
                stringResource(R.string.dashboard_quick_actions),
                fontFamily = Manrope,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface,
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
                        Text(stringResource(R.string.dashboard_scan_glucose), fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = DashBlue)
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
                        Text(stringResource(R.string.dashboard_view_medications), fontFamily = Manrope, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color(0xFFB45309))
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

@OptIn(ExperimentalMaterial3Api::class)
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
    onContactClick: () -> Unit,
    onLegalHelpClick: () -> Unit,
    onRatingClick: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val colorScheme = MaterialTheme.colorScheme
    val prefs = remember { context.getSharedPreferences("vitalsense_watch_prefs", android.content.Context.MODE_PRIVATE) }
    var requireBiometric by remember { mutableStateOf(prefs.getBoolean("require_biometric", false)) }
    var selectedProfile by remember { mutableStateOf(libreProfileMode) }
    var selectedContext by remember { mutableStateOf(libreContextMode) }
    var selectedLanguage by remember { mutableStateOf(AppSettings.getSavedLanguage(context)) }
    var selectedTheme by remember { mutableStateOf(AppSettings.getSavedTheme(context)) }
    var languageExpanded by remember { mutableStateOf(false) }
    var themeExpanded by remember { mutableStateOf(false) }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.health.connect.client.PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        if (granted.containsAll(mx.ita.vitalsense.data.health.HealthConnectRepository.PERMISSIONS)) {
            android.widget.Toast.makeText(context, context.getString(R.string.dashboard_health_connect_linked), android.widget.Toast.LENGTH_SHORT).show()
        } else {
            android.widget.Toast.makeText(context, context.getString(R.string.dashboard_health_connect_denied), android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colorScheme.surface,
        title = {
            Text(stringResource(R.string.settings_title), fontWeight = FontWeight.Bold, color = colorScheme.onSurface)
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Wearable
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable {
                    onWearableClick()
                }) {
                    Box(modifier = Modifier.size(40.dp).background(colorScheme.primary.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Watch, contentDescription = null, tint = colorScheme.primary, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.dashboard_wearable), fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = colorScheme.onSurface)
                        Text(
                            if (isWatchPaired) stringResource(R.string.dashboard_connected_sending) else stringResource(R.string.dashboard_disconnected),
                            fontSize = 12.sp,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Health Connect
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable {
                    if (mx.ita.vitalsense.data.health.HealthConnectRepository.isAvailable(context)) {
                        permissionLauncher.launch(mx.ita.vitalsense.data.health.HealthConnectRepository.PERMISSIONS)
                    } else {
                        android.widget.Toast.makeText(context, context.getString(R.string.dashboard_health_connect_unavailable), android.widget.Toast.LENGTH_LONG).show()
                    }
                }) {
                    Box(modifier = Modifier.size(40.dp).background(colorScheme.secondary.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Favorite, contentDescription = null, tint = colorScheme.secondary, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.dashboard_sync_sleep), fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = colorScheme.onSurface)
                        Text(stringResource(R.string.dashboard_link_health_connect), fontSize = 12.sp, color = colorScheme.onSurfaceVariant)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable {
                    onRestoreBackupClick()
                }) {
                    Box(modifier = Modifier.size(40.dp).background(colorScheme.primary.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                        Text("R", color = colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.dashboard_restore_backup), fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = colorScheme.onSurface)
                        Text(stringResource(R.string.dashboard_force_sync_firebase), fontSize = 12.sp, color = colorScheme.onSurfaceVariant)
                    }
                }

                // Biometrics
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(40.dp).background(colorScheme.surfaceVariant, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Fingerprint, contentDescription = null, tint = colorScheme.onSurface, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.dashboard_biometric_security), fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = colorScheme.onSurface)
                        Text(stringResource(R.string.dashboard_fingerprint_face), fontSize = 12.sp, color = colorScheme.onSurfaceVariant)
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

                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(stringResource(R.string.settings_language), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = colorScheme.onSurface)
                    ExposedDropdownMenuBox(
                        expanded = languageExpanded,
                        onExpandedChange = { languageExpanded = !languageExpanded },
                    ) {
                        OutlinedTextField(
                            value = LanguageOption.entries.firstOrNull { it.key == selectedLanguage }?.let { stringResource(it.labelRes) }
                                ?: stringResource(R.string.language_es),
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = languageExpanded) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = colorScheme.onSurface,
                                unfocusedTextColor = colorScheme.onSurface,
                                focusedBorderColor = colorScheme.primary,
                                unfocusedBorderColor = colorScheme.outline,
                                focusedContainerColor = colorScheme.surface,
                                unfocusedContainerColor = colorScheme.surface,
                                focusedTrailingIconColor = colorScheme.onSurfaceVariant,
                                unfocusedTrailingIconColor = colorScheme.onSurfaceVariant,
                            ),
                        )
                        DropdownMenu(
                            expanded = languageExpanded,
                            onDismissRequest = { languageExpanded = false },
                        ) {
                            LanguageOption.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(option.labelRes)) },
                                    onClick = {
                                        selectedLanguage = option.key
                                        languageExpanded = false
                                        AppSettings.setLanguage(context, option.key)
                                    },
                                )
                            }
                        }
                    }

                    Text(stringResource(R.string.settings_theme), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = colorScheme.onSurface)
                    ExposedDropdownMenuBox(
                        expanded = themeExpanded,
                        onExpandedChange = { themeExpanded = !themeExpanded },
                    ) {
                        OutlinedTextField(
                            value = ThemeOption.entries.firstOrNull { it.key == selectedTheme }?.let { stringResource(it.labelRes) }
                                ?: stringResource(R.string.theme_system),
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = themeExpanded) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = colorScheme.onSurface,
                                unfocusedTextColor = colorScheme.onSurface,
                                focusedBorderColor = colorScheme.primary,
                                unfocusedBorderColor = colorScheme.outline,
                                focusedContainerColor = colorScheme.surface,
                                unfocusedContainerColor = colorScheme.surface,
                                focusedTrailingIconColor = colorScheme.onSurfaceVariant,
                                unfocusedTrailingIconColor = colorScheme.onSurfaceVariant,
                            ),
                        )
                        DropdownMenu(
                            expanded = themeExpanded,
                            onDismissRequest = { themeExpanded = false },
                        ) {
                            ThemeOption.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(option.labelRes)) },
                                    onClick = {
                                        selectedTheme = option.key
                                        themeExpanded = false
                                        AppSettings.setTheme(context, option.key)
                                    },
                                )
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onContactClick() }) {
                    Box(modifier = Modifier.size(40.dp).background(colorScheme.primary.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Message, contentDescription = null, tint = colorScheme.primary, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(16.dp))
                    Text(stringResource(R.string.settings_contact), fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = colorScheme.onSurface)
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onLegalHelpClick() }) {
                    Box(modifier = Modifier.size(40.dp).background(colorScheme.primary.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Favorite, contentDescription = null, tint = colorScheme.primary, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(16.dp))
                    Text(stringResource(R.string.settings_legal_help), fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = colorScheme.onSurface)
                }

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onRatingClick() }) {
                    Box(modifier = Modifier.size(40.dp).background(colorScheme.tertiary.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Favorite, contentDescription = null, tint = colorScheme.tertiary, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(16.dp))
                    Text(stringResource(R.string.settings_rate_app), fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = colorScheme.onSurface)
                }

                // Libre NFC
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable {
                    onLibreClick()
                }) {
                    Box(modifier = Modifier.size(40.dp).background(colorScheme.secondary.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.MonitorHeart, contentDescription = null, tint = colorScheme.secondary, modifier = Modifier.size(24.dp))
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.dashboard_scan_glucose), fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = colorScheme.onSurface)
                        Text(stringResource(R.string.dashboard_nfc_reading_no_vendor_app), fontSize = 12.sp, color = colorScheme.onSurfaceVariant)
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.dashboard_glucose_clinical_target), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = colorScheme.onSurface)
                    Text(
                        stringResource(R.string.dashboard_glucose_target_help),
                        fontSize = 11.sp,
                        color = colorScheme.onSurfaceVariant,
                    )

                    Text(stringResource(R.string.dashboard_profile_label), fontSize = 12.sp, color = colorScheme.onSurfaceVariant)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    ) {
                        LibreProfileMode.entries.forEach { mode ->
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = if (selectedProfile == mode.key) colorScheme.primary else colorScheme.surfaceVariant,
                                modifier = Modifier.clickable {
                                    selectedProfile = mode.key
                                    onLibreModeChanged(selectedProfile, selectedContext)
                                },
                            ) {
                                Text(
                                    text = mode.label,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    color = if (selectedProfile == mode.key) colorScheme.onPrimary else colorScheme.onSurface,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }

                    Text(stringResource(R.string.dashboard_reading_context), fontSize = 12.sp, color = colorScheme.onSurfaceVariant)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    ) {
                        LibreContextMode.entries.forEach { mode ->
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = if (selectedContext == mode.key) colorScheme.primary else colorScheme.surfaceVariant,
                                modifier = Modifier.clickable {
                                    selectedContext = mode.key
                                    onLibreModeChanged(selectedProfile, selectedContext)
                                },
                            ) {
                                Text(
                                    text = mode.label,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                    color = if (selectedContext == mode.key) colorScheme.onPrimary else colorScheme.onSurface,
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
                Text(stringResource(R.string.dashboard_done), color = colorScheme.primary, fontWeight = FontWeight.Bold)
            }
        },
    )
}
