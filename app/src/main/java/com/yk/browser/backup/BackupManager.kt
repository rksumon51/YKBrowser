package com.yk.browser.backup

import android.content.Context
import com.yk.browser.auth.AuthManager
import com.yk.browser.data.AppDatabase
import com.yk.browser.data.Prefs
import com.yk.browser.web.CookieBackupHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Single entry point for all backup/restore work. It builds the vault once
 * and fans it out to whichever providers the user enabled — local, cloud,
 * or both. Add a new provider here later without touching the UI.
 */
class BackupManager(private val context: Context) {
    private val db = AppDatabase.get(context)
    val local = LocalBackupProvider(context)
    val cloud = CloudBackupProvider()

    /** Backs up to every enabled provider. Returns per-provider results. */
    suspend fun backupNow(pin: String): List<Pair<String, BackupResult>> =
        withContext(Dispatchers.IO) {
            val payloadJson = buildVaultPayload(db).toJson()
            val results = mutableListOf<Pair<String, BackupResult>>()
            if (Prefs.isLocalAuto(context)) {
                results += local.name to local.backup(payloadJson, pin)
            }
            // Cloud only when Firebase is configured (google-services.json present) and signed in.
            if (Prefs.isCloudEnabled(context) && AuthManager.isFirebaseConfigured(context) && AuthManager.isSignedIn()) {
                results += cloud.name to cloud.backup(payloadJson, pin)
            }
            results
        }

    suspend fun buildPayloadJson(): String = withContext(Dispatchers.IO) {
        buildVaultPayload(db).toJson()
    }

    /**
     * Restores from [source] ("local" or "cloud") and re-applies session
     * cookies so logged-in pages come back as they were.
     */
    suspend fun restoreFrom(pin: String, source: String): BackupResult {
        return try {
            val json = when (source) {
                "cloud" -> cloud.restore(pin)
                    ?: return BackupResult.Error("No cloud backup found")
                else -> local.restore(pin)
                    ?: return BackupResult.Error("No local backup found")
            }
            val payload = vaultPayloadFromJson(json)
            withContext(Dispatchers.IO) { restoreVaultPayload(db, payload) }
            withContext(Dispatchers.Main) { CookieBackupHelper.restoreCookies(payload.cookies) }
            BackupResult.Success
        } catch (e: Exception) {
            BackupResult.Error("Wrong PIN or corrupted backup")
        }
    }

    /** Restores from an already-decrypted payload JSON (e.g. imported .ykb file). */
    suspend fun restoreJson(payloadJson: String): BackupResult {
        return try {
            val payload = vaultPayloadFromJson(payloadJson)
            withContext(Dispatchers.IO) { restoreVaultPayload(db, payload) }
            withContext(Dispatchers.Main) { CookieBackupHelper.restoreCookies(payload.cookies) }
            BackupResult.Success
        } catch (e: Exception) {
            BackupResult.Error("Could not read backup: ${e.message}")
        }
    }
}

/** Holds the backup PIN in RAM only (never persisted) so background sync can run. */
object BackupPinHolder {
    var pin: String? = null
}
