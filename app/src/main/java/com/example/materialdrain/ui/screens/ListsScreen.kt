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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun ListsScreenContent(
    onShowDialog: (String, String) -> Unit,
    fabHeight: Dp,
    isFabVisible: Boolean
) {
    var listId by remember { mutableStateOf("") }
    // newListFileIds and related Button removed as FAB handles new list creation concept
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp) // Horizontal padding applied once
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        Spacer(modifier = Modifier.height(16.dp)) // Initial top spacer

        OutlinedTextField(value = listId, onValueChange = { listId = it }, label = { Text("Enter List ID to View") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = { onShowDialog("View List", "Fetching details for List ID: $listId (Not Implemented)") }, modifier = Modifier.fillMaxWidth(), enabled = listId.isNotBlank()) {
            Text("View List Details")
        }
        Spacer(modifier = Modifier.weight(1f)) // Pushes content below to the bottom
        Text("List functionality is not yet implemented.", style = MaterialTheme.typography.bodySmall)
        
        // Add Spacer at the end of the scrollable content if FAB is visible
        if (isFabVisible) {
            Spacer(Modifier.height(fabHeight + 16.dp)) // fabHeight + existing 16dp bottom margin
        } else {
            Spacer(modifier = Modifier.height(16.dp)) // Default bottom spacer when FAB is not visible
        }
    }
}
