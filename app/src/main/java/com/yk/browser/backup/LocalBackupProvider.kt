package com.yk.browser.backup

import android.content.Context
import android.net.Uri
import com.yk.browser.crypto.VaultCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Phone-local backup: the encrypted vault is kept as a file in the app's
 * private storage (auto backup) and can also be exported / imported by the
 * user through the system file picker (.ykb file).
 */
class LocalBackupProvider(private val context: Context) : BackupProvider {
    override val name = "local"

    private fun autoFile(): File =
        File(context.filesDir, "backups").apply { mkdirs() }.resolve(AUTO_FILE)

    override suspend fun backup(payloadJson: String, pin: String): BackupResult =
        withContext(Dispatchers.IO) {
            try {
                autoFile().writeText(VaultCrypto.encrypt(payloadJson, pin))
                BackupResult.Success
            } catch (e: Exception) {
                BackupResult.Error(e.message ?: "local backup failed")
            }
        }

    override suspend fun restore(pin: String): String? = withContext(Dispatchers.IO) {
        val f = autoFile()
        if (!f.exists()) return@withContext null
        VaultCrypto.decrypt(f.readText(), pin) // throws on wrong PIN
    }

    override suspend fun hasBackup(): Boolean = withContext(Dispatchers.IO) {
        autoFile().exists()
    }

    /** Export the encrypted vault to a user-chosen location (.ykb file). */
    suspend fun exportTo(uri: Uri, payloadJson: String, pin: String): BackupResult =
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(VaultCrypto.encrypt(payloadJson, pin).toByteArray(Charsets.UTF_8))
                } ?: return@withContext BackupResult.Error("cannot open file")
                BackupResult.Success
            } catch (e: Exception) {
                BackupResult.Error(e.message ?: "export failed")
            }
        }

    /** Read an exported .ykb file and decrypt it. @throws Exception on wrong PIN. */
    suspend fun importFrom(uri: Uri, pin: String): String = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)?.use {
            it.readBytes().toString(Charsets.UTF_8)
        } ?: throw IllegalStateException("cannot open file")
        VaultCrypto.decrypt(text, pin)
    }

    companion object {
        private const val AUTO_FILE = "ykbrowser-auto.ykb"
    }
}
