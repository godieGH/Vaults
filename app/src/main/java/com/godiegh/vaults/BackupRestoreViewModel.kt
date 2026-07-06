package com.godiegh.vaults

import android.content.Context
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BackupRestoreViewModel : ViewModel() {

    private val _backupPayload = MutableStateFlow("")
    val backupPayload: StateFlow<String> = _backupPayload

    private val _errorMessage = MutableStateFlow("")
    val errorMessage: StateFlow<String> = _errorMessage

    fun setBackupPayload(payload: String) {
        _backupPayload.value = payload
    }

    fun setErrorMessage(message: String) {
        _errorMessage.value = message
    }

    fun saveBackupToFile(context: Context, uri: Uri, payload: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                    outputStream.write(payload.toByteArray())
                }
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "Backup saved successfully!"
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "Failed to save file: ${e.localizedMessage}"
                }
            }
        }
    }

    fun loadBackupFromFile(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val content = inputStream.bufferedReader().readText()
                    withContext(Dispatchers.Main) {
                        _backupPayload.value = content
                        _errorMessage.value = ""
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _errorMessage.value = "Failed to read file: ${e.localizedMessage}"
                }
            }
        }
    }
}