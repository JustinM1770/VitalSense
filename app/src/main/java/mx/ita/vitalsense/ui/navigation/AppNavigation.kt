package mx.ita.vitalsense.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
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
import mx.ita.vitalsense.ui.splash.SplashScreen

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
    const val REPORTE_DETALLADO     = "reporte_detallado"  // Jonathan: gráfica expandida
    const val NOTIFICACIONES        = "notificaciones"
    const val DATOS_IMPORTANTES     = "datos_importantes"
    const val DOCUMENTOS            = "documentos"
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Route.SPLASH) {

        composable(Route.SPLASH) {
            SplashScreen(
                onTimeout = {
                    // Si ya hay sesión activa, saltar directo al Dashboard
                    val destination = if (FirebaseAuth.getInstance().currentUser != null)
                        Route.DASHBOARD else Route.ONBOARDING
                    navController.navigate(destination) {
                        popUpTo(Route.SPLASH) { inclusive = true }
                    }
                },
            )
        }

        composable(Route.ONBOARDING) {
            OnboardingScreen(
                onSkip = { navController.navigate(Route.REGISTER) },
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
                onRegisterClick = { navController.popBackStack() },
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
                    navController.navigate(Route.ONBOARDING) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onDatosImportantes = { navController.navigate(Route.DATOS_IMPORTANTES) },
                onHomeClick    = {
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
                onBack        = { navController.popBackStack() },
                onProfileClick = { navController.navigate(Route.PROFILE) },
                onHomeClick   = {
                    navController.navigate(Route.DASHBOARD) {
                        popUpTo(Route.DASHBOARD) { inclusive = false }
                    }
                },
                onNotifClick  = { navController.navigate(Route.NOTIFICACIONES) },
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
    }
}
