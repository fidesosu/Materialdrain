package tools.senko.materialdrain.provider.api

sealed interface SignInResult {
    data class Success(val token: String) : SignInResult
    /** The host wants a two-factor code; sign in again with it. */
    data class OtpRequired(val digits: Int) : SignInResult
    /** The code was wrong (or expired); the user can try another. */
    data class OtpRejected(val digits: Int, val message: String) : SignInResult
    data class Failed(val message: String) : SignInResult
}

/**
 * A host whose username and password sign-in starts a session ([PasswordAuthMode.LOGIN]), like signing in to
 * any app. The caller keeps the returned token; the password itself isn't needed afterwards.
 */
interface PasswordSignIn {
    suspend fun signIn(username: String, password: String, otp: String? = null): SignInResult

    /** Ends the current session on the host when the config says how, otherwise does nothing remote. */
    suspend fun signOut()
}
