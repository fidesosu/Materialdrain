# Getting started

## Installing

The app isn't on the Play Store. Download it from the [home page](https://materialdrain.senko.tools/), or from the
[releases on GitHub](https://github.com/fidesosu/Materialdrain/releases):

- **Stable** releases are the tested ones. Each has its own version.
- The **development build** is the newest state of the app, rebuilt with every change. It has the newest features,
  and may have bugs.

Open the downloaded `.apk` on your phone to install it. Android asks once whether your browser (or file manager) may
install apps; allow it. A newer build installs over an older one, stable or development alike, and your settings and
sign-ins stay. Going back to an older build means uninstalling first.

Android 10 or newer is needed.

## Signing in to Pixeldrain

Pixeldrain is built into the app. Open **Settings** (the gear at the top right) → **Account**, and sign in in one of
three ways:

1. **Username (or e-mail) and password.** If your account has two-factor authentication, a field for the code
   appears when it's needed.
2. **A login link by e-mail**: press *E-mail me a login link*, then paste the link from the e-mail.
3. **An API key**, from your Pixeldrain account page, under *API key*.

The app keeps the session (or the key) encrypted on the device; your password isn't stored.

## Adding another host

Open **Settings → Advanced**. Under **Add a custom host**, pick the kind of host (*WebDAV*, *S3*, *SMB*, or
*REST API*): a form opens with everything that kind needs, with the usual values already filled in. Change the
address (and anything else that's different for you) and press **Add host**. Then enter your sign-in on the host's
card, which appears in the list above.

Someone may also have given you a **config**, a short piece of JSON describing a host. Paste it into *Or paste a
config* and press **Add host**, or **Review first** to see it in the form before adding it.

[Everything about hosts](../hosts/index.md){ .md-button }

## Finding your way around

- **The host switcher**, at the top of the screen, shows the host you're using, with a dot saying whether it
  answers. Tap it to switch to another host.
- **The tabs** at the bottom (or the floating button, if you turned that on) move between the screens the host offers:
  Upload, Files, Lists and Filesystem.
- **The magnifier** at the top left searches the screen's files.
- **The gear** at the top right opens the settings.

The next page goes through each screen: [The screens](screens.md).
