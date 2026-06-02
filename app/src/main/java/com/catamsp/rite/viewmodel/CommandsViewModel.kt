package com.catamsp.rite.viewmodel

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.catamsp.rite.RiteApp
import com.catamsp.rite.manager.CommandManager
import com.catamsp.rite.model.Command
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

sealed interface CommandDialogState {
    data object Hidden : CommandDialogState
    data class Add(val existingTriggers: Set<String>) : CommandDialogState
    data class Edit(val trigger: String, val existingTriggers: Set<String>) : CommandDialogState
}

@Stable
data class CommandsState(
    val commands: ImmutableList<Command> = persistentListOf(),
    val triggerPrefix: String = "",
    val importResult: String? = null
)

class CommandsViewModel(application: Application) : AndroidViewModel(application) {
    private val commandManager = (application as RiteApp).commandManager

    private val _state = MutableStateFlow(CommandsState())
    val state: StateFlow<CommandsState> = _state.asStateFlow()

    init {
        refreshCommands()
        refreshPrefix()
    }

    fun refreshCommands() {
        viewModelScope.launch(Dispatchers.IO) {
            val commands = commandManager.getCommands().toImmutableList()
            _state.value = _state.value.copy(commands = commands)
        }
    }

    private fun refreshPrefix() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(triggerPrefix = commandManager.getTriggerPrefix())
        }
    }

    fun addCommand(command: Command) {
        viewModelScope.launch(Dispatchers.IO) {
            commandManager.addCustomCommand(command)
            refreshCommands()
        }
    }

    fun removeCommand(trigger: String) {
        viewModelScope.launch(Dispatchers.IO) {
            commandManager.removeCustomCommand(trigger)
            refreshCommands()
        }
    }

    fun updateCommand(oldTrigger: String, newCommand: Command) {
        viewModelScope.launch(Dispatchers.IO) {
            commandManager.updateCustomCommand(oldTrigger, newCommand)
            refreshCommands()
        }
    }

    fun importCommands(uri: Uri, contentResolver: ContentResolver) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val lines = contentResolver.openInputStream(uri)?.use { stream ->
                    BufferedReader(InputStreamReader(stream)).readLines()
                } ?: emptyList()

                val existingTriggers = _state.value.commands.map { it.trigger }.toSet()
                val result = commandManager.importCommands(lines, existingTriggers)

                val skippedDetail = if (result.skippedTriggers.isNotEmpty()) {
                    "\nSkipped: ${result.skippedTriggers.joinToString(", ")}"
                } else ""

                _state.value = _state.value.copy(importResult = "Imported ${result.imported} commands, ${result.skipped} skipped$skippedDetail")
                refreshCommands()
            } catch (e: Exception) {
                _state.value = _state.value.copy(importResult = "Import failed: ${e.message}")
            }
        }
    }

    fun exportCommands(uri: Uri, contentResolver: ContentResolver) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val csv = commandManager.exportCustomCommandsCsv()
                contentResolver.openOutputStream(uri)?.use { stream ->
                    OutputStreamWriter(stream).use { it.write(csv) }
                }
                _state.value = _state.value.copy(importResult = "Exported ${_state.value.commands.count { !it.isBuiltIn }} commands")
            } catch (e: Exception) {
                _state.value = _state.value.copy(importResult = "Export failed: ${e.message}")
            }
        }
    }

    fun clearImportResult() {
        _state.value = _state.value.copy(importResult = null)
    }
}
