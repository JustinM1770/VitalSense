package mx.ita.vitalsense.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import mx.ita.vitalsense.ui.dashboard.DashboardScreen
import mx.ita.vitalsense.ui.device.DeviceScanScreen
import mx.ita.vitalsense.ui.onboarding.OnboardingScreen
import mx.ita.vitalsense.ui.register.RegisterScreen
import mx.ita.vitalsense.ui.splash.SplashScreen

object Route {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val REGISTER = "register"
    const val DASHBOARD = "dashboard"
    const val DEVICE = "device"
}

@Composable
fun AppNavigation() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Route.SPLASH) {
        composable(Route.SPLASH) {
            SplashScreen(
                onTimeout = {
                    navController.navigate(Route.ONBOARDING) {
                        popUpTo(Route.SPLASH) { inclusive = true }
                    }
                },
            )
        }
        composable(Route.ONBOARDING) {
            OnboardingScreen(
                // "Omitir" y "Comenzar" van al mismo destino por ahora
                onSkip = { navController.navigate(Route.REGISTER) },
                onGetStarted = { navController.navigate(Route.REGISTER) },
            )
        }
        composable(Route.REGISTER) {
            RegisterScreen(
                // popBackStack() regresa a Onboarding porque NO lo sacamos del stack
                onBack = { navController.popBackStack() },
                onRegisterSuccess = {
                    navController.navigate(Route.DASHBOARD) {
                        popUpTo(0) { inclusive = true } // limpia todo el stack
                    }
                },
                onLoginClick = {
                    // TODO: LoginScreen (pendiente de diseño Figma)
                },
            )
        }
        composable(Route.DASHBOARD) {
            DashboardScreen(
                onConnectDevice = { navController.navigate(Route.DEVICE) }
            )
        }
        composable(Route.DEVICE) {
            DeviceScanScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
