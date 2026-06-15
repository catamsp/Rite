package com.catamsp.rite.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.catamsp.rite.model.ProviderType
import com.catamsp.rite.ui.components.PressableCard
import com.catamsp.rite.ui.components.ScreenTitle
import com.catamsp.rite.viewmodel.FallbackRow
import com.catamsp.rite.viewmodel.ProviderKeyStatus
import com.catamsp.rite.viewmodel.SettingsViewModel
import kotlin.math.roundToInt

private val PROVIDER_OPTIONS = listOf(
    ProviderType.GEMINI,
    ProviderType.GROQ,
    ProviderType.KILO,
    ProviderType.CEREBRAS,
    ProviderType.CUSTOM
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = viewModel()) {
    val haptic = LocalHapticFeedback.current
    val settingsState by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val callPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.updateCallsEnabled(granted)
    }

    var showEditDialog by remember { mutableStateOf(false) }
    var advancedExpanded by remember { mutableStateOf(true) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 24.dp, bottom = 24.dp)
    ) {
        item(key = "title") { ScreenTitle("Settings") }

        item(key = "fallbackDashboard") {
            FallbackDashboardSection(
                rows = settingsState.fallbackRows,
                modelsLoading = settingsState.modelsLoading,
                deprecatedModels = settingsState.deprecatedModels,
                providerKeyStatuses = settingsState.providerKeyStatuses,
                onRefresh = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.refreshAllModels()
                },
                onMoveRow = { from, to -> viewModel.moveFallbackRow(from, to) },
                onAddRow = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    viewModel.addFallbackRow(FallbackRow(ProviderType.GEMINI, ""))
                    showEditDialog = true
                }
            )
        }

        item(key = "spacer1") { Spacer(modifier = Modifier.height(12.dp)) }

        item(key = "advancedHeader") {
            AdvancedSettingsHeader(
                expanded = advancedExpanded,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    advancedExpanded = !advancedExpanded
                }
            )
        }

        if (advancedExpanded) {
            item(key = "temperature") {
                Spacer(modifier = Modifier.height(8.dp))
                CreativitySection(
                    temperature = settingsState.temperature,
                    onCreativityChange = { temp ->
                        viewModel.updateTemperature(temp)
                    }
                )
            }

            item(key = "screenContext") {
                Spacer(modifier = Modifier.height(8.dp))
                ScreenContextSection(
                    enabled = settingsState.screenContextEnabled,
                    onToggle = { viewModel.toggleScreenContext() }
                )
            }

            item(key = "phoneCalls") {
                Spacer(modifier = Modifier.height(8.dp))
                PhoneCallsSection(
                    enabled = settingsState.callsEnabled,
                    onToggle = {
                        if (!settingsState.callsEnabled) {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE)
                                == PackageManager.PERMISSION_GRANTED
                            ) {
                                viewModel.updateCallsEnabled(true)
                            } else {
                                callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
                            }
                        } else {
                            viewModel.updateCallsEnabled(false)
                        }
                    }
                )
            }
        }
    }

    if (showEditDialog) {
        EditFallbackDialog(
            rows = settingsState.fallbackRows,
            availableModels = settingsState.availableModels,
            modelsLoading = settingsState.modelsLoading,
            deprecatedModels = settingsState.deprecatedModels,
            onDismiss = { showEditDialog = false },
            onAddRow = { viewModel.addFallbackRow(it) },
            onRemoveRow = { viewModel.removeFallbackRow(it) },
            onRowChanged = { index, row -> viewModel.updateFallbackRow(index, row) },
            onMoveRow = { from, to -> viewModel.moveFallbackRow(from, to) },
            onProviderChanged = { viewModel.fetchModelsForProvider(it) }
        )
    }
}

@Composable
private fun FallbackDashboardSection(
    rows: List<FallbackRow>,
    modelsLoading: Set<String>,
    deprecatedModels: Set<String>,
    providerKeyStatuses: Map<String, List<ProviderKeyStatus>>,
    onRefresh: () -> Unit,
    onMoveRow: (Int, Int) -> Unit,
    onAddRow: () -> Unit
) {
    var draggedIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var itemHeight by remember { mutableFloatStateOf(0f) }

    PressableCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "AI provider, Models & Fallback",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${rows.size} row${if (rows.size != 1) "s" else ""} configured",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                val isRefreshing = modelsLoading.isNotEmpty()
                IconButton(
                    onClick = onRefresh,
                    enabled = !isRefreshing,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Refresh models",
                        tint = if (isRefreshing) MaterialTheme.colorScheme.onSurfaceVariant
                               else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        if (rows.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(modifier = Modifier.width(32.dp))
                Text(
                    text = "Provider",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "Model",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1.5f)
                )
                Spacer(modifier = Modifier.width(48.dp))
            }

            Spacer(modifier = Modifier.height(6.dp))

            rows.forEachIndexed { index, row ->
                val isDeprecated = row.model.isNotEmpty() && "${row.provider}:${row.model}" in deprecatedModels
                val isDragged = draggedIndex == index
                val keyStatuses = providerKeyStatuses[row.provider] ?: emptyList()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(if (isDragged) 1f else 0f)
                        .offset { IntOffset(0, if (isDragged) dragOffsetY.roundToInt() else 0) }
                        .onGloballyPositioned { coords ->
                            if (index == 0) itemHeight = coords.size.height.toFloat()
                        }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "\u283F",
                        fontSize = 16.sp,
                        color = if (isDragged) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .width(32.dp)
                            .pointerInput(index) {
                                detectDragGestures(
                                    onDragStart = {
                                        draggedIndex = index
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffsetY += dragAmount.y
                                    },
                                    onDragEnd = {
                                        if (itemHeight > 0f) {
                                            val targetIndex = (index + (dragOffsetY / itemHeight).roundToInt())
                                                .coerceIn(0, rows.lastIndex)
                                            if (targetIndex != index) {
                                                onMoveRow(index, targetIndex)
                                            }
                                        }
                                        draggedIndex = -1
                                        dragOffsetY = 0f
                                    },
                                    onDragCancel = {
                                        draggedIndex = -1
                                        dragOffsetY = 0f
                                    }
                                )
                            },
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Text(
                        text = ProviderType.label(row.provider),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    Column(modifier = Modifier.weight(1.5f)) {
                        Text(
                            text = row.model.ifEmpty { "No model" },
                            fontSize = 13.sp,
                            color = when {
                                row.model.isEmpty() -> MaterialTheme.colorScheme.onSurfaceVariant
                                isDeprecated -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )
                        if (isDeprecated) {
                            Text(
                                text = "deprecated",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.width(48.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        keyStatuses.forEach { status ->
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(
                                        color = if (status.isActive) Color.White else Color.Gray,
                                        shape = RoundedCornerShape(3.dp)
                                    )
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable { onAddRow() }
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "Add row",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Add Row",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun AdvancedSettingsHeader(expanded: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Advanced Settings",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Icon(
                Icons.Default.ArrowDropDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(22.dp)
                    .then(
                        if (expanded) Modifier
                        else Modifier
                    )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditFallbackDialog(
    rows: List<FallbackRow>,
    availableModels: Map<String, List<String>>,
    modelsLoading: Set<String>,
    deprecatedModels: Set<String>,
    onDismiss: () -> Unit,
    onAddRow: (FallbackRow) -> Unit,
    onRemoveRow: (Int) -> Unit,
    onRowChanged: (Int, FallbackRow) -> Unit,
    onMoveRow: (Int, Int) -> Unit,
    onProviderChanged: (String) -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f)
                .border(1.dp, Color.White, RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Edit Fallback Order",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    TextButton(onClick = onDismiss) {
                        Text("Done", fontSize = 14.sp)
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Try each row in order. If all keys for a row are rate-limited, moves to the next.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(rows.size) { index ->
                        FallbackRowItem(
                            index = index,
                            row = rows[index],
                            availableModels = availableModels[rows[index].provider] ?: emptyList(),
                            isLoadingModels = modelsLoading.contains(rows[index].provider),
                            isDeprecated = rows[index].model.isNotEmpty() && "${rows[index].provider}:${rows[index].model}" in deprecatedModels,
                            canRemove = rows.size > 1,
                            onRowChanged = { onRowChanged(index, it) },
                            onRemove = { onRemoveRow(index) },
                            onProviderChanged = onProviderChanged
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            onAddRow(FallbackRow(ProviderType.GEMINI, ""))
                        }
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add row",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Add Row",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FallbackRowItem(
    index: Int,
    row: FallbackRow,
    availableModels: List<String>,
    isLoadingModels: Boolean,
    isDeprecated: Boolean,
    canRemove: Boolean,
    onRowChanged: (FallbackRow) -> Unit,
    onRemove: () -> Unit,
    onProviderChanged: (String) -> Unit
) {
    var providerExpanded by remember { mutableStateOf(false) }
    var modelExpanded by remember { mutableStateOf(false) }
    var localModel by remember(row.model) { mutableStateOf(row.model) }

    val isCustom = row.provider == ProviderType.CUSTOM
    val displayProvider = ProviderType.label(row.provider)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${index + 1}.",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(28.dp)
            )

            Box(modifier = Modifier.weight(1f)) {
                ExposedDropdownMenuBox(
                    expanded = providerExpanded,
                    onExpandedChange = { providerExpanded = it }
                ) {
                    OutlinedTextField(
                        value = displayProvider,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = providerExpanded,
                        onDismissRequest = { providerExpanded = false }
                    ) {
                        PROVIDER_OPTIONS.forEach { provider ->
                            DropdownMenuItem(
                                text = { Text(ProviderType.label(provider), fontSize = 13.sp) },
                                onClick = {
                                    val newModel = if (provider == row.provider) row.model
                                                   else availableModels.firstOrNull() ?: ""
                                    onRowChanged(FallbackRow(provider, newModel))
                                    onProviderChanged(provider)
                                    providerExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (isCustom) {
                Column(modifier = Modifier.weight(1.5f)) {
                    OutlinedTextField(
                        value = localModel,
                        onValueChange = {
                            localModel = it
                            onRowChanged(row.copy(model = it))
                        },
                        placeholder = { Text("Model ID", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                        )
                    )
                }
            } else {
                Box(modifier = Modifier.weight(1.5f)) {
                    if (isLoadingModels && availableModels.isEmpty()) {
                        OutlinedTextField(
                            value = "",
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier.fillMaxWidth(),
                            placeholder = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        strokeWidth = 1.5.dp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Loading...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            },
                            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                            )
                        )
                    } else if (availableModels.isEmpty()) {
                        OutlinedTextField(
                            value = localModel,
                            onValueChange = {
                                localModel = it
                                onRowChanged(row.copy(model = it))
                            },
                            placeholder = { Text("Type model ID", fontSize = 12.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                            )
                        )
                    } else {
                        ExposedDropdownMenuBox(
                            expanded = modelExpanded,
                            onExpandedChange = { modelExpanded = it }
                        ) {
                            OutlinedTextField(
                                value = row.model.ifEmpty { "Select model" },
                                onValueChange = {},
                                readOnly = true,
                                modifier = Modifier
                                    .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                    .fillMaxWidth(),
                                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = if (isDeprecated) MaterialTheme.colorScheme.error
                                                         else MaterialTheme.colorScheme.onSurface,
                                    unfocusedBorderColor = if (isDeprecated) MaterialTheme.colorScheme.error
                                                          else MaterialTheme.colorScheme.outlineVariant
                                )
                            )
                            ExposedDropdownMenu(
                                expanded = modelExpanded,
                                onDismissRequest = { modelExpanded = false }
                            ) {
                                availableModels.forEach { model ->
                                    DropdownMenuItem(
                                        text = { Text(model, fontSize = 13.sp) },
                                        onClick = {
                                            onRowChanged(row.copy(model = model))
                                            modelExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (canRemove) {
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Remove row",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CreativitySection(temperature: Float, onCreativityChange: (Float) -> Unit) {
    PressableCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingsLabel("Creativity")
            Text(
                text = String.format("%.1f", temperature),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
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
            modifier = Modifier.fillMaxWidth().height(32.dp)
        )
    }
}

@Composable
private fun ScreenContextSection(enabled: Boolean, onToggle: () -> Unit) {
    PressableCard {
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
private fun PhoneCallsSection(enabled: Boolean, onToggle: () -> Unit) {
    PressableCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Phone Calls",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Allow ?call and ?tel: to make phone calls",
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
private fun SettingsLabel(text: String) {
    Text(text = text, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
}
