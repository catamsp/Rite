package com.catamsp.rite.manager

import android.content.Context
import android.provider.ContactsContract

class ContactResolver(private val context: Context) {

    fun resolveContact(nameQuery: String): String? {
        val uri = ContactsContract.Contacts.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.Contacts._ID,
            ContactsContract.Contacts.DISPLAY_NAME,
            ContactsContract.Contacts.HAS_PHONE_NUMBER,
            ContactsContract.Contacts.STARRED
        )
        val selection = "${ContactsContract.Contacts.DISPLAY_NAME} LIKE ? AND ${ContactsContract.Contacts.HAS_PHONE_NUMBER} = 1"
        val selectionArgs = arrayOf("%$nameQuery%")
        val sortOrder = "${ContactsContract.Contacts.STARRED} DESC"

        context.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val contactIdIndex = cursor.getColumnIndex(ContactsContract.Contacts._ID)
                if (contactIdIndex >= 0) {
                    val contactId = cursor.getString(contactIdIndex)
                    return resolveBestPhoneNumber(contactId)
                }
            }
        }
        return null
    }

    private fun resolveBestPhoneNumber(contactId: String): String? {
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.TYPE,
            ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?"
        val selectionArgs = arrayOf(contactId)

        context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
            var mobileNumber: String? = null
            var firstNumber: String? = null

            val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val typeIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE)
            val superPrimaryIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY)

            if (numberIndex < 0 || typeIndex < 0 || superPrimaryIndex < 0) return null

            while (cursor.moveToNext()) {
                val number = cursor.getString(numberIndex)
                val type = cursor.getInt(typeIndex)
                val isSuperPrimary = cursor.getInt(superPrimaryIndex)

                if (firstNumber == null) {
                    firstNumber = number
                }

                if (isSuperPrimary != 0) {
                    return number
                }

                if (type == ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE && mobileNumber == null) {
                    mobileNumber = number
                }
            }

            return mobileNumber ?: firstNumber
        }
        return null
    }
}
