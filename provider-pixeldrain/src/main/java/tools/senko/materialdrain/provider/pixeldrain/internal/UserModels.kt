package tools.senko.materialdrain.provider.pixeldrain.internal

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class UserFilesListResponse(
    val files: List<FileInfoResponse>
)

@Serializable
data class UserList(
    val id: String,
    val title: String,
    @SerialName("date_created") val dateCreated: String,
    @SerialName("file_count") val fileCount: Int,
    val files: List<FileInfoResponse>? = null,
    @SerialName("can_edit") val canEdit: Boolean
)

@Serializable
data class UserListsResponse(
    val lists: List<UserList>
)


// Response of POST /user/login and POST /user/session when a session was created (HTTP 201), and the entries of GET /user/session
@Serializable
data class LoginSession(
    @SerialName("auth_key") val authKey: String,
    @SerialName("app_name") val appName: String? = null,
    @SerialName("creation_time") val creationTime: String? = null,
    @SerialName("last_used_time") val lastUsedTime: String? = null,
    @SerialName("creation_ip_address") val creationIpAddress: String? = null,
    @SerialName("user_agent") val userAgent: String? = null
)

sealed class LoginResult {
    data class LoggedIn(val session: LoginSession) : LoginResult()
    // HTTP 202 login_link_sent: a login link was e-mailed to the account
    data object LoginLinkSent : LoginResult()
}

/** GET /user: the logged in account. -1 in the limits of [subscription] means there is no limit. */
@Serializable
data class UserInfo(
    val id: String = "",
    val username: String = "",
    // Verified addresses, at most 5; notifications go to all of them
    @SerialName("email_addresses") val emailAddresses: List<String> = emptyList(),
    // Waiting for its verification link to be clicked
    @SerialName("email_address_pending") val emailAddressPending: String = "",
    @SerialName("can_upload") val canUpload: Boolean = true,
    @SerialName("otp_enabled") val otpEnabled: Boolean = false,
    val subscription: Subscription = Subscription(),
    @SerialName("storage_space_used") val storageSpaceUsed: Long = 0,
    @SerialName("filesystem_storage_used") val filesystemStorageUsed: Long = 0,
    @SerialName("file_count") val fileCount: Long = 0,
    @SerialName("filesystem_node_count") val filesystemNodeCount: Long = 0,
    @SerialName("is_admin") val isAdmin: Boolean = false,
    // 1000000 = €1
    @SerialName("balance_micro_eur") val balanceMicroEur: Long = 0,
    @SerialName("hotlinking_enabled") val hotlinkingEnabled: Boolean = false,
    // The account's own cap in bytes, 0 when none is set (the plan's cap is in subscription)
    @SerialName("monthly_transfer_cap") val monthlyTransferCap: Long = 0,
    @SerialName("monthly_transfer_used") val monthlyTransferUsed: Long = 0,
    // See PUT /user/file_customization; null when nothing was customized
    @SerialName("file_viewer_branding") val fileViewerBranding: JsonObject? = null,
    // Space separated
    @SerialName("file_embed_domains") val fileEmbedDomains: String = "",
    @SerialName("skip_file_viewer") val skipFileViewer: Boolean = false,
    @SerialName("affiliate_user_name") val affiliateUserName: String = "",
    @SerialName("checkout_country") val checkoutCountry: String = "",
    @SerialName("checkout_name") val checkoutName: String = "",
    @SerialName("checkout_provider") val checkoutProvider: String = ""
)

@Serializable
data class Subscription(
    val id: String = "",
    val name: String = "",
    val type: String = "",
    @SerialName("file_size_limit") val fileSizeLimit: Long = 0,
    @SerialName("file_expiry_days") val fileExpiryDays: Int = 0,
    @SerialName("storage_space") val storageSpace: Long = 0,
    // Micro euros per TB
    @SerialName("price_per_tb_storage") val pricePerTbStorage: Long = 0,
    @SerialName("price_per_tb_bandwidth") val pricePerTbBandwidth: Long = 0,
    @SerialName("monthly_transfer_cap") val monthlyTransferCap: Long = 0,
    @SerialName("file_viewer_branding") val fileViewerBranding: Boolean = false,
    @SerialName("filesystem_access") val filesystemAccess: Boolean = false,
    @SerialName("filesystem_storage_limit") val filesystemStorageLimit: Long = 0
)

/** GET /user/time_series/{statistic}: one amount per time slot. */
@Serializable
data class TimeSeries(
    val timestamps: List<String> = emptyList(),
    val amounts: List<Long> = emptyList()
)

/** An entry of GET /user/activity/{month}: a file which was removed from the account. */
@Serializable
data class ActivityEvent(
    val time: String = "",
    // file_instance_expired, file_instance_blocked, file_instance_lost, filesystem_node_blocked or filesystem_node_lost
    val event: String = "",
    @SerialName("file_id") val fileId: String = "",
    @SerialName("file_name") val fileName: String = "",
    @SerialName("file_removal_reason") val fileRemovalReason: String = ""
)

/** An entry of GET /user/transactions/{month}. Amounts in micro euros, charges are negative. */
@Serializable
data class Transaction(
    val time: String = "",
    @SerialName("new_balance") val newBalance: Long = 0,
    @SerialName("deposit_amount") val depositAmount: Long = 0,
    @SerialName("subscription_charge") val subscriptionCharge: Long = 0,
    @SerialName("storage_charge") val storageCharge: Long = 0,
    @SerialName("storage_used") val storageUsed: Long = 0,
    @SerialName("bandwidth_charge") val bandwidthCharge: Long = 0,
    @SerialName("bandwidth_used") val bandwidthUsed: Long = 0,
    @SerialName("affiliate_amount") val affiliateAmount: Long = 0,
    @SerialName("affiliate_count") val affiliateCount: Int = 0
)

/** An entry of GET /user/invoice. Amounts in micro euros. */
@Serializable
data class Invoice(
    val id: String = "",
    val time: String = "",
    val amount: Long = 0,
    val vat: Long = 0,
    val country: String = "",
    @SerialName("payment_gateway") val paymentGateway: String = "",
    @SerialName("payment_method") val paymentMethod: String = "",
    val status: String = ""
)

/** POST /user/invoice: where the new invoice is paid. */
@Serializable
data class CreatedInvoice(
    @SerialName("invoice_id") val invoiceId: String = "",
    @SerialName("checkout_url") val checkoutUrl: String = ""
)

/** POST /user/totp action generate: a secret which isn't saved to the account until it's verified. */
@Serializable
data class TotpSecret(
    val secret: String = "",
    // otpauth:// URI for authenticator apps
    val uri: String = ""
)
