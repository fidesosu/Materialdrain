# Example configs

These configs ship with the app's source, in
[`docs/provider-configs`](https://github.com/fidesosu/Materialdrain/tree/dev/docs/provider-configs). Copy one,
change the address, paste it under **Settings → Advanced → Or paste a config**, and enter the sign-in on its card.

| File | Kind | What it is |
| --- | --- | --- |
| [`pixeldrain.json`](https://github.com/fidesosu/Materialdrain/blob/dev/docs/provider-configs/pixeldrain.json) | `generic_rest` | Pixeldrain, described as a config. Uses nearly every endpoint there is; added and made the active host on the first start |
| [`nextcloud.json`](https://github.com/fidesosu/Materialdrain/blob/dev/docs/provider-configs/nextcloud.json) | `webdav` | A Nextcloud account |
| [`truenas-webdav.json`](https://github.com/fidesosu/Materialdrain/blob/dev/docs/provider-configs/truenas-webdav.json) | `webdav` | A TrueNAS WebDAV share |
| [`minio.json`](https://github.com/fidesosu/Materialdrain/blob/dev/docs/provider-configs/minio.json) | `s3` | A MinIO bucket, or any S3-compatible one |
| [`truenas-s3.json`](https://github.com/fidesosu/Materialdrain/blob/dev/docs/provider-configs/truenas-s3.json) | `s3` | A TrueNAS S3 bucket |
| [`smb.json`](https://github.com/fidesosu/Materialdrain/blob/dev/docs/provider-configs/smb.json) | `smb` | A Windows, Samba or NAS share |

The quickest start is still **New host** in the app, which opens the editor on a complete config of the kind you pick;
see [The config editor](editor.md#starting-a-new-host).

## Nextcloud

```json
{
  "kind": "webdav",
  "name": "Nextcloud",
  "base_url": "https://cloud.example.com/remote.php/dav/files/{username}",
  "auth": { "type": "BASIC", "password_auth": { "mode": "BASIC" } },
  "screens": ["UPLOAD", "FILESYSTEM"]
}
```

Sign in with your username and an app password.

## A MinIO bucket

```json
{
  "kind": "s3",
  "name": "MinIO",
  "endpoint": "https://nas.example.com:9000",
  "bucket": "materialdrain",
  "screens": ["UPLOAD", "FILESYSTEM"]
}
```

Enter an access key pair on the card. `region` (`us-east-1`) and `path_style` (`true`) are left at their defaults.

## An SMB share, only one folder of it

```json
{
  "kind": "smb",
  "name": "Photos",
  "host": "192.168.1.10",
  "share": "Media",
  "root_path": "Photos",
  "screens": [{ "screen": "FILESYSTEM", "name": "Photos" }]
}
```

Only the Filesystem screen, so there are no tabs: the app opens straight on the folder.

## A read-only view

Any kind can drop actions from a screen. A share you can browse and download from, but not change:

```json
"screens": [
  { "screen": "FILESYSTEM", "disabled_capabilities": ["UPLOAD", "MKDIR", "RENAME", "DELETE"] }
]
```

## A small REST API

A host that lists and downloads files, and nothing else:

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
    "download": { "method": "GET", "path": "/files/{id}/content" },
    "raw_id": { "method": "GET", "path": "/files/{id}/content" }
  },
  "meta": { "id": "com.example.fileserver", "version": 1, "author": "You" }
}
```

[REST API](rest-api.md) walks through it step by step.
