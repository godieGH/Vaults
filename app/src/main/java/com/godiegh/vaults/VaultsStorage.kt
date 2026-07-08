
package com.godiegh.vaults

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.core.content.edit
import android.util.Base64
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

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

    private const val KEY_SETUP_STEP = "setup_step"
    const val STEP_ONBOARDING = "onboarding"
    const val STEP_2FA_SETUP = "2fa_setup"
    const val STEP_COMPLETED = "completed"

    fun saveSetupStep(context: Context, step: String) {
        getPrefs(context).edit { putString(KEY_SETUP_STEP, step) }
    }

    fun loadSetupStep(context: Context): String {
        return getPrefs(context).getString(KEY_SETUP_STEP, STEP_ONBOARDING) ?: STEP_ONBOARDING
    }

    private const val KEY_FINGERPRINT = "passphrase_fingerprint"

    fun saveFingerprint(context: Context, fingerprint: String) {
        getPrefs(context).edit { putString(KEY_FINGERPRINT, fingerprint) }
    }

    fun loadFingerprint(context: Context): String? {
        return getPrefs(context).getString(KEY_FINGERPRINT, null)
    }

    private const val KEY_AUTO_SYNC_ENABLED = "auto_sync_enabled"
    private const val KEY_AUTO_SYNC_SALT = "auto_sync_salt"
    private const val KEY_AUTO_SYNC_KEY_MATERIAL = "auto_sync_key_material"
    private const val KEY_DRIVE_ACCOUNT_EMAIL = "drive_account_email"

    fun isAutoSyncEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUTO_SYNC_ENABLED, false)
    }

    fun loadDriveAccountEmail(context: Context): String? {
        return getPrefs(context).getString(KEY_DRIVE_ACCOUNT_EMAIL, null)
    }

    /**
     * Derives an AES-256 key from the auto-sync password (same PBKDF2 params as
     * the manual backup encryption) and persists salt + key material + the
     * enabled flag + drive account email. EncryptedSharedPreferences already
     * encrypts values at rest, so storing the derived key here (rather than the
     * raw password) is what lets the background sync worker encrypt future
     * backups without prompting the user each time.
     */
    fun enableAutoSync(context: Context, password: String, driveAccountEmail: String) {
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)

        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, 65536, 256)
        val derivedKey = factory.generateSecret(spec).encoded

        getPrefs(context).edit {
            putString(KEY_AUTO_SYNC_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
            putString(KEY_AUTO_SYNC_KEY_MATERIAL, Base64.encodeToString(derivedKey, Base64.NO_WRAP))
            putString(KEY_DRIVE_ACCOUNT_EMAIL, driveAccountEmail)
            putBoolean(KEY_AUTO_SYNC_ENABLED, true)
        }
    }

    /** Returns the derived AES key for the sync worker to use, or null if not configured. */
    fun loadAutoSyncKey(context: Context): ByteArray? {
        val str = getPrefs(context).getString(KEY_AUTO_SYNC_KEY_MATERIAL, null) ?: return null
        return Base64.decode(str, Base64.NO_WRAP)
    }

    fun clearAutoSyncConfig(context: Context) {
        getPrefs(context).edit {
            remove(KEY_AUTO_SYNC_ENABLED)
            remove(KEY_AUTO_SYNC_SALT)
            remove(KEY_AUTO_SYNC_KEY_MATERIAL)
            remove(KEY_DRIVE_ACCOUNT_EMAIL)
        }
    }

    fun hasPendingAutoSyncChanges(context: Context): Boolean {
        return getPrefs(context).getBoolean("auto_sync_pending", false)
    }

    fun markAutoSyncDirty(context: Context) {
        getPrefs(context).edit { putBoolean("auto_sync_pending", true) }
    }

    fun markAutoSyncSynced(context: Context) {
        getPrefs(context).edit { putBoolean("auto_sync_pending", false) }
    }

    fun saveDriveAccountEmail(context: Context, email: String) {
        getPrefs(context).edit { putString(KEY_DRIVE_ACCOUNT_EMAIL, email) }
    }

    fun clearDriveAccountEmail(context: Context) {
        getPrefs(context).edit { remove(KEY_DRIVE_ACCOUNT_EMAIL) }
    }

    // --- WorkManager scheduling helpers ---

    private const val SYNC_WORK_NAME = "vaults_auto_sync"

    fun schedulePeriodicSync(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
    }

    fun scheduleOneTimeSync(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "vaults_manual_sync",
            ExistingWorkPolicy.REPLACE,
            syncRequest
        )
    }

    fun cancelSync(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(SYNC_WORK_NAME)
    }

    /**
     * Decrypts a backup payload specifically generated by the SyncWorker.
     */
    fun decryptAutoSyncBackup(payload: String, context: Context): String? {
        if (!payload.startsWith("sync_v1:")) return null
        val parts = payload.split(":")
        if (parts.size != 3) return null

        val keyMaterial = loadAutoSyncKey(context) ?: return null

        return try {
            val iv = Base64.decode(parts[1], Base64.NO_WRAP)
            val ciphertext = Base64.decode(parts[2], Base64.NO_WRAP)

            val secretKey = SecretKeySpec(keyMaterial, "AES")
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            val gcmSpec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)

            val decrypted = cipher.doFinal(ciphertext)
            String(decrypted, Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    fun loadAutoSyncSalt(context: Context): ByteArray? {
        val str = getPrefs(context).getString(KEY_AUTO_SYNC_SALT, null) ?: return null
        return Base64.decode(str, Base64.NO_WRAP)
    }
}