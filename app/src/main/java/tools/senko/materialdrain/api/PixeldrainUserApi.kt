package tools.senko.materialdrain.api

import android.util.Log
import io.ktor.client.call.body
import io.ktor.client.request.headers
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.path
import kotlinx.coroutines.CancellationException
import okhttp3.FormBody
import okhttp3.Request

private const val TAG_USER_API = "PIXEL_API_SERVICE"
private const val LOGIN_APP_NAME = "Materialdrain" // shown on the pixeldrain API keys page

/** Pixeldrain user API: every /user endpoint. Only login/logout and the existing lists are implemented so far. */
interface PixeldrainUserApi {

    /**
     * POST /user/login. Password login: [username] + [password]. E-mail login: only [username], the server
     * then e-mails a link (LoginLinkSent); the ids from that link complete the login via
     * [linkLoginUserId] + [linkLoginId]. Accounts with 2FA additionally need [totp].
     */
    suspend fun login(
        username: String,
        password: String = "",
        totp: String = "",
        linkLoginUserId: String = "",
        linkLoginId: String = ""
    ): ApiResponse<LoginResult>

    /** DELETE /user/session: invalidates the API key used for the request. */
    suspend fun logout(apiKey: String): ApiResponse<FileUploadResponse>

    suspend fun getUserFiles(apiKey: String): ApiResponse<UserFilesListResponse>

    suspend fun getUserLists(apiKey: String): ApiResponse<UserListsResponse>
}

class PixeldrainUserApiImpl(private val http: PixeldrainHttpClient) : PixeldrainUserApi {

    override suspend fun login(
        username: String,
        password: String,
        totp: String,
        linkLoginUserId: String,
        linkLoginId: String
    ): ApiResponse<LoginResult> {
        if (username.isBlank()) {
            return ApiResponse.Error(FileUploadResponse(success = false, value = "missing_field", message = "Enter your username or e-mail address."))
        }
        val form = FormBody.Builder().apply {
            add("username", username.trim())
            if (password.isNotEmpty()) add("password", password)
            if (totp.isNotBlank()) add("totp", totp.trim())
            if (linkLoginUserId.isNotBlank()) add("link_login_user_id", linkLoginUserId.trim())
            if (linkLoginId.isNotBlank()) add("link_login_id", linkLoginId.trim())
            add("app_name", LOGIN_APP_NAME)
        }.build()
        val request = Request.Builder().url(http.apiUrl("user", "login").build()).post(form).build()

        return try {
            val raw = http.send(request)
            when (raw.code) {
                HttpStatusCode.Created.value -> ApiResponse.Success(LoginResult.LoggedIn(http.json.decodeFromString<LoginSession>(raw.body)))
                HttpStatusCode.Accepted.value -> ApiResponse.Success(LoginResult.LoginLinkSent)
                else -> ApiResponse.Error(http.parseError(raw.code, raw.body, "login_failed_status"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Never log the request: it contains the password
            Log.e(TAG_USER_API, "Exception during POST login: ${e.javaClass.simpleName}")
            ApiResponse.Error(FileUploadResponse(success = false, value = "network_exception_login", message = e.message ?: "Network request failed"))
        }
    }

    override suspend fun logout(apiKey: String): ApiResponse<FileUploadResponse> {
        if (apiKey.isBlank()) {
            return ApiResponse.Error(FileUploadResponse(success = false, value = "api_key_missing", message = "No session to log out of."))
        }
        val request = Request.Builder()
            .url(http.apiUrl("user", "session").build())
            .header(HttpHeaders.Authorization, http.basicAuth(apiKey))
            .delete()
            .build()
        return try {
            val raw = http.send(request)
            val parsed = if (raw.isSuccessful) {
                try { http.json.decodeFromString<FileUploadResponse>(raw.body) } catch (_: Exception) { null }
            } else null
            // The API states that when the value is not "ok" the session is still usable
            if (parsed != null && parsed.value == "ok") ApiResponse.Success(parsed)
            else ApiResponse.Error(parsed ?: http.parseError(raw.code, raw.body, "logout_failed_status"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG_USER_API, "Exception during DELETE session: ${e.message}", e)
            ApiResponse.Error(FileUploadResponse(success = false, value = "network_exception_logout", message = e.message ?: "Network request failed"))
        }
    }

    override suspend fun getUserFiles(apiKey: String): ApiResponse<UserFilesListResponse> {
        if (apiKey.isBlank()) {
            return ApiResponse.Error(FileUploadResponse(success = false, value = "api_key_missing", message = "API Key is required to fetch user files."))
        }
        return try {
            http.ktor.prepareGet {
                url {
                    protocol = URLProtocol.HTTPS
                    host = PIXELDRAIN_HOST
                    path("api/user/files")
                }
                headers {
                    append(HttpHeaders.Authorization, http.basicAuth(apiKey))
                }
            }.execute { response: HttpResponse ->
                if (response.status == HttpStatusCode.OK) {
                    ApiResponse.Success(response.body<UserFilesListResponse>())
                } else {
                    ApiResponse.Error(http.parseError(response, "user_files_failed_status"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG_USER_API, "Exception for GET user files: ${e.message}", e)
            val errorMsg = e.message ?: "Network request failed or failed to parse error response"
            ApiResponse.Error(FileUploadResponse(success = false, value = "network_exception_user_files", message = errorMsg))
        }
    }

    override suspend fun getUserLists(apiKey: String): ApiResponse<UserListsResponse> {
        if (apiKey.isBlank()) {
            return ApiResponse.Error(FileUploadResponse(success = false, value = "api_key_missing", message = "API Key is required to fetch user lists."))
        }
        return try {
            http.ktor.prepareGet {
                url {
                    protocol = URLProtocol.HTTPS
                    host = PIXELDRAIN_HOST
                    path("api/user/lists")
                }
                headers {
                    append(HttpHeaders.Authorization, http.basicAuth(apiKey))
                }
            }.execute { response: HttpResponse ->
                if (response.status == HttpStatusCode.OK) {
                    ApiResponse.Success(response.body<UserListsResponse>())
                } else {
                    ApiResponse.Error(http.parseError(response, "user_lists_failed_status"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG_USER_API, "Exception for GET user lists: ${e.message}", e)
            val errorMsg = e.message ?: "Network request failed or failed to parse error response"
            ApiResponse.Error(FileUploadResponse(success = false, value = "network_exception_user_lists", message = errorMsg))
        }
    }
}
