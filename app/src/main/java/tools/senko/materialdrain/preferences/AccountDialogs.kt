package tools.senko.materialdrain.preferences

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import tools.senko.materialdrain.util.formatSize

/** Like TextInputDialog, but an empty value can be saved too: it removes the setting. */
@Composable
internal fun EditableTextDialog(
    title: String,
    label: String,
    initialValue: String,
    supportingText: String,
    singleLine: Boolean = true,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var value by remember { mutableStateOf(initialValue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = { Text(label) },
                    singleLine = singleLine,
                    minLines = if (singleLine) 1 else 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(supportingText, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { Button(onClick = { onConfirm(value) }, enabled = value != initialValue) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private const val BYTES_PER_GB = 1_000_000_000L

/** The account's own monthly transfer cap, in GB; 0 or empty leaves only the plan's. */
@Composable
internal fun TransferCapDialog(currentBytes: Long, planCapBytes: Long, onConfirm: (Long) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(if (currentBytes > 0) (currentBytes / BYTES_PER_GB).toString() else "") }
    val gigabytes = text.trim().ifEmpty { "0" }.toLongOrNull()
    val bytes = gigabytes?.times(BYTES_PER_GB)
    val overPlan = bytes != null && planCapBytes > 0 && bytes > planCapBytes
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Monthly transfer cap") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.filter(Char::isDigit).take(7) },
                    label = { Text("GB a month") },
                    singleLine = true,
                    isError = overPlan,
                    supportingText = if (overPlan) ({ Text("More than your plan allows (${formatSize(planCapBytes)})") }) else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Stops your premium transfer from being used past this, so it doesn't cost more than you want. Leave it empty for your plan's cap.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = { Button(onClick = { onConfirm(bytes ?: 0L) }, enabled = bytes != null && !overPlan) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/**
 * The ways to pay for credit, as POST /user/invoice names them: the ones pixeldrain's own website offers (see
 * payment_providers in its checkout/CheckoutLib.ts). PayPal, cards and the bank transfers were turned off there, and an
 * invoice for them fails on the server.
 */
private val PaymentMethods = listOf(
    "btc" to "Bitcoin",
    "doge" to "Dogecoin",
    "xmr" to "Monero",
    "ltc" to "Litecoin",
    "usdt_ethereum" to "USD₮ Ethereum",
    "usdt_tron" to "USD₮ TRON"
)

private val CreditAmount = 10L..1_000_000L

/** Adding credit: how much, how it's paid and where from (for VAT). The payment itself happens in the browser. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AddCreditDialog(
    initialCountry: String,
    initialMethod: String,
    hasOpenInvoice: Boolean,
    onConfirm: (amountEur: Long, method: String, country: String) -> Unit,
    onDismiss: () -> Unit
) {
    var amount by remember { mutableStateOf("10") }
    var method by remember { mutableStateOf(initialMethod.takeIf { saved -> PaymentMethods.any { it.first == saved } } ?: PaymentMethods.first().first) }
    // The server takes the three letter code (ISO 3166 alpha-3), as the website sends it
    var country by remember { mutableStateOf(initialCountry.takeIf { it.length == 3 }.orEmpty()) }
    val euros = amount.toLongOrNull()
    val amountOk = euros != null && euros in CreditAmount
    val valid = amountOk && country.length == 3
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add credit") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                if (hasOpenInvoice) {
                    Text(
                        "You still have an unpaid invoice. Pay that one first: invoices can't be cancelled, they expire by themselves.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter(Char::isDigit).take(7) },
                    label = { Text("Amount in euros") },
                    prefix = { Text("€") },
                    singleLine = true,
                    isError = amount.isNotEmpty() && !amountOk,
                    supportingText = { Text("At least €${CreditAmount.first}. VAT is added to it.") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Pay with", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PaymentMethods.forEach { (id, label) ->
                        FilterChip(selected = method == id, onClick = { method = id }, label = { Text(label) })
                    }
                }
                Text(
                    "Pixeldrain only takes cryptocurrency for now. The payment page opens in your browser, with the address and amount to send.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = country,
                    onValueChange = { country = it.filter(Char::isLetter).take(3).uppercase() },
                    label = { Text("Country code") },
                    placeholder = { Text("NLD") },
                    singleLine = true,
                    isError = country.isNotEmpty() && country.length != 3,
                    supportingText = { Text("Three letters, like NLD, DEU or USA, for the VAT") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = { Button(onClick = { onConfirm(euros ?: 0L, method, country) }, enabled = valid) { Text("Continue to payment") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/** A setting of the file viewer, as PUT /user/file_customization names it. */
internal class BrandingField(val key: String, val label: String, val isSwitch: Boolean = false, val hint: String? = null)

internal val BrandingFields = listOf(
    BrandingField("theme", "Theme", hint = "The name of one of pixeldrain's colour themes"),
    BrandingField("header_image", "Header image", hint = "ID of an image file on your account"),
    BrandingField("header_link", "Header link", hint = "https://…"),
    BrandingField("background_image", "Background image", hint = "ID of an image file on your account"),
    BrandingField("footer_image", "Footer image", hint = "ID of an image file on your account"),
    BrandingField("footer_link", "Footer link", hint = "https://…"),
    BrandingField("affiliate_prompt", "Affiliate message"),
    BrandingField("disable_download_button", "Hide the download button", isSwitch = true),
    BrandingField("disable_share_button", "Hide the share button", isSwitch = true)
)

/** Every setting of the file viewer at once; empty ones are removed. */
@Composable
internal fun BrandingDialog(current: Map<String, String>, onConfirm: (Map<String, String>) -> Unit, onDismiss: () -> Unit) {
    val values = remember { mutableStateMapOf<String, String>().apply { BrandingFields.forEach { put(it.key, current[it.key].orEmpty()) } } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("File viewer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
                Text(
                    "How the download page of your files looks. Images are files on your account (JPEG, PNG, GIF or WebP, at most 10 MB): copy their ID from their details.",
                    style = MaterialTheme.typography.bodySmall
                )
                BrandingFields.forEach { field ->
                    if (field.isSwitch) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text(field.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Switch(checked = values[field.key] == "true", onCheckedChange = { values[field.key] = it.toString() })
                        }
                    } else {
                        OutlinedTextField(
                            value = values[field.key].orEmpty(),
                            onValueChange = { values[field.key] = it },
                            label = { Text(field.label) },
                            placeholder = field.hint?.let { { Text(it) } },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onConfirm(BrandingFields.associate { field ->
                    field.key to if (field.isSwitch) (values[field.key] == "true").toString() else values[field.key].orEmpty().trim()
                })
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
