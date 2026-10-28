package tools.senko.materialdrain.settings.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import tools.senko.materialdrain.ui.icons.automirrored.filled.Label
import tools.senko.materialdrain.ui.icons.filled.AccountBalanceWallet
import tools.senko.materialdrain.ui.icons.filled.Add
import tools.senko.materialdrain.ui.icons.filled.Block
import tools.senko.materialdrain.ui.icons.filled.Brush
import tools.senko.materialdrain.ui.icons.filled.CalendarToday
import tools.senko.materialdrain.ui.icons.filled.Code
import tools.senko.materialdrain.ui.icons.filled.DataUsage
import tools.senko.materialdrain.ui.icons.filled.DeleteForever
import tools.senko.materialdrain.ui.icons.filled.AlternateEmail
import tools.senko.materialdrain.ui.icons.filled.Email
import tools.senko.materialdrain.ui.icons.filled.Folder
import tools.senko.materialdrain.ui.icons.filled.Handshake
import tools.senko.materialdrain.ui.icons.automirrored.filled.HelpOutline
import tools.senko.materialdrain.ui.icons.filled.Key
import tools.senko.materialdrain.ui.icons.filled.Link
import tools.senko.materialdrain.ui.icons.filled.MarkEmailUnread
import tools.senko.materialdrain.ui.icons.filled.Password
import tools.senko.materialdrain.ui.icons.filled.Person
import tools.senko.materialdrain.ui.icons.filled.Receipt
import tools.senko.materialdrain.ui.icons.automirrored.filled.ReceiptLong
import tools.senko.materialdrain.ui.icons.filled.Security
import tools.senko.materialdrain.ui.icons.filled.Speed
import tools.senko.materialdrain.ui.icons.filled.Storage
import tools.senko.materialdrain.ui.icons.filled.Straighten
import tools.senko.materialdrain.ui.icons.filled.Timer
import tools.senko.materialdrain.ui.icons.filled.Visibility
import tools.senko.materialdrain.ui.icons.filled.WorkspacePremium
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
import androidx.compose.ui.res.stringResource
import tools.senko.materialdrain.R
import androidx.annotation.StringRes

/** A small spinner in a button while what it started runs. */
@Composable
private fun ButtonSpinner() = CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)

// ---- Profile ----

/** The username, which can be changed here, and the e-mail addresses, which are managed on the website. */
@Composable
internal fun ProfileCard(user: UserInfo, state: AccountUiState, viewModel: AccountViewModel) {
    var renaming by remember { mutableStateOf(false) }
    val busy = AccountTask.PROFILE in state.busy

    AccountCard(title = stringResource(R.string.settings_profile)) {
        AccountRow(Icons.Filled.Person, stringResource(R.string.settings_username), user.username) {
            TextButton(onClick = { renaming = true }, enabled = !busy) { if (busy) ButtonSpinner() else Text(stringResource(R.string.settings_change)) }
        }
        user.emailAddresses.forEachIndexed { index, address ->
            CardDivider()
            AccountRow(Icons.Filled.Email, if (index == 0) "E-mail" else stringResource(R.string.settings_also_e_mail), address)
        }
        if (user.emailAddressPending.isNotBlank()) {
            CardDivider()
            AccountRow(Icons.Filled.MarkEmailUnread, stringResource(R.string.settings_waiting_for_verification), user.emailAddressPending, supporting = stringResource(R.string.settings_open_the_link_in_the))
        }
        CardDivider()
        AccountRow(Icons.Filled.AlternateEmail, stringResource(R.string.settings_e_mail_addresses), stringResource(R.string.settings_add_or_remove_them), supporting = stringResource(R.string.settings_on_pixeldrain_com)) {
            WebsiteLink(WEBSITE_SETTINGS_URL, stringResource(R.string.settings_adding_and_removing_e_mail))
        }
    }

    if (renaming) {
        TextInputDialog(
            title = stringResource(R.string.settings_change_username),
            label = stringResource(R.string.settings_username),
            confirmLabel = stringResource(R.string.settings_change),
            initialValue = user.username,
            supportingText = stringResource(R.string.settings_links_to_your_files_stay),
            onConfirm = { viewModel.changeUsername(it); renaming = false },
            onDismiss = { renaming = false }
        )
    }
}

// ---- Security ----

/** Two-factor authentication, the password and the API keys, which pixeldrain only lets the website change. */
@Composable
internal fun SecurityCard(user: UserInfo) {
    AccountCard(title = stringResource(R.string.settings_security)) {
        AccountRow(
            Icons.Filled.Security,
            stringResource(R.string.settings_two_factor_authentication),
            if (user.otpEnabled) stringResource(R.string.settings_on) else stringResource(R.string.media_off),
            supporting = stringResource(R.string.settings_on_pixeldrain_com)
        ) {
            WebsiteLink(WEBSITE_SETTINGS_URL, stringResource(R.string.settings_turning_two_factor_authentication_on))
        }
        CardDivider()
        AccountRow(Icons.Filled.Password, stringResource(R.string.settings_password), stringResource(R.string.settings_change_it), supporting = stringResource(R.string.settings_on_pixeldrain_com)) {
            WebsiteLink(WEBSITE_SETTINGS_URL, stringResource(R.string.settings_changing_your_password))
        }
        CardDivider()
        AccountRow(Icons.Filled.Key, stringResource(R.string.settings_sessions_and_api_keys), stringResource(R.string.settings_see_make_and_remove_them), supporting = stringResource(R.string.settings_on_pixeldrain_com)) {
            WebsiteLink(WEBSITE_API_KEYS_URL, stringResource(R.string.settings_managing_your_sessions_and_api))
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

    AccountCard(title = stringResource(R.string.settings_sharing)) {
        AccountSwitchRow(
            Icons.Filled.Speed,
            stringResource(R.string.settings_hotlinking),
            stringResource(R.string.settings_fast_downloads_of_your_files),
            checked = user.hotlinkingEnabled,
            enabled = !busy,
            onChange = viewModel::setHotlinking
        )
        CardDivider()
        AccountSwitchRow(
            Icons.Filled.Visibility,
            stringResource(R.string.settings_skip_the_file_viewer),
            stringResource(R.string.settings_links_open_the_file_not),
            checked = user.skipFileViewer,
            enabled = !busy,
            onChange = viewModel::setSkipFileViewer
        )
        CardDivider()
        AccountRow(
            Icons.Filled.Code,
            stringResource(R.string.settings_sites_which_can_embed_your),
            user.fileEmbedDomains.split(' ').filter { it.isNotBlank() }.joinToString(", ").ifBlank { "None" }
        ) {
            TextButton(onClick = { dialog = SharingDialog.EMBED }, enabled = !busy) { Text(stringResource(R.string.settings_edit)) }
        }
        CardDivider()
        AccountRow(
            Icons.Filled.DataUsage,
            stringResource(R.string.settings_monthly_transfer_cap),
            if (user.monthlyTransferCap > 0) formatSize(user.monthlyTransferCap) else stringResource(R.string.settings_the_plan_s),
            supporting = if (planCap > 0) stringResource(R.string.settings_your_plan_allows_a_month, formatSize(planCap)) else stringResource(R.string.settings_your_plan_has_no_cap)
        ) {
            TextButton(onClick = { dialog = SharingDialog.CAP }, enabled = !busy) { Text(stringResource(R.string.settings_edit)) }
        }
        CardDivider()
        AccountRow(
            Icons.Filled.Handshake,
            stringResource(R.string.settings_affiliate),
            user.affiliateUserName.ifBlank { stringResource(R.string.common_none) },
            supporting = stringResource(R.string.settings_gets_a_reward_for_your)
        ) {
            TextButton(onClick = { dialog = SharingDialog.AFFILIATE }, enabled = !busy) { Text(stringResource(R.string.settings_edit)) }
        }
    }

    when (dialog) {
        SharingDialog.EMBED -> EditableTextDialog(
            title = stringResource(R.string.settings_sites_which_can_embed_your),
            label = stringResource(R.string.settings_domains),
            initialValue = user.fileEmbedDomains.split(' ').filter { it.isNotBlank() }.joinToString("\n"),
            supportingText = stringResource(R.string.settings_one_per_line_like_example),
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
            title = stringResource(R.string.settings_affiliate),
            label = stringResource(R.string.settings_username),
            initialValue = user.affiliateUserName,
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
private val PrepaidPlans = listOf("prepaid" to R.string.settings_prepaid, "prepaid_lite" to R.string.settings_prepaid_lite)

/** The plan and what it includes, the credit, the invoices and a month of transactions. */
@Composable
internal fun BillingCard(user: UserInfo, state: AccountUiState, viewModel: AccountViewModel) {
    val plan = user.subscription
    var addingCredit by remember { mutableStateOf(false) }
    var switchingTo by remember { mutableStateOf<Pair<String, String>?>(null) }
    val creditBusy = AccountTask.CREDIT in state.busy
    val planBusy = AccountTask.PLAN in state.busy

    AccountCard(title = stringResource(R.string.settings_plan_and_billing)) {
        AccountRow(Icons.Filled.WorkspacePremium, stringResource(R.string.settings_plan), plan.name.ifBlank { stringResource(R.string.settings_free) }, supporting = plan.type.takeIf { it.isNotBlank() && !it.equals(plan.name, ignoreCase = true) })
        CardDivider()
        AccountRow(Icons.Filled.AccountBalanceWallet, stringResource(R.string.settings_credit), formatEuro(user.balanceMicroEur)) {
            FilledTonalButton(onClick = { addingCredit = true }, enabled = !creditBusy) { if (creditBusy) ButtonSpinner() else Text(stringResource(R.string.settings_add_credit)) }
        }
        CardDivider()
        // The plan's limit per upload (subscription.file_size_limit), not the size of a file on the account
        AccountRow(
            Icons.Filled.Straighten,
            stringResource(R.string.settings_upload_size_limit),
            if (plan.fileSizeLimit > 0) formatSize(plan.fileSizeLimit) else stringResource(R.string.settings_no_limit),
            supporting = stringResource(R.string.settings_largest_upload).takeIf { plan.fileSizeLimit > 0 }
        )
        CardDivider()
        AccountRow(Icons.Filled.Timer, stringResource(R.string.settings_files_are_removed), if (plan.fileExpiryDays > 0) stringResource(R.string.settings_days_after_they_were_last, plan.fileExpiryDays) else stringResource(R.string.settings_never))
        CardDivider()
        AccountRow(Icons.Filled.Folder, stringResource(R.string.files_filesystem), if (plan.filesystemAccess) stringResource(R.string.settings_included) else stringResource(R.string.settings_not_included))
        if (plan.pricePerTbStorage > 0 || plan.pricePerTbBandwidth > 0) {
            CardDivider()
            AccountRow(
                Icons.Filled.Storage,
                stringResource(R.string.settings_prices),
                stringResource(R.string.settings_per_tb_stored_per_tb, formatEuro(plan.pricePerTbStorage), formatEuro(plan.pricePerTbBandwidth))
            )
        }
        val otherPlans = PrepaidPlans.filter { it.first != plan.id }
        if (otherPlans.isNotEmpty()) {
            CardButtons {
                otherPlans.forEach { option ->
                    val name = stringResource(option.second)
                    OutlinedButton(onClick = { switchingTo = option.first to name }, enabled = !planBusy) { if (planBusy) ButtonSpinner() else Text(stringResource(R.string.settings_switch_to, name)) }
                }
            }
        }

        CardSubtitle(stringResource(R.string.settings_invoices))
        LoadableRows(state.invoices) { invoices ->
            if (invoices.isEmpty()) CardNote(stringResource(R.string.settings_no_invoices_yet))
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
                    if (open) TextButton(onClick = { viewModel.payInvoice(invoice.id) }) { Text(stringResource(R.string.settings_pay)) }
                }
            }
        }

        CardSubtitle(stringResource(R.string.settings_transactions)) { MonthSwitcher(state.transactionsMonth, viewModel::showTransactionsMonth) }
        LoadableRows(state.transactions) { transactions ->
            if (transactions.isEmpty()) CardNote(stringResource(R.string.settings_no_transactions_in, state.transactionsMonth.format(MonthFormat)))
            transactions.forEachIndexed { index, transaction ->
                if (index > 0) CardDivider()
                AccountRow(Icons.AutoMirrored.Filled.ReceiptLong, formatAccountDate(transaction.time), stringResource(R.string.settings_balance, formatEuro(transaction.newBalance)), supporting = transactionParts(transaction))
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
            title = stringResource(R.string.settings_switch_to_2, label),
            text = stringResource(R.string.settings_paid_from_your_credit_at, formatEuro(1_000_000), formatEuro(user.balanceMicroEur)),
            confirmLabel = stringResource(R.string.settings_switch),
            onConfirm = { viewModel.activatePlan(id, label); switchingTo = null },
            onDismiss = { switchingTo = null }
        )
    }
}

/** What an invoice's status means, in the words pixeldrain's website uses for it. */
@Composable
private fun invoiceStatusLabel(status: String): String = when (status.lowercase()) {
    "open" -> stringResource(R.string.settings_waiting_for_payment)
    "processing" -> stringResource(R.string.settings_payment_received_waiting_for_confirmations)
    "paid" -> stringResource(R.string.settings_paid)
    "expired" -> stringResource(R.string.settings_expired)
    "canceled" -> stringResource(R.string.settings_canceled)
    else -> status.replaceFirstChar { it.uppercase() }
}

/** "Deposit +€10.00 · Storage −€0.03 · …": the parts of a transaction which aren't zero. */
@Composable
private fun transactionParts(transaction: Transaction): String? = listOfNotNull(
    transaction.depositAmount.takeIf { it != 0L }?.let { stringResource(R.string.settings_deposit, formatEuroSigned(it)) },
    transaction.subscriptionCharge.takeIf { it != 0L }?.let { stringResource(R.string.settings_plan_2, formatEuroSigned(it)) },
    transaction.storageCharge.takeIf { it != 0L }?.let { stringResource(R.string.settings_storage, formatEuroSigned(it), formatSize(transaction.storageUsed)) },
    transaction.bandwidthCharge.takeIf { it != 0L }?.let { stringResource(R.string.settings_transfer_2, formatEuroSigned(it), formatSize(transaction.bandwidthUsed)) },
    transaction.affiliateAmount.takeIf { it != 0L }?.let { stringResource(R.string.settings_affiliate_2, formatEuroSigned(it), transaction.affiliateCount) }
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

    AccountCard(title = stringResource(R.string.settings_file_viewer)) {
        if (set.isEmpty()) CardNote(stringResource(R.string.settings_your_own_theme_images_and))
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
                if (field.isSwitch) stringResource(R.string.settings_yes) else current[field.key].orEmpty(),
                monospace = field.key.endsWith("_image")
            )
        }
        CardButtons {
            OutlinedButton(onClick = { editing = true }, enabled = !busy) { if (busy) ButtonSpinner() else Text(stringResource(R.string.settings_customize)) }
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
    AccountCard(title = stringResource(R.string.settings_removed_files), action = { MonthSwitcher(state.activityMonth, viewModel::showActivityMonth) }) {
        LoadableRows(state.activity) { events ->
            if (events.isEmpty()) CardNote(stringResource(R.string.settings_nothing_was_removed_from_your, state.activityMonth.format(MonthFormat)))
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

@Composable
private fun activityLabel(event: ActivityEvent): String = when (event.event) {
    "file_instance_expired" -> stringResource(R.string.settings_expired)
    "file_instance_blocked" -> stringResource(R.string.settings_blocked)
    "file_instance_lost" -> stringResource(R.string.settings_lost)
    "filesystem_node_blocked" -> stringResource(R.string.settings_blocked_in_the_filesystem)
    "filesystem_node_lost" -> stringResource(R.string.settings_lost_from_the_filesystem)
    else -> event.event.replace('_', ' ').replaceFirstChar { it.uppercase() }
}

// ---- Deleting the account ----

/** Deleting the account, which pixeldrain only lets the website do. */
@Composable
internal fun DangerCard() {
    AccountCard(title = stringResource(R.string.settings_delete_account)) {
        AccountRow(
            Icons.Filled.DeleteForever,
            stringResource(R.string.settings_delete_my_account),
            stringResource(R.string.settings_on_pixeldrain_com),
            supporting = stringResource(R.string.settings_you_re_logged_out_everywhere)
        ) {
            WebsiteLink(WEBSITE_SETTINGS_URL, stringResource(R.string.settings_deleting_your_account))
        }
    }
}

// ---- Previews ----
// What's here is drawn by the account page's previews, with a made-up account: SettingsAccountPreview (signed in, every
// card) and SettingsAccountSignedOutPreview in SettingsScreen.kt, and the account's tile in SettingsHomePreview.
