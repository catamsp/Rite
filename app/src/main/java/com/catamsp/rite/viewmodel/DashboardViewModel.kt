package com.catamsp.rite.viewmodel

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Application
import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.Stable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.catamsp.rite.RiteApp
import com.catamsp.rite.manager.KeyManager
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Stable
data class DashboardState(
    val isServiceEnabled: Boolean = false,
    val keyCount: Int = 0,
    val currentPrefix: String = "",
    val keyStatuses: ImmutableList<KeyManager.KeyStatus> = persistentListOf()
)

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val keyManager = (application as RiteApp).keyManager
    private val commandManager = (application as RiteApp).commandManager

    private val _serviceEnabled = MutableStateFlow(false)
    val serviceEnabled: StateFlow<Boolean> = _serviceEnabled.asStateFlow()

    private val _keyCount = MutableStateFlow(0)
    val keyCount: StateFlow<Int> = _keyCount.asStateFlow()

    private val _keyStatuses = MutableStateFlow<ImmutableList<KeyManager.KeyStatus>>(persistentListOf())
    val keyStatuses: StateFlow<ImmutableList<KeyManager.KeyStatus>> = _keyStatuses.asStateFlow()

    val currentPrefix: StateFlow<String> = _serviceEnabled.map { commandManager.getTriggerPrefix() }
        .distinctUntilChanged()
        .let { flow ->
            MutableStateFlow(commandManager.getTriggerPrefix()).also { state ->
                viewModelScope.launch {
                    flow.collect { state.value = it }
                }
            }
        }

    init {
        startPolling()
    }

    private fun startPolling() {
        viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                _serviceEnabled.value = checkServiceEnabled(getApplication())

                withContext(Dispatchers.IO) {
                    val keys = keyManager.getKeys()
                    _keyCount.value = keys.size
                    _keyStatuses.value = keyManager.getKeyStatuses().toImmutableList()
                }
                delay(10_000)
            }
        }
    }

    private fun checkServiceEnabled(context: Context): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
        return enabledServices.any {
            it.resolveInfo.serviceInfo.packageName == context.packageName
        }
    }

    fun refreshServiceStatus() {
        _serviceEnabled.value = checkServiceEnabled(getApplication())
    }
}
