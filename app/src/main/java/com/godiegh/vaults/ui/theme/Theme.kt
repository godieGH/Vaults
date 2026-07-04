package com.godiegh.vaults.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = VaultGold,
    secondary = VaultBlue,

    background = VaultBackground,
    surface = VaultSurface,
    surfaceVariant = VaultSurfaceVariant,

    onPrimary = VaultBackground,
    onSecondary = VaultText,

    onBackground = VaultText,
    onSurface = VaultText,

    error = VaultError
)

private val LightColorScheme = lightColorScheme(
    primary = VaultGold,
    secondary = VaultBlue,

    background = VaultText,
    surface = Color.White,
    surfaceVariant = Color(0xFFF2F2F2),

    onPrimary = Color.Black,
    onSecondary = Color.White,

    onBackground = Color.Black,
    onSurface = Color.Black,

    error = VaultError
)

@Composable
fun VaultsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme =
        if (darkTheme) DarkColorScheme
        else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}