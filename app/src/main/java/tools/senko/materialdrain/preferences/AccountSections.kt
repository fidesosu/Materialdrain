package tools.senko.materialdrain.preferences

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Handshake
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.Password
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.serialization.json.JsonPrimitive
import tools.senko.materialdrain.provider.pixeldrain.internal.ActivityEvent
import tools.senko.materialdrain.provider.pixeldrain.internal.Transaction
import tools.senko.materialdrain.provider.pixeldrain.internal.UserInfo
import tools.senko.materialdrain.ui.components.ConfirmDialog
import tools.senko.materialdrain.ui.components.TextInputDialog
import tools.senko.materialdrain.util.formatSize

/** A small spinner in a button while what it started runs. */
@Composable
private fun ButtonSpinner() = CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)

// ---- Profile ----

/** The username, which can be changed here, and the e-mail addresses, which are managed on the website. */
@Composable
internal fun ProfileCard(user: UserInfo, state: AccountUiState, viewModel: AccountViewModel) {
    var renaming by remember { mutableStateOf(false) }
    val busy = AccountTask.PROFILE in state.busy

    AccountCard(title = "Profile") {
        AccountRow(Icons.Filled.Person, "Username", user.username) {
            TextButton(onClick = { renaming = true }, enabled = !busy) { if (busy) ButtonSpinner() else Text("Change") }
        }
        user.emailAddresses.forEachIndexed { index, address ->
            CardDivider()
            AccountRow(Icons.Filled.Email, if (index == 0) "E-mail" else "Also e-mail", address)
        }
        if (user.emailAddressPending.isNotBlank()) {
            CardDivider()
            AccountRow(Icons.Filled.MarkEmailUnread, "Waiting for verification", user.emailAddressPending, supporting = "Open the link in the e-mail to add it")
        }
        CardDivider()
        AccountRow(Icons.Filled.AlternateEmail, "E-mail addresses", "Add or remove them", supporting = "On pixeldrain.com") {
            WebsiteLink(WEBSITE_SETTINGS_URL, "Adding and removing e-mail addresses")
        }
    }

    if (renaming) {
        TextInputDialog(
            title = "Change username",
            label = "Username",
            confirmLabel = "Change",
            initialValue = user.username,
            supportingText = "Log in with the new username from now on. Links to your files stay the same.",
            onConfirm = { viewModel.changeUsername(it); renaming = false },
            onDismiss = { renaming = false }
        )
    }
}

// ---- Security ----

/** Two-factor authentication, the password and the API keys, which pixeldrain only lets the website change. */
@Composable
internal fun SecurityCard(user: UserInfo) {
    AccountCard(title = "Security") {
        AccountRow(
            Icons.Filled.Security,
            "Two-factor authentication",
            if (user.otpEnabled) "On" else "Off",
            supporting = "Turned on or off on pixeldrain.com"
        ) {
            WebsiteLink(WEBSITE_SETTINGS_URL, "Turning two-factor authentication on or off")
        }
        CardDivider()
        AccountRow(Icons.Filled.Password, "Password", "Change it", supporting = "On pixeldrain.com") {
            WebsiteLink(WEBSITE_SETTINGS_URL, "Changing your password")
        }
        CardDivider()
        AccountRow(Icons.Filled.Key, "Sessions and API keys", "See, make and remove them", supporting = "On pixeldrain.com") {
            WebsiteLink(WEBSITE_API_KEYS_URL, "Managing your sessions and API keys")
        }
    }
}

// ---- Sharing ----

private enum class SharingDialog { EMBED, CAP, AFFILIATE }

/** How others get at the files: hotlinking, the file viewer, embedding, and the cap on the account's transfer. */
@Composable
internal fun SharingCard(user: UserInfo, state: AccountUiState, viewModel: AccountViewModel) {
    var dialog by remember { mutableStateOf<SharingDialog?>(null) }
    val busy = AccountTask.SHARING in state.busy
    val planCap = user.subscription.monthlyTransferCap

    AccountCard(title = "Sharing") {
        AccountSwitchRow(
            Icons.Filled.Speed,
            "Hotlinking",
            "Downloads of your files use your premium transfer, so they're fast and work outside the file viewer",
            checked = user.hotlinkingEnabled,
            enabled = !busy,
            onChange = viewModel::setHotlinking
        )
        CardDivider()
        AccountSwitchRow(
            Icons.Filled.Visibility,
            "Skip the file viewer",
            "Links to your files open the file itself instead of pixeldrain's page",
            checked = user.skipFileViewer,
            enabled = !busy,
            onChange = viewModel::setSkipFileViewer
        )
        CardDivider()
        AccountRow(
            Icons.Filled.Code,
            "Sites which can embed your files",
            user.fileEmbedDomains.split(' ').filter { it.isNotBlank() }.joinToString(", ").ifBlank { "None" }
        ) {
            TextButton(onClick = { dialog = SharingDialog.EMBED }, enabled = !busy) { Text("Edit") }
        }
        CardDivider()
        AccountRow(
            Icons.Filled.DataUsage,
            "Monthly transfer cap",
            if (user.monthlyTransferCap > 0) formatSize(user.monthlyTransferCap) else "The plan's",
            supporting = if (planCap > 0) "Your plan allows ${formatSize(planCap)} a month" else "Your plan has no cap"
        ) {
            TextButton(onClick = { dialog = SharingDialog.CAP }, enabled = !busy) { Text("Edit") }
        }
        CardDivider()
        AccountRow(
            Icons.Filled.Handshake,
            "Affiliate",
            user.affiliateUserName.ifBlank { "None" },
            supporting = "The account which gets a reward for what you buy"
        ) {
            TextButton(onClick = { dialog = SharingDialog.AFFILIATE }, enabled = !busy) { Text("Edit") }
        }
    }

    when (dialog) {
        SharingDialog.EMBED -> EditableTextDialog(
            title = "Sites which can embed your files",
            label = "Domains",
            initialValue = user.fileEmbedDomains.split(' ').filter { it.isNotBlank() }.joinToString("\n"),
            supportingText = "One per line, like example.com. Leave it empty so no site can.",
            singleLine = false,
            onConfirm = { viewModel.setEmbedDomains(it); dialog = null },
            onDismiss = { dialog = null }
        )
        SharingDialog.CAP -> TransferCapDialog(
            currentBytes = user.monthlyTransferCap,
            planCapBytes = planCap,
            onConfirm = { viewModel.setTransferCap(it); dialog = null },
            onDismiss = { dialog = null }
        )
        SharingDialog.AFFILIATE -> EditableTextDialog(
            title = "Affiliate",
            label = "Username",
            initialValue = user.affiliateUserName,
            supportingText = "Leave it empty to have none.",
            onConfirm = { viewModel.setAffiliate(it); dialog = null },
            onDismiss = { dialog = null }
        )
        null -> {}
    }
}

// ---- Plan and billing ----

/** The statuses of an invoice which can still be paid. */
private val UnpaidInvoiceStatuses = setOf("open")

/** The prepaid plans which can be switched to from the app. */
private val PrepaidPlans = listOf("prepaid" to "Prepaid", "prepaid_lite" to "Prepaid Lite")

/** The plan and what it includes, the credit, the invoices and a month of transactions. */
@Composable
internal fun BillingCard(user: UserInfo, state: AccountUiState, viewModel: AccountViewModel) {
    val plan = user.subscription
    var addingCredit by remember { mutableStateOf(false) }
    var switchingTo by remember { mutableStateOf<Pair<String, String>?>(null) }
    val creditBusy = AccountTask.CREDIT in state.busy
    val planBusy = AccountTask.PLAN in state.busy

    AccountCard(title = "Plan and billing") {
        AccountRow(Icons.Filled.WorkspacePremium, "Plan", plan.name.ifBlank { "Free" }, supporting = plan.type.takeIf { it.isNotBlank() && !it.equals(plan.name, ignoreCase = true) })
        CardDivider()
        AccountRow(Icons.Filled.AccountBalanceWallet, "Credit", formatEuro(user.balanceMicroEur)) {
            FilledTonalButton(onClick = { addingCredit = true }, enabled = !creditBusy) { if (creditBusy) ButtonSpinner() else Text("Add credit") }
        }
        CardDivider()
        // The plan's limit per upload (subscription.file_size_limit), not the size of a file on the account
        AccountRow(
            Icons.Filled.Straighten,
            "Upload size limit",
            if (plan.fileSizeLimit > 0) formatSize(plan.fileSizeLimit) else "No limit",
            supporting = "The biggest a single file you upload can be".takeIf { plan.fileSizeLimit > 0 }
        )
        CardDivider()
        AccountRow(Icons.Filled.Timer, "Files are removed", if (plan.fileExpiryDays > 0) "${plan.fileExpiryDays} days after they were last viewed" else "Never")
        CardDivider()
        AccountRow(Icons.Filled.Folder, "Filesystem", if (plan.filesystemAccess) "Included" else "Not included")
        if (plan.pricePerTbStorage > 0 || plan.pricePerTbBandwidth > 0) {
            CardDivider()
            AccountRow(
                Icons.Filled.Storage,
                "Prices",
                "${formatEuro(plan.pricePerTbStorage)} per TB stored · ${formatEuro(plan.pricePerTbBandwidth)} per TB transferred"
            )
        }
        val otherPlans = PrepaidPlans.filter { it.first != plan.id }
        if (otherPlans.isNotEmpty()) {
            CardButtons {
                otherPlans.forEach { option ->
                    OutlinedButton(onClick = { switchingTo = option }, enabled = !planBusy) { if (planBusy) ButtonSpinner() else Text("Switch to ${option.second}") }
                }
            }
        }

        CardSubtitle("Invoices")
        LoadableRows(state.invoices) { invoices ->
            if (invoices.isEmpty()) CardNote("No invoices yet.")
            invoices.forEachIndexed { index, invoice ->
                if (index > 0) CardDivider()
                val open = invoice.status.lowercase() in UnpaidInvoiceStatuses
                AccountRow(
                    Icons.Filled.Receipt,
                    formatAccountDate(invoice.time),
                    formatEuro(invoice.amount),
                    supporting = listOfNotNull(
                        invoiceStatusLabel(invoice.status).takeIf { it.isNotBlank() },
                        invoice.paymentMethod.ifBlank { invoice.paymentGateway }.takeIf { it.isNotBlank() },
                        invoice.vat.takeIf { it > 0 }?.let { "${formatEuro(it)} VAT" }
                    ).joinToString(" · ")
                ) {
                    if (open) TextButton(onClick = { viewModel.payInvoice(invoice.id) }) { Text("Pay") }
                }
            }
        }

        CardSubtitle("Transactions") { MonthSwitcher(state.transactionsMonth, viewModel::showTransactionsMonth) }
        LoadableRows(state.transactions) { transactions ->
            if (transactions.isEmpty()) CardNote("No transactions in ${state.transactionsMonth.format(MonthFormat)}.")
            transactions.forEachIndexed { index, transaction ->
                if (index > 0) CardDivider()
                AccountRow(Icons.AutoMirrored.Filled.ReceiptLong, formatAccountDate(transaction.time), "Balance ${formatEuro(transaction.newBalance)}", supporting = transactionParts(transaction))
            }
        }
    }

    if (addingCredit) {
        AddCreditDialog(
            initialCountry = user.checkoutCountry,
            initialMethod = user.checkoutProvider,
            hasOpenInvoice = state.invoices.dataOrNull.orEmpty().any { it.status.lowercase() in UnpaidInvoiceStatuses },
            onConfirm = { amount, method, country -> viewModel.addCredit(amount, method, country); addingCredit = false },
            onDismiss = { addingCredit = false }
        )
    }
    switchingTo?.let { (id, label) ->
        ConfirmDialog(
            title = "Switch to $label?",
            text = "Your plan's costs are then paid from your credit, which needs to be at least ${formatEuro(1_000_000)}. You have ${formatEuro(user.balanceMicroEur)}.",
            confirmLabel = "Switch",
            onConfirm = { viewModel.activatePlan(id, label); switchingTo = null },
            onDismiss = { switchingTo = null }
        )
    }
}

/** What an invoice's status means, in the words pixeldrain's website uses for it. */
private fun invoiceStatusLabel(status: String): String = when (status.lowercase()) {
    "open" -> "Waiting for payment"
    "processing" -> "Payment received, waiting for confirmations"
    "paid" -> "Paid"
    "expired" -> "Expired"
    "canceled" -> "Canceled"
    else -> status.replaceFirstChar { it.uppercase() }
}

/** "Deposit +€10.00 · Storage −€0.03 · …": the parts of a transaction which aren't zero. */
private fun transactionParts(transaction: Transaction): String? = listOfNotNull(
    transaction.depositAmount.takeIf { it != 0L }?.let { "Deposit ${formatEuroSigned(it)}" },
    transaction.subscriptionCharge.takeIf { it != 0L }?.let { "Plan ${formatEuroSigned(it)}" },
    transaction.storageCharge.takeIf { it != 0L }?.let { "Storage ${formatEuroSigned(it)} (${formatSize(transaction.storageUsed)})" },
    transaction.bandwidthCharge.takeIf { it != 0L }?.let { "Transfer ${formatEuroSigned(it)} (${formatSize(transaction.bandwidthUsed)})" },
    transaction.affiliateAmount.takeIf { it != 0L }?.let { "Affiliate ${formatEuroSigned(it)} (${transaction.affiliateCount})" }
).joinToString(" · ").ifBlank { null }

// ---- File viewer branding ----

/** How the file viewer looks for the account's files, for plans with branding. */
@Composable
internal fun BrandingCard(user: UserInfo, state: AccountUiState, viewModel: AccountViewModel) {
    var editing by remember { mutableStateOf(false) }
    val busy = AccountTask.BRANDING in state.busy
    val current = remember(user.fileViewerBranding) {
        user.fileViewerBranding.orEmpty().mapValues { (_, value) -> (value as? JsonPrimitive)?.content.orEmpty() }
    }
    val set = BrandingFields.filter { current[it.key].orEmpty().let { value -> value.isNotBlank() && value != "false" } }

    AccountCard(title = "File viewer") {
        if (set.isEmpty()) CardNote("Your files are shown on pixeldrain's own file viewer. Your plan lets you give it your own theme, images and links.")
        set.forEachIndexed { index, field ->
            if (index > 0) CardDivider()
            AccountRow(
                when {
                    field.isSwitch -> Icons.Filled.Block
                    field.key.endsWith("_link") -> Icons.Filled.Link
                    field.key.endsWith("_image") -> Icons.Filled.Brush
                    else -> Icons.AutoMirrored.Filled.Label
                },
                field.label,
                if (field.isSwitch) "Yes" else current[field.key].orEmpty(),
                monospace = field.key.endsWith("_image")
            )
        }
        CardButtons {
            OutlinedButton(onClick = { editing = true }, enabled = !busy) { if (busy) ButtonSpinner() else Text("Customize") }
        }
    }
    if (editing) {
        BrandingDialog(
            current = current,
            onConfirm = { viewModel.saveBranding(it); editing = false },
            onDismiss = { editing = false }
        )
    }
}

// ---- Activity ----

/** The files which were removed from the account in a month, and why. */
@Composable
internal fun ActivityCard(state: AccountUiState, viewModel: AccountViewModel) {
    AccountCard(title = "Removed files", action = { MonthSwitcher(state.activityMonth, viewModel::showActivityMonth) }) {
        LoadableRows(state.activity) { events ->
            if (events.isEmpty()) CardNote("Nothing was removed from your account in ${state.activityMonth.format(MonthFormat)}.")
            events.forEachIndexed { index, event ->
                if (index > 0) CardDivider()
                AccountRow(
                    when {
                        event.event.endsWith("expired") -> Icons.Filled.CalendarToday
                        event.event.endsWith("blocked") -> Icons.Filled.Block
                        else -> Icons.AutoMirrored.Filled.HelpOutline
                    },
                    "${activityLabel(event)} · ${formatAccountDate(event.time)}",
                    event.fileName.ifBlank { event.fileId },
                    supporting = event.fileRemovalReason.takeIf { it.isNotBlank() }?.replace('_', ' ')?.replaceFirstChar { it.uppercase() }
                )
            }
        }
    }
}

private fun activityLabel(event: ActivityEvent): String = when (event.event) {
    "file_instance_expired" -> "Expired"
    "file_instance_blocked" -> "Blocked"
    "file_instance_lost" -> "Lost"
    "filesystem_node_blocked" -> "Blocked in the filesystem"
    "filesystem_node_lost" -> "Lost from the filesystem"
    else -> event.event.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

// ---- Deleting the account ----

/** Deleting the account, which pixeldrain only lets the website do. */
@Composable
internal fun DangerCard() {
    AccountCard(title = "Delete account") {
        AccountRow(
            Icons.Filled.DeleteForever,
            "Delete my account",
            "On pixeldrain.com",
            supporting = "Every session is logged out, and the account and its files are deleted 7 days later. Logging in again before then cancels it."
        ) {
            WebsiteLink(WEBSITE_SETTINGS_URL, "Deleting your account")
        }
    }
}
