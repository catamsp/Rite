package com.catamsp.rite.service

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.SharedPreferences
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
import com.catamsp.rite.model.ProviderType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
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
    private var currentJob: Job? = null
    private var debounceJob: Job? = null
    private var watchdogJob: Job? = null
    @Volatile
    private var lastOriginalText: String? = null

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == "custom_commands") {
            commandManager.invalidateCache()
        }
    }

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
        applicationContext.getSharedPreferences("commands", Context.MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(prefsListener)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) return
        if (isProcessing.get()) return

        if (event.packageName == packageName) return

        val source = event.source ?: return
        val text = source.text?.toString()
        if (text.isNullOrEmpty()) return

        debounceJob?.cancel()
        debounceJob = serviceScope.launch {
            delay(DEBOUNCE_MS)
            withContext(Dispatchers.Main) { processTextChange(source, text) }
        }
    }

    private fun processTextChange(source: AccessibilityNodeInfo, text: String) {
        if (isProcessing.get()) return

        val lastChar = text[text.length - 1]
        if (!lastChar.isLetterOrDigit() && !lastChar.isWhitespace()) {
            return
        }

        val command = commandManager.findCommand(text) ?: return

        val cleanText = text.substring(0, text.length - command.trigger.length).trim()

        if (command.trigger.endsWith("undo") && command.isBuiltIn) {
            if (source.isPassword) return
            isProcessing.set(true)
            startWatchdog()
            currentJob?.cancel()
            handleUndo(source, cleanText)
            return
        }

        val mode = getCommandMode(command.trigger)
        val cmdName = extractCmdName(command.trigger, commandManager.getTriggerPrefix())

        if (command.isBuiltIn && LOCAL_COMMANDS.contains(cmdName)) {
            if (source.isPassword) return
            isProcessing.set(true)
            startWatchdog()
            currentJob?.cancel()
            currentJob = localCommandExecutor.execute(source, cleanText, cmdName, mode, command.trigger) {
                withContext(NonCancellable + Dispatchers.Main) { isProcessing.set(false) }
                cancelWatchdog()
            }
            return
        }

        if (command.type == com.catamsp.rite.model.CommandType.TEXT_REPLACER) {
            if (source.isPassword) return
            isProcessing.set(true)
            startWatchdog()
            currentJob?.cancel()
            currentJob = serviceScope.launch {
                try {
                    withContext(Dispatchers.Main) {
                        val result = applyMode(cleanText, command.prompt, mode)
                        textHelper.replaceText(source, result)
                    }
                    performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    toastManager.show("Text replacement failed: ${e.message}")
                } finally {
                    withContext(NonCancellable + Dispatchers.Main) {
                        isProcessing.set(false)
                    }
                    cancelWatchdog()
                }
            }
            return
        }

        val isIntent = INTENT_PREFIXES.any { command.prompt.trimStart().startsWith(it) }
        if (!isIntent && (cleanText.isEmpty() || source.isPassword)) return
        if (isIntent && source.isPassword) return

        isProcessing.set(true)
        startWatchdog()
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
                    cancelWatchdog()
                }
            }
            return
        }

        val mode = getCommandMode(command.trigger)
        currentJob = serviceScope.launch {
            val prefs = applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
            val providerType = prefs.getString("provider_type", ProviderType.GEMINI) ?: ProviderType.GEMINI
            val temperature = prefs.getFloat("temperature", 0.5f).toDouble()
            val model: String
            val endpoint: String

            when (providerType) {
                ProviderType.CUSTOM -> {
                    model = prefs.getString("custom_model", "") ?: ""
                    endpoint = prefs.getString("custom_endpoint", "") ?: ""
                    if (model.isBlank() || endpoint.isBlank()) {
                        withContext(Dispatchers.Main) { toastManager.show("Custom provider not configured. Set endpoint and model in Settings.") }
                        isProcessing.set(false)
                        return@launch
                    }
                }
                ProviderType.GROQ -> {
                    model = prefs.getString("groq_model", "llama-3.3-70b-versatile") ?: "llama-3.3-70b-versatile"
                    endpoint = "https://api.groq.com/openai/v1"
                }
                else -> {
                    model = prefs.getString("model", "gemini-2.5-flash-lite") ?: "gemini-2.5-flash-lite"
                    endpoint = ""
                }
            }

            val originalText = text
            val useStructuredOutput = run {
                val disabledAt = prefs.getLong("structured_output_disabled_at", 0L)
                System.currentTimeMillis() - disabledAt > 86_400_000L
            }
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

                        val result = when (providerType) {
                            ProviderType.GROQ -> openAIClient.generate(command.prompt, text, key, model, temperature, endpoint, useStructuredOutput, useStructuredOutput)
                            ProviderType.CUSTOM -> openAIClient.generate(command.prompt, text, key, model, temperature, endpoint, useStructuredOutput, false)
                            else -> client.generate(command.prompt, text, key, model, temperature, useStructuredOutput)
                        }

                        if (result.isSuccess) {
                            val generateResult = result.getOrThrow()
                            spinnerJob!!.cancel()
                            spinnerJob = null
                            lastOriginalText = originalText
                            textHelper.replaceText(source, applyMode(originalText, generateResult.text, mode))
                            performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                            if (generateResult.structuredOutputFailed) {
                                prefs.edit().putLong("structured_output_disabled_at", System.currentTimeMillis()).apply()
                            }
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
                            toastManager.show(mapErrorMessage(lastErrorMsg))
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
                toastManager.show(mapErrorMessage(e.message ?: "Unknown error"))
            } finally {
                withContext(NonCancellable + Dispatchers.Main) {
                    spinnerJob?.cancel()
                    isProcessing.set(false)
                }
                cancelWatchdog()
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

    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = serviceScope.launch {
            delay(WATCHDOG_TIMEOUT_MS)
            if (isProcessing.compareAndSet(true, false)) {
                withContext(Dispatchers.Main) {
                    currentJob?.cancel()
                    toastManager.show("Operation timed out")
                }
            }
        }
    }

    private fun cancelWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = null
    }

    override fun onInterrupt() {
        isProcessing.set(false)
        watchdogJob?.cancel()
        debounceJob?.cancel()
        currentJob?.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        watchdogJob?.cancel()
        debounceJob?.cancel()
        toastManager.dismiss()
        try {
            applicationContext.getSharedPreferences("commands", Context.MODE_PRIVATE)
                .unregisterOnSharedPreferenceChangeListener(prefsListener)
        } catch (_: Exception) {}
        serviceScope.cancel()
    }

    private fun mapErrorMessage(raw: String): String {
        val lower = raw.lowercase()
        return when {
            lower.contains("permission_denied") || lower.contains("permission denied") ->
                "Your API key doesn't have access to this model."
            lower.contains("invalid api key") || lower.contains("api key not valid") || lower.contains("api_key_invalid") ->
                "Invalid API key. Please check your key in Settings."
            lower.contains("rate limit") || lower.contains("resource_exhausted") || lower.contains("quota") ->
                "Rate limited. Try again shortly."
            lower.contains("model not found") || lower.contains("model_not_found") || lower.contains("not found for api version") ->
                "Model not found. Check your model selection in Settings."
            lower.contains("safety") || lower.contains("content_filter") || lower.contains("recitation") ||
                lower.contains("blocked by safety") || lower.contains("finish_reason: safety") ||
                lower.contains("failed_generation") ->
                "Response blocked by safety filters. Try rephrasing."
            lower.contains("empty response") || lower.contains("no content found") || lower.contains("no choices found") ->
                "Model returned an empty response. Try again."
            lower.contains("timeout") || lower.contains("timed out") ->
                "Request timed out. Check your connection."
            lower.contains("unable to resolve host") || lower.contains("no address associated") ||
                lower.contains("network is unreachable") || lower.contains("no route to host") ||
                lower.contains("software caused connection abort") || lower.contains("connection reset") ||
                lower.contains("broken pipe") ->
                "No internet connection."
            lower.contains("connection refused") || lower.contains("connect failed") ->
                "Could not reach the API. Check your endpoint URL."
            lower.contains("bad request") ->
                "Request failed. Check your settings."
            else -> raw
        }
    }

    private companion object {
        const val DEBOUNCE_MS = 150L
        const val ENABLE_DEBUG_LOGGING = false
        const val AI_COMMAND_TIMEOUT_MS = 90_000L
        const val WATCHDOG_TIMEOUT_MS = 120_000L
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
