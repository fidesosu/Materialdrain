package tools.senko.materialdrain.preferences

import tools.senko.materialdrain.ui.theme.AppShapes
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import tools.senko.materialdrain.ui.icons.automirrored.filled.KeyboardArrowLeft
import tools.senko.materialdrain.ui.icons.automirrored.filled.KeyboardArrowRight
import tools.senko.materialdrain.ui.icons.automirrored.filled.OpenInNew
import tools.senko.materialdrain.ui.icons.filled.AccountBalanceWallet
import tools.senko.materialdrain.ui.icons.filled.AccountCircle
import tools.senko.materialdrain.ui.icons.filled.Folder
import tools.senko.materialdrain.ui.icons.automirrored.filled.InsertDriveFile
import tools.senko.materialdrain.ui.icons.filled.Key
import tools.senko.materialdrain.ui.icons.filled.Language
import tools.senko.materialdrain.ui.icons.filled.Refresh
import tools.senko.materialdrain.ui.icons.filled.Smartphone
import tools.senko.materialdrain.ui.icons.filled.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.drawBehind
import tools.senko.materialdrain.ui.components.AppMenu
import tools.senko.materialdrain.ui.components.AppMenuDivider
import tools.senko.materialdrain.ui.components.AppMenuItem
import tools.senko.materialdrain.ui.icons.filled.Image
import tools.senko.materialdrain.ui.icons.filled.Crop
import tools.senko.materialdrain.ui.icons.filled.Delete
import tools.senko.materialdrain.ui.icons.filled.Edit
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import tools.senko.materialdrain.auth.ApiKeySource
import tools.senko.materialdrain.provider.pixeldrain.internal.UserInfo
import tools.senko.materialdrain.ui.components.NumberOdometer
import tools.senko.materialdrain.ui.components.OdometerText
import tools.senko.materialdrain.util.formatSize
import tools.senko.materialdrain.util.parseDateTime
import java.text.NumberFormat
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency
import java.util.Locale

/** Where pixeldrain lets the password, two-factor authentication, the e-mail addresses and deleting the account be changed. */
internal const val WEBSITE_SETTINGS_URL = "https://pixeldrain.com/user/settings"

/** Where pixeldrain lets the sessions and API keys be managed. */
internal const val WEBSITE_API_KEYS_URL = "https://pixeldrain.com/user/api_keys"

/** The corners of the account page's cards, the same as the file details' cards. */
internal val AccountCardShape = AppShapes.Card

/**
 * The Account page. Logged out (and without an API key) it's the login; with one it's the account itself: who it is and
 * its plan at the top, then how much of its storage and transfer is used, its statistics, and a card for each part of it
 * that can be changed (profile, security, sharing, billing, the file viewer), with what was removed from it, how this
 * device is signed in and deleting it at the end.
 */
@Composable
fun SettingsEnvironment.AccountPage() {
    val state by accountViewModel.uiState.collectAsState()
    val context = LocalContext.current
    LaunchedEffect(Unit) { accountViewModel.onPageShown() }
    // The checkout of an invoice opens in the browser
    LaunchedEffect(Unit) { accountViewModel.openLinks.collect { openLink(context, it) } }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        if (!state.hasKey) {
            SignedOutHero()
            AccountCard(title = "Log in") { Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { LoginSection() } }
            AccountCard(title = "Or use an API key") { Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { ApiKeySection() } }
            return@Column
        }

        when (val user = state.user) {
            Loadable.Loading -> Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is Loadable.Failed -> AccountCard(title = "Your account") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    Text(user.message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = { accountViewModel.refresh() }) { Text("Try again") }
                }
            }
            is Loadable.Loaded -> {
                AccountHero(user.data, onRefresh = { accountViewModel.refresh() })
                if (!user.data.canUpload) {
                    WarningBanner("Verify an e-mail address to upload. New accounts need one before they can upload files.")
                }
                UsageCard(user.data)
                StatisticsCard(state, accountViewModel)
                ProfileCard(user.data, state, accountViewModel)
                SecurityCard(user.data)
                SharingCard(user.data, state, accountViewModel)
                BillingCard(user.data, state, accountViewModel)
                if (user.data.subscription.fileViewerBranding) BrandingCard(user.data, state, accountViewModel)
                ActivityCard(state, accountViewModel)
            }
        }
        AccountCard(title = "This device") { Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) { DeviceSection() } }
        if (state.user is Loadable.Loaded) DangerCard()
    }
}

// ---- The top of the page ----

/** Logged out: what the page is for, over the login. */
@Composable
private fun SignedOutHero() {
    HeroSurface {
        Icon(Icons.Filled.AccountCircle, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(64.dp))
        Spacer(Modifier.height(12.dp))
        Text("Your pixeldrain account", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
        Spacer(Modifier.height(4.dp))
        Text(
            "Log in to see your storage, statistics and billing here, and to change your account's settings.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
        )
    }
}

/** Who the account is: its picture, name and first e-mail address, with its plan and status as tags under them. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountHero(user: UserInfo, onRefresh: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val avatar = rememberAccountAvatar(user.id.ifBlank { user.username })
    // A little of the picture's colour, glowing out from behind it; the rest is the theme's
    val glowTarget = avatar.accent?.let { accentContainer(it, colors.primaryContainer, colors.primary) } ?: Color.Transparent
    val glow by animateColorAsState(glowTarget, label = "heroGlow")
    HeroSurface(glow = glow) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AvatarButton(user, avatar)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                SelectionContainer {
                    Text(user.username, style = MaterialTheme.typography.headlineSmall, color = colors.onPrimaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                user.emailAddresses.firstOrNull()?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.onPrimaryContainer.copy(alpha = 0.8f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            IconButton(onClick = onRefresh) { Icon(Icons.Filled.Refresh, contentDescription = "Refresh", tint = colors.onPrimaryContainer) }
        }
        Spacer(Modifier.height(12.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            HeroTag(user.subscription.name.ifBlank { "Free" })
            HeroTag(if (user.otpEnabled) "Two-factor on" else "Two-factor off")
            if (user.emailAddressPending.isNotBlank()) HeroTag("E-mail waiting for verification")
            if (user.isAdmin) HeroTag("Admin")
        }
    }
}

/** How big the picture is in the header. */
private val AvatarSize = 64.dp

/**
 * The account's picture (which moves when it's a GIF or an animated WebP), or its initial without one. Tapped, it offers
 * to choose a picture from the device, to move the square of it that's shown, or to remove it; a picture which isn't
 * square is cropped first (see AvatarCropper). The picture is only kept on this device (see AccountAvatarStore).
 */
@Composable
private fun AvatarButton(user: UserInfo, avatar: AvatarState) {
    val colors = MaterialTheme.colorScheme
    var menuOpen by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(avatar.choose) }
    avatar.cropping?.let { AvatarCropper(it, onKeep = avatar.keep, onCancel = avatar.cancelCrop) }
    Box {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(AvatarSize)
                .clip(AvatarShape)
                .background(colors.primary)
                .clickable(onClickLabel = "Change picture") { menuOpen = true }
        ) {
            val picture = avatar.picture
            if (picture != null) {
                AvatarPictureView(picture, contentDescription = "Your picture", modifier = Modifier.size(AvatarSize))
            } else {
                Text(user.username.firstOrNull()?.uppercase() ?: "?", style = MaterialTheme.typography.headlineMedium, color = colors.onPrimary)
            }
        }
        // A small pencil on its edge says it can be changed
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(22.dp)
                .clip(CircleShape)
                .background(colors.surface)
                .padding(2.dp)
                .clip(CircleShape)
                .background(colors.secondaryContainer)
        ) {
            Icon(Icons.Filled.Edit, contentDescription = null, tint = colors.onSecondaryContainer, modifier = Modifier.size(12.dp))
        }
        AppMenu(expanded = menuOpen, onDismiss = { menuOpen = false }) {
            Text(
                "Only kept on this device",
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
            AppMenuItem(
                text = "Choose a picture",
                leadingIcon = Icons.Filled.Image,
                onClick = {
                    menuOpen = false
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            )
            if (avatar.picture != null) {
                AppMenuItem(text = "Adjust crop", leadingIcon = Icons.Filled.Crop, onClick = { menuOpen = false; avatar.recrop() })
                AppMenuDivider()
                AppMenuItem(text = "Remove picture", leadingIcon = Icons.Filled.Delete, destructive = true, onClick = { menuOpen = false; avatar.remove() })
            }
        }
    }
}

/**
 * The header's background: the theme's container colour, fading into the cards' colour towards the bottom end, with
 * [glow] (the picture's colour, or transparent) shining out from behind the picture at the top start.
 */
@Composable
private fun HeroSurface(glow: Color = Color.Transparent, content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    val base = Brush.linearGradient(listOf(colors.primaryContainer, lerp(colors.primaryContainer, colors.surfaceContainerHigh, 0.6f)))
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppShapes.ExtraLarge)
            .background(base)
            .drawBehind {
                if (glow.alpha == 0f) return@drawBehind
                // Centred on the picture: the padding, then half the picture
                val centre = Offset((20.dp + AvatarSize / 2).toPx(), (20.dp + AvatarSize / 2).toPx())
                drawRect(Brush.radialGradient(listOf(glow, glow.copy(alpha = 0f)), center = centre, radius = size.width * 0.8f))
            }
            .padding(20.dp),
        content = content
    )
}

/** How big the picture is on the settings' account tile. */
private val TileAvatarSize = 48.dp

/**
 * The account at the top of the settings, which opens its page. A card like the other categories' tiles, but bigger and
 * with more in it: the picture, name and e-mail with the plan beside them, then how full the storage and this month's
 * transfer are, and the number of files and the credit. Logged out, it asks to log in. The account is loaded for it once
 * (see AccountViewModel.ensureLoaded), not each time the settings are opened.
 */
@Composable
internal fun SettingsEnvironment.AccountTile(onOpen: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val state by accountViewModel.uiState.collectAsState()
    LaunchedEffect(Unit) { accountViewModel.ensureLoaded() }
    val user = (state.user as? Loadable.Loaded)?.data?.takeIf { state.hasKey }
    val avatar = user?.let { rememberAccountAvatar(it.id.ifBlank { it.username }) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AccountCardShape)
            .background(colors.surfaceContainerLow)
            .clickable(onClickLabel = "Open your account", onClick = onOpen)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(TileAvatarSize).clip(AvatarShape).background(if (user != null) colors.primary else colors.primaryContainer)
            ) {
                val picture = avatar?.picture
                when {
                    picture != null -> AvatarPictureView(picture, contentDescription = null, modifier = Modifier.size(TileAvatarSize))
                    user != null -> Text(user.username.firstOrNull()?.uppercase() ?: "?", style = MaterialTheme.typography.titleLarge, color = colors.onPrimary)
                    else -> Icon(Icons.Filled.AccountCircle, contentDescription = null, tint = colors.onPrimaryContainer, modifier = Modifier.size(28.dp))
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                val title = when {
                    user != null -> user.username
                    state.hasKey -> "Your account"
                    else -> "Log in to pixeldrain"
                }
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val line = when {
                    user != null -> user.emailAddresses.firstOrNull() ?: "No e-mail address"
                    state.user is Loadable.Failed -> "Couldn't load the account"
                    state.hasKey -> "Loading…"
                    else -> "To see your storage, statistics and billing"
                }
                Text(line, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (user != null) {
                Text(
                    user.subscription.name.ifBlank { "Free" },
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onSecondaryContainer,
                    maxLines = 1,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .clip(AppShapes.Small)
                        .background(colors.secondaryContainer)
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = colors.onSurfaceVariant)
        }
        if (user != null) {
            val plan = user.subscription
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                TileMeter("Storage", user.storageSpaceUsed, plan.storageSpace, Modifier.weight(1f))
                // The account's own cap when it set one, else the plan's (as on the account's page)
                TileMeter("Transfer", user.monthlyTransferUsed, user.monthlyTransferCap.takeIf { it > 0 } ?: plan.monthlyTransferCap, Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
            val numbers = NumberFormat.getIntegerInstance()
            Text(
                "${numbers.format(user.fileCount)} files · ${formatEuro(user.balanceMicroEur)} credit",
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** A small meter of the account tile: what's used of [limit], or only the amount when there's no limit. */
@Composable
private fun TileMeter(label: String, used: Long, limit: Long, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(
                if (limit > 0) "${formatSize(used)} / ${formatSize(limit)}" else formatSize(used),
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1
            )
        }
        Spacer(Modifier.height(4.dp))
        if (limit > 0) {
            val fraction = (used.toDouble() / limit).toFloat().coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { fraction },
                color = if (fraction > 0.9f) colors.error else colors.primary,
                modifier = Modifier.fillMaxWidth().height(4.dp)
            )
        } else {
            // No limit: nothing to fill, a quiet line keeps the two alike
            HorizontalDivider(color = colors.outlineVariant, modifier = Modifier.padding(vertical = 1.5.dp))
        }
    }
}

@Composable
private fun HeroTag(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .clip(AppShapes.Small)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

@Composable
internal fun WarningBanner(message: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(AccountCardShape)
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Icon(Icons.Filled.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
        Spacer(Modifier.width(12.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
    }
}

// ---- Usage ----

/** How full the account is: its storage and this month's transfer as meters, and its counts as tiles. */
@Composable
private fun UsageCard(user: UserInfo) {
    val plan = user.subscription
    AccountCard(title = "Storage and transfer") {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            UsageMeter("Files", user.storageSpaceUsed, plan.storageSpace)
            if (plan.filesystemAccess || user.filesystemStorageUsed > 0) {
                UsageMeter("Filesystem", user.filesystemStorageUsed, plan.filesystemStorageLimit)
            }
            // The account's own cap when it set one, else the plan's
            val cap = user.monthlyTransferCap.takeIf { it > 0 } ?: plan.monthlyTransferCap
            UsageMeter("Transfer this month", user.monthlyTransferUsed, cap)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp)) {
            val numbers = NumberFormat.getIntegerInstance()
            CountTile(Icons.AutoMirrored.Filled.InsertDriveFile, numbers.format(user.fileCount), "Files")
            CountTile(Icons.Filled.Folder, numbers.format(user.filesystemNodeCount), "In filesystem")
            CountTile(Icons.Filled.AccountBalanceWallet, formatEuro(user.balanceMicroEur), "Credit")
        }
    }
}

/** What is used of a limit, as a bar; a [limit] of 0 or less is none, and only the amount is shown. */
@Composable
private fun UsageMeter(label: String, used: Long, limit: Long) {
    Column {
        Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                if (limit > 0) "${formatSize(used)} of ${formatSize(limit)}" else "${formatSize(used)} · no limit",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(6.dp))
        if (limit > 0) {
            val fraction = (used.toDouble() / limit).toFloat().coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { fraction },
                color = if (fraction > 0.9f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth().height(8.dp)
            )
        } else {
            // Without a limit there's nothing to fill: a quiet line keeps the rows alike
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun RowScope.CountTile(icon: ImageVector, value: String, label: String) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clip(AppShapes.Large)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(6.dp))
        OdometerText(value, style = MaterialTheme.typography.titleMedium, spec = NumberOdometer)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ---- This device ----

/** How this device is signed in: the login (with Log out) or an API key, the form to log in, and the API key field. */
@Composable
private fun SettingsEnvironment.DeviceSection() {
    val authState by authViewModel.uiState.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (authState.activeSource) {
            ApiKeySource.LOGIN -> {
                AccountRow(Icons.Filled.Smartphone, "Logged in as", authState.loggedInUser.orEmpty(), padded = false) {
                    OutlinedButton(onClick = { authViewModel.logout() }, enabled = !authState.isBusy) {
                        if (authState.isBusy) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Log out")
                    }
                }
                ApiKeySection(showMessages = true)
            }
            ApiKeySource.MANUAL -> {
                AccountRow(Icons.Filled.Key, "Signed in with", "An API key", padded = false)
                // The login form under it shows the messages
                ApiKeySection()
                Text("Or log in", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
                LoginSection()
            }
            // Logged out, the login and the API key have cards of their own (see AccountPage)
            ApiKeySource.NONE -> Text("Not signed in.", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** Username/password login, e-mail login link and second factor. */
@Composable
fun SettingsEnvironment.LoginSection() {
    val authState by authViewModel.uiState.collectAsState()
    var username by rememberSaveable { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var totp by rememberSaveable { mutableStateOf("") }
    var pastedLink by rememberSaveable { mutableStateOf("") }

    // Never keep credentials around once they were used
    LaunchedEffect(authState.loggedInUser) {
        if (authState.loggedInUser != null) {
            password = ""
            totp = ""
            pastedLink = ""
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        val canSubmit = !authState.isBusy && username.isNotBlank()
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Username or e-mail address") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next)
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                if (canSubmit && password.isNotEmpty()) authViewModel.login(username, password, totp)
            })
        )
        if (authState.otpRequired) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = totp,
                onValueChange = { totp = it.filter { c -> c.isDigit() }.take(6) },
                label = { Text("One-time password (2FA)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = { authViewModel.login(username, password, totp) },
                enabled = canSubmit && password.isNotEmpty(),
                modifier = Modifier.weight(1f)
            ) {
                if (authState.isBusy) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Log in")
            }
            TextButton(
                onClick = { authViewModel.login(username, "", "") },
                enabled = canSubmit
            ) { Text("E-mail me a login link") }
        }

        if (authState.loginLinkSent) {
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = pastedLink,
                onValueChange = { pastedLink = it },
                label = { Text("Login link from the e-mail") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { authViewModel.completeLinkLogin(username, pastedLink, password, totp) },
                enabled = canSubmit && pastedLink.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Finish login") }
        }
        AuthMessages()
    }
}

/** The API key entered by hand, with its own Save button. */
@Composable
fun SettingsEnvironment.ApiKeySection(showMessages: Boolean = false) {
    val authState by authViewModel.uiState.collectAsState()
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = apiKeyInput,
                onValueChange = { onApiKeyInputChange(it) },
                label = { Text("Pixeldrain API key") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace)
            )
            OutlinedButton(onClick = { authViewModel.saveApiKey(apiKeyInput) }) { Text("Save") }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = when (authState.activeSource) {
                ApiKeySource.LOGIN -> "Your login is used for requests. This API key is used again after you log out."
                ApiKeySource.MANUAL -> "This API key is used for requests. Logging in replaces it while you are logged in."
                ApiKeySource.NONE -> "Make one on pixeldrain.com under API keys, paste it here and press Save."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth()
        )
        // Where a login form is on the page too, it shows them
        if (showMessages) AuthMessages()
    }
}

@Composable
private fun SettingsEnvironment.AuthMessages() {
    val authState by authViewModel.uiState.collectAsState()
    authState.errorMessage?.let {
        Spacer(modifier = Modifier.height(8.dp))
        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth())
    }
    authState.infoMessage?.let {
        Spacer(modifier = Modifier.height(8.dp))
        Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth())
    }
}

// ---- Building blocks of the account's cards ----

/** A card of the page: its [title] (and an [action] beside it), then its rows. */
@Composable
internal fun AccountCard(
    title: String,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AccountCardShape)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f).padding(top = 8.dp, bottom = 4.dp)
            )
            action?.invoke()
        }
        content()
    }
}

/** A smaller title inside a card, above a group of its rows. */
@Composable
internal fun CardSubtitle(text: String, action: (@Composable () -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp)) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        action?.invoke()
    }
}

@Composable
internal fun CardDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        // Under the text, not the icons, like the file details
        modifier = Modifier.padding(start = 56.dp, end = 16.dp)
    )
}

/** A short explanation inside a card. */
@Composable
internal fun CardNote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
    )
}

/** The buttons at the bottom of a card, wrapping onto a second line when they don't fit. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CardButtons(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
    ) { content() }
}

/**
 * One detail of the account: its icon, its name over its value, an optional line under it, and whatever [trailing] is
 * at its end (a button, a switch).
 */
@Composable
internal fun AccountRow(
    icon: ImageVector,
    label: String,
    value: String,
    supporting: String? = null,
    monospace: Boolean = false,
    padded: Boolean = true,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = if (padded) 16.dp else 0.dp, end = if (padded) 8.dp else 0.dp, top = 8.dp, bottom = 8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SelectionContainer {
                Text(value, style = MaterialTheme.typography.bodyLarge, fontFamily = if (monospace) FontFamily.Monospace else null)
            }
            supporting?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        trailing?.invoke()
    }
}

/** A setting which is on or off, as a row with a switch; the whole row turns it. */
@Composable
internal fun AccountSwitchRow(icon: ImageVector, title: String, summary: String, checked: Boolean, enabled: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onChange(!checked) }
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

/**
 * What [loadable] holds, or a spinner while it loads, or why it couldn't be: for what only the website allows, with a
 * link to it.
 */
@Composable
internal fun <T> LoadableRows(loadable: Loadable<T>, content: @Composable (T) -> Unit) {
    when (loadable) {
        Loadable.Loading -> Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        }
        is Loadable.Failed -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp)) {
            Text(
                loadable.message,
                style = MaterialTheme.typography.bodySmall,
                color = if (loadable.websiteOnly) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f)
            )
            if (loadable.websiteOnly) WebsiteLink(WEBSITE_SETTINGS_URL, "This")
        }
        is Loadable.Loaded -> content(loadable.data)
    }
}

/**
 * For what pixeldrain only lets its website do: an icon which, tapped, first says where it goes, then opens [url] in the
 * browser. [what] names what is done there, as the start of a sentence ("Changing your password").
 */
@Composable
internal fun WebsiteLink(url: String, what: String) {
    val context = LocalContext.current
    var asking by remember { mutableStateOf(false) }
    IconButton(onClick = { asking = true }) {
        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "$what, on pixeldrain.com", tint = MaterialTheme.colorScheme.primary)
    }
    if (asking) {
        AlertDialog(
            onDismissRequest = { asking = false },
            icon = { Icon(Icons.Filled.Language, contentDescription = null) },
            title = { Text("Open pixeldrain.com?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("$what is only possible on the pixeldrain website, pixeldrain doesn't let apps do it. This opens")
                    Text(
                        url.removePrefix("https://"),
                        style = MaterialTheme.typography.bodyLarge,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text("in your browser. Log in there if it asks you to.")
                }
            },
            confirmButton = { Button(onClick = { asking = false; openLink(context, url) }) { Text("Open") } },
            dismissButton = { TextButton(onClick = { asking = false }) { Text("Cancel") } }
        )
    }
}

/** A month, with buttons to the one before and after (not past the current one). */
@Composable
internal fun MonthSwitcher(month: YearMonth, onChange: (YearMonth) -> Unit) {
    val isCurrent = !month.isBefore(YearMonth.now(ZoneOffset.UTC))
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onChange(month.minusMonths(1)) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Month before")
        }
        Text(month.format(MonthFormat), style = MaterialTheme.typography.labelLarge)
        IconButton(onClick = { onChange(month.plusMonths(1)) }, enabled = !isCurrent) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Month after")
        }
    }
}

internal val MonthFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM yyyy")

/** "€12.50" from micro euros (1000000 = €1). */
internal fun formatEuro(microEur: Long): String {
    val format = NumberFormat.getCurrencyInstance(Locale.getDefault()).apply { currency = Currency.getInstance("EUR") }
    return format.format(microEur / 1_000_000.0)
}

/** "€0.05" with a sign, for the parts of a transaction. */
internal fun formatEuroSigned(microEur: Long): String = (if (microEur > 0) "+" else "") + formatEuro(microEur)

/** "3 Oct 2026, 14:05" for an API date; the text itself when it can't be read. */
internal fun formatAccountDate(date: String?, withTime: Boolean = true): String {
    val instant = parseDateTime(date) ?: return date.orEmpty()
    val formatter = if (withTime) DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT) else DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    return formatter.withZone(java.time.ZoneId.systemDefault()).format(instant)
}

/** "3 days ago"; null when the date can't be read. */
internal fun relativeAccountTime(date: String?): String? = parseDateTime(date)?.let {
    DateUtils.getRelativeTimeSpanString(it.toEpochMilli(), System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()
}

internal fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}

// ---- Previews ----
// What's here is drawn by the account page's previews, with a made-up account: SettingsAccountPreview (signed in, every
// card) and SettingsAccountSignedOutPreview in SettingsScreen.kt, and the account's tile in SettingsHomePreview.
