package com.godiegh.vaults

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.godiegh.vaults.VaultsStorage.TIER_BIOMETRIC
import com.godiegh.vaults.VaultsStorage.TIER_PASSPHRASE_ONLY
import com.godiegh.vaults.VaultsStorage.TIER_TOTP
import com.godiegh.vaults.VaultsStorage.TIER_TOTP_BIOMETRIC

/**
 * No longer takes the passphrase as a parameter — every caller (onboarding,
 * restore, and "change 2FA method" from settings) now writes it to
 * VaultsStorage *before* navigating here, and this screen reads it back from
 * there. That keeps the passphrase out of the nav back stack entirely.
 */
@Composable
fun TwoFactorSetupScreen(navController: NavController) {
    val context = LocalContext.current
    val activity = context as? androidx.fragment.app.FragmentActivity

    val passphrase = remember { VaultsStorage.loadEncryptedPassphrase(context) }

    // Shouldn't normally happen — every entry point saves the passphrase first —
    // but bail safely rather than proceeding with a blank secret.
    if (passphrase.isNullOrEmpty()) {
        LaunchedEffect(Unit) { navController.popBackStack() }
        return
    }

    var errorMessage by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }

    // Check if biometric authentication is available and enrolled
    val isBiometricAvailable = remember {
        val biometricManager = BiometricManager.from(context)
        val result = biometricManager.canAuthenticate(BIOMETRIC_STRONG)
        result == BiometricManager.BIOMETRIC_SUCCESS
    }

    fun proceedToTotpSetup() {
        navController.navigate("totp_setup") {
            popUpTo("onboarding") { inclusive = true }
        }
    }

    /**
     * Common path for both biometric tiers: get an encryption cipher, run it
     * through a biometric prompt so the key can actually be used, then persist
     * the hardware-bound copy once authenticated — before continuing.
     */
    fun setUpBiometricThen(onDone: () -> Unit) {
        if (activity == null) {
            errorMessage = "Unable to launch biometric prompt"
            return
        }
        isProcessing = true
        val encryptionCipher = BiometricAuthenticator.getEncryptionCipher(context)
        BiometricAuthenticator.authenticate(
            activity,
            title = "Secure Your Vault",
            subtitle = "Confirm your identity to enable biometric unlock",
            cryptoCipher = encryptionCipher
        ) { result ->
            isProcessing = false
            when (result) {
                is BiometricAuthenticator.Result.Success -> {
                    val authCipher = result.authenticatedCipher
                    if (authCipher == null) {
                        errorMessage = "Failed to secure biometric storage."
                        return@authenticate
                    }
                    BiometricAuthenticator.encryptAndStorePassphrase(context, authCipher, passphrase)
                    errorMessage = ""
                    onDone()
                }
                is BiometricAuthenticator.Result.Error -> errorMessage = result.message
                is BiometricAuthenticator.Result.Cancelled -> {}
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Choose Your Security Method",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "How do you want to unlock your vault?",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(32.dp))

            if (errorMessage.isNotEmpty()) {
                Text(errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Option 1 — Passphrase only (Always available)
            SecurityOptionCard(
                icon = { Icon(Icons.Filled.LockOpen, null, modifier = Modifier.size(32.dp)) },
                title = "Passphrase Only",
                description = "Type your master passphrase every time you reveal a PIN. No extra setup.",
                enabled = !isProcessing,
                onClick = {
                    VaultsStorage.saveTier(context, TIER_PASSPHRASE_ONLY)
                    VaultsStorage.saveSetupStep(context, VaultsStorage.STEP_COMPLETED)
                    navController.navigate("main") {
                        popUpTo("onboarding") { inclusive = true }
                    }
                }
            )

            // Option 2 — Biometric (Conditional)
            if (isBiometricAvailable) {
                Spacer(modifier = Modifier.height(12.dp))
                SecurityOptionCard(
                    icon = { Icon(Icons.Filled.Fingerprint, null, modifier = Modifier.size(32.dp)) },
                    title = "Biometric (Recommended)",
                    description = "Use fingerprint or face unlock. Your passphrase is stored securely on device.",
                    enabled = !isProcessing,
                    onClick = {
                        setUpBiometricThen {
                            VaultsStorage.saveTier(context, TIER_BIOMETRIC)
                            VaultsStorage.saveSetupStep(context, VaultsStorage.STEP_COMPLETED)
                            navController.navigate("main") {
                                popUpTo("onboarding") { inclusive = true }
                            }
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Option 3 — TOTP (Always available)
            SecurityOptionCard(
                icon = { Icon(Icons.Filled.Shield, null, modifier = Modifier.size(32.dp)) },
                title = "TOTP Authenticator",
                description = "Use a time-based one-time code from Google Authenticator before each PIN reveal.",
                enabled = !isProcessing,
                onClick = {
                    VaultsStorage.saveTier(context, TIER_TOTP)
                    proceedToTotpSetup()
                }
            )

            // Option 4 — TOTP + Biometric (Conditional)
            if (isBiometricAvailable) {
                Spacer(modifier = Modifier.height(12.dp))
                SecurityOptionCard(
                    icon = { Icon(Icons.Filled.Lock, null, modifier = Modifier.size(32.dp)) },
                    title = "TOTP + Biometric",
                    description = "Strongest option. Requires both an authenticator code and your fingerprint.",
                    enabled = !isProcessing,
                    onClick = {
                        setUpBiometricThen {
                            VaultsStorage.saveTier(context, TIER_TOTP_BIOMETRIC)
                            proceedToTotpSetup()
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun SecurityOptionCard(
    icon: @Composable () -> Unit,
    title: String,
    description: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        enabled = enabled
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            icon()
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}