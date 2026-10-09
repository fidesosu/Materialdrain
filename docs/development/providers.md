# Hosts in code

Every host the app can use, from the built-in Pixeldrain to an SMB share, is a `StorageProvider`. The interface, the
models and the config classes live in `provider-api`; each kind of host is a module of its own that implements them.
This page walks through the contract, then each implementation, then what it takes to add a new kind of host.

For how configs look to the people who write them, see [Hosts](../hosts/index.md). For where the modules sit in the
app, see [Architecture](architecture.md).

## The contract: `provider-api`

All files are in `provider-api/src/main/java/tools/senko/materialdrain/provider/api/`.

| File | What's in it |
| --- | --- |
| `StorageProvider.kt` | The `StorageProvider` interface, and the result of its connection test |
| `Ops.kt` | The groups of operations a host may offer: `FileStoreOps`, `BrowseOps`, `FileListOps`, `ListOps`, `ArchiveOps`, `AccountOps` |
| `Capabilities.kt` | `ProviderCapability`, `ProviderKind`, `HostScreen`, `ScreenConfig` and `resolveScreens` |
| `Models.kt` | `StorageRef`, `StorageNode`, `StorageListing`, `AccountInfo`, `RichDetails` |
| `ApiResponse.kt` | `ApiResponse` and `ProviderError` |
| `ProviderConfig.kt` | The config classes of every kind, auth settings, `Credentials` |
| `ProviderConfigCodec.kt` | Reading and writing configs as JSON |
| `ProviderConfigTemplates.kt` | The ready-to-fill config of each kind |
| `ConfigUpdates.kt` | Deciding what a config update means, and which of its changes need the user's approval |
| `SignIn.kt` | `PasswordSignIn`, for hosts that sign in with a username and password |
| `ProgressThrottle.kt` | Limits how often a transfer reports its progress |
| `ZipInfo.kt` | Reads a `?zip_info` answer (the contents of an archive) |
| `ProviderLog.kt` | The app's diagnostic log |

### `StorageProvider`

```kotlin
interface StorageProvider {
    val id: String
    val displayName: String
    val kind: ProviderKind
    val capabilities: Set<ProviderCapability>

    val fileStore: FileStoreOps?
    val browse: BrowseOps?
    val account: AccountOps?
    val fileList: FileListOps?
    val lists: ListOps? get() = null
    val archives: ArchiveOps? get() = null

    fun requestHeaders(url: String): Map<String, String> = emptyMap()
    val rootPath: String get() = ""
    val rootName: String get() = "/"

    fun shareUrl(node: StorageNode): String?
    fun thumbnailUrl(node: StorageNode): String?
    fun rawContentUrl(node: StorageNode, attachment: Boolean = false): String?

    suspend fun validate(): ProviderValidationResult
}
```

A provider is a set of optional parts. Each `...Ops` is null when the host can't do that, and `capabilities` says the
same thing in a form the screens can check. There is deliberately no emulation: the interface's own comment says that
faking folders out of a flat store *"would silently misrepresent what the host actually supports."*

| Member | Meaning |
| --- | --- |
| `requestHeaders(url)` | Headers a request for one of this host's own private URLs needs (its login), e.g. for a thumbnail. Empty for other URLs |
| `rootPath` | Where the folder browser starts: Pixeldrain's `me`, a config's `browse_root` or `root_path`. `""` for the top |
| `rootName` | What the path calls the top when `rootPath` is `""`: `/`, or an SMB share's name |
| `shareUrl` | A link to share a file, null without `SHARE_LINK` |
| `thumbnailUrl` | A picture of the file for its row, null when there is none |
| `rawContentUrl` | The file's bytes by URL, for previews and media players. `attachment` asks for a download instead |
| `validate()` | The per-field check behind **Test connection**. Read-only probes only (`HEAD`/`GET`): testing a destructive endpoint *"would itself be a destructive action"* |

Everything that shows a file works from a URL: the image loader, the video and audio players. That's why
`thumbnailUrl` and `rawContentUrl` exist, and why SMB, which has no URLs, serves its files on the device (see
[SMB](#smb)). See [Thumbnails and previews](media.md).

### The ops

`Ops.kt` splits what a host can do into groups. A host fills in the ones it has.

| Interface | For | Operations |
| --- | --- | --- |
| `FileStoreOps` | A flat store of files addressed by id, like Pixeldrain's `/file` API | `upload`, `download`, `fileInfo`, `delete`, `downloadArchive` (default: not supported) |
| `BrowseOps` | Folders and paths | `list(path)`, `upload`, `download`, `createDirectory`, `rename`, `delete(path, recursive)`, `importFiles` (default: not supported) |
| `FileListOps` | One flat "everything on the account" listing | `list()` |
| `ListOps` | Named lists of files (Pixeldrain's lists) | `lists`, `listContents`, `create`, `update`, `delete` |
| `ArchiveOps` | Looking inside an archive on the host without downloading it | `list(archivePath, inside)`, `read(archivePath, entryPath, ...)` |
| `AccountOps` | The account's name and quota | `accountInfo` |

`FileListOps` and `BrowseOps` are kept apart on purpose. A host can upload, download and delete by id without any
way to ask what's already there; `fileList` being null is how that's told apart from a host that lists per folder.

Transfers take an `OutputStream` (downloads) or a content `Uri` and a `Context` (uploads), and an
`onProgress(bytes, total)` callback. `total` is null when the size isn't known. See
[Uploads and downloads](transfers.md).

### Capabilities and screens

`ProviderCapability` is what a host can do:

| Capability | Meaning |
| --- | --- |
| `UPLOAD`, `DOWNLOAD`, `DELETE`, `FILE_INFO` | The basic file operations |
| `BROWSE`, `MKDIR`, `RENAME` | Folders: listing them, making one, renaming and moving |
| `ENUMERATE` | A flat list of everything on the account (`FileListOps`). Named so it doesn't read as a typo of `LISTS` |
| `LISTS` | Lists of files (`ListOps`) |
| `SHARE_LINK` | `shareUrl` gives a link |
| `USER_QUOTA` | `AccountOps` |
| `RICH_FILE_STATS` | Extra details such as views and downloads (`PixeldrainRichDetails`) |
| `ARCHIVE_DOWNLOAD` | Several files as one zip (`FileStoreOps.downloadArchive`) |
| `ARCHIVE_BROWSE` | Looking inside archives (`ArchiveOps`) |
| `SEARCH`, `PERMISSIONS` | Declared, but no host claims them yet |

The UI reads these *"instead of guessing from which ops are non-null."* `ProviderKind` (`PIXELDRAIN`, `WEBDAV`, `S3`,
`GENERIC_REST`, `SMB`) says which implementation it is; the app uses it for icons and labels, and for a few
Pixeldrain-only paths such as the account login.

**Screens** are a separate idea: capability is what a host *can* do, a screen is what a config *chooses* to show.
`HostScreen` has the four tabs, and each knows which capability backs it:

```kotlin
fun isBackedBy(capabilities: Set<ProviderCapability>): Boolean = when (this) {
    UPLOAD -> ProviderCapability.UPLOAD in capabilities
    FILES -> ProviderCapability.ENUMERATE in capabilities
    LISTS -> ProviderCapability.LISTS in capabilities
    FILESYSTEM -> ProviderCapability.BROWSE in capabilities
}
```

`resolveScreens` turns a config's `screens` into the tabs to show:

```kotlin
fun resolveScreens(screens: List<ScreenConfig>?, capabilities: Set<ProviderCapability>): List<ScreenConfig> =
    (screens ?: HostScreen.entries.map { ScreenConfig(it) })
        .filter { it.screen.isBackedBy(capabilities) }
        .distinctBy { it.screen }
```

| The config says | The tabs |
| --- | --- |
| `screens` left out (`null`) | Every screen the host can back, in the order Upload, Files, Lists, Filesystem |
| A list | Those, in the config's order |
| `[]` | None: the app says the host has no screens to show |
| A screen the host can't back | Left out, rather than shown full of errors |
| A screen listed twice | Shown once, where it's first listed |
| `"filesystem"` | Read as `FILESYSTEM`: `ScreenEntrySerializer` trims and upper-cases names |

`ScreenEntrySerializer` also reads a bare name as `{"screen": ...}`, and writes a screen with no `name` and no
`disabled_capabilities` back as just its name, so exported configs stay short. `disabledCapabilities` only ever takes
away: `BrowserScreen` checks `UPLOAD`, `MKDIR`, `RENAME` and `DELETE` against it.

The built-in Pixeldrain host has no config, so it doesn't go through `resolveScreens`: `App.kt` builds its tabs from
its capabilities directly. See [Screens follow the host](architecture.md#screens-follow-the-host).

### The models

| Type | Meaning |
| --- | --- |
| `StorageRef(path, id)` | Points at a file or folder. Folder hosts navigate by `path`; flat stores hand back an `id`. *"A provider only ever reads the field it produced."* |
| `StorageNode` | One file or folder, the same for every host: `ref`, `name`, `isDirectory`, `size`, `createdAt`, `modifiedAt`, `mimeType`, `richDetails` |
| `StorageListing` | A folder's contents: `breadcrumb`, `children`, and whether the caller `canWrite` / `canDelete` there |
| `RichDetails` | A sealed interface for extra fields that don't generalise. `PixeldrainRichDetails` (views, downloads, hash, availability, ...) and `ArchiveEntryDetails` (a file inside an archive) implement it |
| `AccountInfo` | Username and quota |
| `FileList`, `FileListDetail` | A list of files, and a list with its files (in `Ops.kt`) |

`RichDetails` is sealed rather than a map of strings, so the UI matches on a concrete type instead of guessing keys.

### `ApiResponse`

```kotlin
data class ProviderError(val code: String, val message: String, val httpStatus: Int? = null)

sealed class ApiResponse<out T> {
    data class Success<out T>(val data: T) : ApiResponse<T>()
    data class Error(val error: ProviderError) : ApiResponse<Nothing>()
}
```

Every operation returns an `ApiResponse`. A provider catches what goes wrong (the network gone, a refused login, an
answer it can't read) and returns it as an `Error`, so a ViewModel handles every outcome with one `when` and never needs
a `try` around a call.

The one thing that is thrown is cancellation. Every provider has the same pattern:

```kotlin
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    return ApiResponse.Error(networkError(e))
}
```

Cancelling a coroutine is how a transfer is stopped, and the HTTP clients cancel the socket call when that happens
(`call.cancel()`). Turning the `CancellationException` into an `Error` would make a cancelled upload look like a failed
one, and keep the coroutine running after it was told to stop. `UploadViewModel` goes one step further: a host that
reports a cut connection as an error during a cancel is still treated as the cancel (`ensureActive()`).

`ProviderError.code` is a short machine name (`network_error`, `bad_response`, `not_supported`, ...); `message` is what
the user sees. The app adds advice for a refused login with `ProviderError.forDisplay()`.

### Configs: `ProviderConfig.kt` and `ProviderConfigCodec.kt`

`ProviderConfig` is a sealed interface with one class per kind: `GenericRestConfig`, `WebDavConfig`, `S3Config` and
`SmbConfig`. Only `name`, `screens` and `meta` are common; the rest is kind-specific. They are plain
`@Serializable` data classes (kotlinx.serialization), with `@SerialName` for the snake_case JSON names and a Kotlin
default for every optional field, so a config only has to write what differs.

The same file has the parts every kind shares:

| Type | Meaning |
| --- | --- |
| `AuthConfig`, `AuthType` | How an API key or token is attached: `NONE`, `BASIC`, `BEARER`, `HEADER` |
| `PasswordAuth`, `PasswordAuthMode`, `LoginEndpoint`, `OtpConfig` | Signing in with a username and password: sent each time (`BASIC`) or exchanged for a token (`LOGIN`), with an optional two-factor step |
| `EndpointConfig` | One request of a `generic_rest` config |
| `ProviderConfigMeta` | `id`, `version`, `update_url`, `min_app_version`, `author` |
| `Credentials` | What the user entered: API key, username, password, session token. **Never part of a config** |

Two extension functions are kept next to the classes, and both need a branch for every kind: `passwordAuth` (how this
host takes a username and password) and `withMeta`. `allowsAccountFallback` is the one place that decides a
`generic_rest` host may borrow the Pixeldrain account's key: only when `account_fallback` is set *and* `base_url` is
pixeldrain.com, since *"the login must never be sent to another server, whatever the config says."*

`ProviderConfigCodec` reads and writes the JSON. The `kind` field picks the class:

| Method | Does |
| --- | --- |
| `decode(text)` | The config, or null when it isn't one (not JSON, no `kind`, an unknown `kind`, a required field missing) |
| `explainFailure(text)` | Why it isn't one, in words, for the import message: *"it still needs "host""* |
| `encode(config, allFields)` | The config as text, `kind` first. With `allFields`, every field is written with its default (for templates) |
| `parseObject(text)`, `format(obj)` | The raw JSON object, every field kept in its order; for the config editor |
| `removeUpdateUrl(text)` | The text without `meta.update_url`, everything else as it was |

The codec reads with `ignoreUnknownKeys = true`, so a field the app doesn't know (a note, a field of a newer version)
never stops a config from loading. And the app doesn't throw that field away: `ProviderConfigStore` keeps the config's
**text** next to the parsed class (`ConfigSource`), and saves, exports and edits the text. The parsed class is only
what the app reads from it. That is why **Export** gives back exactly what was imported.

### `ProviderConfigTemplates`

The **New host** buttons start from a complete config. `ProviderConfigTemplates.Kind` lists the kinds that have one
(`WEBDAV`, `S3`, `SMB`); `config(kind)` builds it from the config class with example values, and `text(kind)` encodes
it with `allFields = true`. Because the template is made from the class, a field added to a kind shows up in its
template without anyone having to remember to. `generic_rest` has no template: the app offers the bundled Pixeldrain
config instead, which uses every endpoint there is.

### `ConfigUpdates`

`ConfigUpdates.evaluate(local, remoteText, appVersion)` decides what the file at an `update_url` means:

1. Not a config: `Invalid`.
2. A different `meta.id`: `Invalid`.
3. Not a higher `version`: `UpToDate`, even if the text changed.
4. `min_app_version` above this app: `NeedsNewerApp`.
5. Otherwise `Available`, with its `sensitiveChanges`.

`sensitiveChanges(old, new)` lists every change to where, or how, credentials are sent: another kind of host
(`KIND`), a new scheme, host or port (`DESTINATION`), another way of attaching the login (`AUTH`), a new update source
(`UPDATE_SOURCE`). It compares the origins of every URL a config can send credentials to (`originsOf`), which is
another function with a branch per kind. `ProviderUpdater` in the app only applies an update on its own when this list
is empty. See [Sharing and updates](../hosts/sharing-and-updates.md).

### `SignIn.kt`

`PasswordSignIn` is a separate interface for hosts whose sign-in starts a session (`PasswordAuthMode.LOGIN`):
`signIn(username, password, otp)` returns a `SignInResult` (`Success` with the token, `OtpRequired`, `OtpRejected` or
`Failed`), and `signOut()` ends the session on the host when the config says how. Only `GenericRestStorageProvider`
implements it. The settings screen builds the host's provider and checks `as? PasswordSignIn`; the caller keeps the
token, and the password isn't needed afterwards. See [Sign-in](../hosts/sign-in.md).

### Small helpers

| Helper | What it does |
| --- | --- |
| `ProgressThrottle` | Passes a transfer's progress on at most every fifth of a second; `finish` always reports. A fast transfer copies many chunks a second, and each report updates the screen and the notification |
| `ZipInfo.entriesIn(json, inside)` | Reads the entries of one folder of an archive from a `?zip_info` answer (Pixeldrain's format). Used by the Pixeldrain provider and by `generic_rest` configs with `archive_info` |
| `ProviderLog` | Logs with the tag `MD/<category>` (Files, Lists, Filesystem, Http, Auth, Config, ...), so a logcat filter on `MD/` shows just the app. Never logs a credential, only its kind or length. `snippet` shortens a response body for an error report |

## The implementations

| Kind | Module | Class | Client | Capabilities |
| --- | --- | --- | --- | --- |
| Built-in Pixeldrain | `provider-pixeldrain` | `PixeldrainStorageProvider` | OkHttp and Ktor | Fixed set, below |
| `generic_rest` | `provider-generic-rest` | `GenericRestStorageProvider` | OkHttp | From the endpoints the config has |
| `webdav` | `provider-webdav` | `WebDavStorageProvider` | OkHttp | `BROWSE`, `MKDIR`, `RENAME`, `UPLOAD`, `DOWNLOAD`, `DELETE` |
| `s3` | `provider-s3` | `S3StorageProvider` | OkHttp | The same, and `SHARE_LINK` |
| `smb` | `provider-smb` | `SmbStorageProvider` | jcifs-ng | `BROWSE`, `MKDIR`, `RENAME`, `UPLOAD`, `DOWNLOAD`, `DELETE` |

Every config-made provider takes its credentials as `credentials: () -> Credentials` and reads them on every request,
so a new key or password applies at once.

### Pixeldrain

`PixeldrainStorageProvider` wraps three API classes in `internal/`: `PixeldrainCoreApi` (files, lists),
`PixeldrainFilesystemApi` (the filesystem) and `PixeldrainUserApi` (the account, its files and lists, the login).
They share one `PixeldrainHttpClient`: one OkHttp client (HTTP/2, no read or write timeout, for long transfers) and a
Ktor client running on top of it for the JSON calls. Uploads and downloads stream straight through OkHttp. The internal
classes have their own `ApiResponse`, which the provider maps to the `provider-api` one.

- **Capabilities:** `UPLOAD`, `DOWNLOAD`, `DELETE`, `FILE_INFO`, `BROWSE`, `MKDIR`, `RENAME`, `SHARE_LINK`,
  `RICH_FILE_STATS`, `LISTS`, `ENUMERATE`, `ARCHIVE_DOWNLOAD`, `ARCHIVE_BROWSE`. `SEARCH` and `PERMISSIONS` are left out
  on purpose: the API has them, but nothing in the app calls them, *"so claiming the capability here would be a promise
  the UI can't keep."* `account` is null.
- **Root:** `rootPath` is `me`, the account's own filesystem.
- **Login on private URLs:** `requestHeaders` adds the `pd_auth_key` cookie to URLs under `/api/filesystem/`.
- The API key comes from `SessionManager` through `apiKeyProvider`. `AppContainer` creates the one instance, and its
  OkHttp client is shared with the image loader and the media players.

### Generic REST

`GenericRestStorageProvider` turns a `GenericRestConfig` into a host with no Kotlin written for it. What it can do is
decided by which endpoint names the config has:

| Endpoints in the config | Capability | Ops |
| --- | --- | --- |
| `upload` or `browse_upload` | `UPLOAD` | |
| `download` or `browse_download` | `DOWNLOAD` | |
| `delete` or `browse_delete` | `DELETE` | |
| `file_info` | `FILE_INFO` | |
| `list` | `ENUMERATE` | `fileList` |
| `browse_list` | `BROWSE` | `browse` |
| `browse_mkdir` | `MKDIR` | |
| `browse_rename` | `RENAME` | |
| `user_lists` | `LISTS` | `lists` |
| `user_info` | `USER_QUOTA` | `account` |
| `download_archive` | `ARCHIVE_DOWNLOAD` | |
| `archive_info` | `ARCHIVE_BROWSE` | `archives` (`archive_file` reads one file) |
| `raw_id` or `browse_download` | `SHARE_LINK` | |

`fileStore` is always there; an operation whose endpoint is missing returns an error (`not_configured`). The other
endpoints (`thumbnail_id`, `browse_thumbnail`, `share_id`, `share_path`, `browse_import`, `list_info`, `list_create`,
`list_update`, `list_delete`) add to what an existing part can do. The full list for config authors is on
[REST API](../hosts/rest-api.md#endpoints).

Its helpers, all `internal` to the module:

| Class | Does |
| --- | --- |
| `GenericRestClient` | Builds and sends the requests: fills `{placeholders}` into the path, query, form and JSON body, streams uploads and downloads, sends the login request, and `probe`s an address with a `HEAD` for **Test connection** |
| `DotPath` | Reads `$.file.id`-style paths out of a JSON answer. Object keys only, no array indexes, on purpose: *"a config author writing 'which item' logic is exactly the scripting-language trap a plain field-mapping config is meant to avoid"* |
| `AuthHeaders` | Decides which saved credentials a request uses (`AuthPlan`): a session from signing in, or a username and password sent as Basic, then the API key, then nothing. Also reads a sign-in answer into a `SignInResult` |

A request made with a session the host no longer accepts (a `401`) drops the session, so the host shows as signed out,
and is retried once with whatever is left. `requestHeaders` only adds the login to URLs under the config's `base_url`.
With `send_credentials: false`, no login is sent at all.

### WebDAV

`WebDavStorageProvider` browses under `root_path` with `PROPFIND` (`WebDavClient`, `WebDavXml` for the multistatus
answer). It has `browse` only: `fileStore`, `fileList` and `account` are null, so the Upload screen uploads into the
root folder through `browse`. `{username}` in `base_url` is filled in with the signed-in username, for Nextcloud-style
per-user paths. `WebDavAuth` is a trimmed copy of the REST module's auth rules, without a sign-in request: a username
and password as Basic (`password_auth` mode `BASIC`), or an API key attached the way `auth` says. Images are their own
thumbnail (the image loader scales them down), and `requestHeaders` adds the login to URLs under the base URL so
previews load.

### S3

`S3StorageProvider` treats a bucket as folders the way S3 consoles do: `ListObjectsV2` with `delimiter=/`, common
prefixes as folders. A new folder is a zero-byte marker object; a rename copies every object under the old prefix and
deletes the old ones (S3 has no rename). `prefix` scopes every request to a part of the bucket. The username field
holds the Access Key ID and the password field the Secret Access Key.

Requests are signed by `S3Signer`, AWS Signature Version 4 written from scratch, which works the same against AWS,
MinIO, Backblaze B2, Cloudflare R2 and TrueNAS. Streamed uploads are signed as `UNSIGNED-PAYLOAD`. `shareUrl` and
`rawContentUrl` are presigned `GET` URLs valid for an hour. `thumbnailUrl` is null: the app makes thumbnails of
photos, videos and songs itself, from `rawContentUrl` (see [Thumbnails and previews](media.md)).

### SMB

`SmbStorageProvider` opens a share with jcifs-ng. `SmbClient` builds the jcifs context from the config (SMB 2 or 3,
never SMB 1; encryption raises the minimum to SMB 3) and the sign-in (`CREDENTIALS`, `GUEST` or `ANONYMOUS`). jcifs
calls block, so every operation runs on `Dispatchers.IO` through `onShare`, which also turns exceptions into
`ApiResponse.Error`. `rootName` is the share's name.

A share has no URLs, but the rest of the app needs them for thumbnails and media. `SmbContentServer` is a small HTTP
server on `127.0.0.1` only, started on first use, that serves `/<token>/raw/...` (the file, with ranges, so a video
can seek) and `/<token>/thumb/...` (a thumbnail made on the device and cached). The token is a random secret made
fresh each time the app starts, so other apps can't read shares through it. The details are on
[Thumbnails and previews](media.md).

## Adding a new kind of host

Most of the places below are `when` expressions over the sealed `ProviderConfig` or the `ProviderKind` enum, so once
the new config class exists, the compiler points at nearly all of them. A good way to find every spot is to search for
an existing kind: `grep -rn "SmbConfig\|ProviderKind.SMB\|\"smb\"" --include=*.kt .`

1. **A new module.** Copy `provider-smb/build.gradle.kts` (an Android library depending on `:provider-api`), add
   `include(":provider-foo")` to `settings.gradle.kts`, and `implementation(project(":provider-foo"))` to
   `app/build.gradle.kts`.
2. **The config class**, in `provider-api/.../ProviderConfig.kt`: a `@Serializable data class FooConfig(...) :
   ProviderConfig`, with `name`, `screens` (using `ScreenEntrySerializer`, like the others) and `meta`. Then:
    - add a branch to `ProviderConfig.passwordAuth` and `withMeta` in the same file;
    - add a `ProviderKind.FOO` to `Capabilities.kt`.
3. **The codec**, in `ProviderConfigCodec.kt`: a `KIND_FOO` constant, and a branch in `encode`, `decode` and
   `explainFailure` (including its set of known kinds).
4. **Updates**, in `ConfigUpdates.kt`: `originsOf` (every address the sign-in can go to, so a change of address asks
   first), `authOf` and `kindName`.
5. **The provider**: `FooStorageProvider : StorageProvider` in the new module, taking `id`, the config and
   `credentials: () -> Credentials`. Set `capabilities` honestly, leave the ops it can't do null, return every failure
   as an `ApiResponse.Error` and rethrow `CancellationException`.
6. **The registry**: a branch in `ProviderRegistry.build` (`app/.../provider/ProviderRegistry.kt`).
7. **The config editor**: a `fooSections` list in `app/.../preferences/configeditor/ConfigSchema.kt`, and a branch in
   `sectionsFor` and `kindLabel`. `ConfigSchemaTest` fails if the template has a field the form doesn't describe.
8. **The template**: an entry in `ProviderConfigTemplates.Kind` and a branch in `config(kind)`. It becomes a **New
   host** button in **Settings → Advanced** by itself.
9. **The rest of the app** that names each kind:
    - `preferences/AdvancedSettings.kt`: `kindLabel`, and the host card's sign-in fields. S3 and SMB have a branch of
      their own there (a key pair, or a username and password, and no API key); every other kind gets its fields from
      `passwordAuth`: a sign-in form for `LOGIN`, a username and password for `BASIC`, and an API key;
    - `hosts/HostSwitcher.kt`: the icon and label for the `ProviderKind`, whether it needs a sign-in, and the address
      shown under its name;
    - `hosts/HostHealth.kt`: `targetOf`, what to check to know whether the host answers.
10. **Tests**: a `src/test` folder in the new module for the provider, and cases in `provider-api`'s
    `ProviderConfigCodecTest` and `ScreensAndTemplatesTest` (decoding, defaults, the template).
11. **An example config** in `docs/provider-configs/` of the app's repository, if it helps: those files are bundled
    as assets, and `ExampleConfigsTest` checks every one still loads.
12. **Documentation**: a page under `docs/hosts/` in the `website` branch, linked from the kinds table on
    [Hosts](../hosts/index.md) and from `mkdocs.yml`, and the new `kind` added to
    [The config format](../hosts/config-format.md).
