package com.catamsp.rite.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.catamsp.rite.ui.theme.SurfaceTertiary
import com.catamsp.rite.viewmodel.DashboardViewModel

@Composable
fun DashboardScreenPrototype(viewModel: DashboardViewModel = viewModel()) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val isServiceEnabled by viewModel.serviceEnabled.collectAsStateWithLifecycle()
    val currentPrefix by viewModel.currentPrefix.collectAsStateWithLifecycle()

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refreshServiceStatus()
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().graphicsLayer { },
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item { DashboardHeader() }
        item {
            ServiceStatusSection(
                isServiceEnabled = isServiceEnabled,
                onEnableClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            )
        }
        item { HowItWorksSection(currentPrefix) }
    }
}

@Composable
private fun DashboardHeader() {
    val logoPainter = painterResource(id = com.catamsp.rite.R.drawable.ic_app_logo)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 0.dp, bottom = 8.dp)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = logoPainter,
            contentDescription = "Rite Logo",
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(14.dp))
        )
        Spacer(modifier = Modifier.height(1.dp))
        Text(
            text = "Type  →  Trigger  →  Done",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ServiceStatusSection(isServiceEnabled: Boolean, onEnableClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isServiceEnabled) "Service Active" else "Service Off",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isServiceEnabled) MaterialTheme.colorScheme.onSurface else SurfaceTertiary
                )
                Text(
                    text = if (isServiceEnabled) "Rite is running in the background" else "Enable accessibility service to get started",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isServiceEnabled) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.onSurface)
                    .clickable(onClick = onEnableClick)
                    .padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Text(
                    text = if (isServiceEnabled) "Disable" else "Enable",
                    color = if (isServiceEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.background,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
    Spacer(modifier = Modifier.height(12.dp))
}

@Composable
private fun HowItWorksSection(prefix: String) {
    val steps = remember(prefix) {
        listOf(
            "Enable the service" to "Turn on accessibility service",
            "Add API keys" to "So Rite can process text",
            "Type '${prefix}fix' anywhere" to "Works in any app, at the end of text",
            "Done" to "Text transforms instantly"
        )
    }

    Spacer(modifier = Modifier.height(12.dp))
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "How it works",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(14.dp))
            steps.forEachIndexed { index, (title, desc) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.onSurface),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${index + 1}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.background
                        )
                    }
                    Column {
                        Text(text = title, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
                        Text(text = desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
