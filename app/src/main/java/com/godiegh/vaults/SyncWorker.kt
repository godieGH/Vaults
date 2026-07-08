package com.godiegh.vaults

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.api.client.http.ByteArrayContent
import com.google.api.services.drive.model.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class SyncWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        if (!VaultsStorage.isAutoSyncEnabled(context)) {
            Log.d("SyncWorker", "Auto-sync disabled, skipping.")
            return@withContext Result.success()
        }

        if (!VaultsStorage.hasPendingAutoSyncChanges(context)) {
            Log.d("SyncWorker", "No pending changes, skipping.")
            return@withContext Result.success()
        }

        try {
            val authResult = GoogleDriveAuth.authorize(context)
            val accessToken = authResult.accessToken ?: return@withContext Result.retry()
            val drive = GoogleDriveAuth.buildDriveService(accessToken)

            // 1. Build Payload
            val backupObj = JSONObject()
            backupObj.put("version", 1)
            backupObj.put("salt", VaultsStorage.loadSalt(context)?.joinToString("") { "%02x".format(it) } ?: "")
            backupObj.put("totp_secret", VaultsStorage.loadTotpSecret(context) ?: "")
            backupObj.put("passphrase_fingerprint", VaultsStorage.loadFingerprint(context) ?: "")
            backupObj.put("encrypted_passphrase", VaultsStorage.loadEncryptedPassphrase(context) ?: "")

            val services = VaultsStorage.loadServices(context)
            val servicesArray = JSONArray()
            services.forEach { s ->
                val obj = JSONObject()
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

            // 2. Encrypt with AutoSync Key
            val keyMaterial = VaultsStorage.loadAutoSyncKey(context) ?: return@withContext Result.failure()
            val salt = VaultsStorage.loadAutoSyncSalt(context) ?: return@withContext Result.failure()
            val encryptedPayload = encryptWithStoredKey(backupObj.toString(), keyMaterial, salt)

            // 3. Upload to Drive
            val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
            val timestamp = sdf.format(Date())
            val fileMetadata = File().apply {
                name = "vaults_backup_$timestamp.vbak"
                parents = listOf("appDataFolder")
            }
            val mediaContent = ByteArrayContent("text/plain", encryptedPayload.toByteArray(Charsets.UTF_8))

            drive.files().create(fileMetadata, mediaContent)
                .setFields("id")
                .execute()

            VaultsStorage.markAutoSyncSynced(context)
            Log.d("SyncWorker", "Sync successful: ${fileMetadata.name}")
            Result.success()
        } catch (e: Exception) {
            Log.e("SyncWorker", "Sync failed", e)
            Result.retry()
        }
    }

    private fun encryptWithStoredKey(payload: String, keyMaterial: ByteArray, salt: ByteArray): String {
        val iv = ByteArray(12)
        SecureRandom().nextBytes(iv)

        val secretKey = SecretKeySpec(keyMaterial, "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)

        val ciphertext = cipher.doFinal(payload.toByteArray(Charsets.UTF_8))

        val saltB64 = Base64.encodeToString(salt, Base64.NO_WRAP)
        val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        val ciphertextB64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP)

        // Now embeds the salt, so a fresh device can re-derive the key from the
        // password alone — same shape as the manual enc_v1: format.
        return "sync_v1:$saltB64:$ivB64:$ciphertextB64"
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "vaults_auto_sync_periodic"
        private const val IMMEDIATE_WORK_NAME = "vaults_auto_sync_now"

        fun schedulePeriodic(context: Context) {
            val request = androidx.work.PeriodicWorkRequestBuilder<SyncWorker>(6, java.util.concurrent.TimeUnit.HOURS)
                .setConstraints(
                    androidx.work.Constraints.Builder()
                        .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                        .build()
                )
                .build()
            androidx.work.WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                androidx.work.ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun cancelPeriodic(context: Context) {
            androidx.work.WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK_NAME)
        }

        fun enqueueImmediate(context: Context) {
            val request = androidx.work.OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(
                    androidx.work.Constraints.Builder()
                        .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                        .build()
                )
                .setExpedited(androidx.work.OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE_WORK_NAME,
                androidx.work.ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
