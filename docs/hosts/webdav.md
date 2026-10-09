# WebDAV

For Nextcloud, ownCloud, TrueNAS, and any other WebDAV server. The WebDAV protocol is built into the app, so a config
only says where the server is and how to sign in.

```json
{
  "kind": "webdav",
  "name": "Nextcloud",
  "base_url": "https://cloud.example.com/remote.php/dav/files/{username}",
  "auth": { "type": "BASIC", "password_auth": { "mode": "BASIC" } }
}
```

## Fields

| Field | Default | Meaning |
| --- | --- | --- |
| `base_url` | required | The address of the folder the app sees as the top. `{username}` in it is filled in with the username you sign in with |
| `auth` | `{"type": "BASIC"}` | How the sign-in is sent; see [Sign-in](sign-in.md) |
| `root_path` | `/` | The folder (under `base_url`) the app starts in, and which it stays inside |
| `screens`, `meta` | | See [The config format](config-format.md) |

## Finding the address

| Server | `base_url` |
| --- | --- |
| Nextcloud | `https://your.server/remote.php/dav/files/{username}` |
| ownCloud | `https://your.server/remote.php/dav/files/{username}` (or `/remote.php/webdav`) |
| TrueNAS | `https://your.nas:8081/your-share`, as set up under *Shares → WebDAV* |
| Other servers | The WebDAV address their documentation gives, often ending in `/dav` or `/webdav` |

With Nextcloud, an **app password** (*Settings → Security → Devices & sessions*) is better than your real password: it
can be revoked on its own, and works with two-factor authentication on.

## Signing in

Usually a username and password, sent with every request (HTTP Basic). That's the `auth` above, and the card asks for
a username and password. A server that takes a token instead can use `"type": "BEARER"` or `"HEADER"` with an API key;
see [Sign-in](sign-in.md).

## What works

| | |
| --- | --- |
| **Browsing** | Folders and files, with sizes and last modified dates |
| **Changes** | Upload (into the open folder, or into `root_path` from the Upload screen), new folder, rename, move, delete |
| **Thumbnails** | Photos (the photo itself, scaled down on the phone), video frames and album covers, read from the files |
| **Previews** | Images, video and audio (streamed, with seeking), text |
| **Share links** | None: WebDAV has no public links |

The sign-in is only ever sent to addresses under `base_url`.
