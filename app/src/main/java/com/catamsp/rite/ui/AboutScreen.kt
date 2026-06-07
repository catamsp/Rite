package com.catamsp.rite.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.catamsp.rite.ui.components.ScreenTitle
import com.catamsp.rite.ui.theme.SurfaceTertiary
import com.catamsp.rite.viewmodel.SettingsViewModel

@Composable
fun AboutScreen(viewModel: SettingsViewModel = viewModel()) {
    val settingsState by viewModel.state.collectAsStateWithLifecycle()
    val triggerPrefix = settingsState.triggerPrefix

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp)
    ) {
        item(key = "title") { ScreenTitle("About") }

        item(key = "about") { AboutSection() }

        item(key = "spacer0") { Spacer(modifier = Modifier.height(12.dp)) }

        item(key = "triggers") { TriggerPrefixSection(triggerPrefix = triggerPrefix) }

        item(key = "spacer1") { Spacer(modifier = Modifier.height(12.dp)) }

        item(key = "ref_ai_header") {
            CommandReferenceHeader(title = "AI Commands")
        }
        item(key = "ref_ai_1") {
            CommandReferenceChunk(
                commands = listOf(
                    "${triggerPrefix}fix" to "\"i dont no\" → \"I don't know\"",
                    "${triggerPrefix}formal" to "Casual → Professional tone",
                    "${triggerPrefix}emoji" to "\"Great job\" → \"Great job! \uD83C\uDF89\"",
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
                    "${triggerPrefix}fancy" to "\"hello\" → \"\uD83D\uDCD3\u0435\u043B\u043B\u043E\"",
                    "${triggerPrefix}translate:es" to "\"Hello\" → \"Hola\"",
                )
            )
        }
        item(key = "ref_ctx_header") {
            CommandReferenceHeader(title = "Context-Aware Commands")
        }
        item(key = "ref_ctx_1") {
            CommandReferenceChunk(
                commands = listOf(
                    "${triggerPrefix}freply" to "Full screen context reply",
                    "${triggerPrefix}qreply" to "Quick recent context reply",
                    "${triggerPrefix}sreply" to "Screenshot-based reply (Android 11+)",
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
                    "${triggerPrefix}bold" to "\u201Chello\u201D \u2192 \u201C𝐡𝐞𝐥𝐥𝐨\u201D",
                    "${triggerPrefix}italic" to "\u201Chello\u201D \u2192 \u201Cℎ𝑒𝑙𝑙𝑜\u201D",
                    "${triggerPrefix}rot13" to "Rot13 encode/decode",
                )
            )
        }
        item(key = "ref_local_3") {
            CommandReferenceChunk(
                commands = listOf(
                    "${triggerPrefix}md5" to "Calculate MD5 hash",
                    "${triggerPrefix}upside" to "\"hello\" → \"\u0285\u01DD\u026C\u0131\"",
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
    }
}

@Composable
private fun AboutSection() {
    val uriHandler = LocalUriHandler.current
    AboutCard {
        SettingsLabel("About")
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Rite is a system-wide AI text assistant for Android. Type a trigger at the end of any text in any app and it transforms instantly. Works via Accessibility Service — no copy-paste, no app switching.",
            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = buildAnnotatedString {
                append("Version 2.0.6 · Built by ")
                withStyle(SpanStyle(fontWeight = FontWeight.Medium, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline)) {
                    append("catamsp")
                }
            },
            fontSize = 12.sp, color = SurfaceTertiary,
            modifier = Modifier.clickable { uriHandler.openUri("https://github.com/catamsp/Rite") }
        )
        Text(
            text = buildAnnotatedString {
                append("Forked from ")
                withStyle(SpanStyle(fontWeight = FontWeight.Medium, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline)) {
                    append("SwiftSlate")
                }
                append(" by Musheer Alam")
            },
            fontSize = 12.sp, color = SurfaceTertiary,
            modifier = Modifier.clickable { uriHandler.openUri("https://github.com/Musheer360/SwiftSlate") }
        )
    }
}

@Composable
private fun CommandReferenceHeader(title: String) {
    AboutCard {
        SettingsLabel(title)
    }
}

@Composable
private fun CommandReferenceChunk(commands: List<Pair<String, String>>) {
    AboutCard {
        commands.forEach { (trigger, desc) ->
            CmdExample(trigger, desc)
        }
    }
}

@Composable
private fun AboutCard(content: @Composable ColumnScope.() -> Unit) {
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
private fun CmdExample(trigger: String, desc: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = trigger, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.width(130.dp))
        Text(text = desc, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TriggerPrefixSection(triggerPrefix: String) {
    AboutCard {
        SettingsLabel("Modes")
        Spacer(modifier = Modifier.height(4.dp))
        Text("The symbols that start commands. Base prefix is set at first launch.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(12.dp))
        appendPrependRow("Replace", "${triggerPrefix}fix", "Result replaces your text entirely")
        Spacer(modifier = Modifier.height(6.dp))
        appendPrependRow("Append", "!fix", "Result is added after your text")
        Spacer(modifier = Modifier.height(6.dp))
        appendPrependRow("Prepend", "+fix", "Result is added before your text")
    }
}

@Composable
private fun appendPrependRow(mode: String, example: String, desc: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
        Text(text = mode, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.width(60.dp))
        Text(text = example, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(60.dp))
        Text(text = desc, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
