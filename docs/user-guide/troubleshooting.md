# Troubleshooting

??? question "A host says it doesn't answer"
    The badge of the host switcher (and its menu) says why: the address can't be found, the connection was refused, it
    took too long, or your phone is offline. The check is only whether the address answers, without signing in; whether
    your sign-in works is **Test connection** on the host's card in **Settings → Hosts**. For an SMB share, the
    server has to be on the same network (or reachable through a VPN).

??? question "There are no files, or a screen is missing"
    A host shows only the screens its config turns on and the host can back. A config whose `screens` list is empty,
    or only names screens the host can't offer, shows a message saying so. Remove `screens` from the config to show
    everything the host can do. See [Screens](../hosts/config-format.md#screens).

??? question "A thumbnail is missing"
    Thumbnails are only made for photos, videos and songs with a cover; other files show a tile with the kind of file.
    The first time, a photo or video is read from the host, which takes a moment on a slow connection; after that it
    comes from the phone.

??? question "A video won't play"
    The app plays what the phone's own player can: MP4, WebM and MKV with common codecs nearly always work, some AVI
    files or rare codecs don't, whichever host they're on. In the Nightly build, **Open in another app** in the
    player's settings :nightly: hands it to a player of your own, which may play what this one can't.

??? question "A song stops when I leave it"
    That's the default. Turn on **Settings → Media → Play songs in the background** :nightly: for it to go on, with the
    miniplayer and controls in the notification.

??? question "A shared file wasn't uploaded"
    In the Nightly build :nightly:, shared files wait in a bar at the bottom of the Files and Filesystem screens until
    you press **Upload here** where they should go. Nothing is uploaded before that.

??? question "Uploads go up in the wrong order"
    Turn on **Settings → Files → Upload by last change**. The order is the files' *last change*
    on the phone. Photos picked from Google Photos are often copies made when you pick them, so they all have nearly
    the same date; picking them through the system's file picker (*Browse*) gives their real dates.

??? question "Something else"
    [Open an issue on GitHub](https://github.com/fidesosu/Materialdrain/issues) with what you did and what happened.
    The app writes what it does to Android's log, tagged `MD/`: `adb logcat | grep "MD/"` (with the phone connected to a
    computer) shows it. It never includes your passwords or keys.
