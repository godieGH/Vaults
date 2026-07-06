package com.godiegh.vaults

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
        contract = ActivityResultContracts.CreateDocument("text/plain")
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
                .padding(24.dp),
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
                            val backupObj = JSONObject()
                            backupObj.put("version", 1)
                            backupObj.put("salt", VaultsStorage.loadSalt(context)?.joinToString("") { "%02x".format(it) } ?: "")
                            backupObj.put("totp_secret", VaultsStorage.loadTotpSecret(context) ?: "")
                            backupObj.put("passphrase_fingerprint", VaultsStorage.loadFingerprint(context) ?: "")
                            
                            val services = VaultsStorage.loadServices(context)
                            val servicesArray = JSONArray()
                            services.forEach { s ->
                                val obj = JSONObject()
                                obj.put("id", s.id)
                                obj.put("name", s.name)
                                obj.put("countryCode", s.countryCode)
                                obj.put("identifier", s.identifier)
                                obj.put("pinLength", s.pinLength)
                                obj.put("rotation", s.rotation)
                                obj.put("displayName", s.displayName)
                                obj.put("category", s.category)
                                servicesArray.put(obj)
                            }
                            backupObj.put("services", servicesArray)

                            encryptedBackupPayload = encryptBackup(backupObj.toString(), backupPassword)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Encrypt Backup")
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
                            createDocumentLauncher.launch("vaults_backup.txt")
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