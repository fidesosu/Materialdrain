package tools.senko.materialdrain.provider

import androidx.compose.material.icons.Icons
import tools.senko.materialdrain.ui.icons.filled.Dns
import tools.senko.materialdrain.ui.icons.filled.FolderShared
import tools.senko.materialdrain.ui.icons.filled.Share
import tools.senko.materialdrain.ui.icons.filled.Storage
import androidx.compose.ui.graphics.vector.ImageVector
import tools.senko.materialdrain.hosts.HealthTarget
import tools.senko.materialdrain.provider.api.AuthType
import tools.senko.materialdrain.provider.api.ConfigUpdates
import tools.senko.materialdrain.provider.api.Credentials
import tools.senko.materialdrain.provider.api.GenericRestConfig
import tools.senko.materialdrain.provider.api.ProviderConfig
import tools.senko.materialdrain.provider.api.S3Config
import tools.senko.materialdrain.provider.api.SmbAuthMode
import tools.senko.materialdrain.provider.api.SmbConfig
import tools.senko.materialdrain.provider.api.StorageProvider
import tools.senko.materialdrain.provider.api.WebDavConfig
import tools.senko.materialdrain.provider.api.allowsAccountFallback
import tools.senko.materialdrain.provider.genericrest.GenericRestStorageProvider
import tools.senko.materialdrain.provider.s3.S3StorageProvider
import tools.senko.materialdrain.provider.smb.SmbStorageProvider
import tools.senko.materialdrain.provider.webdav.WebDavStorageProvider
import androidx.annotation.StringRes
import tools.senko.materialdrain.R

/** The two halves of a sign-in, as a kind of host calls them: "Access Key ID" and "Secret Access Key" for S3. */
data class KeyPairLabels(@param:StringRes val username: Int, @param:StringRes val password: Int)

/**
 * What the app knows of one kind of configured host, in one place: its names and icon, where it is, what the health
 * check probes, how it's signed into and how its provider is built. Adding a kind of host is adding one of these (next
 * to its config class and provider module) and its line in [hostTypeOf], instead of a branch in every screen.
 *
 * Made for one host's config, so what depends on the config is a plain value.
 */
sealed class HostType {
    /** Short, in the host switcher: "S3" */
    @get:StringRes abstract val shortLabel: Int
    /** In the host settings: "S3-compatible" */
    @get:StringRes abstract val label: Int
    abstract val icon: ImageVector
    /** Where the host is, as the switcher shows it, e.g. "nas.local/Documents"; null when its config has none yet */
    abstract val address: String?
    /** What the host health check probes; null when its config has no address yet */
    abstract val healthTarget: HealthTarget?
    /** Whether there's anything to sign in to: a share open to guests, or a REST API without auth, has nothing */
    open val needsSignIn: Boolean get() = true
    /**
     * For a host which signs in with two keys and has no API key to fall back to (S3, SMB), what the two are called. Null
     * for hosts which take an API key, and maybe a username and password (see ProviderConfig.passwordAuth).
     */
    open val keyPairLabels: KeyPairLabels? get() = null
    /** Shown in the host settings instead of the sign-in fields, when [needsSignIn] is false */
    @get:StringRes open val noSignInNote: Int? get() = null

    /**
     * Builds the host's provider. [accountCredentials] is the account's own login (Settings → Account), for the hosts
     * which may fall back to it.
     */
    abstract fun build(id: String, configStore: ProviderConfigStore, accountCredentials: () -> Credentials): StorageProvider
}

/** The [HostType] of a configured host. */
fun hostTypeOf(config: ProviderConfig): HostType = when (config) {
    is GenericRestConfig -> GenericRestHost(config)
    is WebDavConfig -> WebDavHost(config)
    is S3Config -> S3Host(config)
    is SmbConfig -> SmbHost(config)
}

private class GenericRestHost(private val config: GenericRestConfig) : HostType() {
    override val shortLabel = R.string.hosts_kind_rest_short
    override val label = R.string.hosts_kind_rest
    override val icon = Icons.Filled.Dns
    override val address get() = ConfigUpdates.hostOf(config.baseUrl)
    override val healthTarget get() = HealthTarget.originOf(config.baseUrl)
    override val needsSignIn get() = config.auth.type != AuthType.NONE

    override fun build(id: String, configStore: ProviderConfigStore, accountCredentials: () -> Credentials) =
        GenericRestStorageProvider(
            id = id,
            config = config,
            credentials = {
                // The host's own sign-in or API key. A host with `account_fallback` and none of its own uses the
                // account's login instead, so Pixeldrain works from the Account settings alone.
                val own = configStore.credentials(id)
                val hasOwn = own.apiKey.isNotBlank() || own.loginToken != null || own.hasPassword
                if (!hasOwn && allowsAccountFallback(config)) accountCredentials() else own
            },
            onLoginToken = { configStore.saveLoginToken(id, it) }
        )
}

private class WebDavHost(private val config: WebDavConfig) : HostType() {
    override val shortLabel = R.string.hosts_kind_webdav
    override val label = R.string.hosts_kind_webdav
    override val icon = Icons.Filled.FolderShared
    override val address get() = ConfigUpdates.hostOf(config.baseUrl)
    override val healthTarget get() = HealthTarget.originOf(config.baseUrl)

    override fun build(id: String, configStore: ProviderConfigStore, accountCredentials: () -> Credentials) =
        WebDavStorageProvider(id, config, credentials = { configStore.credentials(id) })
}

private class S3Host(private val config: S3Config) : HostType() {
    override val shortLabel = R.string.hosts_kind_s3_short
    override val label = R.string.hosts_kind_s3
    override val icon = Icons.Filled.Storage
    override val address get() = ConfigUpdates.hostOf(config.endpoint)?.let { host -> config.bucket.takeIf { it.isNotBlank() }?.let { "$host/$it" } ?: host }
    override val healthTarget get() = HealthTarget.originOf(config.endpoint)
    // Both halves of a key pair, always: S3 has no single API key
    override val keyPairLabels = KeyPairLabels(R.string.hosts_access_key_id, R.string.hosts_secret_access_key)

    override fun build(id: String, configStore: ProviderConfigStore, accountCredentials: () -> Credentials) =
        S3StorageProvider(id, config, credentials = { configStore.credentials(id) })
}

private class SmbHost(private val config: SmbConfig) : HostType() {
    override val shortLabel = R.string.hosts_kind_smb_short
    override val label = R.string.hosts_kind_smb
    override val icon = Icons.Filled.Share
    override val address get() = config.host.trim().takeIf { it.isNotEmpty() }?.let { host -> "$host/${config.share.trim('/', '\\')}" }
    // SMB isn't HTTP: the check connects to its port
    override val healthTarget get() = config.host.trim().takeIf { it.isNotEmpty() }?.let { HealthTarget.Tcp(it, config.port) }
    // A share can be open to guests or anonymous: then there's nothing to sign in to
    override val needsSignIn get() = config.auth == SmbAuthMode.CREDENTIALS
    override val keyPairLabels = KeyPairLabels(R.string.settings_username, R.string.settings_password)
    override val noSignInNote get() = R.string.hosts_guest_share

    override fun build(id: String, configStore: ProviderConfigStore, accountCredentials: () -> Credentials) =
        SmbStorageProvider(id, config, credentials = { configStore.credentials(id) })
}
