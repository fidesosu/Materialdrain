# Sign-in

A config never holds a password, a key or a token. It only says **how** the host wants them sent; you type them in on
the host's card under **Settings → Advanced**, and they stay on your phone. That's what makes a config safe to share.

## What you're asked for

| Kind | The card asks for |
| --- | --- |
| WebDAV, REST API | Depends on `auth`, below: an API key, or a username and password (with an API key as a second way in) |
| S3 | An **Access Key ID** and a **Secret Access Key** |
| SMB | A **Username** and **Password**, unless the share is opened as a guest or anonymously (`"auth": "GUEST"` / `"ANONYMOUS"`) |

**Save & test** (or **Test connection**) tries the host right away, and shows what worked and what didn't, one line
per request it tried.

## `auth`

WebDAV and REST API configs describe their sign-in with an `auth` object. Every field is optional:

```json
"auth": {
  "type": "BEARER",
  "header_name": "Authorization",
  "key_username": "",
  "password_auth": null
}
```

| Field | Default | Meaning |
| --- | --- | --- |
| `type` | `NONE` | How the API key goes with each request; see below |
| `header_name` | `Authorization` | For `HEADER`: the header the key goes in |
| `key_username` | `""` | For `BASIC` with an API key: the username sent along with it |
| `password_auth` | none | For hosts that take a username and password; see [Username and password](#username-and-password) |

### `type`: sending an API key

| `type` | What's sent with each request |
| --- | --- |
| `NONE` | Nothing. A public host |
| `BASIC` | `Authorization: Basic …`, with the key as the password and `key_username` as the username (Pixeldrain's way) |
| `BEARER` | `Authorization: Bearer <key>` |
| `HEADER` | `<header_name>: <key>`, e.g. `"header_name": "X-Api-Key"` |

## Username and password

`password_auth` lets the card ask for a username and password. It works one of two ways:

=== "BASIC: sent every time"

    The username and password go with every request, as HTTP Basic. That's what WebDAV servers want.

    ```json
    "auth": { "type": "BASIC", "password_auth": { "mode": "BASIC" } }
    ```

    Since they're sent every time, both are kept (encrypted) on the phone. An API key, when one is also entered, is
    used while the username and password aren't both filled in.

=== "LOGIN: exchanged for a session"

    A sign-in request trades the username and password for a token, once; from then on the token is used like an API
    key, sent the way `type` says. Only the token is kept, never the password, like signing in to any other app.

    ```json
    "auth": {
      "type": "BASIC",
      "password_auth": {
        "mode": "LOGIN",
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
        },
        "logout": { "method": "DELETE", "path": "/user/session" }
      }
    }
    ```

    The card then shows **Username or e-mail**, **Password** and **Sign in**; once signed in, **Signed in as …** and
    **Sign out**. **Use an API key instead** is there for when you'd rather not sign in.

### The `login` request

| Field | Default | Meaning |
| --- | --- | --- |
| `method` | `POST` | The HTTP method |
| `path` | required | Added to `base_url` (or a full `https://` address) |
| `body` | `FORM` | How `fields` are sent: `FORM` or `JSON` |
| `fields` | `{"username": "{username}", "password": "{password}"}` | What's sent. `{username}`, `{password}` and `{otp}` are filled in |
| `token` | required | Where the token is in the answer, as a dot-path such as `$.auth_key` |
| `otp` | none | Two-factor codes; see below |

### Two-factor codes

With `otp`, a host that asks for a code gets one:

1. The app signs in without the code: a field whose value is exactly `{otp}` is left out.
2. If the answer matches `required_when` (the value at `path` equals `equals`), the app asks you for the code.
3. The same request is sent again, with the code in the `{otp}` field.
4. If the answer matches `rejected_when`, the code was wrong and you're asked again. Without `rejected_when`, a code
   that's still asked for after one was sent counts as wrong.

`digits` (default `6`) is how long the code is.

### Signing out

`logout` is an ordinary [endpoint](rest-api.md#a-request) that ends the session on the host. Without it, **Sign out**
just forgets the token on the phone.

## Where the sign-in is sent

- **Only to the host.** The sign-in goes with the host's own requests: those to `base_url` (or the S3 `endpoint`),
  and a REST API's endpoints written as full addresses. A thumbnail or a preview loaded from anywhere else (a CDN)
  gets nothing.
- **`"send_credentials": false`** (REST API) never sends it at all, for a public host that mustn't see it.
- **`"account_fallback": true`** (REST API) uses the Pixeldrain account from **Settings → Account** when the host has
  no sign-in of its own. It's meant for configs of pixeldrain.com, like the bundled one.
- An update to a config that would change where or how the sign-in is sent always asks you first; see
  [Sharing and updates](sharing-and-updates.md#updates-that-ask-first).

## How it's kept

Everything you enter is encrypted with a key held by Android's Keystore (AES-256-GCM), which never leaves the phone's
secure hardware. Removing a host deletes its sign-in too. More on this in
[Data and security](../development/data-and-security.md).
