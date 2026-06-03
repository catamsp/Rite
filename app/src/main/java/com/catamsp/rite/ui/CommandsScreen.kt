package com.catamsp.rite.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.catamsp.rite.model.Command
import com.catamsp.rite.viewmodel.CommandDialogState
import com.catamsp.rite.viewmodel.CommandsViewModel

private val DownloadIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Download",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = androidx.compose.ui.graphics.SolidColor(androidx.compose.ui.graphics.Color.Black),
            strokeLineWidth = 0f
        ) {
            moveTo(19f, 9f)
            horizontalLineToRelative(-4f)
            verticalLineTo(3f)
            horizontalLineTo(9f)
            verticalLineTo(6f)
            lineTo(5f, 6f)
            lineTo(12f, 13f)
            lineTo(19f, 6f)
            close()
            moveTo(5f, 18f)
            verticalLineTo(20f)
            horizontalLineTo(19f)
            verticalLineTo(18f)
            close()
        }
    }.build()
}

private val LOCAL_SET = setOf(
    "cp", "ct", "pt", "del", "upper", "lower", "title", "date", "time", "count",
    "trim", "join", "split", "sort", "dedupe", "upside", "mirror", "bold", "italic",
    "rot13", "md5", "reverse", "undo"
)

private fun getCommandType(cmd: Command): String {
    if (cmd.isBuiltIn) {
        val name = cmd.trigger.removePrefix("?").removePrefix("!").removePrefix("+")
        return if (LOCAL_SET.contains(name)) "Local" else "AI"
    }
    return when (cmd.type) {
        com.catamsp.rite.model.CommandType.TEXT_REPLACER -> "Replacer"
        com.catamsp.rite.model.CommandType.AI -> {
            val p = cmd.prompt.trimStart()
            if (p.startsWith("app:") || p.startsWith("tel:") || p.startsWith("sms:") || p.startsWith("mailto:") || p.startsWith("https://") || p.startsWith("http://")) "Action" else "AI"
        }
    }
}

@Composable
fun CommandsScreen(viewModel: CommandsViewModel = viewModel()) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val commandsState by viewModel.state.collectAsStateWithLifecycle()
    val allCommands = commandsState.commands
    val currentPrefix = commandsState.triggerPrefix
    val importResult = commandsState.importResult

    var dialogState by remember { mutableStateOf<CommandDialogState>(CommandDialogState.Hidden) }
    var showExportPicker by remember { mutableStateOf(false) }
    var commandToDelete by remember { mutableStateOf<String?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importCommands(uri, context.contentResolver)
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            viewModel.exportCommands(uri, context.contentResolver)
        }
    }

    var selectedFilter by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }
    var expandedIds by remember { mutableStateOf(emptySet<String>()) }

    val filteredCommands by remember {
        derivedStateOf {
            var result = if (selectedFilter == "All") allCommands
            else allCommands.filter { cmd -> getCommandType(cmd) == selectedFilter }
            if (searchQuery.isNotBlank()) {
                result = result.filter {
                    it.trigger.contains(searchQuery, ignoreCase = true) ||
                    it.prompt.contains(searchQuery, ignoreCase = true)
                }
            }
            result
        }
    }

    importResult?.let { msg ->
        LaunchedEffect(msg) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { }
            .padding(horizontal = 16.dp)
            .padding(top = 24.dp)
    ) {
        CommandHeader(
            onImport = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                importLauncher.launch(arrayOf("text/*", "text/csv"))
            },
            onExport = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                showExportPicker = true
            },
            onAdd = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                dialogState = CommandDialogState.Add(allCommands.map { it.trigger }.toSet())
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (allCommands.isNotEmpty()) {
            CommandSearchBar(
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                expandedIds = expandedIds,
                filteredCommands = filteredCommands,
                onToggleExpandAll = {
                    expandedIds = if (expandedIds.isEmpty()) {
                        filteredCommands.map { it.trigger }.toSet()
                    } else {
                        emptySet()
                    }
                }
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        CommandFilters(
            selectedFilter = selectedFilter,
            onFilterSelected = { filter ->
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                selectedFilter = filter
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        ImportResultBanner(
            message = importResult,
            onDismiss = { viewModel.clearImportResult() }
        )

        if (showExportPicker) {
            ExportDialog(
                onDismiss = { showExportPicker = false },
                onExport = {
                    showExportPicker = false
                    exportLauncher.launch("rite-commands.csv")
                }
            )
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            if (filteredCommands.isEmpty() && searchQuery.isNotBlank()) {
                item(key = "no-match") {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "No matching commands", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else if (filteredCommands.isEmpty()) {
                item(key = "empty") {
                    EmptyCommandState()
                }
            }
            items(filteredCommands, key = { it.trigger }) { cmd ->
                CommandItem(
                    command = cmd,
                    allCommands = allCommands,
                    isExpanded = cmd.trigger in expandedIds,
                    onToggleExpand = {
                        expandedIds = if (cmd.trigger in expandedIds) expandedIds - cmd.trigger
                        else expandedIds + cmd.trigger
                    },
                    onDelete = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        commandToDelete = cmd.trigger
                    },
                    onEdit = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        dialogState = CommandDialogState.Edit(
                            cmd.trigger,
                            allCommands.filter { it.trigger != cmd.trigger }.map { it.trigger }.toSet()
                        )
                    }
                )
            }
        }
    }

    when (val state = dialogState) {
        is CommandDialogState.Hidden -> {}
        is CommandDialogState.Add -> {
            val onDismiss = remember { { dialogState = CommandDialogState.Hidden } }
            val onSave = remember(state) {
                { trigger: String, prompt: String, type: com.catamsp.rite.model.CommandType ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.addCommand(Command(trigger, prompt, false, type))
                    dialogState = CommandDialogState.Hidden
                }
            }

            CommandDialog(
                prefix = currentPrefix,
                isEdit = false,
                initialTrigger = "",
                initialPrompt = "",
                existingCommands = allCommands,
                onDismiss = onDismiss,
                onSave = onSave
            )
        }
        is CommandDialogState.Edit -> {
            val initialCommand = allCommands.find { it.trigger == state.trigger }
            val existingCommands = remember(allCommands, state.trigger) {
                allCommands.filter { it.trigger != state.trigger }
            }
            val onDismiss = remember { { dialogState = CommandDialogState.Hidden } }
            val onSave = remember(state) {
                { trigger: String, prompt: String, type: com.catamsp.rite.model.CommandType ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.updateCommand(state.trigger, Command(trigger, prompt, false, type))
                    dialogState = CommandDialogState.Hidden
                }
            }

            CommandDialog(
                prefix = currentPrefix,
                isEdit = true,
                initialTrigger = initialCommand?.trigger ?: "",
                initialPrompt = initialCommand?.prompt ?: "",
                initialType = initialCommand?.type ?: com.catamsp.rite.model.CommandType.AI,
                existingCommands = existingCommands,
                onDismiss = onDismiss,
                onSave = onSave
            )
        }
    }

    commandToDelete?.let { triggerToDelete ->
        AlertDialog(
            onDismissRequest = { commandToDelete = null },
            title = { Text("Delete Command") },
            text = { Text("Are you sure you want to delete this command?") },
            confirmButton = {
                TextButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.removeCommand(triggerToDelete)
                    commandToDelete = null
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { commandToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun CommandHeader(onImport: () -> Unit, onExport: () -> Unit, onAdd: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Commands",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Row {
            IconButton(onClick = onImport) {
                Icon(DownloadIcon, contentDescription = "Import CSV", tint = MaterialTheme.colorScheme.onSurface)
            }
            IconButton(onClick = onExport) {
                Icon(Icons.Default.Share, contentDescription = "Export CSV", tint = MaterialTheme.colorScheme.onSurface)
            }
            IconButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = "Add Command", tint = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun CommandFilters(selectedFilter: String, onFilterSelected: (String) -> Unit) {
    val filters = remember { listOf("All", "AI", "Local", "Action", "Replacer") }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        filters.forEach { filter ->
            val isSelected = filter == selectedFilter
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surface)
                    .clickable(enabled = !isSelected) { onFilterSelected(filter) }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = filter,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun CommandSearchBar(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    expandedIds: Set<String>,
    filteredCommands: List<Command>,
    onToggleExpandAll: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(8.dp))
            androidx.compose.foundation.text.BasicTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                modifier = Modifier.weight(1f),
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp
                ),
                singleLine = true,
                decorationBox = { innerTextField ->
                    Box {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Search commands...",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                        }
                        innerTextField()
                    }
                }
            )
            if (searchQuery.isNotEmpty()) {
                IconButton(onClick = { onSearchQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onToggleExpandAll) {
                Icon(
                    imageVector = if (expandedIds.isEmpty()) Icons.AutoMirrored.Filled.List else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expandedIds.isEmpty()) "Expand all" else "Collapse all",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ImportResultBanner(message: String?, onDismiss: () -> Unit) {
    if (message != null) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = message, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismiss) {
                    Text("Dismiss", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun ExportDialog(onDismiss: () -> Unit, onExport: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.outline),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Export Commands", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Choose where to save the CSV file.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Button(
                        onClick = onExport,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSurface)
                    ) {
                        Text("Choose Location", color = MaterialTheme.colorScheme.background)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyCommandState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "No commands", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = "Tap + to add one", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun CommandItem(command: Command, allCommands: List<Command>, isExpanded: Boolean, onToggleExpand: () -> Unit, onDelete: () -> Unit, onEdit: () -> Unit) {
    val type = remember(command) { getCommandType(command) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleExpand() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.outlineVariant)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = type,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = command.trigger,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (!command.isBuiltIn) {
                    Row {
                        IconButton(onClick = onDelete) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = onEdit) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(animationSpec = tween(250), expandFrom = Alignment.Top) + fadeIn(tween(200)),
                exit = shrinkVertically(animationSpec = tween(250), shrinkTowards = Alignment.Top) + fadeOut(tween(150))
            ) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = command.prompt,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun CommandDialog(
    prefix: String,
    isEdit: Boolean,
    initialTrigger: String,
    initialPrompt: String,
    initialType: com.catamsp.rite.model.CommandType = com.catamsp.rite.model.CommandType.AI,
    existingCommands: List<Command>,
    onDismiss: () -> Unit,
    onSave: (String, String, com.catamsp.rite.model.CommandType) -> Unit
) {
    var trigger by remember { mutableStateOf(initialTrigger) }
    var prompt by remember { mutableStateOf(initialPrompt) }
    var selectedType by remember { mutableStateOf(initialType) }
    var error by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.outline),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(if (isEdit) "Edit Command" else "Add Command", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurface)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurface)
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
                        focusedBorderColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("Prompt or app:com.pkg, tel:..., https://...") },
                    modifier = Modifier.fillMaxWidth().height(90.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                    )
                )
                Spacer(modifier = Modifier.height(12.dp))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    com.catamsp.rite.model.CommandType.entries.forEachIndexed { index, type ->
                        SegmentedButton(
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = com.catamsp.rite.model.CommandType.entries.size),
                            onClick = { selectedType = type },
                            selected = selectedType == type,
                            colors = SegmentedButtonDefaults.colors(
                                activeContainerColor = MaterialTheme.colorScheme.onSurface,
                                activeContentColor = MaterialTheme.colorScheme.background
                            )
                        ) {
                            Text(when (type) {
                                com.catamsp.rite.model.CommandType.AI -> "AI"
                                com.catamsp.rite.model.CommandType.TEXT_REPLACER -> "Text Replacer"
                            })
                        }
                    }
                }
                error?.let {
                    Text(text = it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                }
                Spacer(modifier = Modifier.height(16.dp))

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
                            if (t.length <= prefix.length) {
                                error = "Trigger must be longer than just the prefix"
                                return@Button
                            }
                            if (existingCommands.any { it.trigger == t }) {
                                error = "This trigger already exists"
                                return@Button
                            }
                            val conflicting = existingCommands.firstOrNull {
                                it.trigger.startsWith(t) || t.startsWith(it.trigger)
                            }
                            if (conflicting != null) {
                                error = "Conflicts with existing trigger '${conflicting.trigger}'"
                                return@Button
                            }
                            onSave(t, p, selectedType)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSurface)
                    ) {
                        Text(if (isEdit) "Update" else "Save", color = MaterialTheme.colorScheme.background)
                    }
                }
            }
        }
    }
}
