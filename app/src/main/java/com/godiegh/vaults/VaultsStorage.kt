
package com.godiegh.vaults

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.core.content.edit

object VaultsStorage {

    private const val PREFS_NAME = "vaults_secure_prefs"
    private const val KEY_SALT = "salt"
    private const val KEY_TOTP_SECRET = "totp_secret"

    private fun getPrefs(context: Context) = EncryptedSharedPreferences.create(
        context,
        PREFS_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveSalt(context: Context, salt: ByteArray) {
        getPrefs(context).edit {
            putString(KEY_SALT, salt.joinToString(",") { it.toString() })
        }
    }

    fun loadSalt(context: Context): ByteArray? {
        val str = getPrefs(context).getString(KEY_SALT, null) ?: return null
        return str.split(",").map { it.toByte() }.toByteArray()
    }

    fun saveTotpSecret(context: Context, secret: String) {
        getPrefs(context).edit { putString(KEY_TOTP_SECRET, secret) }
    }

    fun loadTotpSecret(context: Context): String? {
        return getPrefs(context).getString(KEY_TOTP_SECRET, null)
    }

    fun isOnboarded(context: Context): Boolean {
        return loadSalt(context) != null && loadTotpSecret(context) != null
    }
}