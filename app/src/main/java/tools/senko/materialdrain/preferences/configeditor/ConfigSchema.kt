package tools.senko.materialdrain.preferences.configeditor

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

/*
 * What the config editor knows about each kind of config: its fields, how each one is edited, and what it means. The
 * meanings are the ones in docs/provider-configuration.md, kept short: one line under the field, with the default when
 * there is one. The editor works on the config's JSON itself, so a field that isn't described here is still kept, and
 * shown as it is (see ConfigEditor's "Other fields").
 */

/** How a field is edited. */
sealed interface FieldKind {
    /** A line of text; [multiline] for longer text, e.g. a JSON body. */
    data class Text(val multiline: Boolean = false) : FieldKind
    data object Number : FieldKind
    /** On or off. */
    data object Toggle : FieldKind
    /** One of [options]: buttons side by side when there are a few, a dropdown when there are more. */
    data class Choice(val options: List<ChoiceOption>) : FieldKind
    /** An object with fields of its own, e.g. the sign-in. */
    data class Group(val fields: List<FieldSpec>) : FieldKind
    /** Names with a text value each, e.g. a response map; [suggestions] are the names the app reads. */
    data class KeyValues(val suggestions: List<Suggestion> = emptyList(), val valueHint: String = "") : FieldKind
    /** HTTP status codes, written "200, 201". */
    data object StatusCodes : FieldKind
    /** The tabs the host shows, see ScreensEditor. */
    data object Screens : FieldKind
    /** A generic REST config's requests, see EndpointsEditor. */
    data object Endpoints : FieldKind
}

data class ChoiceOption(val value: String, val label: String, val help: String? = null)

/** A name a key-value field can take, with what it's for; [value] is filled in when it's added. */
data class Suggestion(val key: String, val help: String, val value: String = "")

/**
 * One field of a config.
 *
 * @param default what the app uses while the field is left out, shown with it; null when there is none
 * @param required the config can't be used without it
 * @param common shown even while it's left out; the others are offered under "More options", so a config shows little at
 *   first but every field is a tap away
 * @param shownWhen only for the fields that mean something given the others in the same object, e.g. the header name of
 *   a sign-in sent in a header
 */
data class FieldSpec(
    val key: String,
    val label: String,
    val help: String,
    val kind: FieldKind = FieldKind.Text(),
    val default: JsonElement? = null,
    val required: Boolean = false,
    val common: Boolean = required,
    val placeholder: String? = null,
    val shownWhen: (JsonObject) -> Boolean = { true }
)

/** A part of the editor's form, folded away unless [startOpen]. */
data class Section(val title: String, val summary: String, val fields: List<FieldSpec>, val startOpen: Boolean = false)

/** The text of a field in [obj], e.g. a choice; null when it's left out or isn't text. */
fun JsonObject.textOf(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun choice(vararg options: ChoiceOption) = FieldKind.Choice(options.toList())
private fun str(value: String) = JsonPrimitive(value)

// ---- Shared parts ----

private val nameField = FieldSpec(
    "name", "Name", "The name the app shows for this host, in the switcher and the settings.",
    required = true, placeholder = "My NAS"
)

private val screensField = FieldSpec(
    "screens", "Screens",
    "Which tabs this host shows, and in what order. Left to the app, every tab the host can back is shown.",
    kind = FieldKind.Screens, common = true
)

private val metaSection = Section(
    "Sharing and updates",
    "Who made the config and where newer versions come from. None of it is secret.",
    listOf(
        FieldSpec(
            "meta", "Details", "", common = true,
            kind = FieldKind.Group(
                listOf(
                    FieldSpec(
                        "id", "Config id", "A stable name for the config. Importing one with the same id and update address replaces it.",
                        common = true, placeholder = "com.example.my-host"
                    ),
                    FieldSpec("version", "Version", "A whole number; raise it whenever you publish a change.", kind = FieldKind.Number, default = JsonPrimitive(0), common = true),
                    FieldSpec("author", "Author", "Shown in the app next to the version.", common = true),
                    FieldSpec(
                        "update_url", "Update address", "An https address of the raw config file, checked at most once a day for a newer version.",
                        placeholder = "https://example.com/my-host.json"
                    ),
                    FieldSpec("min_app_version", "Lowest app version", "Older apps say so instead of breaking.", kind = FieldKind.Number)
                )
            )
        )
    )
)

private val methodChoice = choice(
    ChoiceOption("GET", "GET"), ChoiceOption("POST", "POST"), ChoiceOption("PUT", "PUT"),
    ChoiceOption("PATCH", "PATCH"), ChoiceOption("DELETE", "DELETE"), ChoiceOption("HEAD", "HEAD")
)

private val bodyChoice = choice(
    ChoiceOption("RAW", "Raw", "The file itself, or nothing"),
    ChoiceOption("MULTIPART", "Multipart", "A file upload form"),
    ChoiceOption("FORM", "Form", "The form fields below"),
    ChoiceOption("JSON", "JSON", "The JSON text below")
)

private val responseFieldSuggestions = listOf(
    Suggestion("id", "The id of a file or list", "$.id"),
    Suggestion("name", "The file name, or a folder's title", "$.name"),
    Suggestion("path", "The path of a file or folder", "$.path"),
    Suggestion("is_directory", "Whether it's a folder (dir, directory, folder or true)", "$.type"),
    Suggestion("size", "The size in bytes", "$.size"),
    Suggestion("mime_type", "The file's type, for previews", "$.mime_type"),
    Suggestion("created", "When it was made", "$.created"),
    Suggestion("modified", "When it last changed", "$.modified"),
    Suggestion("username", "The account's name (user_info)", "$.username"),
    Suggestion("quota_used", "Storage used (user_info)", "$.storage_used"),
    Suggestion("quota_total", "Storage there is (user_info)", "$.storage_total"),
    Suggestion("title", "A list's title", "$.title"),
    Suggestion("file_count", "How many files a list has", "$.file_count"),
    Suggestion("can_edit", "Whether a list can be changed", "$.can_edit")
)

/** The fields of one request: an endpoint, or the sign-out. */
private fun requestFields(placeholders: String?): List<FieldSpec> = listOf(
    FieldSpec("method", "Method", "The HTTP method of the request.", kind = methodChoice, required = true, default = str("GET")),
    FieldSpec(
        "path", "Path",
        "Added to the base address, or a full https:// address." + (placeholders?.let { " Can use $it." } ?: ""),
        required = true, placeholder = "/files/{id}"
    ),
    FieldSpec("body", "Body", "What's sent with the request.", kind = bodyChoice, default = str("RAW")),
    FieldSpec(
        "query", "Query parameters", "Added to the address. A value that's just one {placeholder} with nothing to fill in is left out.",
        kind = FieldKind.KeyValues(valueHint = "{placeholder} or a fixed value")
    ),
    FieldSpec(
        "form", "Form fields", "Sent as the body when it's Form.", kind = FieldKind.KeyValues(valueHint = "{placeholder} or a fixed value"),
        shownWhen = { it.textOf("body") == "FORM" || it.containsKey("form") }
    ),
    FieldSpec(
        "json", "JSON body", "Sent as the body when it's JSON. {title_json} and {files_json} are filled in already encoded.",
        kind = FieldKind.Text(multiline = true), shownWhen = { it.textOf("body") == "JSON" || it.containsKey("json") },
        common = true
    ),
    FieldSpec(
        "success_status", "Success codes", "The HTTP status codes that count as success.", kind = FieldKind.StatusCodes,
        default = JsonArray(listOf(JsonPrimitive(200), JsonPrimitive(201)))
    ),
    FieldSpec(
        "list_path", "List of items", "For a listing: where the array of items is in the answer. Left out, the answer itself is the array.",
        placeholder = "$.files"
    ),
    FieldSpec(
        "response_map", "Answer fields", "Where the app finds each value in the answer (for a listing, in each item), as a dot-path like $.file.name.",
        kind = FieldKind.KeyValues(responseFieldSuggestions, valueHint = "$.field")
    ),
    FieldSpec("breadcrumb_path", "Folder path", "For a folder listing: where the folders leading to it are in the answer.", placeholder = "$.path"),
    FieldSpec(
        "listing_map", "Listing fields", "Values of the listing as a whole.",
        kind = FieldKind.KeyValues(
            listOf(
                Suggestion("can_write", "A folder can be changed", "$.permissions.write"),
                Suggestion("can_delete", "A folder's files can be deleted", "$.permissions.delete"),
                Suggestion("title", "A list's title", "$.title"),
                Suggestion("can_edit", "A list can be changed", "$.can_edit")
            ),
            valueHint = "$.field"
        )
    )
)

/** The fields of an endpoint the app doesn't know by name. */
fun endpointFields(): List<FieldSpec> = requestFields(null)

/** A REST endpoint the app knows: what it unlocks ([area]), what it's for, and the placeholders it fills in. */
data class EndpointInfo(val name: String, val area: String, val help: String, val placeholders: String? = null) {
    val fields: List<FieldSpec> get() = requestFields(placeholders)
}

/** Every endpoint name the app uses, by what it unlocks, in the order they're offered. */
val KnownEndpoints: List<EndpointInfo> = listOf(
    EndpointInfo("list", "Files tab", "The flat list of every file of the account."),
    EndpointInfo("file_info", "Files tab", "The details of one file.", "{id}"),
    EndpointInfo("download", "Files tab", "Downloading a file by its id.", "{id}"),
    EndpointInfo("download_archive", "Files tab", "Several files at once as one zip.", "{ids}"),
    EndpointInfo("delete", "Files tab", "Deleting a file by its id.", "{id}"),
    EndpointInfo("upload", "Upload tab", "Uploading a file, with no folder of its own.", "{filename}"),
    EndpointInfo("browse_list", "Filesystem tab", "Listing one folder.", "{path}"),
    EndpointInfo("browse_download", "Filesystem tab", "Downloading a file by its path.", "{path}"),
    EndpointInfo("browse_upload", "Filesystem tab", "Uploading into a folder.", "{path} and {make_parents}"),
    EndpointInfo("browse_thumbnail", "Filesystem tab", "The thumbnail of a file by its path.", "{path}"),
    EndpointInfo("browse_mkdir", "Filesystem tab", "Making a folder ({action} is mkdir or mkdirall).", "{path} and {action}"),
    EndpointInfo("browse_rename", "Filesystem tab", "Renaming or moving a file or folder.", "{path}, {target} and {make_parents}"),
    EndpointInfo("browse_delete", "Filesystem tab", "Deleting a file or folder by its path.", "{path} and {recursive}"),
    EndpointInfo("browse_import", "Filesystem tab", "Copying files into a folder by their ids.", "{path} and {files_json}"),
    EndpointInfo("archive_info", "Filesystem tab", "What's inside an archive, without downloading it.", "{path}"),
    EndpointInfo("archive_file", "Filesystem tab", "One file out of an archive.", "{path} and {entry}"),
    EndpointInfo("user_lists", "Lists tab", "The lists of the account."),
    EndpointInfo("list_info", "Lists tab", "The files of one list.", "{list_id}"),
    EndpointInfo("list_create", "Lists tab", "Making a list.", "{title_json} and {files_json}"),
    EndpointInfo("list_update", "Lists tab", "Changing a list's title or files.", "{list_id}, {title_json} and {files_json}"),
    EndpointInfo("list_delete", "Lists tab", "Deleting a list.", "{list_id}"),
    EndpointInfo("user_info", "Account", "The account's name and storage."),
    EndpointInfo("thumbnail_id", "Previews and links", "The thumbnail of a file by its id.", "{id}"),
    EndpointInfo("raw_id", "Previews and links", "A file's own address, for previews and sharing.", "{id}"),
    EndpointInfo("share_id", "Previews and links", "The public page of a file.", "{id}"),
    EndpointInfo("share_path", "Previews and links", "The public page of a path.", "{path}")
)

/** The sign-in fields, for WebDAV ([webdav]: sent as HTTP Basic unless set otherwise) and generic REST configs. */
private fun authField(webdav: Boolean) = FieldSpec(
    "auth", "Sign-in", "", common = true,
    kind = FieldKind.Group(
        listOf(
            FieldSpec(
                "type", "API key sent as",
                "How a saved API key (or a session from signing in) goes with each request.",
                kind = choice(
                    ChoiceOption("NONE", "Nothing", "No API key"),
                    ChoiceOption("BASIC", "Basic", "HTTP Basic: the key is the password"),
                    ChoiceOption("BEARER", "Bearer", "Authorization: Bearer <key>"),
                    ChoiceOption("HEADER", "Header", "The key in a header of its own")
                ),
                default = str(if (webdav) "BASIC" else "NONE"), common = true
            ),
            FieldSpec(
                "header_name", "Header name", "The header the key goes in.", default = str("Authorization"), common = true,
                shownWhen = { it.textOf("type") == "HEADER" }
            ),
            FieldSpec(
                "key_username", "Username sent with the key", "With Basic, the username that goes with the key (often left empty).",
                default = str(""), shownWhen = { (it.textOf("type") ?: if (webdav) "BASIC" else "NONE") == "BASIC" }
            ),
            FieldSpec(
                "password_auth", "Username and password", "For hosts that take a username and password, not only an API key.",
                kind = FieldKind.Group(
                    listOf(
                        FieldSpec(
                            "mode", "Used as",
                            "Sent with every request, or exchanged once for a session (the password isn't kept then).",
                            kind = choice(
                                ChoiceOption("BASIC", "Every request", "HTTP Basic, the password is kept on the device"),
                                ChoiceOption("LOGIN", "Sign-in request", "Exchanged for a session, the password isn't kept")
                            ),
                            required = true, default = str("BASIC")
                        ),
                        FieldSpec(
                            "login", "Sign-in request", "The request that exchanges the username and password for a session.",
                            common = true, shownWhen = { it.textOf("mode") == "LOGIN" },
                            kind = FieldKind.Group(
                                listOf(
                                    FieldSpec("method", "Method", "The HTTP method of the sign-in.", kind = methodChoice, default = str("POST"), common = true),
                                    FieldSpec("path", "Path", "Added to the base address.", required = true, placeholder = "/user/login"),
                                    FieldSpec(
                                        "body", "Sent as", "How the fields below are sent.",
                                        kind = choice(ChoiceOption("FORM", "Form"), ChoiceOption("JSON", "JSON")), default = str("FORM"), common = true
                                    ),
                                    FieldSpec(
                                        "fields", "Fields",
                                        "{username}, {password} and {otp} are filled in. A field that's just {otp} is only sent once a code is asked for.",
                                        kind = FieldKind.KeyValues(
                                            listOf(
                                                Suggestion("username", "The username", "{username}"),
                                                Suggestion("password", "The password", "{password}"),
                                                Suggestion("otp", "A two-factor code", "{otp}")
                                            ),
                                            valueHint = "{username}, {password}, {otp} or a fixed value"
                                        ),
                                        common = true
                                    ),
                                    FieldSpec("token", "Session in the answer", "Where the session token is in the answer.", required = true, placeholder = "$.auth_key"),
                                    FieldSpec(
                                        "otp", "Two-factor codes", "When the host asks for a code, the same sign-in is sent again with it.",
                                        kind = FieldKind.Group(
                                            listOf(
                                                FieldSpec("required_when", "A code is asked for when", "", required = true, kind = conditionGroup()),
                                                FieldSpec("rejected_when", "The code was wrong when", "", kind = conditionGroup()),
                                                FieldSpec("digits", "Digits", "How long a code is.", kind = FieldKind.Number, default = JsonPrimitive(6))
                                            )
                                        )
                                    )
                                )
                            )
                        ),
                        FieldSpec(
                            "logout", "Sign-out request", "Ends the session on the host when signing out; without it, signing out is only on the device.",
                            kind = FieldKind.Group(requestFields(null)), shownWhen = { it.textOf("mode") == "LOGIN" }
                        )
                    )
                )
            )
        )
    )
)

private fun conditionGroup() = FieldKind.Group(
    listOf(
        FieldSpec("path", "Field in the answer", "", required = true, placeholder = "$.value"),
        FieldSpec("equals", "Has the value", "", required = true, placeholder = "otp_required")
    )
)

// ---- The kinds ----

private val genericRestSections = listOf(
    Section(
        "Basics", "The name, and where the API is.",
        listOf(
            nameField,
            FieldSpec("base_url", "Base address", "The API's address; every endpoint's path is added to it.", required = true, placeholder = "https://files.example.com/api"),
            FieldSpec("browse_root", "Start folder", "The folder the Filesystem tab starts at (Pixeldrain uses me).", default = str("")),
            FieldSpec(
                "send_credentials", "Send the sign-in", "Off for a public host that must not see it.",
                kind = FieldKind.Toggle, default = JsonPrimitive(true)
            ),
            FieldSpec(
                "account_fallback", "Use the Pixeldrain account", "Without a sign-in of its own, use the one in Settings → Account. Only for pixeldrain.com.",
                kind = FieldKind.Toggle, default = JsonPrimitive(false)
            )
        ),
        startOpen = true
    ),
    Section("Sign-in", "How the sign-in goes with each request. The key or password itself is entered on the host's card, never here.", listOf(authField(webdav = false))),
    Section(
        "Endpoints", "The requests the app makes. Each one unlocks a part of the app; the ones left out are simply not offered.",
        listOf(FieldSpec("endpoints", "Endpoints", "", kind = FieldKind.Endpoints, required = true))
    ),
    Section("Screens", "The tabs this host shows.", listOf(screensField)),
    metaSection
)

private val webDavSections = listOf(
    Section(
        "Basics", "The name, and where the share is.",
        listOf(
            nameField,
            FieldSpec(
                "base_url", "WebDAV address", "The share's address. {username} is filled in with the username you sign in with.",
                required = true, placeholder = "https://cloud.example.com/remote.php/dav/files/{username}"
            ),
            FieldSpec("root_path", "Start folder", "The folder the app starts in.", default = str("/"), common = true)
        ),
        startOpen = true
    ),
    Section("Sign-in", "How the sign-in is sent. The username and password are entered on the host's card, never here.", listOf(authField(webdav = true))),
    Section("Screens", "The tabs this host shows.", listOf(screensField)),
    metaSection
)

private val s3Sections = listOf(
    Section(
        "Basics", "The name, and which bucket on which server.",
        listOf(
            nameField,
            FieldSpec("endpoint", "Server address", "The S3 server, with its port when it has one.", required = true, placeholder = "https://nas.example.com:9000"),
            FieldSpec("bucket", "Bucket", "The bucket to browse.", required = true, placeholder = "my-bucket"),
            FieldSpec("region", "Region", "The region the requests are signed for; most self-hosted servers take any.", default = str("us-east-1"), common = true),
            FieldSpec(
                "path_style", "Bucket in the path", "Addresses like server/bucket/file, which MinIO and most self-hosted servers need. Off, bucket.server/file.",
                kind = FieldKind.Toggle, default = JsonPrimitive(true)
            ),
            FieldSpec("prefix", "Only under", "Only show files under this prefix, as if it were the top.", default = str(""), placeholder = "photos/")
        ),
        startOpen = true
    ),
    Section("Screens", "The tabs this host shows. The access key is entered on the host's card.", listOf(screensField)),
    metaSection
)

private val smbSections = listOf(
    Section(
        "Basics", "The name, and which share on which server.",
        listOf(
            nameField,
            FieldSpec("host", "Server", "The server's name or address.", required = true, placeholder = "nas.local or 192.168.1.10"),
            FieldSpec("share", "Share", "The share to browse, by its name (not a path).", required = true, placeholder = "Documents"),
            FieldSpec(
                "auth", "Sign in", "Guest and anonymous shares need no username or password.",
                kind = choice(
                    ChoiceOption("CREDENTIALS", "Account", "A username and password, entered on the host's card"),
                    ChoiceOption("GUEST", "Guest"),
                    ChoiceOption("ANONYMOUS", "Anonymous")
                ),
                default = str("CREDENTIALS"), common = true
            )
        ),
        startOpen = true
    ),
    Section(
        "Connection", "The port, the account's domain, and how the connection is protected.",
        listOf(
            FieldSpec("root_path", "Start folder", "The folder inside the share the app starts in.", default = str(""), placeholder = "Photos/2024"),
            FieldSpec("port", "Port", "The SMB port.", kind = FieldKind.Number, default = JsonPrimitive(445)),
            FieldSpec("domain", "Domain", "The Windows domain or workgroup of the account; empty for a local account.", default = str("")),
            FieldSpec(
                "min_version", "Oldest version", "The oldest SMB version allowed. The newest the server offers is always used.",
                kind = choice(ChoiceOption("SMB2", "SMB 2"), ChoiceOption("SMB3", "SMB 3")), default = str("SMB2")
            ),
            FieldSpec(
                "encrypt", "Encrypt", "Encrypts the traffic. Needs SMB 3; without it the connection fails rather than going unencrypted.",
                kind = FieldKind.Toggle, default = JsonPrimitive(false)
            )
        )
    ),
    Section("Screens", "The tabs this host shows.", listOf(screensField)),
    metaSection
)

/** The form of a config of [kind] (its "kind" field), null for a kind the app doesn't know. */
fun sectionsFor(kind: String?): List<Section>? = when (kind) {
    "generic_rest" -> genericRestSections
    "webdav" -> webDavSections
    "s3" -> s3Sections
    "smb" -> smbSections
    else -> null
}

/** What a config of [kind] is, for the editor's title. */
fun kindLabel(kind: String?): String = when (kind) {
    "generic_rest" -> "REST API"
    "webdav" -> "WebDAV"
    "s3" -> "S3"
    "smb" -> "SMB share"
    else -> kind ?: "Unknown kind"
}

/** The keys a list of sections describes at their top level, the rest of a config is "Other fields". */
fun describedKeys(sections: List<Section>): Set<String> = sections.flatMap { s -> s.fields.map { it.key } }.toSet() + "kind"

/** How a value reads back in a field's "Default:" line. */
fun JsonElement.displayText(): String = when (this) {
    is JsonPrimitive -> if (isString) content.ifEmpty { "empty" } else content
    is JsonArray -> joinToString(", ") { runCatching { it.jsonPrimitive.content }.getOrDefault(it.toString()) }
    else -> toString()
}
