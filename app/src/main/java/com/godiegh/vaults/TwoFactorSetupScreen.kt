package com.godiegh.vaults

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

@Composable
fun TwoFactorSetupScreen(
    passphrase: String,
    navController: NavController
) {
    val context = LocalContext.current
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        )
        {
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

            // Option 1 — Passphrase only
            SecurityOptionCard(
                icon = { Icon(Icons.Filled.LockOpen, null, modifier = Modifier.size(32.dp)) },
                title = "Passphrase Only",
                description = "Type your master passphrase every time you reveal a PIN. No extra setup.",
                onClick = {
                    VaultsStorage.saveTier(context, TIER_PASSPHRASE_ONLY)
                    VaultsStorage.saveEncryptedPassphrase(context, passphrase)
                    VaultsStorage.saveSetupStep(context, VaultsStorage.STEP_COMPLETED)
                    navController.navigate("main") {
                        popUpTo("onboarding") { inclusive = true }
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Option 2 — Biometric
            SecurityOptionCard(
                icon = { Icon(Icons.Filled.Fingerprint, null, modifier = Modifier.size(32.dp)) },
                title = "Biometric (Recommended)",
                description = "Use fingerprint or face unlock. Your passphrase is stored securely on device.",
                onClick = {
                    VaultsStorage.saveTier(context, TIER_BIOMETRIC)
                    VaultsStorage.saveEncryptedPassphrase(context, passphrase)
                    VaultsStorage.saveSetupStep(context, VaultsStorage.STEP_COMPLETED)
                    navController.navigate("main") {
                        popUpTo("onboarding") { inclusive = true }
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Option 3 — TOTP
            SecurityOptionCard(
                icon = { Icon(Icons.Filled.Shield, null, modifier = Modifier.size(32.dp)) },
                title = "TOTP Authenticator",
                description = "Use a time-based one-time code from Google Authenticator before each PIN reveal.",
                onClick = {
                    VaultsStorage.saveTier(context, TIER_TOTP)
                    VaultsStorage.saveEncryptedPassphrase(context, passphrase)
                    val totpSecret = VaultsStorage.loadTotpSecret(context) ?: ""
                    navController.navigate("totp_setup/$totpSecret") {
                        popUpTo("onboarding") { inclusive = true }
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Option 4 — TOTP + Biometric
            SecurityOptionCard(
                icon = { Icon(Icons.Filled.Lock, null, modifier = Modifier.size(32.dp)) },
                title = "TOTP + Biometric",
                description = "Strongest option. Requires both an authenticator code and your fingerprint.",
                onClick = {
                    VaultsStorage.saveTier(context, TIER_TOTP_BIOMETRIC)
                    VaultsStorage.saveEncryptedPassphrase(context, passphrase)
                    val totpSecret = VaultsStorage.loadTotpSecret(context) ?: ""
                    navController.navigate("totp_setup/$totpSecret") {
                        popUpTo("onboarding") { inclusive = true }
                    }
                }
            )
        }
    }
}


@Composable
fun SecurityOptionCard(
    icon: @Composable () -> Unit,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick
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