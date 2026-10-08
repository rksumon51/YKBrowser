package com.yk.browser.data

import androidx.room.Entity

/**
 * A session cookie captured from the WebView. The value is AES-GCM encrypted
 * with the Android Keystore key. Restoring these brings back logged-in sessions.
 * Note: HttpOnly cookies are not visible to the WebView and cannot be backed up.
 */
@Entity(tableName = "session_cookies", primaryKeys = ["domain", "name"])
data class SessionCookie(
    val domain: String,
    val name: String,
    val valueEncrypted: String,
    val path: String = "/",
    val updatedAt: Long = System.currentTimeMillis()
)
