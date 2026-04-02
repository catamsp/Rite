package com.catamsp.rite.manager

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.provider.MediaStore
import com.catamsp.rite.model.AppShortcut
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class AppManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("rite_app_launchers", Context.MODE_PRIVATE)
    private val gson = Gson()
    
    companion object {
        const val KEY_SHORTCUTS = "app_shortcuts"
    }

    fun getShortcuts(): List<AppShortcut> {
        val json = prefs.getString(KEY_SHORTCUTS, null) ?: return emptyList()
        return try {
            val jsonArray = org.json.JSONArray(json)
            val list = mutableListOf<AppShortcut>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val appName = obj.getString("appName")
                val packageName = if (obj.has("packageName") && !obj.isNull("packageName")) obj.getString("packageName") else null
                val triggerCode = obj.getString("triggerCode")
                val intentAction = if (obj.has("intentAction") && !obj.isNull("intentAction")) obj.getString("intentAction") else null
                val intentUri = if (obj.has("intentUri") && !obj.isNull("intentUri")) obj.getString("intentUri") else null
                val isSystemApp = if (obj.has("isSystemApp")) obj.getBoolean("isSystemApp") else false
                val isEnabled = if (obj.has("isEnabled")) obj.getBoolean("isEnabled") else true
                list.add(AppShortcut(appName, packageName, triggerCode, intentAction, intentUri, isSystemApp, isEnabled))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveShortcuts(shortcuts: List<AppShortcut>) {
        prefs.edit().putString(KEY_SHORTCUTS, gson.toJson(shortcuts)).apply()
    }

    fun toggleEnabled(triggerCode: String, enabled: Boolean) {
        val list = getShortcuts().map { 
            if (it.triggerCode == triggerCode) it.copy(isEnabled = enabled) else it 
        }
        saveShortcuts(list)
    }

    fun initializeIfNeeded() {
        if (prefs.contains(KEY_SHORTCUTS)) return
        
        val shortcuts = mutableListOf<AppShortcut>()
        val existingTriggers = mutableSetOf<String>()
        
        // Add default deep links
        val defaultLinks = listOf(
            AppShortcut("Google Pay Scanner", null, "?scan", Intent.ACTION_VIEW, "upi://pay", true, true),
            AppShortcut("New Email", null, "?mail", Intent.ACTION_SENDTO, "mailto:", true, true),
            AppShortcut("Phone Dialer", null, "?dial", Intent.ACTION_DIAL, "tel:", true, true),
            AppShortcut("Camera", null, "?cam", MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA, null, true, true),
            AppShortcut("New Calendar Event", null, "?cal", Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI.toString(), true, true)
        )
        
        shortcuts.addAll(defaultLinks)
        existingTriggers.addAll(defaultLinks.map { it.triggerCode })

        // Scan installed apps
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(intent, 0)
        
        for (resolveInfo in resolveInfos) {
            val appName = resolveInfo.loadLabel(pm).toString()
            val packageName = resolveInfo.activityInfo.packageName
            
            if (packageName == context.packageName) continue
            
            val baseTrigger = generateTrigger(appName)
            var finalTrigger = baseTrigger
            var counter = 1
            
            while (existingTriggers.contains(finalTrigger)) {
                finalTrigger = "$baseTrigger$counter"
                counter++
            }
            
            existingTriggers.add(finalTrigger)
            shortcuts.add(AppShortcut(appName, packageName, finalTrigger, null, null, false, true))
        }
        
        saveShortcuts(shortcuts)
    }

    private fun generateTrigger(appName: String): String {
        val cleanName = appName.replace(" ", "").lowercase()
        if (cleanName.length < 3) {
            return "?${cleanName}"
        }
        val first = cleanName[0]
        val middle = cleanName[cleanName.length / 2]
        val last = cleanName[cleanName.length - 1]
        return "?$first$middle$last"
    }
}
