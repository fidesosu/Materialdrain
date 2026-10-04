package tools.senko.materialdrain.provider.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** How an API key (or a token from signing in) is attached to requests. */
@Serializable
enum class AuthType { NONE, BASIC, BEARER, HEADER }

/** How a username and password are used: sent with every request (HTTP Basic), or exchanged for a token once. */
@Serializable
enum class PasswordAuthMode { BASIC, LOGIN }

@Serializable
enum class LoginBody { FORM, JSON }

/** Matches a response whose JSON has [equals] at dot-path [path], e.g. {"path": "$.value", "equals": "otp_required"}. */
@Serializable
data class ResponseCondition(val path: String, val equals: String)

/**
 * Two-factor sign-in, the "ask again with the code" kind: when the sign-in response matches [requiredWhen], the
 * user is asked for a code and the same sign-in request is sent again with it (the {otp} field filled in).
 */
@Serializable
data class OtpConfig(
    @SerialName("required_when") val requiredWhen: ResponseCondition,
    /** The code was wrong; without it, a code that's still asked for after sending one counts as rejected. */
    @SerialName("rejected_when") val rejectedWhen: ResponseCondition? = null,
    val digits: Int = 6
)

/** A sign-in request exchanging a username and password for a token, which is then used like an API key. */
@Serializable
data class LoginEndpoint(
    val method: String = "POST",
    val path: String,
    val body: LoginBody = LoginBody.FORM,
    /**
     * Sent with the request; {username}, {password} and {otp} in the values are filled in. A field whose value
     * is exactly "{otp}" is left out until the host has asked for a code.
     */
    val fields: Map<String, String> = mapOf("username" to "{username}", "password" to "{password}"),
    /** Dot-path of the token in the response, e.g. "$.auth_key". */
    val token: String,
    val otp: OtpConfig? = null
)

@Serializable
data class PasswordAuth(
    val mode: PasswordAuthMode,
    /** Required for [PasswordAuthMode.LOGIN]. */
    val login: LoginEndpoint? = null,
    /** [PasswordAuthMode.LOGIN]: ends the session on the host when signing out; without it, signing out is local only. */
    val logout: EndpointConfig? = null
)

@Serializable
data class AuthConfig(
    val type: AuthType = AuthType.NONE,
    @SerialName("header_name") val headerName: String = "Authorization",
    /** [AuthType.BASIC] with an API key: the username sent along with it, the key goes in the password field. */
    @SerialName("key_username") val keyUsername: String = "",
    /** How a username and password can be used instead of an API key; null when the host only takes a key. */
    @SerialName("password_auth") val passwordAuth: PasswordAuth? = null
)

/**
 * What the user entered for a host in the app, never part of a config.
 *
 * @param password only kept for [PasswordAuthMode.BASIC], where it's sent with every request. A
 *   [PasswordAuthMode.LOGIN] sign-in keeps the session ([loginToken]) instead, like any app's normal sign-in.
 * @param loginToken the session from signing in; while present, the host counts as signed in
 */
data class Credentials(
    val apiKey: String = "",
    val username: String = "",
    val password: String = "",
    val loginToken: String? = null
) {
    val hasPassword: Boolean get() = username.isNotBlank() && password.isNotEmpty()
}

@Serializable
enum class BodyEncoding { RAW, MULTIPART, FORM, JSON }

/** One request/response shape for [GenericRestConfig]. {placeholders} in [path] are filled in at call time. */
@Serializable
data class EndpointConfig(
    val method: String,
    val path: String,
    val body: BodyEncoding = BodyEncoding.RAW,
    @SerialName("success_status") val successStatus: List<Int> = listOf(200, 201),
    /** Dot-path (e.g. "$.file.id") into the JSON response for each normalized field: id, download_url, size, name, mime_type, username, quota_used, quota_total. */
    @SerialName("response_map") val responseMap: Map<String, String> = emptyMap(),
    /**
     * For the "list" endpoint only: dot-path to the array of items in the response (e.g. "$.files"), null
     * when the response body itself is that array. [responseMap] is then applied to each item instead of once.
     */
    @SerialName("list_path") val listPath: String? = null,
    /**
     * Query parameters sent with the request. Values take the same {placeholders}; a value that is exactly one
     * placeholder with nothing filled in is left out, so optional flags (e.g. "recursive") can be omitted.
     */
    val query: Map<String, String> = emptyMap(),
    /** Form fields for a body of [BodyEncoding.FORM], with the same placeholder rules as [query]. */
    val form: Map<String, String> = emptyMap(),
    /**
     * The JSON text of a body of [BodyEncoding.JSON]. Placeholders are replaced as they are, so a value which must
     * be JSON (a string in quotes, an array) is passed in already encoded, e.g. {title_json} or {files_json}.
     */
    val json: String? = null,
    /** For a folder listing: dot-path to the breadcrumb nodes of the folder (the path to it), mapped with [responseMap]. */
    @SerialName("breadcrumb_path") val breadcrumbPath: String? = null,
    /** For a folder listing: fields of the listing as a whole, e.g. "can_write" / "can_delete" (dot-paths into the body). */
    @SerialName("listing_map") val listingMap: Map<String, String> = emptyMap()
)

/**
 * Optional sharing/update information. Never holds anything private: credentials live in the app, not in
 * a config, so a config can be passed around freely.
 */
@Serializable
data class ProviderConfigMeta(
    /** Stable identity across versions, e.g. "io.github.someone.truenas-scale". */
    val id: String? = null,
    /** Only ever goes up; an update is a remote copy with a higher version. */
    val version: Int = 0,
    /** Raw config file (https) that newer versions of this config are fetched from. */
    @SerialName("update_url") val updateUrl: String? = null,
    /** The app's versionCode this config needs at least; older apps report it instead of breaking. */
    @SerialName("min_app_version") val minAppVersion: Int? = null,
    val author: String? = null
)

/** Config for every kind a saved host can be. Only [name] and [meta] are common; the rest is kind-specific. */
sealed interface ProviderConfig {
    val name: String
    val meta: ProviderConfigMeta?
}

/** How this host takes a username and password, null when it only takes an API key. */
val ProviderConfig.passwordAuth: PasswordAuth?
    get() = when (this) {
        is GenericRestConfig -> auth.passwordAuth
        is WebDavConfig -> auth.passwordAuth
        is S3Config -> null
    }

fun ProviderConfig.withMeta(meta: ProviderConfigMeta?): ProviderConfig = when (this) {
    is GenericRestConfig -> copy(meta = meta)
    is WebDavConfig -> copy(meta = meta)
    is S3Config -> copy(meta = meta)
}

/**
 * The long-tail case: a host described entirely by this config, no Kotlin written for it. Only
 * upload/download/delete/file_info/user_info are supported — search/share/permissions vary too much in
 * semantics across arbitrary REST hosts to describe generically, so they're simply absent here.
 */
@Serializable
data class GenericRestConfig(
    override val name: String,
    @SerialName("base_url") val baseUrl: String,
    val auth: AuthConfig = AuthConfig(),
    val endpoints: Map<String, EndpointConfig>,
    /** The folder the browser starts at, e.g. "me" for Pixeldrain; empty for the top of the host. */
    @SerialName("browse_root") val browseRoot: String = "",
    /**
     * Whether the saved API key or sign-in is sent with the requests. On by default, which is what almost every
     * host needs; a host that must not see the login (e.g. a public mirror) sets it to false.
     */
    @SerialName("send_credentials") val sendCredentials: Boolean = true,
    /** Whether the account's own login (Settings → Account) is used when this host has no sign-in or API key of its own. */
    @SerialName("account_fallback") val accountFallback: Boolean = false,
    override val meta: ProviderConfigMeta? = null
) : ProviderConfig

/** Nextcloud / ownCloud / TrueNAS WebDAV share. Protocol logic lives in the webdav adapter module, not here. */
@Serializable
data class WebDavConfig(
    override val name: String,
    @SerialName("base_url") val baseUrl: String,
    val auth: AuthConfig = AuthConfig(type = AuthType.BASIC),
    @SerialName("root_path") val rootPath: String = "/",
    override val meta: ProviderConfigMeta? = null
) : ProviderConfig

/** MinIO / TrueNAS-S3 / Backblaze B2 / R2 / Wasabi. Protocol (SigV4) logic lives in the s3 adapter module. */
@Serializable
data class S3Config(
    override val name: String,
    val endpoint: String,
    val region: String = "us-east-1",
    val bucket: String,
    @SerialName("path_style") val pathStyle: Boolean = true,
    val prefix: String = "",
    override val meta: ProviderConfigMeta? = null
) : ProviderConfig

/** The address of a host's own service, or null when the base URL isn't a valid one. */
private fun hostOf(baseUrl: String): String? = try {
    java.net.URI(baseUrl.trim()).host?.lowercase()
} catch (_: Exception) {
    null
}

/**
 * Whether the account's own login may be sent to this host. Only Pixeldrain's own address qualifies: a config can
 * come from anyone, and the login must never be sent to another server, whatever the config says.
 */
fun allowsAccountFallback(config: GenericRestConfig): Boolean =
    config.accountFallback && hostOf(config.baseUrl).let { it == "pixeldrain.com" || it == "www.pixeldrain.com" }
