package tools.senko.materialdrain.provider.api

/**
 * Ready-to-fill configs, one per kind built into the app: every field the app reads for that kind is in them, with its
 * default or an example value, so a new host starts from a complete config instead of a blank page. Made from the
 * config classes themselves (see [ProviderConfigCodec.encode] with all fields), so a field added to a kind shows up in
 * its template without anyone having to remember to.
 *
 * `generic_rest` has no template here: what such a config can hold is mostly its endpoints, which only make sense for a
 * real API. The app offers the bundled Pixeldrain config for it, which uses every endpoint there is.
 */
object ProviderConfigTemplates {

    /** The kinds with a template, by the name a config's "kind" field uses. */
    enum class Kind(val label: String) { WEBDAV("WebDAV"), S3("S3"), SMB("SMB") }

    fun config(kind: Kind): ProviderConfig = when (kind) {
        Kind.WEBDAV -> WebDavConfig(
            name = "Nextcloud",
            baseUrl = "https://cloud.example.com/remote.php/dav/files/{username}",
            auth = AuthConfig(type = AuthType.BASIC, passwordAuth = PasswordAuth(mode = PasswordAuthMode.BASIC)),
            rootPath = "/",
            screens = folderHostScreens(),
            meta = meta("webdav")
        )
        Kind.S3 -> S3Config(
            name = "MinIO",
            endpoint = "https://nas.example.com:9000",
            region = "us-east-1",
            bucket = "my-bucket",
            screens = folderHostScreens(),
            meta = meta("s3")
        )
        Kind.SMB -> SmbConfig(
            name = "NAS share",
            host = "nas.local",
            share = "Documents",
            screens = folderHostScreens(),
            meta = meta("smb")
        )
    }

    /** [config] as the text to paste into the app, with every field written out. */
    fun text(kind: Kind): String = ProviderConfigCodec.encode(config(kind), allFields = true)

    /**
     * The screens a folder host (WebDAV, S3, SMB) can back, written out so the template shows what can be set: the same
     * as leaving "screens" out, but easy to trim down or rename. They have no flat file list or lists of their own.
     */
    private fun folderHostScreens(): List<ScreenConfig> = listOf(ScreenConfig(HostScreen.UPLOAD), ScreenConfig(HostScreen.FILESYSTEM))

    private fun meta(kind: String) = ProviderConfigMeta(id = "example.$kind", version = 1, author = "")
}
