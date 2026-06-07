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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.foundation.layout.width
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.catamsp.rite.ui.DashboardScreenPrototype
import com.catamsp.rite.ui.CommandsScreen
import com.catamsp.rite.ui.KeysScreen
import com.catamsp.rite.ui.SettingsScreen
import com.catamsp.rite.ui.AboutScreen
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
    Dashboard(R.string.tab_home, Icons.Default.Home),
    Keys(R.string.tab_keys, Icons.Default.Lock),
    Commands(R.string.tab_commands, Icons.AutoMirrored.Filled.List),
    Settings(R.string.tab_settings, Icons.Default.Settings),
    About(R.string.tab_about, Icons.Default.Person)
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
                    Tab.About -> AboutScreen(viewModel = settingsViewModel)
                }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            FloatingTabBar(
                selectedTab = selectedTab,
                onTabSelected = { tab ->
                    if (selectedTab != tab) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        selectedTab = tab
                    }
                }
            )
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

@Composable
private fun FloatingTabBar(
    selectedTab: Tab,
    onTabSelected: (Tab) -> Unit
) {
    val density = LocalDensity.current
    val tabPositions = remember { mutableStateMapOf<Tab, Float>() }
    val tabWidths = remember { mutableStateMapOf<Tab, Float>() }

    val targetX = tabPositions[selectedTab] ?: 0f
    val targetWidth = tabWidths[selectedTab] ?: 0f

    val animatedX by animateFloatAsState(
        targetValue = targetX,
        animationSpec = tween(250, easing = FastOutSlowInEasing),
        label = "pill_x"
    )
    val animatedWidth by animateFloatAsState(
        targetValue = targetWidth,
        animationSpec = tween(250, easing = FastOutSlowInEasing),
        label = "pill_width"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black)
            .border(1.dp, Color.White, RoundedCornerShape(24.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        if (tabWidths.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(animatedX.toInt(), 0) }
                    .width(with(density) { animatedWidth.toDp() })
                    .height(40.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Tab.entries.forEach { tab ->
                val isSelected = selectedTab == tab
                Box(
                    modifier = Modifier
                        .onGloballyPositioned { coords ->
                            tabPositions[tab] = coords.positionInParent().x
                            tabWidths[tab] = coords.size.width.toFloat()
                        }
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onTabSelected(tab) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            tab.icon,
                            contentDescription = stringResource(tab.titleRes),
                            modifier = Modifier.size(20.dp),
                            tint = if (isSelected) Color.Black else Color(0xFF8E8E93)
                        )
                        AnimatedVisibility(
                            visible = isSelected,
                            enter = fadeIn(tween(200)),
                            exit = fadeOut(tween(150))
                        ) {
                            Text(
                                text = stringResource(tab.titleRes),
                                color = Color.Black,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}
