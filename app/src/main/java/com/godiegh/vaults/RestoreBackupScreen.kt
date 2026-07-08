package com.godiegh.vaults

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.filled.FileOpen
import org.json.JSONObject
import android.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Cloud
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.app.Activity
import androidx.activity.result.IntentSenderRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestoreBackupScreen(
    navController: NavController,
    vm: BackupRestoreViewModel = viewModel()
) {
    val context = LocalContext.current

    val backupPayload by vm.backupPayload.collectAsState()
    var backupPassword by remember { mutableStateOf("") }

    val vmError by vm.errorMessage.collectAsState()
    var localErrorMessage by remember { mutableStateOf("") }
    val displayError = vmError.ifEmpty { localErrorMessage }

    val coroutineScope = rememberCoroutineScope()
    val relocationRequester1 = remember { BringIntoViewRequester() }
    val relocationRequester2 = remember { BringIntoViewRequester() }

    // Validation state
    val canAttemptRestore = backupPayload.isNotBlank() && backupPassword.isNotBlank()

    var showCloudPicker by remember { mutableStateOf(false) }
    var cloudBackups by remember { mutableStateOf<List<com.google.api.services.drive.model.File>>(emptyList()) }
    var isLoadingCloudBackups by remember { mutableStateOf(false) }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            vm.loadBackupFromFile(context, it)
            localErrorMessage = ""
        }
    }

    suspend fun loadCloudBackups(authResult: AuthorizationResult) {
        val token = authResult.accessToken
        if (token == null) {
            localErrorMessage = "Google Drive didn't return an access token. Please try again."
            return
        }
        val drive = GoogleDriveAuth.buildDriveService(token)
        cloudBackups = withContext(Dispatchers.IO) { GoogleDriveAuth.listBackups(drive) }
        showCloudPicker = true
    }

    val driveAuthorizationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val authResult = GoogleDriveAuth.resultFromIntent(context, result.data!!)
            coroutineScope.launch {
                isLoadingCloudBackups = true
                loadCloudBackups(authResult)
                isLoadingCloudBackups = false
            }
        } else {
            localErrorMessage = "Google Drive connection was cancelled."
            isLoadingCloudBackups = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Restore Vault") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        // Root container handles keyboard padding
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            // Scrollable form area
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Filled.Restore,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Restore from Backup",
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Paste your encrypted backup data or load from a file, and enter the password you used to secure it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { openDocumentLauncher.launch(arrayOf("text/plain")) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                ) {
                    Icon(Icons.Filled.FileOpen, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Load from File", style = MaterialTheme.typography.titleMedium)
                }

                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        isLoadingCloudBackups = true
                        localErrorMessage = ""
                        coroutineScope.launch {
                            try {
                                val authResult = GoogleDriveAuth.authorize(context)
                                if (authResult.hasResolution()) {
                                    val pendingIntent = authResult.pendingIntent
                                    if (pendingIntent != null) {
                                        driveAuthorizationLauncher.launch(
                                            IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                                        )
                                    } else {
                                        localErrorMessage = "Couldn't start Google Drive authorization."
                                        isLoadingCloudBackups = false
                                    }
                                } else {
                                    loadCloudBackups(authResult)
                                    isLoadingCloudBackups = false
                                }
                            } catch (e: Exception) {
                                localErrorMessage = "Couldn't connect to Google Drive: ${e.localizedMessage}"
                                isLoadingCloudBackups = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Filled.Cloud, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (isLoadingCloudBackups) "Loading..." else "Restore from Cloud", style = MaterialTheme.typography.titleMedium)
                }

                if (showCloudPicker) {
                    AlertDialog(
                        onDismissRequest = { showCloudPicker = false },
                        title = { Text("Choose a Backup") },
                        text = {
                            if (cloudBackups.isEmpty()) {
                                Text("No backups found in your connected Google Drive.")
                            } else {
                                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                                    items(cloudBackups) { file ->
                                        Text(
                                            text = file.name ?: "Unknown",
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    showCloudPicker = false
                                                    val fileId = file.id ?: return@clickable
                                                    coroutineScope.launch {
                                                        val token = GoogleDriveAuth.authorize(context).accessToken
                                                        if (token != null) {
                                                            val drive = GoogleDriveAuth.buildDriveService(token)
                                                            val content = withContext(Dispatchers.IO) {
                                                                GoogleDriveAuth.downloadFile(drive, fileId)
                                                            }
                                                            if (content != null) {
                                                                vm.setBackupPayload(content)
                                                            } else {
                                                                localErrorMessage = "Failed to download backup."
                                                            }
                                                        }
                                                    }
                                                }
                                                .padding(vertical = 12.dp)
                                        )
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showCloudPicker = false }) { Text("Cancel") }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = backupPayload,
                    onValueChange = { vm.setBackupPayload(it); localErrorMessage = ""; vm.setErrorMessage("") },
                    label = { Text("Encrypted Backup Data") },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 200.dp)
                        .bringIntoViewRequester(relocationRequester1)
                        .onFocusChanged {
                            if (it.isFocused) {
                                coroutineScope.launch { relocationRequester1.bringIntoView() }
                            }
                        },
                    minLines = 4
                )

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = backupPassword,
                    onValueChange = { backupPassword = it; localErrorMessage = ""; vm.setErrorMessage("") },
                    label = { Text("Backup Password") },
                    shape = RoundedCornerShape(14.dp),
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .bringIntoViewRequester(relocationRequester2)
                        .onFocusChanged {
                            if (it.isFocused) {
                                coroutineScope.launch { relocationRequester2.bringIntoView() }
                            }
                        }
                )

                if (displayError.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(displayError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }

            // Fixed bottom area for the action button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Button(
                    onClick = {
                        val cleanPayload = backupPayload.trim()

                        // 1. Pre-flight format check
                        if (!cleanPayload.startsWith("enc_v1:") && !cleanPayload.startsWith("sync_v1:")) {
                            localErrorMessage = "Invalid backup format."
                            return@Button
                        }

                        // 2. Attempt Decryption
                        try {
                            val decrypted = decryptBackup(cleanPayload, backupPassword)
                            if (decrypted != null) {
                                val backupObj = JSONObject(decrypted)

                                // Restore data...
                                val saltHex = backupObj.getString("salt")
                                val salt = saltHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
                                VaultsStorage.saveSalt(context, salt)

                                val totpSecret = backupObj.getString("totp_secret")
                                VaultsStorage.saveTotpSecret(context, totpSecret)

                                val fingerprint = backupObj.getString("passphrase_fingerprint")
                                VaultsStorage.saveFingerprint(context, fingerprint)

                                val encPass = backupObj.optString("encrypted_passphrase", "")
                                if (encPass.isNotEmpty()) {
                                    VaultsStorage.saveEncryptedPassphrase(context, encPass)
                                }

                                val servicesArray = backupObj.getJSONArray("services")
                                val services = (0 until servicesArray.length()).map { i ->
                                    val obj = servicesArray.getJSONObject(i)
                                    ServiceConfig(
                                        id = obj.getString("id"),
                                        name = obj.getString("name"),
                                        countryCode = obj.getString("countryCode"),
                                        identifier = obj.getString("identifier"),
                                        pinLength = obj.getInt("pinLength"),
                                        rotation = obj.optInt("rotation", 1),
                                        displayName = obj.optString("displayName", obj.getString("name")),
                                        category = obj.optString("category", "MOBILE_MONEY")
                                    )
                                }
                                VaultsStorage.saveServices(context, services)

                                val pass = VaultsStorage.loadEncryptedPassphrase(context) ?: ""
                                val encoded = java.net.URLEncoder.encode(pass, "UTF-8")
                                VaultsStorage.saveSetupStep(context, VaultsStorage.STEP_2FA_SETUP)

                                navController.navigate("2fa_setup/$encoded") {
                                    popUpTo("restore_backup") { inclusive = true }
                                }
                            } else {
                                localErrorMessage = "Incorrect password or corrupted backup data."
                            }
                        } catch (e: Exception) {
                            localErrorMessage = "Restore failed: ensure the data is complete and password is correct."
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(14.dp),
                    enabled = canAttemptRestore // Button is disabled if fields are empty
                ) {
                    Text("Restore Vault", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

/**
 * Real AES-GCM decryption for the backup payload.
 */
private fun decryptBackup(payload: String, password: String): String? {
    val prefix = when {
        payload.startsWith("enc_v1:") -> "enc_v1:"
        payload.startsWith("sync_v1:") -> "sync_v1:"
        else -> return null
    }

    val parts = payload.removePrefix(prefix).split(":")
    if (parts.size != 3) return null

    return try {
        val salt = Base64.decode(parts[0], Base64.NO_WRAP)
        val iv = Base64.decode(parts[1], Base64.NO_WRAP)
        val ciphertext = Base64.decode(parts[2], Base64.NO_WRAP)

        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, 65536, 256)
        val tmp = factory.generateSecret(spec)
        val secret = SecretKeySpec(tmp.encoded, "AES")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val gcmSpec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, secret, gcmSpec)

        val decrypted = cipher.doFinal(ciphertext)
        String(decrypted, Charsets.UTF_8)
    } catch (e: Exception) {
        null
    }
}