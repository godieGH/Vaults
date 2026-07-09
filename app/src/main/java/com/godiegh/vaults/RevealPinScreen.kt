package com.godiegh.vaults

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import android.content.Context
import android.content.ContextWrapper
import androidx.fragment.app.FragmentActivity
import com.godiegh.vaults.VaultsStorage.TIER_BIOMETRIC
import com.godiegh.vaults.VaultsStorage.TIER_PASSPHRASE_ONLY
import com.godiegh.vaults.VaultsStorage.TIER_TOTP_BIOMETRIC
import uniffi.vaults.ffiDeriveMasterKey
import uniffi.vaults.ffiDerivePin
import uniffi.vaults.ffiVerifyTotp

@Composable
fun RevealPinScreen(
    service: ServiceConfig,
    navController: NavController
) {
    val context = LocalContext.current
    val tier = remember { VaultsStorage.loadTier(context) }

    var totpCode by remember { mutableStateOf("") }
    var passphrase by remember { mutableStateOf("") }
    var isTotpVerified by remember { mutableStateOf(tier == TIER_PASSPHRASE_ONLY || tier == TIER_BIOMETRIC) }
    var derivedPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var secondsRemaining by remember { mutableStateOf(10) }


    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                derivedPin = ""
                passphrase = ""
                totpCode = ""
                errorMessage = ""
                secondsRemaining = 10
                isTotpVerified = (tier == TIER_PASSPHRASE_ONLY || tier == TIER_BIOMETRIC)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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
            when {
                // STEP 1: TOTP gate — only for TOTP tiers
                !isTotpVerified -> {
                    Text(
                        "Two-Factor Authentication",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Enter your 6-digit authenticator code to continue.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    OutlinedTextField(
                        value = totpCode,
                        onValueChange = { totpCode = it },
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
                                isTotpVerified = true
                                errorMessage = ""
                            } else {
                                errorMessage = "Invalid code. Try again."
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Verify")
                    }
                }

                // STEP 2: Passphrase or Biometric
                derivedPin.isEmpty() -> {
                    Text(
                        "Unlock ${service.name.replaceFirstChar { it.uppercase() }}",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    if (tier == TIER_BIOMETRIC || tier == TIER_TOTP_BIOMETRIC) {
                        Text(
                            "Authenticate to reveal your PIN",
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
                                val activity = act as? FragmentActivity

                                if (activity == null) {
                                    errorMessage = "Unable to launch biometric prompt"
                                    return@Button
                                }

                                val decryptionCipher = BiometricAuthenticator.getDecryptionCipher(context)
                                if (decryptionCipher == null) {
                                    errorMessage = "Biometric storage key not initialized."
                                    return@Button
                                }

                                BiometricAuthenticator.authenticate(activity, cryptoCipher = decryptionCipher) { result ->
                                    when (result) {
                                        is BiometricAuthenticator.Result.Success -> {
                                            val authCipher = result.authenticatedCipher
                                            val storedPassphrase = if (authCipher != null) {
                                                BiometricAuthenticator.decryptPassphrase(context, authCipher) ?: ""
                                            } else ""

                                            if (storedPassphrase.isEmpty()) {
                                                errorMessage = "Failed to unlock storage hardware securely."
                                                return@authenticate
                                            }

                                            val storedSalt = VaultsStorage.loadSalt(context) ?: byteArrayOf()
                                            val masterKey = ffiDeriveMasterKey(storedPassphrase, storedSalt)
                                            val formattedContext =
                                                "v1|${service.countryCode.lowercase()}|${service.name.lowercase()}|${service.identifier}|${service.pinLength}|${service.rotation}"
                                            derivedPin = ffiDerivePin(
                                                masterKey,
                                                formattedContext,
                                                service.pinLength.toUInt()
                                            )
                                            errorMessage = ""
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
                    } else {
                        OutlinedTextField(
                            value = passphrase,
                            onValueChange = { passphrase = it },
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
                        // Inside RevealPinScreen -> Button onClick (Passphrase entry block)
                        Button(
                            onClick = {
                                val storedSalt = VaultsStorage.loadSalt(context) ?: byteArrayOf()
                                val storedFingerprint = VaultsStorage.loadFingerprint(context)

                                // NEW: Check fingerprint before deriving key
                                val inputFingerprint = uniffi.vaults.ffiDerivePassphraseFingerprint(passphrase, storedSalt)

                                if (storedFingerprint != null && inputFingerprint != storedFingerprint) {
                                    errorMessage = "Incorrect master passphrase."
                                    return@Button
                                }

                                // If it matches, proceed as normal
                                errorMessage = ""
                                val masterKey = ffiDeriveMasterKey(passphrase, storedSalt)
                                val formattedContext = "v1|${service.countryCode.lowercase()}|${service.name.lowercase()}|${service.identifier}|${service.pinLength}|${service.rotation}"
                                derivedPin = ffiDerivePin(
                                    masterKey,
                                    formattedContext,
                                    service.pinLength.toUInt()
                                )
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Derive PIN")
                        }
                    }
                }

                // STEP 3: PIN reveal with countdown
                else -> {
                    LaunchedEffect(Unit) {
                        while (secondsRemaining > 0) {
                            kotlinx.coroutines.delay(1000L)
                            secondsRemaining--
                        }
                        derivedPin = ""
                        passphrase = ""
                        navController.popBackStack()
                    }

                    Text(
                        "Your ${service.name.replaceFirstChar { it.uppercase() }} PIN",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = derivedPin,
                        style = MaterialTheme.typography.displayLarge,
                        letterSpacing = 8.sp
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        "Clearing in ${secondsRemaining}s",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}