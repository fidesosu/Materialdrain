package com.example.materialdrain.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ListsScreenContent(onShowDialog: (String, String) -> Unit) {
    var listId by remember { mutableStateOf("") }
    var newListFileIds by remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(value = newListFileIds, onValueChange = { newListFileIds = it }, label = { Text("Enter File IDs for new list (comma-separated)") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = { onShowDialog("Create List", "Creating new list with files: $newListFileIds (Not Implemented)") }, modifier = Modifier.fillMaxWidth(), enabled = newListFileIds.isNotBlank()) {
            Text("Create New List")
        }
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedTextField(value = listId, onValueChange = { listId = it }, label = { Text("Enter List ID to View") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = { onShowDialog("View List", "Fetching details for List ID: $listId (Not Implemented)") }, modifier = Modifier.fillMaxWidth(), enabled = listId.isNotBlank()) {
            Text("View List Details")
        }
        Spacer(modifier = Modifier.weight(1f))
        Text("List functionality is not yet implemented.", style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.height(16.dp))
    }
}
