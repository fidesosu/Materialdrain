# The screens

Which screens you see depends on the host you're using: Pixeldrain has all four, a WebDAV, S3 or SMB host has Upload
and Filesystem, and a REST API host has whichever its config turns on (see [Screens](../hosts/config-format.md#screens)).
When a host has only one screen, the tabs are hidden and that screen is simply shown.

## Upload

Pick one or more files, or switch to text and paste some, then press **Upload**.

- Picked files are listed with their size before anything is sent; a single file shows a preview: an image, a video's
  first frame, a song's cover and details, a PDF's page count, an app's icon and name.
- Up to three files go up at a time. With **Settings → Uploads → Upload in the order the files were changed** on, they
  go one after another instead, oldest first, so the newest ends up at the top of the host's list.
- When it's done, each file's link can be copied, or all of them at once.
- **Sharing into the app**: in any other app, *Share* a file (or several) and choose Materialdrain. They land here,
  ready to upload.

On Pixeldrain, files go into your account's file list. On a WebDAV, S3 or SMB host, which have no such list, they go
into the host's top folder (the config's `root_path` or `prefix`).

## Files

Every file of your account in one list (on Pixeldrain, the files on your account page), by name at first.

- **Sort** with the *Sort by* card above the list: by name, size or date. Choosing the same field again flips the
  direction.
- **Pull down** to refresh.
- Tap a file for its [details](#file-details). The **⋮** of a file has *Download*, *Copy link*, *Share link*,
  *Add to filesystem*, *Add to list*, *Select* and *Delete*.

## Lists

Pixeldrain's lists: shareable collections of files. Open one to see its files, sorted like the Files screen. A list you
own can have files removed from it, and the **All (ZIP)** button downloads the whole list as one zip.

## Filesystem

A folder browser, for every host that has folders.

- The **path** above the list shows where you are. Tap any part of it to go back there; the back button goes up a
  folder.
- **New** (next to *Sort by*) uploads into the open folder, makes a folder, or, on Pixeldrain, imports files by their ids.
- The **⋮** of a file or folder has *Download*, *Copy link*, *Share link*, *Rename*, *Move*, *Select* and *Delete*,
  where the host allows them.
- **Move** opens a folder picker: tap folders to go into them, the path at its top to go back.

## Search

The magnifier at the top left. Type, and the matches appear under the search box, over the blurred screen; tap one to
open it. The list behind isn't changed.

On the Filesystem screen, search looks through the open folder **and every folder under it**: the app lists the
folders in the background while the box is open, nearest first, and the matches come in as they're found. Each match
says which folder it's in. Searching again is instant until something changes on the host.

All matches are shown, unless you set a limit in **Settings → Search**.

## Selecting several files

Long-press a file (or choose *Select* in its menu) to start selecting; tap others to add them. A bar at the bottom then
offers what can be done with all of them at once:

- on the **Filesystem** screen: download (one after another), move, delete;
- on the **Files** and **Lists** screens: download as one zip, add to a list, add to the filesystem, delete, and in a
  list of yours, remove from the list.

Back, or the ✕, ends the selection.

## File details

Tap a file to open it.

- **The preview** at the top: an image (tap for full screen, pinch to zoom), a video (tap to play full screen), a song
  with its player, the start of a text or code file with colours (open it full screen for all of it), or the contents
  of an archive (zip, 7z, rar, tar, apk), whose files can be downloaded one by one.
- **The actions**: *Download* (tap twice: the first tap asks for the second, so a slip of the finger doesn't start a
  big download), *Share the link*, *Copy the link*, *Delete*. While it downloads, the button shows the progress and
  cancels when tapped; when it's done, it opens the file.
- **The details**: size, type, dates, and on Pixeldrain the views, downloads and bandwidth, links and ids (tap to copy).

## Transfers

While something uploads or downloads, a bar under the top bar shows its progress, speed and time left, with a
**Cancel** button beside *Sort by*. Transfers keep running when you leave the app: a notification shows their progress
and can cancel them, and another says when they finish (or fail) while the app is in the background.
