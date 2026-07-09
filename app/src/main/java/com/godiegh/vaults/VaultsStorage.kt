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
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object VaultsStorage {

    // --- Keystore wrap key for the cached auto-sync key material ---
    // This does NOT replace the password-derived key used to encrypt backup
    // content (that must stay password-portable so a fresh device can restore).
    // It only protects the on-device *cache* of that derived key from being
    // lifted out of EncryptedSharedPreferences on a rooted/compromised device:
    // the wrap key is hardware-backed and non-exportable, and (API 30+) can
    // only be used while the device is unlocked.
    private const val AUTOSYNC_WRAP_KEY_ALIAS = "com.godiegh.vaults.autosync_wrap_key"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val WRAP_TRANSFORMATION = "AES/GCM/NoPadding"

    private fun getOrCreateAutoSyncWrapKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(AUTOSYNC_WRAP_KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val builder = KeyGenParameterSpec.Builder(
            AUTOSYNC_WRAP_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            // Deliberately NOT setUserAuthenticationRequired(true) — this key must be
            // usable by a background WorkManager job with no user present.
            .setUserAuthenticationRequired(false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Requires the device to have been unlocked at least once since boot
            // and not be in a "before first unlock" state. Background sync will
            // simply retry later (SyncWorker already treats exceptions as retry).
            builder.setUnlockedDeviceRequired(true)
        }

        keyGenerator.init(builder.build())
        return keyGenerator.generateKey()
    }

    /** Wraps raw key bytes for at-rest storage. Format: ivB64:ciphertextB64 */
    private fun wrapAutoSyncKeyMaterial(raw: ByteArray): String {
        val cipher = Cipher.getInstance(WRAP_TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, getOrCreateAutoSyncWrapKey())
        }
        val ciphertext = cipher.doFinal(raw)
        return Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" +
                Base64.encodeToString(ciphertext, Base64.NO_WRAP)
    }

    /** Reverses [wrapAutoSyncKeyMaterial]. Returns null if unavailable (e.g. device locked). */
    private fun unwrapAutoSyncKeyMaterial(wrapped: String): ByteArray? {
        return try {
            val parts = wrapped.split(":")
            if (parts.size != 2) return null
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val ciphertext = Base64.decode(parts[1], Base64.NO_WRAP)
            val cipher = Cipher.getInstance(WRAP_TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, getOrCreateAutoSyncWrapKey(), GCMParameterSpec(128, iv))
            }
            cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            null
        }
    }

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
            putString(KEY_AUTO_SYNC_KEY_MATERIAL, wrapAutoSyncKeyMaterial(derivedKey))
            putString(KEY_DRIVE_ACCOUNT_EMAIL, driveAccountEmail)
            putBoolean(KEY_AUTO_SYNC_ENABLED, true)
        }
    }

    /**
     * Returns the derived AES key for the sync worker to use, or null if not configured
     * or if the Keystore wrap key is currently unusable (e.g. device locked since boot
     * on API 30+ with setUnlockedDeviceRequired). SyncWorker already treats a null/failed
     * result as retryable.
     */
    fun loadAutoSyncKey(context: Context): ByteArray? {
        val str = getPrefs(context).getString(KEY_AUTO_SYNC_KEY_MATERIAL, null) ?: return null
        return unwrapAutoSyncKeyMaterial(str)
    }

    fun clearAutoSyncConfig(context: Context) {
        getPrefs(context).edit {
            remove(KEY_AUTO_SYNC_ENABLED)
            remove(KEY_AUTO_SYNC_SALT)
            remove(KEY_AUTO_SYNC_KEY_MATERIAL)
            remove(KEY_DRIVE_ACCOUNT_EMAIL)
        }
        try {
            KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
                .deleteEntry(AUTOSYNC_WRAP_KEY_ALIAS)
        } catch (e: Exception) {
            // best-effort cleanup
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



    // Inside VaultsStorage object

    /**
     * Packs the entire current state of the vault into a single JSON string.
     * Reusable for manual exports, background auto-sync, or troubleshooting.
     */
    fun exportVaultAsJson(context: Context): String {
        val backupObj = org.json.JSONObject()
        backupObj.put("version", 1)
        backupObj.put("salt", loadSalt(context)?.joinToString("") { "%02x".format(it) } ?: "")
        backupObj.put("totp_secret", loadTotpSecret(context) ?: "")
        backupObj.put("passphrase_fingerprint", loadFingerprint(context) ?: "")
        backupObj.put("encrypted_passphrase", loadEncryptedPassphrase(context) ?: "")

        val services = loadServices(context)
        val servicesArray = org.json.JSONArray()
        services.forEach { s ->
            val obj = org.json.JSONObject()
            obj.put("id", s.id)
            obj.put("name", s.name)
            obj.put("countryCode", s.countryCode)
            obj.put("identifier", s.identifier)
            obj.put("pinLength", s.pinLength)
            obj.put("rotation", s.rotation)
            obj.put("displayName", s.displayName)
            obj.put("category", s.category)
            servicesArray.put(obj)
        }
        backupObj.put("services", servicesArray)
        return backupObj.toString()
    }
}