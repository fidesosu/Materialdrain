package tools.senko.materialdrain.hosts

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import tools.senko.materialdrain.auth.ApiKeySource
import tools.senko.materialdrain.auth.SessionManager
import tools.senko.materialdrain.provider.PIXELDRAIN_PROVIDER_ID
import tools.senko.materialdrain.provider.ProviderConfigStore
import tools.senko.materialdrain.provider.StoredProvider
import tools.senko.materialdrain.provider.api.AuthType
import tools.senko.materialdrain.provider.api.ConfigUpdates
import tools.senko.materialdrain.provider.api.GenericRestConfig
import tools.senko.materialdrain.provider.api.ProviderKind
import tools.senko.materialdrain.provider.api.S3Config
import tools.senko.materialdrain.provider.api.SmbAuthMode
import tools.senko.materialdrain.provider.api.SmbConfig
import tools.senko.materialdrain.provider.api.WebDavConfig
import tools.senko.materialdrain.ui.LocalReduceMotion
import tools.senko.materialdrain.ui.components.AppMenu
import tools.senko.materialdrain.ui.components.AppMenuDefaults
import tools.senko.materialdrain.ui.components.AppMenuItem
import tools.senko.materialdrain.ui.components.FloatingCardShape
import tools.senko.materialdrain.ui.components.MenuEdge
import tools.senko.materialdrain.ui.components.MenuSide
import tools.senko.materialdrain.ui.components.floatingCardBorder
import tools.senko.materialdrain.ui.components.floatingCardColor

/** Whether a host has a sign-in, shown next to its name in the switcher. */
enum class HostStatus(val label: String) {
    SIGNED_IN("Signed in"),
    NOT_SIGNED_IN("Not signed in"),
    NO_SIGN_IN_NEEDED("No sign-in needed")
}

/**
 * One host in the switcher. [detail] is the more specific line, e.g. the user the host is signed in as; [address] where
 * the host is, e.g. "nas.local/Documents", null when its config has none yet.
 */
data class HostOption(
    val id: String,
    val name: String,
    val kind: ProviderKind,
    val status: HostStatus,
    val detail: String,
    val address: String?
) {
    val icon: ImageVector
        get() = when (kind) {
            ProviderKind.PIXELDRAIN -> Icons.Filled.Cloud
            ProviderKind.WEBDAV -> Icons.Filled.FolderShared
            ProviderKind.S3 -> Icons.Filled.Storage
            ProviderKind.GENERIC_REST -> Icons.Filled.Dns
            ProviderKind.SMB -> Icons.Filled.Share
        }

    val kindLabel: String
        get() = when (kind) {
            ProviderKind.PIXELDRAIN -> "Pixeldrain"
            ProviderKind.WEBDAV -> "WebDAV"
            ProviderKind.S3 -> "S3"
            ProviderKind.GENERIC_REST -> "REST API"
            ProviderKind.SMB -> "SMB"
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
        },
        address = "pixeldrain.com"
    )
    return listOf(pixeldrain) + configStore.providers.value.map { it.toHostOption(configStore) }
}

private fun StoredProvider.toHostOption(configStore: ProviderConfigStore): HostOption {
    val (kind, needsSignIn) = when (val config = config) {
        is GenericRestConfig -> ProviderKind.GENERIC_REST to (config.auth.type != AuthType.NONE)
        is WebDavConfig -> ProviderKind.WEBDAV to true
        is S3Config -> ProviderKind.S3 to true
        // A share can be open to guests or anonymous: then there's nothing to sign in to
        is SmbConfig -> ProviderKind.SMB to (config.auth == SmbAuthMode.CREDENTIALS)
    }
    val creds = configStore.credentials(id)
    val signedIn = creds.apiKey.isNotBlank() || creds.loginToken != null || creds.hasPassword
    val status = when {
        !needsSignIn -> HostStatus.NO_SIGN_IN_NEEDED
        signedIn -> HostStatus.SIGNED_IN
        else -> HostStatus.NOT_SIGNED_IN
    }
    val address = when (val config = config) {
        is GenericRestConfig -> ConfigUpdates.hostOf(config.baseUrl)
        is WebDavConfig -> ConfigUpdates.hostOf(config.baseUrl)
        is S3Config -> ConfigUpdates.hostOf(config.endpoint)?.let { host -> config.bucket.takeIf { it.isNotBlank() }?.let { "$host/$it" } ?: host }
        is SmbConfig -> config.host.trim().takeIf { it.isNotEmpty() }?.let { host -> "$host/${config.share.trim('/', '\\')}" }
    }
    return HostOption(
        id = id,
        name = config.name,
        kind = kind,
        status = status,
        detail = when {
            status == HostStatus.SIGNED_IN && creds.username.isNotBlank() -> "Signed in as ${creds.username}"
            else -> status.label
        },
        address = address
    )
}

/** Green: the host answers. Grey: not checked yet, or no address to check. Red: it doesn't answer. */
private val ReachableColor = Color(0xFF2E7D32)
private val UnreachableColor = Color(0xFFC62828)

@Composable
private fun Reachability.color(): Color = when (this) {
    Reachability.REACHABLE -> ReachableColor
    Reachability.UNREACHABLE -> UnreachableColor
    Reachability.UNKNOWN, Reachability.OFFLINE -> MaterialTheme.colorScheme.outline
}

/** The symbol in the status badge, so the state doesn't hang on the colour alone. */
private val Reachability.symbol: ImageVector
    get() = when (this) {
        Reachability.REACHABLE -> Icons.Filled.Check
        Reachability.UNREACHABLE -> Icons.Filled.Close
        Reachability.UNKNOWN -> Icons.Filled.QuestionMark
        Reachability.OFFLINE -> Icons.Filled.CloudOff
    }

/** What the switcher says about a host's last check, e.g. "Reachable · 84 ms". */
private fun HostCheck?.label(): String = when {
    this == null -> "Checking…"
    checking && checkedAtMillis == 0L -> "Checking…"
    reachability == Reachability.REACHABLE -> latencyMillis?.let { "Reachable · $it ms" } ?: "Reachable"
    reachability == Reachability.UNREACHABLE -> message ?: "Not reachable"
    reachability == Reachability.OFFLINE -> "This device is offline"
    else -> message ?: "Not checked yet"
}

private val SwitcherHeight = 40.dp
private val MenuWidth = 320.dp

/**
 * The host the browser is using, shown in the top bar: a floating card with the host's icon, its status badge (see
 * [StatusBadge]) and its name. Tapping it opens the list of hosts under it, in the same floating card look as the sort
 * menus: each with where it is, whether it answers (checked ahead of time by [HostHealth], so it's known before the
 * list opens) and whether it's signed in. Choosing one makes it the active host; the gear, or "Sign in", opens its own
 * settings.
 */
@Composable
fun HostSwitcher(
    hosts: List<HostOption>,
    activeId: String,
    checks: Map<String, HostCheck>,
    onSelect: (String) -> Unit,
    onOpenHostSettings: (String) -> Unit,
    onManage: () -> Unit,
    onOpened: () -> Unit,
    onRefresh: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val active = hosts.firstOrNull { it.id == activeId } ?: hosts.first()
    val activeCheck = checks[active.id]
    val chevronTurn by animateFloatAsState(if (expanded && !LocalReduceMotion.current) 180f else 0f, label = "chevron")

    Box {
        Surface(
            onClick = {
                expanded = true
                onOpened()
            },
            shape = FloatingCardShape,
            color = floatingCardColor(),
            border = floatingCardBorder(),
            contentColor = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .height(SwitcherHeight)
                .widthIn(min = 150.dp, max = 240.dp)
                .semantics { contentDescription = "Host: ${active.name}, ${activeCheck.label()}. Tap to switch hosts" }
        ) {
            Row(
                modifier = Modifier.padding(start = 10.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HostIcon(active, activeCheck, size = 26.dp, badgeSize = 12.dp, highlighted = false)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = active.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(Modifier.width(4.dp))
                Icon(
                    Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp).rotate(chevronTurn)
                )
            }
        }

        AppMenu(
            expanded = expanded,
            onDismiss = { expanded = false },
            side = MenuSide.Below,
            edge = MenuEdge.Center,
            minWidth = MenuWidth
        ) {
            MenuHeader(checks.values, onRefresh)
            hosts.forEach { host ->
                HostRow(
                    host = host,
                    check = checks[host.id],
                    active = host.id == activeId,
                    onClick = {
                        expanded = false
                        onSelect(host.id)
                    },
                    onOpenSettings = {
                        expanded = false
                        onOpenHostSettings(host.id)
                    }
                )
            }
            Spacer(Modifier.height(4.dp))
            AppMenuItem(
                text = "Manage hosts",
                leadingIcon = Icons.Filled.Settings,
                onClick = {
                    expanded = false
                    onManage()
                }
            )
        }
    }
}

/** "Hosts", when they were last checked, and a button to check them all again now. */
@Composable
private fun MenuHeader(checks: Collection<HostCheck>, onRefresh: () -> Unit) {
    // Ticks along while the menu is open, so "1 min ago" doesn't stay "just now"
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(15_000)
        }
    }
    val checking = checks.any { it.checking }
    val lastChecked = checks.maxOfOrNull { it.checkedAtMillis }?.takeIf { it > 0 }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Hosts", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                when {
                    checking -> "Checking…"
                    lastChecked != null -> "Checked ${ago(now - lastChecked)}"
                    else -> "Not checked yet"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onRefresh, enabled = !checking) {
            if (checking) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Filled.Refresh, contentDescription = "Check the hosts again")
            }
        }
    }
}

private fun ago(millis: Long): String {
    val minutes = millis / 60_000
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "$minutes min ago"
        else -> "${minutes / 60} h ago"
    }
}

/**
 * A host in the list: its icon with the status badge, its name, what and where it is, and whether it answers and is
 * signed in. The active host has the tinted highlight of the menus' current choice.
 */
@Composable
private fun HostRow(
    host: HostOption,
    check: HostCheck?,
    active: Boolean,
    onClick: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    val variant = MaterialTheme.colorScheme.onSurfaceVariant
    val reachability = check?.reachability ?: Reachability.UNKNOWN
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppMenuDefaults.ItemShape)
            .background(if (active) accent.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)
    ) {
        HostIcon(host, check, size = 40.dp, badgeSize = 16.dp, highlighted = active)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                host.name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (active) accent else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                listOfNotNull(host.kindLabel, host.address).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = variant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${check.label()} · ${host.detail}",
                style = MaterialTheme.typography.labelSmall,
                color = if (reachability == Reachability.UNREACHABLE) UnreachableColor else variant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (host.status == HostStatus.NOT_SIGNED_IN) {
            TextButton(onClick = onOpenSettings) { Text("Sign in") }
        } else {
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings of ${host.name}", tint = variant)
            }
        }
    }
}

/** The host's kind in a circle, with its [StatusBadge] on the lower corner, like a contact's presence. */
@Composable
private fun HostIcon(host: HostOption, check: HostCheck?, size: Dp, badgeSize: Dp, highlighted: Boolean) {
    val colors = MaterialTheme.colorScheme
    Box(modifier = Modifier.size(size)) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .background(if (highlighted) colors.primary.copy(alpha = 0.2f) else colors.secondaryContainer)
        ) {
            Icon(
                host.icon,
                contentDescription = null,
                tint = if (highlighted) colors.primary else colors.onSecondaryContainer,
                modifier = Modifier.size(size * 0.55f)
            )
        }
        StatusBadge(
            check,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(badgeSize)
        )
    }
}

/**
 * Whether the host answers: a tick on green, a cross on red, a question mark on grey (not checked, no address) or a
 * crossed-out cloud (the device is offline). While a check runs, a thin ring spins around it.
 */
@Composable
private fun StatusBadge(check: HostCheck?, modifier: Modifier = Modifier) {
    val reachability = check?.reachability ?: Reachability.UNKNOWN
    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .matchParentSize()
                .clip(CircleShape)
                .background(reachability.color())
                // Keeps the badge apart from the icon under it
                .border(1.5.dp, MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
        ) {
            Icon(reachability.symbol, contentDescription = null, tint = Color.White, modifier = Modifier.matchParentSize().padding(3.dp))
        }
        if (check == null || check.checking) {
            CircularProgressIndicator(strokeWidth = 1.5.dp, modifier = Modifier.matchParentSize())
        }
    }
}
