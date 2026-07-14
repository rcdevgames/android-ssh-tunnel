package net.rcdevgames.simpletunnel

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import net.rcdevgames.simpletunnel.presentation.edit.EditScreen
import net.rcdevgames.simpletunnel.presentation.edit.EditViewModel
import net.rcdevgames.simpletunnel.presentation.home.HomeScreen
import net.rcdevgames.simpletunnel.presentation.home.HomeViewModel
import net.rcdevgames.simpletunnel.ui.theme.SimpleTunnelTheme

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Permission result handled */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request notification permission for Android 13+
        requestNotificationPermission()

        val app = application as SimpleTunnelApp
        setContent {
            SimpleTunnelTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    NavHost(
                        navController = navController,
                        startDestination = "home"
                    ) {
                        composable("home") {
                            val viewModel: HomeViewModel = remember {
                                HomeViewModel(app.tunnelRepository, app.credentialStore)
                            }
                            HomeScreen(
                                onNavigateToEdit = { tunnelId ->
                                    navController.navigate("edit/${tunnelId ?: 0}")
                                },
                                viewModel = viewModel
                            )
                        }
                        composable(
                            route = "edit/{tunnelId}",
                            arguments = listOf(
                                navArgument("tunnelId") {
                                    type = NavType.LongType
                                    defaultValue = 0L
                                }
                            )
                        ) { backStackEntry ->
                            val tunnelId = backStackEntry.arguments?.getLong("tunnelId") ?: 0L
                            val viewModel: EditViewModel = remember(tunnelId) {
                                EditViewModel(app.tunnelRepository, app.credentialStore)
                            }
                            EditScreen(
                                onNavigateBack = { navController.popBackStack() },
                                viewModel = viewModel,
                                tunnelId = tunnelId.takeIf { it > 0 }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
