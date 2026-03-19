package mx.ita.vitalsense.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.firebase.auth.FirebaseAuth
import mx.ita.vitalsense.ui.dashboard.DashboardScreen
import mx.ita.vitalsense.ui.device.DeviceScanScreen
import mx.ita.vitalsense.ui.onboarding.OnboardingScreen
import mx.ita.vitalsense.ui.reports.DailyReportScreen
import mx.ita.vitalsense.ui.patient.PatientDetailScreen
import mx.ita.vitalsense.ui.register.RegisterScreen
import mx.ita.vitalsense.ui.login.LoginScreen
import mx.ita.vitalsense.ui.splash.SplashScreen

object Route {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val REGISTER = "register"
    const val LOGIN = "login"
    const val DASHBOARD = "dashboard"
    const val DEVICE = "device"
    const val DAILY_REPORT = "daily_report"
    const val DETAILED_REPORT = "detailed_report"
    const val NOTIFICATIONS = "notifications"
    const val PROFILE = "profile"
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Route.SPLASH) {
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
        composable(Route.LOGIN) {
            LoginScreen(
                onBack = { navController.popBackStack() },
                onLoginSuccess = {
                    navController.navigate(Route.DASHBOARD) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onRegisterClick = { navController.navigate(Route.REGISTER) }
            )
        }
        composable(Route.REGISTER) {
            RegisterScreen(
                onBack = { navController.popBackStack() },
                onRegisterSuccess = {
                    navController.navigate(Route.DASHBOARD) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onLoginClick = {
                    navController.navigate(Route.LOGIN)
                },
            )
        }
        composable(Route.DASHBOARD) {
            DashboardScreen(
                onConnectDevice = { navController.navigate(Route.DEVICE) },
                onNavigateToReports = { navController.navigate(Route.DAILY_REPORT) },
                onNavigateToNotifications = { navController.navigate(Route.NOTIFICATIONS) },
                onNavigateToProfile = { navController.navigate(Route.PROFILE) },
                onNavigateToHome = { /* Already here */ },
                onNavigateToChat = { /* TODO: Chat screen */ }
            )
        }
        composable(Route.DEVICE) {
            DeviceScanScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(Route.DAILY_REPORT) {
            DailyReportScreen(
                onBack = { navController.popBackStack() },
                onNavigateToDetailed = { navController.navigate(Route.DETAILED_REPORT) }
            )
        }
        composable(Route.DETAILED_REPORT) {
            PatientDetailScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(Route.NOTIFICATIONS) {
            // Placeholder for Notifications
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Pantalla de Notificaciones")
                Button(onClick = { navController.popBackStack() }, modifier = Modifier.align(Alignment.BottomCenter)) {
                    Text("Volver")
                }
            }
        }
        composable(Route.PROFILE) {
            // Placeholder for Profile/Settings
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Pantalla de Perfil")
                    Button(onClick = { 
                        FirebaseAuth.getInstance().signOut()
                        navController.navigate(Route.LOGIN) {
                            popUpTo(0) { inclusive = true }
                        }
                    }) {
                        Text("Cerrar Sesión")
                    }
                    Button(onClick = { navController.popBackStack() }) {
                        Text("Volver")
                    }
                }
            }
        }
    }
}
