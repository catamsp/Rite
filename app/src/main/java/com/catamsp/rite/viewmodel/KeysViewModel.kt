package com.catamsp.rite.viewmodel

import android.app.Application
import androidx.compose.runtime.Stable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.catamsp.rite.RiteApp
import com.catamsp.rite.manager.KeyManager
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Stable
data class KeysState(
    val keys: ImmutableList<String> = persistentListOf(),
    val keyStatuses: ImmutableList<KeyManager.KeyStatus> = persistentListOf(),
    val isLoading: Boolean = true,
    val isKeystoreAvailable: Boolean = false
)

class KeysViewModel(application: Application) : AndroidViewModel(application) {
    private val keyManager = (application as RiteApp).keyManager

    private val _state = MutableStateFlow(KeysState(isKeystoreAvailable = keyManager.isKeystoreAvailable))
    val state: StateFlow<KeysState> = _state.asStateFlow()

    init {
        refreshKeys()
        startPolling()
    }

    private fun startPolling() {
        viewModelScope.launch(Dispatchers.Default) {
            while (true) {
                kotlinx.coroutines.delay(10_000)
                withContext(Dispatchers.IO) {
                    _state.value = _state.value.copy(
                        keyStatuses = keyManager.getKeyStatuses().toImmutableList()
                    )
                }
            }
        }
    }

    fun refreshKeys() {
        viewModelScope.launch(Dispatchers.IO) {
            val keys = keyManager.getKeys().toImmutableList()
            val statuses = keyManager.getKeyStatuses().toImmutableList()
            _state.value = _state.value.copy(keys = keys, keyStatuses = statuses, isLoading = false)
        }
    }

    fun addKey(key: String, onResult: (Result<Unit>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = keyManager.addKey(key)
            if (result.isSuccess) {
                _state.value = _state.value.copy(keys = keyManager.getKeys().toImmutableList())
            }
            launch(Dispatchers.Main) {
                onResult(result)
            }
        }
    }

    fun removeKey(key: String) {
        viewModelScope.launch(Dispatchers.IO) {
            keyManager.removeKey(key)
            _state.value = _state.value.copy(keys = keyManager.getKeys().toImmutableList())
        }
    }
}
