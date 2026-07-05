package com.godiegh.vaults

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        ThemeState.mode.value = VaultsStorage.loadThemeMode(this)
        ThemeState.accent.value = VaultsStorage.loadAccentPreset(this)

        setContent {
            VaultsTheme {
                val navController = rememberNavController()
                val context = LocalContext.current
                val startDestination = if (VaultsStorage.isOnboarded(context)) "main" else "onboarding"

                NavHost(navController = navController, startDestination = startDestination) {

                    // 1. Onboarding Flow
                    composable(route = "onboarding") {
                        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                            OnboardingScreen(navController, modifier = Modifier.padding(innerPadding))
                        }
                    }

                    // 2. Initial Setup Verification Target
                    composable(route = "totp_setup/{secret}") { backStackEntry ->
                        val secret = backStackEntry.arguments?.getString("secret") ?: ""
                        TotpSetupScreen(secret = secret, navController = navController)
                    }

                    // 3. Home Screen (Replaces the old placeholder "main" screen)
                    composable(route = "main") {
                        HomeScreen(navController = navController)
                    }

                    composable(route = "2fa_setup/{passphrase}") { backStackEntry ->
                        val passphrase = backStackEntry.arguments?.getString("passphrase") ?: ""
                        TwoFactorSetupScreen(passphrase = passphrase, navController = navController)
                    }

                    // 4. Add Service Link Configuration Page
                    composable(route = "add_service") {
                        AddServiceScreen(navController = navController)
                    }

                    composable(route = "settings") {
                        SettingsScreen(navController = navController,)
                    }

                    // 5. Secure Reveal Screen (Accepts the configuration components as route arguments)
                    composable(
                        route = "reveal_pin/{country}/{name}/{identifier}/{pinLength}"
                    ) { backStackEntry ->
                        // Extract parameters safely from navigation arguments
                        val country = backStackEntry.arguments?.getString("country") ?: "tz"
                        val name = backStackEntry.arguments?.getString("name") ?: ""
                        val identifier = backStackEntry.arguments?.getString("identifier") ?: ""
                        val pinLength = backStackEntry.arguments?.getString("pinLength")?.toIntOrNull() ?: 4

                        // Reconstruct temporary item context object for the Reveal UI layer
                        val selectedService = ServiceConfig(
                            id = "",
                            name = name,
                            countryCode = country,
                            identifier = identifier,
                            pinLength = pinLength
                        )

                        RevealPinScreen(service = selectedService, navController = navController)
                    }
                }

            }
        }
    }
}

@Composable
fun OnboardingScreen(navController: NavController, modifier: Modifier) {
    val context = LocalContext.current

    // Define the overview pages mapped to your actual application goals
    val overviewPages = listOf(
        Pair(
            "Never Memorize a PIN Again",
            "Manage distinct PINs for your mobile money accounts (M-Pesa, Tigo Pesa, Airtel Money) or your bank ATM cards or even Banking apps (like CRDB SimBanking, NMB Mklik, NBC Kiganjani) or a mobile money wallet effortlessly without reusing them or storing them anywhere."
        ),
        Pair(
            "Purely Derived, Zero Storage",
            "Vaults does not save your PINs. Instead, it securely computes them on the fly using your master passphrase combined with a unique cryptographic salt."
        ),
        Pair(
            "Two-Factor Security",
            "With integrated TOTP tokens and biometric authentication, your local generation keys remain safe even if your phone changes hands."
        )
    )

    // Total pages = Overview items + 1 final page for creating the passphrase
    val totalPages = overviewPages.size + 1
    val pagerState = rememberPagerState(pageCount = { totalPages })
    val coroutineScope = rememberCoroutineScope()

    // Passphrase state variables (Moved from original code)
    var passphrase by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var passphraseVisible by remember { mutableStateOf(false) }
    var confirmVisible by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterEnd
            ) {
                // Only show the skip button if we aren't on the final passphrase page yet
                if (pagerState.currentPage < totalPages - 1) {
                    TextButton(
                        onClick = {
                            coroutineScope.launch {
                                // Instantly snap to the final passphrase screen
                                pagerState.scrollToPage(totalPages - 1)
                            }
                        }
                    ) {
                        Text("Skip", color = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    // Keeps the spacing consistent when the button disappears
                    Spacer(modifier = Modifier.height(48.dp))
                }
            }

            // 2. The Swipable Container
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f), // Take up available space
                userScrollEnabled = true
            ) { pageIndex ->
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (pageIndex < overviewPages.size) {
                        // --- OVERVIEW PAGES ---
                        val (title, description) = overviewPages[pageIndex]

                        Text(title, style = MaterialTheme.typography.headlineMedium)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    } else {
                        // --- FINAL STEP: PASSPHRASE CREATION ---
                        Text("Secure Your Vault", style = MaterialTheme.typography.headlineMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Create a master passphrase. This is the only thing you need to remember.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(24.dp))

                        OutlinedTextField(
                            value = passphrase,
                            onValueChange = { passphrase = it },
                            label = { Text("Master passphrase") },
                            visualTransformation = if (passphraseVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                val image =
                                    if (passphraseVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                                val description =
                                    if (passphraseVisible) "Hide passphrase" else "Show passphrase"

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
                                val image =
                                    if (confirmVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                                val description =
                                    if (confirmVisible) "Hide passphrase" else "Show passphrase"

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
                    }
                }
            }

            // 3. Simple Page Indicators (Dots) at the bottom
            Row(
                Modifier.wrapContentHeight().fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(totalPages) { iteration ->
                    val color =
                        if (pagerState.currentPage == iteration) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    Box(
                        modifier = Modifier
                            .padding(4.dp)
                            .size(8.dp).background(
                                color,
                                shape = androidx.compose.foundation.shape.CircleShape
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 4. Dynamic Action Button
            Button(
                onClick = {
                    if (pagerState.currentPage < totalPages - 1) {
                        // If on overview screens, just advance to the next page
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                        }
                    } else {
                        // If on the final passphrase screen, execute your original validation & navigation logic
                        if (passphrase.length < 8) {
                            error = "Passphrase must be at least 8 characters"
                        } else if (passphrase != confirm) {
                            error = "Passphrases do not match"
                        } else {
                            val salt = ffiGenerateSalt()
                            val totpSecret = ffiGenerateTotpSecret()
                            VaultsStorage.saveSalt(context, salt)
                            VaultsStorage.saveTotpSecret(context, totpSecret)
                            navController.navigate("2fa_setup/$passphrase") {
                                popUpTo("onboarding") { inclusive = true }
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                // Text toggles depending on what page the user is currently looking at
                Text(if (pagerState.currentPage < totalPages - 1) "Next" else "Create Vault")
            }
        }
    }
}
