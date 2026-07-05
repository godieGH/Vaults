package com.godiegh.vaults

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

object BiometricAuthenticator {

    sealed class Result {
        object Success : Result()
        data class Error(val message: String) : Result()
        object Cancelled : Result()
    }

    fun canAuthenticate(activity: FragmentActivity): Boolean {
        val biometricManager = BiometricManager.from(activity)
        return biometricManager.canAuthenticate(
            BiometricManager.Authenticators.BIOMETRIC_STRONG
        ) == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun authenticate(
        activity: FragmentActivity,
        title: String = "Unlock Vault",
        subtitle: String = "Confirm your identity to reveal this PIN",
        onResult: (Result) -> Unit
    ) {
        if (!canAuthenticate(activity)) {
            onResult(Result.Error("Biometric authentication not available on this device"))
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)

        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onResult(Result.Success)
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                // Codes 10 (negative button) and 13 (user cancel) are just dismissals, not failures
                if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                    errorCode == BiometricPrompt.ERROR_USER_CANCELED
                ) {
                    onResult(Result.Cancelled)
                } else {
                    onResult(Result.Error(errString.toString()))
                }
            }

            override fun onAuthenticationFailed() {
                // Called on a single failed scan — don't dismiss, let the user retry.
                // Prompt stays open on its own; no action needed here.
            }
        }

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButtonText("Cancel")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()

        val biometricPrompt = BiometricPrompt(activity, executor, callback)
        biometricPrompt.authenticate(promptInfo)
    }
}