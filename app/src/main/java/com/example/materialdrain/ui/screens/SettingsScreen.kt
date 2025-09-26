package com.example.materialdrain.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreenContent(
    apiKeyInput: String,
    onApiKeyInputChange: (String) -> Unit,
    onShowDialog: (String, String) -> Unit,
    fabHeight: Dp,
    isFabVisible: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                start = 16.dp,
                end = 16.dp,
                top = 16.dp,
                bottom = if (isFabVisible) fabHeight + 16.dp else 16.dp
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Pixeldrain Settings", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom=24.dp))
        OutlinedTextField(
            value = apiKeyInput,
            onValueChange = { onApiKeyInputChange(it) },
            label = { Text("Pixeldrain API Key") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Spacer(modifier = Modifier.height(16.dp))
        // Button to save API key is handled by the FAB in MaterialDrainScreen
    }
}
