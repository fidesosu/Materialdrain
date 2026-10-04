package tools.senko.materialdrain.hosts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import tools.senko.materialdrain.auth.ApiKeySource
import tools.senko.materialdrain.auth.SessionManager
import tools.senko.materialdrain.provider.PIXELDRAIN_PROVIDER_ID
import tools.senko.materialdrain.provider.ProviderConfigStore
import tools.senko.materialdrain.provider.StoredProvider
import tools.senko.materialdrain.provider.api.AuthType
import tools.senko.materialdrain.provider.api.GenericRestConfig
import tools.senko.materialdrain.provider.api.ProviderKind
import tools.senko.materialdrain.provider.api.S3Config
import tools.senko.materialdrain.provider.api.WebDavConfig

/** Whether a host has a sign-in, shown next to its name in the switcher. */
enum class HostStatus(val label: String) {
    SIGNED_IN("Signed in"),
    NOT_SIGNED_IN("Not signed in"),
    NO_SIGN_IN_NEEDED("No sign-in needed")
}

/** One host in the switcher. [detail] is the more specific line, e.g. the user the host is signed in as. */
data class HostOption(
    val id: String,
    val name: String,
    val kind: ProviderKind,
    val status: HostStatus,
    val detail: String
) {
    val icon: ImageVector
        get() = when (kind) {
            ProviderKind.PIXELDRAIN -> Icons.Filled.Cloud
            ProviderKind.WEBDAV -> Icons.Filled.FolderShared
            ProviderKind.S3 -> Icons.Filled.Storage
            ProviderKind.GENERIC_REST -> Icons.Filled.Dns
        }
}

/**
 * Every host the user can switch to: the built-in Pixeldrain first, then the configured ones in the order they were
 * added. Built on each call, so it always reflects the current sign-ins.
 */
fun hostOptions(
    configStore: ProviderConfigStore,
    sessionManager: SessionManager,
    signedInUser: String?,
): List<HostOption> {
    val source = sessionManager.activeSource()
    val pixeldrain = HostOption(
        id = PIXELDRAIN_PROVIDER_ID,
        name = "Pixeldrain",
        kind = ProviderKind.PIXELDRAIN,
        status = if (source == ApiKeySource.NONE) HostStatus.NOT_SIGNED_IN else HostStatus.SIGNED_IN,
        detail = when (source) {
            ApiKeySource.LOGIN -> signedInUser?.let { "Signed in as $it" } ?: "Signed in"
            ApiKeySource.MANUAL -> "API key saved"
            ApiKeySource.NONE -> "No API key yet"
        }
    )
    return listOf(pixeldrain) + configStore.providers.value.map { it.toHostOption(configStore) }
}

private fun StoredProvider.toHostOption(configStore: ProviderConfigStore): HostOption {
    val (kind, needsSignIn) = when (val config = config) {
        is GenericRestConfig -> ProviderKind.GENERIC_REST to (config.auth.type != AuthType.NONE)
        is WebDavConfig -> ProviderKind.WEBDAV to true
        is S3Config -> ProviderKind.S3 to true
    }
    val creds = configStore.credentials(id)
    val signedIn = creds.apiKey.isNotBlank() || creds.loginToken != null || creds.hasPassword
    val status = when {
        !needsSignIn -> HostStatus.NO_SIGN_IN_NEEDED
        signedIn -> HostStatus.SIGNED_IN
        else -> HostStatus.NOT_SIGNED_IN
    }
    return HostOption(
        id = id,
        name = config.name,
        kind = kind,
        status = status,
        detail = when {
            status == HostStatus.SIGNED_IN && creds.username.isNotBlank() -> "Signed in as ${creds.username}"
            else -> status.label
        }
    )
}

/** Green: the host answers. Yellow: not checked yet, or unclear. Red: it doesn't answer. */
private val ReachableColor = Color(0xFF2E7D32)
private val UnknownColor = Color(0xFFF9A825)
private val UnreachableColor = Color(0xFFC62828)

private fun Reachability.color(): Color = when (this) {
    Reachability.REACHABLE -> ReachableColor
    Reachability.UNKNOWN -> UnknownColor
    Reachability.UNREACHABLE -> UnreachableColor
}

private val SwitcherShape = RoundedCornerShape(6.dp)

/**
 * The host the browser is using, shown in the top bar. Its status dot is green, yellow or red for reachable,
 * unknown or unreachable. Tapping it opens the list of hosts; choosing one makes it the active host.
 */
@Composable
fun HostSwitcher(
    hosts: List<HostOption>,
    activeId: String,
    reachability: Map<String, Reachability>,
    onSelect: (String) -> Unit,
    onManage: () -> Unit,
    onOpened: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val active = hosts.firstOrNull { it.id == activeId } ?: hosts.first()
    val activeReachability = reachability[active.id] ?: Reachability.UNKNOWN

    Box {
        Row(
            modifier = Modifier
                .width(SwitcherWidth)
                .height(40.dp)
                .clip(SwitcherShape)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, SwitcherShape)
                .clickable {
                    expanded = true
                    onOpened()
                }
                .padding(start = 10.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(active.icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text = active.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            StatusDot(activeReachability)
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(MenuWidth),
            // The menu is wider than the switcher, so it's moved left by half the difference to sit centred under it
            offset = DpOffset((SwitcherWidth - MenuWidth) / 2, 0.dp)
        ) {
            hosts.forEach { host ->
                val isActive = host.id == activeId
                DropdownMenuItem(
                    modifier = if (isActive) Modifier.background(MaterialTheme.colorScheme.secondaryContainer) else Modifier,
                    leadingIcon = { Icon(host.icon, contentDescription = null) },
                    text = {
                        Column {
                            Text(host.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                host.detail,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    trailingIcon = { StatusDot(reachability[host.id] ?: Reachability.UNKNOWN) },
                    onClick = {
                        expanded = false
                        onSelect(host.id)
                    }
                )
            }
            HorizontalDivider()
            DropdownMenuItem(
                leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                text = { Text("Manage hosts") },
                onClick = {
                    expanded = false
                    onManage()
                }
            )
        }
    }
}

private val SwitcherWidth = 200.dp
private val MenuWidth = 240.dp

@Composable
private fun StatusDot(reachability: Reachability) {
    Spacer(
        Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(reachability.color())
    )
}
