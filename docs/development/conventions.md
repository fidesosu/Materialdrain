# Conventions

How the code in this repository is written, and why. None of it is enforced by a tool; it's what the code already
does, so a change that follows it reads like the rest. When you're unsure, look at the code nearest to what you're
changing.

## Comments

Comments explain **why**, and what something is **for**. The code already says what it does.

```kotlin
/**
 * The date a file is listed and sorted by: when it was last modified where the host knows (SMB, WebDAV, S3, Pixeldrain's
 * filesystem), else when it was uploaded (Pixeldrain's files, which never change).
 */
val StorageNode.listDate: String? get() = modifiedAt ?: createdAt
```

```kotlin
// By the moment itself, not the text: hosts write dates differently, and RFC 1123 doesn't sort as text
SortableField.UPLOAD_DATE -> parseDateTime(listDate)?.toEpochMilli() ?: Long.MIN_VALUE
```

- **KDoc (`/** … */`) on anything that isn't obvious from its name**: classes, interfaces, public functions, fields
  of state. It says what the thing is for, and the reason behind a choice that looks odd.
- **Plain English, short sentences.** Write for someone who doesn't know the code yet. Prefer "a cancelled upload
  stops there" to a description of the mechanism.
- **Say why something was *not* done another way**, when that's the obvious question. `StorageProvider`'s KDoc, for
  example, explains that there's deliberately no fake folders for flat hosts, "because that would silently
  misrepresent what the host actually supports".
- **Link with `[Name]`** to the class, function or parameter a comment talks about, so it stays findable when renamed.
- **Line comments (`//`)** go above the line they explain, without a full stop at the end when they're one sentence.
- The same goes for the build files and workflows: `app/build.gradle.kts` explains why versions come from the commit
  time, and every step in `.github/workflows/build.yml` that isn't obvious has a line saying why it's there.

Why: most of the code's difficulty is in decisions (why a sign-in is only sent to some addresses, why an error after a
cancel counts as the cancel), not in syntax. A comment that keeps the reason saves the next person from undoing a fix.

## Naming

| What | Pattern | Examples |
| --- | --- | --- |
| Packages | By feature, under `tools.senko.materialdrain` | `upload`, `files`, `filesystem`, `lists`, `preferences`, `transfer`, `ui.media` |
| Provider modules | `provider.<kind>` | `provider.webdav`, `provider.s3`, `provider.genericrest` |
| ViewModels | `<Feature>ViewModel`, with a `<Feature>UiState` | `UploadViewModel` and `UploadUiState` |
| Hosts | `<Kind>StorageProvider`, often with a `<Kind>Client` for the wire | `WebDavStorageProvider`, `S3Client` |
| Groups of host operations | `<Something>Ops` | `FileStoreOps`, `BrowseOps`, `ArchiveOps` |
| Composition locals | `Local<Thing>` | `LocalReduceMotion`, `LocalBottomInset` |
| Tests | `<Thing>Test`, test names as sentences in backticks | `` fun `a different host is sensitive`() `` |

Names say what something is in the app's own words: a **host** (not a backend), a **sign-in**, a **config**. The code
still calls hosts "providers" in type names, since that's what they were first called; the words the user reads say
"host".

The Kotlin style is the official one (`kotlin.code.style=official` in `gradle.properties`), which Android Studio
formats to.

## Host operations return `ApiResponse`

Every operation of a host (`FileStoreOps`, `BrowseOps`, `ListOps`, `ArchiveOps` and the others in `Ops.kt`) is a
`suspend` function that returns an `ApiResponse`:

```kotlin
sealed class ApiResponse<out T> {
    data class Success<out T>(val data: T) : ApiResponse<T>()
    data class Error(val error: ProviderError) : ApiResponse<Nothing>()
}

data class ProviderError(val code: String, val message: String, val httpStatus: Int? = null)
```

A host **never throws** for a failure: a lost connection, a refused request or an answer it can't read all become an
`ApiResponse.Error`, with a `message` a person can read. The one exception is coroutine cancellation, which must
always be thrown on:

```kotlin
val result = try {
    client.propfind(baseUrl, normalizedPath, depth = 1, resolveAuth())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    return ApiResponse.Error(networkError(e))
}
```

Why: the screens show a host's failure in place (a message, a failed file in a list), and a failure of one file
mustn't stop the others. An exception that escapes a coroutine nobody catches crashes the whole app, which is exactly
the bug the commit "Fix the crash when an upload fails or is cancelled" fixed. Catching `CancellationException`
separately keeps cancelling working: swallowing it would leave a cancelled job running.

A host that can't do something says so with an error too, rather than pretending (see `downloadArchive`'s default,
which answers "This host can't download several files as one archive."). And the optional groups of operations
(`fileStore`, `browse`, `lists`…) are `null` when the host lacks the matching capability; the UI reads
`capabilities` to decide what to show. More in [Hosts in code](providers.md).

## Cancellation

Transfers run in coroutines that the user can cancel at any moment, often in the middle of a host's sending loop.
Three rules keep a cancel a cancel:

1. **Code that calls a host catches exceptions too**, for each item of a loop, and turns them into a failed result.
   `UploadViewModel.uploadFile` and `FilesystemViewModel.uploadOne` "never throw but for a cancel", so one failed
   file doesn't end the batch.
2. **A cancelled job stops inside the host's loop.** The progress callback, which the host calls while sending,
   throws when the job is no longer active:

    ```kotlin
    val response = uploadOne(browse, joinPath(dir, item.name), item.uri) { sent ->
        // Called from inside the host's sending loop: a cancelled upload stops there
        if (job?.isActive == false) throw CancellationException("The upload was cancelled")
        …
    }
    ```

3. **An error after a cancel counts as the cancel.** Cancelling cuts the connection, and some hosts report that as an
   ordinary error (or throw an `IOException`). So after any failure, `ensureActive()` is called before the failure is
   recorded: if the job was cancelled, it throws `CancellationException` and the cancel path runs instead.

    ```kotlin
    } catch (e: Exception) {
        currentCoroutineContext().ensureActive()
        ProviderLog.e("Filesystem", "uploading to '$path' failed: ${e.message}", e)
        ApiResponse.Error(ProviderError("upload_failed", e.message ?: "The upload failed."))
    }
    ```

The `catch (e: CancellationException)` around the whole batch then updates the state ("Upload cancelled."), rethrows,
and a `finally` reports the transfer's outcome. Why: without rule 3, cancelling showed "upload failed" with a
network error, which is both wrong and alarming. More in [Uploads and downloads](transfers.md).

## Logging

Log through `ProviderLog` (in `provider-api`), not `android.util.Log` directly:

```kotlin
ProviderLog.d("Http", "${request.method} ${request.url}")
ProviderLog.e("Config", "…", e)
```

- Every line is tagged `MD/<category>`, so `adb logcat | grep "MD/"` (or `tag:MD/` in Android Studio) shows only the app's own lines.
  The categories in use are `Files`, `Lists`, `Filesystem`, `Upload`, `Http`, `Auth` and `Config`; reuse one before
  making a new one.
- **Never log a credential.** Log its kind or length ("api key, 40 chars"), or the name of the header it goes in, never
  the value. The `Auth` line of the REST host logs the header's name and whether a session token is used, for example.
- `ProviderLog.snippet(text)` gives the start of a response body (300 characters by default) for an error report:
  enough to read the server's message, not a whole page.

A few older files still log with `Log` and a tag of their own; move them to `ProviderLog` when you work on them.

Why: one prefix makes a bug report's log easy to cut down to what matters, and a log that never holds a secret can be
pasted into an issue without a second thought.

## Words in the interface

The text the app shows is written in Kotlin, next to the code that shows it (there are no translations, so
`strings.xml` holds only the app's name). It follows the same style as these pages:

- **Plain words, no jargon.** "Sign in" rather than "authenticate", "host" rather than "provider" or "backend",
  "This host can't receive uploads." rather than an error code.
- **Sentence case** for buttons, titles and labels: **Check all for updates**, **Add a custom host**, **Or paste a
  config**. Some older labels are still in Title Case; fix them as you pass.
- **Say what happened and what to do.** A refused sign-in reads "Not signed in. Sign in, or add an API key for this
  host under Settings → Advanced." (`ProviderError.forDisplay()`).
- **Messages are sentences**, with a full stop: "1 file was uploaded to $dir.", "Upload cancelled."
- **Name settings the way the screen does**, with **Settings → Advanced** style paths, so the user can find them.

Why: the app is used by people who don't know what WebDAV or a 401 is, and they shouldn't need to.

## State and ViewModels

Each screen's state lives in a ViewModel, as one immutable `…UiState` data class behind a `StateFlow`:

```kotlin
private val _uiState = MutableStateFlow(UploadUiState())
val uiState: StateFlow<UploadUiState> = _uiState.asStateFlow()

_uiState.update { it.copy(isLoading = false, errorMessage = null) }
```

- Only the ViewModel writes the state, always through `update { it.copy(…) }`, which is safe when several coroutines
  (parallel uploads, say) change it at once.
- Long work runs in `viewModelScope`, so it ends with the screen's ViewModel, and anything the user can cancel keeps
  its `Job`.
- The ViewModels get what they need through their constructor, from `ViewModelFactory`, which takes it from the one
  `AppContainer`. There's no dependency-injection library: the container is small enough to read in one go.
- Shared, app-wide state (the hosts, the settings, the session) is also a `StateFlow`, on the object that owns it:
  `ProviderConfigStore.providers`, `AppSettings.reduceAnimations`, `SessionManager.loggedInUser`.

Why: one state object per screen is easy to reason about and to rebuild after the screen rotates, and a single owner
for each piece of state means no two places can disagree about it. See [Architecture](architecture.md).

## Compose

- **Screens read state with `collectAsState()`** and call the ViewModel's functions for anything that changes it.
- **Components take values and callbacks**, not ViewModels, so they can be reused on every screen (`ui/components/`).
- **Settings that composables deep in the tree need** are composition locals, provided once at the root in `App.kt`:
  `LocalReduceMotion`, `LocalBlurredBackdrop`, `LocalTextWrap`, `LocalBottomInset`, `LocalVideoLoop` (in
  `ui/LocalSettings.kt`). This saves passing them through every layer.
- **Respect reduced motion.** Read `LocalReduceMotion` wherever something moves: when it's on, decorative motion
  (sliding, scaling, rolling text, animated scrolling) is left out, while fades and feedback such as transfer
  progress stay.
- `remember` for state that only the screen needs, `rememberSaveable` for what should survive a rotation,
  `LaunchedEffect` for work started by a change.
- Material 3 throughout, but a plain layout when a Material component doesn't fit. `FileListItem` explains why it's a
  plain row and not a `ListItem`, for example.

More in [The interface](interface.md).

## Tests

Tests are JUnit 4 unit tests in each module's `src/test/`, run with `./gradlew test`. They need no phone or emulator.

```kotlin
@Test
fun `same or lower version is up to date even when content differs`() {
    val local = config(version = 3)
    assertEquals(UpdateCheckResult.UpToDate, ConfigUpdates.evaluate(local, remote(config(version = 3, uploadPath = "/v2/upload")), 1))
}
```

- **Test names are sentences** in backticks, saying what should be true: `` `a broken address sends nothing` ``,
  `` `missing required fields are named plainly` ``. A failing test then reads as the rule that was broken.
- **Small helpers with defaults** build the objects under test (`config(version = 3)`, `node("webdav", modified = …)`),
  so each test only spells out what matters to it.
- **Hosts are tested against a real HTTP server**: OkHttp's `MockWebServer`, started in `@Before` and shut down in
  `@After`. The test then checks the requests the host sent and what it made of the answers.
- **Rules that protect the user get tests**: where a sign-in may be sent (`AccountFallbackTest`), which config updates
  must ask first (`ConfigUpdatesTest`), and that every bundled example config still loads (`ExampleConfigsTest`).
- Suspend functions are called with `runBlocking` in tests.

Why: the logic most worth testing (configs, requests, signing, parsing) is plain Kotlin and runs on the JVM in
seconds, and the build workflow runs every test before publishing anything.

## Commits

A commit's subject is a plain sentence describing the change, in the imperative, without a prefix or a full stop:

```text
Sort by date without reading each date over and over
Keep the text preview's scrolling inside it
Build the app only when something that goes into it changes
Fix the crash when an upload fails or is cancelled, and finish ordering uploads by last change
```

- Describe what changes **for the user**, where there's a user-visible change, rather than which files changed.
- No `feat:`/`fix:` prefixes, no ticket numbers.
- The body, when there is one, explains why, and what was wrong before, in the same plain style, wrapped at about 80
  characters. Lists are fine for several changes.

Why: the history is how people find out what changed and when, and the `dev-build` release's notes quote the subject
of the commit it was built from, so a subject has to make sense to someone who only reads that one line.
