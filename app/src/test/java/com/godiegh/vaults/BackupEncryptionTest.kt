package com.godiegh.vaults

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import java.util.Base64 // Use java.util.Base64 for JVM unit tests

class BackupEncryptionTest {

    @Test
    fun testEncryptionDecryption() {
        val payload = "{\"version\":1,\"salt\":\"aabbcc\"}"
        val password = "test_password"

        val encrypted = encryptBackup(payload, password)
        assertTrue(encrypted.startsWith("enc_v1:"))
        
        val decrypted = decryptBackup(encrypted, password)
        assertEquals(payload, decrypted)
    }

    @Test
    fun testDecryptionFailureWithWrongPassword() {
        val payload = "{\"version\":1,\"salt\":\"aabbcc\"}"
        val password = "test_password"
        val wrongPassword = "wrong_password"

        val encrypted = encryptBackup(payload, password)
        val decrypted = decryptBackup(encrypted, wrongPassword)
        assertTrue(decrypted == null)
    }

    private fun encryptBackup(payload: String, password: String): String {
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)

        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, 65536, 256)
        val tmp = factory.generateSecret(spec)
        val secret = SecretKeySpec(tmp.encoded, "AES")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secret)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(payload.toByteArray(Charsets.UTF_8))

        val saltB64 = Base64.getEncoder().encodeToString(salt)
        val ivB64 = Base64.getEncoder().encodeToString(iv)
        val ciphertextB64 = Base64.getEncoder().encodeToString(ciphertext)

        return "enc_v1:$saltB64:$ivB64:$ciphertextB64"
    }

    private fun decryptBackup(payload: String, password: String): String? {
        if (!payload.startsWith("enc_v1:")) return null
        
        val parts = payload.split(":")
        if (parts.size != 4) return null
        
        return try {
            val salt = Base64.getDecoder().decode(parts[1])
            val iv = Base64.getDecoder().decode(parts[2])
            val ciphertext = Base64.getDecoder().decode(parts[3])

            val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            val spec = PBEKeySpec(password.toCharArray(), salt, 65536, 256)
            val tmp = factory.generateSecret(spec)
            val secret = SecretKeySpec(tmp.encoded, "AES")

            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmSpec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, secret, gcmSpec)

            val decrypted = cipher.doFinal(ciphertext)
            String(decrypted, Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }
}