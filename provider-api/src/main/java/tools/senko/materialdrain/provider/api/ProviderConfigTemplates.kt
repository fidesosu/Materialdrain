package tools.senko.materialdrain.provider.api

/**
 * Ready-to-fill configs, one per kind built into the app: every field the app reads for that kind is in them, with its
 * default or an example value, so a new host starts from a complete config instead of a blank page. Made from the
 * config classes themselves (see [ProviderConfigCodec.encode] with all fields), so a field added to a kind shows up in
 * its template without anyone having to remember to.
 *
 * None of them is a particular product's: the addresses are example.com, and the REST one describes a made-up API with
 * every endpoint there is, to show what each one maps. docs/provider-configs has each of them as a file (see
 * ExampleConfigsTest); the bundled Pixeldrain config there is a real host, not a template.
 */
object ProviderConfigTemplates {

    /** The kinds with a template, by the name a config's "kind" field uses. */
    enum class Kind(val label: String) { WEBDAV("WebDAV"), S3("S3"), SMB("SMB"), REST("REST API") }

    fun config(kind: Kind): ProviderConfig = when (kind) {
        Kind.WEBDAV -> WebDavConfig(
            name = "WebDAV server",
            baseUrl = "https://files.example.com/dav",
            auth = AuthConfig(type = AuthType.BASIC, passwordAuth = PasswordAuth(mode = PasswordAuthMode.BASIC)),
            rootPath = "/",
            screens = folderHostScreens(),
            meta = meta("webdav")
        )
        Kind.S3 -> S3Config(
            name = "S3 storage",
            endpoint = "https://s3.example.com",
            region = "us-east-1",
            bucket = "my-bucket",
            screens = folderHostScreens(),
            meta = meta("s3")
        )
        Kind.SMB -> SmbConfig(
            name = "SMB share",
            host = "nas.local",
            share = "Documents",
            screens = folderHostScreens(),
            meta = meta("smb")
        )
        Kind.REST -> GenericRestConfig(
            name = "REST API",
            baseUrl = "https://files.example.com/api",
            auth = AuthConfig(
                type = AuthType.BEARER,
                passwordAuth = PasswordAuth(
                    mode = PasswordAuthMode.LOGIN,
                    login = LoginEndpoint(path = "/auth/login", body = LoginBody.JSON, token = "$.token"),
                    logout = EndpointConfig("DELETE", "/auth/session")
                )
            ),
            endpoints = restEndpoints(),
            screens = listOf(
                ScreenConfig(HostScreen.UPLOAD, id = "upload"),
                ScreenConfig(HostScreen.FILES, id = "files"),
                ScreenConfig(HostScreen.LISTS, id = "lists"),
                ScreenConfig(HostScreen.FILESYSTEM, id = "filesystem")
            ),
            meta = meta("rest")
        )
    }

    /** [config] as the text to paste into the app, with every field written out. */
    fun text(kind: Kind): String = ProviderConfigCodec.encode(config(kind), allFields = true)

    /**
     * The screens a folder host (WebDAV, S3, SMB) can back, written out so the template shows what can be set: the same
     * as leaving "screens" out, but easy to trim down, rename, or copy into a second Filesystem tab (another id and
     * root). They have no flat file list or lists of their own.
     */
    private fun folderHostScreens(): List<ScreenConfig> =
        listOf(ScreenConfig(HostScreen.UPLOAD, id = "upload"), ScreenConfig(HostScreen.FILESYSTEM, id = "filesystem"))

    /**
     * Every endpoint a REST host can have, for a made-up API: uploaded files by id under /files, lists under /lists, and
     * folders by path under /tree (listing and changes), /content (a file's bytes), /zip and /thumbnail.
     */
    private fun restEndpoints(): Map<String, EndpointConfig> {
        val file = mapOf("id" to "$.id", "name" to "$.name", "size" to "$.size", "mime_type" to "$.mime_type")
        val listed = file + ("created" to "$.created")
        val node = mapOf("name" to "$.name", "path" to "$.path", "size" to "$.size", "mime_type" to "$.mime_type", "id" to "$.id")
        val list = """{"title": {title_json}, "files": {files_json}}"""
        return linkedMapOf(
            "upload" to EndpointConfig("PUT", "/files/{filename}", successStatus = listOf(201), responseMap = file),
            "download" to EndpointConfig("GET", "/files/{id}/content"),
            "download_archive" to EndpointConfig("GET", "/files/zip", query = mapOf("ids" to "{ids}")),
            "archive_info" to EndpointConfig("GET", "/zip/{path}", query = mapOf("entries" to "")),
            "archive_file" to EndpointConfig("GET", "/zip/{path}", query = mapOf("entry" to "{entry}")),
            "delete" to EndpointConfig("DELETE", "/files/{id}", successStatus = listOf(200, 204)),
            "file_info" to EndpointConfig("GET", "/files/{id}", responseMap = file - "id"),
            "user_info" to EndpointConfig(
                "GET", "/me",
                responseMap = mapOf("username" to "$.username", "quota_used" to "$.storage.used", "quota_total" to "$.storage.total")
            ),
            "list" to EndpointConfig("GET", "/files", listPath = "$.files", responseMap = listed),
            "browse_list" to EndpointConfig(
                "GET", "/tree/{path}",
                listPath = "$.children",
                breadcrumbPath = "$.path",
                listingMap = mapOf("can_write" to "$.permissions.write", "can_delete" to "$.permissions.delete"),
                responseMap = node + mapOf("is_directory" to "$.type", "created" to "$.created", "modified" to "$.modified")
            ),
            "browse_upload" to EndpointConfig("PUT", "/content/{path}", query = mapOf("make_parents" to "{make_parents}"), responseMap = node),
            "browse_download" to EndpointConfig("GET", "/content/{path}"),
            "browse_download_archive" to EndpointConfig("GET", "/zip/{path}"),
            "browse_thumbnail" to EndpointConfig("GET", "/thumbnail/{path}"),
            "browse_mkdir" to EndpointConfig("POST", "/tree/{path}", form = mapOf("action" to "{action}"), successStatus = listOf(201)),
            "browse_rename" to EndpointConfig(
                "POST", "/tree/{path}",
                form = mapOf("action" to "rename", "target" to "{target}", "make_parents" to "{make_parents}"),
                responseMap = mapOf("name" to "$.name", "path" to "$.path")
            ),
            "browse_delete" to EndpointConfig("DELETE", "/tree/{path}", query = mapOf("recursive" to "{recursive}")),
            "browse_import" to EndpointConfig(
                "POST", "/tree/{path}", form = mapOf("action" to "import", "files" to "{files_json}"), successStatus = listOf(201)
            ),
            "user_lists" to EndpointConfig(
                "GET", "/lists",
                listPath = "$.lists",
                responseMap = mapOf("id" to "$.id", "title" to "$.title", "file_count" to "$.file_count", "can_edit" to "$.can_edit")
            ),
            "list_info" to EndpointConfig(
                "GET", "/lists/{list_id}",
                listPath = "$.files",
                listingMap = mapOf("title" to "$.title", "can_edit" to "$.can_edit"),
                responseMap = listed
            ),
            "list_create" to EndpointConfig(
                "POST", "/lists", body = BodyEncoding.JSON, json = list, successStatus = listOf(201), responseMap = mapOf("id" to "$.id")
            ),
            "list_update" to EndpointConfig("PUT", "/lists/{list_id}", body = BodyEncoding.JSON, json = list),
            "list_delete" to EndpointConfig("DELETE", "/lists/{list_id}", successStatus = listOf(200, 204)),
            "thumbnail_id" to EndpointConfig("GET", "/files/{id}/thumbnail"),
            "raw_id" to EndpointConfig("GET", "/files/{id}/content"),
            "share_id" to EndpointConfig("GET", "https://files.example.com/f/{id}"),
            "share_path" to EndpointConfig("GET", "https://files.example.com/d/{path}"),
            "share_list" to EndpointConfig("GET", "https://files.example.com/l/{list_id}")
        )
    }

    private fun meta(kind: String) = ProviderConfigMeta(id = "example.$kind", version = 1, author = "")
}
