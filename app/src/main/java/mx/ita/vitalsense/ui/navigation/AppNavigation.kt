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
import mx.ita.vitalsense.ui.forgotpassword.ForgotPasswordScreen
import mx.ita.vitalsense.ui.login.LoginScreen
import mx.ita.vitalsense.ui.notifications.NotificacionesScreen
import mx.ita.vitalsense.ui.onboarding.OnboardingScreen
import mx.ita.vitalsense.ui.reports.DailyReportScreen
import mx.ita.vitalsense.ui.patient.PatientDetailScreen
import mx.ita.vitalsense.ui.register.RegisterScreen
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
    const val SLEEP_DETAIL = "sleep_detail"
    const val NOTIFICATIONS = "notifications"
    const val PROFILE = "profile"
    const val CHAT = "chat"
    const val FORGOT_PASSWORD = "forgot_password"
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
                        onRegisterClick = { navController.navigate(Route.REGISTER) },
                        onForgotPassword = { navController.navigate(Route.FORGOT_PASSWORD) }
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
                        onNavigateToDetailed = { navController.navigate(Route.DETAILED_REPORT) },
                        onNavigateToSleepDetail = { sleepData ->
                            val score = sleepData?.score ?: 0
                            val horas = sleepData?.horas ?: 0f
                            val estado = sleepData?.estado ?: "Sin Datos"
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
                composable(Route.PROFILE) {
                    mx.ita.vitalsense.ui.profile.ProfileScreen(
                        onDeviceClick = { navController.navigate(Route.DEVICE) },
                        onBack = { navController.navigateUp() },
                        onSignOut = {
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
