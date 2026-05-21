package com.catamsp.rite.viewmodel

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Application
import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.catamsp.rite.manager.CommandManager
import com.catamsp.rite.manager.KeyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val keyManager = KeyManager(application)
    private val commandManager = CommandManager(application)

    private val _isServiceEnabled = MutableStateFlow(false)
    val isServiceEnabled: StateFlow<Boolean> = _isServiceEnabled.asStateFlow()

    private val _keyCount = MutableStateFlow(0)
    val keyCount: StateFlow<Int> = _keyCount.asStateFlow()

    private val _currentPrefix = MutableStateFlow("")
    val currentPrefix: StateFlow<String> = _currentPrefix.asStateFlow()

    private val _keyStatuses = MutableStateFlow<List<KeyManager.KeyStatus>>(emptyList())
    val keyStatuses: StateFlow<List<KeyManager.KeyStatus>> = _keyStatuses.asStateFlow()

    init {
        startPolling()
    }

    private fun startPolling() {
        viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                val enabled = checkServiceEnabled(getApplication())
                
                withContext(Dispatchers.IO) {
                    val count = keyManager.getKeys().size
                    val prefix = commandManager.getTriggerPrefix()
                    val statuses = keyManager.getKeyStatuses()
                    
                    _isServiceEnabled.value = enabled
                    _keyCount.value = count
                    _currentPrefix.value = prefix
                    _keyStatuses.value = statuses
                }
                delay(3000)
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
}
