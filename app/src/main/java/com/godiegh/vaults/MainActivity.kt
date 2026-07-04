package com.godiegh.vaults

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import com.godiegh.vaults.ui.theme.VaultsTheme
import uniffi.vaults.ffiGenerateSalt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            VaultsTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    val salt = ffiGenerateSalt()
                    Text(
                        text = "Salt: ${salt.take(8).joinToString("") { "%02x".format(it) }}...",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

