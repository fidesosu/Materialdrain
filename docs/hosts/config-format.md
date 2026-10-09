# The config format

A config is a JSON object. Here is a whole one, for an SMB share:

```json
{
  "kind": "smb",
  "name": "NAS share",
  "host": "nas.local",
  "share": "Documents",
  "screens": ["UPLOAD", "FILESYSTEM"],
  "meta": { "id": "com.example.nas-documents", "version": 1, "author": "You" }
}
```

Every config has these fields; the rest depend on its `kind`, see the page of each kind.

| Field | Required | Meaning |
| --- | --- | --- |
| `kind` | yes | `webdav`, `s3`, `smb` or `generic_rest` (a REST API) |
| `name` | yes | The name the app shows, in the host switcher and the settings |
| `screens` | no | Which screens the host shows, and in what order. Left out: every screen the host can back. See [Screens](#screens) |
| `meta` | no | Who made the config and where newer versions come from. See [Sharing and updates](sharing-and-updates.md) |

## How the app reads a config

- **Plain JSON.** A config can be a `.json` file that any editor or tool opens as it is.
- **Fields the app doesn't know are kept.** A note you added, a field for a newer version of the app, a field left at
  its default: the app keeps the config exactly as it was written, and only reads what it needs from it. **Export** and
  **Edit** show it as you wrote it.
- **Left out means the default.** Every field except the required ones has a default, given on each kind's page. Only
  write what's different.
- **Names are case-sensitive**, except screen names (`"filesystem"` works as well as `"FILESYSTEM"`).
- A config the app can't read (not JSON, an unknown `kind`, a required field missing) is refused when it's added, with
  the reason under the text box or in the editor.

## Screens

The Upload, Files, Lists and Filesystem screens are the same for every host. `screens` picks which of them a host shows,
and in what order (the tabs follow it).

- **Leave `screens` out** to show every screen the host can back. This is what most configs want.
- **List the ones to show**, to pick and order them yourself:

    ```json
    "screens": ["FILESYSTEM", "UPLOAD"]
    ```

- **`"screens": []`** shows none: the app then says the host has no screens to show.

A screen the host can't back is left out rather than shown broken, and one listed twice counts once, where it's first
listed. When only one screen is left, the tabs are hidden and it's simply shown.

| Screen | What it is | A REST API needs | WebDAV, S3, SMB |
| --- | --- | --- | --- |
| `UPLOAD` | Picking files to upload, with no folder of their own | an `upload` (or `browse_upload`) endpoint | yes, into the top folder (`root_path` / `prefix`) |
| `FILES` | A flat, sortable list of every file of the account | a `list` endpoint | no |
| `LISTS` | Pixeldrain-style lists of files | a `user_lists` endpoint | no |
| `FILESYSTEM` | The folder browser, with an upload of its own into the open folder | a `browse_list` endpoint | yes |

### Naming a screen, or taking actions away from it

A screen can also be written as an object:

| Field | Default | Meaning |
| --- | --- | --- |
| `screen` | required | `UPLOAD`, `FILES`, `LISTS` or `FILESYSTEM` |
| `name` | the screen's own | The tab's label and the title, e.g. `"Media"` for a Filesystem screen that's really one share |
| `disabled_capabilities` | `[]` | Actions this screen doesn't offer, even though the host can do them |

```json
"screens": [
  "UPLOAD",
  { "screen": "FILESYSTEM", "name": "Media", "disabled_capabilities": ["MKDIR", "DELETE"] }
]
```

`disabled_capabilities` only takes away; it can't add what the host doesn't support. The app acts on `UPLOAD` (the
upload buttons), `MKDIR` (new folder), `RENAME` (rename and move) and `DELETE`.

!!! note "Pixeldrain itself"
    The built-in Pixeldrain host, the one you sign into in **Account**, isn't a config, so `screens` doesn't apply to
    it: it always shows everything it can do.
