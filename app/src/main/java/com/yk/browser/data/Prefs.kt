package com.yk.browser.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Encrypted app preferences (backup toggles, PIN-set flag). */
object Prefs {
    private const val FILE = "yk_prefs"
    const val KEY_PIN_SET = "pin_set"
    const val KEY_CLOUD_ENABLED = "cloud_enabled"
    const val KEY_LOCAL_AUTO = "local_auto"

    private fun prefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context, FILE, masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun isPinSet(c: Context): Boolean = prefs(c).getBoolean(KEY_PIN_SET, false)
    fun setPinSet(c: Context, v: Boolean) = prefs(c).edit().putBoolean(KEY_PIN_SET, v).apply()

    fun isCloudEnabled(c: Context): Boolean = prefs(c).getBoolean(KEY_CLOUD_ENABLED, true)
    fun setCloudEnabled(c: Context, v: Boolean) = prefs(c).edit().putBoolean(KEY_CLOUD_ENABLED, v).apply()

    fun isLocalAuto(c: Context): Boolean = prefs(c).getBoolean(KEY_LOCAL_AUTO, true)
    fun setLocalAuto(c: Context, v: Boolean) = prefs(c).edit().putBoolean(KEY_LOCAL_AUTO, v).apply()
}
