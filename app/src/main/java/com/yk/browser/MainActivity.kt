package com.yk.browser

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.CookieManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.ProgressBar
import android.widget.Toast
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder

class MainActivity : AppCompatActivity() {

    private lateinit var webview: WebView
    private lateinit var addressBar: EditText
    private lateinit var progress: ProgressBar
    private val db by lazy { AppDatabase.get(this) }

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webview = findViewById(R.id.webview)
        addressBar = findViewById(R.id.addressBar)
        progress = findViewById(R.id.progress)
        val goButton: Button = findViewById(R.id.goButton)
        val backButton: ImageButton = findViewById(R.id.backButton)
        val forwardButton: ImageButton = findViewById(R.id.forwardButton)
        val menuButton: ImageButton = findViewById(R.id.menuButton)

        val web = webview
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.mediaPlaybackRequiresUserGesture = false
        web.settings.useWideViewPort = true
        web.settings.loadWithOverviewMode = true
        CookieManager.getInstance().setAcceptCookie(true)

        web.addJavascriptInterface(
            LoginCaptureJs { host, user, pass ->
                runOnUiThread { promptSaveLogin(host, user, pass) }
            },
            "AndroidLoginCapture"
        )

        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                view.loadUrl(request.url.toString())
                return true
            }

            override fun onPageFinished(view: WebView, url: String) {
                addressBar.setText(url)
                view.evaluateJavascript(LOGIN_CAPTURE_JS, null)
                lifecycleScope.launch { recordVisit(view.title ?: url, url) }
            }
        }

        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progress.progress = newProgress
                progress.visibility = if (newProgress == 100) View.GONE else View.VISIBLE
            }
        }

        goButton.setOnClickListener { loadFromBar() }
        addressBar.setOnEditorActionListener { _, _, _ -> loadFromBar(); true }
        backButton.setOnClickListener { if (web.canGoBack()) web.goBack() }
        forwardButton.setOnClickListener { if (web.canGoForward()) web.goForward() }
        menuButton.setOnClickListener { showMenu(menuButton) }

        if (savedInstanceState == null) web.loadUrl("https://www.google.com")

        SyncScheduler.schedule(this)
        maybeAskPin()
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

    private fun promptSaveLogin(host: String, username: String, password: String) {
        lifecycleScope.launch {
            val existing = withContext(Dispatchers.IO) { db.loginDao().findByHost(host) }
            if (existing != null) return@launch // already saved
            AlertDialog.Builder(this@MainActivity)
                .setTitle("Save login?")
                .setMessage("Save credentials for $host?")
                .setPositiveButton("Save") { _, _ ->
                    lifecycleScope.launch(Dispatchers.IO) {
                        db.loginDao().upsert(
                            SavedLogin(host = host, username = username,
                                passwordEncrypted = CryptoManager.encrypt(password))
                        )
                    }
                    Toast.makeText(this@MainActivity, "Login saved", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("No", null)
                .show()
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
