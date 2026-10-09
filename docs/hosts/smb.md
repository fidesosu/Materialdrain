# SMB

For Windows shares, Samba, and the shares of a NAS (Synology, QNAP, TrueNAS, Unraid, …). SMB 2 and SMB 3 are built
into the app; the old and insecure SMB 1 is never used.

```json
{
  "kind": "smb",
  "name": "NAS share",
  "host": "nas.local",
  "share": "Documents"
}
```

## Fields

| Field | Default | Meaning |
| --- | --- | --- |
| `host` | required | The server's name or address, e.g. `nas.local` or `192.168.1.10` |
| `share` | required | The share to browse, by its name (`Documents`), not a path |
| `root_path` | `""` | The folder inside the share the app starts in, e.g. `Photos/2024` |
| `port` | `445` | The SMB port |
| `domain` | `""` | The Windows domain or workgroup of the account; empty for an account of the server itself |
| `auth` | `CREDENTIALS` | `CREDENTIALS`: a username and password, entered on the host's card. `GUEST` or `ANONYMOUS`: none |
| `min_version` | `SMB2` | The oldest version allowed: `SMB2` or `SMB3`. The newest the server offers is always used |
| `encrypt` | `false` | Encrypts the traffic. Needs SMB 3 on the server: without it, the connection fails rather than going unencrypted |
| `screens`, `meta` | | See [The config format](config-format.md) |

!!! note "On the same network"
    SMB is meant for local networks. The phone has to reach the server: be on the same Wi-Fi, or connected through a
    VPN (WireGuard, Tailscale, …). Don't open SMB to the internet.

## What works

| | |
| --- | --- |
| **Browsing** | Folders and files, with sizes and last modified dates |
| **Changes** | Upload, new folder, rename, move, delete. The top of the share itself can't be deleted |
| **Thumbnails** | Photos (HEIC too), a frame of each video and songs' album covers, made on the phone and kept in its cache |
| **Previews** | Images, video and audio (streamed, with seeking), text |
| **Share links** | None: an SMB share has no web address |

A share has no web address for its files, while the app's image viewer and players work from addresses. So the app
gives them one itself, from a small server on the phone that only the phone can reach. How that works is in
[Thumbnails and previews](../development/media.md#smb-files-by-address).
