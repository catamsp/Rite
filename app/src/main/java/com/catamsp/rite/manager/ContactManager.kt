package com.catamsp.rite.manager

import android.content.Context
import android.content.SharedPreferences
import android.provider.ContactsContract
import com.catamsp.rite.model.ContactShortcut
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class ContactManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("rite_contact_launchers", Context.MODE_PRIVATE)
    private val gson = Gson()
    
    companion object {
        const val KEY_SHORTCUTS = "contact_shortcuts"
    }

    fun getShortcuts(): List<ContactShortcut> {
        val json = prefs.getString(KEY_SHORTCUTS, null) ?: return emptyList()
        return try {
            val jsonArray = org.json.JSONArray(json)
            val list = mutableListOf<ContactShortcut>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val contactName = obj.getString("contactName")
                val contactId = obj.getString("contactId")
                val triggerCode = obj.getString("triggerCode")
                val isEnabled = if (obj.has("isEnabled")) obj.getBoolean("isEnabled") else true
                list.add(ContactShortcut(contactName, contactId, triggerCode, isEnabled))
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun saveShortcuts(shortcuts: List<ContactShortcut>) {
        prefs.edit().putString(KEY_SHORTCUTS, gson.toJson(shortcuts)).apply()
    }

    fun toggleEnabled(triggerCode: String, enabled: Boolean) {
        val list = getShortcuts().map { 
            if (it.triggerCode == triggerCode) it.copy(isEnabled = enabled) else it 
        }
        saveShortcuts(list)
    }

    fun forceRefresh() {
        prefs.edit().remove(KEY_SHORTCUTS).apply()
        initializeIfNeeded()
    }

    fun initializeIfNeeded() {
        if (prefs.contains(KEY_SHORTCUTS)) return
        
        val shortcuts = mutableListOf<ContactShortcut>()
        val existingTriggers = mutableSetOf<String>()
        
        val uri = ContactsContract.Contacts.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.DISPLAY_NAME,
            ContactsContract.Contacts.HAS_PHONE_NUMBER,
            ContactsContract.Contacts.STARRED
        )
        val selection = "${ContactsContract.Contacts.HAS_PHONE_NUMBER} = 1 AND ${ContactsContract.Contacts.STARRED} = 1"
        
        try {
            context.contentResolver.query(uri, projection, selection, null, null)?.use { cursor ->
                val idIndex = cursor.getColumnIndex(ContactsContract.Contacts._ID)
                val nameIndex = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                
                if (idIndex < 0 || nameIndex < 0) return
                
                while (cursor.moveToNext()) {
                    val id = cursor.getString(idIndex)
                    val name = cursor.getString(nameIndex) ?: continue
                    
                    val baseTrigger = generateTrigger(name)
                    var finalTrigger = baseTrigger
                    var counter = 1
                    
                    while (existingTriggers.contains(finalTrigger)) {
                        finalTrigger = "$baseTrigger$counter"
                        counter++
                    }
                    
                    existingTriggers.add(finalTrigger)
                    shortcuts.add(ContactShortcut(name, id, finalTrigger, true))
                }
            }
        } catch (_: Exception) {}
        
        saveShortcuts(shortcuts)
    }

    private fun generateTrigger(name: String): String {
        val cleanName = name.replace(" ", "").lowercase()
        if (cleanName.length < 3) {
            return "?${cleanName}"
        }
        val first = cleanName[0]
        val middle = cleanName[cleanName.length / 2]
        val last = cleanName[cleanName.length - 1]
        return "?$first$middle$last"
    }
}
