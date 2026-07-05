package com.godiegh.vaults

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.godiegh.vaults.ui.theme.VaultsTheme
import uniffi.vaults.ffiGenerateSalt
import uniffi.vaults.ffiGenerateTotpSecret

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VaultsTheme {
                val navController = rememberNavController()
                val context = LocalContext.current
                val startDestination = if (VaultsStorage.isOnboarded(context)) "main" else "onboarding"

                NavHost(navController = navController, startDestination = startDestination) {
                    composable("onboarding") {
                        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                            OnboardingScreen(navController, modifier = Modifier.padding(innerPadding))
                        }
                    }
                    composable("main") {
                        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                            Box(modifier = Modifier.padding(innerPadding).fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Main Screen - Already Onboarded")
                            }
                        }
                    }
                    composable("totp_setup/{secret}") { backStackEntry ->
                        val secret = backStackEntry.arguments?.getString("secret") ?: ""
                        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                            Box(modifier = Modifier.padding(innerPadding).fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("TOTP Setup Screen - Secret: $secret")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OnboardingScreen(navController: NavController, modifier: Modifier) {
    val context = LocalContext.current
    var passphrase by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var passphraseVisible by remember { mutableStateOf(false) }
    var confirmVisible by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Welcome to Vaults", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Create a master passphrase. This is the only thing you need to remember.", style = MaterialTheme.typography.bodyMedium)
        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = passphrase,
            onValueChange = { passphrase = it },
            label = { Text("Master passphrase") },
            visualTransformation = if (passphraseVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                val image = if (passphraseVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                val description = if (passphraseVisible) "Hide passphrase" else "Show passphrase"

                IconButton(onClick = { passphraseVisible = !passphraseVisible }) {
                    Icon(imageVector = image, contentDescription = description)
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = confirm,
            onValueChange = { confirm = it },
            label = { Text("Confirm passphrase") },
            visualTransformation = if (confirmVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                val image = if (confirmVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                val description = if (confirmVisible) "Hide passphrase" else "Show passphrase"

                IconButton(onClick = { confirmVisible = !confirmVisible }) {
                    Icon(imageVector = image, contentDescription = description)
                }
            },
            modifier = Modifier.fillMaxWidth()
        )

        if (error.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(error, color = MaterialTheme.colorScheme.error)
        }

        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = {
                if (passphrase.length < 8) {
                    error = "Passphrase must be at least 8 characters"
                } else if (passphrase != confirm) {
                    error = "Passphrases do not match"
                } else {
                    val salt = ffiGenerateSalt()
                    val totpSecret = ffiGenerateTotpSecret()
                    VaultsStorage.saveSalt(context, salt)
                    VaultsStorage.saveTotpSecret(context, totpSecret)
                    navController.navigate("totp_setup/$totpSecret") {
                        popUpTo("onboarding") { inclusive = true }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Continue")
        }
    }
}