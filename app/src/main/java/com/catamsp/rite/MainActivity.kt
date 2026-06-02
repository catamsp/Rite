package com.catamsp.rite

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.catamsp.rite.ui.DashboardScreenPrototype
import com.catamsp.rite.ui.CommandsScreen
import com.catamsp.rite.ui.KeysScreen
import com.catamsp.rite.ui.SettingsScreen
import com.catamsp.rite.ui.theme.RiteTheme
import com.catamsp.rite.viewmodel.CommandsViewModel
import com.catamsp.rite.viewmodel.DashboardViewModel
import com.catamsp.rite.viewmodel.KeysViewModel
import com.catamsp.rite.viewmodel.SettingsViewModel
import androidx.compose.runtime.Immutable

class MainActivity : ComponentActivity() {

    private val callPhoneLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            })
        }
    }

    private val accessibilityPermLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            callPhoneLauncher.launch(Manifest.permission.CALL_PHONE)
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, "android.permission.ACCESSIBILITY_SERVICE")
                != PackageManager.PERMISSION_GRANTED
            ) {
                accessibilityPermLauncher.launch("android.permission.ACCESSIBILITY_SERVICE")
            }
        }

        setContent {
            RiteTheme {
                RiteMainScreen()
            }
        }
    }
}

@Immutable
sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Dashboard : Screen("dashboard", "Dashboard", Icons.Default.Home)
    data object Keys : Screen("keys", "Keys", Icons.Default.Lock)
    data object Commands : Screen("commands", "Commands", Icons.AutoMirrored.Filled.List)
    data object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}

@Composable
fun RiteMainScreen() {
    val navController = rememberNavController()
    val items = remember { listOf(Screen.Dashboard, Screen.Keys, Screen.Commands, Screen.Settings) }
    val haptic = LocalHapticFeedback.current
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val navBarColors = NavigationBarItemDefaults.colors(
        selectedIconColor = MaterialTheme.colorScheme.primary,
        selectedTextColor = MaterialTheme.colorScheme.primary,
        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
    )

    val dashboardViewModel: DashboardViewModel = viewModel(LocalContext.current as ComponentActivity)
    val commandsViewModel: CommandsViewModel = viewModel(LocalContext.current as ComponentActivity)
    val keysViewModel: KeysViewModel = viewModel(LocalContext.current as ComponentActivity)
    val settingsViewModel: SettingsViewModel = viewModel(LocalContext.current as ComponentActivity)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.background
            ) {
                items.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = currentRoute == screen.route,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.startDestinationId) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        colors = navBarColors
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Dashboard.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Dashboard.route) { DashboardScreenPrototype(viewModel = dashboardViewModel) }
            composable(Screen.Keys.route) { KeysScreen(viewModel = keysViewModel, settingsViewModel = settingsViewModel) }
            composable(Screen.Commands.route) { CommandsScreen(viewModel = commandsViewModel) }
            composable(Screen.Settings.route) { SettingsScreen(viewModel = settingsViewModel) }
        }
    }
}
