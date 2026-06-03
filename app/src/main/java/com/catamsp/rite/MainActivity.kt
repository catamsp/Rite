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
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.catamsp.rite.ui.DashboardScreenPrototype
import com.catamsp.rite.ui.CommandsScreen
import com.catamsp.rite.ui.KeysScreen
import com.catamsp.rite.ui.SettingsScreen
import com.catamsp.rite.ui.theme.RiteTheme
import com.catamsp.rite.viewmodel.CommandsViewModel
import com.catamsp.rite.viewmodel.DashboardViewModel
import com.catamsp.rite.viewmodel.KeysViewModel
import com.catamsp.rite.viewmodel.SettingsViewModel

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

enum class Tab(@StringRes val titleRes: Int, val icon: ImageVector) {
    Dashboard(R.string.app_name, Icons.Default.Home),
    Keys(R.string.app_name, Icons.Default.Lock),
    Commands(R.string.app_name, Icons.AutoMirrored.Filled.List),
    Settings(R.string.app_name, Icons.Default.Settings)
}

@Composable
fun RiteMainScreen() {
    val haptic = LocalHapticFeedback.current
    var selectedTab by rememberSaveable { mutableStateOf(Tab.Dashboard) }

    val dashboardViewModel: DashboardViewModel = viewModel()
    val commandsViewModel: CommandsViewModel = viewModel()
    val keysViewModel: KeysViewModel = viewModel()
    val settingsViewModel: SettingsViewModel = viewModel()

    val screens = remember {
        Tab.entries.associateWith { tab ->
            movableContentOf {
                when (tab) {
                    Tab.Dashboard -> DashboardScreenPrototype(viewModel = dashboardViewModel)
                    Tab.Keys -> KeysScreen(viewModel = keysViewModel, settingsViewModel = settingsViewModel)
                    Tab.Commands -> CommandsScreen(viewModel = commandsViewModel)
                    Tab.Settings -> SettingsScreen(viewModel = settingsViewModel)
                }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.background,
                tonalElevation = 0.dp
            ) {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        icon = {
                            Icon(
                                tab.icon,
                                contentDescription = stringResource(tab.titleRes)
                            )
                        },
                        label = null,
                        selected = selectedTab == tab,
                        onClick = {
                            if (selectedTab != tab) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                selectedTab = tab
                            }
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        AnimatedContent(
            targetState = selectedTab,
            modifier = Modifier.padding(innerPadding),
            transitionSpec = {
                val direction = if (targetState.ordinal > initialState.ordinal)
                    AnimatedContentTransitionScope.SlideDirection.Left
                else
                    AnimatedContentTransitionScope.SlideDirection.Right
                slideIntoContainer(
                    direction,
                    animationSpec = tween(250, easing = FastOutSlowInEasing)
                ) togetherWith slideOutOfContainer(
                    direction,
                    animationSpec = tween(250, easing = FastOutSlowInEasing)
                )
            },
            label = "tab_transition"
        ) { tab ->
            screens[tab]?.invoke()
        }
    }
}
