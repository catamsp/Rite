package com.catamsp.rite.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
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
import org.json.JSONArray
import org.json.JSONObject

data class FallbackRow(
    val provider: String,
    val model: String
)

@Stable
data class SettingsState(
    val providerType: String = ProviderType.GEMINI,
    val selectedModel: String = "gemini-2.5-flash-lite",
    val groqModel: String = "llama-3.3-70b-versatile",
    val customEndpoint: String = "",
    val customModel: String = "",
    val triggerPrefix: String = "?",
    val temperature: Float = 0.5f,
    val screenContextEnabled: Boolean = false,
    val callsEnabled: Boolean = false,
    val fallbackRows: List<FallbackRow> = listOf(
        FallbackRow(ProviderType.GEMINI, "gemini-2.5-flash-lite")
    ),
    val availableModels: Map<String, List<String>> = emptyMap(),
    val modelsLoading: Set<String> = emptySet()
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val commandManager = (application as RiteApp).commandManager
    private val app = application as RiteApp

    private val _state = MutableStateFlow(SettingsState(
        providerType = ProviderType.sanitize(prefs.getString("provider_type", null)),
        selectedModel = prefs.getString("model", "gemini-2.5-flash-lite") ?: "gemini-2.5-flash-lite",
        groqModel = prefs.getString("groq_model", "llama-3.3-70b-versatile") ?: "llama-3.3-70b-versatile",
        customEndpoint = prefs.getString("custom_endpoint", "") ?: "",
        customModel = prefs.getString("custom_model", "") ?: "",
        triggerPrefix = commandManager.getTriggerPrefix(),
        temperature = prefs.getFloat("temperature", 0.5f),
        screenContextEnabled = prefs.getBoolean("screen_context_enabled", false),
        callsEnabled = prefs.getBoolean("calls_enabled", false),
        fallbackRows = loadFallbackRows()
    ))
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    private var saveEndpointJob: Job? = null
    private var saveModelJob: Job? = null
    private val modelsCache = mutableMapOf<String, List<String>>()

    init {
        val providers = _state.value.fallbackRows.map { it.provider }.distinct()
        providers.forEach { fetchModelsForProvider(it) }
    }

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

    fun updateCallsEnabled(enabled: Boolean) {
        _state.value = _state.value.copy(callsEnabled = enabled)
        viewModelScope.launch(Dispatchers.IO) {
            prefs.edit().putBoolean("calls_enabled", enabled).apply()
        }
    }

    fun addFallbackRow(row: FallbackRow) {
        val current = _state.value.fallbackRows.toMutableList()
        current.add(row)
        _state.value = _state.value.copy(fallbackRows = current)
        saveFallbackRows(current)
        fetchModelsForProvider(row.provider)
    }

    fun removeFallbackRow(index: Int) {
        val current = _state.value.fallbackRows.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            _state.value = _state.value.copy(fallbackRows = current)
            saveFallbackRows(current)
        }
    }

    fun updateFallbackRow(index: Int, row: FallbackRow) {
        val current = _state.value.fallbackRows.toMutableList()
        if (index in current.indices) {
            val oldProvider = current[index].provider
            current[index] = row
            _state.value = _state.value.copy(fallbackRows = current)
            saveFallbackRows(current)
            if (oldProvider != row.provider) {
                fetchModelsForProvider(row.provider)
            }
        }
    }

    fun moveFallbackRow(from: Int, to: Int) {
        val current = _state.value.fallbackRows.toMutableList()
        if (from in current.indices && to in current.indices) {
            val item = current.removeAt(from)
            current.add(to, item)
            _state.value = _state.value.copy(fallbackRows = current)
            saveFallbackRows(current)
        }
    }

    fun refreshAllModels() {
        modelsCache.clear()
        val allProviders = listOf(ProviderType.GEMINI, ProviderType.GROQ, ProviderType.KILO, ProviderType.CEREBRAS)
        val providersWithKeys = allProviders.filter { app.keyManager.getKeysForProvider(it).isNotEmpty() }
        _state.value = _state.value.copy(
            availableModels = emptyMap(),
            modelsLoading = providersWithKeys.toSet()
        )
        providersWithKeys.forEach { fetchModelsForProvider(it) }
    }

    fun fetchModelsForProvider(provider: String) {
        if (provider == ProviderType.CUSTOM) return
        if (modelsCache.containsKey(provider)) return
        if (_state.value.modelsLoading.contains(provider)) return

        val keys = app.keyManager.getKeysForProvider(provider)
        if (keys.isEmpty()) return

        val currentLoading = _state.value.modelsLoading + provider
        _state.value = _state.value.copy(modelsLoading = currentLoading)

        viewModelScope.launch(Dispatchers.IO) {
            val key = keys.first()
            val result = when (provider) {
                ProviderType.GEMINI -> app.geminiClient.listModels(key)
                else -> {
                    val endpoint = resolveEndpoint(provider)
                    app.openAIClient.listModels(key, endpoint)
                }
            }

            val current = _state.value
            val newLoading = current.modelsLoading - provider

            if (result.isSuccess) {
                val models = result.getOrThrow()
                modelsCache[provider] = models
                _state.value = current.copy(
                    availableModels = current.availableModels + (provider to models),
                    modelsLoading = newLoading
                )
                if (ENABLE_DEBUG_LOGGING) Log.d("Rite", "fetchModels: $provider → ${models.size} models")
            } else {
                modelsCache[provider] = emptyList()
                _state.value = current.copy(
                    availableModels = current.availableModels + (provider to emptyList()),
                    modelsLoading = newLoading
                )
                Log.e("Rite", "fetchModels: $provider failed: ${result.exceptionOrNull()?.message}")
            }
        }
    }

    private fun resolveEndpoint(provider: String): String {
        return when (provider) {
            ProviderType.GROQ -> "https://api.groq.com/openai/v1"
            ProviderType.KILO -> "https://api.kilo.ai/api/gateway"
            ProviderType.CEREBRAS -> "https://api.cerebras.ai/v1"
            else -> ""
        }
    }

    private fun saveFallbackRows(rows: List<FallbackRow>) {
        viewModelScope.launch(Dispatchers.IO) {
            val arr = JSONArray()
            rows.forEach { row ->
                arr.put(JSONObject().apply {
                    put("provider", row.provider)
                    put("model", row.model)
                })
            }
            prefs.edit()
                .putString("fallback_rows", arr.toString())
                .remove("structured_output_disabled_at")
                .apply()
        }
    }

    private fun loadFallbackRows(): List<FallbackRow> {
        val json = prefs.getString("fallback_rows", null) ?: return listOf(
            FallbackRow(ProviderType.GEMINI, prefs.getString("model", "gemini-2.5-flash-lite") ?: "gemini-2.5-flash-lite")
        )
        return try {
            val arr = JSONArray(json)
            val rows = mutableListOf<FallbackRow>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                rows.add(FallbackRow(
                    provider = obj.getString("provider"),
                    model = obj.getString("model")
                ))
            }
            if (rows.isEmpty()) listOf(FallbackRow(ProviderType.GEMINI, "gemini-2.5-flash-lite")) else rows
        } catch (_: Exception) {
            listOf(FallbackRow(ProviderType.GEMINI, "gemini-2.5-flash-lite"))
        }
    }

    private companion object {
        const val ENABLE_DEBUG_LOGGING = false
    }
}
