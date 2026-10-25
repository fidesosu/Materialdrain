# The interface

The whole interface is Jetpack Compose with Material 3, in one activity. This page walks through it from the outside
in: the shell that holds the screens, the one browser screen behind three tabs, and the small design system every
screen is built from. Paths are under `app/src/main/java/tools/senko/materialdrain/` unless they say otherwise.

## The shell

`MainActivity` sets the content once. It turns off the overscroll stretch everywhere (`LocalOverscrollFactory provides
null`), wraps everything in `MaterialdrainTheme`, and puts the app lock over the app:

```kotlin
MaterialdrainTheme {
    Box(modifier = Modifier.fillMaxSize()) {
        MaterialdrainScreen()
        AppLockOverlay(this@MainActivity, AppContainer.get(application).appLock)
    }
}
```

`MaterialdrainScreen()` in `App.kt` is the shell. It owns the current screen, the ViewModels, the top bar, the bottom
bar, the FAB, the snackbars and the app-wide dialogs. There is no navigation library: the current screen is a
`rememberSaveable` value of the `Screen` enum (`Navigation.kt`), and `navigateTo` swaps it, remembering the previous one
for back.

| Piece | Where | What it does |
| --- | --- | --- |
| `Screen` | `Navigation.kt` | Every screen: `Upload`, `Files`, `Filesystem`, `FileDetail`, `Lists`, `Settings` |
| `HostScreen.toScreen()` | `Navigation.kt` | Maps provider-api's `HostScreen` to the app's `Screen`. The one place the two meet, as provider-api can't see the app module |
| `Tab` | `Navigation.kt` | One tab of the host: a `Screen` of its kind and its entry in the config (`ScreenConfig`), with its `id`, `title` and icon (`TabIcons`) |
| `rememberHostTabs` | `HostTabs.kt` | The tabs of the active host, worked out again when a config changes |
| `BottomNavigationBar`, `AppNavigationRail`, `TabStrip` | `Navigation.kt` | The three ways of showing the tabs: a bar, a rail at the side of a wide screen, and the dots of the swipe navigation |
| `FabDetails`, `screenFab`, `AppFab` | `AppFab.kt` | The screen's floating button: Upload on the Upload screen, Save in the settings categories that have it |
| `AppDialog` | `AppDialogs.kt` | The app-wide dialogs: a big download to confirm, the delete confirmation |
| `TransferBarSlot` | `App.kt` | The progress bar under the top bar (see [TransferStatusBar](#transferstatusbar)) |
| `lastScreen`, `lastTabId` | `settings/AppSettings.kt` | The screen and tab the app was on, so it opens there again (unless **Open on** names one) |

Screen changes animate with `AnimatedContent`: a vertical slide in tab order, or a quick crossfade with reduced motion
(see [Reduced motion](#reduced-motion-and-the-other-locals)). Its state is a `Shown`: the screen, the tab and the host.
A page sliding out keeps its own host and tab, so it goes on showing its own browser; `contentKey` leaves the host out,
so switching hosts on the same tab changes the page in place instead of sliding.

The bottom bar rises out of the bottom edge and sinks back into it, its icons with it: it expands and shrinks with its
top kept on the space (`expandFrom` / `shrinkTowards = Alignment.Top`), so the page above follows it.

### Tabs come from the host

Which tabs exist is decided by the active host, not by the app. `rememberHostTabs` (`HostTabs.kt`) works it out each
time the configs change:

```mermaid
flowchart LR
    A[Active host] --> B{Built-in Pixeldrain?}
    B -- yes --> C["Capabilities:<br/>ENUMERATE → Files,<br/>LISTS → Lists, BROWSE → Filesystem"]
    B -- no --> D["resolveScreens(config.screens, capabilities)"]
    C --> E{"Files or Filesystem?"}
    D --> E
    E -- yes --> F[Upload left out]
    E -- no --> G[Upload kept]
    F --> H[navBarOrder]
    G --> H
```

- **A config** picks its screens with `screens` (see [Screens](../hosts/config-format.md#screens)).
  `resolveScreens` in provider-api keeps them in the config's order and drops the ones the host can't back.
- **The built-in Pixeldrain host** isn't a config, so it gets whatever its capabilities allow.
- **The Upload tab** is only kept on a host with nowhere else to upload to. The Files and Filesystem screens upload from
  their ⋮ menu (**New → Upload**) and take shared files (see [Shared files and moves](#shared-files-and-moves)).
- **A screen's config entry** can rename the tab (`Tab.title`), give it an icon (`TabIcons`, by name) and take
  capabilities away from it (`disabledCapabilities`, passed to `BrowserScreen`, and to the file details through
  `FileInfoViewModel.setTabLimits`).
- **Several tabs of one kind** are told apart by `Tab.id`. The bar selects a `Tab`, not a `Screen`: `currentTabId`
  says which tab of the current screen's kind is shown (`tabOf(screen)`), and falls back to the first of that kind.
- **A screen the new host doesn't offer** falls back to the first one it does. This runs when the host changes and when
  the screen changes, since a host switched to from Settings only meets its tabs when you leave Settings.
- **No screens at all** (`"screens": []`, or only ones the host can't back) shows a message saying so instead of a
  screen.

### One screen hides the bar

With a single screen there is nothing to switch between, so the bar would only take space. The bottom bar (or the tab
strip) is shown only when `navBarOrder.size > 1`, not on the file details, and not where the rail is.

Without the bottom bar, the file screens and the details are drawn down to the screen's edge, under the see-through
system navigation bar. `LocalBottomInset` tells the lists how much room to leave after their last item, so it can still
be scrolled clear of the bar.

### The three kinds of tabs

- **`BottomNavigationBar`**: up to five tabs (`MAX_BAR_TABS`); with more, the fifth place is **More**, a menu of the
  rest, which shows the one of them that's open. **Tab labels** off shows only the chosen tab's name.
- **`AppNavigationRail`**: the tabs at the side, where the screen is at least 600 dp wide (`isWideScreen`), as a bar
  along the bottom would stretch its few tabs far apart. It scrolls when there are more than fit.
- **`TabStrip`**, with **Look and feel → Navigation → Swipes** (`AppSettings.gestureNavigation`): no bar, only a thin
  row with a dot per tab, the shown one wider and in the accent colour. Tabs change with a swipe sideways across the
  page (`Modifier.sidewaysSwipe`, `ui/components/SidewaysSwipe.kt`), along the row (a tab for every `StripStep`, with a
  light vibration each), or a tap on a dot. Every dot has the same room (`DotPitch`), so the shown one grows and
  shrinks from its middle without moving the others. **Swipe between tabs** gives the bottom bar the same sideways
  swipe.

### The host switcher

On the browse screens (Files, Lists, Filesystem, and Upload where it's a tab) the top bar has no title. `HostSwitcher`
(`hosts/HostSwitcher.kt`) sits in its place: a floating card with the active host's icon, a status badge and its name.
Tapping it opens an [`AppMenu`](#appmenu) of every host (`hostOptions`: the built-in Pixeldrain first, then the configs
in the order they were added), each with its address, whether it answers and whether it's signed in.

- Whether each host answers is known before the menu opens: `HostHealth` checks them every three minutes while the app
  is in front (`HOST_RECHECK_MILLIS`), when the configs change, and when the network changes. Opening the menu checks
  only hosts not checked in the last half minute; the header's refresh button checks all of them now.
- The badge shows the state with a symbol as well as a colour (tick, cross, question mark, crossed-out cloud), so it
  doesn't hang on colour alone.
- Choosing a host calls `ProviderConfigStore.setActive`. The gear, or **Sign in**, opens that host's settings;
  **Manage hosts** opens **Settings → Hosts**.

### The ⋮ menu of a screen

The top bar's ⋮ (in `App.kt`) is the same menu on every screen but a file's details and the settings, which have
none. `ScreenMenuEntries` (`browser/BrowserControls.kt`) lays it out in the order its entries are reached for:

1. what's done with the page shown: the screen's `ScreenAction`s of `ScreenActionGroup.PAGE` (an open list's
   **Download all**, a folder's **Download this folder**, **Select**);
2. **New**, a page of the `ScreenActionGroup.NEW` ones (the Files screen's **Upload**; the Filesystem's **Upload**,
   **New folder** and **Import files by ID**, each where the host and the tab's config allow it);
3. **Sort by**, for a sortable list: `SortMenuEntries`, the field it's sorted on with its direction. Choosing the field
   already sorted on flips it (`AppSettings.changeSortOrder`);
4. **View**: `ViewMenuEntries`, from the screen's `ViewChoice` (rows, grid or Smart, compact rows, for its subfolders
   too, and back to the defaults; see [How a list looks](#how-a-list-looks));
5. **Settings**.

A `ScreenAction` with `choices` (Download: **Separately** or **As one ZIP**) opens a page of those instead of acting.
The pages are `AppMenuPages`; every page is measured even while another is shown, so the menu keeps one width and
only its height changes. Sort by and View stay open while choosing, so the change can be seen. `BrowserScreen` hands
its actions and view up through `onActionsChange`, keyed by the page shown.

## BrowserScreen

`browser/BrowserScreen.kt` is the one screen behind the Files, Lists and Filesystem tabs. `App.kt` passes a
`BrowserMode`, and the mode decides what the screen lists and offers:

| `BrowserMode` | Lists | Above the list | Opening an entry |
| --- | --- | --- | --- |
| `FILES` | The account's files, flat (`FileInfoViewModel.displayedFiles`) | Status cards | Its details |
| `LISTS` | The lists as folders, one level deep; inside one, its files (`ListViewModel`) | Inside a list: its title | A list opens it; a file opens its details |
| `FILESYSTEM` | Folders and files (`FilesystemViewModel.displayedChildren`), folders first unless that's off | The path, status cards | A folder goes into it; a file opens its details |

Everything that differs between the modes is a `when (mode)` near the top of the function: the entries, the loading
and error state, the refresh, the search candidates, the selection actions and the ⋮ menu's actions. The list itself,
pull to refresh, the empty and error states and the `DotScrollbar` are shared.

Details worth knowing before changing it:

- **The loading spinner waits.** Folders usually load within a fraction of a second, so it shows only after
  `LOADING_INDICATOR_DELAY_MS` (400 ms), or at once on a pull to refresh.
- **A refresh keeps the scroll position**, unless it turned up a file that wasn't listed before: then it jumps to the
  top, where the new file is.
- **Back** leaves the selection first, then the open list, then goes up a folder.
- **Scroll positions survive** visits to other screens: the `LazyListState`s live in `App.kt` (`TabScrollStates`), not
  in the screen.

### The status cards

`StatusCards` in `browser/BrowserControls.kt` is the row of floating cards above the list, for what's going on:
**Cancel**, while a transfer the bar above shows runs, and **Thumbnails**, while the data saver holds them back. The
row is only there while one of them is, and grows out of the header and shrinks back into it as they come and go.

The cards size themselves. `statusCardNeeds()` measures what each card needs to show all of itself, from its text as
it's drawn (with `rememberTextMeasurer`) plus its icon and padding. Each shown card then gets a share of the row as big
as its share of what they both need, so one alone fills the row, and one appearing beside it makes room for itself.

### How a list looks

A list is rows, a grid of big pictures (`FileGridItem` in `ui/components/FileListItem.kt`), or Smart: the grid when
more than half of its files are pictures or videos (`smartLayoutIsGrid`). Rows can be compact. **Look and feel → Grid**
and **Compact rows** are the defaults; a folder, screen or open list can have its own (`ViewLook`, chosen in the ⋮
menu's View page), kept in `AppSettings.viewLooks` by its `viewKey` (the host, the tab and the folder or list). A
Filesystem folder's look can be for its subfolders too: those without their own take the nearest such folder's above
them (`inheritedPath`). A look is kept as a short string: the layout's name, then `1`, `0` or `-` for compact, then `r`
for the subfolders.

### The selection bar

A long-press (or *Select* in the ⋮ menu) starts selecting. `BrowserScreen` keeps the selected keys, in the order they
were selected, and `SelectionActionBar` (`files/SelectionBar.kt`) is the bar at the bottom while selecting:

- It takes a list of `SelectionAction`s (label, icon, action, `enabled`, `destructive`, `choices`). The first three
  (`ICON_ACTIONS`) are icons, the rest go into an `AppMenu` behind a ⋮. An action with `choices` is a menu of them.
- It's a `FloatingBottomBar`: a card in the snackbar's look, kept off the sides and above the system navigation bar or
  the miniplayer. `SelectionBarHeight` is public so the FAB and the snackbar can rise by the same amount.
- Entries that disappear (deleted, moved, filtered out) are dropped from the selection.

Which actions there are is built per mode in `BrowserScreen`: download (one file on its own; several, or a folder,
**Separately** or **As one ZIP**), move and delete on the Filesystem; download, delete, add to a list, add to the
filesystem and remove from this list on Files and Lists; and everywhere, while songs play in the background, **Add to
queue** in the order they were selected, and **Select all**. Each is left out when the host or the screen's config
doesn't allow it.

### Shared files and moves

`DestinationBar` (`browser/SharedUploadBar.kt`) is a bar in the selection bar's place for something waiting for the
folder it goes into, which the user browses to. It takes a `DestinationRequest`: what waits, where it would go from
here (or why it can't), and the button that sends it there.

- **Shared files** (`AppContainer.pendingShares`, from `MainActivity`'s share intents, the Upload shortcut and the
  tile): `App.kt` turns them into a `SharedUpload`, which every browse screen shows. **Upload here** uploads them into
  the open folder, or as new files of the host on the Files screen. A host with only an Upload tab takes them there as
  picked files instead. Nothing uploads before the button is pressed, nor while the app is locked.
- **Moves** (`FilesystemViewModel.pendingMove`, from *Move* in a menu or the selection bar): **Move here** moves them
  into the open folder, which can't be one of those being moved.
- **Links and text** (`AppContainer.pendingOpen`, a `PendingOpen` from `AppIntents.kt`): a Pixeldrain file or list link
  opens it on the built-in Pixeldrain, a shortcut opens its screen, and other shared text becomes a text file that
  waits like a shared one. They wait for the unlock too.

### File details

`FileInfoDetailsCard` in `files/FileDetailsScreen.kt` is the details page. From the top:

- **The preview**, chosen by `StorageNode.previewMimeType()`: an image, a video, a song, a text (`CodePreview`, or
  `MarkdownPreview` for Markdown), a PDF, or an archive's contents (`ArchiveContents`). See
  [Thumbnails and previews](media.md).
- **The header**: the whole name (selectable), after the tile of the file's kind, which stays when the preview shows
  the file's own picture.
- **The actions** (`ActionRow`): the download button (tap twice to start, shows progress while it runs, opens the file
  when done), then Share (the link, or the file itself where it has none) and Delete as buttons of their own.
- **The details**: size, type, dates, Pixeldrain's views and downloads (`StatsRow`), ids and links, each copyable.

The details have no ⋮ menu: their own buttons do what's done with the file. With **Swipe between files**, a swipe
sideways goes to the next file of the list they were opened from (`FileInfoViewModel.hasNeighbour`); the page that's
left is a picture of itself (`PageSwap`, `ui/components/PageTransition.kt`), which slides out under the next one.

## The design system

The shared look lives in `ui/components/`. Use these instead of a Material component with the same purpose, so a change
made in one place reaches every screen.

### FloatingCard

`FloatingCard.kt` is three tokens, not a composable:

| Token | Value | Why |
| --- | --- | --- |
| `FloatingCardShape` | `RoundedCornerShape(20.dp)` | The corners of every floating card |
| `floatingCardColor()` | `surfaceContainerHigh` at 72% alpha | Translucent, so what's behind (the blur, in the search) shows through |
| `floatingCardBorder()` | 0.5 dp, `outlineVariant` at 50% alpha | A hairline edge that keeps the card apart from what's behind it |

They're used by the sort row's cards (`FloatingCardButton` in `BrowserControls.kt`), the search result cards
(`SearchResults.kt`), the host switcher, and every `AppMenu` (shape and border; menus are opaque). Put them on a
`Surface`:

```kotlin
Surface(
    shape = FloatingCardShape,
    color = floatingCardColor(),
    border = floatingCardBorder(),
    contentColor = MaterialTheme.colorScheme.onSurface
) { /* … */ }
```

The file details' cards use their own values in the same spirit (`DetailCardShape` at 20 dp).

### AppMenu

`AppMenu.kt` is the app's one menu. The ⋮ menus of files, *Sort by*, *New*, the host switcher, the selection bar's
overflow, the video player's settings and the config editor's choices are all an `AppMenu` with `AppMenuItem`s. There
is no `DropdownMenu` in the app.

**Why every menu goes through it:** menus then look and behave alike, and a change made in `AppMenu.kt` reaches all of
them. What differs between two menus is a parameter, never a copy. A menu built some other way would drift from the rest
the next time the look changes.

| Part | What it is |
| --- | --- |
| `AppMenu` | The popup. A `BoxScope` extension: call it inside the `Box` that holds its button, and it takes its size and place from that box |
| `AppMenuDefaults` | Every measure: `Gap`, `ScreenMargin`, `MinWidth` (168 dp), `MaxWidth` (400 dp), `MAX_HEIGHT_FRACTION` (0.6), `ItemHeight`, `ItemShape`, `ShadowRoom`, `ANIMATION_MS` (200) |
| `MenuSide` | `Below`, `Above` or `Auto` (below, unless it only fits above) |
| `MenuEdge` | Which edge of the button it lines up with: `Start`, `Center`, `End` or `Auto` (start, or end for a button in the end half of the screen) |
| `AppMenuItem` | An entry: `text`, optional `leadingIcon`, `supportingText`, `trailingText`, `trailingIcon`. `active` marks the current choice in the accent colour; `destructive` is in the error colour; `enabled` |
| `AppMenuDivider` | A hairline between groups, e.g. before a destructive entry |
| `AppMenuPages` | Two levels in one menu: `secondary` entries replace `primary` ones while `showSecondary` is true, sliding sideways |
| `OverflowMenuButton` | The usual ⋮ button with its menu. Its content gets `close` |

How it behaves:

- It's as wide as its widest entry, between `minWidth` and `maxWidth`, and never wider than the screen. With
  `matchAnchorWidth` it's at least as wide as its button (the sort row's menus use this).
- Taller than `maxHeight` (by default 60% of the screen) it scrolls. Long entries end in an ellipsis.
- It's kept on screen, and grows out of the side of the menu its button is on. With reduced motion it appears at once.
- A tap outside or back calls `onDismiss`. **Choosing an entry doesn't close it**: that's up to the entry, so a choice
  like a sort order can stay open to show its effect.

The usual ⋮ menu, from `FilesystemMenu` in `BrowserScreen.kt`:

```kotlin
OverflowMenuButton(contentDescription = "More options for ${node.name}") { close ->
    AppMenuItem("Rename", leadingIcon = Icons.Filled.Edit, onClick = { close(); onRename() })
    AppMenuItem("Select", leadingIcon = Icons.Filled.CheckBox, onClick = { close(); onSelect() })
    if (canDelete) {
        AppMenuDivider()
        AppMenuItem("Delete", leadingIcon = Icons.Filled.Delete, destructive = true, onClick = { close(); onDelete() })
    }
}
```

A menu on a button of your own, from the *New* card in `BrowserControls.kt`:

```kotlin
Box(modifier = modifier) {
    FloatingCardButton(onClick = { expanded = true }) { /* the card */ }
    AppMenu(expanded = expanded, onDismiss = { expanded = false },
        side = MenuSide.Below, edge = MenuEdge.End, matchAnchorWidth = true) {
        actions.forEach { action ->
            AppMenuItem(action.description, leadingIcon = action.icon, enabled = action.enabled,
                onClick = { expanded = false; action.onClick() })
        }
    }
}
```

The `Box` should hold only the button: the menu measures that box to place itself. `VideoSettingsMenu` in
`ui/media/VideoPlayer.kt` shows `AppMenuPages` (Loop and Speed, with the speeds on a second page).

### FileListItem and FileIcon

`FileListItem.kt` has the row of a file or folder, and the picture used wherever a file is shown.

- **`FileListItem`** is a plain `Row`, not a Material `ListItem`, whose trailing area reserves far more width than the
  small menu button needs and takes it from the name. It shows the icon, the name, a details line (`fileDetails`: size,
  extension in capitals, date) and a trailing slot. While selecting, the trailing slot becomes a checkbox.
- **`FileIcon`** is a soft tile in the colour of the file's kind with a glyph, and the thumbnail over it when there is
  one. The kind comes from the extension (`FileKind`: folder, image, video, audio, archive, PDF, code, document, app,
  other), and its colours from the theme, so they follow dynamic colour and dark mode.
- **Thumbnails** load with Coil's `AsyncImage`, decoded at the tile's size rather than the original's, and fade in over
  the tile. There's no placeholder or error picture: the tile underneath is both. The request is remembered per URL and
  login, as a new request on every recomposition would load it again. The sign-in headers come from
  `HostRequestAuth.headersFor`; see [Thumbnails and previews](media.md) for where thumbnail URLs come from.

`fileKindLabel` and `fileDetails` are shared with the search result cards, so a match reads like its row.

### Dialogs

`Dialogs.kt` has the app's three dialogs:

| Dialog | For |
| --- | --- |
| `TextInputDialog` | One text field (new folder, rename, import by id). Confirm is off while the text is blank |
| `ConfirmDialog` | A yes or no; `isDestructive` makes the confirm button the error colour |
| `FolderPickerDialog` | Browsing to a folder (move). A fixed-height list, so the dialog never changes size; each listing is kept while it's open; back goes up a folder before it closes |

### SharedSnackbar

`AppSnackbarHost` in `SharedSnackbar.kt` replaces Material's `SnackbarHost`. It shows and hides the snackbar itself so
it can animate in and out, keeps the last message while that one leaves, and grows upward. Every snackbar has one look:
rounded corners, the label type and the FAB's height, at full width. Each message gets its own `SwipeToDismissBox`
(`DismissibleSnackbar`), as a swipe state handed to an already laid-out box crashes in the lookahead pass.
`snackbarMotion(reduceMotion)` is the one motion for the snackbar and for the buttons that lift above it, so they move
together; it's instant with reduced motion.

### DotScrollbar

`DotScrollbar` is a small dot at the right edge showing where a list is. Dragging it scrolls and stretches it into a
line; letting go stops the content dead. Only the dot and a little around it take touches, so rows beside it stay
clickable. It has two overloads, for a `LazyListState` and for a `ScrollState`, and is a `BoxScope` extension: put it in
the `Box` around the list.

### OdometerText

`OdometerText` rolls each changed character to the new one like an odometer wheel, along the alphabet, the digits or
the code table. Use it only where the same text element changes its text (the Save Settings button, a stat on the file
details). `fixedWidth` with `widthReferenceTexts` reserves the width of the widest text, so the container never changes
size. It doesn't animate with reduced motion (`animate = !LocalReduceMotion.current`). The timing lives in
`OdometerSpec`.

### TransferStatusBar

`TransferStatusBar` draws a `TransferProgress` (bytes, total, speed, time left, a label) as the bar under the top bar.
The percentage has a fixed-width slot, so a change of digits never moves the other texts. `TransferBarSlot` in `App.kt`
picks what it shows for the current screen, and reads the transfer state itself, so a progress tick recomposes only the
bar and not the whole screen. See [Uploads and downloads](transfers.md).

## Reduced motion and the other locals

`ui/LocalSettings.kt` holds the settings that composables deep in the tree need, as `CompositionLocal`s. Each is
provided once, in `App.kt`:

| Local | Default | What it is |
| --- | --- | --- |
| `LocalReduceMotion` | `false` | Whether animations are reduced |
| `LocalBlurredBackdrop` | `true` | Whether previews show the file's thumbnail, blurred, as a backdrop |
| `LocalTextWrap` | `true` | Whether long lines of a text preview wrap or scroll sideways |
| `LocalBottomInset` | `0.dp` | How far the system navigation bar reaches over the content, while the app's bottom bar is hidden |
| `LocalVideoLoop` | off | `VideoLoopSetting`: whether videos loop, and how to change it |

`LocalReduceMotion` is on when the user turned on **Settings → Accessibility → Reduce animations**, *or* when animations
are off in the Android settings (`AppSettings.systemAnimationsDisabled()`, the animator duration scale is 0).

**Why every animation must respect it:** people turn animations off because motion makes them unwell, distracts them, or
because their device is slow. The rule, from `LocalSettings.kt`: decorative motion is left out (sliding, scaling,
spinning, growing, rolling text and animated scrolling); fades and changes that give feedback, like a transfer's
progress, stay. Read it wherever something moves, and give the reduced case a short fade or nothing:

```kotlin
val reduceMotion = LocalReduceMotion.current
AnimatedVisibility(
    visible = visible,
    enter = if (reduceMotion) fadeIn(tween(100)) else slideInVertically(tween(200)) { it } + fadeIn(tween(200)),
    exit = if (reduceMotion) fadeOut(tween(100)) else slideOutVertically(tween(200)) { it } + fadeOut(tween(150))
)
```

The shared components already do this (`AppMenu`, `AppMenuPages`, `OdometerText`, `SelectionActionBar`,
`snackbarMotion`), so building from them gets it for free.

## The theme

`ui/theme/Theme.kt` has `MaterialdrainTheme`. On Android 12 and newer it uses dynamic colour (the colours of the
wallpaper, `dynamicDarkColorScheme` / `dynamicLightColorScheme`); before that, a fixed purple scheme from `Color.kt`.
Dark or light follows the system (`isSystemInDarkTheme()`). `Type.kt` sets `bodyLarge` and keeps Material's other text
styles.

Take colours from `MaterialTheme.colorScheme`, never as fixed values: then a screen follows the wallpaper and dark mode
like the rest. The file tiles, the selection bar (`secondaryContainer`) and the menus' highlights all do.

## The app lock

`AppLockOverlay` (`auth/AppLockOverlay.kt`) covers the app and takes every touch while it's locked, asking for the
fingerprint, face or screen lock straight away; see [Data and security](data-and-security.md).
