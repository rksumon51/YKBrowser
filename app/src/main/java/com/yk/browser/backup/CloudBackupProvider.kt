package com.yk.browser.backup

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.yk.browser.crypto.VaultCrypto
import kotlinx.coroutines.tasks.await

/**
 * Cloud backup: the PIN-encrypted vault is stored in Firestore at
 * users/{uid}/vault/data. The server only ever sees ciphertext —
 * without the user's PIN the payload cannot be read.
 */
class CloudBackupProvider : BackupProvider {
    override val name = "cloud"

    private fun docRef() = FirebaseFirestore.getInstance()
        .collection("users")
        .document(FirebaseAuth.getInstance().currentUser!!.uid)
        .collection("vault")
        .document(DATA_DOC)

    override suspend fun backup(payloadJson: String, pin: String): BackupResult {
        if (FirebaseAuth.getInstance().currentUser == null) {
            return BackupResult.Error("Not signed in")
        }
        return try {
            val data = hashMapOf(
                "payload" to VaultCrypto.encrypt(payloadJson, pin),
                "schemaVersion" to VAULT_SCHEMA_VERSION,
                "updatedAt" to FieldValue.serverTimestamp(),
                "device" to android.os.Build.MODEL
            )
            docRef().set(data).await()
            BackupResult.Success
        } catch (e: Exception) {
            BackupResult.Error(e.message ?: "cloud backup failed")
        }
    }

    override suspend fun restore(pin: String): String? {
        if (FirebaseAuth.getInstance().currentUser == null) return null
        val snap = docRef().get().await()
        val encrypted = snap.getString("payload") ?: return null
        return VaultCrypto.decrypt(encrypted, pin) // throws on wrong PIN
    }

    override suspend fun hasBackup(): Boolean {
        if (FirebaseAuth.getInstance().currentUser == null) return false
        return try {
            docRef().get().await().exists()
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        private const val DATA_DOC = "data"
    }
}
