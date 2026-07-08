package com.godiegh.vaults

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.onFocusChanged
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Shield
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE
        )

        enableEdgeToEdge()

        ThemeState.mode.value = VaultsStorage.loadThemeMode(this)
        ThemeState.accent.value = VaultsStorage.loadAccentPreset(this)

        setContent {
            VaultsTheme {
                val navController = rememberNavController()
                val context = LocalContext.current
                
                val startDestination = remember {
                    val step = VaultsStorage.loadSetupStep(context)
                    val onboarded = VaultsStorage.isOnboarded(context)
                    
                    if (onboarded) {
                        when (step) {
                            VaultsStorage.STEP_2FA_SETUP -> {
                                val pass = VaultsStorage.loadEncryptedPassphrase(context) ?: ""
                                val encoded = java.net.URLEncoder.encode(pass, "UTF-8")
                                "2fa_setup/$encoded"
                            }
                            VaultsStorage.STEP_COMPLETED -> "main"
                            else -> "main"
                        }
                    } else {
                        "onboarding"
                    }
                }

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
                        val raw = backStackEntry.arguments?.getString("passphrase") ?: ""
                        val passphrase = java.net.URLDecoder.decode(raw, "UTF-8")
                        TwoFactorSetupScreen(passphrase = passphrase, navController = navController)
                    }

                    composable(route = "reauth_for_2fa/{passphrase}") { backStackEntry ->
                        val raw = backStackEntry.arguments?.getString("passphrase") ?: ""
                        val passphrase = java.net.URLDecoder.decode(raw, "UTF-8")
                        ReAuthScreen(passphrase = passphrase, navController = navController)
                    }
                    // 4. Add Service Link Configuration Page
                    composable(route = "add_service") {
                        AddServiceScreen(navController = navController)
                    }

                    composable(route = "settings") {
                        SettingsScreen(navController = navController)
                    }

                    // 5. Secure Reveal Screen (Accepts the configuration components as route arguments)
                    composable(route = "reveal_pin/{id}/{country}/{name}/{identifier}/{pinLength}/{rotation}") { backStackEntry ->
                        val id = backStackEntry.arguments?.getString("id") ?: ""
                        val country = backStackEntry.arguments?.getString("country") ?: "tz"
                        val name = backStackEntry.arguments?.getString("name") ?: ""
                        val identifier = backStackEntry.arguments?.getString("identifier") ?: ""
                        val pinLength = backStackEntry.arguments?.getString("pinLength")?.toIntOrNull() ?: 4
                        val rotation = backStackEntry.arguments?.getString("rotation")?.toIntOrNull() ?: 1

                        val selectedService = ServiceConfig(
                            id = id, name = name, countryCode = country,
                            identifier = identifier, pinLength = pinLength, rotation = rotation
                        )
                        RevealPinScreen(service = selectedService, navController = navController)
                    }

                    composable(route = "rotate_pin/{id}/{country}/{name}/{identifier}/{pinLength}/{rotation}") { backStackEntry ->
                        val id = backStackEntry.arguments?.getString("id") ?: ""
                        val country = backStackEntry.arguments?.getString("country") ?: "tz"
                        val name = backStackEntry.arguments?.getString("name") ?: ""
                        val identifier = backStackEntry.arguments?.getString("identifier") ?: ""
                        val pinLength = backStackEntry.arguments?.getString("pinLength")?.toIntOrNull() ?: 4
                        val rotation = backStackEntry.arguments?.getString("rotation")?.toIntOrNull() ?: 1

                        val selectedService = ServiceConfig(
                            id = id, name = name, countryCode = country,
                            identifier = identifier, pinLength = pinLength, rotation = rotation
                        )
                        RotatePinScreen(service = selectedService, navController = navController)
                    }

                    composable(route = "reauth_for_backup") {
                        ReAuthForBackupScreen(navController = navController)
                    }

                    composable(route = "backup_salt") {
                        BackupSaltScreen(navController = navController)
                    }

                    composable(route = "restore_backup") {
                        RestoreBackupScreen(navController = navController)
                    }

                    composable(route = "lifecycle_rotation") {
                        LifecycleRotationScreen(navController = navController)
                    }
                }

            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(navController: NavController, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    val overviewPages = listOf(
        Triple(
            Icons.Default.Password,
            "Never Memorize a PIN Again",
            "Manage unique PINs for your mobile money accounts (M-Pesa, Tigo Pesa, Airtel Money), bank ATM cards, and banking apps (CRDB SimBanking, NMB Mkononi, NBC Kiganjani) effortlessly—without reusing or storing them anywhere."
        ),
        Triple(
            Icons.Default.CloudOff,
            "Purely Derived, Zero Storage",
            "Vaults never saves your PINs. It securely computes them on the fly using your master passphrase combined with a unique cryptographic salt."
        ),
        Triple(
            Icons.Default.Shield,
            "Two-Factor Security",
            "With integrated TOTP tokens and biometric authentication, your local generation keys stay safe even if your phone changes hands."
        )
    )

    val totalPages = overviewPages.size + 1
    val pagerState = rememberPagerState(pageCount = { totalPages })
    val coroutineScope = rememberCoroutineScope()

    var passphrase by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var passphraseVisible by remember { mutableStateOf(false) }
    var confirmVisible by remember { mutableStateOf(false) }

    fun strength(pass: String): Float {
        var score = 0
        if (pass.length >= 8) score++
        if (pass.length >= 12) score++
        if (pass.any { it.isDigit() }) score++
        if (pass.any { !it.isLetterOrDigit() }) score++
        if (pass.any { it.isUpperCase() } && pass.any { it.isLowerCase() }) score++
        return score / 5f
    }

    val onLastPage = pagerState.currentPage == totalPages - 1
    val canProceed = !onLastPage || (passphrase.length >= 8 && passphrase == confirm)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                            MaterialTheme.colorScheme.background
                        ),
                        endY = 900f
                    )
                )
        ) {
            // Root column that manages keyboard padding without shrinking inner weights improperly
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .imePadding()
            ) {
                // --- TOP BAR (Fixed at the top) ---
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    if (!onLastPage) {
                        TextButton(
                            onClick = { coroutineScope.launch { pagerState.scrollToPage(totalPages - 1) } }
                        ) {
                            Text("Skip", color = MaterialTheme.colorScheme.primary)
                        }
                    } else {
                        var showInfo by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { showInfo = !showInfo }) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = "Why can't I change this later?",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            val density = LocalDensity.current
                            if (showInfo) {
                                Popup(
                                    alignment = Alignment.TopEnd,
                                    offset = with(density) { IntOffset(-40, 44.dp.roundToPx()) },
                                    onDismissRequest = { showInfo = false }
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(
                                            topEnd = 0.dp,
                                            topStart = 12.dp,
                                            bottomStart = 12.dp,
                                            bottomEnd = 12.dp
                                        ),
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        tonalElevation = 3.dp,
                                        modifier = Modifier.widthIn(min = 240.dp, max = 300.dp)
                                    ) {
                                        Text(
                                            "Unlike a login password, this can't be changed later without starting over — it's the key that generates all your future PINs.",
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.padding(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // --- PAGER AREA (Scrollable area) ---
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        userScrollEnabled = true
                    ) { pageIndex ->
                        // Scroll state is localized to the individual page
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 32.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (pageIndex < overviewPages.size) {
                                val (icon, title, description) = overviewPages[pageIndex]
                                Spacer(modifier = Modifier.height(24.dp))
                                Box(
                                    modifier = Modifier
                                        .size(104.dp)
                                        .background(
                                            Brush.linearGradient(
                                                listOf(
                                                    MaterialTheme.colorScheme.primary,
                                                    MaterialTheme.colorScheme.tertiary
                                                )
                                            ),
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(46.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(32.dp))
                                Text(
                                    title,
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = description,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(24.dp))
                            } else {
                                // --- FINAL STEP: PASSPHRASE CREATION ---
                                Spacer(modifier = Modifier.height(24.dp))
                                Box(
                                    modifier = Modifier
                                        .size(88.dp)
                                        .background(
                                            MaterialTheme.colorScheme.primaryContainer,
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(40.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(20.dp))
                                Text(
                                    "Secure Your Vault",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Create a master passphrase. This is the only thing you need to remember.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(24.dp))

                                OutlinedTextField(
                                    value = passphrase,
                                    onValueChange = { passphrase = it; error = "" },
                                    label = { Text("Master passphrase") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    visualTransformation = if (passphraseVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        IconButton(onClick = { passphraseVisible = !passphraseVisible }) {
                                            Icon(
                                                imageVector = if (passphraseVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                                contentDescription = if (passphraseVisible) "Hide passphrase" else "Show passphrase"
                                            )
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )

                                if (passphrase.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    val s = strength(passphrase)
                                    LinearProgressIndicator(
                                        progress = { s },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(5.dp)
                                            .clip(RoundedCornerShape(50)),
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
                                    value = confirm,
                                    onValueChange = { confirm = it; error = "" },
                                    label = { Text("Confirm passphrase") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    visualTransformation = if (confirmVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        if (confirm.isNotEmpty()) {
                                            Icon(
                                                imageVector = if (confirm == passphrase) Icons.Default.CheckCircle else Icons.Default.Cancel,
                                                contentDescription = null,
                                                tint = if (confirm == passphrase) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                                            )
                                        } else {
                                            IconButton(onClick = { confirmVisible = !confirmVisible }) {
                                                Icon(
                                                    imageVector = if (confirmVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                                    contentDescription = if (confirmVisible) "Hide passphrase" else "Show passphrase"
                                                )
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )

                                if (error.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = error,
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                Spacer(modifier = Modifier.height(24.dp))
                            }
                        }
                    }
                }

                // --- FIXED BOTTOM AREA (Stays locked over keyboard) ---
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                ) {
                    // --- DOT INDICATORS ---
                    Row(
                        Modifier
                            .wrapContentHeight()
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        repeat(totalPages) { iteration ->
                            val active = pagerState.currentPage == iteration
                            val width by animateDpAsState(if (active) 24.dp else 8.dp, label = "dotWidth")
                            Box(
                                modifier = Modifier
                                    .padding(4.dp)
                                    .height(8.dp)
                                    .width(width)
                                    .background(
                                        if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                        shape = CircleShape
                                    )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // --- ACTION BUTTON ---
                    Button(
                        onClick = {
                            if (!onLastPage) {
                                coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                            } else {
                                if (passphrase.length < 8) {
                                    error = "Passphrase must be at least 8 characters"
                                } else if (passphrase != confirm) {
                                    error = "Passphrases do not match"
                                } else {
                                    val existingSalt = VaultsStorage.loadSalt(context)
                                    val salt = existingSalt ?: ffiGenerateSalt()

                                    val existingTotp = VaultsStorage.loadTotpSecret(context)
                                    val totpSecret = existingTotp ?: ffiGenerateTotpSecret()

                                    val fingerprint = uniffi.vaults.ffiDerivePassphraseFingerprint(passphrase, salt)
                                    VaultsStorage.saveFingerprint(context, fingerprint)

                                    VaultsStorage.saveSalt(context, salt)
                                    VaultsStorage.saveTotpSecret(context, totpSecret)

                                    VaultsStorage.saveSetupStep(context, VaultsStorage.STEP_2FA_SETUP)

                                    val encoded = java.net.URLEncoder.encode(passphrase, "UTF-8")
                                    navController.navigate("2fa_setup/$encoded") {
                                        popUpTo("onboarding") { inclusive = true }
                                    }
                                }
                            }
                        },
                        enabled = canProceed,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        contentPadding = PaddingValues()
                    ) {
                        Text(
                            text = if (!onLastPage) "Next" else "Create Vault",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    if (!onLastPage) {
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedButton(
                            onClick = { navController.navigate("restore_backup") },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            Text("Restore from Backup", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}