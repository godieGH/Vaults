
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

    private const val KEY_SERVICES = "services"

    fun saveServices(context: Context, services: List<ServiceConfig>) {
        val array = org.json.JSONArray()
        services.forEach { s ->
            val obj = org.json.JSONObject()
            obj.put("id", s.id)
            obj.put("name", s.name)
            obj.put("countryCode", s.countryCode)
            obj.put("identifier", s.identifier)
            obj.put("pinLength", s.pinLength)
            obj.put("rotation", s.rotation)
            array.put(obj)
            obj.put("displayName", s.displayName)
            obj.put("category", s.category)
        }
        getPrefs(context).edit { putString(KEY_SERVICES, array.toString()) }
    }

    fun loadServices(context: Context): List<ServiceConfig> {
        val str = getPrefs(context).getString(KEY_SERVICES, null) ?: return emptyList()
        val array = org.json.JSONArray(str)
        return (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            ServiceConfig(
                id = obj.getString("id"),
                name = obj.getString("name"),
                countryCode = obj.getString("countryCode"),
                identifier = obj.getString("identifier"),
                pinLength = obj.getInt("pinLength"),
                rotation = obj.optInt("rotation", 1), // old entries saved before this feature existed
                displayName = obj.optString("displayName", obj.getString("name").replaceFirstChar { it.uppercase() }),
                category = obj.optString("category", "MOBILE_MONEY")
            )
        }
    }

    private const val KEY_SECURITY_TIER = "security_tier"
    private const val KEY_ENCRYPTED_PASSPHRASE = "encrypted_passphrase"

    // Tier values
    const val TIER_PASSPHRASE_ONLY = 0
    const val TIER_BIOMETRIC = 1
    const val TIER_TOTP = 2
    const val TIER_TOTP_BIOMETRIC = 3

    fun saveTier(context: Context, tier: Int) {
        getPrefs(context).edit { putInt(KEY_SECURITY_TIER, tier) }
    }

    fun loadTier(context: Context): Int {
        return getPrefs(context).getInt(KEY_SECURITY_TIER, TIER_PASSPHRASE_ONLY)
    }

    fun saveEncryptedPassphrase(context: Context, passphrase: String) {
        getPrefs(context).edit { putString(KEY_ENCRYPTED_PASSPHRASE, passphrase) }
    }

    fun loadEncryptedPassphrase(context: Context): String? {
        return getPrefs(context).getString(KEY_ENCRYPTED_PASSPHRASE, null)
    }

    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_ACCENT_PRESET = "accent_preset"

    const val THEME_SYSTEM = 0
    const val THEME_LIGHT = 1
    const val THEME_DARK = 2

    const val ACCENT_CLASSIC = 0
    const val ACCENT_EMERALD = 1
    const val ACCENT_SAPPHIRE = 2
    const val ACCENT_ROSE = 3

    fun saveThemeMode(context: Context, mode: Int) {
        getPrefs(context).edit { putInt(KEY_THEME_MODE, mode) }
    }

    fun loadThemeMode(context: Context): Int {
        return getPrefs(context).getInt(KEY_THEME_MODE, THEME_SYSTEM)
    }

    fun saveAccentPreset(context: Context, preset: Int) {
        getPrefs(context).edit { putInt(KEY_ACCENT_PRESET, preset) }
    }

    fun loadAccentPreset(context: Context): Int {
        return getPrefs(context).getInt(KEY_ACCENT_PRESET, ACCENT_CLASSIC)
    }

    fun clearAll(context: Context) {
        getPrefs(context).edit { clear() }
    }


    private const val KEY_VIEW_MODE = "view_mode"
    const val VIEW_MODE_LIST = 0
    const val VIEW_MODE_GRID = 1

    fun saveViewMode(context: Context, mode: Int) {
        getPrefs(context).edit { putInt(KEY_VIEW_MODE, mode) }
    }

    fun loadViewMode(context: Context): Int {
        return getPrefs(context).getInt(KEY_VIEW_MODE, VIEW_MODE_LIST)
    }

    private const val KEY_FINGERPRINT = "passphrase_fingerprint"

    fun saveFingerprint(context: Context, fingerprint: String) {
        getPrefs(context).edit { putString(KEY_FINGERPRINT, fingerprint) }
    }

    fun loadFingerprint(context: Context): String? {
        return getPrefs(context).getString(KEY_FINGERPRINT, null)
    }
}