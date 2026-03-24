package mx.ita.vitalsense.ui.navigation

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.navigation.compose.NavHost
import mx.ita.vitalsense.ui.profile.ProfileScreen
import mx.ita.vitalsense.ui.forgotpassword.ForgotPasswordScreen
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.firebase.auth.FirebaseAuth
import mx.ita.vitalsense.ui.archivos.DatosImportantesScreen
import mx.ita.vitalsense.ui.cuestionario.CuestionarioScreen
import mx.ita.vitalsense.ui.dashboard.DashboardScreen
import mx.ita.vitalsense.ui.device.DeviceScanScreen
import android.content.Context
import androidx.compose.ui.platform.LocalContext
import mx.ita.vitalsense.ui.documentos.DocumentosScreen
import mx.ita.vitalsense.ui.forgotpassword.ForgotPasswordScreen
import mx.ita.vitalsense.ui.login.LoginScreen
import mx.ita.vitalsense.ui.notifications.NotificacionesScreen
import mx.ita.vitalsense.ui.onboarding.OnboardingScreen
import mx.ita.vitalsense.ui.patient.PatientDetailScreen
import mx.ita.vitalsense.ui.profile.ProfileScreen
import mx.ita.vitalsense.ui.register.RegisterScreen
import mx.ita.vitalsense.ui.reports.DailyReportScreen
import mx.ita.vitalsense.ui.splash.SplashScreen
import mx.ita.vitalsense.ui.chat.ChatBotScreen
import mx.ita.vitalsense.ui.components.GlobalBottomNavigationBar
import mx.ita.vitalsense.ui.emergency.EmergencyQrScreen
import mx.ita.vitalsense.ui.emergency.EmergencyQrViewModel
import mx.ita.vitalsense.ui.emergency.EmergencyViewerScreen
import mx.ita.vitalsense.ui.libre.LibreScanScreen
import mx.ita.vitalsense.ui.medications.AddMedicationScreen
import mx.ita.vitalsense.MainActivity
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

object Route {
    const val SPLASH                = "splash"
    const val ONBOARDING            = "onboarding"
    const val REGISTER              = "register"
    const val LOGIN                 = "login"
    const val FORGOT_PASSWORD       = "forgot_password"
    const val CUESTIONARIO          = "cuestionario"
    const val DASHBOARD             = "dashboard"
    const val DEVICE                = "device"
    const val PROFILE               = "profile"
    const val EDITAR_PERFIL         = "editar_perfil"
    const val PATIENT_DETAIL        = "patient_detail"
    const val REPORTE_DIARIO        = "reporte_diario"
    const val DAILY_REPORT          = "daily_report"
    const val REPORTE_DETALLADO     = "reporte_detallado"
    const val DETAILED_REPORT       = "detailed_report"
    const val SLEEP_DETAIL          = "sleep_detail"
    const val NOTIFICACIONES        = "notificaciones"
    const val NOTIFICATIONS         = "notifications"
    const val DATOS_IMPORTANTES     = "datos_importantes"
    const val DOCUMENTOS            = "documentos"
    const val CHAT                  = "chat"
    const val ADD_MEDICATION        = "add_medication"
    const val LIBRE_SCAN            = "libre_scan"
    // ── Emergencia ──────────────────────────────────────────────────────────
    const val EMERGENCY_QR          = "emergency_qr"
    const val EMERGENCY_VIEWER      = "emergency_viewer"   // + /{tokenId}
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // ViewModel de emergencia con scope del NavHost (sobrevive cambios de pantalla)
    val emergencyVm: EmergencyQrViewModel = viewModel()

    // Manejo del deep link vitalsense://emergency/{tokenId} cuando la app se abre desde el QR
    val context = LocalContext.current
    val mainActivity = context as? MainActivity
    LaunchedEffect(Unit) {
        val activity = context as? android.app.Activity ?: return@LaunchedEffect
        val uri = activity.intent?.data ?: return@LaunchedEffect
        if (uri.scheme == "vitalsense" && uri.host == "emergency") {
            val tokenId = uri.lastPathSegment ?: return@LaunchedEffect
            navController.navigate("${Route.EMERGENCY_VIEWER}/$tokenId") {
                launchSingleTop = true
            }
        }
    }

    val openNotificationRequest = mainActivity?.pendingNotificationOpen
    LaunchedEffect(openNotificationRequest) {
        val request = openNotificationRequest ?: return@LaunchedEffect
        val alertId = Uri.encode(request.alertId)
        val target = "${Route.NOTIFICACIONES}?alertId=$alertId&lat=${request.lat}&lng=${request.lng}"
        navController.navigate(target) {
            launchSingleTop = true
        }
        mainActivity.consumePendingNotificationOpen()
    }

    val bottomBarRoutes = listOf(
        Route.DASHBOARD,
        Route.DAILY_REPORT,
        Route.REPORTE_DIARIO,
        Route.DETAILED_REPORT,
        Route.REPORTE_DETALLADO,
        Route.NOTIFICACIONES,
        "${Route.NOTIFICACIONES}?alertId={alertId}&lat={lat}&lng={lng}",
        Route.NOTIFICATIONS,
        Route.PROFILE,
        Route.EDITAR_PERFIL,
        Route.CHAT,
    )
    val showBottomBar = currentRoute in bottomBarRoutes

    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier.systemBarsPadding()
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = Route.SPLASH,
                modifier = Modifier.padding(bottom = if (showBottomBar) 0.dp else innerPadding.calculateBottomPadding())
            ) {
                composable(Route.SPLASH) {
                    SplashScreen(
                        onNavigateToOnboarding = {
                            navController.navigate(Route.ONBOARDING) {
                                popUpTo(Route.SPLASH) { inclusive = true }
                            }
                        },
                        onNavigateToDashboard = {
                            navController.navigate(Route.DASHBOARD) {
                                popUpTo(Route.SPLASH) { inclusive = true }
                            }
                        },
                        onNavigateToCuestionario = {
                            navController.navigate(Route.CUESTIONARIO) {
                                popUpTo(Route.SPLASH) { inclusive = true }
                            }
                        }
                    )
                }

                composable(Route.ONBOARDING) {
                    OnboardingScreen(
                        onSkip = { navController.navigate(Route.LOGIN) },
                        onGetStarted = { navController.navigate(Route.REGISTER) },
                    )
                }

                composable(Route.LOGIN) {
                    val context = LocalContext.current
                    LoginScreen(
                        onBack = { navController.popBackStack() },
                        onLoginSuccess = {
                            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
                            val prefs = context.getSharedPreferences("vitalsense_profile", Context.MODE_PRIVATE)
                            if (prefs.getBoolean("cuestionario_completed_$uid", false)) {
                                // Local data present → go directly to dashboard
                                navController.navigate(Route.DASHBOARD) {
                                    popUpTo(0) { inclusive = true }
                                }
                            } else {
                                // No local data — check Firebase (reinstall scenario)
                                CoroutineScope(Dispatchers.IO).launch {
                                    val completedInCloud = try {
                                        uid.isNotEmpty() &&
                                        FirebaseDatabase.getInstance()
                                            .getReference("patients/$uid/profile/cuestionarioCompleted")
                                            .get().await().getValue(Boolean::class.java) == true
                                    } catch (_: Exception) { false }
                                    if (completedInCloud) {
                                        prefs.edit().putBoolean("cuestionario_completed_$uid", true).apply()
                                        
                                        // Also restore watch state if available
                                        try {
                                            val watchSnap = FirebaseDatabase.getInstance().getReference("patients/$uid/watch").get().await()
                                            if (watchSnap.exists() && watchSnap.child("paired").getValue(Boolean::class.java) == true) {
                                                val watchPrefs = context.getSharedPreferences("vitalsense_watch_prefs", Context.MODE_PRIVATE)
                                                watchPrefs.edit()
                                                    .putBoolean("code_paired", true)
                                                    .putString("paired_code", watchSnap.child("code").getValue(String::class.java) ?: "")
                                                    .putString("paired_device_name", watchSnap.child("deviceName").getValue(String::class.java) ?: "Wearable")
                                                    .apply()
                                            }
                                        } catch (_: Exception) {}
                                    }
                                    val dest = if (completedInCloud) Route.DASHBOARD else Route.CUESTIONARIO
                                    CoroutineScope(Dispatchers.Main).launch {
                                        navController.navigate(dest) { popUpTo(0) { inclusive = true } }
                                    }
                                }
                            }
                        },
                        onRegisterClick = { navController.navigate(Route.REGISTER) },
                        onForgotPassword = { navController.navigate(Route.FORGOT_PASSWORD) },
                    )
                }

                composable(Route.FORGOT_PASSWORD) {
                    ForgotPasswordScreen(
                        onBack = { navController.navigateUp() },
                        onBackToLogin = {
                            navController.navigate(Route.LOGIN) {
                                popUpTo(Route.LOGIN) { inclusive = true }
                            }
                        }
                    )
                }
                composable(Route.REGISTER) {
                    val context = LocalContext.current
                    RegisterScreen(
                        onBack = { navController.popBackStack() },
                        onRegisterSuccess = {
                            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
                            val prefs = context.getSharedPreferences("vitalsense_profile", Context.MODE_PRIVATE)
                            if (prefs.getBoolean("cuestionario_completed_$uid", false)) {
                                navController.navigate(Route.DASHBOARD) {
                                    popUpTo(0) { inclusive = true }
                                }
                            } else {
                                navController.navigate(Route.CUESTIONARIO) {
                                    popUpTo(0) { inclusive = true }
                                }
                            }
                        },
                        onLoginClick = { navController.navigate(Route.LOGIN) },
                    )
                }

                composable(Route.CUESTIONARIO) {
                    CuestionarioScreen(
                        onBack = {
                            FirebaseAuth.getInstance().signOut()
                            navController.navigate(Route.LOGIN) {
                                popUpTo(0) { inclusive = true }
                            }
                        },
                        onNext = {
                            navController.navigate(Route.DASHBOARD) {
                                popUpTo(0) { inclusive = true }
                            }
                        },
                    )
                }

                composable(Route.DASHBOARD) {
                    DashboardScreen(
                        onConnectDevice = { navController.navigate(Route.DEVICE) },
                        onPatientClick = { patientId ->
                            navController.navigate("${Route.REPORTE_DETALLADO}/$patientId")
                        },
                        onProfileClick = { navController.navigate(Route.EDITAR_PERFIL) },
                        onReportClick  = { navController.navigate(Route.REPORTE_DIARIO) },
                        onNotifClick   = { navController.navigate(Route.NOTIFICACIONES) },
                        onNavigateToReports = { navController.navigate(Route.DAILY_REPORT) },
                        onNavigateToDetailed = { navController.navigate(Route.DETAILED_REPORT) },
                        onNavigateToNotifications = { navController.navigate(Route.NOTIFICATIONS) },
                        onNavigateToProfile = { navController.navigate(Route.PROFILE) },
                        onNavigateToHome = { /* Already here */ },
                        onNavigateToChat = { navController.navigate(Route.CHAT) },
                        onMedicationClick = { navController.navigate(Route.ADD_MEDICATION) },
                        onLibreScanClick = { navController.navigate(Route.LIBRE_SCAN) },
                        onEmergency = { vitals ->
                            // La IA detectó anomalía crítica → preparar QR y navegar
                            val anomalyType = when {
                                vitals.heartRate > 130              -> "Taquicardia severa (${vitals.heartRate} BPM)"
                                vitals.heartRate in 1..39           -> "Bradicardia severa (${vitals.heartRate} BPM)"
                                vitals.spo2 in 1..84                -> "Hipoxia crítica (SpO₂ ${vitals.spo2}%)"
                                vitals.glucose > 300.0              -> "Hiperglucemia grave (${"%.0f".format(vitals.glucose)} mg/dL)"
                                else                                -> "Anomalía detectada"
                            }
                            emergencyVm.onAnomalyDetected(anomalyType, vitals.heartRate)
                            navController.navigate(Route.EMERGENCY_QR) {
                                launchSingleTop = true
                                popUpTo(Route.DASHBOARD) { inclusive = true }
                            }
                        },
                    )
                }

                composable(Route.DEVICE) {
                    DeviceScanScreen(onBack = { navController.popBackStack() })
                }

                composable("${Route.PATIENT_DETAIL}/{patientId}") { backStackEntry ->
                    val patientId = backStackEntry.arguments?.getString("patientId") ?: return@composable
                    PatientDetailScreen(
                        patientId = patientId,
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(Route.PROFILE) {
                    ProfileScreen(
                        onDeviceClick = { navController.navigate(Route.DEVICE) },
                        onBack = { navController.navigateUp() },
                        onSignOut = {
                            FirebaseAuth.getInstance().signOut()
                            navController.navigate(Route.LOGIN) {
                                popUpTo(0) { inclusive = true }
                            }
                        },
                        onDatosImportantes = { navController.navigate(Route.DATOS_IMPORTANTES) },
                        onHomeClick = {
                            navController.navigate(Route.DASHBOARD) {
                                popUpTo(Route.DASHBOARD) { inclusive = false }
                            }
                        },
                        onHealthClick  = { navController.navigate(Route.REPORTE_DIARIO) },
                        onNotifClick   = { navController.navigate(Route.NOTIFICACIONES) },
                    )
                }

                composable(Route.EDITAR_PERFIL) {
                    ProfileScreen(
                        onDeviceClick = { navController.navigate(Route.DEVICE) },
                        onBack = { navController.popBackStack() },
                        onSignOut = {
                            FirebaseAuth.getInstance().signOut()
                            navController.navigate(Route.ONBOARDING) {
                                popUpTo(0) { inclusive = true }
                            }
                        },
                        onDatosImportantes = { navController.navigate(Route.DATOS_IMPORTANTES) },
                        onHomeClick = {
                            navController.navigate(Route.DASHBOARD) {
                                popUpTo(Route.DASHBOARD) { inclusive = false }
                            }
                        },
                        onHealthClick = { navController.navigate(Route.REPORTE_DIARIO) },
                        onNotifClick  = { navController.navigate(Route.NOTIFICACIONES) },
                    )
                }

                composable(Route.REPORTE_DIARIO) {
                    DailyReportScreen(
                        onBack = { navController.popBackStack() },
                        onNavigateToDetailed = { navController.navigate(Route.DETAILED_REPORT) },
                        onNavigateToSleepDetail = { score, horas, estado ->
                            navController.navigate("${Route.SLEEP_DETAIL}?score=$score&horas=$horas&estado=$estado")
                        }
                    )
                }

                composable(Route.DAILY_REPORT) {
                    DailyReportScreen(
                        onBack = { navController.popBackStack() },
                        onNavigateToDetailed = { navController.navigate(Route.DETAILED_REPORT) },
                        onNavigateToSleepDetail = { score, horas, estado ->
                            navController.navigate("${Route.SLEEP_DETAIL}?score=$score&horas=$horas&estado=$estado")
                        }
                    )
                }

                composable(
                    route = "${Route.SLEEP_DETAIL}?score={score}&horas={horas}&estado={estado}",
                    arguments = listOf(
                        androidx.navigation.navArgument("score") { defaultValue = 0; type = androidx.navigation.NavType.IntType },
                        androidx.navigation.navArgument("horas") { defaultValue = 0f; type = androidx.navigation.NavType.FloatType },
                        androidx.navigation.navArgument("estado") { defaultValue = "Sin Datos"; type = androidx.navigation.NavType.StringType }
                    )
                ) { backStackEntry ->
                    val score = backStackEntry.arguments?.getInt("score") ?: 0
                    val horas = backStackEntry.arguments?.getFloat("horas") ?: 0f
                    val estado = backStackEntry.arguments?.getString("estado") ?: "Sin Datos"
                    mx.ita.vitalsense.ui.reports.SleepDetailScreen(
                        score = score,
                        horas = horas,
                        estado = estado,
                        onBack = { navController.navigateUp() }

                    )
                }

                composable(
                    route = "${Route.NOTIFICACIONES}?alertId={alertId}&lat={lat}&lng={lng}",
                    arguments = listOf(
                        androidx.navigation.navArgument("alertId") { defaultValue = ""; type = androidx.navigation.NavType.StringType },
                        androidx.navigation.navArgument("lat") { defaultValue = 0.0; type = androidx.navigation.NavType.FloatType },
                        androidx.navigation.navArgument("lng") { defaultValue = 0.0; type = androidx.navigation.NavType.FloatType },
                    ),
                    deepLinks = listOf(androidx.navigation.navDeepLink { uriPattern = "vitalsense://notifications" })
                ) { backStackEntry ->
                    val alertId = backStackEntry.arguments?.getString("alertId")
                    val lat = backStackEntry.arguments?.getFloat("lat")?.toDouble() ?: 0.0
                    val lng = backStackEntry.arguments?.getFloat("lng")?.toDouble() ?: 0.0
                    NotificacionesScreen(
                        initialAlertId = alertId,
                        initialLat = lat,
                        initialLng = lng,
                        onBack         = { navController.popBackStack() },
                        onHomeClick    = {
                            navController.navigate(Route.DASHBOARD) {
                                popUpTo(Route.DASHBOARD) { inclusive = false }
                            }
                        },
                        onHealthClick  = { navController.navigate(Route.REPORTE_DIARIO) },
                        onProfileClick = { navController.navigate(Route.PROFILE) },
                    )
                }

                composable(Route.NOTIFICATIONS) {
                    NotificacionesScreen(
                        onBack = { navController.navigateUp() },
                        onHomeClick = {
                            navController.navigate(Route.DASHBOARD) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onHealthClick = {
                            navController.navigate(Route.DAILY_REPORT) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onProfileClick = {
                            navController.navigate(Route.PROFILE) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }

                composable(Route.DATOS_IMPORTANTES) {
                    DatosImportantesScreen(
                        onBack        = { navController.popBackStack() },
                        onHomeClick   = {
                            navController.navigate(Route.DASHBOARD) {
                                popUpTo(Route.DASHBOARD) { inclusive = false }
                            }
                        },
                        onHealthClick = { navController.navigate(Route.REPORTE_DIARIO) },
                        onChatClick   = { navController.navigate(Route.CHAT) },
                        onNotifClick  = { navController.navigate(Route.NOTIFICACIONES) },
                    )
                }

                composable(Route.DOCUMENTOS) {
                    DocumentosScreen(onBack = { navController.popBackStack() })
                }

                // ── Reporte Detallado ─────────────────────────────────────────
                composable("${Route.REPORTE_DETALLADO}/{patientId}") { backStackEntry ->
                    val patientId = backStackEntry.arguments?.getString("patientId") ?: return@composable
                    PatientDetailScreen(
                        patientId = patientId,
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(Route.DETAILED_REPORT) {
                    mx.ita.vitalsense.ui.reports.DetailedReportScreen(
                        onBack = { navController.navigateUp() }
                    )
                }

                composable(Route.CHAT) {
                    ChatBotScreen(
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(Route.ADD_MEDICATION) {
                    AddMedicationScreen(
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(Route.LIBRE_SCAN) {
                    LibreScanScreen(
                        onBack = { navController.popBackStack() },
                    )
                }

                // ── Pantalla de QR de emergencia (paciente) ───────────────────
                composable(Route.EMERGENCY_QR) {
                    EmergencyQrScreen(
                        vm       = emergencyVm,
                        onResolve = {
                            // Navegar de vuelta a DASHBOARD después de resolver emergencia
                            navController.navigate(Route.DASHBOARD) {
                                popUpTo(Route.DASHBOARD) { inclusive = true }
                                launchSingleTop = true
                            }
                        },
                    )
                }

                // ── Pantalla de visualización del perfil (paramédico) ─────────
                composable("${Route.EMERGENCY_VIEWER}/{tokenId}") { backStackEntry ->
                    val tokenId = backStackEntry.arguments?.getString("tokenId") ?: return@composable
                    EmergencyViewerScreen(
                        tokenId = tokenId,
                        onBack  = { navController.popBackStack() },
                    )
                }
            }

            // Flotando encima de todo cuando proceda
            if (showBottomBar) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    GlobalBottomNavigationBar(
                        currentRoute = currentRoute,
                        onNavigate = { route ->
                            if (route != currentRoute) {
                                navController.navigate(route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        }
                    )
                }
            }
            
            GlobalSosOverlay()
        }
    }
}
