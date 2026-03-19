package mx.ita.vitalsense.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.firebase.auth.FirebaseAuth
import mx.ita.vitalsense.ui.archivos.DatosImportantesScreen
import mx.ita.vitalsense.ui.cuestionario.CuestionarioScreen
import mx.ita.vitalsense.ui.dashboard.DashboardScreen
import mx.ita.vitalsense.ui.device.DeviceScanScreen
import mx.ita.vitalsense.ui.documentos.DocumentosScreen
import mx.ita.vitalsense.ui.login.LoginScreen
import mx.ita.vitalsense.ui.notifications.NotificacionesScreen
import mx.ita.vitalsense.ui.onboarding.OnboardingScreen
import mx.ita.vitalsense.ui.patient.PatientDetailScreen
import mx.ita.vitalsense.ui.profile.ProfileScreen
import mx.ita.vitalsense.ui.register.RegisterScreen
import mx.ita.vitalsense.ui.report.ReporteDiarioScreen
import mx.ita.vitalsense.ui.reports.DailyReportScreen
import mx.ita.vitalsense.ui.splash.SplashScreen
import mx.ita.vitalsense.ui.chat.ChatBotScreen
import mx.ita.vitalsense.ui.components.GlobalBottomNavigationBar

object Route {
    const val SPLASH                = "splash"
    const val ONBOARDING            = "onboarding"
    const val REGISTER              = "register"
    const val LOGIN                 = "login"
    const val CUESTIONARIO          = "cuestionario"   // Miguel: flujo de cuestionarios
    const val DASHBOARD             = "dashboard"
    const val DEVICE                = "device"
    const val PROFILE               = "profile"
    const val EDITAR_PERFIL         = "editar_perfil"  // Omar: editar datos personales
    const val PATIENT_DETAIL        = "patient_detail"
    const val REPORTE_DIARIO        = "reporte_diario"
    const val DAILY_REPORT          = "daily_report"
    const val REPORTE_DETALLADO     = "reporte_detallado"  // Jonathan: gráfica expandida
    const val DETAILED_REPORT       = "detailed_report"
    const val NOTIFICACIONES        = "notificaciones"
    const val NOTIFICATIONS         = "notifications"
    const val DATOS_IMPORTANTES     = "datos_importantes"
    const val DOCUMENTOS            = "documentos"
    const val CHAT                  = "chat"
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val bottomBarRoutes = listOf(
        Route.DASHBOARD,
        Route.DAILY_REPORT,
        Route.REPORTE_DIARIO,
        Route.DETAILED_REPORT,
        Route.REPORTE_DETALLADO,
        Route.NOTIFICACIONES,
        Route.NOTIFICATIONS,
        Route.PROFILE,
        Route.CHAT,
    )
    val showBottomBar = currentRoute in bottomBarRoutes

    Scaffold(
        containerColor = Color.Transparent
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
                        }
                    )
                }

                composable(Route.ONBOARDING) {
                    OnboardingScreen(
                        onSkip = { navController.navigate(Route.LOGIN) },
                        onGetStarted = { navController.navigate(Route.REGISTER) },
                    )
                }

                composable(Route.REGISTER) {
                    RegisterScreen(
                        onBack = { navController.popBackStack() },
                        onRegisterSuccess = {
                            navController.navigate(Route.CUESTIONARIO) {
                                popUpTo(0) { inclusive = true }
                            }
                        },
                        onLoginClick = { navController.navigate(Route.LOGIN) },
                    )
                }

                composable(Route.LOGIN) {
                    LoginScreen(
                        onBack = { navController.popBackStack() },
                        onLoginSuccess = {
                            navController.navigate(Route.DASHBOARD) {
                                popUpTo(0) { inclusive = true }
                            }
                        },
                        onRegisterClick = { navController.navigate(Route.REGISTER) },
                    )
                }

                composable(Route.CUESTIONARIO) {
                    CuestionarioScreen(
                        onBack = { navController.popBackStack() },
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
                        onHealthClick  = { navController.navigate(Route.REPORTE_DIARIO) },
                        onNotifClick   = { navController.navigate(Route.NOTIFICACIONES) },
                    )
                }

                composable(Route.REPORTE_DIARIO) {
                    ReporteDiarioScreen(
                        onBack         = { navController.popBackStack() },
                        onProfileClick = { navController.navigate(Route.PROFILE) },
                        onHomeClick    = {
                            navController.navigate(Route.DASHBOARD) {
                                popUpTo(Route.DASHBOARD) { inclusive = false }
                            }
                        },
                        onNotifClick   = { navController.navigate(Route.NOTIFICACIONES) },
                    )
                }

                composable(Route.DAILY_REPORT) {
                    DailyReportScreen(
                        onBack = { navController.popBackStack() },
                        onNavigateToDetailed = { navController.navigate(Route.DETAILED_REPORT) }
                    )
                }

                composable(Route.NOTIFICACIONES) {
                    NotificacionesScreen(
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
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Pantalla de Notificaciones")
                        Button(
                            onClick = { navController.popBackStack() },
                            modifier = Modifier.align(Alignment.BottomCenter)
                        ) {
                            Text("Volver")
                        }
                    }
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
                        onNotifClick  = { navController.navigate(Route.NOTIFICACIONES) },
                    )
                }

                composable(Route.DOCUMENTOS) {
                    DocumentosScreen(onBack = { navController.popBackStack() })
                }

                // ── Editar Perfil (Omar implementa la pantalla completa) ──────────────
                composable(Route.EDITAR_PERFIL) {
                    ProfileScreen(
                        onBack = { navController.popBackStack() },
                        onSignOut = {
                            FirebaseAuth.getInstance().signOut()
                            navController.navigate(Route.ONBOARDING) {
                                popUpTo(0) { inclusive = true }
                            }
                        },
                        onDatosImportantes = { navController.navigate(Route.DATOS_IMPORTANTES) },
                        onHomeClick   = {
                            navController.navigate(Route.DASHBOARD) {
                                popUpTo(Route.DASHBOARD) { inclusive = false }
                            }
                        },
                        onHealthClick = { navController.navigate(Route.REPORTE_DIARIO) },
                        onNotifClick  = { navController.navigate(Route.NOTIFICACIONES) },
                    )
                }

                // ── Reporte Detallado (Jonathan implementa la pantalla) ───────────────
                composable("${Route.REPORTE_DETALLADO}/{patientId}") { backStackEntry ->
                    val patientId = backStackEntry.arguments?.getString("patientId") ?: return@composable
                    PatientDetailScreen(
                        patientId = patientId,
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(Route.DETAILED_REPORT) {
                    PatientDetailScreen(
                        onBack = { navController.popBackStack() }
                    )
                }

                composable(Route.CHAT) {
                    ChatBotScreen(
                        onBack = { navController.popBackStack() }
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
        }
    }
}
