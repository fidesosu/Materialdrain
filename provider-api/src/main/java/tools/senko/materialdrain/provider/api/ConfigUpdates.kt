package tools.senko.materialdrain.provider.api

/**
 * A change an update makes to where the user's credentials would be sent. Configs hold no secrets, but they
 * decide where the app sends them, so an update with any of these is never applied without the user's approval.
 */
data class SensitiveChange(val kind: Kind, val description: String) {
    enum class Kind {
        /** Requests (with credentials) would go to a scheme/host/port they didn't go to before. */
        DESTINATION,
        /** How credentials are attached changed (auth type or header name). */
        AUTH,
        /** Future updates would come from somewhere else. */
        UPDATE_SOURCE,
        /** The update is a different kind of host altogether. */
        KIND
    }
}

sealed interface UpdateCheckResult {
    data object UpToDate : UpdateCheckResult
    data class Available(val update: ProviderConfig, val sensitiveChanges: List<SensitiveChange>) : UpdateCheckResult
    /** Newer, but needs a newer app ([ProviderConfigMeta.minAppVersion]). */
    data class NeedsNewerApp(val update: ProviderConfig, val minAppVersion: Int) : UpdateCheckResult
    data class Invalid(val reason: String) : UpdateCheckResult
}

/** scheme :// [userinfo@] host [:port] — tolerant of {placeholders} in the path, which java.net.URI rejects. */
private val ORIGIN_REGEX = Regex("""^([a-zA-Z][a-zA-Z0-9+.-]*)://(?:[^@/?#]*@)?(\[[^\]]+]|[^/?#:]+)(?::(\d+))?""")

object ConfigUpdates {

    /** Host of [url] for display ("Updates from raw.githubusercontent.com"), null when it can't be parsed. */
    fun hostOf(url: String?): String? = url?.let { ORIGIN_REGEX.find(it.trim())?.groupValues?.get(2)?.lowercase() }

    fun isHttps(url: String): Boolean = url.trim().startsWith("https://", ignoreCase = true)

    /**
     * Decides what [remoteText] (the file at the config's update URL) means for the installed [local] config.
     * Versions are the contract: a remote copy with the same version counts as up to date even if its content
     * differs, so authors must bump [ProviderConfigMeta.version] to publish an update.
     */
    fun evaluate(local: ProviderConfig, remoteText: String, appVersion: Int): UpdateCheckResult {
        val remote = ProviderConfigCodec.decode(remoteText)
            ?: return UpdateCheckResult.Invalid("The update URL doesn't point to a valid provider config.")

        val localId = local.meta?.id
        val remoteId = remote.meta?.id
        if (localId != null && remoteId != localId) {
            return UpdateCheckResult.Invalid("The update URL now serves a different config (\"${remoteId ?: "no id"}\").")
        }

        val localVersion = local.meta?.version ?: 0
        val remoteVersion = remote.meta?.version ?: 0
        if (remoteVersion <= localVersion) return UpdateCheckResult.UpToDate

        val minApp = remote.meta?.minAppVersion
        if (minApp != null && minApp > appVersion) return UpdateCheckResult.NeedsNewerApp(remote, minApp)

        return UpdateCheckResult.Available(remote, sensitiveChanges(local, remote))
    }

    /** Everything about going from [old] to [new] which changes where, or how, credentials are sent. */
    fun sensitiveChanges(old: ProviderConfig, new: ProviderConfig): List<SensitiveChange> = buildList {
        if (old::class != new::class) {
            add(SensitiveChange(SensitiveChange.Kind.KIND, "Becomes a different kind of host (${kindName(old)} → ${kindName(new)})"))
        }

        val oldOrigins = originsOf(old)
        (originsOf(new) - oldOrigins).forEach { origin ->
            add(SensitiveChange(SensitiveChange.Kind.DESTINATION, "Sends requests to $origin"))
        }

        val oldAuth = authOf(old)
        val newAuth = authOf(new)
        if (oldAuth != null && newAuth != null) {
            if (oldAuth.type != newAuth.type) {
                add(SensitiveChange(SensitiveChange.Kind.AUTH, "Sends credentials as ${newAuth.type} instead of ${oldAuth.type}"))
            } else if (newAuth.type == AuthType.HEADER && oldAuth.headerName != newAuth.headerName) {
                add(SensitiveChange(SensitiveChange.Kind.AUTH, "Sends credentials in the \"${newAuth.headerName}\" header instead of \"${oldAuth.headerName}\""))
            } else if (oldAuth.passwordAuth != newAuth.passwordAuth) {
                add(SensitiveChange(SensitiveChange.Kind.AUTH, "Changes how your username and password are used to sign in"))
            } else if (oldAuth != newAuth) {
                add(SensitiveChange(SensitiveChange.Kind.AUTH, "Changes how credentials are attached to requests"))
            }
        }

        // Dropping the update URL only stops updates, only a new or different source is sensitive
        val oldUpdateUrl = old.meta?.updateUrl
        val newUpdateUrl = new.meta?.updateUrl
        if (newUpdateUrl != null && newUpdateUrl != oldUpdateUrl) {
            add(SensitiveChange(SensitiveChange.Kind.UPDATE_SOURCE, "Future updates come from ${hostOf(newUpdateUrl) ?: newUpdateUrl}"))
        }
    }

    /**
     * scheme://host[:port] of every URL a config can send credentials to. A URL that can't be parsed is kept
     * whole instead of dropped, so any change to it still shows up as a new destination.
     */
    private fun originsOf(config: ProviderConfig): Set<String> {
        val base = when (config) {
            is GenericRestConfig -> config.baseUrl
            is WebDavConfig -> config.baseUrl
            is S3Config -> config.endpoint
            is SmbConfig -> "smb://${config.host.trim().lowercase()}:${config.port}"
        }
        val paths = (config as? GenericRestConfig)?.endpoints?.values?.map { it.path }.orEmpty() +
            listOfNotNull(config.passwordAuth?.login?.path, config.passwordAuth?.logout?.path)
        // Relative paths go to the base URL's host, which is already counted
        return (listOf(base) + paths.filter(::isAbsolute)).map { origin(it) ?: it.trim() }.toSet()
    }

    private fun isAbsolute(path: String) = path.trimStart().let { it.startsWith("http://", true) || it.startsWith("https://", true) }

    private fun origin(url: String): String? {
        val match = ORIGIN_REGEX.find(url.trim()) ?: return null
        val (scheme, host, port) = match.destructured
        val base = "${scheme.lowercase()}://${host.lowercase()}"
        return if (port.isEmpty()) base else "$base:$port"
    }

    private fun authOf(config: ProviderConfig): AuthConfig? = when (config) {
        is GenericRestConfig -> config.auth
        is WebDavConfig -> config.auth
        is S3Config -> null
        is SmbConfig -> null
    }

    private fun kindName(config: ProviderConfig) = when (config) {
        is GenericRestConfig -> "generic REST"
        is WebDavConfig -> "WebDAV"
        is S3Config -> "S3"
        is SmbConfig -> "SMB"
    }
}
