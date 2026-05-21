package com.catamsp.rite.viewmodel

import android.app.Application
import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.catamsp.rite.manager.CommandManager
import com.catamsp.rite.model.Command
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

class CommandsViewModel(application: Application) : AndroidViewModel(application) {
    private val commandManager = CommandManager(application)

    private val _allCommands = MutableStateFlow<List<Command>>(emptyList())
    val allCommands: StateFlow<List<Command>> = _allCommands.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _triggerPrefix = MutableStateFlow("")
    val triggerPrefix: StateFlow<String> = _triggerPrefix.asStateFlow()

    private val _importResult = MutableStateFlow<String?>(null)
    val importResult: StateFlow<String?> = _importResult.asStateFlow()

    init {
        refreshCommands()
        refreshPrefix()
    }

    fun refreshCommands() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _allCommands.value = commandManager.getCommands()
            _isLoading.value = false
        }
    }

    private fun refreshPrefix() {
        viewModelScope.launch(Dispatchers.IO) {
            _triggerPrefix.value = commandManager.getTriggerPrefix()
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

                val existingTriggers = _allCommands.value.map { it.trigger }.toSet()
                val result = commandManager.importCommands(lines, existingTriggers)
                
                val skippedDetail = if (result.skippedTriggers.isNotEmpty()) {
                    "\nSkipped: ${result.skippedTriggers.joinToString(", ")}"
                } else ""
                
                _importResult.value = "Imported ${result.imported} commands, ${result.skipped} skipped$skippedDetail"
                refreshCommands()
            } catch (e: Exception) {
                _importResult.value = "Import failed: ${e.message}"
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
                _importResult.value = "Exported ${_allCommands.value.count { !it.isBuiltIn }} commands"
            } catch (e: Exception) {
                _importResult.value = "Export failed: ${e.message}"
            }
        }
    }

    fun clearImportResult() {
        _importResult.value = null
    }
}
