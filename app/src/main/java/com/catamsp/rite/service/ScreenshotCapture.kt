package com.catamsp.rite.service

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.os.Build
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import kotlin.coroutines.resume

object ScreenshotCapture {

    private const val TAG = "ScreenshotCapture"
    private const val JPEG_QUALITY = 80
    private const val MAX_DIMENSION = 1280

    data class ScreenshotResult(val base64Jpeg: String, val width: Int, val height: Int)

    suspend fun capture(service: AccessibilityService): Result<ScreenshotResult> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return Result.failure(Exception("Screenshot requires Android 11+"))
        }

        return try {
            val hardwareBuffer = takeScreenshotAsync(service)
                ?: return Result.failure(Exception("Screenshot capture returned null"))

            try {
                val bitmap = hardwareBufferToBitmap(hardwareBuffer)
                    ?: return Result.failure(Exception("Failed to process screenshot"))

                try {
                    val scaled = scaleIfNeeded(bitmap)
                    val width = scaled.width
                    val height = scaled.height
                    val base64 = bitmapToBase64(scaled)
                    scaled.recycle()
                    bitmap.recycle()
                    Result.success(ScreenshotResult(base64, width, height))
                } catch (e: Exception) {
                    bitmap.recycle()
                    throw e
                }
            } finally {
                hardwareBuffer.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Screenshot capture failed: ${e.message}")
            Result.failure(e)
        }
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
    private suspend fun takeScreenshotAsync(
        service: AccessibilityService
    ): android.hardware.HardwareBuffer? = suspendCancellableCoroutine { cont ->
        val executor = Executors.newSingleThreadExecutor()
        service.takeScreenshot(
            android.view.Display.DEFAULT_DISPLAY,
            executor,
            object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    if (cont.isActive) cont.resume(result.hardwareBuffer)
                }

                override fun onFailure(errorCode: Int) {
                    Log.e(TAG, "takeScreenshot failed with code: $errorCode")
                    if (cont.isActive) cont.resume(null)
                }
            }
        )
    }

    private fun hardwareBufferToBitmap(buffer: android.hardware.HardwareBuffer): Bitmap? {
        val colorSpace = ColorSpace.get(ColorSpace.Named.SRGB)
        return Bitmap.wrapHardwareBuffer(buffer, colorSpace)
    }

    private fun scaleIfNeeded(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= MAX_DIMENSION && height <= MAX_DIMENSION) {
            return bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: bitmap
        }

        val ratio = MAX_DIMENSION.toFloat() / maxOf(width, height)
        val newWidth = (width * ratio).toInt()
        val newHeight = (height * ratio).toInt()
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val softwareBitmap = if (bitmap.config == Bitmap.Config.HARDWARE) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: bitmap
        } else {
            bitmap
        }

        val outputStream = ByteArrayOutputStream()
        softwareBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, outputStream)
        if (softwareBitmap !== bitmap) softwareBitmap.recycle()
        val bytes = outputStream.toByteArray()
        return android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
    }
}
