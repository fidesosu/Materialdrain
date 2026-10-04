package tools.senko.materialdrain.provider.genericrest

import kotlinx.serialization.json.JsonElement
import tools.senko.materialdrain.provider.api.AuthConfig
import tools.senko.materialdrain.provider.api.AuthType
import tools.senko.materialdrain.provider.api.Credentials
import tools.senko.materialdrain.provider.api.LoginEndpoint
import tools.senko.materialdrain.provider.api.PasswordAuthMode
import tools.senko.materialdrain.provider.api.ResponseCondition
import tools.senko.materialdrain.provider.api.SignInResult
import java.util.Base64

/** Which of the saved credentials a request uses. */
internal sealed interface AuthPlan {
    /** Username and password sent with every request as HTTP Basic ([PasswordAuthMode.BASIC]). */
    data class PasswordBasic(val username: String, val password: String) : AuthPlan
    /** The session from signing in ([PasswordAuthMode.LOGIN]). */
    data class SignedIn(val token: String) : AuthPlan
    data class ApiKey(val key: String) : AuthPlan
    data object None : AuthPlan
}

internal object AuthHeaders {

    /**
     * The user's own account wins: a signed-in session, or a username and password sent directly, when the host
     * takes them. Otherwise the API key; otherwise nothing. Requests never sign in by themselves: that only
     * happens when the user taps Sign in (a two-factor host would need a code anyway), so a session host without
     * a session just falls back to the API key.
     */
    fun plan(auth: AuthConfig, credentials: Credentials): AuthPlan {
        when (auth.passwordAuth?.mode) {
            PasswordAuthMode.LOGIN -> credentials.loginToken?.let { return AuthPlan.SignedIn(it) }
            PasswordAuthMode.BASIC -> if (credentials.hasPassword) return AuthPlan.PasswordBasic(credentials.username, credentials.password)
            null -> {}
        }
        if (credentials.apiKey.isNotBlank() && auth.type != AuthType.NONE) return AuthPlan.ApiKey(credentials.apiKey.trim())
        return AuthPlan.None
    }

    /** Attaches an API key, or a session token, the way [auth] says. */
    fun forKey(auth: AuthConfig, key: String): Pair<String, String>? = when (auth.type) {
        AuthType.NONE -> null
        AuthType.BASIC -> "Authorization" to basic(auth.keyUsername, key)
        AuthType.BEARER -> "Authorization" to "Bearer $key"
        AuthType.HEADER -> auth.headerName to key
    }

    fun basic(username: String, password: String): String =
        "Basic " + Base64.getEncoder().encodeToString("$username:$password".toByteArray(Charsets.UTF_8))

    /**
     * The sign-in request's fields with {username}, {password} and {otp} filled in. A field that is only "{otp}"
     * is left out without a code, so the first attempt is a plain password sign-in.
     */
    fun loginFields(fields: Map<String, String>, username: String, password: String, otp: String?): Map<String, String> =
        fields.filterNot { (_, value) -> value.trim() == "{otp}" && otp.isNullOrBlank() }
            .mapValues { (_, value) ->
                value.replace("{username}", username).replace("{password}", password).replace("{otp}", otp.orEmpty())
            }

    /**
     * What a sign-in response means. The two-factor conditions are checked before the status code, because hosts
     * report "code needed" as an error status (Pixeldrain: HTTP 400 with "value": "otp_required").
     */
    fun signInResult(login: LoginEndpoint, code: Int, body: JsonElement?, otpSent: Boolean): SignInResult {
        val message = DotPath.errorMessage(body) ?: "HTTP $code"
        login.otp?.let { otp ->
            if (otp.rejectedWhen?.matches(body) == true) return SignInResult.OtpRejected(otp.digits, message)
            if (otp.requiredWhen.matches(body)) {
                return if (otpSent) SignInResult.OtpRejected(otp.digits, message) else SignInResult.OtpRequired(otp.digits)
            }
        }
        if (code !in 200..299) return SignInResult.Failed(message)
        val token = body?.let { DotPath.resolveString(it, login.token) }
            ?: return SignInResult.Failed("Signed in, but the response has no token at ${login.token}.")
        return SignInResult.Success(token)
    }

    /**
     * Checked against the top-level body first, then (same as [DotPath.errorMessage]) against each entry of
     * an "errors" array when present — a host that wraps multiple problems in one response (Pixeldrain's
     * "multiple_errors") can just as easily put the one this condition is looking for inside that array
     * instead of at the top level.
     */
    private fun ResponseCondition.matches(body: JsonElement?): Boolean {
        if (body == null) return false
        if (DotPath.resolveString(body, path) == equals) return true
        return DotPath.errorEntries(body).any { DotPath.resolveString(it, path) == equals }
    }
}
