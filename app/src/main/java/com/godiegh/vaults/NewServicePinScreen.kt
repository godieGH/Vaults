package com.godiegh.vaults

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.godiegh.vaults.VaultsStorage.TIER_BIOMETRIC
import com.godiegh.vaults.VaultsStorage.TIER_PASSPHRASE_ONLY
import com.godiegh.vaults.VaultsStorage.TIER_TOTP_BIOMETRIC
import uniffi.vaults.ffiDeriveMasterKey
import uniffi.vaults.ffiDerivePin
import uniffi.vaults.ffiVerifyTotp

/** Stashes a service constructed by AddServiceScreen until its PIN is shown and confirmed. */
object PendingServiceHolder {
    var pending: ServiceConfig? = null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewServicePinScreen(navController: NavController) {
    val context = LocalContext.current
    val service = PendingServiceHolder.pending

    if (service == null) {
        LaunchedEffect(Unit) { navController.popBackStack() }
        return
    }

    val tier = remember { VaultsStorage.loadTier(context) }
    val activity = context as? androidx.fragment.app.FragmentActivity
    val lifecycleOwner = LocalLifecycleOwner.current

    var totpCode by remember { mutableStateOf("") }
    var passphrase by remember { mutableStateOf("") }
    var isTotpVerified by remember { mutableStateOf(tier == TIER_PASSPHRASE_ONLY || tier == TIER_BIOMETRIC) }
    var errorMessage by remember { mutableStateOf("") }
    var generatedPin by remember { mutableStateOf<String?>(null) }
    var serviceSaved by remember { mutableStateOf(false) }

    // ── RE-AUTH ON BACKGROUND ────────────────────────────────────────────────
    // Wipe the revealed PIN and auth progress if the app is minimized before the
    // service is confirmed, so coming back forces re-auth instead of leaving a
    // live PIN sitting on screen.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && !serviceSaved) {
                generatedPin = null
                passphrase = ""
                totpCode = ""
                errorMessage = ""
                isTotpVerified = (tier == TIER_PASSPHRASE_ONLY || tier == TIER_BIOMETRIC)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun derivePin(rawPassphrase: String) {
        val storedSalt = VaultsStorage.loadSalt(context) ?: byteArrayOf()
        val masterKey = ffiDeriveMasterKey(rawPassphrase, storedSalt)
        val contextStr = "v1|${service.countryCode.lowercase()}|${service.name.lowercase()}|${service.identifier}|${service.pinLength}|${service.rotation}"
        generatedPin = ffiDerivePin(masterKey, contextStr, service.pinLength.toUInt())
    }

    fun confirmAndSave() {
        val currentServices = VaultsStorage.loadServices(context).toMutableList()
        currentServices.add(service)
        VaultsStorage.saveServices(context, currentServices)
        VaultsStorage.markAutoSyncDirty(context)
        PendingServiceHolder.pending = null
        serviceSaved = true
        navController.navigate("main") {
            popUpTo(0) { inclusive = true }
        }
    }

    fun cancelAndDiscard() {
        PendingServiceHolder.pending = null
        navController.popBackStack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Set Up New PIN") },
                navigationIcon = {
                    IconButton(onClick = { cancelAndDiscard() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when {
                !isTotpVerified -> {
                    Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Two-Factor Authentication", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Enter your 6-digit authenticator code to continue.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(24.dp))
                    OutlinedTextField(
                        value = totpCode,
                        onValueChange = { totpCode = it; errorMessage = "" },
                        label = { Text("TOTP Code") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp)
                    )
                    if (errorMessage.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
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
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("Verify") }
                }

                generatedPin == null -> {
                    Icon(Icons.Filled.Key, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Generate PIN for ${service.displayName}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Authenticate to derive this service's PIN from your vault.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(24.dp))

                    if (tier == TIER_BIOMETRIC || tier == TIER_TOTP_BIOMETRIC) {
                        if (errorMessage.isNotEmpty()) {
                            Text(errorMessage, color = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        Button(
                            onClick = {
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

                                            derivePin(storedPassphrase)
                                            errorMessage = ""
                                        }
                                        is BiometricAuthenticator.Result.Error -> errorMessage = result.message
                                        is BiometricAuthenticator.Result.Cancelled -> {}
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) { Text("Unlock with Biometrics") }
                    } else {
                        OutlinedTextField(
                            value = passphrase,
                            onValueChange = { passphrase = it; errorMessage = "" },
                            label = { Text("Master Passphrase") },
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp)
                        )
                        if (errorMessage.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(errorMessage, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { derivePin(passphrase) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp)
                        ) { Text("Continue") }
                    }
                }

                else -> {
                    Box(
                        modifier = Modifier.size(72.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Text("Your New PIN", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "${service.displayName} • ${service.identifier}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        generatedPin!!.map { "$it" }.joinToString("  "),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp).padding(top = 2.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Go set this exact PIN with ${service.displayName} now — via their app, USSD code, or a branch visit. Only confirm below once it's actually set there, since Vaults will start treating this as the correct PIN immediately.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { confirmAndSave() },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("I've Set This PIN — Save Service", style = MaterialTheme.typography.titleMedium) }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { cancelAndDiscard() },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) { Text("Cancel") }
                }
            }
        }
    }
}