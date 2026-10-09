# Hosts

Besides Pixeldrain, which is built in, Materialdrain can use any number of other hosts. Each is described by a
**provider config**: a short JSON file that says what kind of host it is, where it is, and how to talk to it. The app's
screens are the same for every host; the config decides which of them appear and what each can do.

!!! tip "No passwords in configs"
    A config never holds a password, key or token. Those are entered in the app, on the host's card, and kept
    encrypted on the phone. So a config can be shared freely: post it, put it on GitHub, send it to a friend.

## The kinds of host

| Kind | For | Screens it can have |
| --- | --- | --- |
| [`webdav`](webdav.md) | Nextcloud, ownCloud, TrueNAS, and any WebDAV server | Upload, Filesystem |
| [`s3`](s3.md) | MinIO, Backblaze B2, Cloudflare R2, Wasabi, AWS S3, and any S3-compatible bucket | Upload, Filesystem |
| [`smb`](smb.md) | Windows shares, Samba, and NAS shares (SMB 2 and 3) | Upload, Filesystem |
| [`generic_rest`](rest-api.md) (a REST API) | Any HTTP API, described request by request. Pixeldrain itself can be one | Upload, Files, Lists, Filesystem |

WebDAV, S3 and SMB speak a standard protocol, which is built into the app: a config only says where the host is. A
REST API has no standard, so its config also describes each request, and that's where most of this section's detail is.

## Adding a host, step by step

1. Open **Settings → Advanced**, and scroll to **Add a custom host**.
2. Under **New host**, press the kind you want: **WebDAV**, **S3**, **SMB** or **REST API (from Pixeldrain)**.
3. The [config editor](editor.md) opens on a complete config of that kind. The usual values are filled in; change the
   address and anything else that's different for you. Every field says what it's for.
4. Press **Add host**. The host is saved and becomes the one the screens use.
5. On the host's card (in the list above), enter your sign-in: a username and password, or an API key, or for S3 an
   access key and secret.
6. Press **Test connection** on the card to check that it all works.

=== "Someone gave you a config"

    Paste it into **Or paste a config**, then press **Add host**, or **Review first** to see it in the editor first.

=== "You're writing one"

    Start from the matching button in step 2, or from an [example](examples.md), then read the page of its kind:
    [WebDAV](webdav.md), [S3](s3.md), [SMB](smb.md) or [REST API](rest-api.md).

## A host's card

Each host in **Settings → Advanced** has a card with:

- its **sign-in**: the fields depend on the kind and the config ([Sign-in](sign-in.md));
- **Test connection**: checks the address and the sign-in, field by field;
- **Edit** (the pencil): opens the config in the editor; **Export** copies it, to share it or keep a copy;
- for a config with an update address: **Check for updates**, auto-update, and **Revert update**
  ([Sharing and updates](sharing-and-updates.md));
- **Remove**: stops using the host. Pixeldrain's own sign-in, in **Account**, keeps working on its own.

## Switching hosts

The **host switcher** at the top of the Upload, Files, Lists and Filesystem screens shows the host in use, with a dot
for whether it answers. Tap it for every host, each with its address, whether it answers (and how fast), and whether
you're signed in; tap one to use it. The hosts are checked ahead of time (when the app starts, every few minutes while
it's open, and when the network changes), so the switcher already knows when you open it.
