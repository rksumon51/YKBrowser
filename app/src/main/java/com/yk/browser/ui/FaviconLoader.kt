package com.yk.browser.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.ImageView
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Loads real site favicons (Google's favicon service, the same source the
 * home page already uses for shortcuts) into list rows. On any failure —
 * offline, unknown host, bad image — the letter avatar stays visible.
 * Small in-memory LRU cache (max 100) so scrolling a list does not
 * re-download the same icons.
 */
object FaviconLoader {

    private const val MAX_CACHE = 100
    private val cache = object : LinkedHashMap<String, Bitmap>(MAX_CACHE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>): Boolean {
            return size > MAX_CACHE
        }
    }

    fun hostOf(url: String): String =
        Uri.parse(url).host?.removePrefix("www.") ?: url

    /**
     * Tries to show [imageView] with the site's favicon; leaves [fallback]
     * (the letter avatar) visible when the icon cannot be loaded.
     * Safe to call from onBindViewHolder — results are applied only if the
     * view is still bound to the same host.
     */
    fun load(host: String, imageView: ImageView, fallback: TextView, scope: CoroutineScope) {
        val key = host.lowercase()
        synchronized(cache) {
            cache[key]?.let {
                imageView.setImageBitmap(it)
                imageView.visibility = android.view.View.VISIBLE
                fallback.visibility = android.view.View.GONE
                return
            }
        }
        imageView.visibility = android.view.View.GONE
        fallback.visibility = android.view.View.VISIBLE
        imageView.tag = key
        scope.launch(Dispatchers.IO) {
            val bitmap = try {
                val serviceUrl = "https://www.google.com/s2/favicons?domain=" +
                    URLEncoder.encode(key, "UTF-8") + "&sz=64"
                val conn = URL(serviceUrl).openConnection() as HttpURLConnection
                conn.connectTimeout = 4000
                conn.readTimeout = 4000
                conn.connect()
                if (conn.responseCode != HttpURLConnection.HTTP_OK) null
                else BitmapFactory.decodeStream(conn.inputStream)?.also {
                    synchronized(cache) { cache[key] = it }
                }
            } catch (e: Exception) {
                null
            }
            if (bitmap == null) return@launch
            withContext(Dispatchers.Main) {
                if (imageView.tag != key) return@withContext
                imageView.setImageBitmap(bitmap)
                imageView.visibility = android.view.View.VISIBLE
                fallback.visibility = android.view.View.GONE
            }
        }
    }
}
