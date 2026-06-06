package com.catamsp.rite.viewmodel

import android.app.Application
import android.content.Context
import androidx.compose.runtime.Stable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.catamsp.rite.RiteApp
import com.catamsp.rite.manager.CommandManager
import com.catamsp.rite.model.ProviderType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Stable
data class SettingsState(
    val providerType: String = ProviderType.GEMINI,
    val selectedModel: String = "gemini-2.5-flash-lite",
    val groqModel: String = "llama-3.3-70b-versatile",
    val customEndpoint: String = "",
    val customModel: String = "",
    val triggerPrefix: String = "?",
    val temperature: Float = 0.5f,
    val screenContextEnabled: Boolean = false
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val commandManager = (application as RiteApp).commandManager

    private val _state = MutableStateFlow(SettingsState(
        providerType = ProviderType.sanitize(prefs.getString("provider_type", null)),
        selectedModel = prefs.getString("model", "gemini-2.5-flash-lite") ?: "gemini-2.5-flash-lite",
        groqModel = prefs.getString("groq_model", "llama-3.3-70b-versatile") ?: "llama-3.3-70b-versatile",
        customEndpoint = prefs.getString("custom_endpoint", "") ?: "",
        customModel = prefs.getString("custom_model", "") ?: "",
        triggerPrefix = commandManager.getTriggerPrefix(),
        temperature = prefs.getFloat("temperature", 0.5f),
        screenContextEnabled = prefs.getBoolean("screen_context_enabled", false)
    ))
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    private var saveEndpointJob: Job? = null
    private var saveModelJob: Job? = null

    fun updateProviderType(newType: String) {
        _state.value = _state.value.copy(providerType = newType)
        viewModelScope.launch(Dispatchers.IO) {
            prefs.edit()
                .putString("provider_type", newType)
                .remove("structured_output_disabled_at")
                .apply()
        }
    }

    fun updateSelectedModel(newModel: String) {
        _state.value = _state.value.copy(selectedModel = newModel)
        viewModelScope.launch(Dispatchers.IO) {
            prefs.edit()
                .putString("model", newModel)
                .remove("structured_output_disabled_at")
                .apply()
        }
    }

    fun updateGroqModel(newModel: String) {
        _state.value = _state.value.copy(groqModel = newModel)
        viewModelScope.launch(Dispatchers.IO) {
            prefs.edit()
                .putString("groq_model", newModel)
                .remove("structured_output_disabled_at")
                .apply()
        }
    }

    fun saveCustomEndpoint(endpoint: String) {
        _state.value = _state.value.copy(customEndpoint = endpoint)
        saveEndpointJob?.cancel()
        saveEndpointJob = viewModelScope.launch(Dispatchers.IO) {
            delay(500)
            prefs.edit()
                .putString("custom_endpoint", endpoint)
                .remove("structured_output_disabled_at")
                .apply()
        }
    }

    fun saveCustomModel(model: String) {
        _state.value = _state.value.copy(customModel = model)
        saveModelJob?.cancel()
        saveModelJob = viewModelScope.launch(Dispatchers.IO) {
            delay(500)
            prefs.edit()
                .putString("custom_model", model)
                .remove("structured_output_disabled_at")
                .apply()
        }
    }

    fun updateTemperature(newTemp: Float) {
        val rounded = Math.round(newTemp * 10) / 10f
        _state.value = _state.value.copy(temperature = rounded)
        viewModelScope.launch(Dispatchers.IO) {
            prefs.edit().putFloat("temperature", rounded).apply()
        }
    }

    fun refreshTriggerPrefix() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.value = _state.value.copy(triggerPrefix = commandManager.getTriggerPrefix())
        }
    }

    fun toggleScreenContext() {
        val newValue = !_state.value.screenContextEnabled
        _state.value = _state.value.copy(screenContextEnabled = newValue)
        viewModelScope.launch(Dispatchers.IO) {
            prefs.edit().putBoolean("screen_context_enabled", newValue).apply()
        }
    }
}
