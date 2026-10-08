package com.yk.browser.web

import android.webkit.CookieManager
import com.yk.browser.backup.VaultPayload

/**
 * Captures WebView session cookies per visited site so logged-in sessions
 * can be restored later ("same to same" pages after reinstall).
 *
 * Limitation: HttpOnly cookies are invisible to the WebView and cannot be
 * backed up — those sites will need a fresh login, but saved credentials
 * make that one tap.
 */
object CookieBackupHelper {

    /** Reads all visible cookies for [urls]. Must run on a thread with a Looper. */
    fun collectCookies(urls: List<String>): List<Triple<String, String, String>> {
        val cm = CookieManager.getInstance()
        val out = mutableListOf<Triple<String, String, String>>()
        for (url in urls.distinct()) {
            val raw = try { cm.getCookie(url) } catch (e: Exception) { null } ?: continue
            raw.split(";").forEach { pair ->
                val kv = pair.trim().split("=", limit = 2)
                if (kv.size == 2 && kv[0].isNotBlank()) {
                    out += Triple(url, kv[0].trim(), kv[1])
                }
            }
        }
        return out
    }

    /** Re-applies cookies into the WebView. Must run on the main thread. */
    fun restoreCookies(cookies: List<VaultPayload.CookieEntry>) {
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        for (c in cookies) {
            try {
                cm.setCookie(c.domain, "${c.name}=${c.value}; path=${c.path}")
            } catch (e: Exception) {
                // skip malformed entries, keep restoring the rest
            }
        }
        cm.flush()
    }
}
