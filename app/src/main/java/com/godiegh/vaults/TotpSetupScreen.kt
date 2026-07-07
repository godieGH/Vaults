package com.godiegh.vaults

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun TotpSetupScreen(secret: String, navController: NavController) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val coroutineScope = rememberCoroutineScope()

    // 1. Format the URI for Authenticator apps
    val issuer = "Vaults"
    val accountName = "MyVault"
    val otpUri = "otpauth://totp/$issuer:$accountName?secret=$secret&issuer=$issuer"

    // 2. State to hold the generated QR Bitmap
    var qrBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // 3. Generate the QR code off the main thread when the screen loads
    LaunchedEffect(otpUri) {
        qrBitmap = generateQrCode(otpUri, 512)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()) // Allow scrolling for smaller screens
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Set Up Two-Factor Auth", style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Scan this QR code with Google Authenticator, Authy, or any TOTP app.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))

            // --- QR CODE DISPLAY ---
            Card(
                modifier = Modifier.size(220.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White) // QR needs high contrast
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    qrBitmap?.let { bitmap ->
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "2FA QR Code",
                            modifier = Modifier.fillMaxSize()
                        )
                    } ?: CircularProgressIndicator() // Show loader while generating
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("Or enter this secret manually:", style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Text(
                    text = secret,
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            // --- ACTION BUTTONS (Copy & Save) ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(secret))
                        Toast.makeText(context, "Secret copied!", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Copy")
                }

                OutlinedButton(
                    onClick = {
                        qrBitmap?.let { bmp ->
                            coroutineScope.launch {
                                val success = saveImageToGallery(context, bmp)
                                val msg = if (success) "QR Code saved to Pictures" else "Failed to save image"
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    enabled = qrBitmap != null
                ) {
                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save QR")
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            Text(
                "Once added to your authenticator app, tap Continue. You will need this code every time you reveal a PIN.",
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = {
                    VaultsStorage.saveSetupStep(context, VaultsStorage.STEP_COMPLETED)
                    navController.navigate("main") {
                        popUpTo("totp_setup/$secret") { inclusive = true }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("Continue to Vaults", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

// --- HELPER FUNCTIONS ---

/**
 * Generates a Bitmap QR code from a given string payload.
 */
suspend fun generateQrCode(content: String, size: Int): Bitmap? = withContext(Dispatchers.Default) {
    try {
        val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                pixels[y * width + x] = if (bitMatrix.get(x, y)) {
                    android.graphics.Color.BLACK
                } else {
                    android.graphics.Color.WHITE
                }
            }
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        bitmap
    } catch (e: Exception) {
        null
    }
}

/**
 * Saves a Bitmap to the Android Gallery (Pictures/Vaults folder) using MediaStore.
 */
suspend fun saveImageToGallery(context: Context, bitmap: Bitmap): Boolean = withContext(Dispatchers.IO) {
    try {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "Vaults_2FA_QR_${System.currentTimeMillis()}.png")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/Vaults")
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        uri?.let {
            resolver.openOutputStream(it)?.use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            }
            true
        } ?: false
    } catch (e: Exception) {
        false
    }
}