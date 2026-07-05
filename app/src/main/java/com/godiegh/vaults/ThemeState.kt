package com.godiegh.vaults

import androidx.compose.runtime.mutableStateOf

object ThemeState {
    val mode = mutableStateOf(VaultsStorage.THEME_SYSTEM)
    val accent = mutableStateOf(VaultsStorage.ACCENT_CLASSIC)
}