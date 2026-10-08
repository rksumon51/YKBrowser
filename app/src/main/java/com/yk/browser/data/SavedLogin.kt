package com.yk.browser.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A saved website login. The password is AES-GCM encrypted with the Android Keystore key. */
@Entity(tableName = "saved_logins")
data class SavedLogin(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val host: String,
    val username: String,
    val passwordEncrypted: String,
    val updatedAt: Long = System.currentTimeMillis()
)
