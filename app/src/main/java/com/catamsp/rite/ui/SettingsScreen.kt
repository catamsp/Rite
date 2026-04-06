package com.catamsp.rite.ui

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.catamsp.rite.manager.CommandManager
import com.catamsp.rite.ui.components.ScreenTitle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    val commandManager = remember { CommandManager(context) }
    val currentPrefix = remember { commandManager.getTriggerPrefix() }

    var providerType by remember { mutableStateOf(prefs.getString("provider_type", "gemini") ?: "gemini") }
    var providerExpanded by remember { mutableStateOf(false) }
    var selectedModel by remember { mutableStateOf(prefs.getString("model", "gemini-2.5-flash-lite") ?: "gemini-2.5-flash-lite") }
    var modelExpanded by remember { mutableStateOf(false) }
    val geminiModels = listOf("gemini-2.5-flash-lite", "gemini-3-flash-preview", "gemini-3.1-flash-lite-preview")
    var customEndpoint by remember { mutableStateOf(prefs.getString("custom_endpoint", "") ?: "") }
    var customModel by remember { mutableStateOf(prefs.getString("custom_model", "") ?: "") }

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
                            providerType = key; prefs.edit().putString("provider_type", key).apply(); providerExpanded = false
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
                                selectedModel = model; prefs.edit().putString("model", model).apply(); modelExpanded = false
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
                    value = customEndpoint, onValueChange = { customEndpoint = it; prefs.edit().putString("custom_endpoint", it).apply() },
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
                    value = customModel, onValueChange = { customModel = it; prefs.edit().putString("custom_model", it).apply() },
                    placeholder = { Text("gpt-4o, claude-3-haiku, etc.") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), colors = monofieldColors()
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── Trigger Prefix (read-only) ─────────────────────
        SettingsCard(cardBg) {
            SettingsLabel("Trigger Prefix")
            Spacer(modifier = Modifier.height(4.dp))
            Text("The symbol that starts all commands. Set at first launch.", fontSize = 12.sp, color = Color(0xFF8E8E93))
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .padding(8.dp)
                    .height(40.dp)
                    .width(40.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = currentPrefix,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(start = 4.dp)
                )
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
            cmdExample("${currentPrefix}fix", "\"i dont no\" → \"I don't know\"")
            cmdExample("${currentPrefix}formal", "Casual → Professional tone")
            cmdExample("${currentPrefix}emoji", "\"Great job\" → \"Great job! 🎉\"")
            cmdExample("${currentPrefix}shorten", "Long text → Concise version")
            cmdExample("${currentPrefix}expand", "Short text → Detailed version")
            cmdExample("${currentPrefix}casual", "Formal → Friendly tone")
            cmdExample("${currentPrefix}reply", "Paste a message → Get a reply")
            cmdExample("${currentPrefix}sum", "Long text → Summary")
            cmdExample("${currentPrefix}bullet", "Paragraphs → Bullet points")
            cmdExample("${currentPrefix}rewrite", "Same meaning, new words")
            cmdExample("${currentPrefix}remove", "Clean messy text")
            cmdExample("${currentPrefix}tl", "Any language → English")
            cmdExample("${currentPrefix}explain", "Complex → Simple terms")
            cmdExample("${currentPrefix}fancy", "\"hello\" → \"𝒽𝑒𝓁𝓁𝑜\"")
            cmdExample("${currentPrefix}translate:es", "\"Hello\" → \"Hola\"")
            Spacer(modifier = Modifier.height(8.dp))
            cmdSection("Local Commands (Offline)")
            cmdExample("${currentPrefix}cp", "Copy text to clipboard")
            cmdExample("${currentPrefix}ct", "Cut text to clipboard")
            cmdExample("${currentPrefix}pt", "Paste from clipboard")
            cmdExample("${currentPrefix}del", "Clear all text")
            cmdExample("${currentPrefix}upper", "\"hello\" → \"HELLO\"")
            cmdExample("${currentPrefix}lower", "\"HELLO\" → \"hello\"")
            cmdExample("${currentPrefix}title", "\"the wall\" → \"The Wall\"")
            cmdExample("${currentPrefix}date", "Insert date & time")
            cmdExample("${currentPrefix}time", "Insert current time")
            cmdExample("${currentPrefix}count", "Show word & char count")
            cmdExample("${currentPrefix}trim", "Clean extra spaces & lines")
            cmdExample("${currentPrefix}join", "Multi-line → Single line")
            cmdExample("${currentPrefix}split", "Long text → Short lines")
            cmdExample("${currentPrefix}sort", "Sort lines alphabetically")
            cmdExample("${currentPrefix}dedupe", "Remove duplicate lines")
            cmdExample("${currentPrefix}bold", "\"hello\" → \"𝐡𝐞𝐥𝐥𝐨\"")
            cmdExample("${currentPrefix}italic", "\"hello\" → \"ℎ𝑒𝑙𝑙𝑜\"")
            cmdExample("${currentPrefix}rot13", "Rot13 encode/decode")
            cmdExample("${currentPrefix}md5", "Calculate MD5 hash")
            cmdExample("${currentPrefix}upside", "\"hello\" → \"ollǝɥ\"")
            cmdExample("${currentPrefix}mirror", "\"hello\" → \"olleh\"")
            cmdExample("${currentPrefix}reverse", "\"hello world\" → \"world hello\"")
            cmdExample("${currentPrefix}undo", "Restore previous text")
            Spacer(modifier = Modifier.height(8.dp))
            cmdSection("Intent Commands (Custom)")
            cmdExample("${currentPrefix}wp", "app:com.whatsapp → Opens WhatsApp")
            cmdExample("${currentPrefix}call", "tel:+919876543210 → Makes a call")
            cmdExample("${currentPrefix}sms", "sms:+919876543210 → Opens SMS")
            cmdExample("${currentPrefix}mail", "mailto:user@email.com → Opens email")
            cmdExample("${currentPrefix}google", "https://google.com → Opens URL")
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
