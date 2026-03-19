package mx.ita.vitalsense.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.firebase.auth.FirebaseAuth
import mx.ita.vitalsense.ui.dashboard.DashboardScreen
import mx.ita.vitalsense.ui.device.DeviceScanScreen
import mx.ita.vitalsense.ui.documentos.DocumentosScreen
import mx.ita.vitalsense.ui.login.LoginScreen
import mx.ita.vitalsense.ui.notifications.NotificacionesScreen
import mx.ita.vitalsense.ui.onboarding.OnboardingScreen
import mx.ita.vitalsense.ui.reports.DailyReportScreen
import mx.ita.vitalsense.ui.patient.PatientDetailScreen
import mx.ita.vitalsense.ui.register.RegisterScreen
import mx.ita.vitalsense.ui.login.LoginScreen
import mx.ita.vitalsense.ui.splash.SplashScreen
import mx.ita.vitalsense.ui.chat.ChatBotScreen
import mx.ita.vitalsense.ui.components.GlobalBottomNavigationBar
import androidx.compose.material3.Scaffold
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color

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
    const val CHAT = "chat"
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val bottomBarRoutes = listOf(
        Route.DASHBOARD,
        Route.DAILY_REPORT,
        Route.DETAILED_REPORT,
        Route.NOTIFICATIONS,
        Route.PROFILE,
        Route.CHAT
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
                composable(Route.LOGIN) {
                    LoginScreen(
                        onBack = { navController.navigateUp() },
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
                        onBack = { navController.navigateUp() },
                        onRegisterSuccess = {
                            navController.navigate(Route.DASHBOARD) {
                                popUpTo(0) { inclusive = true }
                            }
                        },
                        onLoginClick = { navController.navigate(Route.LOGIN) },
                    )
                }
                composable(Route.DASHBOARD) {
                    DashboardScreen(
                        onConnectDevice = { navController.navigate(Route.DEVICE) },
                        onNavigateToReports = { navController.navigate(Route.DAILY_REPORT) },
                        onNavigateToDetailed = { navController.navigate(Route.DETAILED_REPORT) },
                        onNavigateToNotifications = { navController.navigate(Route.NOTIFICATIONS) },
                        onNavigateToProfile = { navController.navigate(Route.PROFILE) },
                        onNavigateToHome = { /* Already here */ },
                        onNavigateToChat = { navController.navigate(Route.CHAT) }
                    )
                }
                composable(Route.DEVICE) {
                    DeviceScanScreen(
                        onBack = { navController.navigateUp() }
                    )
                }
                composable(Route.DAILY_REPORT) {
                    DailyReportScreen(
                        onBack = { navController.navigateUp() },
                        onNavigateToDetailed = { navController.navigate(Route.DETAILED_REPORT) }
                    )
                }
                composable(Route.DETAILED_REPORT) {
                    mx.ita.vitalsense.ui.reports.DetailedReportScreen(
                        onBack = { navController.navigateUp() }
                    )
                }
                composable(Route.CHAT) {
                    ChatBotScreen(
                        onBack = { navController.navigateUp() }
                    )
                }
                composable(Route.NOTIFICATIONS) {
                    Scaffold(
                        bottomBar = { Spacer(Modifier.height(80.dp)) } // Reservar espacio para la barra global
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Pantalla de Notificaciones", style = MaterialTheme.typography.titleLarge)
                                Spacer(Modifier.height(20.dp))
                                Button(onClick = { navController.navigateUp() }) {
                                    Text("Volver")
                                }
                            }
                        }
                    }
                }
                composable(Route.PROFILE) {
                    mx.ita.vitalsense.ui.profile.ProfileScreen(
                        onDeviceClick = { navController.navigate(Route.DEVICE) },
                        onBack = { navController.navigateUp() },
                        onLogout = {
                            FirebaseAuth.getInstance().signOut()
                            navController.navigate(Route.LOGIN) {
                                popUpTo(0) { inclusive = true }
                            }
                        }
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
