package mx.ita.vitalsense

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.fragment.app.FragmentActivity
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import mx.ita.vitalsense.data.health.HealthConnectRepository
import mx.ita.vitalsense.ui.health.HealthConnectViewModel
import mx.ita.vitalsense.ui.navigation.AppNavigation
import mx.ita.vitalsense.ui.theme.VitalSenseTheme

class MainActivity : FragmentActivity() {

    val healthConnectViewModel: HealthConnectViewModel by viewModels()

    // Use the official Health Connect permission contract
    private val hcPermissionLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { _ ->
        healthConnectViewModel.refreshPairedState()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = androidx.activity.SystemBarStyle.light(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )
        setContent {
            VitalSenseTheme {
                AppNavigation()
            }
        }
        // Request Health Connect permissions AFTER setContent to avoid blank-screen freeze
        if (HealthConnectClient.getSdkStatus(this) == HealthConnectClient.SDK_AVAILABLE) {
            hcPermissionLauncher.launch(HealthConnectRepository.PERMISSIONS)
        }
    }
}
