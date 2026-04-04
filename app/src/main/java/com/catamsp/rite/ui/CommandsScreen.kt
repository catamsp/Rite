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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.catamsp.rite.manager.CommandManager
import com.catamsp.rite.model.Command
import com.catamsp.rite.ui.components.ScreenTitle
import com.catamsp.rite.ui.components.SlateCard

@Composable
fun CommandsScreen() {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val commandManager = remember { CommandManager(context) }
    var commands by remember { mutableStateOf(commandManager.getCommands()) }
    var trigger by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val prefix = commandManager.getTriggerPrefix()

    val cardBg = Color(0xFF1C1C1E)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(top = 24.dp)
    ) {
        ScreenTitle("Commands")

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Add Custom Command",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = trigger,
                    onValueChange = {
                        trigger = it
                        errorMessage = null
                    },
                    label = { Text("Trigger (e.g., ${prefix}code)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color(0xFF3A3A3C)
                    )
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("Prompt (must ask for JUST modified text)") },
                    modifier = Modifier.fillMaxWidth().height(90.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.White,
                        unfocusedBorderColor = Color(0xFF3A3A3C)
                    )
                )
                errorMessage?.let { msg ->
                    Text(
                        text = msg,
                        color = Color(0xFF6E6E73),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = {
                            val trimmedTrigger = trigger.trim()
                            if (trimmedTrigger.isNotBlank() && prompt.isNotBlank()) {
                                if (!trimmedTrigger.startsWith(prefix)) {
                                    errorMessage = "Trigger must start with '$prefix'"
                                    return@Button
                                }
                                if (commands.any { it.trigger == trimmedTrigger }) {
                                    errorMessage = "A command with this trigger already exists"
                                    return@Button
                                }
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                val newCommand = Command(trimmedTrigger, prompt.trim(), false)
                                commandManager.addCustomCommand(newCommand)
                                commands = commandManager.getCommands()
                                trigger = ""
                                prompt = ""
                                errorMessage = null
                            }
                        },
                        enabled = trigger.isNotBlank() && prompt.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White)
                    ) {
                        Text("Add Command", color = Color.Black)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(commands) { cmd ->
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
                                text = cmd.trigger,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = cmd.prompt,
                                fontSize = 13.sp,
                                color = Color(0xFF8E8E93)
                            )
                            if (cmd.isBuiltIn) {
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = "Built-in",
                                    fontSize = 11.sp,
                                    color = Color(0xFF6E6E73)
                                )
                            }
                        }
                        if (!cmd.isBuiltIn) {
                            IconButton(onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                commandManager.removeCustomCommand(cmd.trigger)
                                commands = commandManager.getCommands()
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Command",
                                    tint = Color(0xFF8E8E93)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}