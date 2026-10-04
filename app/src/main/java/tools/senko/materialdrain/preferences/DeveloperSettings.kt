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
import tools.senko.materialdrain.navmenu.NavMenuPreview

/** Which provider's menu the FAB navigation prototype shows. */
@Composable
fun SettingsEnvironment.NavMenuPreviewSection() {
    val selected by appSettings.navMenuPreview.collectAsState()
    Column(modifier = Modifier.fillMaxWidth().selectableGroup()) {
        NavMenuPreview.entries.forEach { preview ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = preview == selected,
                        onClick = { appSettings.setNavMenuPreview(preview) },
                        role = Role.RadioButton
                    )
            ) {
                RadioButton(selected = preview == selected, onClick = null)
                Text(preview.label, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
