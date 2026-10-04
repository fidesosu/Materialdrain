package tools.senko.materialdrain.provider.api

/** Error shape every [StorageProvider] normalizes its failures into. */
data class ProviderError(val code: String, val message: String, val httpStatus: Int? = null)

/** Result of a [StorageProvider] operation. A single [Error] works for every [T] since it never holds one. */
sealed class ApiResponse<out T> {
    data class Success<out T>(val data: T) : ApiResponse<T>()
    data class Error(val error: ProviderError) : ApiResponse<Nothing>()
}
