package com.catamsp.rite.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.catamsp.rite.manager.ContactManager
import com.catamsp.rite.model.ContactShortcut
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun ContactsScreen() {
    val context = LocalContext.current
    val contactManager = remember { ContactManager(context) }
    var shortcuts by remember { mutableStateOf<List<ContactShortcut>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        coroutineScope.launch(Dispatchers.IO) {
            val list = contactManager.getShortcuts()
            shortcuts = list.sortedBy { it.contactName }
        }
    }

    val filteredShortcuts = remember(shortcuts, searchQuery) {
        if (searchQuery.isBlank()) {
            shortcuts
        } else {
            shortcuts.filter { it.contactName.contains(searchQuery, ignoreCase = true) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Search contacts...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = { 
                    coroutineScope.launch(Dispatchers.IO) {
                        contactManager.forceRefresh()
                        val list = contactManager.getShortcuts()
                        shortcuts = list.sortedBy { it.contactName }
                    }
                },
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Sync Contacts")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(filteredShortcuts, key = { it.contactId }) { shortcut ->
                ContactItemRow(
                    shortcut = shortcut,
                    onShortcutChanged = { newTrigger ->
                        val updatedList = shortcuts.map {
                            if (it.contactId == shortcut.contactId) {
                                it.copy(triggerCode = newTrigger)
                            } else {
                                it
                            }
                        }
                        shortcuts = updatedList
                        contactManager.saveShortcuts(updatedList)
                    },
                    onToggleEnabled = { enabled ->
                        contactManager.toggleEnabled(shortcut.triggerCode, enabled)
                        val updatedList = shortcuts.map {
                            if (it.contactId == shortcut.contactId) {
                                it.copy(isEnabled = enabled)
                            } else {
                                it
                            }
                        }
                        shortcuts = updatedList
                    }
                )
            }
        }
    }
}

@Composable
fun ContactItemRow(
    shortcut: ContactShortcut,
    onShortcutChanged: (String) -> Unit,
    onToggleEnabled: (Boolean) -> Unit
) {
    var tempTrigger by remember(shortcut.triggerCode) { mutableStateOf(shortcut.triggerCode) }
    
    Card(
        modifier = Modifier.fillMaxWidth().alpha(if (shortcut.isEnabled) 1f else 0.6f),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = shortcut.contactName.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = shortcut.contactName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Contact Trigger",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            OutlinedTextField(
                value = tempTrigger,
                onValueChange = { tempTrigger = it },
                modifier = Modifier.width(100.dp),
                textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                singleLine = true,
                shape = RoundedCornerShape(8.dp)
            )

            AnimatedVisibility(visible = tempTrigger != shortcut.triggerCode) {
                IconButton(onClick = { onShortcutChanged(tempTrigger) }) {
                    Icon(Icons.Default.Check, contentDescription = "Save", tint = MaterialTheme.colorScheme.primary)
                }
            }
            Switch(
                checked = shortcut.isEnabled,
                onCheckedChange = { onToggleEnabled(it) },
                modifier = Modifier.scale(0.8f)
            )
        }
    }
}
