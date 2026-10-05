package tools.senko.materialdrain.provider.smb

import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import tools.senko.materialdrain.provider.api.Credentials
import tools.senko.materialdrain.provider.api.SmbAuthMode
import tools.senko.materialdrain.provider.api.SmbConfig
import tools.senko.materialdrain.provider.api.SmbMinVersion
import java.util.Properties

/**
 * The SMB connection of one host: the protocol settings from its config, and the sign-in the user entered. Every
 * [SmbFile] is made through here, so a change of username or password applies to the next request.
 *
 * Blocking: call it from a background thread (the provider does, on Dispatchers.IO).
 */
internal class SmbClient(private val config: SmbConfig, private val credentials: () -> Credentials) {

    // Created on first use, not here: the context looks up this device's name on the network, and the provider is built on
    // the main thread. Every use is already on a background thread (see SmbStorageProvider.onShare).
    private val base: BaseContext by lazy { BaseContext(PropertyConfiguration(protocolSettings(config))) }

    // The context for the sign-in in use; rebuilt only when the username or password changes
    private var signedIn: Triple<String, String, CIFSContext>? = null

    @Synchronized
    fun context(): CIFSContext {
        val login = credentials()
        val username = login.username.trim()
        val password = login.password
        signedIn?.takeIf { it.first == username && it.second == password }?.let { return it.third }
        val context = when (config.auth) {
            SmbAuthMode.CREDENTIALS -> base.withCredentials(NtlmPasswordAuthenticator(config.domain.trim(), username, password))
            SmbAuthMode.GUEST -> base.withGuestCrendentials()
            SmbAuthMode.ANONYMOUS -> base.withAnonymousCredentials()
        }
        signedIn = Triple(username, password, context)
        return context
    }

    /** The item at [path] (relative to the share). [directory] makes the address a folder's, see [SmbPaths.url]. */
    fun file(path: String, directory: Boolean): SmbFile = SmbFile(SmbPaths.url(config, path, directory), context())

    private companion object {
        /** The protocol choices of the config, in jCIFS's own names. SMB1 is never allowed. */
        fun protocolSettings(config: SmbConfig): Properties = Properties().apply {
            // Encryption only exists in SMB 3, so asking for it raises the minimum: a host without SMB 3 fails, rather
            // than quietly talking unencrypted
            val needsSmb3 = config.encrypt || config.minVersion == SmbMinVersion.SMB3
            setProperty("jcifs.smb.client.minVersion", if (needsSmb3) "SMB300" else "SMB202")
            setProperty("jcifs.smb.client.maxVersion", "SMB311")
            if (config.encrypt) setProperty("jcifs.smb.client.encryptionEnabled", "true")
            setProperty("jcifs.smb.client.responseTimeout", "30000")
            setProperty("jcifs.smb.client.connTimeout", "15000")
        }
    }
}
