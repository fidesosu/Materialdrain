package com.example.materialdrain.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.materialdrain.network.FileInfoResponse
import com.example.materialdrain.ui.formatApiDateTimeString
import com.example.materialdrain.ui.formatSize
import com.example.materialdrain.viewmodel.ListViewModel

@Composable
fun ListDetailsScreen(
    listViewModel: ListViewModel
) {
    val uiState by listViewModel.uiState.collectAsState()

    if (uiState.isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
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
    } else if (uiState.selectedListDetails?.files?.isEmpty() == true) {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            Text(
                text = "This list is empty.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            items(uiState.selectedListDetails?.files ?: emptyList<FileInfoResponse>()) { file ->
                ListItem(
                    headlineContent = { Text(file.name, maxLines = 1) },
                    supportingContent = {
                        Text(
                            "Size: ${formatSize(file.size)} • Uploaded: ${formatApiDateTimeString(file.dateUpload)}",
                            maxLines = 1
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Filled.Description,
                            contentDescription = "File"
                        )
                    }
                )
                HorizontalDivider()
            }
        }
    }
}