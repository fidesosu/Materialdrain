# Common tasks

Short recipes for the changes that come up most. Each names the files and functions to touch, in order. App paths are
under `app/src/main/java/tools/senko/materialdrain/`; the other modules say so. For the reasons behind the pieces, see
[Architecture](architecture.md), [Hosts in code](providers.md) and [The interface](interface.md).

## Adding a setting

A setting lives in `AppSettings`, is shown by the settings catalog, and reaches the screens either through a ViewModel
or through a `CompositionLocal`.

1. **Store it** in `settings/AppSettings.kt`. Add a preference key at the top of the file, then a private
   `MutableStateFlow` read from the preferences with its default, a public `StateFlow` with a KDoc that says what it
   does and what the default is, and a setter that writes both:

    ```kotlin
    private const val COMPACT_ROWS_PREF = "compact_rows"

    private val _compactRows = MutableStateFlow(prefs.getBoolean(COMPACT_ROWS_PREF, false))
    /** Shows the file lists with smaller rows. Off by default. */
    val compactRows: StateFlow<Boolean> = _compactRows.asStateFlow()

    fun setCompactRows(enabled: Boolean) {
        prefs.edit { putBoolean(COMPACT_ROWS_PREF, enabled) }
        _compactRows.value = enabled
    }
    ```

    `compactRows` is an example name, not a real setting. An enum goes through the private `enumPref(key, default)`
    helper, as `sortField` and `navFabPosition` do.

2. **Show it** in `preferences/SettingsCatalog.kt`: add an item to the `items` of the right `SettingsCategory`. A switch
   is a `SettingsItem.Toggle`; `isChecked` runs while composing, so it collects the flow and stays up to date:

    ```kotlin
    SettingsItem.Toggle(
        title = "Compact rows",
        summary = "Smaller rows, so more files fit on the screen.",
        isChecked = { appSettings.compactRows.collectAsState().value },
        onCheckedChange = { appSettings.setCompactRows(it) }
    )
    ```

    Anything that isn't a switch, an action, a note or a header is a `SettingsItem.Custom` with a
    `SettingsEnvironment.XSection()` composable in a file of its own, like `SearchResultLimitSection` in
    `preferences/SearchSettings.kt`.

3. **A new category** is a new `SettingsCategory(id, title, summary, icon, items)` in the `SettingsCatalog` list, in the
   order it should appear. Nothing else needs to change: `SettingsScreen.kt` renders every category and item from the
   catalog. Leave `hasSaveButton` false; only the Account's API key is saved with the button.

4. **If an item needs something the settings don't have** (another ViewModel, say), add it to `SettingsEnvironment` in
   `preferences/SettingsModel.kt` and pass it where `SettingsScreenContent` builds the environment.

5. **Make it reach the screen**, one of two ways:
    - **A ViewModel reads it.** The ViewModels get `AppSettings` from `ViewModelFactory`; collect the flow in `init`, as
      `FilesystemViewModel` does with `hideSearchIndex`, or `combine` it, as the sort order is.
    - **A composable deep in the tree reads it.** Add a `CompositionLocal` to `ui/LocalSettings.kt` with its default and
      a KDoc. In `MaterialdrainScreen()` (`App.kt`), collect the flow
      (`val compactRows by appContainer.appSettings.compactRows.collectAsState()`) and add
      `LocalCompactRows provides compactRows` to the `CompositionLocalProvider`. Read it with `LocalCompactRows.current`.
      This is how `LocalBlurredBackdrop` and `LocalTextWrap` work. A screen that already gets `appSettings` (like
      `BrowserScreen`) can just collect the flow itself.

6. **Document it** in the website's `docs/user-guide/settings.md`.

## Adding a menu or a menu item

Every menu is an `AppMenu` (`ui/components/AppMenu.kt`); see [AppMenu](interface.md#appmenu) for why. Don't use
Material's `DropdownMenu`, and don't restyle one menu on its own: change `AppMenuDefaults` and every menu follows.

**A new ⋮ menu:**

1. Use `OverflowMenuButton(contentDescription = "More options for …") { close -> … }`.
2. Add one `AppMenuItem(text, leadingIcon = …, onClick = { close(); doIt() })` per entry. Close first, then act.
3. Gate entries on what the host and the screen allow (`if (canDelete) …`), as `FilesystemMenu` does with its
   `canWrite` and `canDelete`.
4. Put a destructive entry last, after an `AppMenuDivider()`, with `destructive = true`.

**A menu on a button of your own:**

1. Wrap the button in a `Box` that holds only the button; the menu measures that box to place itself.
2. Keep `var expanded by remember { mutableStateOf(false) }`; the button sets it to `true`.
3. In the same `Box`, call `AppMenu(expanded = expanded, onDismiss = { expanded = false }) { … }`.
4. Pick `side` (`MenuSide.Below`, `Above`, `Auto`) and `edge` (`MenuEdge.Start`, `Center`, `End`, `Auto`) when the
   defaults don't fit, and `matchAnchorWidth = true` for a menu under a wide card.
5. For a choice that should stay visible (a sort field, a speed), mark the current one `active = true` and don't close
   the menu in `onClick`.
6. For a second level, wrap the entries in `AppMenuPages(showSecondary, primary = { … }, secondary = { … })`, and start
   the secondary page with an entry going back, as `VideoSettingsMenu` in `ui/media/VideoPlayer.kt` does.

**An item in an existing menu:**

| Menu | Where |
| --- | --- |
| A file of the Files screen or of a list | `FileItemMenu` in `files/FileActions.kt` |
| A file or folder of the Filesystem | `FilesystemMenu` in `browser/BrowserScreen.kt` |
| A list in the lists overview | `ListFolderMenu` in `browser/BrowserScreen.kt` |
| The file details' ⋮ | The `actions` of the top bar in `App.kt` |
| The selection bar | A `SelectionAction` in `selectionActions` in `BrowserScreen` (the first three are icons, the rest the menu) |
| **New** in the sort row | A `SortRowAction` in the `actions` passed to `SortControls` |
| The host switcher | `HostSwitcher` in `hosts/HostSwitcher.kt` |

## Adding a REST endpoint name

A `generic_rest` config's endpoints are looked up by name. A new name has to be known by the provider (which calls it),
by the config editor (which offers it), and by the documentation.

1. **Name it** in `provider-generic-rest/…/GenericRestStorageProvider.kt`: add
   `private const val ENDPOINT_FOO = "foo"` next to the others. Names are lowercase with underscores; most that work on
   a path start with `browse_`.
2. **Call it** where the operation is implemented. Look the endpoint up, and say so when the config has none:

    ```kotlin
    val endpoint = config.endpoints[ENDPOINT_BROWSE_MKDIR]
        ?: return ApiResponse.Error(ProviderError("not_configured", "This host has no mkdir endpoint configured."))
    val placeholders = mapOf("path" to path.trim('/'), "action" to if (makeParents) "mkdirall" else "mkdir")
    val result = buffered(endpoint, placeholders)
        ?: return ApiResponse.Error(ProviderError("bad_endpoint", "mkdir endpoint could not be resolved."))
    return if (result.isSuccessful) ApiResponse.Success(Unit) else ApiResponse.Error(statusError(result))
    ```

    Read an answer with `parseJson` and `nodeFrom(endpoint, item)`, which applies the endpoint's `response_map`.
3. **Turn on what it unlocks.** When the endpoint makes the host able to do something new, add it to the `capabilities`
   `buildSet` at the top of the class (`if (config.endpoints.containsKey(ENDPOINT_FOO)) add(ProviderCapability.…)`).
4. **If it's a new kind of action**, provider-api needs it too: a value in `ProviderCapability`
   (`provider-api/…/Capabilities.kt`), and a function in the right interface of `Ops.kt`. The other providers then
   implement it or leave it out. See [Hosts in code](providers.md). The interface reads capabilities, never guesses
   from which operations exist.
5. **Offer it in the editor**: add an `EndpointInfo(name, area, help, placeholders)` to `KnownEndpoints` in
   `preferences/configeditor/ConfigSchema.kt`, next to the endpoints of the same area. The `area` is the heading it's
   grouped under (`"Files tab"`, `"Upload tab"`, `"Filesystem tab"`, `"Lists tab"`, `"Account"`,
   `"Previews and links"`); a name the editor doesn't know shows under *Other*, and **Add an endpoint** only offers
   known ones. A new `response_map` key the provider reads goes into `responseFieldSuggestions` in the same file.
6. **Use it in the bundled Pixeldrain config**, if Pixeldrain has it: `docs/provider-configs/pixeldrain.json` (bundled
   as an asset). Raise its `meta.version`, so `refreshBundledPixeldrainConfig` updates the copies already installed, and
   add the name to the set in `ExampleConfigsTest` (`provider-api/src/test/…`). `ConfigSchemaTest` checks that every
   endpoint of that config is in `KnownEndpoints`.
7. **Document it** in the website's `docs/hosts/rest-api.md`, in the table of its tab, with its placeholders.

Nothing changes in `ConfigUpdates`: an endpoint's full `https://` address already counts as a destination when an
update is checked.

## Adding a field to a config kind

A field of a WebDAV, S3, SMB or REST config is a property of its data class. The template and the codec follow from
the class; the editor and the documentation are written by hand.

1. **Add the property** to the data class in `provider-api/…/ProviderConfig.kt` (`WebDavConfig`, `S3Config`,
   `SmbConfig` or `GenericRestConfig`), with a `@SerialName` in snake case and **a default**:

    ```kotlin
    /** Encrypts the traffic on the wire. Needs SMB 3 on the host, the connection fails when it doesn't offer it. */
    val encrypt: Boolean = false,
    ```

    The default is what keeps every existing config readable: a field left out means the default. A field without one
    is required, and every config written before it would stop loading.
2. **The template** (`ProviderConfigTemplates`) is made from the class with every field written out, so the field shows
   up there by itself. Set an example value in `ProviderConfigTemplates.config(kind)` only if the default doesn't show
   what the field is for.
3. **Describe it for the editor**: add a `FieldSpec` to the kind's sections in
   `preferences/configeditor/ConfigSchema.kt` (`webDavSections`, `s3Sections`, `smbSections`, `genericRestSections`).
   Give the JSON name as `key`, a short `label`, a one-line `help`, the `kind` (`Text`, `Number`, `Toggle`, `Choice`,
   `Group`), and the same `default` as the data class, as a `JsonPrimitive`. Set `common = true` only for fields most
   configs need; the rest are under **More options**. `ConfigSchemaTest` fails until every field of every template has
   a place in the form.
4. **Use it** in the kind's provider: `provider-webdav`, `provider-s3`, `provider-smb`, or `GenericRestStorageProvider`.
   `ProviderRegistry` (`provider/ProviderRegistry.kt`) passes the config to it.
5. **If it changes where or how the sign-in is sent** (an address, a port, an auth mode), add it to
   `ConfigUpdates.originsOf` or `authOf` in `provider-api/…/ConfigUpdates.kt`. Then an update that changes it is a
   `SensitiveChange` and needs the user's approval. Check `HostHealth` (`hosts/HostHealth.kt`, what it checks) and
   `HostSwitcher` (the address it shows) too, as both read each kind's address.
6. **Document it** in the website's page of the kind (`docs/hosts/webdav.md`, `s3.md`, `smb.md` or `rest-api.md`),
   and use it in an example under `docs/provider-configs/` if it helps. `ExampleConfigsTest` decodes every example.

## Adding a file preview type

The file details choose a preview from the file's type. A new kind of preview is a composable in `ui/media/` and a
branch in `FileInfoDetailsCard`. See [Thumbnails and previews](media.md) for how the existing ones load.

1. **Recognize the file.** `StorageNode.previewMimeType()` (`files/FileInfoViewModel.kt`) gives the host's type, or one
   worked out from the extension when the host's is blank or `application/octet-stream`. Match on its prefix, as the
   image, video and audio previews do, or on the extension, as archives do (`isArchiveName`, `ARCHIVE_EXTENSIONS`).
2. **Write the preview** in a new file in `ui/media/`. Take the file's address from `fileInfoViewModel.rawUrlFor(file)`
   and the `apiKey` the details pass (only filesystem files need the sign-in for a preview), and take `outerPadding`
   like `InlineImagePreview` does. Read `LocalReduceMotion` wherever it moves and `LocalBlurredBackdrop` if it shows a
   backdrop.
3. **Load what it needs first**, if anything (text, an archive's listing), in `FileInfoViewModel`: a field in
   `FileInfoUiState`, and a load started where a file is opened, in `fetchFileInfo` (by id) and `setFileInfoFromNode`
   (from a listing), next to `fetchTextFilePreviewContent` and `loadArchive`. Clear it when another file opens, or the
   last file's preview stays on screen.
4. **Show it**: add a branch to the `if … else if` chain under `if (showPreviews)` in `FileInfoDetailsCard`
   (`files/FileDetailsScreen.kt`), before the thumbnail fallback at the end. If it shows the file's own picture, add it
   to `mediaPreviewFirst` and `previewShowsPicture`, so the header doesn't repeat the picture with a tile.
5. **Full screen**, if it has one: `FullScreenMediaPreviewDialog` (`ui/media/FullScreenPreview.kt`) handles video and
   images; anything else shows *Unsupported preview type for fullscreen.* until it gets a branch.
6. **Give it a tile**, if it's a new kind of file: a `FileKind` and its extensions in `ui/components/FileListItem.kt`, so
   the lists show its icon and colour. A text format that should be coloured goes into `SourceLanguages.byExtension`
   in `files/SourceHighlighting.kt`, which also makes `isTextFile` show it as text.
7. **Document it** under *File details* in the website's `docs/user-guide/screens.md`.

## Logging

Diagnostics go through `ProviderLog` (`provider-api/…/ProviderLog.kt`), which every module can reach.

1. Call `ProviderLog.d`, `i`, `w` or `e` with a category and a message:

    ```kotlin
    ProviderLog.i("Config", "imported the bundled '${source.config.name}' config and made it the active host")
    ProviderLog.e("Upload", "the upload failed: ${e.message}", e)
    ```

2. Use an existing category where one fits: `Files`, `Lists`, `Filesystem`, `Http`, `Auth`, `Config`
   (`Upload` is used too). Every line is tagged `MD/<category>`.
3. **Never log a credential.** Log its length or kind ("api key, 40 chars"), not its value. `GenericRestStorageProvider`
   logs only the header's *name* and whether it's a session.
4. For a server's error, log the start of its answer with `ProviderLog.snippet(body)`: the first 300 characters on one
   line, enough to read the message without a whole page.
5. Read them with logcat, filtered on the `MD/` tags, e.g. `adb logcat -s MD/Http MD/Auth`, or in Android Studio's
   Logcat with `tag:MD/`.

Some app code still logs with `android.util.Log` under its own tags (`App`, `FilesystemViewModel`). New diagnostics
should use `ProviderLog`, so they're found in one place.
