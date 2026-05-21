package com.catamsp.rite.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.catamsp.rite.manager.KeyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class KeysViewModel(application: Application) : AndroidViewModel(application) {
    private val keyManager = KeyManager(application)

    private val _keys = MutableStateFlow<List<String>>(emptyList())
    val keys: StateFlow<List<String>> = _keys.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        refreshKeys()
    }

    fun refreshKeys() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _keys.value = keyManager.getKeys()
            _isLoading.value = false
        }
    }

    fun addKey(key: String, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = keyManager.addKey(key)
            if (result.isSuccess) {
                _keys.value = keyManager.getKeys()
            }
            launch(Dispatchers.Main) {
                onResult(result)
            }
        }
    }

    fun removeKey(key: String) {
        viewModelScope.launch(Dispatchers.IO) {
            keyManager.removeKey(key)
            _keys.value = keyManager.getKeys()
        }
    }

    fun isKeystoreAvailable(): Boolean = keyManager.isKeystoreAvailable
}
