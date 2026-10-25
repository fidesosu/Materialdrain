# Settings

Open **⋮** at the top right, then **Settings** (in a stable release, the gear at the top right). The settings are
tiles, one per category, each with a short status at its end (the number of hosts, whether the data saver is on, the version); a tile
opens its category's page. A setting with an ⓘ unfolds an explanation under it when tapped, and a setting's own
settings are indented under it, out of reach while it's off.

## Account

The bigger tile at the top: your Pixeldrain account's picture, name, e-mail and plan, how full its storage and this
month's transfer are, or a prompt to sign in. Its page is the account itself: storage and transfer, statistics, the
profile, sign-in and security, sharing, the plan and billing, removed files, and the API key with its own **Save**.
Signing in is described in [Signing in to Pixeldrain](index.md#signing-in-to-pixeldrain). Changes Pixeldrain only allows
on its website (the password, two-factor sign-in, e-mail addresses, deleting the account) open its website instead.

The account's picture :nightly: is yours to choose and crop; it isn't sent to Pixeldrain.

## Hosts

Your custom hosts: each one's card (sign-in, connection test, updates, edit, export, remove), **Add a custom host**, and
**Check all for updates**. See [Hosts](../hosts/index.md).

## Data saver

| Setting | What it does |
| --- | --- |
| **Data saver** | What the app loads by itself waits until you ask for it. Nothing is turned off |
| **Applies** | **Always**, or **On mobile data**: only on a metered network (mobile data, a phone's hotspot), followed as the network changes |
| **Thumbnails** | A file's thumbnail loads when its tile is tapped, or all of them with **Thumbnails** above the list |
| **Previews** | Pictures, videos, songs, text, PDFs and archive contents wait in a file's details, in the preview's own frame, with about how much that loads. A song's player is there as always, as it only streams once it's played |
| **Config updates** | Configs with automatic updates wait for **Check all for updates** on the Hosts page, instead of being checked once a day |
| **Host checks** | The host switcher checks whether the hosts answer only with its refresh, instead of every few minutes |

## Look and feel

| Setting | What it does |
| --- | --- |
| **Light or dark** :nightly: | **System** (the default), **Light** or **Dark** |
| **Black background** :nightly: | The dark theme on black, which saves power on OLED screens |
| **Wallpaper colours** :nightly: | The app's colours from your wallpaper (Android 12 and newer). On by default |
| **Colour** :nightly: | With the wallpaper's colours off (or before Android 12), the colour the app's are made from: one of the swatches, or your own hex code |
| **Navigation** :nightly: | **Bottom bar**, or **Swipes**: swipe sideways across the screen to change tabs, with a thin row of dots at the bottom for where you are |
| **Tab labels** :nightly: | Every tab's name under its icon, not only the open one's |
| **Open on** :nightly: | The screen the app opens on: **Last** (the default), Files, Lists, Filesystem or Upload |
| **Press back twice to close** :nightly: | Back on the first screen asks to be pressed again before it closes the app. On by default |
| **Swipe between files** :nightly: | A swipe sideways on a file's details (and full screen) goes to the file beside it |
| **Swipe between tabs** :nightly: | A swipe sideways across a screen goes to the tab beside it. Always on with Swipes navigation |
| **Grid** :nightly: | The file lists are a grid of big pictures, instead of rows. Only the default: a folder can have its own, in **⋮ → View** |
| **Compact rows** :nightly: | Lower rows, so more of them fit. Only the default, as Grid |
| **Dates as time ago** :nightly: | "3 days ago" instead of the date |
| **Blurred backdrop** | A file's thumbnail, blurred, fills the space around a full-screen image or video and shows while a preview loads. Off, it's black |
| **Wrap long lines** | Long lines of a text preview break onto the next line; off, they keep their length and scroll sideways |
| **Reduce animations** | Leaves out sliding, scaling, spinning and rolling text, and scrolls without animation; screens only fade. Also on when animations are turned off in Android's own settings |

## Media :nightly:

| Setting | What it does |
| --- | --- |
| **Autoplay videos** | A video starts as soon as its player opens. On by default |
| **Resume videos** | A video opened again goes on from where it was left. The last 200 are remembered, on this phone only. On by default |
| **Skip by** | How far the skip buttons and a double tap go: 5, 10, 15 or 30 seconds |
| **Default speed** | The speed videos and songs start at: 0.75× to 2× |
| **Brightness and volume swipes** | In the video player, a swipe up or down on the left changes the brightness, on the right the volume |
| **Play songs in the background** | A song goes on after you leave it and close the app, with the miniplayer and its controls in the notification and on the lock screen. Off, it stops when you leave it |
| **Tint the player** | The miniplayer and the players take the colour of the song's cover |
| **Compact player** | The player opens as a card a third of the screen high, instead of over the whole screen |
| **Undo closing a song** | Swiping the miniplayer away offers an **Undo**, which puts the song and its queue back |

## Feedback :nightly:

| Setting | What it does |
| --- | --- |
| **Vibration** | The app's own vibrations, when letting go of a swipe would do something. Also follows Android's touch feedback setting. On by default |
| **Strength** | **Light** (the default), **Medium** or **Strong**, with **Try it**. Some phones only tell Light apart from the rest |
| **Song player swipes** | The miniplayer vibrates when letting go would open, skip or close |
| **Navigation swipes** | With Swipes navigation, a light vibration for each tab a swipe goes past |

## Files

| Setting | What it does |
| --- | --- |
| **Hide .search_index.gz** | Pixeldrain keeps the paths of your files in this file, and makes it again when it's deleted. Hidden by default, so it can't be deleted by accident |
| **Folders first** :nightly: | Folders come before files on the Filesystem screen, whatever it's sorted by. On by default |
| **Copy the link after uploading** :nightly: | When an upload is done, its links are copied, one per line. Only for hosts with links |
| **Open downloads when done** :nightly: | A file downloaded on its own opens once it's in |
| **Download into** :nightly: | **Download**, or a folder of the app's own there, **Download/Materialdrain** |
| **Show hosts in the file picker** :nightly: | Your hosts with folders show in the system's file picker and Files app, so other apps can open their files. Read only. Hidden while the app lock is on. Off by default |
| **Upload by last change** | Several files go up oldest first, by when they were last changed on the device, one after another, so they reach the host (and get their upload dates) in that order. Off, a few go up at once |
| **Tap twice to download** | The **Download** button in a file's details asks to be tapped again within 3 seconds, so a slip of the finger doesn't start a download of what may be gigabytes. Off, one tap starts it. On by default |
| **Search result limit** | No limit, or at most 200, 50 or 20. Searching a big folder tree can match thousands of files; a limit can help on slower phones. (**Most search results** in a stable release) |

## Notifications

Links to Android's own settings for the app's notifications, and for each of its three kinds: transfer progress,
completed transfers and failed transfers. Each can have its own importance, sound and lock screen visibility there.

## Privacy and security

| Setting | What it does |
| --- | --- |
| **Lock with fingerprint or face** | Asks for your fingerprint, face or screen lock when the app is opened again after it was in the background. Needs a screen lock on the phone |
| **Lock after** :nightly: | How long the app may be away before it asks again: every time, 30 seconds, 1 minute or 5 minutes |
| **Block screenshots** :nightly: | Screenshots and screen recordings of the app come out black, and the recent apps show it blank |

## About

The app's version, its kind of build (stable, development or debug), when the commit it was built from was made, and a
button to copy all of it for a bug report. Then links to the website, this documentation, the changes of each release,
the source code, and reporting a problem.

A stable release also has a **Developer** category, with a floating navigation button that was being tried out. The
Nightly build no longer has it.
