package com.catamsp.rite.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.catamsp.rite.RiteApp
import com.catamsp.rite.model.ProviderType
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

    var showAddDialog by remember { mutableStateOf(false) }
    var keyToDelete by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { }
            .padding(horizontal = 16.dp)
            .padding(top = 24.dp)
    ) {
        KeysHeader(
            onAdd = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                showAddDialog = true
            }
        )

        if (!isKeystoreAvailable) {
            SecurityWarningCard()
        }

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

    if (showAddDialog) {
        AddKeyDialog(
            existingKeys = keys,
            viewModel = viewModel,
            settingsViewModel = settingsViewModel,
            onDismiss = { showAddDialog = false },
            onKeyAdded = { showAddDialog = false }
        )
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

private fun getProviderFromKey(key: String): String {
    return when {
        key.startsWith("AIza") -> "Gemini"
        key.startsWith("gsk_") -> "Groq"
        key.startsWith("csk-") -> "Cerebras"
        key.startsWith("kilo_") || (key.startsWith("eyJ") && key.contains(".")) -> "Kilo"
        key.startsWith("sk-") -> "OpenAI"
        else -> "Other"
    }
}

@Composable
private fun KeysHeader(onAdd: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "API Keys",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        IconButton(onClick = onAdd) {
            Icon(Icons.Default.Add, contentDescription = "Add Key", tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun AddKeyDialog(
    existingKeys: List<String>,
    viewModel: KeysViewModel,
    settingsViewModel: SettingsViewModel,
    onDismiss: () -> Unit,
    onKeyAdded: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val app = context.applicationContext as RiteApp
    val scope = rememberCoroutineScope()

    var newKey by remember { mutableStateOf("") }
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color.White, RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.outline),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Add API Key", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = newKey,
                    onValueChange = { newKey = it; testResult = null },
                    label = { Text("API Key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )
                if (testResult != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = testResult!!,
                        color = if (testResult!!.startsWith("Valid")) MaterialTheme.colorScheme.onSurface else SurfaceTertiary,
                        fontSize = 13.sp
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
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
                                    if (existingKeys.contains(trimmedKey)) {
                                        isTesting = false
                                        testResult = "This key has already been added"
                                        return@launch
                                    }

                                    val settingsState = settingsViewModel.state.value
                                    val result = withContext(Dispatchers.IO) {
                                        when {
                                            trimmedKey.startsWith("AIza") -> {
                                                app.geminiClient.validateKey(trimmedKey)
                                            }
                                            trimmedKey.startsWith("gsk_") -> {
                                                app.openAIClient.validateKey(trimmedKey, "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile")
                                            }
                                            trimmedKey.startsWith("csk-") -> {
                                                app.openAIClient.validateKey(trimmedKey, "https://api.cerebras.ai/v1", "gpt-oss-120b")
                                            }
                                            trimmedKey.startsWith("kilo_") || (trimmedKey.startsWith("eyJ") && trimmedKey.contains(".")) -> {
                                                app.openAIClient.validateKey(trimmedKey, "https://api.kilo.ai/api/gateway", "anthropic/claude-sonnet-4.5")
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
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                                clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                                                onKeyAdded()
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
                        },
                        enabled = newKey.isNotBlank() && !isTesting,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.onSurface,
                            disabledContainerColor = MaterialTheme.colorScheme.onSurface,
                            disabledContentColor = MaterialTheme.colorScheme.background
                        )
                    ) {
                        Text(if (isTesting) "Testing..." else "Add Key", color = MaterialTheme.colorScheme.background)
                    }
                }
            }
        }
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
            text = "Tap + to add one",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun KeyItem(maskedKey: String, isReady: Boolean, remainingMs: Long?, onDelete: () -> Unit) {
    val provider = remember(maskedKey) { getProviderFromKey(maskedKey) }
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
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            if (isReady) MaterialTheme.colorScheme.onSurface else OutlineDim,
                            shape = RoundedCornerShape(4.dp)
                        )
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.outlineVariant)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = provider,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
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
