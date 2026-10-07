package tools.senko.materialdrain.preferences

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import tools.senko.materialdrain.settings.SEARCH_RESULT_LIMITS

/** The choice of [tools.senko.materialdrain.settings.AppSettings.searchResultLimit]. */
@Composable
fun SettingsEnvironment.SearchResultLimitSection() {
    val selected by appSettings.searchResultLimit.collectAsState()
    Column(modifier = Modifier.fillMaxWidth().selectableGroup()) {
        SEARCH_RESULT_LIMITS.forEach { limit ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = limit == selected,
                        onClick = { appSettings.setSearchResultLimit(limit) },
                        role = Role.RadioButton
                    )
            ) {
                RadioButton(selected = limit == selected, onClick = null)
                Text(if (limit == 0) "No limit" else "$limit", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
