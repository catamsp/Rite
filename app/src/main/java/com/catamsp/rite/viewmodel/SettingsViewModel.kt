package com.catamsp.rite.viewmodel

import android.app.Application
import android.content.Context
import androidx.compose.runtime.Stable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.catamsp.rite.RiteApp
import com.catamsp.rite.manager.CommandManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Stable
data class SettingsState(
    val providerType: String = "gemini",
    val selectedModel: String = "gemini-2.5-flash-lite",
    val customEndpoint: String = "",
    val customModel: String = "",
    val triggerPrefix: String = "?"
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val commandManager = (application as RiteApp).commandManager

    private val _state = MutableStateFlow(SettingsState(
        providerType = prefs.getString("provider_type", "gemini") ?: "gemini",
        selectedModel = prefs.getString("model", "gemini-2.5-flash-lite") ?: "gemini-2.5-flash-lite",
        customEndpoint = prefs.getString("custom_endpoint", "") ?: "",
        customModel = prefs.getString("custom_model", "") ?: "",
        triggerPrefix = commandManager.getTriggerPrefix()
    ))
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    private var saveEndpointJob: Job? = null
    private var saveModelJob: Job? = null

    fun updateProviderType(newType: String) {
        _state.value = _state.value.copy(providerType = newType)
        viewModelScope.launch(Dispatchers.IO) {
            prefs.edit().putString("provider_type", newType).apply()
        }
    }

    fun updateSelectedModel(newModel: String) {
        _state.value = _state.value.copy(selectedModel = newModel)
        viewModelScope.launch(Dispatchers.IO) {
            prefs.edit().putString("model", newModel).apply()
        }
    }

    fun saveCustomEndpoint(endpoint: String) {
        _state.value = _state.value.copy(customEndpoint = endpoint)
        saveEndpointJob?.cancel()
        saveEndpointJob = viewModelScope.launch(Dispatchers.IO) {
            delay(500)
            prefs.edit().putString("custom_endpoint", endpoint).apply()
        }
    }

    fun saveCustomModel(model: String) {
        _state.value = _state.value.copy(customModel = model)
        saveModelJob?.cancel()
        saveModelJob = viewModelScope.launch(Dispatchers.IO) {
            delay(500)
            prefs.edit().putString("custom_model", model).apply()
        }
    }

    fun refreshTriggerPrefix() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(triggerPrefix = commandManager.getTriggerPrefix())
        }
    }
}
