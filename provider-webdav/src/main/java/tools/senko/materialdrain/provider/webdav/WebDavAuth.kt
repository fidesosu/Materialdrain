package tools.senko.materialdrain.provider.webdav

import tools.senko.materialdrain.provider.api.AuthConfig
import tools.senko.materialdrain.provider.api.AuthType
import tools.senko.materialdrain.provider.api.Credentials
import tools.senko.materialdrain.provider.api.PasswordAuthMode
import java.util.Base64

/** An auth header (name to value); null sends none. */
internal typealias AuthHeader = Pair<String, String>?

/**
 * Resolves the auth header for a WebDAV request. Unlike the generic-REST module, there's no sign-in
 * endpoint to speak of (every request authenticates directly), so this is a trimmed version of
 * `provider-generic-rest`'s `AuthHeaders`: just the username+password sent with every request
 * ([PasswordAuthMode.BASIC], an explicit choice in the config) or an API key applied the way [AuthConfig]
 * says - which for the common case of [AuthType.BASIC] with a fixed [AuthConfig.keyUsername] is how an
 * "app password" (Nextcloud, ownCloud) is normally used: the account name lives in the config, the
 * per-device app password is what the user enters as the API key.
 */
internal object WebDavAuth {
    fun resolve(auth: AuthConfig, credentials: Credentials): AuthHeader {
        if (auth.passwordAuth?.mode == PasswordAuthMode.BASIC && credentials.hasPassword) {
            return "Authorization" to basic(credentials.username, credentials.password)
        }
        if (credentials.apiKey.isNotBlank() && auth.type != AuthType.NONE) {
            return forKey(auth, credentials.apiKey.trim())
        }
        return null
    }

    private fun forKey(auth: AuthConfig, key: String): AuthHeader = when (auth.type) {
        AuthType.NONE -> null
        AuthType.BASIC -> "Authorization" to basic(auth.keyUsername, key)
        AuthType.BEARER -> "Authorization" to "Bearer $key"
        AuthType.HEADER -> auth.headerName to key
    }

    private fun basic(username: String, password: String): String =
        "Basic " + Base64.getEncoder().encodeToString("$username:$password".toByteArray(Charsets.UTF_8))
}
