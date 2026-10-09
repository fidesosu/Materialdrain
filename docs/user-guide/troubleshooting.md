# Troubleshooting

??? question "A host says it doesn't answer"
    The dot of the host switcher (and its menu) says why: the address can't be found, the connection was refused, it
    took too long, or your phone is offline. The check is only whether the address answers, without signing in; whether
    your sign-in works is **Test connection** on the host's card in **Settings → Advanced**. For an SMB share, the
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
    files or rare codecs don't, whichever host they're on.

??? question "Uploads go up in the wrong order"
    Turn on **Settings → Uploads → Upload in the order the files were changed**. The order is the files' *last change*
    on the phone. Photos picked from Google Photos are often copies made when you pick them, so they all have nearly
    the same date; picking them through the system's file picker (*Browse*) gives their real dates.

??? question "Something else"
    [Open an issue on GitHub](https://github.com/fidesosu/Materialdrain/issues) with what you did and what happened.
    The app writes what it does to Android's log, tagged `MD/`: `adb logcat | grep "MD/"` (with the phone connected to a
    computer) shows it. It never includes your passwords or keys.
