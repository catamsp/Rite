package com.catamsp.rite.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.catamsp.rite.model.ProviderType
import com.catamsp.rite.ui.components.PressableCard
import com.catamsp.rite.ui.components.ScreenTitle
import com.catamsp.rite.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val haptic = LocalHapticFeedback.current

    val settingsState by viewModel.state.collectAsStateWithLifecycle()
    val providerType = settingsState.providerType
    val selectedModel = settingsState.selectedModel
    val groqModel = settingsState.groqModel
    val triggerPrefix = settingsState.triggerPrefix
    val temperature = settingsState.temperature
    val screenContextEnabled = settingsState.screenContextEnabled

    var providerExpanded by remember { mutableStateOf(false) }
    var modelExpanded by remember { mutableStateOf(false) }
    var groqModelExpanded by remember { mutableStateOf(false) }
    val geminiModels = remember { listOf("gemini-2.5-flash-lite", "gemini-2.5-flash", "gemini-2.5-pro", "gemini-3.1-flash-lite", "gemini-3.5-flash") }
    val groqModels = remember { listOf("llama-3.3-70b-versatile", "llama-3.1-8b-instant", "openai/gpt-oss-120b", "openai/gpt-oss-20b", "meta-llama/llama-4-scout-17b-16e-instruct") }

    var localEndpoint by remember { mutableStateOf(settingsState.customEndpoint) }
    var localModel by remember { mutableStateOf(settingsState.customModel) }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.saveCustomEndpoint(localEndpoint)
            viewModel.saveCustomModel(localModel)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().graphicsLayer { }.padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp)
    ) {
        item(key = "title") { ScreenTitle("Settings") }

        item(key = "provider") {
            ProviderSection(
                providerType = providerType,
                providerExpanded = providerExpanded,
                onProviderExpandedChange = { providerExpanded = it },
                onProviderSelected = { key ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.updateProviderType(key)
                    providerExpanded = false
                }
            )
        }

        item(key = "spacer1") { Spacer(modifier = Modifier.height(8.dp)) }

        when (providerType) {
            ProviderType.GEMINI -> {
                item(key = "model") {
                    GeminiModelSection(
                        selectedModel = selectedModel,
                        modelExpanded = modelExpanded,
                        onModelExpandedChange = { modelExpanded = it },
                        onModelSelected = { model ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.updateSelectedModel(model)
                            modelExpanded = false
                        },
                        geminiModels = geminiModels
                    )
                }
            }
            ProviderType.GROQ -> {
                item(key = "groqModel") {
                    GroqModelSection(
                        groqModel = groqModel,
                        groqModelExpanded = groqModelExpanded,
                        onGroqModelExpandedChange = { groqModelExpanded = it },
                        onGroqModelSelected = { model ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.updateGroqModel(model)
                            groqModelExpanded = false
                        },
                        groqModels = groqModels
                    )
                }
            }
            else -> {
                item(key = "endpoint") {
                    CustomEndpointSection(
                        endpoint = localEndpoint,
                        onEndpointChange = { localEndpoint = it }
                    )
                }
                item(key = "spacer2") { Spacer(modifier = Modifier.height(8.dp)) }
                item(key = "customModel") {
                    CustomModelSection(
                        model = localModel,
                        onModelChange = { localModel = it }
                    )
                }
            }
        }

        item(key = "spacer3") { Spacer(modifier = Modifier.height(8.dp)) }

        item(key = "temperature") {
            CreativitySection(
                temperature = temperature,
                onCreativityChange = { temp ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.updateTemperature(temp)
                }
            )
        }

        item(key = "spacer4") { Spacer(modifier = Modifier.height(8.dp)) }

        item(key = "screenContext") {
            ScreenContextSection(
                enabled = screenContextEnabled,
                onToggle = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.toggleScreenContext()
                }
            )
        }

        item(key = "spacer5") { Spacer(modifier = Modifier.height(8.dp))         }

    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderSection(
    providerType: String,
    providerExpanded: Boolean,
    onProviderExpandedChange: (Boolean) -> Unit,
    onProviderSelected: (String) -> Unit
) {
    SettingsCard {
        SettingsLabel("Provider")
        Spacer(modifier = Modifier.height(8.dp))
        ExposedDropdownMenuBox(expanded = providerExpanded, onExpandedChange = onProviderExpandedChange) {
            OutlinedTextField(
                value = when (providerType) {
                    ProviderType.GEMINI -> "Google Gemini"
                    ProviderType.GROQ -> "Groq"
                    else -> "Custom (OpenAI Compatible)"
                },
                onValueChange = {}, readOnly = true,
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )
            ExposedDropdownMenu(expanded = providerExpanded, onDismissRequest = { onProviderExpandedChange(false) }) {
                listOf(
                    ProviderType.GEMINI to "Google Gemini",
                    ProviderType.GROQ to "Groq",
                    ProviderType.CUSTOM to "Custom (OpenAI Compatible)"
                ).forEach { (key, label) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = { onProviderSelected(key) })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GeminiModelSection(
    selectedModel: String,
    modelExpanded: Boolean,
    onModelExpandedChange: (Boolean) -> Unit,
    onModelSelected: (String) -> Unit,
    geminiModels: List<String>
) {
    SettingsCard {
        SettingsLabel("Model")
        Spacer(modifier = Modifier.height(8.dp))
        ExposedDropdownMenuBox(expanded = modelExpanded, onExpandedChange = onModelExpandedChange) {
            OutlinedTextField(
                value = selectedModel, onValueChange = {}, readOnly = true,
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )
            ExposedDropdownMenu(expanded = modelExpanded, onDismissRequest = { onModelExpandedChange(false) }) {
                geminiModels.forEach { model ->
                    DropdownMenuItem(text = { Text(model) }, onClick = { onModelSelected(model) })
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroqModelSection(
    groqModel: String,
    groqModelExpanded: Boolean,
    onGroqModelExpandedChange: (Boolean) -> Unit,
    onGroqModelSelected: (String) -> Unit,
    groqModels: List<String>
) {
    SettingsCard {
        SettingsLabel("Model")
        Spacer(modifier = Modifier.height(8.dp))
        ExposedDropdownMenuBox(expanded = groqModelExpanded, onExpandedChange = onGroqModelExpandedChange) {
            OutlinedTextField(
                value = groqModel, onValueChange = {}, readOnly = true,
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )
            ExposedDropdownMenu(expanded = groqModelExpanded, onDismissRequest = { onGroqModelExpandedChange(false) }) {
                groqModels.forEach { model ->
                    DropdownMenuItem(text = { Text(model) }, onClick = { onGroqModelSelected(model) })
                }
            }
        }
    }
}

@Composable
private fun CustomEndpointSection(endpoint: String, onEndpointChange: (String) -> Unit) {
    SettingsCard {
        SettingsLabel("Endpoint")
        Spacer(modifier = Modifier.height(4.dp))
        Text("Base URL of the OpenAI-compatible API", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = endpoint,
            onValueChange = onEndpointChange,
            placeholder = { Text("https://api.example.com/v1") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.onSurface,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
            )
        )
    }
}

@Composable
private fun CustomModelSection(model: String, onModelChange: (String) -> Unit) {
    SettingsCard {
        SettingsLabel("Model")
        Spacer(modifier = Modifier.height(4.dp))
        Text("Model identifier from your provider", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = model,
            onValueChange = onModelChange,
            placeholder = { Text("gpt-4o, claude-3-haiku, etc.") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.onSurface,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
            )
        )
    }
}

@Composable
private fun CreativitySection(temperature: Float, onCreativityChange: (Float) -> Unit) {
    SettingsCard {
        SettingsLabel("Creativity")
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Lower = focused, predictable · Higher = creative, varied",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(10.dp))
        Slider(
            value = temperature,
            onValueChange = onCreativityChange,
            valueRange = 0f..2f,
            steps = 19,
            modifier = Modifier.fillMaxWidth().height(26.dp),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.outline
            )
        )
    }
}

@Composable
private fun ScreenContextSection(enabled: Boolean, onToggle: () -> Unit) {
    SettingsCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Screen Context",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Allow ?freply and ?qreply to read screen text",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = { onToggle() }
            )
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    PressableCard(content = content)
}

@Composable
private fun SettingsLabel(text: String) {
    Text(text = text, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
}
