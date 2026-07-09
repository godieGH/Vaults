package com.godiegh.vaults

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

object BiometricAuthenticator {

    private const val KEY_ALIAS = "com.godiegh.vaults.biometric_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val PREFS_NAME = "biometric_hardware_vault"
    private const val KEY_CIPHERTEXT = "hw_encrypted_passphrase"
    private const val KEY_IV = "hw_iv"

    sealed class Result {
        data class Success(val authenticatedCipher: Cipher?) : Result()
        data class Error(val message: String) : Result()
        object Cancelled : Result()
    }

    fun canAuthenticate(activity: FragmentActivity): Boolean {
        val biometricManager = BiometricManager.from(activity)
        return biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    }

    /**
     * Step 1 of enabling the biometric tier: get an ENCRYPT_MODE cipher bound to the
     * hardware key. This cipher must be passed into [authenticate] as the cryptoCipher
     * so the user proves presence before the key can be used — the key requires
     * per-operation auth, so calling doFinal on an un-authenticated cipher throws
     * UserNotAuthenticatedException.
     */
    fun getEncryptionCipher(context: Context): Cipher {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!keyStore.containsAlias(KEY_ALIAS)) {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            keyGenerator.init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setUserAuthenticationRequired(true)
                    .setInvalidatedByBiometricEnrollment(true)
                    .build()
            )
            keyGenerator.generateKey()
        }

        val secretKey = keyStore.getKey(KEY_ALIAS, null) as SecretKey
        return Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, secretKey) }
    }

    /**
     * Step 2: call this from inside a successful [authenticate] callback with the
     * cipher it handed back (result.authenticatedCipher), to actually encrypt and
     * persist the passphrase under the hardware key.
     */
    fun encryptAndStorePassphrase(context: Context, authenticatedCipher: Cipher, rawPassphrase: String) {
        val ciphertext = authenticatedCipher.doFinal(rawPassphrase.toByteArray(Charsets.UTF_8))
        val iv = authenticatedCipher.iv

        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_CIPHERTEXT, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .putString(KEY_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
            .apply()
    }

    /**
     * Initializes an instances of Cipher configured for decryption.
     * Must be passed into the authenticate method inside a CryptoObject.
     */
    fun getDecryptionCipher(context: Context): Cipher? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val ivString = prefs.getString(KEY_IV, null) ?: return null
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val secretKey = keyStore.getKey(KEY_ALIAS, null) as? SecretKey ?: return null

        val iv = Base64.decode(ivString, Base64.NO_WRAP)
        return Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        }
    }

    /**
     * Extracts and decrypts the master passphrase from the authorized cipher stream.
     */
    fun decryptPassphrase(context: Context, authenticatedCipher: Cipher): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val ciphertextString = prefs.getString(KEY_CIPHERTEXT, null) ?: return null
        val ciphertext = Base64.decode(ciphertextString, Base64.NO_WRAP)
        val decryptedBytes = authenticatedCipher.doFinal(ciphertext)
        return String(decryptedBytes, Charsets.UTF_8)
    }

    fun authenticate(
        activity: FragmentActivity,
        title: String = "Unlock Vault",
        subtitle: String = "Confirm your identity to continue",
        cryptoCipher: Cipher? = null,
        onResult: (Result) -> Unit
    ) {
        if (!canAuthenticate(activity)) {
            onResult(Result.Error("Biometric authentication not available on this device"))
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onResult(Result.Success(result.cryptoObject?.cipher))
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                    errorCode == BiometricPrompt.ERROR_USER_CANCELED
                ) {
                    onResult(Result.Cancelled)
                } else {
                    onResult(Result.Error(errString.toString()))
                }
            }

            override fun onAuthenticationFailed() {}
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText("Cancel") // DEVICE_CREDENTIAL cannot be combined with custom CryptoObjects easily
            .build()

        val biometricPrompt = BiometricPrompt(activity, executor, callback)

        if (cryptoCipher != null) {
            biometricPrompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cryptoCipher))
        } else {
            biometricPrompt.authenticate(promptInfo)
        }
    }
}