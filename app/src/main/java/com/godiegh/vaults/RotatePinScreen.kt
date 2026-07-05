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
import com.godiegh.vaults.VaultsStorage.TIER_BIOMETRIC
import com.godiegh.vaults.VaultsStorage.TIER_PASSPHRASE_ONLY
import com.godiegh.vaults.VaultsStorage.TIER_TOTP_BIOMETRIC
import uniffi.vaults.ffiDeriveMasterKey
import uniffi.vaults.ffiDerivePin
import uniffi.vaults.ffiVerifyTotp

@Composable
fun RotatePinScreen(
    service: ServiceConfig,
    navController: NavController
) {
    val context = LocalContext.current
    val tier = remember { VaultsStorage.loadTier(context) }
    val activity = context as? androidx.fragment.app.FragmentActivity

    var totpCode by remember { mutableStateOf("") }
    var passphrase by remember { mutableStateOf("") }
    var isTotpVerified by remember { mutableStateOf(tier == TIER_PASSPHRASE_ONLY || tier == TIER_BIOMETRIC) }
    var errorMessage by remember { mutableStateOf("") }

    var oldPin by remember { mutableStateOf<String?>(null) }
    var newPin by remember { mutableStateOf<String?>(null) }
    var rotationApplied by remember { mutableStateOf(false) }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP && !rotationApplied) {
                oldPin = null
                newPin = null
                passphrase = ""
                totpCode = ""
                errorMessage = ""
                isTotpVerified = (tier == TIER_PASSPHRASE_ONLY || tier == TIER_BIOMETRIC)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun computeBothPins(rawPassphrase: String) {
        val storedSalt = VaultsStorage.loadSalt(context) ?: byteArrayOf()
        val masterKey = ffiDeriveMasterKey(rawPassphrase, storedSalt)
        val base = "v1|${service.countryCode.lowercase()}|${service.name.lowercase()}|${service.identifier}|${service.pinLength}"
        oldPin = ffiDerivePin(masterKey, "$base|${service.rotation}", service.pinLength.toUInt())
        newPin = ffiDerivePin(masterKey, "$base|${service.rotation + 1}", service.pinLength.toUInt())
    }

    fun commitRotation() {
        val services = VaultsStorage.loadServices(context).toMutableList()
        val index = services.indexOfFirst { it.id == service.id }
        if (index != -1) {
            services[index] = services[index].copy(rotation = service.rotation + 1)
            VaultsStorage.saveServices(context, services)
        }
        rotationApplied = true
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
                rotationApplied -> {
                    Text("PIN Rotated", style = MaterialTheme.typography.headlineMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Make sure you've already updated this PIN with ${service.name.replaceFirstChar { it.uppercase() }} before relying on it here.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Done") }
                }

                !isTotpVerified -> {
                    Text("Two-Factor Authentication", style = MaterialTheme.typography.headlineMedium)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Enter your 6-digit authenticator code to continue.", style = MaterialTheme.typography.bodyMedium)
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
                    ) { Text("Verify") }
                }

                oldPin == null -> {
                    Text("Rotate PIN for ${service.name.replaceFirstChar { it.uppercase() }}", style = MaterialTheme.typography.headlineMedium)
                    Spacer(modifier = Modifier.height(16.dp))

                    if (tier == TIER_BIOMETRIC || tier == TIER_TOTP_BIOMETRIC) {
                        Text("Authenticate to continue", style = MaterialTheme.typography.bodyMedium)
                        if (errorMessage.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(errorMessage, color = MaterialTheme.colorScheme.error)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                if (activity == null) {
                                    errorMessage = "Unable to launch biometric prompt"
                                    return@Button
                                }
                                BiometricAuthenticator.authenticate(activity) { result ->
                                    when (result) {
                                        is BiometricAuthenticator.Result.Success -> {
                                            val storedPassphrase = VaultsStorage.loadEncryptedPassphrase(context) ?: ""
                                            computeBothPins(storedPassphrase)
                                            errorMessage = ""
                                        }
                                        is BiometricAuthenticator.Result.Error -> errorMessage = result.message
                                        is BiometricAuthenticator.Result.Cancelled -> { /* no-op */ }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Unlock with Biometrics") }
                    } else {
                        OutlinedTextField(
                            value = passphrase,
                            onValueChange = { passphrase = it },
                            label = { Text("Master Passphrase") },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { computeBothPins(passphrase) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Continue") }
                    }
                }

                else -> {
                    Text("Confirm PIN Rotation", style = MaterialTheme.typography.headlineMedium)
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Current PIN", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(oldPin ?: "", style = MaterialTheme.typography.displaySmall, letterSpacing = 6.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("New PIN", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(newPin ?: "", style = MaterialTheme.typography.displaySmall, letterSpacing = 6.sp, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        "Go set this new PIN with ${service.name.replaceFirstChar { it.uppercase() }} first (via their app, USSD, or branch). Only confirm once you've actually changed it there — until then, Vaults will keep showing the current PIN as correct.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { commitRotation() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("I've Updated It — Confirm Rotation") }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Cancel") }
                }
            }
        }
    }
}