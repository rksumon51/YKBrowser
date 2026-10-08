package com.yk.browser.crypto

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Vault encryption: AES-256-GCM with a key derived from the user's backup PIN
 * via PBKDF2-HMAC-SHA256 (120k iterations). The whole vault payload is encrypted
 * on the device BEFORE it leaves the phone, so neither the cloud provider nor
 * anyone holding the backup file can read it without the PIN.
 *
 * Wire format: salt(16) || iv(12) || ciphertext, Base64-encoded.
 */
object VaultCrypto {
    private const val ITERATIONS = 120_000
    private const val KEY_LEN_BITS = 256

    fun encrypt(plain: String, pin: String): String {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, derive(pin, salt))
        val out = salt + cipher.iv + cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    /** @throws Exception on wrong PIN or corrupted data (GCM auth tag mismatch). */
    fun decrypt(encoded: String, pin: String): String {
        val all = Base64.decode(encoded, Base64.NO_WRAP)
        require(all.size > 28) { "corrupted vault" }
        val salt = all.copyOfRange(0, 16)
        val iv = all.copyOfRange(16, 28)
        val ct = all.copyOfRange(28, all.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, derive(pin, salt), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(ct), Charsets.UTF_8)
    }

    private fun derive(pin: String, salt: ByteArray) = SecretKeySpec(
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_LEN_BITS)).encoded,
        "AES"
    )
}
