package com.catamsp.rite.api

import org.json.JSONObject

data class GenerateResult(val text: String, val structuredOutputFailed: Boolean = false)

object ApiClientUtils {

    private val HTTP_PREFIX_REGEX = Regex("""^HTTP_\d+:\s*""")
    private val HTTP_CODE_REGEX = Regex("""HTTP[_ ](\d+)""")

    fun tryExtractStructuredText(rawText: String): Pair<String?, Boolean> {
        return try {
            val parsed = JSONObject(rawText)
            val extracted = parsed.optString("text", "")
            if (extracted.isNotBlank()) Pair(extracted, false) else Pair(null, false)
        } catch (_: Exception) {
            Pair(null, true)
        }
    }

    fun stripHttpPrefix(result: Result<String>): Result<String> {
        if (result.isFailure) {
            val msg = result.exceptionOrNull()?.message ?: ""
            val cleaned = msg.replaceFirst(HTTP_PREFIX_REGEX, "")
            if (cleaned != msg) return Result.failure(Exception(cleaned))
        }
        return result
    }

    fun readResponseBounded(stream: java.io.InputStream, maxBytes: Int = 1_048_576): String {
        val buffer = ByteArray(8192)
        val output = StringBuilder()
        var totalRead = 0
        stream.use { input ->
            while (totalRead < maxBytes) {
                val bytesRead = input.read(buffer)
                if (bytesRead == -1) break
                output.append(String(buffer, 0, bytesRead, Charsets.UTF_8))
                totalRead += bytesRead
            }
        }
        return output.toString()
    }

    fun readErrorBody(stream: java.io.InputStream?): String {
        return stream?.use { input ->
            val reader = java.io.BufferedReader(java.io.InputStreamReader(input))
            reader.use { it.readText() }
        } ?: ""
    }

    fun isTransientNetwork(errorMsg: String): Boolean {
        val lower = errorMsg.lowercase()
        return lower.contains("timeout") || lower.contains("timed out") ||
            lower.contains("connection reset") || lower.contains("broken pipe") ||
            lower.contains("econnreset") || lower.contains("econnrefused")
    }

    fun extractHttpCode(errorMsg: String): Int? {
        return HTTP_CODE_REGEX.find(errorMsg)?.groupValues?.get(1)?.toIntOrNull()
    }
}
