package com.example.materialdrain.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.materialdrain.network.UserList
import com.example.materialdrain.ui.formatApiDateTimeString
import com.example.materialdrain.viewmodel.ListViewModel

@Composable
fun ListsScreenContent(
    listViewModel: ListViewModel,
    fabHeight: Dp,
    isFabVisible: Boolean
) {
    val uiState by listViewModel.uiState.collectAsState()

    if (uiState.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (uiState.apiKeyMissingError) {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            Text(
                text = uiState.errorMessage ?: "API Key is missing. Please set it in Settings to browse your lists.",
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    } else if (uiState.errorMessage != null) {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            Text(
                text = uiState.errorMessage!!,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    } else if (uiState.lists.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            Text(
                text = "No lists found.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = if (isFabVisible) fabHeight + 16.dp else 16.dp)
        ) {
            items(uiState.lists) { list ->
                ListItem(
                    headlineContent = { Text(list.title) },
                    supportingContent = {
                        Text(
                            "Files: ${list.fileCount} • Created: ${formatApiDateTimeString(list.dateCreated)}"
                        )
                    },
                    modifier = Modifier.clickable { /* Handle list item click */ }
                )
                HorizontalDivider()
            }
        }
    }
}
