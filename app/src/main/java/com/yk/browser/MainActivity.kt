package com.yk.browser

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.CookieManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.button.MaterialButton
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.progressindicator.LinearProgressIndicator
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.yk.browser.backup.BackupManager
import com.yk.browser.backup.BackupPinHolder
import com.yk.browser.backup.BackupResult
import com.yk.browser.crypto.CryptoManager
import com.yk.browser.data.AppDatabase
import com.yk.browser.data.HistoryEntry
import com.yk.browser.data.Prefs
import com.yk.browser.data.SavedLogin
import com.yk.browser.data.SessionCookie
import com.yk.browser.sync.SyncScheduler
import com.yk.browser.ui.LoginsActivity
import com.yk.browser.ui.SettingsActivity
import com.yk.browser.web.CookieBackupHelper
import com.yk.browser.web.LOGIN_CAPTURE_JS
import com.yk.browser.web.LoginCaptureJs
import com.yk.browser.web.buildAutofillJs
import com.yk.browser.web.HomeJs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder

class MainActivity : AppCompatActivity() {

    private lateinit var webview: WebView
    private lateinit var tabCountView: TextView
    private lateinit var addressBar: EditText
    private lateinit var progress: LinearProgressIndicator
    private val db by lazy { AppDatabase.get(this) }

    /** Unlimited tabs: every tab is its own WebView; no cap on the count. */
    private val tabs = mutableListOf<WebView>()
    private var currentTabIndex = 0
    private lateinit var tabContainer: FrameLayout
    private lateinit var tabStripItems: LinearLayout

    /** Login attempt waiting for success confirmation. No popup — auto-saves only if login succeeds. */
    private var pendingLogin: PendingLogin? = null

    companion object {
        const val HOME_URL = "file:///android_asset/home.html"
    }
    private data class PendingLogin(
        val host: String,
        val username: String,
        val password: String,
        val pageUrl: String,
        val time: Long
    )

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webview = findViewById(R.id.webview)
        addressBar = findViewById(R.id.addressBar)
        progress = findViewById(R.id.progress)
        tabCountView = findViewById(R.id.tabCount)
        val goButton: MaterialButton = findViewById(R.id.goButton)
        val backButton: ImageButton = findViewById(R.id.backButton)
        val forwardButton: ImageButton = findViewById(R.id.forwardButton)
        val homeButton: ImageButton = findViewById(R.id.homeButton)
        val menuButton: ImageButton = findViewById(R.id.menuButton)
        val tabButton: android.widget.FrameLayout = findViewById(R.id.tabButton)
        val newTabButton: ImageButton = findViewById(R.id.newTabButton)
        val profileAvatar: ImageView = findViewById(R.id.profileAvatar)

        tabContainer = findViewById(R.id.tabContainer)
        tabStripItems = findViewById(R.id.tabStripItems)

        // Initialize first tab
        val initialWebView = findViewById<WebView>(R.id.webview)
        tabs.add(initialWebView)
        setupWebView(initialWebView)

        // Restore previously open tabs (unlimited tabs survive app restarts);
        // only the active tab loads immediately, the rest load on first switch.
        val savedTabs = if (savedInstanceState == null) Prefs.loadOpenTabs(this) else null
        if (savedTabs != null && savedTabs.first.isNotEmpty()) {
            val (urls, current) = savedTabs
            initialWebView.tag = urls[0]
            for (i in 1 until urls.size) {
                val wv = WebView(this)
                wv.layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
                setupWebView(wv)
                wv.tag = urls[i]
                tabs.add(wv)
                tabContainer.addView(wv)
            }
            switchToTab(current)
        } else {
            if (savedInstanceState == null) webview.loadUrl(HOME_URL)
            updateTabStrip()
        }

        tabButton.setOnClickListener { showTabSwitcher() }
        newTabButton.setOnClickListener { createNewTab() }
        profileAvatar.setOnClickListener {
            Toast.makeText(this, "Profile", Toast.LENGTH_SHORT).show()
        }
        findViewById<ImageButton>(R.id.expandTabsButton)?.setOnClickListener { showTabSwitcher() }

        homeButton.setOnClickListener { webview.loadUrl(HOME_URL) }

        goButton.setOnClickListener { loadFromBar() }
        addressBar.setOnEditorActionListener { _, _, _ -> loadFromBar(); true }
        backButton.setOnClickListener { if (webview.canGoBack()) webview.goBack() }
        forwardButton.setOnClickListener { if (webview.canGoForward()) webview.goForward() }
        menuButton.setOnClickListener { showMenu(menuButton) }

        SyncScheduler.schedule(this)
        maybeAskPin()
    }

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    private fun setupWebView(web: WebView) {
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.mediaPlaybackRequiresUserGesture = false
        web.settings.useWideViewPort = true
        web.settings.loadWithOverviewMode = true
        CookieManager.getInstance().setAcceptCookie(true)

        web.addJavascriptInterface(
            LoginCaptureJs(
                onCapture = { host, user, pass -> runOnUiThread { onLoginAttempt(host, user, pass) } },
                onSuccess = { host, user, pass -> runOnUiThread { onLoginSucceeded(host, user, pass) } }
            ),
            "AndroidLoginCapture"
        )
        web.addJavascriptInterface(HomeJs(db), "AndroidHome")

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                view.loadUrl(request.url.toString())
                return true
            }

            override fun onPageFinished(view: WebView, url: String) {
                val isHome = url.startsWith("file:///android_asset/")
                if (isHome) {
                    addressBar.setText("")
                } else {
                    addressBar.setText(url)
                }
                if (!isHome) {
                    view.evaluateJavascript(LOGIN_CAPTURE_JS, null)
                    lifecycleScope.launch { recordVisit(view.title ?: url, url) }
                }
                checkPendingLoginAfterNavigation(url)
                if (isHome) return
                // Auto-fill saved login for this host
                lifecycleScope.launch {
                    try {
                        val host = android.net.Uri.parse(url).host ?: return@launch
                        val saved = withContext(Dispatchers.IO) { db.loginDao().findByHost(host) }
                        if (saved != null) {
                            val password = withContext(Dispatchers.IO) {
                                CryptoManager.decrypt(saved.passwordEncrypted)
                            }
                            view.evaluateJavascript(
                                buildAutofillJs(saved.username, password), null)
                            withContext(Dispatchers.Main) {
                                Toast.makeText(this@MainActivity,
                                    "Login auto-filled for $host", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } catch (e: Exception) { /* skip */ }
                }
                updateTabStrip()
                saveTabs()
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                // Only update progress for current tab
                if (view == webview) {
                    progress.progress = newProgress
                    progress.visibility = if (newProgress == 100) View.GONE else View.VISIBLE
                }
            }
        }
    }

    private fun createNewTab() {
        val newWebView = WebView(this)
        newWebView.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        setupWebView(newWebView)
        tabs.add(newWebView)
        tabContainer.addView(newWebView)
        switchToTab(tabs.size - 1)
        newWebView.loadUrl(HOME_URL)
    }

    private fun switchToTab(index: Int) {
        if (index < 0 || index >= tabs.size) return
        currentTabIndex = index
        webview = tabs[index]
        for (i in tabs.indices) {
            tabs[i].visibility = if (i == index) View.VISIBLE else View.GONE
        }
        // Lazily load a restored tab the first time it is opened.
        val pending = webview.tag as? String
        if (webview.url == null && pending != null) {
            webview.tag = null
            webview.loadUrl(pending)
        }
        val url = webview.url
        if (url != null && !url.startsWith("file:///android_asset/")) {
            addressBar.setText(url)
        } else {
            addressBar.setText("")
        }
        progress.visibility = View.GONE
        updateTabStrip()
        saveTabs()
    }

    private fun closeTab(index: Int) {
        if (index < 0 || index >= tabs.size) return
        if (tabs.size <= 1) {
            // Don't close last tab, just load home
            webview.loadUrl(HOME_URL)
            saveTabs()
            return
        }
        val wv = tabs[index]
        tabContainer.removeView(wv)
        wv.stopLoading()
        wv.destroy()
        tabs.removeAt(index)
        if (index < currentTabIndex) currentTabIndex--
        if (currentTabIndex >= tabs.size) currentTabIndex = tabs.size - 1
        switchToTab(currentTabIndex)
    }

    private fun updateTabStrip() {
        tabCountView.text = tabs.size.toString()
        tabStripItems.removeAllViews()
        for (i in tabs.indices) {
            val tabView = createTabStripItem(i)
            tabStripItems.addView(tabView)
        }
    }

    private fun createTabStripItem(index: Int): View {
        val isActive = index == currentTabIndex
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val pad = (12 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            setBackgroundResource(
                if (isActive) android.R.drawable.dialog_holo_dark_frame
                else android.R.color.transparent
            )
            isClickable = true
            isFocusable = true
        }
        val dot = View(this).apply {
            val size = (40 * resources.displayMetrics.density).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                marginEnd = (8 * resources.displayMetrics.density).toInt()
            }
            setBackgroundResource(android.R.drawable.presence_online)
        }
        val label = TextView(this).apply {
            text = tabs[index].title?.takeIf { it.isNotBlank() } ?: "New tab"
            setTextColor(if (isActive) 0xFFFFFFFF.toInt() else 0xFF9AA0A6.toInt())
            textSize = 13f
            maxLines = 1
        }
        val closeBtn = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            setColorFilter(0xFF9AA0A6.toInt())
            background = null
            val size = (32 * resources.displayMetrics.density).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size)
            setOnClickListener { closeTab(index) }
        }
        container.addView(dot)
        container.addView(label)
        container.addView(closeBtn)
        container.setOnClickListener { switchToTab(index) }
        return container
    }

    /** Real tab switcher: scrollable list of ALL open tabs (no cap), tap to switch,
     *  X to close, "+ New tab" to open one more. */
    private fun showTabSwitcher() {
        val sheet = BottomSheetDialog(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = dp(16)
            setPadding(pad, pad, pad, pad)
        }
        val title = TextView(this).apply {
            text = "${tabs.size} tabs open"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 16f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, dp(12))
        }
        root.addView(title)
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(360)
            )
        }
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        for (i in tabs.indices) {
            val wv = tabs[i]
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val pad = dp(12)
                setPadding(pad, pad, pad, pad)
                setBackgroundResource(
                    if (i == currentTabIndex) android.R.drawable.dialog_holo_dark_frame
                    else android.R.color.transparent
                )
                isClickable = true
                isFocusable = true
            }
            val texts = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val titleView = TextView(this).apply {
                text = wv.title?.takeIf { it.isNotBlank() }
                    ?: wv.url?.takeIf { it.isNotBlank() }
                    ?: (wv.tag as? String)?.takeIf { it.isNotBlank() }
                    ?: "New tab"
                setTextColor(if (i == currentTabIndex) 0xFFFFFFFF.toInt() else 0xFFE3E3E3.toInt())
                textSize = 14f
                maxLines = 1
            }
            val urlView = TextView(this).apply {
                text = wv.url ?: (wv.tag as? String) ?: "New tab"
                setTextColor(0xFF9AA0A6.toInt())
                textSize = 12f
                maxLines = 1
            }
            texts.addView(titleView)
            texts.addView(urlView)
            val closeBtn = ImageButton(this).apply {
                setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                setColorFilter(0xFF9AA0A6.toInt())
                background = null
                val size = dp(32)
                layoutParams = LinearLayout.LayoutParams(size, size)
                setOnClickListener { closeTab(i); sheet.dismiss() }
            }
            row.addView(texts)
            row.addView(closeBtn)
            row.setOnClickListener { switchToTab(i); sheet.dismiss() }
            list.addView(row)
        }
        scroll.addView(list)
        root.addView(scroll)
        val newTabBtn = MaterialButton(this).apply {
            text = "+ New tab"
            setOnClickListener { createNewTab(); sheet.dismiss() }
        }
        root.addView(newTabBtn)
        sheet.setContentView(root)
        sheet.show()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    /** Persists open-tab URLs so unlimited tabs survive an app restart. */
    private fun saveTabs() {
        val urls = tabs.map { it.url ?: (it.tag as? String) ?: HOME_URL }
        Prefs.saveOpenTabs(this, urls, currentTabIndex)
    }

    override fun onBackPressed() {
        if (webview.canGoBack()) webview.goBack() else super.onBackPressed()
    }

    private fun loadFromBar() {
        var input = addressBar.text.toString().trim()
        if (input.isEmpty()) return
        if (!input.contains("://")) {
            input = if (input.contains(".") && !input.contains(" ")) "https://$input"
            else "https://www.google.com/search?q=" + URLEncoder.encode(input, "UTF-8")
        }
        webview.loadUrl(input)
    }

    private fun showMenu(anchor: View) {
        PopupMenu(this, anchor).apply {
            menu.add("Saved logins")
            menu.add("Backup now")
            menu.add("Settings")
            setOnMenuItemClickListener { item ->
                when (item.title) {
                    "Saved logins" -> startActivity(Intent(this@MainActivity, LoginsActivity::class.java))
                    "Backup now" -> lifecycleScope.launch { backupNow() }
                    "Settings" -> startActivity(Intent(this@MainActivity, SettingsActivity::class.java))
                }
                true
            }
        }.show()
    }

    private suspend fun backupNow() {
        val pin = BackupPinHolder.pin
        if (pin == null) {
            Toast.makeText(this, "Set/unlock your backup PIN in Settings first", Toast.LENGTH_LONG).show()
            startActivity(Intent(this, SettingsActivity::class.java))
            return
        }
        Toast.makeText(this, "Backing up…", Toast.LENGTH_SHORT).show()
        val results = withContext(Dispatchers.IO) { BackupManager(this@MainActivity).backupNow(pin) }
        val msg = if (results.isEmpty()) "Nothing enabled to back up"
        else results.joinToString("\n") { (name, r) ->
            "$name: ${if (r is BackupResult.Success) "OK" else (r as BackupResult.Error).message}"
        }
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    private suspend fun recordVisit(title: String, url: String) = withContext(Dispatchers.IO) {
        db.historyDao().insert(HistoryEntry(title = title, url = url))
        val cookies = withContext(Dispatchers.Main) { CookieBackupHelper.collectCookies(listOf(url)) }
        val dao = db.cookieDao()
        for ((domain, name, value) in cookies) {
            try {
                dao.upsert(SessionCookie(domain = domain, name = name,
                    valueEncrypted = CryptoManager.encrypt(value)))
            } catch (e: Exception) { /* skip */ }
        }
    }

    /**
     * Called when the page's JS detects a login attempt (button click / form submit
     * with a filled password field). Does NOT show a popup. Instead it stores the
     * attempt as pending and only saves it if the login actually succeeds:
     *  - the page navigates to a different URL (same host), or
     *  - the password field disappears from the page (SPA login).
     * If neither happens within 30s the attempt is discarded (login failed).
     */
    private fun onLoginAttempt(host: String, username: String, password: String) {
        val now = System.currentTimeMillis()
        val current = pendingLogin
        // Dedupe: JS may report the same attempt twice (click + delayed check)
        if (current != null && current.host == host &&
            current.username == username && current.password == password &&
            now - current.time < 10_000) return

        val pageUrl = webview.url ?: return
        pendingLogin = PendingLogin(host, username, password, pageUrl, now)

        // Verify after a few seconds (covers SPA logins without navigation)
        lifecycleScope.launch {
            kotlinx.coroutines.delay(4000)
            verifyPendingLogin()
        }
        // Final timeout: discard if login never succeeded
        lifecycleScope.launch {
            kotlinx.coroutines.delay(30_000)
            val cur = pendingLogin
            if (cur != null && System.currentTimeMillis() - cur.time >= 30_000) {
                pendingLogin = null
            }
        }
    }

    /** Page-side confirmation that the login actually succeeded -> save it. */
    private fun onLoginSucceeded(host: String, username: String, password: String) {
        pendingLogin = null
        saveLogin(PendingLogin(host, username, password, webview.url ?: "", System.currentTimeMillis()))
    }

    /** Called from onPageFinished: navigation away from the login page = success. */
    private fun checkPendingLoginAfterNavigation(newUrl: String) {
        val p = pendingLogin ?: return
        if (System.currentTimeMillis() - p.time > 30_000) { pendingLogin = null; return }
        try {
            val newHost = android.net.Uri.parse(newUrl).host ?: return
            if (newHost == p.host && newUrl != p.pageUrl) {
                // Left the login page on the same site -> login succeeded
                pendingLogin = null
                saveLogin(p)
            }
        } catch (e: Exception) { /* skip */ }
    }

    /** For SPA logins: if the password field is gone or hidden, the login form is done -> success. */
    private fun verifyPendingLogin() {
        val p = pendingLogin ?: return
        if (System.currentTimeMillis() - p.time > 30_000) { pendingLogin = null; return }
        try {
            webview.evaluateJavascript(
                "(function(){var f=document.querySelector('input[type=\"password\"]');" +
                "if(!f) return '1'; try{return f.offsetParent===null?'1':'0';}catch(e){return '0';}})();"
            ) { result ->
                val goneOrHidden = result?.trim('"') == "1"
                if (goneOrHidden && pendingLogin?.host == p.host) {
                    pendingLogin = null
                    saveLogin(p)
                }
            }
        } catch (e: Exception) { /* skip */ }
    }

    /** Saves (or updates) the login silently, then shows a small confirmation toast.
     *  Multiple accounts per website are supported: entries are keyed by (host, username). */
    private fun saveLogin(p: PendingLogin) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val existing = db.loginDao().findByHostAndUsername(p.host, p.username)
                if (existing != null) {
                    val oldPass = try { CryptoManager.decrypt(existing.passwordEncrypted) } catch (e: Exception) { "" }
                    if (oldPass == p.password) return@launch // identical, skip
                }
                // Remove any stale/duplicate rows for this account, then insert fresh
                db.loginDao().deleteByHostAndUsername(p.host, p.username)
                db.loginDao().upsert(
                    SavedLogin(host = p.host, username = p.username,
                        passwordEncrypted = CryptoManager.encrypt(p.password))
                )
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity,
                        "🔐 Login saved for ${p.host}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) { /* skip */ }
        }
    }

    /** Unlocks background sync for this session if a backup PIN exists. */
    private fun maybeAskPin() {
        if (!Prefs.isPinSet(this) || BackupPinHolder.pin != null) return
        val input = EditText(this).apply { inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        AlertDialog.Builder(this)
            .setTitle("Unlock backup")
            .setMessage("Enter your backup PIN to enable auto backup this session.")
            .setView(input)
            .setPositiveButton("Unlock") { _, _ -> BackupPinHolder.pin = input.text.toString() }
            .setNegativeButton("Later", null)
            .show()
    }
}
