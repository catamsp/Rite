package com.catamsp.rite.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.catamsp.rite.manager.CommandManager
import com.catamsp.rite.model.Command
import com.catamsp.rite.ui.components.ScreenTitle

@Composable
fun CommandsScreen() {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val commandManager = remember { CommandManager(context) }
    var allCommands by remember { mutableStateOf(commandManager.getCommands()) }
    
    // Dialog state: null = closed, Pair = (isEdit, oldTrigger/null)
    var dialogState by remember { mutableStateOf<Pair<Boolean, String?>?>(null) }

    // Filter state
    var selectedFilter by remember { mutableStateOf("All") }
    val filters = listOf("All", "AI", "Local", "Action")

    // Determine command type
    fun getCommandType(cmd: Command): String {
        if (cmd.isBuiltIn) {
            val localSet = setOf("cp", "ct", "pt", "del", "upper", "lower", "title", "date", "time", "count", "trim", "join", "split", "sort", "dedupe", "upside", "mirror", "bold", "italic", "rot13", "md5", "reverse", "undo")
            val name = cmd.trigger.removePrefix("?").removePrefix("!").removePrefix("+")
            return if (localSet.contains(name)) "Local" else "AI"
        }
        val p = cmd.prompt.trimStart()
        return if (p.startsWith("app:") || p.startsWith("tel:") || p.startsWith("sms:") || p.startsWith("mailto:") || p.startsWith("https://") || p.startsWith("http://")) "Action" else "AI"
    }

    // Filter commands
    val filteredCommands = allCommands.filter { cmd ->
        if (selectedFilter == "All") true else getCommandType(cmd) == selectedFilter
    }

    val prefix = commandManager.getTriggerPrefix()
    val cardBg = Color(0xFF1C1C1E)
    val dimText = Color(0xFF8E8E93)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(top = 24.dp)
    ) {
        // Header row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Commands",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            IconButton(onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                dialogState = Pair(false, null) // Open in Add mode
            }) {
                Icon(Icons.Default.Add, contentDescription = "Add Command", tint = Color.White)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Filter chips
        Row(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            filters.forEach { filter ->
                val isSelected = filter == selectedFilter
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (isSelected) Color.White else cardBg)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            selectedFilter = filter
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = filter,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color.Black else dimText
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Command list
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            if (filteredCommands.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "No commands", fontSize = 16.sp, color = dimText)
                        Text(text = "Tap + to add one", fontSize = 13.sp, color = dimText)
                    }
                }
            }
            items(filteredCommands) { cmd ->
                val type = getCommandType(cmd)
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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF3A3A3C))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = type,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = cmd.trigger,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = cmd.prompt,
                                fontSize = 13.sp,
                                color = dimText,
                                maxLines = 2
                            )
                        }
                        if (!cmd.isBuiltIn) {
                            Row {
                                IconButton(onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    commandManager.removeCustomCommand(cmd.trigger)
                                    allCommands = commandManager.getCommands()
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = dimText)
                                }
                                IconButton(onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    dialogState = Pair(true, cmd.trigger) // Open in Edit mode
                                }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit", tint = dimText)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Add/Edit Dialog
    dialogState?.let { (isEdit, oldTrigger) ->
        val initialTrigger = if (isEdit && oldTrigger != null) {
            allCommands.find { it.trigger == oldTrigger }?.trigger ?: ""
        } else ""
        val initialPrompt = if (isEdit && oldTrigger != null) {
            allCommands.find { it.trigger == oldTrigger }?.prompt ?: ""
        } else ""

        CommandDialog(
            prefix = prefix,
            isEdit = isEdit,
            initialTrigger = initialTrigger,
            initialPrompt = initialPrompt,
            existingCommands = if (isEdit) allCommands.filter { it.trigger != oldTrigger } else allCommands,
            onDismiss = { dialogState = null },
            onSave = { trigger, prompt ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                if (isEdit && oldTrigger != null) {
                    commandManager.updateCustomCommand(oldTrigger, Command(trigger, prompt, false))
                } else {
                    commandManager.addCustomCommand(Command(trigger, prompt, false))
                }
                allCommands = commandManager.getCommands()
                dialogState = null
            }
        )
    }
}

@Composable
fun CommandDialog(
    prefix: String,
    isEdit: Boolean,
    initialTrigger: String,
    initialPrompt: String,
    existingCommands: List<Command>,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var trigger by remember { mutableStateOf(initialTrigger) }
    var prompt by remember { mutableStateOf(initialPrompt) }
    var error by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1C1E)),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (isEdit) "Edit Command" else "Add Command", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = trigger,
                    onValueChange = { trigger = it; error = null },
                    label = { Text("Trigger (e.g. ${prefix}mycmd)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color(0xFF3A3A3C)
                    )
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("Prompt or app:com.pkg, tel:..., https://...") },
                    modifier = Modifier.fillMaxWidth().height(90.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color(0xFF3A3A3C)
                    )
                )
                error?.let {
                    Text(text = it, color = Color(0xFF8E8E93), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                }
                Spacer(modifier = Modifier.height(16.dp))
                
                // Buttons row - Save on right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = {
                            val t = trigger.trim()
                            val p = prompt.trim()
                            if (t.isBlank() || p.isBlank()) {
                                error = "Trigger and prompt cannot be empty"
                                return@Button
                            }
                            if (!t.startsWith(prefix)) {
                                error = "Trigger must start with '$prefix'"
                                return@Button
                            }
                            if (existingCommands.any { it.trigger == t }) {
                                error = "This trigger already exists"
                                return@Button
                            }
                            onSave(t, p)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White)
                    ) {
                        Text(if (isEdit) "Update" else "Save", color = Color.Black)
                    }
                }
            }
        }
    }
}
