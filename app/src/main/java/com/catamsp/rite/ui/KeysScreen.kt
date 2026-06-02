package com.catamsp.rite.ui

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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.catamsp.rite.RiteApp
import com.catamsp.rite.ui.components.ScreenTitle
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
    val isLoading = keysState.isLoading
    val isKeystoreAvailable = keysState.isKeystoreAvailable

    var newKey by remember { mutableStateOf("") }
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(top = 24.dp)
    ) {
        ScreenTitle("API Keys")

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
                            if (settingsState.providerType == "custom" && settingsState.customEndpoint.isNotBlank()) {
                                app.openAIClient.validateKey(trimmedKey, settingsState.customEndpoint, settingsState.customModel)
                            } else {
                                app.geminiClient.validateKey(trimmedKey)
                            }
                        }

                        if (result.isSuccess) {
                            viewModel.addKey(trimmedKey) { addResult ->
                                isTesting = false
                                if (addResult.isSuccess) {
                                    newKey = ""
                                    testResult = "Valid key added!"
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
                KeyItem(
                    maskedKey = key,
                    onDelete = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        viewModel.removeKey(key)
                    }
                )
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
private fun KeyItem(maskedKey: String, onDelete: () -> Unit) {
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
            Text(
                text = "••••••••" + maskedKey.takeLast(6),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
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
