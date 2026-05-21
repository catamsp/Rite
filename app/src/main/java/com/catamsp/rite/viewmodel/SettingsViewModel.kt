package com.catamsp.rite.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.catamsp.rite.manager.CommandManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val commandManager = CommandManager(application)

    private val _providerType = MutableStateFlow(prefs.getString("provider_type", "gemini") ?: "gemini")
    val providerType: StateFlow<String> = _providerType.asStateFlow()

    private val _selectedModel = MutableStateFlow(prefs.getString("model", "gemini-2.5-flash-lite") ?: "gemini-2.5-flash-lite")
    val selectedModel: StateFlow<String> = _selectedModel.asStateFlow()

    private val _customEndpoint = MutableStateFlow(prefs.getString("custom_endpoint", "") ?: "")
    val customEndpoint: StateFlow<String> = _customEndpoint.asStateFlow()

    private val _customModel = MutableStateFlow(prefs.getString("custom_model", "") ?: "")
    val customModel: StateFlow<String> = _customModel.asStateFlow()

    private val _triggerPrefix = MutableStateFlow(commandManager.getTriggerPrefix())
    val triggerPrefix: StateFlow<String> = _triggerPrefix.asStateFlow()

    private var saveEndpointJob: Job? = null
    private var saveModelJob: Job? = null

    fun updateProviderType(newType: String) {
        _providerType.value = newType
        viewModelScope.launch(Dispatchers.IO) {
            prefs.edit().putString("provider_type", newType).apply()
        }
    }

    fun updateSelectedModel(newModel: String) {
        _selectedModel.value = newModel
        viewModelScope.launch(Dispatchers.IO) {
            prefs.edit().putString("model", newModel).apply()
        }
    }

    fun updateCustomEndpoint(newEndpoint: String) {
        _customEndpoint.value = newEndpoint
        saveEndpointJob?.cancel()
        saveEndpointJob = viewModelScope.launch(Dispatchers.IO) {
            delay(500)
            prefs.edit().putString("custom_endpoint", newEndpoint).apply()
        }
    }

    fun updateCustomModel(newModel: String) {
        _customModel.value = newModel
        saveModelJob?.cancel()
        saveModelJob = viewModelScope.launch(Dispatchers.IO) {
            delay(500)
            prefs.edit().putString("custom_model", newModel).apply()
        }
    }

    fun refreshTriggerPrefix() {
        viewModelScope.launch(Dispatchers.IO) {
            _triggerPrefix.value = commandManager.getTriggerPrefix()
        }
    }
}
