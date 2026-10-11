package tools.senko.materialdrain.provider.pixeldrain.internal

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
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.Request

private const val TAG_USER_API = "PIXEL_API_SERVICE"
private const val LOGIN_APP_NAME = "Materialdrain" // shown on the pixeldrain API keys page

/**
 * Pixeldrain user API: every /user endpoint. Changing the password, deleting the account, the API keys and two-factor
 * authentication only work with a session made by logging in on the website; with the app's own session the server
 * answers website_login_required.
 */
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

    /** GET /user: the account, its plan and its usage. */
    suspend fun getUser(apiKey: String): ApiResponse<UserInfo>

    /**
     * PUT /user: changes only the given [fields] (username, email, password_new, subscription, hotlinking_enabled,
     * transfer_cap, skip_file_viewer, embed_domains, affiliate_user_name, checkout_*). Adding an e-mail address answers
     * with value login_link_sent: a verification link was sent to it.
     */
    suspend fun updateUser(apiKey: String, fields: Map<String, String>): ApiResponse<FileUploadResponse>

    /** DELETE /user/email: removes an address, also one still waiting for verification. The last one can't be removed. */
    suspend fun removeEmail(apiKey: String, email: String): ApiResponse<FileUploadResponse>

    /** DELETE /user: deletes the account in 7 days, unless it logs in again before then. */
    suspend fun deleteAccount(apiKey: String): ApiResponse<FileUploadResponse>

    /** GET /user/time_series/{statistic}: [statistic] is bandwidth, transfer_paid, views or downloads; times in RFC 3339. */
    suspend fun getTimeSeries(apiKey: String, statistic: String, start: String, end: String, intervalMinutes: Int): ApiResponse<TimeSeries>

    /** GET /user/activity/{month}, [month] like "2024-02": the files which were removed from the account. */
    suspend fun getActivity(apiKey: String, month: String): ApiResponse<List<ActivityEvent>>

    /** GET /user/transactions/{month}, [month] like "2024-02": the prepaid transactions. */
    suspend fun getTransactions(apiKey: String, month: String): ApiResponse<List<Transaction>>

    /** POST /user/invoice: an invoice of [amountMicroEur] to add credit, paid at the returned checkout URL. */
    suspend fun createInvoice(apiKey: String, amountMicroEur: Long, network: String, country: String, name: String = ""): ApiResponse<CreatedInvoice>

    suspend fun getInvoices(apiKey: String): ApiResponse<List<Invoice>>

    /** Where GET /user/pay_invoice/{id} sends the browser: the checkout page of an open invoice. */
    suspend fun getInvoiceCheckoutUrl(apiKey: String, invoiceId: String): ApiResponse<String>

    /** PUT /user/file_customization: the look of the file viewer for all the account's files. Empty values remove a field. */
    suspend fun setFileCustomization(apiKey: String, fields: Map<String, String>): ApiResponse<FileUploadResponse>

    /** POST /user/session: an extra API key for another app. */
    suspend fun createSession(apiKey: String, appName: String): ApiResponse<LoginSession>

    /** GET /user/session: every active session of the account. */
    suspend fun getSessions(apiKey: String): ApiResponse<List<LoginSession>>

    /** POST /user/totp action generate: a new secret, not saved to the account until [verifyTotp]. */
    suspend fun generateTotp(apiKey: String): ApiResponse<TotpSecret>

    /** POST /user/totp action verify: checks [otp] against [secret] and turns two-factor authentication on. */
    suspend fun verifyTotp(apiKey: String, secret: String, otp: String): ApiResponse<FileUploadResponse>

    /** POST /user/totp action delete: turns two-factor authentication off. */
    suspend fun disableTotp(apiKey: String): ApiResponse<FileUploadResponse>
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

    // The account's answers can hold nulls where a list or text is expected (an empty array is written as null), which
    // then read as the field's default
    private val accountJson = Json {
        isLenient = true
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    override suspend fun getUser(apiKey: String): ApiResponse<UserInfo> =
        call(apiKey, Request.Builder().url(http.apiUrl("user").build()).get(), "user")

    override suspend fun updateUser(apiKey: String, fields: Map<String, String>): ApiResponse<FileUploadResponse> =
        call(apiKey, Request.Builder().url(http.apiUrl("user").build()).put(form(fields)), "update_user")

    override suspend fun removeEmail(apiKey: String, email: String): ApiResponse<FileUploadResponse> {
        // In the URL and the body both: a DELETE's body isn't read as a form by every server
        val url = http.apiUrl("user", "email").addQueryParameter("email", email).build()
        return call(apiKey, Request.Builder().url(url).delete(form(mapOf("email" to email))), "remove_email")
    }

    override suspend fun deleteAccount(apiKey: String): ApiResponse<FileUploadResponse> =
        call(apiKey, Request.Builder().url(http.apiUrl("user").build()).delete(), "delete_account")

    override suspend fun getTimeSeries(apiKey: String, statistic: String, start: String, end: String, intervalMinutes: Int): ApiResponse<TimeSeries> {
        val url = http.apiUrl("user", "time_series", statistic)
            .addQueryParameter("start", start)
            .addQueryParameter("end", end)
            .addQueryParameter("interval", intervalMinutes.toString())
            .build()
        return call(apiKey, Request.Builder().url(url).get(), "time_series")
    }

    override suspend fun getActivity(apiKey: String, month: String): ApiResponse<List<ActivityEvent>> =
        call<List<ActivityEvent>?>(apiKey, Request.Builder().url(http.apiUrl("user", "activity", month).build()).get(), "activity")
            .orEmptyList()

    override suspend fun getTransactions(apiKey: String, month: String): ApiResponse<List<Transaction>> =
        call<List<Transaction>?>(apiKey, Request.Builder().url(http.apiUrl("user", "transactions", month).build()).get(), "transactions")
            .orEmptyList()

    override suspend fun createInvoice(apiKey: String, amountMicroEur: Long, network: String, country: String, name: String): ApiResponse<CreatedInvoice> {
        val fields = buildMap {
            put("amount", amountMicroEur.toString())
            put("network", network)
            put("country", country)
            if (name.isNotBlank()) put("name", name.trim())
        }
        return call(apiKey, Request.Builder().url(http.apiUrl("user", "invoice").build()).post(form(fields)), "create_invoice")
    }

    override suspend fun getInvoices(apiKey: String): ApiResponse<List<Invoice>> =
        call<List<Invoice>?>(apiKey, Request.Builder().url(http.apiUrl("user", "invoice").build()).get(), "invoices")
            .orEmptyList()

    override suspend fun getInvoiceCheckoutUrl(apiKey: String, invoiceId: String): ApiResponse<String> {
        if (apiKey.isBlank()) return keyMissing()
        val request = Request.Builder()
            .url(http.apiUrl("user", "pay_invoice", invoiceId).build())
            .header(HttpHeaders.Authorization, http.basicAuth(apiKey))
            .get()
            .build()
        // The answer is a redirect to the checkout page, which is opened in the browser rather than followed here
        val client = http.okHttpClient.newBuilder().followRedirects(false).followSslRedirects(false).build()
        return try {
            val call = client.newCall(request)
            http.executeCancellable(call) {
                call.execute().use { response ->
                    val location = response.header(HttpHeaders.Location)
                    when {
                        response.isRedirect && !location.isNullOrBlank() -> ApiResponse.Success(response.request.url.resolve(location)?.toString() ?: location)
                        else -> ApiResponse.Error(http.parseError(response.code, response.body.string(), "pay_invoice_failed_status"))
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG_USER_API, "Exception for GET pay_invoice: ${e.message}", e)
            ApiResponse.Error(FileUploadResponse(success = false, value = "network_exception_pay_invoice", message = e.message ?: "Network request failed"))
        }
    }

    override suspend fun setFileCustomization(apiKey: String, fields: Map<String, String>): ApiResponse<FileUploadResponse> =
        call(apiKey, Request.Builder().url(http.apiUrl("user", "file_customization").build()).put(form(fields)), "file_customization")

    override suspend fun createSession(apiKey: String, appName: String): ApiResponse<LoginSession> {
        val fields = if (appName.isNotBlank()) mapOf("app_name" to appName.trim()) else emptyMap()
        return call(apiKey, Request.Builder().url(http.apiUrl("user", "session").build()).post(form(fields)), "create_session")
    }

    override suspend fun getSessions(apiKey: String): ApiResponse<List<LoginSession>> =
        call<List<LoginSession>?>(apiKey, Request.Builder().url(http.apiUrl("user", "session").build()).get(), "sessions")
            .orEmptyList()

    override suspend fun generateTotp(apiKey: String): ApiResponse<TotpSecret> =
        call(apiKey, Request.Builder().url(http.apiUrl("user", "totp").build()).post(form(mapOf("action" to "generate"))), "totp_generate")

    override suspend fun verifyTotp(apiKey: String, secret: String, otp: String): ApiResponse<FileUploadResponse> {
        val fields = mapOf("action" to "verify", "secret" to secret, "otp" to otp.trim())
        return call(apiKey, Request.Builder().url(http.apiUrl("user", "totp").build()).post(form(fields)), "totp_verify")
    }

    override suspend fun disableTotp(apiKey: String): ApiResponse<FileUploadResponse> =
        call(apiKey, Request.Builder().url(http.apiUrl("user", "totp").build()).post(form(mapOf("action" to "delete"))), "totp_delete")

    private fun form(fields: Map<String, String>): FormBody =
        FormBody.Builder().apply { fields.forEach { (name, value) -> add(name, value) } }.build()

    private fun <T> keyMissing(): ApiResponse<T> =
        ApiResponse.Error(FileUploadResponse(success = false, value = "api_key_missing", message = "Log in or enter an API key first."))

    /**
     * Sends [request] with the API key and reads a successful answer as [T]. An answer without a body (some of the
     * modifying calls) reads as the plain "ok" one when [T] is that.
     */
    private suspend inline fun <reified T> call(apiKey: String, request: Request.Builder, name: String): ApiResponse<T> {
        if (apiKey.isBlank()) return keyMissing()
        return try {
            val raw = http.send(request.header(HttpHeaders.Authorization, http.basicAuth(apiKey)).build())
            when {
                !raw.isSuccessful -> ApiResponse.Error(http.parseError(raw.code, raw.body, "${name}_failed_status"))
                raw.body.isBlank() && T::class == FileUploadResponse::class ->
                    ApiResponse.Success(FileUploadResponse(success = true, value = "ok") as T)
                else -> ApiResponse.Success(accountJson.decodeFromString<T>(raw.body))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Never log the request: it can contain a password
            Log.e(TAG_USER_API, "Exception during $name: ${e.javaClass.simpleName}: ${e.message}")
            ApiResponse.Error(FileUploadResponse(success = false, value = "network_exception_$name", message = e.message ?: "Network request failed"))
        }
    }

    private fun <T> ApiResponse<List<T>?>.orEmptyList(): ApiResponse<List<T>> = when (this) {
        is ApiResponse.Success -> ApiResponse.Success(data.orEmpty())
        is ApiResponse.Error -> ApiResponse.Error(errorDetails)
    }
}
