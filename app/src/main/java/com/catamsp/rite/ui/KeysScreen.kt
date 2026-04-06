package com.catamsp.rite.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.catamsp.rite.api.GeminiClient
import com.catamsp.rite.api.OpenAICompatibleClient
import com.catamsp.rite.manager.KeyManager
import com.catamsp.rite.ui.components.ScreenTitle
import com.catamsp.rite.ui.components.SlateCard
import kotlinx.coroutines.launch

@Composable
fun KeysScreen() {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val keyManager = remember { KeyManager(context) }
    var keys by remember { mutableStateOf(keyManager.getKeys()) }
    var newKey by remember { mutableStateOf("") }
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val geminiClient = remember { GeminiClient() }
    val openAIClient = remember { OpenAICompatibleClient() }
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    val providerType = remember { prefs.getString("provider_type", "gemini") ?: "gemini" }
    val customEndpoint = remember { prefs.getString("custom_endpoint", "") ?: "" }
    val customModel = remember { prefs.getString("custom_model", "") ?: "" }

    val cardBg = Color(0xFF1C1C1E)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(top = 24.dp)
    ) {
        ScreenTitle("API Keys")

        // Warning when keystore is unavailable
        if (!keyManager.isKeystoreAvailable) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2E)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "⚠️ Security Warning",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color(0xFFEBEBF5)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Your device's security chip is unavailable. API keys cannot be stored safely on this device.",
                        fontSize = 13.sp,
                        color = Color(0xFF8E8E93)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = newKey,
                    onValueChange = { newKey = it },
                    label = { Text("API Key") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color(0xFF3A3A3C)
                    )
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = {
                            if (newKey.isNotBlank()) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                isTesting = true
                                testResult = null
                                scope.launch {
                                    val trimmedKey = newKey.trim()
                                    if (keys.contains(trimmedKey)) {
                                        isTesting = false
                                        testResult = "This key has already been added"
                                        return@launch
                                    }
                                    val result = if (providerType == "custom" && customEndpoint.isNotBlank()) {
                                        openAIClient.validateKey(trimmedKey, customEndpoint, customModel)
                                    } else {
                                        geminiClient.validateKey(trimmedKey)
                                    }
                                    isTesting = false
                                    if (result.isSuccess) {
                                        val addResult = keyManager.addKey(trimmedKey)
                                        if (addResult.isSuccess) {
                                            keys = keyManager.getKeys()
                                            newKey = ""
                                            testResult = "Valid key added!"
                                        } else {
                                            testResult = addResult.exceptionOrNull()?.message ?: "Failed to store key"
                                        }
                                    } else {
                                        testResult = result.exceptionOrNull()?.message ?: "Validation failed"
                                    }
                                }
                            }
                        },
                        enabled = newKey.isNotBlank() && !isTesting,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White)
                    ) {
                        Text(if (isTesting) "Testing..." else "Add Key", color = Color.Black)
                    }
                }
                if (testResult != null) {
                    Text(
                        text = testResult!!,
                        color = if (testResult!!.startsWith("Valid")) Color.White else Color(0xFF6E6E73),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            if (keys.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No keys yet",
                            fontSize = 16.sp,
                            color = Color(0xFF8E8E93)
                        )
                        Text(
                            text = "Add one above to get started",
                            fontSize = 13.sp,
                            color = Color(0xFF8E8E93)
                        )
                    }
                }
            }
            items(keys) { key ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = cardBg),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "••••••••" + key.takeLast(6),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color.White
                            )
                        }
                        IconButton(onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            keyManager.removeKey(key)
                            keys = keyManager.getKeys()
                        }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Key",
                                tint = Color(0xFF8E8E93)
                            )
                        }
                    }
                }
            }
        }
    }
}