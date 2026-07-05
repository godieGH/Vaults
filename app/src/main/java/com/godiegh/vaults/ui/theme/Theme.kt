package com.godiegh.vaults.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.godiegh.vaults.ThemeState
import com.godiegh.vaults.VaultsStorage

private fun accentColors(preset: Int): Pair<Color, Color> = when (preset) {
    VaultsStorage.ACCENT_EMERALD -> VaultGold to VaultEmerald
    VaultsStorage.ACCENT_SAPPHIRE -> VaultBlue to VaultGold
    VaultsStorage.ACCENT_ROSE -> VaultRoseGold to VaultPlatinum
    else -> VaultGold to VaultBlue // ACCENT_CLASSIC
}

@Composable
fun VaultsTheme(
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val mode = ThemeState.mode.value
    val darkTheme = when (mode) {
        VaultsStorage.THEME_LIGHT -> false
        VaultsStorage.THEME_DARK -> true
        else -> isSystemInDarkTheme()
    }

    val (primaryColor, secondaryColor) = accentColors(ThemeState.accent.value)

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = primaryColor,
            secondary = secondaryColor,
            background = VaultBackground,
            surface = VaultSurface,
            surfaceVariant = VaultSurfaceVariant,
            onPrimary = VaultBackground,
            onSecondary = VaultText,
            onBackground = VaultText,
            onSurface = VaultText,
            error = VaultError
        )
    } else {
        lightColorScheme(
            primary = primaryColor,
            secondary = secondaryColor,
            background = VaultBackgroundLight,
            surface = Color.White,
            surfaceVariant = Color(0xFFF2F2F2),
            onPrimary = Color.Black,
            onSecondary = Color.White,
            onBackground = Color.Black,
            onSurface = Color.Black,
            error = VaultError
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}