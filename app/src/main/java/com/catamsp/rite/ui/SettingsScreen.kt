package com.catamsp.rite.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.catamsp.rite.ui.components.ScreenTitle
import com.catamsp.rite.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val haptic = LocalHapticFeedback.current
    
    val providerType by viewModel.providerType.collectAsState()
    val selectedModel by viewModel.selectedModel.collectAsState()
    val customEndpoint by viewModel.customEndpoint.collectAsState()
    val customModel by viewModel.customModel.collectAsState()
    val triggerPrefix by viewModel.triggerPrefix.collectAsState()
    
    var providerExpanded by remember { mutableStateOf(false) }
    var modelExpanded by remember { mutableStateOf(false) }
    val geminiModels = listOf("gemini-2.5-flash-lite", "gemini-3.5-flash", "gemini-3.1-flash-lite")

    val cardBg = Color(0xFF1C1C1E)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(top = 24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        ScreenTitle("Settings")

        // ── Provider ────────────────────────────────────────
        SettingsCard(cardBg) {
            SettingsLabel("Provider")
            Spacer(modifier = Modifier.height(8.dp))
            ExposedDropdownMenuBox(expanded = providerExpanded, onExpandedChange = { providerExpanded = !providerExpanded }) {
                OutlinedTextField(
                    value = if (providerType == "gemini") "Google Gemini" else "Custom (OpenAI Compatible)",
                    onValueChange = {}, readOnly = true,
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                    colors = monofieldColors()
                )
                ExposedDropdownMenu(expanded = providerExpanded, onDismissRequest = { providerExpanded = false }) {
                    listOf("gemini" to "Google Gemini", "custom" to "Custom (OpenAI Compatible)").forEach { (key, label) ->
                        DropdownMenuItem(text = { Text(label) }, onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.updateProviderType(key)
                            providerExpanded = false
                        })
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── Model ───────────────────────────────────────────
        if (providerType == "gemini") {
            SettingsCard(cardBg) {
                SettingsLabel("Model")
                Spacer(modifier = Modifier.height(8.dp))
                ExposedDropdownMenuBox(expanded = modelExpanded, onExpandedChange = { modelExpanded = !modelExpanded }) {
                    OutlinedTextField(
                        value = selectedModel, onValueChange = {}, readOnly = true,
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                        colors = monofieldColors()
                    )
                    ExposedDropdownMenu(expanded = modelExpanded, onDismissRequest = { modelExpanded = false }) {
                        geminiModels.forEach { model ->
                            DropdownMenuItem(text = { Text(model) }, onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                viewModel.updateSelectedModel(model)
                                modelExpanded = false
                            })
                        }
                    }
                }
            }
        } else {
            SettingsCard(cardBg) {
                SettingsLabel("Endpoint")
                Spacer(modifier = Modifier.height(4.dp))
                Text("Base URL of the OpenAI-compatible API", fontSize = 12.sp, color = Color(0xFF8E8E93))
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = customEndpoint, onValueChange = { viewModel.updateCustomEndpoint(it) },
                    placeholder = { Text("https://api.example.com/v1") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), colors = monofieldColors()
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            SettingsCard(cardBg) {
                SettingsLabel("Model")
                Spacer(modifier = Modifier.height(4.dp))
                Text("Model identifier from your provider", fontSize = 12.sp, color = Color(0xFF8E8E93))
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = customModel, onValueChange = { viewModel.updateCustomModel(it) },
                    placeholder = { Text("gpt-4o, claude-3-haiku, etc.") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), colors = monofieldColors()
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── Trigger Prefix (read-only) ─────────────────────
        SettingsCard(cardBg) {
            SettingsLabel("Trigger Prefixes")
            Spacer(modifier = Modifier.height(4.dp))
            Text("The symbols that start commands. Base prefix is set at first launch.", fontSize = 12.sp, color = Color(0xFF8E8E93))
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
                                .background(Color(0xFF3A3A3C))
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = prefix,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = mode, fontSize = 11.sp, color = Color(0xFF8E8E93))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── Append & Prepend ───────────────────────────────
        SettingsCard(cardBg) {
            SettingsLabel("Append & Prepend Modes")
            Spacer(modifier = Modifier.height(8.dp))
            appendPrependRow("Replace", "?fix", "Result replaces your text entirely", cardBg)
            Spacer(modifier = Modifier.height(6.dp))
            appendPrependRow("Append", "!fix", "Result is added after your text", cardBg)
            Spacer(modifier = Modifier.height(6.dp))
            appendPrependRow("Prepend", "+fix", "Result is added before your text", cardBg)
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Command Reference ──────────────────────────────
        SettingsCard(cardBg) {
            SettingsLabel("Command Reference")
            Spacer(modifier = Modifier.height(10.dp))
            cmdSection("AI Commands")
            cmdExample("${triggerPrefix}fix", "\"i dont no\" → \"I don't know\"")
            cmdExample("${triggerPrefix}formal", "Casual → Professional tone")
            cmdExample("${triggerPrefix}emoji", "\"Great job\" → \"Great job! 🎉\"")
            cmdExample("${triggerPrefix}shorten", "Long text → Concise version")
            cmdExample("${triggerPrefix}expand", "Short text → Detailed version")
            cmdExample("${triggerPrefix}casual", "Formal → Friendly tone")
            cmdExample("${triggerPrefix}reply", "Paste a message → Get a reply")
            cmdExample("${triggerPrefix}sum", "Long text → Summary")
            cmdExample("${triggerPrefix}bullet", "Paragraphs → Bullet points")
            cmdExample("${triggerPrefix}rewrite", "Same meaning, new words")
            cmdExample("${triggerPrefix}remove", "Clean messy text")
            cmdExample("${triggerPrefix}tl", "Any language → English")
            cmdExample("${triggerPrefix}explain", "Complex → Simple terms")
            cmdExample("${triggerPrefix}fancy", "\"hello\" → \"𝒽𝑒𝓁𝓁𝑜\"")
            cmdExample("${triggerPrefix}translate:es", "\"Hello\" → \"Hola\"")
            Spacer(modifier = Modifier.height(8.dp))
            cmdSection("Local Commands (Offline)")
            cmdExample("${triggerPrefix}cp", "Copy text to clipboard")
            cmdExample("${triggerPrefix}ct", "Cut text to clipboard")
            cmdExample("${triggerPrefix}pt", "Paste from clipboard")
            cmdExample("${triggerPrefix}del", "Clear all text")
            cmdExample("${triggerPrefix}upper", "\"hello\" → \"HELLO\"")
            cmdExample("${triggerPrefix}lower", "\"HELLO\" → \"hello\"")
            cmdExample("${triggerPrefix}title", "\"the wall\" → \"The Wall\"")
            cmdExample("${triggerPrefix}date", "Insert date & time")
            cmdExample("${triggerPrefix}time", "Insert current time")
            cmdExample("${triggerPrefix}count", "Show word & char count")
            cmdExample("${triggerPrefix}trim", "Clean extra spaces & lines")
            cmdExample("${triggerPrefix}join", "Multi-line → Single line")
            cmdExample("${triggerPrefix}split", "Long text → Short lines")
            cmdExample("${triggerPrefix}sort", "Sort lines alphabetically")
            cmdExample("${triggerPrefix}dedupe", "Remove duplicate lines")
            cmdExample("${triggerPrefix}bold", "\"hello\" → \"𝐡𝐞𝐥𝐥𝐨\"")
            cmdExample("${triggerPrefix}italic", "\"hello\" → \"ℎ𝑒𝑙𝑙𝑜\"")
            cmdExample("${triggerPrefix}rot13", "Rot13 encode/decode")
            cmdExample("${triggerPrefix}md5", "Calculate MD5 hash")
            cmdExample("${triggerPrefix}upside", "\"hello\" → \"ollǝɥ\"")
            cmdExample("${triggerPrefix}mirror", "\"hello\" → \"olleh\"")
            cmdExample("${triggerPrefix}reverse", "\"hello world\" → \"world hello\"")
            cmdExample("${triggerPrefix}undo", "Restore previous text")
            Spacer(modifier = Modifier.height(8.dp))
            cmdSection("Intent Commands (Custom)")
            cmdExample("${triggerPrefix}wp", "app:com.whatsapp → Opens WhatsApp")
            cmdExample("${triggerPrefix}call", "tel:+919876543210 → Makes a call")
            cmdExample("${triggerPrefix}sms", "sms:+919876543210 → Opens SMS")
            cmdExample("${triggerPrefix}mail", "mailto:user@email.com → Opens email")
            cmdExample("${triggerPrefix}google", "https://google.com → Opens URL")
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── About ──────────────────────────────────────────
        SettingsCard(cardBg) {
            SettingsLabel("About")
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Rite is a system-wide AI text assistant for Android. Type a trigger at the end of any text in any app and it transforms instantly. Works via Accessibility Service — no copy-paste, no app switching.",
                fontSize = 13.sp, color = Color(0xFF8E8E93), lineHeight = 18.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("Version 2.0 · Built by catamsp · Vibe Coded", fontSize = 12.sp, color = Color(0xFF6E6E73))
            Text("Forked from SwiftSlate by Musheer Alam", fontSize = 12.sp, color = Color(0xFF6E6E73))
        }
    }
}

// ── Helpers ──────────────────────────────────────────────────

@Composable
private fun monofieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Color.White,
    unfocusedBorderColor = Color(0xFF3A3A3C)
)

@Composable
private fun SettingsCard(cardBg: Color, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardBg),
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
    Text(text = text, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
}

@Composable
private fun appendPrependRow(mode: String, example: String, desc: String, cardBg: Color) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Text(text = mode, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.width(60.dp))
        Text(text = example, fontSize = 13.sp, color = Color(0xFF8E8E93), modifier = Modifier.width(60.dp))
        Text(text = desc, fontSize = 13.sp, color = Color(0xFF8E8E93))
    }
}

@Composable
private fun cmdSection(title: String) {
    Text(text = title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White)
    Spacer(modifier = Modifier.height(6.dp))
}

@Composable
private fun cmdExample(trigger: String, desc: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = trigger, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.White, modifier = Modifier.width(130.dp))
        Text(text = desc, fontSize = 12.sp, color = Color(0xFF8E8E93))
    }
}
