# Getting started

!!! note "Stable and Nightly"
    These pages describe the app as it is now, in the Nightly build. Anything it has that the newest stable release
    doesn't yet is marked :nightly:. The end of each page says when it was last changed, and which stable release was
    the newest then.

## Installing

The app isn't on the Play Store. Download it from the [home page](https://materialdrain.senko.tools/), or from the
[releases on GitHub](https://github.com/fidesosu/Materialdrain/releases):

- **Stable** releases are the tested ones. Each has its own version.
- The **development build** (**Nightly** on the home page) is the newest state of the app, rebuilt with every
  change. It has the newest features, and may have bugs.

Open the downloaded `.apk` on your phone to install it. Android asks once whether your browser (or file manager) may
install apps; allow it. A newer build installs over an older one, stable or development alike, and your settings and
sign-ins stay. Going back to an older build means uninstalling first.

Android 10 or newer is needed.

[What you're installing](what-you-get.md) lists everything the app asks your phone for and connects to, and how to
check that a download is the real one.

## Signing in to Pixeldrain

Pixeldrain is built into the app. Open **⋮** at the top right → **Settings** → **Account** (in a stable release, the
gear at the top right), and sign in in one of three ways:

1. **Username (or e-mail) and password.** If your account has two-factor authentication, a field for the code
   appears when it's needed.
2. **A login link by e-mail**: press *E-mail me a login link*, then paste the link from the e-mail.
3. **An API key**, from your Pixeldrain account page, under *API keys*.

The app keeps the session (or the key) encrypted on the device; your password isn't stored.

## Adding another host

Open **Settings → Hosts**. Under **Add a custom host**, pick the kind of host (*WebDAV*, *S3*, *SMB*, or
*REST API*): a form opens with everything that kind needs, with the usual values already filled in. Change the
address (and anything else that's different for you) and press **Add host**. Then enter your sign-in on the host's
card, which appears in the list above.

Someone may also have given you a **config**, a short piece of JSON describing a host. Paste it into *Or paste a
config* and press **Add host**, or **Review first** to see it in the form before adding it.

[Everything about hosts](../hosts/index.md){ .md-button }

## Finding your way around

- **The host switcher**, in the middle of the top bar, shows the host you're using, with a badge saying whether it
  answers. Tap it to switch to another host.
- **The tabs** at the bottom move between the screens the host offers: Files, Lists and Filesystem on Pixeldrain. With
  more than five, the last place is **More**, with the rest. On a wide screen (a tablet, a phone on its side) they're
  at the side instead. With **Swipes** navigation :nightly:, there's no bar: swipe sideways across the screen, and a
  row of dots at the bottom says where you are.
- **The magnifier** at the top left searches the screen's files.
- **⋮** at the top right :nightly: has what the screen does (Select, and under **New**: upload, a new folder), how
  its list is shown (**Sort by**, **View**), and **Settings**.
- **Outside the app** :nightly:: long-press its icon for shortcuts to Upload, Files and Filesystem, add the **Upload**
  tile to Quick Settings, or *Share* files to it from any other app.

The next page goes through each screen: [The screens](screens.md).
