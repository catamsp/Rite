package com.catamsp.rite

import android.app.Application
import com.catamsp.rite.api.GeminiClient
import com.catamsp.rite.api.OpenAICompatibleClient
import com.catamsp.rite.manager.CommandManager
import com.catamsp.rite.manager.KeyManager

class RiteApp : Application() {
    lateinit var keyManager: KeyManager
        private set
    lateinit var commandManager: CommandManager
        private set
    val geminiClient by lazy { GeminiClient() }
    val openAIClient by lazy { OpenAICompatibleClient() }

    override fun onCreate() {
        super.onCreate()
        keyManager = KeyManager(this)
        commandManager = CommandManager(this)
    }
}
