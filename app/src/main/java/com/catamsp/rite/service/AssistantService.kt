package com.catamsp.rite.service

import android.accessibilityservice.AccessibilityService
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import android.widget.Toast
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicBoolean

class AssistantService : AccessibilityService() {

    private enum class CommandMode { REPLACE, APPEND, PREPEND }

    private lateinit var keyManager: KeyManager
    private lateinit var commandManager: CommandManager
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
    private var currentOverlayToast: View? = null
    private var dismissRunnable: Runnable? = null
    private var dismissAnimator: AnimatorSet? = null
    private var enterAnimator: AnimatorSet? = null

    private fun dp(value: Int): Int {
        val density = resources.displayMetrics.density
        return (value * density + 0.5f).toInt()
    }

    private companion object {
        const val TRIGGER_REFRESH_INTERVAL_MS = 5_000L
        const val DEFAULT_TEMPERATURE = 0.5
        const val ENABLE_DEBUG_LOGGING = false // Set to false for release builds
        val SPINNER_FRAMES = arrayOf("⣷", "⣯", "⣟", "⡿", "⢿", "⣻", "⣽", "⣾")
        const val TOAST_BACKGROUND_COLOR = 0xE6323232.toInt()
        const val TOAST_DURATION_MS = 3500L
        const val TOAST_BOTTOM_MARGIN_DP = 64
        val TOAST_ANIM_DURATION_MS = 300L
        val TOAST_SLIDE_DISTANCE_DP = 40
        const val AI_COMMAND_TIMEOUT_MS = 90_000L  // 90 seconds max for AI response
        // Note: uses "app:" not "app://" — matches findCommand + launchIntent
        val INTENT_PREFIXES = listOf("app:", "tel:", "sms:", "mailto:", "https://", "http://")
        // Note: "undo" is NOT here — it's handled separately before the LOCAL_COMMANDS check
        val LOCAL_COMMANDS = setOf(
            "cp", "ct", "pt", "del",
            "upper", "lower", "title",
            "date", "time", "count",
            "trim", "join", "split", "sort", "dedupe",
            "upside", "mirror", "bold", "italic",
            "rot13", "md5", "reverse"
        )
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        keyManager = KeyManager(applicationContext)
        commandManager = CommandManager(applicationContext)
        updateTriggers()
    }

    private fun updateTriggers() {
        cachedPrefix = commandManager.getTriggerPrefix()
        val cmds = commandManager.getCommands()
        triggerLastChars = cmds.mapNotNull { it.trigger.lastOrNull() }.toSet()
        lastTriggerRefresh = System.currentTimeMillis()
    }

    private fun getCommandMode(trigger: String): CommandMode = when {
        trigger.startsWith("!") -> CommandMode.APPEND
        trigger.startsWith("+") -> CommandMode.PREPEND
        else -> CommandMode.REPLACE
    }

    private fun applyMode(original: String, result: String, mode: CommandMode): String = when (mode) {
        CommandMode.REPLACE -> result
        CommandMode.APPEND -> if (original.isEmpty()) result else "$original\n$result"
        CommandMode.PREPEND -> if (original.isEmpty()) result else "$result\n$original"
    }

    /** Extract bare command name regardless of which mode prefix is present. */
    private fun extractCmdName(trigger: String): String = when {
        trigger.startsWith(cachedPrefix) -> trigger.removePrefix(cachedPrefix)
        trigger.startsWith("!") -> trigger.removePrefix("!")
        trigger.startsWith("+") -> trigger.removePrefix("+")
        else -> trigger
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED) return
        if (isProcessing.get()) { if (ENABLE_DEBUG_LOGGING) Log.d("Rite", "SKIP: isProcessing=true"); return }

        // Ignore the Rite app's own text fields — prevents intercepting command setup
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

        // ?undo — handled separately (no mode needed)
        if (command.trigger.endsWith("undo") && command.isBuiltIn) {
            if (source.isPassword) { if (ENABLE_DEBUG_LOGGING) Log.d("Rite", "SKIP: password field"); return }
            isProcessing.set(true)
            currentJob?.cancel()
            handleUndo(source, cleanText)
            return
        }

        val mode = getCommandMode(command.trigger)
        val cmdName = extractCmdName(command.trigger)
        if (ENABLE_DEBUG_LOGGING) Log.d("Rite", "mode=$mode, cmdName='$cmdName'")

        if (command.isBuiltIn && LOCAL_COMMANDS.contains(cmdName)) {
            if (source.isPassword) { if (ENABLE_DEBUG_LOGGING) Log.d("Rite", "SKIP: password field"); return }
            if (ENABLE_DEBUG_LOGGING) Log.d("Rite", "HANDLING: local command '$cmdName'")
            isProcessing.set(true)
            currentJob?.cancel()
            handleLocalCommand(source, cleanText, cmdName, mode)
            return
        }

        // Allow custom intent commands even if cleanText is empty
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
        // Handle intent-style custom commands (app:, tel:, sms:, mailto:, https://, http://)
        if (INTENT_PREFIXES.any { command.prompt.trimStart().startsWith(it) }) {
            if (ENABLE_DEBUG_LOGGING) Log.d("Rite", "INTENT PATH: launching intent command")
            currentJob = serviceScope.launch {
                try {
                    withContext(Dispatchers.Main) {
                        replaceText(source, "")
                        launchIntent(command.prompt.trim())
                    }
                    performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    showToast("Launched intent")
                } catch (e: CancellationException) {
                    throw e
                } catch (e: SecurityException) {
                    showToast("Phone permission denied. Go to Settings → Apps → Rite → Permissions → Phone → Allow")
                } catch (e: Exception) {
                    if (ENABLE_DEBUG_LOGGING) Log.e("Rite", "Intent launch failed: ${e.message}")
                    showToast("Could not launch: ${e.message}")
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
                serviceScope.launch { showToast("Custom provider not configured. Set endpoint and model in Settings.") }
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
                            spinnerJob = startInlineSpinner(source, originalText)
                        }

                        val result = if (providerType == "custom") {
                            openAIClient.generate(command.prompt, text, key, model, DEFAULT_TEMPERATURE, endpoint)
                        } else {
                            client.generate(command.prompt, text, key, model, DEFAULT_TEMPERATURE)
                        }

                        if (result.isSuccess) {
                            spinnerJob?.cancel(); spinnerJob = null
                            lastOriginalText = originalText
                            replaceText(source, applyMode(originalText, result.getOrThrow(), mode))
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
                        replaceText(source, originalText)
                        performHapticFeedback(HapticFeedbackConstants.REJECT)
                        if (lastErrorMsg != null) {
                            showToast("Rite Error: $lastErrorMsg")
                        } else {
                            val waitMs = keyManager.getShortestWaitTimeMs()
                            when {
                                waitMs != null -> {
                                    val waitSec = ((waitMs + 999) / 1000).coerceAtLeast(1)
                                    showToast("API key rate limited. Try again in ${waitSec}s")
                                }
                                keyManager.getKeys().isEmpty() -> showToast("No API keys configured")
                                else -> showToast("All API keys are invalid. Please check your keys")
                            }
                        }
                    }
                }
            } catch (e: TimeoutCancellationException) {
                spinnerJob?.cancel()
                try { replaceText(source, originalText) } catch (_: Exception) {}
                showToast("Request timed out")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                spinnerJob?.cancel()
                try { replaceText(source, originalText) } catch (_: Exception) {
                    showToast("Could not restore original text")
                }
                showToast("Rite Error: ${e.message}")
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
        val intent: Intent = when {
            prompt.startsWith("app:") -> {
                val pkg = prompt.removePrefix("app:").trim()
                if (ENABLE_DEBUG_LOGGING) Log.d("Rite", "  → launching app: $pkg")

                val launchIntent = applicationContext.packageManager.getLaunchIntentForPackage(pkg)
                    ?: throw IllegalArgumentException("App not found: $pkg")

                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (ENABLE_DEBUG_LOGGING) Log.d("Rite", "  → starting")
                launchIntent
            }
            prompt.startsWith("tel:") -> {
                if (ENABLE_DEBUG_LOGGING) Log.d("Rite", "  → calling: ***${prompt.takeLast(4)}")
                Intent(Intent.ACTION_CALL, Uri.parse(prompt))
            }
            prompt.startsWith("sms:") -> {
                if (ENABLE_DEBUG_LOGGING) Log.d("Rite", "  → SMS: ***${prompt.takeLast(4)}")
                Intent(Intent.ACTION_SENDTO, Uri.parse(prompt))
            }
            prompt.startsWith("mailto:") -> {
                if (ENABLE_DEBUG_LOGGING) Log.d("Rite", "  → email to: ***")
                Intent(Intent.ACTION_SENDTO, Uri.parse(prompt))
            }
            prompt.startsWith("https://") || prompt.startsWith("http://") -> {
                if (ENABLE_DEBUG_LOGGING) Log.d("Rite", "  → opening URL")
                Intent(Intent.ACTION_VIEW, Uri.parse(prompt))
            }
            else -> throw IllegalArgumentException("Unknown intent type: $prompt")
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (ENABLE_DEBUG_LOGGING) Log.d("Rite", "  → startActivity")
        startActivity(intent)
    }

    private fun handleUndo(source: AccessibilityNodeInfo, currentText: String) {
        currentJob = serviceScope.launch {
            try {
                val previousText = lastOriginalText
                if (previousText == null) {
                    replaceText(source, currentText)
                    performHapticFeedback(HapticFeedbackConstants.REJECT)
                    showToast("Nothing to undo")
                } else {
                    lastOriginalText = currentText
                    replaceText(source, previousText)
                    performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showToast("Could not undo")
            } finally {
                withContext(NonCancellable + Dispatchers.Main) {
                    isProcessing.set(false)
                }
            }
        }
    }

    private fun handleLocalCommand(
        source: AccessibilityNodeInfo,
        cleanText: String,
        commandName: String,
        mode: CommandMode = CommandMode.REPLACE
    ) {
        currentJob = serviceScope.launch {
            try {
                when (commandName) {
                    // ── Clipboard / meta operations (mode not applied) ──────────────
                    "cp" -> {
                        copyToClipboard(cleanText)
                        replaceText(source, cleanText)
                        performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) showToast("Copied to clipboard")
                    }
                    "ct" -> {
                        copyToClipboard(cleanText)
                        replaceText(source, "")
                        performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) showToast("Cut to clipboard")
                    }
                    "pt" -> {
                        pasteFromClipboard(source, cleanText, mode)
                    }
                    "del" -> {
                        replaceText(source, "")
                        performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    }

                    // ── Insert commands (date/time get appended to existing text) ──
                    "date" -> {
                        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault())
                        val stamp = fmt.format(java.util.Date())
                        val result = when (mode) {
                            CommandMode.REPLACE -> if (cleanText.isEmpty()) stamp else "$cleanText $stamp"
                            CommandMode.APPEND -> if (cleanText.isEmpty()) stamp else "$cleanText\n$stamp"
                            CommandMode.PREPEND -> if (cleanText.isEmpty()) stamp else "$stamp\n$cleanText"
                        }
                        replaceText(source, result)
                        performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    }
                    "time" -> {
                        val fmt = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                        val stamp = fmt.format(java.util.Date())
                        val result = when (mode) {
                            CommandMode.REPLACE -> if (cleanText.isEmpty()) stamp else "$cleanText $stamp"
                            CommandMode.APPEND -> if (cleanText.isEmpty()) stamp else "$cleanText\n$stamp"
                            CommandMode.PREPEND -> if (cleanText.isEmpty()) stamp else "$stamp\n$cleanText"
                        }
                        replaceText(source, result)
                        performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    }
                    "count" -> {
                        if (cleanText.isEmpty()) {
                            replaceText(source, "")
                            performHapticFeedback(HapticFeedbackConstants.REJECT)
                            showToast("No text to count")
                        } else {
                            val words = cleanText.split("\\s+".toRegex()).filter { it.isNotEmpty() }.size
                            val info = "[${words} words | ${cleanText.length} chars]"
                            val result = when (mode) {
                                CommandMode.REPLACE -> "$cleanText  $info"
                                CommandMode.APPEND -> "$cleanText  $info"
                                CommandMode.PREPEND -> "$info  $cleanText"
                            }
                            replaceText(source, result)
                            performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                        }
                    }

                    // ── Text transformations (mode applied) ────────────────────────
                    "upper" -> {
                        replaceText(source, applyMode(cleanText, cleanText.uppercase(), mode))
                        performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    }
                    "lower" -> {
                        replaceText(source, applyMode(cleanText, cleanText.lowercase(), mode))
                        performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    }
                    "title" -> {
                        val result = cleanText.split(" ").joinToString(" ") { word ->
                            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
                        }
                        replaceText(source, applyMode(cleanText, result, mode))
                        performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    }
                    "trim" -> {
                        val result = cleanText
                            .replace(Regex("\\s+"), " ")  // Replace any whitespace sequence with single space
                            .trim()
                        replaceText(source, applyMode(cleanText, result, mode))
                        performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    }
                    "join" -> {
                        val result = cleanText.replace(Regex("\n+"), " ")
                        replaceText(source, applyMode(cleanText, result, mode))
                        performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    }
                    "split" -> {
                        val result = cleanText.chunked(80).joinToString("\n")
                        replaceText(source, applyMode(cleanText, result, mode))
                        performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    }
                    "sort" -> {
                        val result = cleanText.lines().sorted().joinToString("\n")
                        replaceText(source, applyMode(cleanText, result, mode))
                        performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    }
                    "dedupe" -> {
                        val result = cleanText.lines().distinct().joinToString("\n")
                        replaceText(source, applyMode(cleanText, result, mode))
                        performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    }
                    "upside" -> {
                        val map = mapOf(
                            'a' to 'ɐ', 'b' to 'q', 'c' to 'ɔ', 'd' to 'p', 'e' to 'ǝ',
                            'f' to 'ɟ', 'g' to 'ƃ', 'h' to 'ɥ', 'i' to 'ᴉ', 'j' to 'ɾ',
                            'k' to 'ʞ', 'l' to 'l', 'm' to 'ɯ', 'n' to 'u', 'o' to 'o',
                            'p' to 'd', 'q' to 'b', 'r' to 'ɹ', 's' to 's', 't' to 'ʇ',
                            'u' to 'n', 'v' to 'ʌ', 'w' to 'ʍ', 'x' to 'x', 'y' to 'ʎ', 'z' to 'z'
                        )
                        val result = cleanText.map { map[it.lowercaseChar()] ?: it }.reversed().joinToString("")
                        replaceText(source, applyMode(cleanText, result, mode))
                        performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    }
                    "mirror" -> {
                        replaceText(source, applyMode(cleanText, cleanText.reversed(), mode))
                        performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    }
                    "bold" -> {
                        // Unicode Mathematical Bold — requires surrogate pairs (code points > U+FFFF)
                        val result = cleanText.map { ch ->
                            when {
                                ch in 'a'..'z' -> String(Character.toChars(0x1D41A + (ch - 'a')))
                                ch in 'A'..'Z' -> String(Character.toChars(0x1D400 + (ch - 'A')))
                                else -> ch.toString()
                            }
                        }.joinToString("")
                        replaceText(source, applyMode(cleanText, result, mode))
                        performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    }
                    "italic" -> {
                        // Unicode Mathematical Italic — requires surrogate pairs (code points > U+FFFF)
                        val result = cleanText.map { ch ->
                            when {
                                ch in 'a'..'z' -> String(Character.toChars(0x1D44E + (ch - 'a')))
                                ch in 'A'..'Z' -> String(Character.toChars(0x1D434 + (ch - 'A')))
                                else -> ch.toString()
                            }
                        }.joinToString("")
                        replaceText(source, applyMode(cleanText, result, mode))
                        performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    }
                    "rot13" -> {
                        val result = cleanText.map { ch ->
                            when {
                                ch in 'a'..'z' -> ('a' + (ch - 'a' + 13) % 26).toChar()
                                ch in 'A'..'Z' -> ('A' + (ch - 'A' + 13) % 26).toChar()
                                else -> ch
                            }
                        }.joinToString("")
                        replaceText(source, applyMode(cleanText, result, mode))
                        performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    }
                    "md5" -> {
                        val md = java.security.MessageDigest.getInstance("MD5")
                        val hash = md.digest(cleanText.toByteArray()).joinToString("") { "%02x".format(it) }
                        val hashText = "[md5: $hash]"
                        val result = when (mode) {
                            CommandMode.REPLACE -> "$cleanText  $hashText"
                            CommandMode.APPEND -> if (cleanText.isEmpty()) hashText else "$cleanText  $hashText"
                            CommandMode.PREPEND -> if (cleanText.isEmpty()) hashText else "$hashText  $cleanText"
                        }
                        replaceText(source, result)
                        performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    }
                    "reverse" -> {
                        val result = cleanText.split(" ").reversed().joinToString(" ")
                        replaceText(source, applyMode(cleanText, result, mode))
                        performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showToast("Command failed")
            } finally {
                withContext(NonCancellable + Dispatchers.Main) {
                    isProcessing.set(false)
                }
            }
        }
    }

    /**
     * Paste clipboard content using Accessibility Service ACTION_PASTE.
     * Works around Android 13+ clipboard restrictions for background services.
     */
    private fun pasteFromClipboard(
        source: AccessibilityNodeInfo,
        cleanText: String,
        mode: CommandMode
    ) {
        handler.post {
            try {
                val textBeforePaste = source.text?.toString() ?: ""

                when (mode) {
                    CommandMode.REPLACE -> {
                        // Clear text, then paste at start
                        replaceTextSync(source, "")
                        source.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                    }
                    CommandMode.APPEND -> {
                        // Move cursor to end, then paste
                        val textLen = source.text?.length ?: 0
                        val selectionArgs = Bundle().apply {
                            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, textLen)
                            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, textLen)
                        }
                        source.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selectionArgs)
                        source.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                    }
                    CommandMode.PREPEND -> {
                        // Move cursor to start, then paste
                        val selectionArgs = Bundle().apply {
                            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0)
                            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, 0)
                        }
                        source.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selectionArgs)
                        source.performAction(AccessibilityNodeInfo.ACTION_PASTE)
                    }
                }

                // Check if paste actually added content
                handler.postDelayed({
                    source.refresh()
                    val textAfter = source.text?.toString() ?: ""
                    val isEmptyResult = when (mode) {
                        CommandMode.REPLACE -> textAfter.isEmpty()
                        CommandMode.APPEND, CommandMode.PREPEND -> textAfter == textBeforePaste
                    }

                    if (isEmptyResult) {
                        // Clipboard was empty — restore original text
                        replaceTextSync(source, cleanText)
                        performHapticFeedback(HapticFeedbackConstants.REJECT)
                        serviceScope.launch { showToast("Clipboard is empty") }
                    } else {
                        performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                    }
                }, 300)
            } catch (e: Exception) {
                android.util.Log.e("Rite", "Paste failed: ${e.message}")
                serviceScope.launch { showToast("Paste failed") }
            }
        }
    }

    /** Synchronous text replacement (no coroutine needed, runs on main thread). */
    private fun replaceTextSync(source: AccessibilityNodeInfo, newText: String) {
        val bundle = Bundle()
        bundle.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText)
        source.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)
    }

    private fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Rite", text))
    }

    private suspend fun replaceText(source: AccessibilityNodeInfo, newText: String) = withContext(Dispatchers.Main) {
        source.refresh()
        val bundle = Bundle()
        bundle.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText)
        val success = source.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)

        if (!success) {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val oldClip = clipboard.primaryClip
            clipboard.setPrimaryClip(ClipData.newPlainText("Rite Result", newText))

            val selectAllArgs = Bundle().apply {
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0)
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, source.text?.length ?: 0)
            }
            source.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selectAllArgs)
            source.performAction(AccessibilityNodeInfo.ACTION_PASTE)

            handler.postDelayed({
                if (oldClip != null) clipboard.setPrimaryClip(oldClip)
            }, 500)
        }
    }

    private fun setFieldText(source: AccessibilityNodeInfo, text: String) {
        val bundle = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        source.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, bundle)
    }

    private fun startInlineSpinner(source: AccessibilityNodeInfo, baseText: String): Job {
        return serviceScope.launch(Dispatchers.Main) {
            var frameIndex = 0
            while (isActive) {
                setFieldText(source, "$baseText ${SPINNER_FRAMES[frameIndex]}")
                frameIndex = (frameIndex + 1) % SPINNER_FRAMES.size
                delay(200)
            }
        }
    }

    private suspend fun showToast(msg: String) = withContext(Dispatchers.Main) {
        dismissOverlayToast()
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val textView = TextView(applicationContext).apply {
            text = msg
            setTextColor(Color.WHITE)
            textSize = 14f
            setPadding(dp(24), dp(12), dp(24), dp(12))
            maxWidth = (resources.displayMetrics.widthPixels * 0.85).toInt()
            background = GradientDrawable().apply {
                setColor(TOAST_BACKGROUND_COLOR)
                cornerRadius = dp(24).toFloat()
            }
            gravity = Gravity.CENTER
            alpha = 0f
            translationY = dp(TOAST_SLIDE_DISTANCE_DP).toFloat()
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dp(TOAST_BOTTOM_MARGIN_DP)
            windowAnimations = 0
        }

        try {
            wm.addView(textView, params)
            currentOverlayToast = textView

            AnimatorSet().apply {
                playTogether(
                    ObjectAnimator.ofFloat(textView, View.ALPHA, 0f, 1f),
                    ObjectAnimator.ofFloat(textView, View.TRANSLATION_Y, dp(TOAST_SLIDE_DISTANCE_DP).toFloat(), 0f)
                )
                duration = TOAST_ANIM_DURATION_MS
                interpolator = DecelerateInterpolator()
                start()
                enterAnimator = this
            }

            val runnable = Runnable { dismissOverlayToastAnimated() }
            dismissRunnable = runnable
            handler.postDelayed(runnable, TOAST_DURATION_MS)
        } catch (_: Exception) {
            Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
        }
    }

    private fun dismissOverlayToast() {
        dismissRunnable?.let { handler.removeCallbacks(it) }
        dismissRunnable = null
        enterAnimator?.cancel(); enterAnimator = null
        dismissAnimator?.cancel(); dismissAnimator = null
        currentOverlayToast?.let { view ->
            try {
                view.visibility = View.GONE
                (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(view)
            } catch (_: Exception) {}
            currentOverlayToast = null
        }
    }

    private fun dismissOverlayToastAnimated() {
        dismissRunnable?.let { handler.removeCallbacks(it) }
        dismissRunnable = null
        enterAnimator?.cancel(); enterAnimator = null
        dismissAnimator?.cancel()
        currentOverlayToast?.let { view ->
            try {
                val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
                dismissAnimator = AnimatorSet().apply {
                    playTogether(
                        ObjectAnimator.ofFloat(view, View.ALPHA, view.alpha, 0f),
                        ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, view.translationY, dp(TOAST_SLIDE_DISTANCE_DP).toFloat())
                    )
                    duration = TOAST_ANIM_DURATION_MS
                    interpolator = DecelerateInterpolator()
                    addListener(object : android.animation.AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: android.animation.Animator) {
                            view.visibility = View.GONE
                            try { wm.removeView(view) } catch (_: Exception) {}
                            dismissAnimator = null
                        }
                    })
                    start()
                }
            } catch (_: Exception) {}
            currentOverlayToast = null
        }
    }

    @Suppress("DEPRECATION")
    private fun performHapticFeedback(feedbackType: Int) {
        handler.post {
            try {
                // Get appropriate vibrator for API level
                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
                } else {
                    getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                }

                // Create vibration effect based on feedback type
                val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    when (feedbackType) {
                        HapticFeedbackConstants.CONFIRM -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK)
                        HapticFeedbackConstants.REJECT -> VibrationEffect.createPredefined(VibrationEffect.EFFECT_DOUBLE_CLICK)
                        else -> VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
                    }
                } else {
                    VibrationEffect.createOneShot(50, VibrationEffect.DEFAULT_AMPLITUDE)
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
        dismissOverlayToast()
        serviceScope.cancel()
    }
}
