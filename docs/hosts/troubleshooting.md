# Troubleshooting hosts

Start with **Save & test** (or **Test connection**) on the host's card. It tries the host and gives a line per check:
whether the address answers, whether the sign-in is taken, and what the host said when it isn't.

## What the test says

??? question "Address not found / Not answering / Refused the connection"
    The phone can't reach the host. Check the address in the config, that the phone is on the right network (a NAS
    at home isn't reachable from mobile data without a VPN), and the port. For SMB, check `host` and `port` (445).

??? question "Its certificate isn't trusted"
    The host uses `https` with a certificate Android doesn't trust, such as a self-signed one. The app only trusts
    the system's certificate authorities, so give the host a certificate from a public one: Let's Encrypt is free,
    and TrueNAS, Nextcloud and most NAS systems can get one for you.

??? question "Rejected (HTTP 401) or (HTTP 403)"
    The host doesn't take the sign-in. Check the key or the password, then how it's sent: `auth.type`, `header_name`,
    `key_username`. With Nextcloud and two-factor authentication on, use an app password.

??? question "Not checked: this config has no user_info endpoint to test them against"
    Nothing's wrong: a REST API config without `user_info` can't check a sign-in on its own. Open the host's tabs to
    see whether it works.

??? question "Your session has expired, sign in again"
    The token from a `LOGIN` sign-in no longer works. Sign in again on the card.

??? question "This device is offline"
    The phone has no network right now.

## When the config is the problem

??? question "The config is refused when it's added"
    The reason is under the text box, or at the top of the editor. Usually: it isn't valid JSON (a missing comma or
    quote), the `kind` is misspelled, or a required field (`name`, the address, `endpoints` for a REST API) is
    missing. **Review first** opens it in the editor, which shows each problem by its field.

??? question "A tab I expected is missing"
    A screen only shows when the host can back it: a REST API needs `list` for Files, `browse_list` for Filesystem,
    `user_lists` for Lists, and `upload` or `browse_upload` for Upload. Also check `screens`, if the config has it.
    See [Screens](config-format.md#screens).

??? question "The list is empty, but the request works"
    The request worked but the app didn't find the files in the answer: check `list_path` (where the array is) and
    the `response_map` paths. Each path starts with `$.` and is relative to one item for a list.

??? question "Folders show up as files"
    Map `is_directory` in `browse_list`'s `response_map`. A value of `true`, `dir`, `directory` or `folder` counts as
    a folder.

??? question "No thumbnails"
    REST API: add `thumbnail_id` or `browse_thumbnail`. Photos and videos also need `raw_id` or `browse_download`
    for previews. WebDAV, S3 and SMB read thumbnails from the files themselves.

## Reading the log

When a request fails, the app writes what happened to Android's log, tagged `MD/` and a category (`MD/Http`,
`MD/Auth`, `MD/Config`, `MD/Files`, `MD/Filesystem`, …). The lines name the endpoint and the start of the answer, but
never a key, password or token.

With the phone connected to a computer with USB debugging on:

```sh
adb logcat | grep "MD/"
```

That's also the most useful thing to include when [reporting a problem](https://github.com/fidesosu/Materialdrain/issues).
