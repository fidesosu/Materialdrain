package tools.senko.materialdrain.provider.api

/** Error shape every [StorageProvider] normalizes its failures into. */
data class ProviderError(val code: String, val message: String, val httpStatus: Int? = null)

/**
 * The [ProviderError.code]s the app itself acts on, whichever host they come from. A host can have codes of its own too
 * (its server's); the app shows the message of those as it is.
 */
object ErrorCodes {
    /** The host can't do this at all: no such operation, endpoint or capability. */
    const val NOT_SUPPORTED = "not_supported"
    /** The host couldn't be reached, or the connection broke off. */
    const val NETWORK_ERROR = "network_error"
    /** Pixeldrain's own code for a failed connection starts with this, e.g. "network_exception_get_list". */
    const val NETWORK_EXCEPTION_PREFIX = "network_exception"
    const val AUTHENTICATION_REQUIRED = "authentication_required"
    /** The host needs the account's API key, and there is none. */
    const val API_KEY_MISSING = "api_key_missing"
    const val FORBIDDEN = "forbidden"
    const val ACCESS_DENIED = "access_denied"
    const val NOT_FOUND = "not_found"
    const val ALREADY_EXISTS = "already_exists"
    const val DIRECTORY_NOT_EMPTY = "directory_not_empty"

    /** Whether [code] says the host couldn't be reached. */
    fun isNetworkError(code: String): Boolean = code == NETWORK_ERROR || code.startsWith(NETWORK_EXCEPTION_PREFIX)
}

/** Result of a [StorageProvider] operation. A single [Error] works for every [T] since it never holds one. */
sealed class ApiResponse<out T> {
    data class Success<out T>(val data: T) : ApiResponse<T>()
    data class Error(val error: ProviderError) : ApiResponse<Nothing>()
}
