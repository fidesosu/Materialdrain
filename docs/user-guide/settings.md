# Settings

The gear at the top right of the screen. The settings are tiles, one per category, each with a short status at its end
(the number of hosts, whether the data saver is on, the version); a tile opens its category's page. A setting with an ⓘ
unfolds an explanation under it when tapped, and a setting's own settings are indented under it, out of reach while
it's off.

## Account

The bigger tile at the top: your Pixeldrain account's picture, name, e-mail and plan, how full its storage and this
month's transfer are, or a prompt to sign in. Its page is the account itself: storage and transfer, statistics, the
profile, sign-in and security, sharing, the plan and billing, removed files, and the API key with its own **Save**.
Signing in is described in [Signing in to Pixeldrain](index.md#signing-in-to-pixeldrain). Changes Pixeldrain only allows
on its website (the password, two-factor sign-in, e-mail addresses, deleting the account) open its website instead.

## Hosts

Your custom hosts: each one's card (sign-in, connection test, updates, edit, export, remove), **Add a custom host**, and
**Check all for updates**. See [Hosts](../hosts/index.md).

## Data saver

| Setting | What it does |
| --- | --- |
| **Data saver** | What the app loads by itself waits until you ask for it. Nothing is turned off |
| **Applies** | **Always**, or **On mobile data**: only on a metered network (mobile data, a phone's hotspot), followed as the network changes |
| **Thumbnails** | A file's thumbnail loads when its tile is tapped, or all of them with **Thumbnails** above the list |
| **Previews** | Pictures, videos, songs, text, PDFs and archive contents wait in a file's details, in the preview's own frame, with "Tap to load" and about how much that loads. A song's player is there as always, as it only streams once it's played |
| **Config updates** | Configs with automatic updates wait for **Check all for updates** on the Hosts page, instead of being checked once a day |
| **Host checks** | The host switcher checks whether the hosts answer only with its refresh, instead of every few minutes |

## Look and feel

| Setting | What it does |
| --- | --- |
| **Blurred backdrop** | A file's thumbnail, blurred, fills the space around a full-screen image or video and shows while a preview loads |
| **Wrap long lines** | Long lines of a text preview break onto the next line; off, they keep their length and scroll sideways |
| **Reduce animations** | Leaves out sliding, scaling, spinning and rolling text, and scrolls without animation; screens only fade. Also on when animations are turned off in Android's own settings |

## Files

| Setting | What it does |
| --- | --- |
| **Hide .search_index.gz** | Pixeldrain keeps the paths of your files in this file, and makes it again when it's deleted. Hidden by default, so it can't be deleted by accident |
| **Upload by last change** | Several files go up oldest first, by when they were last changed on the device, one after another, so they reach the host (and get their upload dates) in that order. Off, a few go up at once |
| **Tap twice to download** | The **Download** button in a file's details asks to be tapped again within 3 seconds, so a slip of the finger doesn't start a download of what may be gigabytes. Off, one tap starts it. On by default |
| **Most search results** | No limit, or at most 200, 50 or 20. Searching a big folder tree can match thousands of files; a limit can help on slower phones |

## Notifications

Links to Android's own settings for the app's notifications, and for each of its three kinds: transfer progress,
completed transfers and failed transfers. Each can have its own importance, sound and lock screen visibility there.

## Privacy and security

| Setting | What it does |
| --- | --- |
| **Lock with fingerprint or face** | Asks for your fingerprint, face or screen lock when the app is opened again after it was in the background for a while |

## Developer

Things being tried out. **FAB navigation prototype** replaces the bottom tabs with a floating button: tap it for a
window of the host's tabs, swipe it sideways to move it to the left, middle or right. **Menu of** shows that window with
a much bigger (pretend) menu instead, to see how it copes.

## About

The app's version, its kind of build (stable, development or debug), when the commit it was built from was made, and a
button to copy all of it for a bug report. Then links to the website, this documentation, the changes of each release,
the source code, and reporting a problem.
