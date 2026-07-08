package com.godiegh.vaults

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.tasks.await
import android.accounts.Account
import com.google.android.gms.auth.api.identity.RevokeAccessRequest

/**
 * Thin wrapper around Google's Identity Services AuthorizationClient, scoped
 * to the app's hidden Drive appDataFolder.
 *
 * Deliberately NOT using the legacy GoogleSignInClient — Google now separates
 * authentication (Credential Manager) from authorization (AuthorizationClient)
 * for scope grants like Drive access, and the old sign-in-with-scopes flow is
 * being phased out. Since we only need Drive access (not a full sign-in /
 * profile), we skip Credential Manager entirely and go straight to
 * AuthorizationClient — the friendly email shown in the UI comes from Drive's
 * own `about` endpoint after access is granted, not from a separate identity.
 *
 * Access tokens returned here are short-lived (~1 hour) and are not persisted
 * anywhere: once the scope is granted, calling authorize() again (e.g. from
 * the background sync worker) silently returns a fresh token with no UI,
 * as long as the user hasn't revoked access.
 */
object GoogleDriveAuth {

    private val DRIVE_APPDATA_SCOPE = Scope(DriveScopes.DRIVE_APPDATA)

    private fun buildAuthorizationRequest(): AuthorizationRequest {
        return AuthorizationRequest.builder()
            .setRequestedScopes(listOf(DRIVE_APPDATA_SCOPE))
            .build()
    }

    /**
     * Requests Drive appDataFolder access. If already granted, this resolves
     * immediately with a usable access token and no UI. If not, the result
     * carries a PendingIntent the caller must launch via
     * ActivityResultContracts.StartIntentSenderForResult.
     */
    suspend fun authorize(context: Context): AuthorizationResult {
        return Identity.getAuthorizationClient(context)
            .authorize(buildAuthorizationRequest())
            .await()
    }

    /** Call from the ActivityResult callback after launching a PendingIntent from authorize(). */
    fun resultFromIntent(context: Context, intent: Intent): AuthorizationResult {
        return Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(intent)
    }

    /** Builds a Drive service using a short-lived access token as a bearer credential. */
    fun buildDriveService(accessToken: String): Drive {
        val requestInitializer = HttpRequestInitializer { request ->
            request.headers.authorization = "Bearer $accessToken"
        }
        return Drive.Builder(NetHttpTransport(), GsonFactory.getDefaultInstance(), requestInitializer)
            .setApplicationName("Vaults")
            .build()
    }

    /**
     * Fetches the connected account's email via Drive's `about` endpoint —
     * this is a network call, so run it off the main thread (Dispatchers.IO).
     * Returns null on any failure; callers should fall back to a generic label.
     */
    /**
     * Fetches the connected account's email via Drive's `about` endpoint —
     * this is a network call, so run it off the main thread (Dispatchers.IO).
     * Returns null on any failure; callers should fall back to a generic label.
     */
    fun fetchAccountEmail(drive: Drive): String? {
        return try {
            drive.about().get().setFields("user").execute().user?.emailAddress
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Round-trip test: writes a tiny file to the hidden appDataFolder, then
     * reads it back. Since appDataFolder is invisible in the normal Drive UI,
     * this is the only real way to confirm the connection actually works end
     * to end, rather than just "authorization succeeded."
     */
    fun testConnection(drive: Drive): Result<String> {
        return try {
            val fileMetadata = com.google.api.services.drive.model.File().apply {
                name = "vaults_connection_test.txt"
                parents = listOf("appDataFolder")
            }
            val content = com.google.api.client.http.ByteArrayContent(
                "text/plain",
                "Vaults connection test — ${System.currentTimeMillis()}".toByteArray()
            )
            val uploaded = drive.files().create(fileMetadata, content)
                .setFields("id, name")
                .execute()

            val listed = drive.files().list()
                .setSpaces("appDataFolder")
                .setFields("files(id, name)")
                .execute()

            Result.success(
                "Wrote \"${uploaded.name}\" and found ${listed.files.size} file(s) in appDataFolder."
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Revokes the existing authorization grant for the specified account.
     * This forces the account picker to appear the next time authorize() is called.
     */
    suspend fun revokeAccess(context: Context, email: String) {
        val account = Account(email, "com.google")
        val request = RevokeAccessRequest.builder()
            .setAccount(account)
            .setScopes(listOf(DRIVE_APPDATA_SCOPE))
            .build()

        try {
            // Identity.getAuthorizationClient uses the updated play-services-auth library
            Identity.getAuthorizationClient(context).revokeAccess(request).await()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * Lists all vaults backup files in the appDataFolder.
     * Backups are named like 'vaults_backup_20240708_120000.txt'
     */
    fun listBackups(drive: Drive): List<com.google.api.services.drive.model.File> {
        return try {
            val result = drive.files().list()
                .setSpaces("appDataFolder")
                .setQ("name contains 'vaults_backup_' and trashed = false")
                .setFields("files(id, name, createdTime, size)")
                .setOrderBy("createdTime desc")
                .execute()
            result.files ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Downloads the content of a specific file from Drive.
     */
    fun downloadFile(drive: Drive, fileId: String): String? {
        return try {
            val outputStream = java.io.ByteArrayOutputStream()
            drive.files().get(fileId).executeMediaAndDownloadTo(outputStream)
            outputStream.toString("UTF-8")
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Moves a file to Drive's trash rather than permanently deleting it — Google
     * retains trashed files for ~30 days before final purge, so a wrong tap here
     * (or reaching this screen under duress) still leaves a recovery window.
     */
    fun trashFile(drive: Drive, fileId: String): Boolean {
        return try {
            val metadata = com.google.api.services.drive.model.File().apply { trashed = true }
            drive.files().update(fileId, metadata).execute()
            true
        } catch (e: Exception) {
            false
        }
    }
}