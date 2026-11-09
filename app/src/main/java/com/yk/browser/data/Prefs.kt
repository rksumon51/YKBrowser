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

    // Open tabs (plain prefs — tab URLs are not secrets)
    private const val TABS_FILE = "yk_tabs"
    fun saveOpenTabs(c: Context, urls: List<String>, current: Int) {
        c.getSharedPreferences(TABS_FILE, Context.MODE_PRIVATE).edit()
            .putString("urls", urls.joinToString("\n"))
            .putInt("current", current).apply()
    }
    fun loadOpenTabs(c: Context): Pair<List<String>, Int>? {
        val sp = c.getSharedPreferences(TABS_FILE, Context.MODE_PRIVATE)
        val raw = sp.getString("urls", null) ?: return null
        val urls = raw.split("\n").filter { it.isNotBlank() }
        if (urls.isEmpty()) return null
        return urls to sp.getInt("current", 0).coerceIn(0, urls.size - 1)
    }
}
