package com.yk.browser.ui

import android.app.AlertDialog
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.google.android.material.switchmaterial.SwitchMaterial
import com.yk.browser.R
import com.yk.browser.auth.AuthManager
import com.yk.browser.backup.BackupManager
import com.yk.browser.backup.BackupPinHolder
import com.yk.browser.backup.BackupResult
import com.yk.browser.data.Prefs
import com.yk.browser.sync.SyncScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsActivity : AppCompatActivity() {

    private val backupManager by lazy { BackupManager(this) }

    private lateinit var tvAccount: TextView
    private lateinit var btnSignIn: com.google.android.material.button.MaterialButton
    private lateinit var btnSignOut: com.google.android.material.button.MaterialButton
    private lateinit var btnSetPin: com.google.android.material.button.MaterialButton
    private lateinit var switchCloud: SwitchMaterial
    private lateinit var switchLocal: SwitchMaterial

    private val googleSignInLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode != RESULT_OK) return@registerForActivityResult
            try {
                val account = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                    .getResult(ApiException::class.java)
                val idToken = account?.idToken
                if (idToken == null) {
                    toast("Google sign-in unavailable (Firebase not configured)")
                    return@registerForActivityResult
                }
                AuthManager.signInWithGoogle(idToken).addOnCompleteListener { task ->
                    toast(if (task.isSuccessful) "Signed in" else "Sign-in failed: ${task.exception?.message}")
                    refreshAccount()
                }
            } catch (e: ApiException) {
                toast("Google sign-in failed: ${e.statusCode}")
            }
        }

    private val exportLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
            if (uri == null) return@registerForActivityResult
            requirePin { pin ->
                lifecycleScope.launch {
                    val payload = withContext(Dispatchers.IO) { backupManager.buildPayloadJson() }
                    val result = withContext(Dispatchers.IO) { backupManager.local.exportTo(uri, payload, pin) }
                    toast(describe(result))
                }
            }
        }

    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@registerForActivityResult
            requirePin { pin ->
                lifecycleScope.launch {
                    try {
                        val json = withContext(Dispatchers.IO) { backupManager.local.importFrom(uri, pin) }
                        val result = withContext(Dispatchers.IO) { backupManager.restoreJson(json) }
                        toast(describe(result))
                    } catch (e: Exception) {
                        toast("Import failed: wrong PIN or bad file")
                    }
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        SyncScheduler.schedule(this)

        findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
            .setNavigationOnClickListener { finish() }

        tvAccount = findViewById(R.id.tvAccount)
        btnSignIn = findViewById(R.id.btnSignIn)
        btnSignOut = findViewById(R.id.btnSignOut)
        btnSetPin = findViewById(R.id.btnSetPin)
        switchCloud = findViewById(R.id.switchCloud)
        switchLocal = findViewById(R.id.switchLocal)
        val btnBackupNow: com.google.android.material.button.MaterialButton = findViewById(R.id.btnBackupNow)
        val btnRestoreCloud: com.google.android.material.button.MaterialButton = findViewById(R.id.btnRestoreCloud)
        val btnRestoreLocal: com.google.android.material.button.MaterialButton = findViewById(R.id.btnRestoreLocal)
        val btnExport: com.google.android.material.button.MaterialButton = findViewById(R.id.btnExport)
        val btnImport: com.google.android.material.button.MaterialButton = findViewById(R.id.btnImport)

        btnSignIn.setOnClickListener {
            if (!AuthManager.isFirebaseConfigured(this)) {
                toast("Firebase not configured yet — add google-services.json (see FIREBASE_SETUP.md) and rebuild")
                return@setOnClickListener
            }
            val client = GoogleSignIn.getClient(this, AuthManager.googleSignInOptions(this))
            googleSignInLauncher.launch(client.signInIntent)
        }
        btnSignOut.setOnClickListener {
            AuthManager.signOut(this)
            BackupPinHolder.pin = null
            refreshAccount()
            toast("Signed out")
        }
        btnSetPin.setOnClickListener { askNewPin() }

        switchCloud.isChecked = Prefs.isCloudEnabled(this)
        switchCloud.setOnCheckedChangeListener { _, v -> Prefs.setCloudEnabled(this, v) }
        switchLocal.isChecked = Prefs.isLocalAuto(this)
        switchLocal.setOnCheckedChangeListener { _, v -> Prefs.setLocalAuto(this, v) }

        btnBackupNow.setOnClickListener {
            requirePin { pin ->
                lifecycleScope.launch {
                    toast("Backing up…")
                    val results = withContext(Dispatchers.IO) { backupManager.backupNow(pin) }
                    val msg = if (results.isEmpty()) "Nothing enabled to back up"
                    else results.joinToString("\n") { (n, r) -> "$n: ${describe(r)}" }
                    toast(msg)
                }
            }
        }
        btnRestoreCloud.setOnClickListener {
            if (!AuthManager.isFirebaseConfigured(this)) {
                toast("Firebase not configured yet — add google-services.json (see FIREBASE_SETUP.md) and rebuild")
                return@setOnClickListener
            }
            requirePin { pin ->
                lifecycleScope.launch {
                    toast("Restoring from cloud…")
                    val r = withContext(Dispatchers.IO) { backupManager.restoreFrom(pin, "cloud") }
                    toast(describe(r))
                }
            }
        }
        btnRestoreLocal.setOnClickListener {
            requirePin { pin ->
                lifecycleScope.launch {
                    toast("Restoring from local…")
                    val r = withContext(Dispatchers.IO) { backupManager.restoreFrom(pin, "local") }
                    toast(describe(r))
                }
            }
        }
        btnExport.setOnClickListener {
            val name = "ykbrowser-backup-" +
                SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date()) + ".ykb"
            exportLauncher.launch(name)
        }
        btnImport.setOnClickListener { importLauncher.launch(arrayOf("*/*")) }

        refreshAccount()
    }

    private fun refreshAccount() {
        val email = AuthManager.currentEmail()
        tvAccount.text = if (email != null) "Signed in as $email" else "Not signed in"
        btnSignIn.isEnabled = email == null
        btnSignOut.isEnabled = email != null
        btnSetPin.text = if (Prefs.isPinSet(this)) "Change backup PIN" else "Set backup PIN"
    }

    /** Ensures a PIN is available (in RAM), asking the user when needed. */
    private fun requirePin(onPin: (String) -> Unit) {
        BackupPinHolder.pin?.let { onPin(it); return }
        if (!Prefs.isPinSet(this)) {
            toast("Set a backup PIN first")
            askNewPin()
            return
        }
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = "Backup PIN"
        }
        AlertDialog.Builder(this)
            .setTitle("Enter backup PIN")
            .setView(input)
            .setPositiveButton("OK") { _, _ ->
                BackupPinHolder.pin = input.text.toString()
                onPin(input.text.toString())
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun askNewPin() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = "New PIN (min 4 chars)"
        }
        AlertDialog.Builder(this)
            .setTitle("Set backup PIN")
            .setMessage("This PIN encrypts your vault. Without it nobody — not even the cloud — can read your backup.")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val pin = input.text.toString()
                if (pin.length < 4) {
                    toast("PIN must be at least 4 characters")
                    return@setPositiveButton
                }
                Prefs.setPinSet(this, true)
                BackupPinHolder.pin = pin
                refreshAccount()
                toast("Backup PIN set")
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun describe(r: BackupResult): String =
        if (r is BackupResult.Success) "OK" else (r as BackupResult.Error).message

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
}
