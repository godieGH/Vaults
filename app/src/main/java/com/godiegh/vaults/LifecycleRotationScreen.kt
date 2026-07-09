package com.godiegh.vaults

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import uniffi.vaults.ffiDeriveMasterKey
import uniffi.vaults.ffiDerivePin
import uniffi.vaults.ffiGenerateSalt
import uniffi.vaults.ffiDerivePassphraseFingerprint

private enum class RotationStep {
    AUTHENTICATE,
    WARNING,
    NEW_PASSPHRASE,
    ROTATE_SERVICES,
    COMPLETE
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LifecycleRotationScreen(navController: NavController) {
    val context = LocalContext.current
    val tier = remember { VaultsStorage.loadTier(context) }
    val activity = context as? androidx.fragment.app.FragmentActivity
    val lifecycleOwner = LocalLifecycleOwner.current

    val services = remember { VaultsStorage.loadServices(context) }

    // ── UI STATE THAT SURVIVES PROCESS DEATH ───────────────────────────────────
    var currentStep by rememberSaveable { mutableStateOf(RotationStep.AUTHENTICATE) }
    var isLocked by rememberSaveable { mutableStateOf(false) }
    var doneStatesList by rememberSaveable { mutableStateOf(List(services.size) { false }) }

    // ── SENSITIVE CRYPTOGRAPHIC STATE (RAM ONLY) ───────────────────────────────
    var currentPassphrase by remember { mutableStateOf("") }
    var newPassphrase by remember { mutableStateOf("") }
    var confirmPassphrase by remember { mutableStateOf("") }
    var oldMasterKey by remember { mutableStateOf(byteArrayOf()) }
    var newMasterKey by remember { mutableStateOf(byteArrayOf()) }
    var newSalt by remember { mutableStateOf(byteArrayOf()) }

    // ── TRANSIENT UI STATE ─────────────────────────────────────────────────────
    var errorMessage by remember { mutableStateOf("") }
    var newPassphraseVisible by remember { mutableStateOf(false) }
    var confirmPassphraseVisible by remember { mutableStateOf(false) }
    var newPassphraseError by remember { mutableStateOf("") }
    var unlockPassphrase by remember { mutableStateOf("") }
    var unlockError by remember { mutableStateOf("") }

    val expandedStates = remember { mutableStateListOf(*Array(services.size) { false }) }
    val allDone = doneStatesList.all { it }
    val doneCount = doneStatesList.count { it }

    // ── PROCESS DEATH DETECTION & SECURITY BOUNDARY ────────────────────────────
    // If the OS killed the process and restored the screen mid-way, the cryptographic keys
    // in RAM are missing. We reset state back to step 1 to maintain database safety.
    // COMPLETE is excluded: by that point the migration is already committed and the keys
    // are intentionally cleared, so an empty oldMasterKey there is expected, not a crash signal.
    LaunchedEffect(currentStep, oldMasterKey) {
        if (currentStep != RotationStep.AUTHENTICATE && currentStep != RotationStep.COMPLETE && oldMasterKey.isEmpty()) {
            doneStatesList = List(services.size) { false }
            currentStep = RotationStep.AUTHENTICATE
            isLocked = false
        }
    }

    // ── BACKGROUND AUTOMATIC LOCK ──────────────────────────────────────────────
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                // Lock screen if app is minimized during active credential flows
                if (currentStep == RotationStep.ROTATE_SERVICES ||
                    currentStep == RotationStep.WARNING ||
                    currentStep == RotationStep.NEW_PASSPHRASE) {
                    isLocked = true
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun strength(pass: String): Float {
        var score = 0
        if (pass.length >= 8) score++
        if (pass.length >= 12) score++
        if (pass.any { it.isDigit() }) score++
        if (pass.any { !it.isLetterOrDigit() }) score++
        if (pass.any { it.isUpperCase() } && pass.any { it.isLowerCase() }) score++
        return score / 5f
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Lifecycle Rotation") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            when (currentStep) {
                // ── STEP 1: AUTHENTICATE ─────────────────────────────────────
                RotationStep.AUTHENTICATE -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Verify Identity", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Confirm your current master passphrase to begin the lifecycle rotation process.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                            Spacer(modifier = Modifier.height(32.dp))

                            if (tier == VaultsStorage.TIER_BIOMETRIC || tier == VaultsStorage.TIER_TOTP_BIOMETRIC) {
                                if (errorMessage.isNotEmpty()) {
                                    Text(errorMessage, color = MaterialTheme.colorScheme.error)
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                                Button(
                                    onClick = {
                                        if (activity == null) return@Button
                                        BiometricAuthenticator.authenticate(activity) { result ->
                                            when (result) {
                                                is BiometricAuthenticator.Result.Success -> {
                                                    currentPassphrase = VaultsStorage.loadEncryptedPassphrase(context) ?: ""
                                                    val salt = VaultsStorage.loadSalt(context) ?: byteArrayOf()
                                                    oldMasterKey = ffiDeriveMasterKey(currentPassphrase, salt)
                                                    currentStep = RotationStep.WARNING
                                                    errorMessage = ""
                                                }
                                                is BiometricAuthenticator.Result.Error -> errorMessage = result.message
                                                is BiometricAuthenticator.Result.Cancelled -> {}
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Authenticate with Biometrics") }
                            } else {
                                OutlinedTextField(
                                    value = currentPassphrase,
                                    onValueChange = { currentPassphrase = it; errorMessage = "" },
                                    label = { Text("Current Master Passphrase") },
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
                                    onClick = {
                                        val salt = VaultsStorage.loadSalt(context) ?: byteArrayOf()
                                        val storedFingerprint = VaultsStorage.loadFingerprint(context)
                                        val inputFingerprint = ffiDerivePassphraseFingerprint(currentPassphrase, salt)
                                        if (storedFingerprint != null && inputFingerprint != storedFingerprint) {
                                            errorMessage = "Incorrect passphrase."
                                        } else {
                                            oldMasterKey = ffiDeriveMasterKey(currentPassphrase, salt)
                                            currentStep = RotationStep.WARNING
                                            errorMessage = ""
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp)
                                ) { Text("Continue") }
                            }
                        }
                    }
                }

                // ── STEP 2: WARNING ──────────────────────────────────────────
                RotationStep.WARNING -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(88.dp)
                                    .background(MaterialTheme.colorScheme.errorContainer, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(44.dp))
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                            Text("Passphrase Compromised?", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("This will guide you through changing every PIN linked to this vault. You will first create your new master key, and then we will provide you with both your Current PINs and New PINs side-by-side to authorize the updates.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                            Spacer(modifier = Modifier.height(16.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                                    Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp).padding(top = 2.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("The old salt is only destroyed after you confirm all PIN changes are done. Until then, your vault remains intact.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                                }
                            }
                            Spacer(modifier = Modifier.height(32.dp))
                            Button(
                                onClick = { currentStep = RotationStep.NEW_PASSPHRASE },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) { Text("Begin Rotation") }
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { navController.popBackStack() },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp)
                            ) { Text("Cancel") }
                        }
                    }
                }

                // ── STEP 3: NEW PASSPHRASE ───────────────────────────────────
                RotationStep.NEW_PASSPHRASE -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Filled.Key, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Set Your New Passphrase", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Generate the future lifecycle now. This computes the exact new PINs you'll need to set at your banks in the next step.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                            Spacer(modifier = Modifier.height(32.dp))

                            OutlinedTextField(
                                value = newPassphrase,
                                onValueChange = { newPassphrase = it; newPassphraseError = "" },
                                label = { Text("New Passphrase") },
                                visualTransformation = if (newPassphraseVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    IconButton(onClick = { newPassphraseVisible = !newPassphraseVisible }) {
                                        Icon(if (newPassphraseVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff, contentDescription = null)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp)
                            )

                            if (newPassphrase.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                val s = strength(newPassphrase)
                                LinearProgressIndicator(
                                    progress = { s },
                                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(50)),
                                    color = when {
                                        s < 0.4f -> MaterialTheme.colorScheme.error
                                        s < 0.7f -> Color(0xFFF9A825)
                                        else -> Color(0xFF2E7D32)
                                    },
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            OutlinedTextField(
                                value = confirmPassphrase,
                                onValueChange = { confirmPassphrase = it; newPassphraseError = "" },
                                label = { Text("Confirm New Passphrase") },
                                visualTransformation = if (confirmPassphraseVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                trailingIcon = {
                                    if (confirmPassphrase.isNotEmpty()) {
                                        Icon(
                                            if (confirmPassphrase == newPassphrase) Icons.Filled.CheckCircle else Icons.Filled.Cancel,
                                            contentDescription = null,
                                            tint = if (confirmPassphrase == newPassphrase) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                                        )
                                    } else {
                                        IconButton(onClick = { confirmPassphraseVisible = !confirmPassphraseVisible }) {
                                            Icon(
                                                if (confirmPassphraseVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                                contentDescription = null
                                            )
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp)
                            )

                            if (newPassphraseError.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(newPassphraseError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }

                            Spacer(modifier = Modifier.height(32.dp))

                            Button(
                                onClick = {
                                    when {
                                        newPassphrase.length < 8 -> newPassphraseError = "Passphrase must be at least 8 characters"
                                        newPassphrase != confirmPassphrase -> newPassphraseError = "Passphrases do not match"
                                        newPassphrase == currentPassphrase -> newPassphraseError = "New passphrase must be different from the current one"
                                        else -> {
                                            newSalt = ffiGenerateSalt()
                                            newMasterKey = ffiDeriveMasterKey(newPassphrase, newSalt)
                                            currentStep = RotationStep.ROTATE_SERVICES
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                shape = RoundedCornerShape(14.dp)
                            ) { Text("Generate Migration Checklist", style = MaterialTheme.typography.titleMedium) }
                        }
                    }
                }

                // ── STEP 4: ROTATE SERVICES ──────────────────────────────────
                RotationStep.ROTATE_SERVICES -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text("Change PINs at Each Service", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Text("$doneCount / ${services.size}", style = MaterialTheme.typography.labelLarge, color = if (allDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { if (services.isEmpty()) 0f else doneCount.toFloat() / services.size },
                                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                )
                            }
                        }

                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            itemsIndexed(services) { index, service ->
                                val isDone = doneStatesList.getOrElse(index) { false }
                                val isExpanded = expandedStates[index]

                                val contextStr = "v1|${service.countryCode.lowercase()}|${service.name.lowercase()}|${service.identifier}|${service.pinLength}|${service.rotation}"

                                val currentPin = remember(oldMasterKey, contextStr) {
                                    if (oldMasterKey.isNotEmpty()) ffiDerivePin(oldMasterKey, contextStr, service.pinLength.toUInt()) else ""
                                }
                                val targetPin = remember(newMasterKey, contextStr) {
                                    if (newMasterKey.isNotEmpty()) ffiDerivePin(newMasterKey, contextStr, service.pinLength.toUInt()) else ""
                                }

                                ServiceRotationNode(
                                    index = index + 1,
                                    total = services.size,
                                    service = service,
                                    oldPin = currentPin,
                                    newPin = targetPin,
                                    isDone = isDone,
                                    isExpanded = isExpanded,
                                    onToggleExpand = { if (!isDone) expandedStates[index] = !expandedStates[index] },
                                    onMarkDone = {
                                        doneStatesList = doneStatesList.toMutableList().apply { set(index, true) }
                                        expandedStates[index] = false
                                        val next = doneStatesList.indexOfFirst { !it }
                                        if (next != -1) expandedStates[next] = true
                                    }
                                )
                            }
                        }

                        Surface(shadowElevation = 8.dp, modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                if (!allDone) {
                                    Text("Mark all services done to commit changes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                                Button(
                                    onClick = {
                                        val newFingerprint = ffiDerivePassphraseFingerprint(newPassphrase, newSalt)
                                        VaultsStorage.saveSalt(context, newSalt)
                                        VaultsStorage.saveEncryptedPassphrase(context, newPassphrase)
                                        VaultsStorage.saveFingerprint(context, newFingerprint)
                                        VaultsStorage.markAutoSyncDirty(context)

                                        oldMasterKey = byteArrayOf()
                                        newMasterKey = byteArrayOf()
                                        currentStep = RotationStep.COMPLETE
                                    },
                                    enabled = allDone || services.isEmpty(),
                                    modifier = Modifier.fillMaxWidth().height(52.dp),
                                    shape = RoundedCornerShape(14.dp)
                                ) { Text("Confirm Migration & Lock Vault", style = MaterialTheme.typography.titleMedium) }
                            }
                        }
                    }
                }

                // ── STEP 5: COMPLETE ─────────────────────────────────────────
                RotationStep.COMPLETE -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(96.dp)
                                    .background(Color(0xFF2E7D32).copy(alpha = 0.12f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(52.dp))
                            }
                            Spacer(modifier = Modifier.height(24.dp))
                            Text("New Lifecycle Active", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Your vault has been rotated successfully. All ${services.size} service${if (services.size != 1) "s" else ""} now derive new PINs from your new passphrase and salt. The old vault lifecycle is gone.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                            Spacer(modifier = Modifier.height(12.dp))
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Tip: Create a fresh backup now to preserve your new vault state.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                            Spacer(modifier = Modifier.height(32.dp))
                            Button(
                                onClick = {
                                    navController.navigate("main") {
                                        popUpTo(0) { inclusive = true }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                shape = RoundedCornerShape(14.dp)
                            ) { Text("Back to Vaults", style = MaterialTheme.typography.titleMedium) }
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = { navController.navigate("reauth_for_backup") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp)
                            ) { Text("Backup Now") }
                        }
                    }
                }
            }

            // ── RE-AUTHENTICATION SECURITY OVERLAY ───────────────────────────────────
            if (isLocked) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Filled.Security, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Session Locked", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("For your security, the rotation checklist was hidden because the app was minimized.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(32.dp))

                        if (tier == VaultsStorage.TIER_BIOMETRIC || tier == VaultsStorage.TIER_TOTP_BIOMETRIC) {
                            if (unlockError.isNotEmpty()) {
                                Text(unlockError, color = MaterialTheme.colorScheme.error)
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            Button(
                                onClick = {
                                    if (activity == null) return@Button
                                    BiometricAuthenticator.authenticate(activity) { result ->
                                        when (result) {
                                            is BiometricAuthenticator.Result.Success -> {
                                                isLocked = false
                                                unlockError = ""
                                            }
                                            is BiometricAuthenticator.Result.Error -> unlockError = result.message
                                            is BiometricAuthenticator.Result.Cancelled -> {}
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Unlock with Biometrics") }
                        } else {
                            OutlinedTextField(
                                value = unlockPassphrase,
                                onValueChange = { unlockPassphrase = it; unlockError = "" },
                                label = { Text("Current Master Passphrase") },
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(14.dp)
                            )
                            if (unlockError.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(unlockError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    if (unlockPassphrase == currentPassphrase) {
                                        isLocked = false
                                        unlockPassphrase = ""
                                        unlockError = ""
                                    } else {
                                        unlockError = "Incorrect passphrase."
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp)
                            ) { Text("Unlock Checklist") }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        TextButton(
                            onClick = { navController.popBackStack() },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) { Text("Abort Migration") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ServiceRotationNode(
    index: Int,
    total: Int,
    service: ServiceConfig,
    oldPin: String,
    newPin: String,
    isDone: Boolean,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onMarkDone: () -> Unit
) {
    val containerColor = when {
        isDone -> Color(0xFF2E7D32).copy(alpha = 0.08f)
        isExpanded -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val borderColor = when {
        isDone -> Color(0xFF2E7D32).copy(alpha = 0.4f)
        isExpanded -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        else -> Color.Transparent
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(if (isDone) Color(0xFF2E7D32) else MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (isDone) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    } else {
                        Text("$index", color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(service.displayName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(service.identifier, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                if (isDone) {
                    Text("Done", style = MaterialTheme.typography.labelMedium, color = Color(0xFF2E7D32))
                } else {
                    IconButton(onClick = onToggleExpand, modifier = Modifier.size(32.dp)) {
                        Icon(if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            AnimatedVisibility(visible = isExpanded && !isDone, enter = expandVertically(), exit = shrinkVertically()) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Current PIN", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(oldPin.map { "$it" }.joinToString("  "), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, letterSpacing = 4.sp, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(16.dp))

                    Text("New PIN", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(newPin.map { "$it" }.joinToString("  "), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, letterSpacing = 4.sp, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(16.dp))

                    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("What to do:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("1. Open the ${service.displayName} app, USSD code, or visit a branch.\n2. Change your PIN from the Current PIN shown to the New PIN.\n3. Once done, tap Mark as Done below.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onMarkDone, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Mark as Done")
                    }
                }
            }
        }
    }
}