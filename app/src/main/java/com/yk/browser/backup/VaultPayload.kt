package com.yk.browser.backup

import com.yk.browser.crypto.CryptoManager
import com.yk.browser.data.AppDatabase
import com.yk.browser.data.Bookmark
import com.yk.browser.data.HistoryEntry
import com.yk.browser.data.SavedLogin
import com.yk.browser.data.SessionCookie
import org.json.JSONArray
import org.json.JSONObject

/**
 * The complete vault: everything needed to restore the browser
 * "same to same" on a new install. Serialized to JSON, then the whole
 * document is encrypted with the user's PIN (see VaultCrypto).
 */
data class VaultPayload(
    val logins: List<LoginEntry>,
    val bookmarks: List<BookmarkEntry>,
    val history: List<HistoryEntryData>,
    val cookies: List<CookieEntry>
) {
    data class LoginEntry(val host: String, val username: String, val password: String)
    data class BookmarkEntry(val title: String, val url: String)
    data class HistoryEntryData(val title: String, val url: String, val visitedAt: Long)
    data class CookieEntry(val domain: String, val name: String, val value: String, val path: String)
}

const val VAULT_SCHEMA_VERSION = 1

/** Reads every table and builds the (plaintext) vault. Call off the main thread. */
suspend fun buildVaultPayload(db: AppDatabase): VaultPayload {
    val logins = db.loginDao().getAll().map {
        VaultPayload.LoginEntry(it.host, it.username, CryptoManager.decrypt(it.passwordEncrypted))
    }
    val bookmarks = db.bookmarkDao().getAll().map {
        VaultPayload.BookmarkEntry(it.title, it.url)
    }
    val history = db.historyDao().getRecent().map {
        VaultPayload.HistoryEntryData(it.title, it.url, it.visitedAt)
    }
    val cookies = db.cookieDao().getAll().map {
        VaultPayload.CookieEntry(it.domain, it.name, CryptoManager.decrypt(it.valueEncrypted), it.path)
    }
    return VaultPayload(logins, bookmarks, history, cookies)
}

/** Writes a decrypted vault into the local DB (re-encrypting secrets with the Keystore key). */
suspend fun restoreVaultPayload(db: AppDatabase, payload: VaultPayload) {
    val loginDao = db.loginDao()
    loginDao.clear()
    payload.logins.forEach {
        loginDao.upsert(SavedLogin(host = it.host, username = it.username,
            passwordEncrypted = CryptoManager.encrypt(it.password)))
    }
    val bookmarkDao = db.bookmarkDao()
    bookmarkDao.clear()
    payload.bookmarks.forEach { bookmarkDao.insert(Bookmark(title = it.title, url = it.url)) }
    val historyDao = db.historyDao()
    historyDao.clear()
    payload.history.forEach {
        historyDao.insert(HistoryEntry(title = it.title, url = it.url, visitedAt = it.visitedAt))
    }
    val cookieDao = db.cookieDao()
    cookieDao.clear()
    payload.cookies.forEach {
        cookieDao.upsert(SessionCookie(domain = it.domain, name = it.name,
            valueEncrypted = CryptoManager.encrypt(it.value), path = it.path))
    }
}

fun VaultPayload.toJson(): String {
    val root = JSONObject()
    root.put("schemaVersion", VAULT_SCHEMA_VERSION)
    root.put("exportedAt", System.currentTimeMillis())
    root.put("logins", JSONArray().apply {
        logins.forEach { put(JSONObject().put("host", it.host).put("username", it.username).put("password", it.password)) }
    })
    root.put("bookmarks", JSONArray().apply {
        bookmarks.forEach { put(JSONObject().put("title", it.title).put("url", it.url)) }
    })
    root.put("history", JSONArray().apply {
        history.forEach { put(JSONObject().put("title", it.title).put("url", it.url).put("visitedAt", it.visitedAt)) }
    })
    root.put("cookies", JSONArray().apply {
        cookies.forEach { put(JSONObject().put("domain", it.domain).put("name", it.name).put("value", it.value).put("path", it.path)) }
    })
    return root.toString()
}

fun vaultPayloadFromJson(json: String): VaultPayload {
    val root = JSONObject(json)
    fun objArray(key: String): List<JSONObject> {
        val arr = root.optJSONArray(key) ?: JSONArray()
        return (0 until arr.length()).map { arr.getJSONObject(it) }
    }
    return VaultPayload(
        logins = objArray("logins").map {
            VaultPayload.LoginEntry(it.getString("host"), it.optString("username"), it.getString("password"))
        },
        bookmarks = objArray("bookmarks").map {
            VaultPayload.BookmarkEntry(it.optString("title"), it.getString("url"))
        },
        history = objArray("history").map {
            VaultPayload.HistoryEntryData(it.optString("title"), it.getString("url"), it.optLong("visitedAt"))
        },
        cookies = objArray("cookies").map {
            VaultPayload.CookieEntry(it.getString("domain"), it.getString("name"), it.getString("value"), it.optString("path", "/"))
        }
    )
}
