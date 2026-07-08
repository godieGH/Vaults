package com.godiegh.vaults

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

object ConfigManager {

    private const val FILE_NAME = "vaults_config.json"
    // Replace with your actual GitHub Raw URL
    private const val GITHUB_RAW_URL = "https://raw.githubusercontent.com/godieGH/Vaults/refs/heads/main/app/src/main/assets/vaults_config.json"

    /**
     * Reads the JSON immediately.
     * Tries the downloaded cache first. If missing, falls back to the APK's assets.
     */
    fun getLocalConfig(context: Context): String {
        val cachedFile = File(context.filesDir, FILE_NAME)

        return if (cachedFile.exists()) {
            // Read from the downloaded update
            cachedFile.readText()
        } else {
            // Read the hardcoded fallback from the assets folder
            context.assets.open(FILE_NAME).bufferedReader().use { it.readText() }
        }
    }

    /**
     * Silently attempts to download the latest JSON from GitHub.
     * Call this in the background when the app launches or reaches the main screen.
     */
    suspend fun syncWithGitHub(context: Context) {
        withContext(Dispatchers.IO) {
            try {
                // Fetch the latest text from GitHub
                val liveJson = URL(GITHUB_RAW_URL).readText()

                // If successful, save it to internal storage, overwriting the old one
                val cachedFile = File(context.filesDir, FILE_NAME)
                cachedFile.writeText(liveJson)

            } catch (e: Exception) {
                // User has no internet, or GitHub is down.
                // We silently ignore the error. The app will continue using the local cache or assets.
                e.printStackTrace()
            }
        }
    }
}