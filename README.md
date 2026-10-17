# Materialdrain

Materialdrain is an Android file client built with Jetpack Compose and Material 3. It started as a Pixeldrain app and
still has Pixeldrain built in, but it can also connect to your own storage:

- **Pixeldrain**: uploads, your files and lists, the filesystem, and your account.
- **WebDAV** (Nextcloud, TrueNAS and others), **S3** (MinIO, TrueNAS and others) and **SMB** shares.
- **Generic REST** hosts, described by a JSON config.

You can upload, browse, search, preview (pictures, video, music, PDFs, text and archives), download, share, move and
delete files, on as many hosts as you like.

## Setup

- Download and install a version from the GitHub
  [Releases page](https://github.com/fidesosu/Materialdrain/releases). It needs Android 10 or newer.
- To use Pixeldrain, go to Settings → Account and log in with an API key, or with your username or e-mail and password.
- To add another host, go to Settings → Hosts.

> To open the settings, tap the ⋮ menu at the top right of the screen and choose Settings.

## Host configs

Example configs for other hosts are in [`docs/provider-configs`](docs/provider-configs) (MinIO, Nextcloud, SMB, TrueNAS
over S3 and WebDAV, and Pixeldrain as a REST host). Paste one into "Or paste a config" in Settings → Hosts, or start
from one of the "New host" templates there.

I don't have a schedule for updates. I'll update the app when I feel like it.
