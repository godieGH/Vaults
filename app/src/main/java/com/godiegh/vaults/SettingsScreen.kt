package com.godiegh.vaults

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

private data class ThemeModeOption(val value: Int, val label: String)
private data class AccentOption(val value: Int, val label: String, val primary: Color, val secondary: Color)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController) {
    val context = LocalContext.current

    var showClearDataDialog by remember { mutableStateOf(false) }

    var themeMode by remember {
        mutableStateOf(
            VaultsStorage.loadThemeMode(context).takeIf {
                it in listOf(VaultsStorage.THEME_SYSTEM, VaultsStorage.THEME_LIGHT, VaultsStorage.THEME_DARK)
            } ?: VaultsStorage.THEME_SYSTEM
        )
    }
    var accentPreset by remember {
        mutableStateOf(
            VaultsStorage.loadAccentPreset(context).takeIf {
                it in listOf(
                    VaultsStorage.ACCENT_CLASSIC,
                    VaultsStorage.ACCENT_EMERALD,
                    VaultsStorage.ACCENT_SAPPHIRE,
                    VaultsStorage.ACCENT_ROSE
                )
            } ?: VaultsStorage.ACCENT_CLASSIC
        )
    }

    val themeModes = listOf(
        ThemeModeOption(VaultsStorage.THEME_SYSTEM, "System Default"),
        ThemeModeOption(VaultsStorage.THEME_LIGHT, "Light"),
        ThemeModeOption(VaultsStorage.THEME_DARK, "Dark")
    )

    val accentOptions = listOf(
        AccentOption(VaultsStorage.ACCENT_CLASSIC, "Classic", com.godiegh.vaults.ui.theme.VaultGold, com.godiegh.vaults.ui.theme.VaultBlue),
        AccentOption(VaultsStorage.ACCENT_EMERALD, "Emerald Vault", com.godiegh.vaults.ui.theme.VaultGold, com.godiegh.vaults.ui.theme.VaultEmerald),
        AccentOption(VaultsStorage.ACCENT_SAPPHIRE, "Sapphire", com.godiegh.vaults.ui.theme.VaultBlue, com.godiegh.vaults.ui.theme.VaultGold),
        AccentOption(VaultsStorage.ACCENT_ROSE, "Rose Vault", com.godiegh.vaults.ui.theme.VaultRoseGold, com.godiegh.vaults.ui.theme.VaultPlatinum)
    )

    fun applyThemeMode(mode: Int) {
        themeMode = mode
        ThemeState.mode.value = mode
        VaultsStorage.saveThemeMode(context, mode)
    }

    fun applyAccent(preset: Int) {
        accentPreset = preset
        ThemeState.accent.value = preset
        VaultsStorage.saveAccentPreset(context, preset)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (!navController.popBackStack()) {
                            navController.navigate("main")
                        }
                    }) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            item { SettingsSectionHeader("Appearance") }

            item {
                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    Text("Theme", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    themeModes.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .clickable { applyThemeMode(option.value) }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(option.label, style = MaterialTheme.typography.bodyLarge)
                            if (themeMode == option.value) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }

            item {
                Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                    Text("Accent", style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    accentOptions.forEach { option ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.small)
                                .clickable { applyAccent(option.value) }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(option.primary)
                                )
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .offset(x = (-8).dp)
                                        .clip(CircleShape)
                                        .background(option.secondary)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(option.label, style = MaterialTheme.typography.bodyLarge)
                            }
                            if (accentPreset == option.value) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
            item { HorizontalDivider() }
            item { SettingsSectionHeader("Security") }

            item {
                SettingsStubRow(label = "Change Master Passphrase") {
                    /* TODO: wire once passphrase-change flow exists */
                }
            }
            item {
                SettingsStubRow(label = "Change 2FA Method") {
                    /* TODO: navigate back into 2fa_setup flow */
                }
            }
            item {
                SettingsStubRow(label = "Backup Salt") {
                    /* TODO: export encrypted salt */
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
            item { HorizontalDivider() }
            item { SettingsSectionHeader("Data") }

            item {
                SettingsStubRow(
                    label = "Clear All Data",
                    labelColor = MaterialTheme.colorScheme.error
                ) {
                    showClearDataDialog = true
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }

            item {
                Text(
                    text = "Vaults v1.0.0",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp)
                )
            }
        }

        if (showClearDataDialog) {
            AlertDialog(
                onDismissRequest = { showClearDataDialog = false },
                title = { Text("Clear All Data?") },
                text = {
                    Text(
                        "This permanently deletes your master passphrase salt, TOTP secret, saved services, and all preferences. " +
                                "This cannot be undone and you will need to set up Vaults again from scratch."
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            VaultsStorage.clearAll(context)
                            ThemeState.mode.value = VaultsStorage.THEME_SYSTEM
                            ThemeState.accent.value = VaultsStorage.ACCENT_CLASSIC
                            showClearDataDialog = false
                            navController.navigate("onboarding") {
                                popUpTo(0) { inclusive = true }
                            }
                        }
                    ) {
                        Text("Delete Everything", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearDataDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
    )
}

@Composable
private fun SettingsStubRow(
    label: String,
    labelColor: Color = Color.Unspecified,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = labelColor
        )
    }
}