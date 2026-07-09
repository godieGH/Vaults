package com.godiegh.vaults

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import android.content.Context
import android.content.ContextWrapper
import androidx.fragment.app.FragmentActivity
import com.godiegh.vaults.VaultsStorage.TIER_BIOMETRIC
import com.godiegh.vaults.VaultsStorage.TIER_PASSPHRASE_ONLY
import com.godiegh.vaults.VaultsStorage.TIER_TOTP
import com.godiegh.vaults.VaultsStorage.TIER_TOTP_BIOMETRIC
import uniffi.vaults.ffiVerifyTotp

@Composable
fun ReAuthScreen(
    navController: NavController
) {
    val context = LocalContext.current
    val tier = remember { VaultsStorage.loadTier(context) }

    var totpCode by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var confirmPassphrase by remember { mutableStateOf("") }

    // If tier is Passphrase Only, they just need to re-enter it to prove it's them.
    val requiresTotp = tier == TIER_TOTP || tier == TIER_TOTP_BIOMETRIC
    val requiresBiometric = tier == TIER_BIOMETRIC || tier == TIER_TOTP_BIOMETRIC

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
                "Verify to Continue",
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(16.dp))

            when {
                requiresTotp -> {
                    Text(
                        "Enter your 6-digit authenticator code.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    OutlinedTextField(
                        value = totpCode,
                        onValueChange = { totpCode = it; errorMessage = "" },
                        label = { Text("TOTP Code") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    if (errorMessage.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(errorMessage, color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            val storedSecret = VaultsStorage.loadTotpSecret(context) ?: ""
                            if (ffiVerifyTotp(storedSecret, totpCode)) {
                                navController.navigate("2fa_setup") {
                                    popUpTo("reauth_for_2fa") { inclusive = true }
                                }
                            } else {
                                errorMessage = "Invalid code. Try again."
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Verify")
                    }
                }

                requiresBiometric -> {
                    Text(
                        "Authenticate to change security settings.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    if (errorMessage.isNotEmpty()) {
                        Text(errorMessage, color = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Button(
                        onClick = {
                            var act: Context? = context
                            while (act is ContextWrapper) {
                                if (act is FragmentActivity) break
                                act = act.baseContext
                            }
                            val activity = act as? androidx.fragment.app.FragmentActivity

                            if (activity == null) {
                                errorMessage = "Unable to launch biometric prompt"
                                return@Button
                            }
                            BiometricAuthenticator.authenticate(activity) { result ->
                                when (result) {
                                    is BiometricAuthenticator.Result.Success -> {
                                        navController.navigate("2fa_setup") {
                                            popUpTo("reauth_for_2fa") { inclusive = true }
                                        }
                                    }
                                    is BiometricAuthenticator.Result.Error -> errorMessage = result.message
                                    is BiometricAuthenticator.Result.Cancelled -> { /* no-op */ }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Unlock with Biometrics")
                    }
                }

                else -> {
                    // TIER_PASSPHRASE_ONLY scenario
                    Text(
                        "Enter your master passphrase to continue.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    OutlinedTextField(
                        value = confirmPassphrase,
                        onValueChange = { confirmPassphrase = it; errorMessage = "" },
                        label = { Text("Master Passphrase") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    if (errorMessage.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(errorMessage, color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            val storedSalt = VaultsStorage.loadSalt(context) ?: byteArrayOf()
                            val storedFingerprint = VaultsStorage.loadFingerprint(context)

                            val inputFingerprint = uniffi.vaults.ffiDerivePassphraseFingerprint(confirmPassphrase, storedSalt)

                            if (storedFingerprint != null && inputFingerprint == storedFingerprint) {
                                navController.navigate("2fa_setup") {
                                    popUpTo("reauth_for_2fa") { inclusive = true }
                                }
                            } else {
                                errorMessage = "Incorrect passphrase."
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Verify")
                    }
                }
            }
        }
    }
}