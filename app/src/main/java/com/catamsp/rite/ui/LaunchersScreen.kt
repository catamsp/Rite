package com.catamsp.rite.ui

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.catamsp.rite.manager.AppManager
import com.catamsp.rite.model.AppShortcut
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun LaunchersScreen() {
    val context = LocalContext.current
    val appManager = remember { AppManager(context) }
    var shortcuts by remember { mutableStateOf<List<AppShortcut>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        coroutineScope.launch(Dispatchers.IO) {
            val list = appManager.getShortcuts()
            shortcuts = list.sortedBy { it.appName }
        }
    }

    val filteredShortcuts = remember(shortcuts, searchQuery) {
        if (searchQuery.isBlank()) {
            shortcuts
        } else {
            shortcuts.filter { it.appName.contains(searchQuery, ignoreCase = true) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search launchers...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
            shape = RoundedCornerShape(12.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(filteredShortcuts, key = { it.triggerCode + it.appName }) { shortcut ->
                LauncherItemRow(
                    shortcut = shortcut,
                    onShortcutChanged = { newTrigger ->
                        val updatedList = shortcuts.map {
                            if (it.packageName == shortcut.packageName && it.intentUri == shortcut.intentUri) {
                                it.copy(triggerCode = newTrigger)
                            } else {
                                it
                            }
                        }
                        shortcuts = updatedList
                        appManager.saveShortcuts(updatedList)
                    },
                    onToggleEnabled = { enabled ->
                        appManager.toggleEnabled(shortcut.triggerCode, enabled)
                        val updatedList = shortcuts.map {
                            if (it.packageName == shortcut.packageName && it.intentUri == shortcut.intentUri) {
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
fun LauncherItemRow(
    shortcut: AppShortcut,
    onShortcutChanged: (String) -> Unit,
    onToggleEnabled: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var tempTrigger by remember(shortcut.triggerCode) { mutableStateOf(shortcut.triggerCode) }
    val packageManager = context.packageManager
    
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
            val icon = remember(shortcut.packageName) {
                try {
                    shortcut.packageName?.let { packageManager.getApplicationIcon(it) }
                } catch (e: PackageManager.NameNotFoundException) {
                    null
                }
            }

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (icon != null) {
                    val bitmap = remember(icon) { drawableToBitmap(icon).asImageBitmap() }
                    Image(
                        bitmap = bitmap,
                        contentDescription = shortcut.appName,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = shortcut.appName.firstOrNull()?.toString()?.uppercase() ?: "?",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = shortcut.appName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = if (shortcut.isSystemApp || shortcut.intentAction != null) "Deep Link" else "Installed App",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            OutlinedTextField(
                value = tempTrigger,
                onValueChange = { 
                    tempTrigger = it
                },
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

fun drawableToBitmap(drawable: Drawable): Bitmap {
    if (drawable is BitmapDrawable) {
        if (drawable.bitmap != null) {
            return drawable.bitmap
        }
    }
    val bitmap = if (drawable.intrinsicWidth <= 0 || drawable.intrinsicHeight <= 0) {
        Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    } else {
        Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
    }
    val canvas = android.graphics.Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}
