package tools.senko.materialdrain.provider.smb

import tools.senko.materialdrain.provider.api.SmbConfig
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * The paths the browser uses and the addresses SMB needs for them. A browser path is relative to the top of the share,
 * with "/" between the names; "" is the top itself. Kept free of any SMB call so it can be tested on the JVM.
 */
internal object SmbPaths {

    /** A browser path with no leading, trailing or doubled separators, "" for the top of the share. */
    fun normalize(path: String): String = path.split('/', '\\').filter { it.isNotBlank() }.joinToString("/") { it.trim() }

    /** The smb:// address of [path]. A folder gets its trailing "/", which is how SMB tells a folder from a file. */
    fun url(config: SmbConfig, path: String, directory: Boolean): String {
        val share = encodeSegment(config.share.trim('/', '\\'))
        val inside = normalize(path).split('/').filter { it.isNotEmpty() }.joinToString("/") { encodeSegment(it) }
        return buildString {
            append("smb://").append(config.host.trim())
            // The default port is left out, as the address of a host normally has no port
            if (config.port != DEFAULT_PORT) append(':').append(config.port)
            append('/').append(share).append('/')
            if (inside.isNotEmpty()) {
                append(inside)
                if (directory) append('/')
            }
        }
    }

    fun parentOf(path: String): String = normalize(path).substringBeforeLast('/', "")

    fun nameOf(path: String): String = normalize(path).substringAfterLast('/')

    private const val DEFAULT_PORT = 445

    /** The name of an item as the user sees it. jCIFS hands names back percent-encoded ("test.zip%20%282%29"), so they're decoded. */
    fun displayName(encoded: String): String = try {
        URLDecoder.decode(encoded.replace("+", "%2B"), "UTF-8")
    } catch (_: Exception) {
        encoded
    }

    /** Percent-encodes one name for an address; "+" would be read as a space by some servers, so it's written as %20. */
    private fun encodeSegment(segment: String): String = URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
}
