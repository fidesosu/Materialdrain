package com.example.materialdrain.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.materialdrain.viewmodel.FileInfoViewModel
import com.example.materialdrain.viewmodel.UploadViewModel

// SharedPreferences constants (moved here as they are specific to settings screen UI logic)
private const val PREFS_NAME = "pixeldrain_prefs"
private const val API_KEY_PREF = "api_key"

@Composable
fun SettingsScreenContent(
    uploadViewModel: UploadViewModel,
    fileInfoViewModel: FileInfoViewModel,
    onShowDialog: (String, String) -> Unit
) {
    var apiKeyInput by remember { mutableStateOf("") }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        apiKeyInput = sharedPrefs.getString(API_KEY_PREF, "") ?: ""
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Pixeldrain Settings", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom=24.dp))
        OutlinedTextField(value = apiKeyInput, onValueChange = { apiKeyInput = it }, label = { Text("Pixeldrain API Key") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                val sharedPrefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                with(sharedPrefs.edit()) {
                    putString(API_KEY_PREF, apiKeyInput.trim())
                    apply()
                }
                uploadViewModel.updateApiKey(apiKeyInput.trim()) // Ensure ViewModel has this method
                fileInfoViewModel.loadApiKey() // Ensure ViewModel has this method
                onShowDialog("Settings Saved", "API Key saved successfully.")
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save API Key")
        }
    }
}
