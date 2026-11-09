package com.yk.browser.web

import android.net.Uri
import android.webkit.JavascriptInterface
import com.yk.browser.data.AppDatabase
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

/**
 * JS bridge for the built-in Chrome-style home page (android_asset/home.html).
 * Exposes the user's most-visited sites as shortcuts.
 */
class HomeJs(private val db: AppDatabase) {

    @JavascriptInterface
    fun getShortcuts(): String {
        return try {
            runBlocking {
                val urls = db.historyDao().getTopUrls()
                val arr = JSONArray()
                val seenHosts = LinkedHashSet<String>()
                for (url in urls) {
                    val host = try { Uri.parse(url).host ?: continue } catch (e: Exception) { continue }
                    if (!seenHosts.add(host)) continue // one shortcut per site
                    if (arr.length() >= 8) break
                    val title = db.historyDao().getTitleFor(url)?.takeIf { it.isNotBlank() } ?: host
                    // Prefer a short readable label
                    val label = host.removePrefix("www.").removePrefix("m.")
                    arr.put(JSONObject()
                        .put("title", title)
                        .put("label", label)
                        .put("url", url)
                        .put("host", host))
                }
                arr.toString()
            }
        } catch (e: Exception) {
            "[]"
        }
    }
}
