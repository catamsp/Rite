package com.catamsp.rite.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.catamsp.rite.RiteApp
import com.catamsp.rite.model.ProviderType
import com.catamsp.rite.ui.components.ScreenTitle
import com.catamsp.rite.ui.theme.OutlineDim
import com.catamsp.rite.ui.theme.SurfaceTertiary
import com.catamsp.rite.viewmodel.KeysViewModel
import com.catamsp.rite.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun KeysScreen(
    viewModel: KeysViewModel = viewModel(),
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val app = context.applicationContext as RiteApp

    val keysState by viewModel.state.collectAsStateWithLifecycle()
    val keys = keysState.keys
    val keyStatuses = keysState.keyStatuses
    val isLoading = keysState.isLoading
    val isKeystoreAvailable = keysState.isKeystoreAvailable

    var newKey by remember { mutableStateOf("") }
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var keyToDelete by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { }
            .padding(horizontal = 16.dp)
            .padding(top = 24.dp)
    ) {
        ScreenTitle("API Keys")

        KeyCountSummary(keyCount = keys.size)

        if (!isKeystoreAvailable) {
            SecurityWarningCard()
        }

        KeyInputCard(
            newKey = newKey,
            onNewKeyChange = { newKey = it },
            isTesting = isTesting,
            testResult = testResult,
            onAddKey = {
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

                        val settingsState = settingsViewModel.state.value
                        val result = withContext(Dispatchers.IO) {
                            when {
                                trimmedKey.startsWith("gsk_") -> {
                                    app.openAIClient.validateKey(trimmedKey, "https://api.groq.com/openai/v1", settingsState.groqModel)
                                }
                                settingsState.providerType == ProviderType.CUSTOM && settingsState.customEndpoint.isNotBlank() -> {
                                    app.openAIClient.validateKey(trimmedKey, settingsState.customEndpoint, settingsState.customModel)
                                }
                                settingsState.providerType == ProviderType.GROQ -> {
                                    app.openAIClient.validateKey(trimmedKey, "https://api.groq.com/openai/v1", settingsState.groqModel)
                                }
                                else -> {
                                    app.geminiClient.validateKey(trimmedKey)
                                }
                            }
                        }

                        if (result.isSuccess) {
                            viewModel.addKey(trimmedKey) { addResult ->
                                isTesting = false
                                if (addResult.isSuccess) {
                                    newKey = ""
                                    testResult = "Valid key added!"
                                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                                } else {
                                    testResult = addResult.exceptionOrNull()?.message ?: "Failed to store key"
                                }
                            }
                        } else {
                            isTesting = false
                            testResult = result.exceptionOrNull()?.message ?: "Validation failed"
                        }
                    }
                }
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            if (keys.isEmpty() && !isLoading) {
                item(key = "empty") {
                    EmptyKeyState()
                }
            } else if (isLoading) {
                item(key = "loading") {
                    Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
            items(keys, key = { it }) { key ->
                val status = keyStatuses.find { it.maskedKey == key }
                KeyItem(
                    maskedKey = key,
                    isReady = status?.isReady ?: true,
                    remainingMs = status?.remainingMs,
                    onDelete = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        keyToDelete = key
                    }
                )
            }
        }
    }

    keyToDelete?.let { keyValue ->
        AlertDialog(
            onDismissRequest = { keyToDelete = null },
            title = { Text("Delete Key") },
            text = { Text("Are you sure you want to delete this API key?") },
            confirmButton = {
                TextButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.removeKey(keyValue)
                    keyToDelete = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { keyToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SecurityWarningCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Security Warning",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Your device's security chip is unavailable. API keys cannot be stored safely on this device.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    Spacer(modifier = Modifier.height(12.dp))
}

@Composable
private fun KeyInputCard(
    newKey: String,
    onNewKeyChange: (String) -> Unit,
    isTesting: Boolean,
    testResult: String?,
    onAddKey: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = newKey,
                onValueChange = onNewKeyChange,
                label = { Text("API Key") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = onAddKey,
                    enabled = newKey.isNotBlank() && !isTesting,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSurface)
                ) {
                    Text(if (isTesting) "Testing..." else "Add Key", color = MaterialTheme.colorScheme.background)
                }
            }
            if (testResult != null) {
                Text(
                    text = testResult,
                    color = if (testResult.startsWith("Valid")) MaterialTheme.colorScheme.onSurface else SurfaceTertiary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyKeyState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "No keys yet",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "Add one above to get started",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun KeyCountSummary(keyCount: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = when (keyCount) {
                    0 -> "No API Keys"
                    1 -> "1 API Key"
                    else -> "$keyCount API Keys"
                },
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (keyCount == 0) "Add a key below to get started" else "Add more keys for failover",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    Spacer(modifier = Modifier.height(12.dp))
}

@Composable
private fun KeyItem(maskedKey: String, isReady: Boolean, remainingMs: Long?, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            if (isReady) MaterialTheme.colorScheme.onSurface else OutlineDim,
                            shape = RoundedCornerShape(4.dp)
                        )
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "\u2022\u2022\u2022\u2022\u2022\u2022\u2022\u2022" + maskedKey.takeLast(6),
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = if (isReady) "Active" else "Rate limited (${((remainingMs ?: 0L) / 1000L) + 1}s)",
                fontSize = 12.sp,
                color = if (isReady) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 8.dp)
            )
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete Key",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
