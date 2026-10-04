# Provider configuration

Materialdrain is built for Pixeldrain, but a host can be added to it with a small JSON file, a **provider config**.
A config describes where a host lives, how it signs in, and which of its requests the app should use. The Files,
Lists and Filesystem screens are the same for every host. A config decides which of them appear and what each one
can do.

The shipped examples are in [`provider-configs/`](provider-configs/):

| File | Kind | What it is |
| --- | --- | --- |
| `pixeldrain.json` | `generic_rest` | Pixeldrain itself, described as a config (imported and activated on first start) |
| `nextcloud.json` | `webdav` | A Nextcloud account over WebDAV |
| `minio.json` | `s3` | A MinIO (or any S3-compatible) bucket |

## Adding a host

1. Open **Settings → Advanced**, then the **Add a custom host** section.
2. Paste the config JSON and press **Import host**. The host is saved and becomes the active one, which the Files,
   Lists and Filesystem screens then use.
3. Enter the host's sign-in (API key, or username and password) on its card in the same section. Credentials are
   never part of a config, so configs can be shared safely.

The app adds a first line `MATERIALDRAIN-PROVIDER-CONFIG-V1` when it exports a config. Importing accepts text with or
without it.

Removing a host just stops using it. The active host falls back to the built-in Pixeldrain, and the Pixeldrain
sign-in in **Account** keeps working on its own.

## The three kinds

Every config has a `kind`, which decides how the host is talked to:

- **`generic_rest`**: any HTTP API described by endpoints (method, path, body, which JSON fields mean what). Use it for
  Pixeldrain and for most REST services.
- **`webdav`**: a WebDAV server (Nextcloud, ownCloud, TrueNAS and others). The protocol is built into the app.
- **`s3`**: an S3-compatible bucket (MinIO, Backblaze B2, Cloudflare R2, Wasabi, AWS). The signing is built into the app.

Only `generic_rest` lets you describe a host in detail, so most of this guide is about that kind.

### Common fields

| Field | Required | Meaning |
| --- | --- | --- |
| `kind` | yes | `generic_rest`, `webdav` or `s3` |
| `name` | yes | The name shown in the app |
| `meta` | no | Identity, version and update information, see [Sharing and updates](#sharing-and-updates) |

### `webdav`

| Field | Default | Meaning |
| --- | --- | --- |
| `base_url` | required | The WebDAV address, for example `https://cloud.example.com/remote.php/dav/files/{username}` |
| `auth` | `BASIC` | How the sign-in is sent, see [Authentication](#authentication) |
| `root_path` | `/` | The folder the app starts in |

### `s3`

| Field | Default | Meaning |
| --- | --- | --- |
| `endpoint` | required | The server address, for example `https://nas.example.com:9000` |
| `region` | `us-east-1` | The region used for signing |
| `bucket` | required | The bucket to browse |
| `path_style` | `true` | Use `endpoint/bucket/key` addressing (needed by MinIO and most self-hosted servers) |
| `prefix` | `""` | Only show keys under this prefix |

The access key and secret key are entered in the app, like any other sign-in.

### `generic_rest`

| Field | Default | Meaning |
| --- | --- | --- |
| `base_url` | required | The API address, for example `https://pixeldrain.com/api`. Every endpoint path is added to it |
| `auth` | none | How the sign-in is sent, see [Authentication](#authentication) |
| `endpoints` | required | The requests the app can make, see [Endpoints](#endpoints) |
| `browse_root` | `""` | The folder the browser starts at. Pixeldrain uses `me` |
| `send_credentials` | `true` | Whether the sign-in is sent with the requests at all. Set `false` for a public host that must not see it |
| `account_fallback` | `false` | When the host has no sign-in of its own, use the one from **Account** (the Pixeldrain login) |

## Authentication

`auth` describes how the app's sign-in is attached to each request:

```json
"auth": {
  "type": "BASIC",
  "key_username": "",
  "password_auth": { "mode": "LOGIN", "login": { ... }, "logout": { ... } }
}
```

- `type`: `NONE`, `BASIC` (HTTP Basic; the API key is the password, with `key_username` as the user name),
  `BEARER` (`Authorization: Bearer <key>`) or `HEADER` (the key in the header named by `header_name`, default
  `Authorization`).
- `password_auth`: for hosts which take a username and password as well.
  - `mode: "BASIC"`: the username and password are sent with every request (HTTP Basic). Used by WebDAV Nextcloud.
  - `mode: "LOGIN"`: a sign-in request exchanges the username and password for a token, which is then used like an API
    key. The session is kept on the device; the password is not.

A `LOGIN` sign-in is described like this:

```json
"login": {
  "method": "POST",
  "body": "FORM",
  "path": "/user/login",
  "fields": { "username": "{username}", "password": "{password}", "totp": "{otp}" },
  "token": "$.auth_key",
  "otp": {
    "required_when": { "path": "$.value", "equals": "otp_required" },
    "rejected_when": { "path": "$.value", "equals": "otp_incorrect" }
  }
}
```

- `fields` is sent as `FORM` or `JSON` (`body`). `{username}`, `{password}` and `{otp}` are filled in.
- A field whose value is exactly `{otp}` is left out until the host asks for a two-factor code. When the response
  matches `required_when`, the app asks the user for the code and sends the same request again with it.
- `token` is the dot-path of the session token in the response.
- `logout` (optional) is an endpoint which ends the session on the host when the user signs out.

## Endpoints

`endpoints` maps a name to a request. The name decides what the app uses it for. Only the endpoints you declare
are used, so a host with only `list` and `download` just gets a flat file list and downloads.

```json
"list": {
  "method": "GET",
  "path": "/user/files",
  "list_path": "$.files",
  "response_map": { "id": "$.id", "name": "$.name", "size": "$.size", "created": "$.date_upload" }
}
```

### Request fields

| Field | Default | Meaning |
| --- | --- | --- |
| `method` | required | `GET`, `POST`, `PUT`, `DELETE`, ... |
| `path` | required | Added to `base_url`. May be a full URL (`https://...`) for hosts which serve files from another address |
| `body` | `RAW` | `RAW` (the file, or nothing), `MULTIPART` (a file upload), `FORM` (`form` fields), or `JSON` (`json` text) |
| `query` | none | Query parameters: `{ "name": "{placeholder}" }` |
| `form` | none | Form fields for a `FORM` body |
| `json` | none | The JSON text of a `JSON` body. Values that must be JSON are passed already encoded: `{title_json}`, `{files_json}` |
| `success_status` | `[200, 201]` | The HTTP status codes which count as success |

### Response fields

| Field | Meaning |
| --- | --- |
| `response_map` | Maps the app's field names to dot-paths in the JSON response, e.g. `"name": "$.file.name"` |
| `list_path` | For a listing: the dot-path of the array of items. Each item is mapped with `response_map` |
| `breadcrumb_path` | For a folder listing: the dot-path of the folder's own path segments |
| `listing_map` | Values of the listing as a whole, e.g. `"can_write": "$.permissions.write"` |

Dot-paths start with `$.` and use `.` between keys. Array indexes are not needed, because lists use `list_path`.

### Placeholders

Paths, query values, form fields and JSON bodies can contain `{placeholders}`, which the app fills in:

- In a `path`, slashes in a value are kept as path separators, so `{path}` can be `Photos/2024`.
- In `query` and `form`, a value that is exactly one placeholder with nothing filled in is left out. This makes
  optional flags like `make_parents` easy to drop.
- `*_json` placeholders (`title_json`, `files_json`) already contain valid JSON text, including the quotes.

### The endpoint names

Each name below unlocks a part of the app. The tab of a screen only appears when the host has what it needs.

| Endpoint | Placeholders | Used for | Tab or feature |
| --- | --- | --- | --- |
| `list` | none | Flat list of the files of the account | **Files** tab |
| `file_info` | `{id}` | Details of one file | File details |
| `download` | `{id}` | Downloading a file by id | Download (also used for zip) |
| `upload` | `{filename}` | Uploading a file | Upload tab |
| `delete` | `{id}` | Deleting a file by id | Delete |
| `user_info` | none | Account name and storage quota (`username`, `quota_used`, `quota_total`) | **Account** section |
| `browse_list` | `{path}` | Listing one folder (`name`, `is_directory`, `path`, ...) | **Filesystem** tab |
| `browse_download` | `{path}` | Downloading a file by path | Download in the Filesystem tab |
| `browse_upload` | `{path}`, `{make_parents}` | Uploading into a folder | Upload into the Filesystem tab |
| `browse_thumbnail` | `{path}` | Thumbnail of a file by path | Previews |
| `browse_mkdir` | `{path}`, `{action}` | Creating a folder (`action` is `mkdir` or `mkdirall`) | New folder |
| `browse_rename` | `{path}`, `{target}`, `{make_parents}` | Renaming or moving a node | Rename, Move |
| `browse_delete` | `{path}`, `{recursive}` | Deleting a file or folder by path | Delete in the Filesystem tab |
| `browse_import` | `{path}`, `{files_json}` | Copying files into a folder by their ids (Pixeldrain only) | Import files by ID |
| `user_lists` | none | The lists of the account (`id`, `title`, `file_count`, `can_edit`) | **Lists** tab |
| `list_info` | `{list_id}` | The files of one list | Opening a list |
| `list_create` | `{title_json}`, `{files_json}` | Creating a list | Create list |
| `list_update` | `{list_id}`, `{title_json}`, `{files_json}` | Changing a list's title or files | Edit list |
| `list_delete` | `{list_id}` | Deleting a list | Delete list |
| `thumbnail_id` | `{id}` | Thumbnail of a file by id | Previews and thumbnails |
| `raw_id` | `{id}` | The file's own address, for previews and sharing | Previews, share link |
| `share_id` | `{id}` | The public share page of a file | Share link |
| `share_path` | `{path}` | The public share page of a path | Share link |

Files without an id (path-based hosts) use the `browse_*` endpoints. Hosts which have both can declare both.

### Field names for `response_map`

The app reads these normalized names from a response:

| Name | Used for |
| --- | --- |
| `id` | The id of a file or list (the thing the `*_id` endpoints take) |
| `name` | The file name, or the title of a folder |
| `path` | The path of a node (`browse_list`, `browse_rename`) |
| `is_directory` | Whether a node is a folder. A value of `dir`, `directory`, `folder` or `true` counts as a folder |
| `size` | The size in bytes |
| `mime_type` | The file's type. Used for previews; when it's missing the app guesses it from the extension |
| `created`, `modified` | Dates, shown in the list and used for sorting |
| `username`, `quota_used`, `quota_total` | The Account section (`user_info`) |
| `title`, `file_count`, `can_edit` | Lists (`user_lists`, `list_info`) |

`listing_map` takes `can_write` and `can_delete` for folders, and `title` and `can_edit` for lists. When a flag isn't
mapped, the app assumes what the host's endpoints allow.

## Thumbnails, previews and links

- Thumbnails come from `thumbnail_id` (for files with an id) or `browse_thumbnail` (for paths).
- Previews of images, video and audio load from `raw_id` or `browse_download`.
- The share link copied and shared by the app is the `share_id` or `share_path` address when declared; otherwise it
  is the file's own address, without any `download` flag.
- The login is only sent to addresses under `base_url`. Thumbnails and previews from other addresses get none.

## Example: a minimal host

A host which can list and download files, and nothing else:

```json
{
  "kind": "generic_rest",
  "name": "My file server",
  "base_url": "https://files.example.com/api",
  "auth": { "type": "BEARER" },
  "endpoints": {
    "list": {
      "method": "GET",
      "path": "/files",
      "list_path": "$.items",
      "response_map": { "id": "$.id", "name": "$.filename", "size": "$.bytes", "created": "$.created_at" }
    },
    "download": { "method": "GET", "path": "/files/{id}/content" }
  },
  "meta": { "id": "com.example.fileserver", "version": 1, "author": "You" }
}
```

For the complete Pixeldrain config, see [`provider-configs/pixeldrain.json`](provider-configs/pixeldrain.json). It uses
almost every endpoint above.

## Sharing and updates

`meta` identifies a config and says how it's updated. None of it is secret.

- `id`: a stable name for the config, for example `com.example.fileserver`. Importing a config with the same `id` and
  `update_url` replaces the one already there and keeps its sign-in.
- `version`: a whole number. Only ever increase it when you publish a change.
- `update_url`: an `https` address of the raw config file. The app checks it at most once a day and can apply the
  newer version, if auto-update is on.
- `min_app_version`: the lowest app version which can use the config. Older apps report that instead of breaking.
- `author`: shown in the app.

## Checking a config

Before sharing a config, check that it parses and that the names are the ones listed above. A config which doesn't
parse (for example an unknown `kind` or a missing required field) is refused on import, and the error appears under
the text box.

When a request fails, the app writes log lines with the `MD/` prefix. They include the endpoint and a short part of
the response, but never the sign-in.
