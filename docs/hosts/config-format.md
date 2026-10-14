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

A screen the host can't back is left out rather than shown broken. Two tabs with the same id (see
[Several tabs of one kind](#several-tabs-of-one-kind)) count once, where the first of them is listed. When only one
screen is left, the tabs are hidden and it's simply shown.

| Screen | What it is | A REST API needs | WebDAV, S3, SMB |
| --- | --- | --- | --- |
| `UPLOAD` | Picking files to upload, with no folder of their own | an `upload` (or `browse_upload`) endpoint | yes, into the top folder (`root_path` / `prefix`) |
| `FILES` | A flat, sortable list of every file of the account | a `list` endpoint | no |
| `LISTS` | Pixeldrain-style lists of files | a `user_lists` endpoint | no |
| `FILESYSTEM` | The folder browser, with an upload of its own into the open folder | a `browse_list` endpoint | yes |

### Naming a screen, giving it a root, or taking actions away from it

A screen can also be written as an object:

| Field | Default | Meaning |
| --- | --- | --- |
| `screen` | required | `UPLOAD`, `FILES`, `LISTS` or `FILESYSTEM` |
| `id` | the kind in lower case (`"filesystem"`) | Tells the tab apart from others of its kind; needed for a second one. See [Several tabs of one kind](#several-tabs-of-one-kind) |
| `name` | the screen's own | The tab's label and the title, e.g. `"Media"` for a Filesystem screen that's really one share |
| `root` | the whole host | A Filesystem tab's folder, under the host's top folder (`root_path`, `prefix`, `browse_root`): it opens there and can't go above it |
| `disabled_capabilities` | `[]` | Actions this screen doesn't offer, even though the host can do them |

```json
"screens": [
  "UPLOAD",
  { "screen": "FILESYSTEM", "name": "Media", "disabled_capabilities": ["MKDIR", "DELETE"] }
]
```

`disabled_capabilities` only takes away; it can't add what the host doesn't support. The app acts on `UPLOAD` (the
upload buttons), `MKDIR` (new folder), `RENAME` (rename and move), `DELETE` and `ARCHIVE_BROWSE` (looking inside
archives). A file's details follow the tab they were opened from: opened from a tab without `DELETE`, they have no
Delete either.

### Several tabs of one kind

A kind can be shown more than once, each tab with an `id` of its own. This is mostly for the filesystem: two tabs on
one NAS, each opening at its own folder, with its own name and actions:

```json
"screens": [
  "UPLOAD",
  { "screen": "FILESYSTEM", "id": "media", "name": "Media", "root": "Media" },
  { "screen": "FILESYSTEM", "id": "backups", "name": "Backups", "root": "Backups", "disabled_capabilities": ["DELETE"] }
]
```

- Each tab keeps its own open folder and scroll position, and opens at the folder it was left at after a restart. That
  goes by the tab's id, so the tabs can be put in another order without losing it.
- The tab's breadcrumbs, going up, and the folder picker for moving all stop at its `root`. A folder saved from before
  the root was set is replaced by the root.
- Each Filesystem tab uploads into its own open folder. **Add to filesystem** on the Files tab picks a folder in the
  host's first Filesystem tab.
- A second tab without its own `id` gets the same id as the first, so it doesn't show. The editor marks it, and adding
  a host says so too. **Another Filesystem tab**, in a tab's ⋮ menu in the editor, fills in a free id (`filesystem-2`, …).
- A `root` on a tab that isn't a Filesystem tab does nothing; the editor marks that as well.

!!! note "Pixeldrain itself"
    The built-in Pixeldrain host, the one you sign into in **Account**, isn't a config, so `screens` doesn't apply to
    it: it always shows everything it can do.
