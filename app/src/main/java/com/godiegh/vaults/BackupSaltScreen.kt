package com.godiegh.vaults

import android.app.Activity
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import android.util.Base64
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupSaltScreen(
    navController: NavController,
    vm: BackupRestoreViewModel = viewModel()
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var backupPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }

    val vmError by vm.errorMessage.collectAsState()
    var localErrorMessage by remember { mutableStateOf("") }
    val displayError = vmError.ifEmpty { localErrorMessage }

    // Guards ON_STOP wipe/navigate logic when WE are the ones causing the backgrounding
    // (file picker or share sheet), not the user leaving the app.
    var isPickerActive by remember { mutableStateOf(false) }
    var isShareActive by remember { mutableStateOf(false) }

    // State to hold the encrypted payload once generated
    var encryptedBackupPayload by remember { mutableStateOf<String?>(null) }
    var showRawText by remember { mutableStateOf(false) }

    // --- Auto Sync state ---
    // `autoSyncEnabled` mirrors the persisted truth (VaultsStorage). `showAutoSyncSetup`
    // is purely local UI state for "the setup panel is open but not confirmed yet".
    var autoSyncEnabled by remember { mutableStateOf(VaultsStorage.isAutoSyncEnabled(context)) }
    var showAutoSyncSetup by remember { mutableStateOf(false) }
    var driveConnectedEmail by remember { mutableStateOf(VaultsStorage.loadDriveAccountEmail(context)) }
    var autoSyncPassword by remember { mutableStateOf("") }
    var autoSyncConfirmPassword by remember { mutableStateOf("") }
    var autoSyncErrorMessage by remember { mutableStateOf("") }
    var isTestingConnection by remember { mutableStateOf(false) }
    var connectionTestSuccess by remember { mutableStateOf<Boolean?>(null) }

    fun resetAutoSyncSetupFields() {
        autoSyncPassword = ""
        autoSyncConfirmPassword = ""
        autoSyncErrorMessage = ""
    }

    fun disableAutoSync() {
        VaultsStorage.clearAutoSyncConfig(context)
        SyncWorker.cancelPeriodic(context)
        autoSyncEnabled = false
        showAutoSyncSetup = false
        driveConnectedEmail = null
        resetAutoSyncSetupFields()
    }

    // Runs after Drive access is granted (whether that happened silently or via
    // the account-picker PendingIntent): fetches a friendly email label off the
    // main thread and persists the connection so it survives leaving this screen.
    suspend fun completeDriveConnection(authResult: com.google.android.gms.auth.api.identity.AuthorizationResult) {
        val token = authResult.accessToken
        if (token == null) {
            autoSyncErrorMessage = "Google Drive didn't return an access token. Please try again."
            return
        }
        val email = withContext(Dispatchers.IO) {
            val drive = GoogleDriveAuth.buildDriveService(token)
            GoogleDriveAuth.fetchAccountEmail(drive)
        }
        driveConnectedEmail = email ?: "Google Drive connected"
        VaultsStorage.saveDriveAccountEmail(context, driveConnectedEmail!!)
    }

    val driveAuthorizationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val authResult = GoogleDriveAuth.resultFromIntent(context, result.data!!)
            coroutineScope.launch { completeDriveConnection(authResult) }
        } else {
            autoSyncErrorMessage = "Google Drive connection was cancelled."
        }
    }

    // Security Feature: Wipe state when backgrounded (but not for our own picker/share sheet)
    val lifecycleOwner = LocalLifecycleOwner.current
    var requireReauth by remember { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                if (isPickerActive || isShareActive) {
                    // Expected backgrounding caused by our own file picker or share sheet.
                    // Don't wipe state or force reauth.
                    return@LifecycleEventObserver
                }

                // 1. Mark that we need to re-authenticate when the app comes back
                requireReauth = true

                // 2. Instantly wipe the sensitive data from memory just in case
                encryptedBackupPayload = null
                backupPassword = ""
                confirmPassword = ""
                showRawText = false
                localErrorMessage = ""
                vm.setErrorMessage("")

                // Wipe any not-yet-confirmed auto-sync setup password too. If auto sync
                // is already enabled, leave that config alone — only the in-progress
                // setup fields are sensitive here.
                autoSyncPassword = ""
                autoSyncConfirmPassword = ""
                autoSyncErrorMessage = ""
                showAutoSyncSetup = false

            } else if (event == Lifecycle.Event.ON_RESUME) {
                // 3. When app is active again, the navigation system is unlocked.
                // We safely trigger the navigation here.
                if (requireReauth) {
                    requireReauth = false
                    navController.navigate("reauth_for_backup") {
                        // Remove the backup screen from history so they can't hit 'back' to bypass it
                        popUpTo("backup_salt") { inclusive = true }
                    }
                }
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Launcher for saving to device storage
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        isPickerActive = false
        uri?.let { destinationUri ->
            vm.saveBackupToFile(context, destinationUri, encryptedBackupPayload ?: "")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Backup Vault Salt") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (encryptedBackupPayload == null) {
                // STEP 1: Set Backup Password
                Text(
                    "Secure Your Backup",
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Set a strong backup password. You will need this password to restore your vault on a new device. If you lose this password, your backup is useless.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = backupPassword,
                    onValueChange = { backupPassword = it; localErrorMessage = ""; vm.setErrorMessage("") },
                    label = { Text("Backup Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it; localErrorMessage = ""; vm.setErrorMessage("") },
                    label = { Text("Confirm Backup Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (displayError.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(displayError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }

                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        if (backupPassword.length < 6) {
                            localErrorMessage = "Password must be at least 6 characters"
                        } else if (backupPassword != confirmPassword) {
                            localErrorMessage = "Passwords do not match"
                        } else {
                            // Old manual array building replaced with:
                            val backupPayload = VaultsStorage.exportVaultAsJson(context)
                            encryptedBackupPayload = encryptBackup(backupPayload, backupPassword)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Encrypt Backup")
                }

                Spacer(modifier = Modifier.height(32.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(24.dp))

                // --- Auto Sync section ---
                // This is independent of the manual export above: enabling it doesn't
                // create a backup right now, it schedules future backups to happen
                // automatically once the vault contents change.

                var showManageBackups by remember { mutableStateOf(false) }
                var cloudBackupsList by remember { mutableStateOf<List<com.google.api.services.drive.model.File>>(emptyList()) }
                var isLoadingBackupsList by remember { mutableStateOf(false) }
                var pendingDeleteFile by remember { mutableStateOf<com.google.api.services.drive.model.File?>(null) }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto Sync", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Automatically back up future changes to your connected cloud drive.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = autoSyncEnabled || showAutoSyncSetup,
                        onCheckedChange = { checked ->
                            if (checked) {
                                if (!autoSyncEnabled) {
                                    showAutoSyncSetup = true
                                }
                            } else {
                                if (autoSyncEnabled) {
                                    disableAutoSync()
                                } else {
                                    showAutoSyncSetup = false
                                    resetAutoSyncSetupFields()
                                }
                            }
                        }
                    )
                }

                // Setup panel: shown while configuring, before auto sync is actually enabled.
                AnimatedVisibility(
                    visible = showAutoSyncSetup && !autoSyncEnabled,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                        if (driveConnectedEmail == null) {
                            OutlinedButton(
                                onClick = {
                                    autoSyncErrorMessage = ""
                                    coroutineScope.launch {
                                        try {
                                            val authResult = GoogleDriveAuth.authorize(context)
                                            if (authResult.hasResolution()) {
                                                val pendingIntent = authResult.pendingIntent
                                                if (pendingIntent != null) {
                                                    driveAuthorizationLauncher.launch(
                                                        IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                                                    )
                                                }
                                            } else {
                                                completeDriveConnection(authResult)
                                            }
                                        } catch (e: Exception) {
                                            autoSyncErrorMessage = "Couldn't connect to Google Drive: ${e.localizedMessage}"
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Filled.Cloud, contentDescription = null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Connect Google Drive")
                            }
                        } else {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.CloudDone,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    driveConnectedEmail ?: "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = {
                                    val emailToRevoke = driveConnectedEmail
                                    driveConnectedEmail = null
                                    VaultsStorage.clearDriveAccountEmail(context)
                                    if (emailToRevoke != null) {
                                        coroutineScope.launch { GoogleDriveAuth.revokeAccess(context, emailToRevoke) }
                                    }
                                }) {
                                    Text("Change")
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Set a password to encrypt future auto-sync backups. This is separate " +
                                        "from your manual backup password above, since the app needs to use " +
                                        "it automatically in the background.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = autoSyncPassword,
                                onValueChange = { autoSyncPassword = it; autoSyncErrorMessage = "" },
                                label = { Text("Auto Sync Password") },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = autoSyncConfirmPassword,
                                onValueChange = { autoSyncConfirmPassword = it; autoSyncErrorMessage = "" },
                                label = { Text("Confirm Auto Sync Password") },
                                visualTransformation = PasswordVisualTransformation(),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            if (autoSyncErrorMessage.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    autoSyncErrorMessage,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    when {
                                        autoSyncPassword.length < 6 ->
                                            autoSyncErrorMessage = "Password must be at least 6 characters"
                                        autoSyncPassword != autoSyncConfirmPassword ->
                                            autoSyncErrorMessage = "Passwords do not match"
                                        else -> {
                                            VaultsStorage.enableAutoSync(
                                                context,
                                                autoSyncPassword,
                                                driveConnectedEmail!!
                                            )
                                            autoSyncEnabled = true
                                            showAutoSyncSetup = false
                                            resetAutoSyncSetupFields()
                                            SyncWorker.schedulePeriodic(context)
                                            SyncWorker.enqueueImmediate(context)

                                            // Schedule periodic backups and trigger the first one now
                                            VaultsStorage.schedulePeriodicSync(context)
                                            VaultsStorage.markAutoSyncDirty(context)
                                            VaultsStorage.scheduleOneTimeSync(context)
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Enable Auto Sync")
                            }
                        }
                    }
                }

                // Status panel: shown once auto sync is actually enabled.
                AnimatedVisibility(
                    visible = autoSyncEnabled,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    "Auto sync is enabled",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    driveConnectedEmail ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(
                                onClick = {
                                    connectionTestSuccess = null
                                    isTestingConnection = true
                                    coroutineScope.launch {
                                        connectionTestSuccess = try {
                                            val authResult = GoogleDriveAuth.authorize(context)
                                            val token = authResult.accessToken
                                            if (token == null) {
                                                false
                                            } else {
                                                withContext(Dispatchers.IO) {
                                                    GoogleDriveAuth.testConnection(GoogleDriveAuth.buildDriveService(token))
                                                }.isSuccess
                                            }
                                        } catch (e: Exception) {
                                            false
                                        } finally {
                                            isTestingConnection = false
                                        }
                                    }
                                },
                                enabled = !isTestingConnection,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(if (isTestingConnection) "Testing..." else "Test Connection")
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            when (connectionTestSuccess) {
                                true -> Icon(Icons.Filled.CheckCircle, contentDescription = "Connected", tint = MaterialTheme.colorScheme.primary)
                                false -> Icon(Icons.Filled.Close, contentDescription = "Failed", tint = MaterialTheme.colorScheme.error)
                                null -> {}
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = {
                                isLoadingBackupsList = true
                                coroutineScope.launch {
                                    try {
                                        val token = GoogleDriveAuth.authorize(context).accessToken
                                        if (token != null) {
                                            val drive = GoogleDriveAuth.buildDriveService(token)
                                            cloudBackupsList = withContext(Dispatchers.IO) { GoogleDriveAuth.listBackups(drive) }
                                            showManageBackups = true
                                        }
                                    } finally {
                                        isLoadingBackupsList = false
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(if (isLoadingBackupsList) "Loading..." else "Manage Cloud Backups")
                        }

                        if (showManageBackups) {
                            AlertDialog(
                                onDismissRequest = { showManageBackups = false },
                                title = { Text("Cloud Backups") },
                                text = {
                                    if (cloudBackupsList.isEmpty()) {
                                        Text("No backups found.")
                                    } else {
                                        LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                                            items(cloudBackupsList) { file ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(file.name ?: "Unknown", modifier = Modifier.weight(1f))
                                                    IconButton(onClick = { pendingDeleteFile = file }) {
                                                        Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                },
                                confirmButton = {
                                    TextButton(onClick = { showManageBackups = false }) { Text("Close") }
                                }
                            )
                        }

                        pendingDeleteFile?.let { file ->
                            AlertDialog(
                                onDismissRequest = { pendingDeleteFile = null },
                                title = { Text("Move to Trash?") },
                                text = { Text("\"${file.name}\" will be moved to your Google Drive trash and permanently removed after ~30 days. This can't be undone from within Vaults.") },
                                confirmButton = {
                                    TextButton(onClick = {
                                        coroutineScope.launch {
                                            val token = GoogleDriveAuth.authorize(context).accessToken
                                            if (token != null) {
                                                val drive = GoogleDriveAuth.buildDriveService(token)
                                                val success = withContext(Dispatchers.IO) {
                                                    GoogleDriveAuth.trashFile(drive, file.id ?: "")
                                                }
                                                if (success) {
                                                    cloudBackupsList = cloudBackupsList.filter { it.id != file.id }
                                                }
                                            }
                                            pendingDeleteFile = null
                                        }
                                    }) {
                                        Text("Move to Trash", color = MaterialTheme.colorScheme.error)
                                    }
                                },
                                dismissButton = {
                                    TextButton(onClick = { pendingDeleteFile = null }) { Text("Cancel") }
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        TextButton(onClick = { disableAutoSync() }) {
                            Text("Disable Auto Sync", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            } else {
                // STEP 2: Export Options
                Icon(
                    Icons.Filled.Download,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Backup Ready",
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Your salt has been securely encrypted. Choose how you want to save it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(32.dp))

                // Option A: Save to File
                Button(
                    onClick = {
                        if (!isPickerActive) {
                            isPickerActive = true // Set flag before opening picker
                            createDocumentLauncher.launch("vaults_backup.vbak")
                        }
                    },
                    enabled = !isPickerActive,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save to File")
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Option B: Share Sheet
                OutlinedButton(
                    onClick = {
                        isShareActive = true // Set flag before opening share sheet
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, encryptedBackupPayload)
                            type = "text/plain"
                        }
                        val shareIntent = Intent.createChooser(sendIntent, "Save Backup")
                        context.startActivity(shareIntent)
                        // No reliable callback for ACTION_SEND chooser, so clear the flag
                        // once we're back on ON_RESUME instead of here.
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Share...")
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Option C: Reveal on Screen
                TextButton(
                    onClick = { showRawText = !showRawText },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Visibility, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (showRawText) "Hide Raw Data" else "Show Raw Data")
                }

                if (showRawText) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                            .padding(16.dp)
                    ) {
                        Text(
                            text = encryptedBackupPayload!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    // Clear the share flag once we resume from the share sheet.
    // (ACTION_SEND has no result callback, so ON_RESUME is our signal that it's closed.)
    DisposableEffect(lifecycleOwner) {
        val shareResetObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && isShareActive) {
                isShareActive = false
            }
        }
        lifecycleOwner.lifecycle.addObserver(shareResetObserver)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(shareResetObserver)
        }
    }
}

/**
 * Real AES-GCM encryption for the backup payload.
 */
private fun encryptBackup(payload: String, password: String): String {
    val salt = ByteArray(16)
    SecureRandom().nextBytes(salt)

    val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
    val spec = PBEKeySpec(password.toCharArray(), salt, 65536, 256)
    val tmp = factory.generateSecret(spec)
    val secret = SecretKeySpec(tmp.encoded, "AES")

    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, secret)
    val iv = cipher.iv
    val ciphertext = cipher.doFinal(payload.toByteArray(Charsets.UTF_8))

    val saltB64 = Base64.encodeToString(salt, Base64.NO_WRAP)
    val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
    val ciphertextB64 = Base64.encodeToString(ciphertext, Base64.NO_WRAP)

    return "enc_v1:$saltB64:$ivB64:$ciphertextB64"
}