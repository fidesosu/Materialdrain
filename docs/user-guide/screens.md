# The screens

Which screens you see depends on the host you're using: Pixeldrain has Files, Lists and Filesystem, a WebDAV, S3 or SMB
host has Filesystem, and a REST API host has whichever its config turns on (see [Screens](../hosts/config-format.md#screens)).
When a host has only one screen, the tabs are hidden and that screen is simply shown. A host can also have several
Filesystem tabs, each opening at a folder of its own (see [Several tabs of one kind](../hosts/config-format.md#several-tabs-of-one-kind)).

## The top bar

- **Back** or **the magnifier** at the start: back on a file's details and in the settings, search on the screens with
  files.
- **The host switcher** in the middle, on the screens with files, and the screen's name elsewhere. It shows the host
  you're using, with a badge: a tick on green when it answers, a cross on red when it doesn't, a question mark when it
  hasn't been checked. Tap it for all your hosts, each with where it is, whether it answers and whether you're signed
  in, and **Manage hosts**.
- **⋮** at the end :nightly:, with what the screen does first:
    - **Select**, and on an open list **Download all**, on a folder **Download this folder**;
    - **New**: **Upload** (into the open folder, or as new files of the host), **New folder**, and on Pixeldrain
      **Import files by ID**;
    - **Sort by**: name, size or date. Choosing the field it's sorted on flips the direction. Every list of files shares
      one order, kept across restarts;
    - **View**: rows, a grid of big pictures, or **Smart**, which picks the grid when most of the files are pictures and
      videos; and **Compact rows**. What you choose is this folder's (or list's) own; on the Filesystem it can be for its
      subfolders too. **Use the default** goes back to the one in **Settings → Look and feel**;
    - **Settings**.

    Sort by and View stay open while you choose, so you see the change. In a stable release, Sort by and New are
    cards above the list, and the settings are behind a gear.

## Files

Every file of your account in one list (on Pixeldrain, the files on your account page).

- **Pull down** to refresh.
- Tap a file for its [details](#file-details). Its **⋮** has *Download*, *Share link*, *Add to…* (the filesystem or a
  list), *Delete*, and for a song playing in the background, *Add to queue* :nightly:.
- **Upload** is in the ⋮ menu, under **New** :nightly:.

## Lists

Pixeldrain's lists: shareable collections of files. A list's **⋮** has *Open*, *Download* and *Share link*. Opened, its
files are sorted like the Files screen; a list you own can have files removed from it (*Remove from this list*). Taking
the last file out of a list deletes the list, which the app says first.

**Download all**, in the ⋮ menu of an open list, downloads every file of it: each on its own, or as one zip.

## Filesystem

A folder browser, for every host that has folders. Each Filesystem tab keeps its own folder, and opens at it again
after a restart; a tab with its own root folder can't go above it.

- The **path** above the list shows where you are. Tap any part of it to go back there; the back button goes up a
  folder.
- The **⋮** of a file has *Download*, *Rename*, *Move* and *Delete*, where the host allows them. A folder's
  *Download* :nightly: has **Separately** (its files, in the same folders, in your Download folder) and **As one ZIP**.
  On a host whose files have no link (SMB, a private WebDAV), *Share file* :nightly: downloads the file and hands it to
  the share sheet.
- **Move** :nightly:: browse to the folder they go into; a bar at the bottom says what's moving and where, with
  **Move here**. Its ✕ leaves them where they are. (In a stable release, a folder picker opens.)
- Folders come first :nightly:, unless you turn that off in **Settings → Files**.

## Sharing into the app

In any other app, *Share* a file (or several) and choose Materialdrain.

- **Files** :nightly: wait in a bar at the bottom of the screens with files, until you're where they should go: in a
  folder of a Filesystem tab, or on the Files screen as new files of the host. **Upload here** sends them; the ✕ drops
  them. Nothing is uploaded before that. (In a stable release, the Upload screen opens with them.)
- **A Pixeldrain link** :nightly: (`pixeldrain.com/u/…` for a file, `/l/…` for a list), shared or tapped in another
  app, opens it in the app, on the built-in Pixeldrain. Android asks the first time which app opens such links, or
  turn it on in the app's system settings under *Open by default*.
- **Other text** :nightly: becomes a text file, which waits like a shared file.

The app waits for the unlock first when it's locked.

## Upload

Only on a host that has nowhere else to upload to: on the others, upload from the **⋮** menu of the Files or Filesystem
screen :nightly:. (In a stable release, every host that takes uploads has this screen.)

Pick one or more files, or switch to **Upload Text** and paste some, then press **Upload**.

- Picked files are listed with their size before anything is sent; a single file shows a preview: an image, a video's
  first frame, a song's cover and details, a PDF's page count, an app's icon and name.
- Up to three files go up at a time. With **Settings → Files → Upload by last change** on, they go one after another
  instead, oldest first, so the newest ends up at the top of the host's list.
- When it's done, each file's link can be copied, or all of them at once (or right away, with **Copy the link after
  uploading** :nightly:).

On Pixeldrain, files go into your account's file list. On a WebDAV, S3 or SMB host, which have no such list, they go
into the host's top folder (the config's `root_path` or `prefix`).

## Search

The magnifier at the top left. Type, and the matches appear under the search box, over the blurred screen; tap one to
open it. The list behind isn't changed.

On the Filesystem screen, search looks through the open folder **and every folder under it**: the app lists the
folders in the background while the box is open, nearest first, and the matches come in as they're found. Each match
says which folder it's in. Searching again is instant until something changes on the host.

All matches are shown, unless you set a limit in **Settings → Files → Search result limit**.

## Selecting several files

Long-press a file (or choose **Select** in the ⋮ menu) to start selecting; tap others to add them. A bar at the bottom
then offers what can be done with all of them at once, the first few as buttons and the rest in its own ⋮:

- on the **Filesystem** screen: download (each file on its own, or as one zip :nightly:), move, delete;
- on the **Files** and **Lists** screens: download (each on its own, or as one zip), delete, add to a list, add to the
  filesystem, and in a list of yours, remove from the list;
- everywhere, while a song plays in the background, *Add to queue* :nightly:, in the order you selected them; and
  **Select all**.

Back, or the ✕, ends the selection.

## File details

Tap a file to open it. With **Settings → Look and feel → Swipe between files** :nightly: on, a swipe sideways goes to
the file beside it in the list.

- **The preview** at the top:
    - **a picture** (animated ones move): tap for full screen, pinch or double-tap to zoom;
    - **a video**: tap to play it full screen (see [Videos](#videos));
    - **a song**: its card, with its cover, title and artist from the file's own tags, and its player (see
      [Songs](#songs));
    - **a text or code file**: the start of it, coloured, in a card the size of the others, saying what it is and how
      long. Its badge opens all of it full screen :nightly:, with numbered lines, **Find in the text**, wrapping, bigger
      or smaller text, and copying all of it, or only some lines: long-press a line's number, then tap another. A
      **Markdown** file :nightly: is shown as it looks, with a button for its source;
    - **a PDF**: the top of its first page and how many pages it has; tap it for every page full screen, scrolled with
      a finger, zoomed with two (or a double tap). It's loaded into the app to be shown (at most 64 MB, and only the
      last few PDFs are kept); one over 16 MB waits for a tap first;
    - **an archive**: what's inside it: open its folders, and tap a file to save it. On Pixeldrain, zip, 7z,
      rar, tar and apk; on WebDAV, S3 and SMB :nightly:, zips, of which only the parts needed are read.

    With the [data saver](settings.md#data-saver) on, the preview waits for a tap. In the full screen view, a swipe
    sideways goes to the next file of the same kind.

- **The actions**: **Download**, **Share** (the link, or :nightly: the file itself where it has none) and **Delete**.
  *Download* asks to be tapped twice, so a slip of the finger doesn't start a big download (**Settings → Files → Tap
  twice to download** turns that off). While it downloads, the button shows the progress and cancels when tapped; when
  it's done, it opens the file. The snackbar that says it's done has an **Open** too. An APK opens the installer,
  which asks once to allow installs from Materialdrain.
- **The details**: type, size, dates, the folder it's in, and on Pixeldrain the views, downloads and bandwidth, when it
  deletes itself, and its links and ids. Tap a value to see all of it; the buttons beside it copy or open it.

## Videos

Full screen, in the app's dark colours. A tap shows or hides the controls.

- **Double-tap** the left or right side to skip back or on (by **Settings → Media → Skip by**), the middle to pause.
- **Hold** it down to play at double speed :nightly:, until you let go.
- **Pinch** to zoom in :nightly:, up to four times, and drag to move about.
- With **Brightness and volume swipes** :nightly: on, swipe up or down on the left for the brightness, on the right
  for the volume, and sideways to seek.
- **The buttons at the top** :nightly:: fit or fill the screen, turn it to landscape or portrait, and the settings:
  **Loop**, **Speed**, **Subtitles** and **Audio** (where the video has them), and **Open in another app**, for a
  player of your own or one that casts it.
- A video opened again goes on from where you left it :nightly: (**Resume videos**).

## Songs

A song plays from its card in its details. With **Settings → Media → Play songs in the background** :nightly: on, it
goes on when you leave its details and close the app, with controls in the notification and on the lock screen, and
the songs of the list it was opened from after it.

- **The miniplayer** :nightly: floats above the tabs: its cover, title and play button, with a line along its bottom for
  how far it is. Swipe it sideways for the song before or after, down to close the song, up (or tap it) for the
  player.
- **The player** :nightly: covers the screen: the cover (swipe it for the next song), the seek bar, repeat (the queue,
  or the one song), previous, play, next and shuffle, and what kind of file it is ("FLAC • 24-bit • 1751 kb/s •
  48.0 kHz"). Pull it down, or press back, to hide it; the song goes on. With **Compact player** on, it opens as a card
  a third of the screen high instead, which a pull up grows to the whole screen.
- **The queue** :nightly:, behind its button in the player: the songs in the order they play, the ones played greyed
  out. Tap one to play it, ✕ to take it out, and drag its handle to move it. *Add to queue*, in a song's ⋮ and in the
  selection bar, puts more at its end.

## Downloads

Downloads go into your **Download** folder (or :nightly: **Download/Materialdrain**, in **Settings → Files**), keeping
the name the host gives the file. A name that's already taken gets a number before its extension: `photo.jpg`, then
`photo (1).jpg`.

- **A folder** :nightly:, or several files, downloads **Separately**, its folders made again in Download around their
  files, or **As one ZIP**: Pixeldrain makes the zip itself, for other hosts the app zips the files as they come in.
- One of more than 100 files, or over 1 GB, asks first :nightly:: a folder can hold far more than it looks.
- With **Open downloads when done** :nightly: on, a single file opens once it's in.

## Transfers

While something uploads or downloads, a bar under the top bar shows its progress, speed and time left, with a
**Cancel** card above the list. Transfers keep running when you leave the app: a notification shows their progress
and can cancel them, and another says when they finish (or fail) while the app is in the background.

## Outside the app

- **Shortcuts** :nightly:: long-press the app's icon for Upload, Files and Filesystem.
- **A Quick Settings tile** :nightly:, **Upload**, if you add it: it opens the app to upload, after the phone is
  unlocked.
- **The system's file picker** :nightly:: with **Settings → Files → Show hosts in the file picker** on, your hosts with
  folders show in it (and in the Files app), so other apps can open their files. Read only, and hidden while the app
  lock is on.
