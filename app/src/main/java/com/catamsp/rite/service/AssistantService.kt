package com.catamsp.rite.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.catamsp.rite.api.GeminiClient
import com.catamsp.rite.api.OpenAICompatibleClient
import com.catamsp.rite.manager.CommandManager
import com.catamsp.rite.manager.KeyManager
import com.catamsp.rite.model.Command
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicBoolean

class AssistantService : AccessibilityService() {

    private lateinit var keyManager: KeyManager
    private lateinit var commandManager: CommandManager
    private lateinit var toastManager: OverlayToastManager
    private lateinit var textHelper: TextHelper
    private lateinit var localCommandExecutor: LocalCommandExecutor

    private val client = GeminiClient()
    private val openAIClient = OpenAICompatibleClient()
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.IO)
    private val isProcessing = AtomicBoolean(false)
    private val handler = Handler(Looper.getMainLooper())
    private var triggerLastChars = setOf<Char>()
    private var cachedPrefix = CommandManager.DEFAULT_PREFIX
    private var currentJob: Job? = null
    @Volatile
    private var lastOriginalText: String? = null
    private var lastTriggerRefresh = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        keyManager = KeyManager(applicationContext)
        commandManager = CommandManager(applicationContext)
        toastManager = OverlayToastManager(applicationContext, handler)
        textHelper = TextHelper(applicationContext, serviceScope, handler)
        localCommandExecutor = LocalCommandExecutor(
            textHelper = textHelper,
            toastManager = toastManager,
            scope = serviceScope,
            performHaptic = ::performHapticFeedback
        )
        updateTriggers()
    }

    private fun updateTriggers() {
        cachedPrefix = commandManager.getTriggerPrefix()
        val cmds = commandManager.getCommands()
        triggerLastChars = cmds.mapNotNull { it.trigger.lastOrNull() }.toSet()
        lastTriggerRefresh = System.currentTimeMillis()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) return
        if (isProcessing.get()) { if (ENABLE_DEBUG_LOGGING) Log.d("Rite", "SKIP: isProcessing=true"); return }

        if (event.packageName == packageName) {
            if (ENABLE_DEBUG_LOGGING) Log.d("Rite", "SKIP: own app text field")
            return
        }

        val source = event.source
        if (source == null) { if (ENABLE_DEBUG_LOGGING) Log.d("Rite", "SKIP: source=null"); return }

        val text = source.text?.toString()
        if (text.isNullOrEmpty()) { if (ENABLE_DEBUG_LOGGING) Log.d("Rite", "SKIP: text=null or empty"); return }
        if (ENABLE_DEBUG_LOGGING) Log.d("Rite", "TEXT received, len=${text.length}")

        if (System.currentTimeMillis() - lastTriggerRefresh > TRIGGER_REFRESH_INTERVAL_MS) {
            updateTriggers()
            if (ENABLE_DEBUG_LOGGING) Log.d("Rite", "Triggers refreshed")
        }

        val lastChar = text[text.length - 1]
        val hasIntentPrefix = INTENT_PREFIXES.any { text.contains("${cachedPrefix}${it}") }
        if (!triggerLastChars.contains(lastChar)) {
            val hasTranslate = text.contains("${cachedPrefix}translate:") ||
                text.contains("!translate:") ||
                text.contains("+translate:")
            if (!lastChar.isLetterOrDigit() && !hasTranslate && !hasIntentPrefix) {
                if (ENABLE_DEBUG_LOGGING) Log.d("Rite", "SKIP: lastChar not matched")
                return
            }
            if (hasIntentPrefix && !lastChar.isLetterOrDigit()) {
                if (ENABLE_DEBUG_LOGGING) Log.d("Rite", "Allowing intent-type text")
            }
        }

        val command = commandManager.findCommand(text)
        if (command == null) { if (ENABLE_DEBUG_LOGGING) Log.d("Rite", "SKIP: no command found"); return }
        if (ENABLE_DEBUG_LOGGING) Log.d("Rite", "COMMAND: trigger='${command.trigger}', builtIn=${command.isBuiltIn}")

        val cleanText = text.substring(0, text.length - command.trigger.length).trim()

        if (command.trigger.endsWith("undo") && command.isBuiltIn) {
            if (source.isPassword) { if (ENABLE_DEBUG_LOGGING) Log.d("Rite", "SKIP: password field"); return }
            isProcessing.set(true)
            currentJob?.cancel()
            handleUndo(source, cleanText)
            return
        }

        val mode = getCommandMode(command.trigger)
        val cmdName = extractCmdName(command.trigger, cachedPrefix)
        if (ENABLE_DEBUG_LOGGING) Log.d("Rite", "mode=$mode, cmdName='$cmdName'")

        if (command.isBuiltIn && LOCAL_COMMANDS.contains(cmdName)) {
            if (source.isPassword) { if (ENABLE_DEBUG_LOGGING) Log.d("Rite", "SKIP: password field"); return }
            if (ENABLE_DEBUG_LOGGING) Log.d("Rite", "HANDLING: local command '$cmdName'")
            isProcessing.set(true)
            currentJob?.cancel()
            currentJob = localCommandExecutor.execute(source, cleanText, cmdName, mode, command.trigger) {
                withContext(NonCancellable + Dispatchers.Main) { isProcessing.set(false) }
            }
            return
        }

        val isIntent = INTENT_PREFIXES.any { command.prompt.trimStart().startsWith(it) }
        if (ENABLE_DEBUG_LOGGING) Log.d("Rite", "isIntent=$isIntent, cleanText empty=${cleanText.isEmpty()}")
        if (!isIntent && (cleanText.isEmpty() || source.isPassword)) {
            if (ENABLE_DEBUG_LOGGING) Log.d("Rite", "SKIP: cleanText empty or password field")
            return
        }
        if (isIntent && source.isPassword) {
            if (ENABLE_DEBUG_LOGGING) Log.d("Rite", "SKIP: password field for intent")
            return
        }

        if (ENABLE_DEBUG_LOGGING) Log.d("Rite", "HANDLING: ${if (isIntent) "INTENT" else "AI"} command '${command.trigger}'")
        isProcessing.set(true)
        currentJob?.cancel()
        processCommand(source, cleanText, command)
    }

    private fun processCommand(source: AccessibilityNodeInfo, text: String, command: Command) {
        if (INTENT_PREFIXES.any { command.prompt.trimStart().startsWith(it) }) {
            if (ENABLE_DEBUG_LOGGING) Log.d("Rite", "INTENT PATH: launching intent command")
            currentJob = serviceScope.launch {
                try {
                    withContext(Dispatchers.Main) {
                        textHelper.replaceText(source, "")
                        launchIntent(command.prompt.trim())
                    }
                    performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    toastManager.show("Launched intent")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: SecurityException) {
                    toastManager.show("Phone permission denied. Go to Settings → Apps → Rite → Permissions → Phone → Allow")
                } catch (e: Exception) {
                    if (ENABLE_DEBUG_LOGGING) Log.e("Rite", "Intent launch failed: ${e.message}")
                    toastManager.show("Could not launch: ${e.message}")
                } finally {
                    withContext(NonCancellable + Dispatchers.Main) {
                        isProcessing.set(false)
                    }
                }
            }
            return
        }

        val mode = getCommandMode(command.trigger)
        val prefs = applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val providerType = prefs.getString("provider_type", "gemini") ?: "gemini"
        val model: String
        val endpoint: String

        if (providerType == "custom") {
            model = prefs.getString("custom_model", "") ?: ""
            endpoint = prefs.getString("custom_endpoint", "") ?: ""
            if (model.isBlank() || endpoint.isBlank()) {
                serviceScope.launch { toastManager.show("Custom provider not configured. Set endpoint and model in Settings.") }
                isProcessing.set(false)
                return
            }
        } else {
            model = prefs.getString("model", "gemini-2.5-flash-lite") ?: "gemini-2.5-flash-lite"
            endpoint = ""
        }

        currentJob = serviceScope.launch {
            val originalText = text
            var spinnerJob: Job? = null
            try {
                withTimeout(AI_COMMAND_TIMEOUT_MS) {
                    val maxAttempts = keyManager.getKeys().size.coerceAtLeast(1)
                    var lastErrorMsg: String? = null
                    var succeeded = false

                    for (attempt in 0 until maxAttempts) {
                        val key = keyManager.getNextKey() ?: break

                        if (spinnerJob == null) {
                            spinnerJob = textHelper.startInlineSpinner(source, originalText)
                        }

                        val result = if (providerType == "custom") {
                            openAIClient.generate(command.prompt, text, key, model, DEFAULT_TEMPERATURE, endpoint)
                        } else {
                            client.generate(command.prompt, text, key, model, DEFAULT_TEMPERATURE)
                        }

                        if (result.isSuccess) {
                            spinnerJob?.cancel(); spinnerJob = null
                            lastOriginalText = originalText
                            textHelper.replaceText(source, applyMode(originalText, result.getOrThrow(), mode))
                            performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            succeeded = true
                            break
                        }

                        val msg = result.exceptionOrNull()?.message ?: ""
                        lastErrorMsg = msg
                        val isRateLimit = msg.contains("Rate limit") || msg.contains("rate limit")
                        val isInvalidKey = msg.contains("Invalid API key", ignoreCase = true) || msg.contains("API key not valid", ignoreCase = true)

                        when {
                            isRateLimit -> {
                                val seconds = Regex("retry after (\\d+)s").find(msg)?.groupValues?.get(1)?.toLongOrNull() ?: 60
                                keyManager.reportRateLimit(key, seconds)
                            }
                            isInvalidKey -> keyManager.markInvalid(key)
                            else -> break
                        }
                    }

                    if (!succeeded) {
                        spinnerJob?.cancel(); spinnerJob = null
                        textHelper.replaceText(source, originalText)
                        performHapticFeedback(HapticFeedbackConstants.REJECT)
                        if (lastErrorMsg != null) {
                            toastManager.show("Rite Error: $lastErrorMsg")
                        } else {
                            val waitMs = keyManager.getShortestWaitTimeMs()
                            when {
                                waitMs != null -> {
                                    val waitSec = ((waitMs + 999) / 1000).coerceAtLeast(1)
                                    toastManager.show("API key rate limited. Try again in ${waitSec}s")
                                }
                                keyManager.getKeys().isEmpty() -> toastManager.show("No API keys configured")
                                else -> toastManager.show("All API keys are invalid. Please check your keys")
                            }
                        }
                    }
                }
            } catch (e: TimeoutCancellationException) {
                spinnerJob?.cancel()
                try { textHelper.replaceText(source, originalText) } catch (_: Exception) {}
                toastManager.show("Request timed out")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                spinnerJob?.cancel()
                try { textHelper.replaceText(source, originalText) } catch (_: Exception) {
                    toastManager.show("Could not restore original text")
                }
                toastManager.show("Rite Error: ${e.message}")
            } finally {
                withContext(NonCancellable + Dispatchers.Main) {
                    spinnerJob?.cancel()
                    isProcessing.set(false)
                }
            }
        }
    }

    private fun launchIntent(prompt: String) {
        if (ENABLE_DEBUG_LOGGING) Log.d("Rite", "launchIntent: ${prompt.split(":").firstOrNull() ?: "unknown"}://***")
        val intent = when {
            prompt.startsWith("app:") -> {
                val pkg = prompt.removePrefix("app:").trim()
                if (ENABLE_DEBUG_LOGGING) Log.d("Rite", "  → launching app: $pkg")
                val launchIntent = applicationContext.packageManager.getLaunchIntentForPackage(pkg)
                    ?: throw IllegalArgumentException("App not found: $pkg")
                launchIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                if (ENABLE_DEBUG_LOGGING) Log.d("Rite", "  → starting")
                launchIntent
            }
            prompt.startsWith("tel:") -> {
                if (ENABLE_DEBUG_LOGGING) Log.d("Rite", "  → calling: ***${prompt.takeLast(4)}")
                android.content.Intent(android.content.Intent.ACTION_CALL, android.net.Uri.parse(prompt))
            }
            prompt.startsWith("sms:") -> {
                if (ENABLE_DEBUG_LOGGING) Log.d("Rite", "  → SMS: ***${prompt.takeLast(4)}")
                android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse(prompt))
            }
            prompt.startsWith("mailto:") -> {
                if (ENABLE_DEBUG_LOGGING) Log.d("Rite", "  → email to: ***")
                android.content.Intent(android.content.Intent.ACTION_SENDTO, android.net.Uri.parse(prompt))
            }
            prompt.startsWith("https://") || prompt.startsWith("http://") -> {
                if (ENABLE_DEBUG_LOGGING) Log.d("Rite", "  → opening URL")
                android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(prompt))
            }
            else -> throw IllegalArgumentException("Unknown intent type: $prompt")
        }
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        if (ENABLE_DEBUG_LOGGING) Log.d("Rite", "  → startActivity")
        startActivity(intent)
    }

    private fun handleUndo(source: AccessibilityNodeInfo, currentText: String) {
        currentJob = serviceScope.launch {
            try {
                val previousText = lastOriginalText
                if (previousText == null) {
                    textHelper.replaceText(source, currentText)
                    performHapticFeedback(HapticFeedbackConstants.REJECT)
                    toastManager.show("Nothing to undo")
                } else {
                    lastOriginalText = currentText
                    textHelper.replaceText(source, previousText)
                    performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toastManager.show("Could not undo")
            } finally {
                withContext(NonCancellable + Dispatchers.Main) {
                    isProcessing.set(false)
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun performHapticFeedback(feedbackType: Int) {
        handler.post {
            try {
                val vibrator = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager).defaultVibrator
                } else {
                    getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
                }

                val effect = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    when (feedbackType) {
                        HapticFeedbackConstants.CONFIRM -> android.os.VibrationEffect.createPredefined(android.os.VibrationEffect.EFFECT_TICK)
                        HapticFeedbackConstants.REJECT -> android.os.VibrationEffect.createPredefined(android.os.VibrationEffect.EFFECT_DOUBLE_CLICK)
                        else -> android.os.VibrationEffect.createOneShot(50, android.os.VibrationEffect.DEFAULT_AMPLITUDE)
                    }
                } else {
                    android.os.VibrationEffect.createOneShot(50, android.os.VibrationEffect.DEFAULT_AMPLITUDE)
                }

                vibrator.vibrate(effect)
            } catch (_: Exception) {}
        }
    }

    override fun onInterrupt() {
        isProcessing.set(false)
        currentJob?.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        toastManager.dismiss()
        serviceScope.cancel()
    }

    private companion object {
        const val TRIGGER_REFRESH_INTERVAL_MS = 5_000L
        const val DEFAULT_TEMPERATURE = 0.5
        const val ENABLE_DEBUG_LOGGING = false
        const val AI_COMMAND_TIMEOUT_MS = 90_000L
        val INTENT_PREFIXES = listOf("app:", "tel:", "sms:", "mailto:", "https://", "http://")
        val LOCAL_COMMANDS = setOf(
            "cp", "ct", "pt", "del",
            "upper", "lower", "title",
            "date", "time", "count",
            "trim", "join", "split", "sort", "dedupe",
            "upside", "mirror", "bold", "italic",
            "rot13", "md5", "reverse"
        )
    }
}
