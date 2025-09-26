package com.example.materialdrain.ui.shared

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue // Ensure this import is correct
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {}
) {
    SnackbarHost(
        hostState = hostState,
        modifier = modifier
    ) { snackbarData ->
        val dismissState = rememberSwipeToDismissBoxState(
            confirmValueChange = {
                if (it != SwipeToDismissBoxValue.Settled) { // Check usage of Settled
                    snackbarData.dismiss()
                    onDismiss() // Callback for when dismissed by swipe
                }
                true // Confirm the change
            }
        )

        // Reset dismiss state if the snackbarData changes (new snackbar appears)
        // to prevent auto-dismissal of new snackbars if the previous one was swiped.
        LaunchedEffect(snackbarData, dismissState) { // Add dismissState as a key
            // This should be line 31 or around it
            if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) { // Check usage of Settled
                 dismissState.snapTo(SwipeToDismissBoxValue.Settled) // Ensure this is snapTo and uses Settled
            }
        }

        SwipeToDismissBox(
            state = dismissState,
            backgroundContent = { /* No background needed for this use case */ },
            content = {
                Snackbar(
                    snackbarData = snackbarData,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    actionColor = MaterialTheme.colorScheme.primary,
                    dismissActionContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )
    }
}
