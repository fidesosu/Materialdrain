# What you're installing

Everything the app asks your phone for, everything it connects to, what it keeps, and how to check that the APK you
downloaded is the real one. All of it can be checked in the [source code](https://github.com/fidesosu/Materialdrain),
and the parts about the APK in the APK itself.

## In short

- **No ads, no analytics, no crash reports, no tracking.** The app has none of the libraries for them, and sends
  nothing about you or your use of it to anyone.
- **It only connects to your hosts**: the ones you add, and Pixeldrain, which is built in. It doesn't check for
  updates of itself; you do that, on the [home page](https://materialdrain.senko.tools/).
- **It can't read your files on its own.** It has no storage permission: it only sees the files you pick or share
  with it, and writes downloads to your Download folder.
- **Your passwords and keys stay on the phone, encrypted**, and never go into Android's backups.
- **Free and open source.** The APKs are built from the public code by GitHub, not on someone's computer.

## Where the APK comes from

Every APK since 1.5 is built by the app's [build workflow](../development/index.md#the-build-workflow) on GitHub
Actions, from the code as it is on GitHub:

- A push to `main` builds a **stable** release; a push to `dev` builds the **development build** (Nightly on the home
  page). The tests run first, and a build whose tests fail isn't published.
- The APK is signed with the app's release key, which GitHub keeps as a secret, and published to the repository's
  [releases](https://github.com/fidesosu/Materialdrain/releases). The home page only lists those: every download
  comes straight from GitHub.
- Each release says which commit it was built from, and **Settings → About** in the app shows its version and kind of
  build, and when that commit was made.

The releases before 1.5 were built by hand, before the workflow existed.

## Checking a download

There are two checks: the checksum says the file is the one GitHub published, and the signature says who made it.

**The checksum.** The home page shows each APK's SHA-256 under its card (**Copy** copies all of it), and hovering over
an older version shows its own. Compare it with the file you downloaded:

=== "Windows"

    ```powershell
    certutil -hashfile Materialdrain-1.5.20261014.1558.apk SHA256
    ```

=== "Linux"

    ```bash
    sha256sum Materialdrain-1.5.20261014.1558.apk
    ```

=== "macOS"

    ```bash
    shasum -a 256 Materialdrain-1.5.20261014.1558.apk
    ```

**The signature.** Stable and development builds are signed with the same key, whose certificate is:

- **Package:** `tools.senko.materialdrain`
- **Certificate SHA-256:** `d2:49:9e:b4:96:0b:01:d4:36:a9:5e:c8:01:3d:b8:1d:ea:ad:a2:ea:e0:36:e3:90:86:a5:34:ba:32:60:85:a6`
- **Issued to:** `CN=fides, OU=senko.tools, O=senko.tools`

With the Android SDK's `apksigner`:

```bash
apksigner verify --print-certs Materialdrain-1.5.20261014.1558.apk
```

It prints `certificate SHA-256 digest: d2499eb4960b01d436a95ec8013db81deaada2eae036e39086a534ba326085a6`. Android checks
this too, by itself: an update signed with any other key won't install over the app, so once it's installed, nobody
else can replace it.

## What it asks your phone for

These are the permissions in the APK, all of them. None of them is a "dangerous" one that Android asks you about,
except notifications.

| Permission | What for |
| --- | --- |
| **Internet** (`INTERNET`) | Reaching your hosts |
| **Network state** (`ACCESS_NETWORK_STATE`) | Knowing when the phone is offline, and checking the hosts again when the network changes |
| **Notifications** (`POST_NOTIFICATIONS`) | The progress of uploads and downloads, and when they finish or fail. Android asks you first |
| **Foreground service** (`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC`) | Transfers keep going with the app closed, with a notification for as long as they run |
| **Install apps** (`REQUEST_INSTALL_PACKAGES`) | Opening an APK you downloaded with the app hands it to Android's installer, which won't open for the app without this. Android asks you once whether to allow it. The app never installs anything by itself |
| **Biometrics** (`USE_BIOMETRIC`, `USE_FINGERPRINT`) | The optional app lock, with your fingerprint, face or screen lock. Added by Android's own biometric library |
| **Keep awake** (`WAKE_LOCK`) | Keeping a video playing with the screen on. Added by the video player (Media3) |
| `tools.senko.materialdrain.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` | Not a permission over anything of yours: the app's own, which Android's support library adds so that only the app can send messages to itself |

The development build adds two, for features not in a stable release yet:

| Permission | What for |
| --- | --- |
| **Media playback** (`FOREGROUND_SERVICE_MEDIA_PLAYBACK`) | Songs going on in the background, with controls in the notification and on the lock screen |
| **Vibrate** (`VIBRATE`) | The app's own vibrations, with the strength you choose in the settings |

**What it doesn't ask for:** your files and photos (storage), contacts, location, camera, microphone, phone, SMS,
accounts or the list of your apps.

### What else it adds to your phone

- A **share target**: "Share" from another app can send files to Materialdrain, to upload them, and in the Nightly
  build :nightly: text, with a Pixeldrain link opened in the app.
- Pixeldrain's **file and list links** (`pixeldrain.com/u/…`, `/l/…`) :nightly: can open in the app. Android asks, or
  you turn it on in the app's system settings under *Open by default*. The app doesn't claim them as its own.
- **Shortcuts** on its icon :nightly: (Upload, Files, Filesystem), and a **Quick Settings tile** :nightly: that opens
  the upload, if you add it.
- Your hosts in the **system's file picker** :nightly:, read only, if you turn that on in the settings. Only the system
  can reach it, for an app you're picking a file in, and it shows nothing while the app lock is on.
- **Media controls** :nightly: for a song playing in the background, in the notification and on the lock screen. Other
  apps that control music (a watch, a car) can play, pause, skip and seek it, but can't choose what it plays.
- **Shared files** :nightly:: *Share file*, for a file without a link, downloads it into the app's cache and hands only
  that one file to the app you pick. Those copies are cleared after a day.

## What it connects to

| Where | When |
| --- | --- |
| **Pixeldrain** | When you use it. And, since it's built in, even if you don't: the host switcher checks whether each host answers, with one request to `pixeldrain.com` without signing in, when the app starts, when the network changes, and every few minutes while the app is open (the data saver can make it wait until you ask) |
| **The hosts you add** | When you use them, and the same check |
| **A config's update URL** | Only for a host whose config has one, to look for a newer version of the config. See [Sharing and updates](../hosts/sharing-and-updates.md) |
| **127.0.0.1** | The app's own small server on the phone, for SMB files to its image viewer and players. That traffic never leaves the phone |

And nothing else. The links in **Settings → About** (this site, the releases, the source, reporting a problem) open in
your browser, and only when you tap them.

Everything goes over HTTPS: Android refuses a plain `http://` address before anything is sent. SMB shares can encrypt
their traffic too (see [SMB](../hosts/smb.md)).

## What it keeps on your phone

- **Your settings and your hosts' configs.** A config never holds a password or key.
- **Your sign-ins**: passwords, API keys and session tokens, encrypted with a key in Android's Keystore, which never
  leaves the phone. Your Pixeldrain password isn't kept at all, only the session it signs in to.
- **Caches**: thumbnails, media, songs' tags and the last few PDFs, which Android may clear when it needs the space.
- **Where you left each video** :nightly:, for the last 200, and **your account's picture** :nightly:, if you chose
  one.
- **Downloads** go to your Download folder (or :nightly: a folder of the app's own in it), like a browser's.

Your settings, hosts, sign-ins and video positions don't go into Android's own backups (to your Google account, or to
a new phone): the sign-ins couldn't be decrypted anywhere else, and the rest would be half a setup without them. The
account picture :nightly: isn't left out of them yet, nor a note that the built-in Pixeldrain config was set up once. On a new phone, export your hosts' configs
from the old one and import them. The app doesn't log passwords or keys either, so a bug report can't leak them.
Uninstalling the app removes all of it, except the files you downloaded. And with **Block screenshots** :nightly:
on, the app comes out black in screenshots and screen recordings, and blank in the recent apps.

The details, for each file: [Data and security](../development/data-and-security.md).

## What's inside

The app is written in Kotlin, with Jetpack Compose and Material 3. Besides Android's own libraries (AndroidX), it's
built with:

| Library | For |
| --- | --- |
| [OkHttp](https://square.github.io/okhttp/) and [Ktor](https://ktor.io/) | Talking to the hosts |
| [Media3 / ExoPlayer](https://developer.android.com/media/media3) | Playing video and audio |
| [Coil](https://coil-kt.github.io/coil/) | Loading images and thumbnails |
| [jcifs-ng](https://github.com/AgNO3/jcifs-ng) | SMB shares |
| [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization) and coroutines | Reading configs and answers, and doing things in the background |

The only native code in the APK is a small part of Compose (`libandroidx.graphics.path.so`), in a version for each kind
of phone processor.

## Stable or Nightly

Both are the same app, with the same signature, so either installs over the other and keeps your settings and
sign-ins. Nightly has the newest changes as soon as they're made, and the bugs that come with them; stable releases
are the tested ones. Going back to an older version means uninstalling first, which removes your sign-ins.
