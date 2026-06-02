package com.catamsp.rite.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
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
import com.catamsp.rite.ui.components.ScreenTitle
import com.catamsp.rite.ui.theme.SurfaceTertiary
import com.catamsp.rite.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val haptic = LocalHapticFeedback.current

    val settingsState by viewModel.state.collectAsStateWithLifecycle()
    val providerType = settingsState.providerType
    val selectedModel = settingsState.selectedModel
    val triggerPrefix = settingsState.triggerPrefix

    var providerExpanded by remember { mutableStateOf(false) }
    var modelExpanded by remember { mutableStateOf(false) }
    val geminiModels = remember { listOf("gemini-2.5-flash-lite", "gemini-3.5-flash", "gemini-3.1-flash-lite") }

    var localEndpoint by remember { mutableStateOf(settingsState.customEndpoint) }
    var localModel by remember { mutableStateOf(settingsState.customModel) }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.saveCustomEndpoint(localEndpoint)
            viewModel.saveCustomModel(localModel)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
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

        if (providerType == "gemini") {
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
        } else {
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

        item(key = "spacer3") { Spacer(modifier = Modifier.height(8.dp)) }

        item(key = "triggers") {
            TriggerPrefixSection(triggerPrefix = triggerPrefix)
        }

        item(key = "spacer4") { Spacer(modifier = Modifier.height(8.dp)) }

        item(key = "modes") {
            ModesSection()
        }

        item(key = "spacer5") { Spacer(modifier = Modifier.height(12.dp)) }

        item(key = "ref_ai_header") {
            CommandReferenceHeader(title = "AI Commands")
        }
        item(key = "ref_ai_1") {
            CommandReferenceChunk(
                commands = listOf(
                    "${triggerPrefix}fix" to "\"i dont no\" → \"I don't know\"",
                    "${triggerPrefix}formal" to "Casual → Professional tone",
                    "${triggerPrefix}emoji" to "\"Great job\" → \"Great job! 🎉\"",
                    "${triggerPrefix}shorten" to "Long text → Concise version",
                    "${triggerPrefix}expand" to "Short text → Detailed version",
                    "${triggerPrefix}casual" to "Formal → Friendly tone",
                    "${triggerPrefix}reply" to "Paste a message → Get a reply",
                    "${triggerPrefix}sum" to "Long text → Summary",
                    "${triggerPrefix}bullet" to "Paragraphs → Bullet points",
                )
            )
        }
        item(key = "ref_ai_2") {
            CommandReferenceChunk(
                commands = listOf(
                    "${triggerPrefix}rewrite" to "Same meaning, new words",
                    "${triggerPrefix}remove" to "Clean messy text",
                    "${triggerPrefix}tl" to "Any language → English",
                    "${triggerPrefix}explain" to "Complex → Simple terms",
                    "${triggerPrefix}fancy" to "\"hello\" → \"𝒽𝑒𝓁𝓁𝑜\"",
                    "${triggerPrefix}translate:es" to "\"Hello\" → \"Hola\"",
                )
            )
        }
        item(key = "ref_local_header") {
            CommandReferenceHeader(title = "Local Commands (Offline)")
        }
        item(key = "ref_local_1") {
            CommandReferenceChunk(
                commands = listOf(
                    "${triggerPrefix}cp" to "Copy text to clipboard",
                    "${triggerPrefix}ct" to "Cut text to clipboard",
                    "${triggerPrefix}pt" to "Paste from clipboard",
                    "${triggerPrefix}del" to "Clear all text",
                    "${triggerPrefix}upper" to "\"hello\" → \"HELLO\"",
                    "${triggerPrefix}lower" to "\"HELLO\" → \"hello\"",
                    "${triggerPrefix}title" to "\"the wall\" → \"The Wall\"",
                    "${triggerPrefix}date" to "Insert date & time",
                    "${triggerPrefix}time" to "Insert current time",
                )
            )
        }
        item(key = "ref_local_2") {
            CommandReferenceChunk(
                commands = listOf(
                    "${triggerPrefix}count" to "Show word & char count",
                    "${triggerPrefix}trim" to "Clean extra spaces & lines",
                    "${triggerPrefix}join" to "Multi-line → Single line",
                    "${triggerPrefix}split" to "Long text → Short lines",
                    "${triggerPrefix}sort" to "Sort lines alphabetically",
                    "${triggerPrefix}dedupe" to "Remove duplicate lines",
                    "${triggerPrefix}bold" to "\"hello\" → \"𝐡𝐞𝐥𝐥𝐨\"",
                    "${triggerPrefix}italic" to "\"hello\" → \"ℎ𝑒𝑙𝑙𝑜\"",
                    "${triggerPrefix}rot13" to "Rot13 encode/decode",
                )
            )
        }
        item(key = "ref_local_3") {
            CommandReferenceChunk(
                commands = listOf(
                    "${triggerPrefix}md5" to "Calculate MD5 hash",
                    "${triggerPrefix}upside" to "\"hello\" → \"ollǝɥ\"",
                    "${triggerPrefix}mirror" to "\"hello\" → \"olleh\"",
                    "${triggerPrefix}reverse" to "\"hello world\" → \"world hello\"",
                    "${triggerPrefix}undo" to "Restore previous text",
                )
            )
        }
        item(key = "ref_intent_header") {
            CommandReferenceHeader(title = "Intent Commands (Custom)")
        }
        item(key = "ref_intent") {
            CommandReferenceChunk(
                commands = listOf(
                    "${triggerPrefix}wp" to "app:com.whatsapp → Opens WhatsApp",
                    "${triggerPrefix}call" to "tel:+919876543210 → Makes a call",
                    "${triggerPrefix}sms" to "sms:+919876543210 → Opens SMS",
                    "${triggerPrefix}mail" to "mailto:user@email.com → Opens email",
                    "${triggerPrefix}google" to "https://google.com → Opens URL",
                )
            )
        }

        item(key = "spacer6") { Spacer(modifier = Modifier.height(12.dp)) }

        item(key = "about") { AboutSection() }
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
                value = if (providerType == "gemini") "Google Gemini" else "Custom (OpenAI Compatible)",
                onValueChange = {}, readOnly = true,
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )
            ExposedDropdownMenu(expanded = providerExpanded, onDismissRequest = { onProviderExpandedChange(false) }) {
                listOf("gemini" to "Google Gemini", "custom" to "Custom (OpenAI Compatible)").forEach { (key, label) ->
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
private fun TriggerPrefixSection(triggerPrefix: String) {
    SettingsCard {
        SettingsLabel("Trigger Prefixes")
        Spacer(modifier = Modifier.height(4.dp))
        Text("The symbols that start commands. Base prefix is set at first launch.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(
                triggerPrefix to "Replace",
                "!" to "Append",
                "+" to "Prepend"
            ).forEach { (prefix, mode) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.outlineVariant)
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = prefix,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = mode, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ModesSection() {
    SettingsCard {
        SettingsLabel("Append & Prepend Modes")
        Spacer(modifier = Modifier.height(8.dp))
        appendPrependRow("Replace", "?fix", "Result replaces your text entirely")
        Spacer(modifier = Modifier.height(6.dp))
        appendPrependRow("Append", "!fix", "Result is added after your text")
        Spacer(modifier = Modifier.height(6.dp))
        appendPrependRow("Prepend", "+fix", "Result is added before your text")
    }
}

@Composable
private fun CommandReferenceHeader(title: String) {
    SettingsCard {
        SettingsLabel(title)
    }
}

@Composable
private fun CommandReferenceChunk(commands: List<Pair<String, String>>) {
    SettingsCard {
        commands.forEach { (trigger, desc) ->
            cmdExample(trigger, desc)
        }
    }
}

@Composable
private fun AboutSection() {
    SettingsCard {
        SettingsLabel("About")
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Rite is a system-wide AI text assistant for Android. Type a trigger at the end of any text in any app and it transforms instantly. Works via Accessibility Service — no copy-paste, no app switching.",
            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text("Version 2.0 · Built by catamsp · Vibe Coded", fontSize = 12.sp, color = SurfaceTertiary)
        Text("Forked from SwiftSlate by Musheer Alam", fontSize = 12.sp, color = SurfaceTertiary)
    }
}

@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

@Composable
private fun SettingsLabel(text: String) {
    Text(text = text, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
}

@Composable
private fun appendPrependRow(mode: String, example: String, desc: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Text(text = mode, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.width(60.dp))
        Text(text = example, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(60.dp))
        Text(text = desc, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun cmdExample(trigger: String, desc: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = trigger, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.width(130.dp))
        Text(text = desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
