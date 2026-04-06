package com.catamsp.rite.ui

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.catamsp.rite.manager.CommandManager
import com.catamsp.rite.manager.KeyManager
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreenPrototype() {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val uriHandler = LocalUriHandler.current
    val keyManager = remember { KeyManager(context) }
    val commandManager = remember { CommandManager(context) }
    var isServiceEnabled by remember { mutableStateOf(checkServiceEnabled(context)) }
    var keyCount by remember { mutableIntStateOf(keyManager.getKeys().size) }
    var currentPrefix by remember { mutableStateOf(commandManager.getTriggerPrefix()) }

    val activityLifecycle = (context as? ComponentActivity)?.lifecycle
    LaunchedEffect(activityLifecycle) {
        val lifecycle = activityLifecycle ?: return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                isServiceEnabled = checkServiceEnabled(context)
                keyCount = keyManager.getKeys().size
                currentPrefix = commandManager.getTriggerPrefix()
                delay(3000)
            }
        }
    }

    val surfaceColor = Color(0xFF000000)
    val cardBg = Color(0xFF1C1C1E)
    val accent = Color.White
    val dimText = Color(0xFF8E8E93)
    val green = Color.White
    val red = Color(0xFF6E6E73)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // ── Header ──────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = com.catamsp.rite.R.drawable.ic_app_logo),
                contentDescription = "Rite Logo",
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(14.dp))
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "type  →  trigger  →  done",
                fontSize = 14.sp,
                color = dimText,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // ── Status Card ─────────────────────────────────────
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            shape = RoundedCornerShape(20.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (isServiceEnabled) "We're alive" else "Flatlining",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isServiceEnabled) green else red
                    )
                    Text(
                        text = if (isServiceEnabled) "Watching every keystroke" else "Rite is currently sleeping",
                        fontSize = 13.sp,
                        color = dimText,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                AnimatedVisibility(
                    visible = !isServiceEnabled,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(accent)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            }
                            .padding(horizontal = 18.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = "Wake it up",
                            color = Color.Black,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Keys Card ───────────────────────────────────────
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = when (keyCount) {
                        0 -> "No Brain cells yet"
                        1 -> "1 Brain cell active"
                        else -> "$keyCount brain cells firing"
                    },
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = if (keyCount == 0) "I'm running on fumes. Feed me API keys." else "Feed me more API keys.",
                    fontSize = 13.sp,
                    color = dimText,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Key Status Card ─────────────────────────────────
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "API Key Status",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(12.dp))
                val statuses = keyManager.getKeyStatuses()
                if (statuses.isEmpty()) {
                    Text(
                        text = "No keys added yet",
                        fontSize = 13.sp,
                        color = dimText
                    )
                } else {
                    statuses.forEach { status ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(
                                            if (status.isReady) Color.White else Color(0xFF636366),
                                            shape = RoundedCornerShape(4.dp)
                                        )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "••••${status.maskedKey.takeLast(4)}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White
                                )
                            }
                            Text(
                                text = if (status.isReady) "Alive" else "Resting (${(status.remainingMs!! / 1000L) + 1}s)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = if (status.isReady) Color.White else Color(0xFF8E8E93)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── How To Use ──────────────────────────────────────
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "How it works",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(12.dp))
                listOf(
                    "Flip the switch above" to "Enable the service",
                    "Give me API keys" to "So I can think",
                    "Type '${currentPrefix}fix' anywhere" to "In any app, at the end of text",
                    "Watch the magic" to "Text transforms instantly"
                ).forEach { (title, desc) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(text = title, fontSize = 14.sp, color = Color.White, fontWeight = FontWeight.Medium)
                        Text(text = "→", fontSize = 14.sp, color = accent)
                        Text(text = desc, fontSize = 13.sp, color = dimText)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // ── Footer / Credits ────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val annotatedString = buildAnnotatedString {
                withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Medium)) {
                    append("Built by ")
                }
                pushStringAnnotation(tag = "link", annotation = "https://github.com/catamsp")
                withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Medium, textDecoration = TextDecoration.Underline)) {
                    append("catamsp")
                }
                pop()
                pushStringAnnotation(tag = "star1", annotation = "https://github.com/catamsp")
                withStyle(SpanStyle(color = Color.White)) {
                    append("  ★  ")
                }
                pop()
                withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Medium)) {
                    append("Vibe Coded")
                }
                pushStringAnnotation(tag = "star2", annotation = "https://github.com/Musheer360/SwiftSlate")
                withStyle(SpanStyle(color = Color.White)) {
                    append("  ★  ")
                }
                pop()
                withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Medium)) {
                    append("from ")
                }
                pushStringAnnotation(tag = "link2", annotation = "https://github.com/Musheer360/SwiftSlate")
                withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Medium, textDecoration = TextDecoration.Underline)) {
                    append("SwiftSlate")
                }
                pop()
            }

            ClickableText(
                text = annotatedString,
                onClick = { offset ->
                    annotatedString.getStringAnnotations(start = offset, end = offset)
                        .firstOrNull()?.let { annotation ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            uriHandler.openUri(annotation.item)
                        }
                }
            )
        }
    }
}

private fun checkServiceEnabled(context: Context): Boolean {
    val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
    return enabledServices.any {
        it.resolveInfo.serviceInfo.packageName == context.packageName
    }
}
