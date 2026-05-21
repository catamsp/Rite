package com.catamsp.rite.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.catamsp.rite.viewmodel.DashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreenPrototype(viewModel: DashboardViewModel = viewModel()) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val uriHandler = LocalUriHandler.current
    
    val isServiceEnabled by viewModel.isServiceEnabled.collectAsState()
    val keyCount by viewModel.keyCount.collectAsState()
    val currentPrefix by viewModel.currentPrefix.collectAsState()
    val keyStatuses by viewModel.keyStatuses.collectAsState()

    val cardBg = Color(0xFF1C1C1E)
    val accent = Color.White
    val dimText = Color(0xFF8E8E93)
    val green = Color.White
    val red = Color(0xFF6E6E73)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // ── Header ──────────────────────────────────────────
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp, bottom = 24.dp)
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
        }

        // ── Status Card ─────────────────────────────────────
        item {
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
        }

        // ── Keys Summary Card ───────────────────────────────
        item {
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
        }

        // ── Key Status List Title ───────────────────────────
        item {
            Text(
                text = "API Key Status",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 44.dp, vertical = 8.dp)
            )
        }

        // ── Key Status Items ────────────────────────────────
        if (keyStatuses.isEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        text = "No keys added yet",
                        fontSize = 13.sp,
                        color = dimText,
                        modifier = Modifier.padding(20.dp)
                    )
                }
            }
        } else {
            items(keyStatuses) { status ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
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
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "••••${status.maskedKey.takeLast(4)}",
                                fontSize = 14.sp,
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

        // ── How To Use ──────────────────────────────────────
        item {
            Spacer(modifier = Modifier.height(12.dp))
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
        }

        // ── Footer / Credits ────────────────────────────────
        item {
            Spacer(modifier = Modifier.height(32.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
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

                @Suppress("DEPRECATION")
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
}
