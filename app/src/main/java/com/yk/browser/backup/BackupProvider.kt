package com.yk.browser.backup

/** A backup destination. Both cloud and local providers implement this,
 *  so the rest of the app never cares where the vault lives. */
interface BackupProvider {
    val name: String

    /** Encrypts [payloadJson] with [pin] and stores it. */
    suspend fun backup(payloadJson: String, pin: String): BackupResult

    /** Returns the decrypted payload JSON, or null if no backup exists.
     *  @throws Exception on wrong PIN / corrupted data. */
    suspend fun restore(pin: String): String?

    suspend fun hasBackup(): Boolean
}

sealed class BackupResult {
    object Success : BackupResult()
    data class Error(val message: String) : BackupResult()
}
