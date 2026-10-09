# Data and security

Materialdrain keeps very little: your settings, your host configs, and the sign-ins you typed in. This page says
where each of them lives, how the secrets are protected, and the rules that keep a sign-in from going somewhere it
shouldn't. For each rule, it also says why.

Paths below are under `app/src/main/java/tools/senko/materialdrain/` unless they name a `provider-*` module.

## What is stored where

Everything is in plain `SharedPreferences` files, private to the app (`Context.MODE_PRIVATE`). There's no database and
no DataStore.

| File | Written by | Holds | Encrypted |
| --- | --- | --- | --- |
| `pixeldrain_prefs` | `AppSettings`, `SessionManager` | The app's settings: sorting, the last screen and folder, the open file and list, the toggles in Settings | No |
| `pixeldrain_secure_prefs` | `SessionManager` | The Pixeldrain login session's key and username, and the manually entered API key | The keys, yes |
| `provider_prefs` | `ProviderConfigStore` | Every host config with its update state, and which host is active | No (configs hold no secrets) |
| `provider_secure_prefs` | `ProviderConfigStore` | The sign-in of each host: API key, username, password, session token | Yes, every value |

The file names start with "pixeldrain" because the app began as a Pixeldrain client. They're kept so existing
installs keep their data.

Caches (thumbnails, the media cache) are in the app's cache folder; see
[Thumbnails and previews](media.md#caching). Downloads go to the phone's Download folder; see
[Uploads and downloads](transfers.md#where-files-go).

### Settings

`settings/AppSettings.kt` reads each setting once into a `MutableStateFlow` and writes it back with `prefs.edit { }`
when it changes. The screens collect the flows, so a change shows everywhere at once. Nothing in it is secret.

### Host configs

`provider/ProviderConfigStore.kt` keeps one JSON object under the key `configs`, mapping each host's id (a random
UUID) to a record:

| Record field | What it is |
| --- | --- |
| `config` | The config **text exactly as it was written** |
| `upstream` | The last version that came from the update URL. Comparing it with the config shows whether you edited it |
| `previous` | The text before the last update, for **Revert** |
| `pending_update` | A newer version a check found but that isn't applied yet |
| `auto_update`, `keep_auto_update_when_edited` | The auto-update switch, and whether an edit should leave it on |
| `etag`, `last_checked` | For the next update check |

The active host's id is under `active_provider_id`. The built-in Pixeldrain host (`pixeldrain-default`) is never
stored here; it always exists.

**Why the text, and not the parsed config?** A config can have fields this version of the app doesn't read: a note, a
field left at its default, a field for a newer version. Writing the parsed config back would drop them. Keeping the
text means **Export** and **Edit** show the config exactly as its author wrote it. The parsed form (`ConfigSource`)
is made from the text when it loads. See [The config format](../hosts/config-format.md#how-the-app-reads-a-config).

### Sign-ins

A config never holds a secret. The sign-in of each host is stored separately in `provider_secure_prefs`, one
encrypted entry per value, named `<kind>_<host id>`:

| Kind | Holds |
| --- | --- |
| `secret` | The API key (the name is older than usernames and passwords, and kept so saved keys still load) |
| `username` | A username, or an S3 Access Key ID |
| `password` | A password, or an S3 Secret Access Key |
| `login_token` | The session token from a sign-in (`password_auth` with `"mode": "LOGIN"`) |

A successful sign-in (`saveSignIn`) saves the username and the token, and **deletes** the password. Like any app's
normal sign-in, only the session is kept. A password is only stored for hosts that send it with every request: a
config with `"mode": "BASIC"` (WebDAV, usually), S3 and SMB.

Removing a host (`remove`) deletes its sign-in too. Decrypted values are cached in memory (`credentialCache`) and the
cache is cleared on every change. Without it, every visible thumbnail would decrypt them again.

**Why keep secrets apart from configs?** So a config can be exported, shared and updated without any risk of leaking
a password, and so an update can never replace or read a sign-in.

### The Pixeldrain session

`auth/SessionManager.kt` is the one place the Pixeldrain API key comes from. There are two:

- **The login session** (`session_api_key_encrypted`), from signing in with a username and password. It wins when
  both are there.
- **A manually entered key** (`manual_api_key_encrypted`). Older versions stored it in plain text in
  `pixeldrain_prefs`; `manualApiKey()` moves such a key into the encrypted entry once and removes the plain copy.

`currentApiKey()` returns the session's key, or else the manual one. Both are decrypted once and then kept in memory,
since every request asks for them. Signing out (`clearSession`) forgets the session on the phone.

## Encryption

`auth/KeystoreCipher.kt` encrypts every stored secret with **AES-256-GCM**, using a key that lives in the **Android
Keystore**:

```kotlin
KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
    .setKeySize(256)
    .build()
```

- The key is created the first time it's needed and never leaves the Keystore. The app can ask the Keystore to
  encrypt and decrypt, but can't read the key itself. On phones with secure hardware, the key is kept there.
- Each value gets a fresh random IV. It's stored as `base64(iv):base64(ciphertext)`.
- GCM also checks integrity: a changed value fails to decrypt instead of decrypting to something else.
- There are two keys: `materialdrain_session_key` for `SessionManager` and `materialdrain_provider_secrets_key` for
  `ProviderConfigStore`.

`decrypt` returns `null` instead of throwing when a value can't be decrypted, for example after the key was lost. The
callers then treat the value as not set: `SessionManager` discards an unreadable session, and
`ProviderConfigStore.credentials` returns blank values. You're asked to sign in again; the app doesn't crash.

## Backups

The manifest allows Android's backup (`android:allowBackup="true"`), but `res/xml/backup_rules.xml` (Android 11 and
older) and `res/xml/data_extraction_rules.xml` (Android 12 and later, for both cloud backup and device-to-device
transfer) exclude all four files:

```xml
<!-- The login and the provider credentials are encrypted with a Keystore key which is not part of a backup -->
<exclude domain="sharedpref" path="pixeldrain_secure_prefs.xml" />
<exclude domain="sharedpref" path="provider_secure_prefs.xml" />
<!-- The plain copies of the settings which hold the API key and the provider config state are not backed up -->
<exclude domain="sharedpref" path="pixeldrain_prefs.xml" />
<exclude domain="sharedpref" path="provider_prefs.xml" />
```

Why each one:

- **The two secure files** would be useless on another phone. The Keystore key isn't part of a backup, so the restored
  values couldn't be decrypted. Leaving them out is clearer than restoring values that silently fail.
- **`pixeldrain_prefs`** is where older versions kept the API key in plain text. It must not end up in a cloud backup.
- **`provider_prefs`** holds the hosts. Restored without their sign-ins, they would only half work, and their update
  state (`etag`, the pending update) belongs to this install.

In practice, a backup restores nothing of the app's own data. After moving to a new phone, export your configs from
the old one and import them again.

## App lock

**Settings → Security → Lock with fingerprint or face** (`AppSettings.biometricLock`, off by default) puts the app
behind Android's `BiometricPrompt`. It accepts a strong biometric or the device's screen lock
(`BIOMETRIC_STRONG or DEVICE_CREDENTIAL`), so it works on phones without a fingerprint reader. The switch only turns
on when the device has one of them set up (`biometricLockAvailable()`).

`auth/AppLock.kt` decides when to lock. `MainActivity` calls `onStarted()` and `onStopped()`:

```kotlin
fun onStarted() {
    val wasAway = stoppedAt?.let { SystemClock.elapsedRealtime() - it > LOCK_AFTER_BACKGROUND_MS } ?: true
    if (settings.biometricLock.value && wasAway) _locked.value = true
    if (!settings.biometricLock.value) _locked.value = false
}
```

- It locks when the app is opened, and again when it comes back after more than **30 seconds** in the background
  (`LOCK_AFTER_BACKGROUND_MS`). A quick switch to another app and back doesn't ask again; that would be tiring for no
  gain.
- It uses `elapsedRealtime`, which can't be changed by setting the clock.
- `AppLockOverlay` covers the whole app while it's locked and takes every touch, so nothing underneath can be used
  until the prompt succeeds.
- A file shared from another app waits in `pendingShares` until the app is unlocked. Nothing uploads while it's locked
  (see [Uploads and downloads](transfers.md#files-shared-from-another-app)).

Transfers keep running while the app is locked. The lock protects what's on the screen, not the work in progress.

## Credentials only go to their host

A sign-in must never reach a site it doesn't belong to. These rules make sure of that.

**Media loads.** Thumbnails, previews and players load URLs that can come from anywhere: a host's API can return a
thumbnail on a CDN, for example. They all get their headers from `StorageProvider.requestHeaders(url)` (through
`HostRequestAuth`, see [Thumbnails and previews](media.md#the-login-for-media)), and each host only answers for its own
addresses:

| Host | `requestHeaders` sends the login for |
| --- | --- |
| REST API | URLs that start with the config's `base_url` |
| WebDAV | URLs that start with the share's base URL (with `{username}` filled in) |
| Pixeldrain | URLs under `https://pixeldrain.com/api/filesystem/`, as the `pd_auth_key` cookie |
| S3, SMB | Nothing: S3 file URLs are presigned, and SMB files are served by the app itself |

```kotlin
override fun requestHeaders(url: String): Map<String, String> {
    if (!url.startsWith(config.baseUrl)) return emptyMap()
    val header = resolveAuthPlan().header ?: return emptyMap()
    return mapOf(header.first to header.second)
}
```

**API requests.** A REST API config's endpoints are relative paths under `base_url`, or full `https://` addresses its
author wrote. Both get the sign-in: the config chose them. That's why an update that adds a new address is treated as
sensitive (next section).

**`send_credentials: false`.** A REST API config can turn the sign-in off completely. `credentialsPlan` then returns
`AuthPlan.None`, for every request and every media load.

## Updates can't redirect a sign-in

A config with `meta.update_url` can update itself. The rule is simple: **an update may change what requests look
like, but never where or how the sign-in is sent without asking you first.** See also
[Sharing and updates](../hosts/sharing-and-updates.md#updates-that-ask-first).

### Sensitive changes

`ConfigUpdates.sensitiveChanges(old, new)` in `provider-api` lists everything that would change where credentials go:

| `SensitiveChange.Kind` | When |
| --- | --- |
| `DESTINATION` | A request would go to a scheme, host or port it didn't go to before. It compares the origins of `base_url` (or the S3 `endpoint`, or `smb://host:port`), of every absolute endpoint path, and of the login and logout paths |
| `AUTH` | The `auth` type changed, the header name of `HEADER` changed, `password_auth` changed, or anything else in `auth` |
| `UPDATE_SOURCE` | Future updates would come from a new update URL. Removing the URL only stops updates, so that's fine |
| `KIND` | The config becomes a different kind of host |

An address the code can't parse is kept whole rather than dropped, so any change to it still counts as a new
destination.

`ConfigUpdates.evaluate` also refuses an update whose `meta.id` differs from the installed one ("The update URL now
serves a different config"), and treats a remote copy with the same or a lower `version` as up to date. Versions are
the contract: a config's author has to raise the version to publish an update.

`ConfigUpdatesTest` in `provider-api` covers these rules.

### Checking and applying

`provider/ProviderUpdater.kt` fetches and applies updates:

- **HTTPS only.** An `update_url` that doesn't start with `https://` is refused. A config that updates over plain
  HTTP could be swapped on the way.
- **At most 256 KB.** `readLimited` asks for 256 KB + 1 bytes; if they arrive, the file is too big and is refused. No
  config is that large, and a bad server can't make the app read without end.
- **20 seconds.** The whole call times out after 20 seconds.
- **ETag.** The last `ETag` is sent as `If-None-Match`, so an unchanged file costs a `304` and no download.
- **Auto-update** runs once when the app starts (`AppContainer` launches `runAutoUpdates`), at most once a day per
  config. There's no background job. It only applies updates **without** sensitive changes; the rest wait for you.
- **A new destination clears the sign-in.** When you accept an update with a `DESTINATION` or `KIND` change,
  `applyPending` passes `clearSecret = true`, and every saved credential of that host is deleted. You type it in again
  for the new place, so it's never sent there without you knowing.

```kotlin
val clearSecret = ConfigUpdates.sensitiveChanges(stored.config, pending.config).any {
    it.kind == SensitiveChange.Kind.DESTINATION || it.kind == SensitiveChange.Kind.KIND
}
store.applyUpdate(id, pending, clearSecret)
```

Two more safeguards in `ProviderConfigStore`:

- **Editing turns auto-update off, once.** If your edit makes the config differ from `upstream`, auto-update is
  switched off, so the next check doesn't overwrite your change. If you turn it back on, later edits leave it on.
- **Revert turns auto-update off**, so the next check doesn't re-apply what you just reverted.

## Logging

The app's diagnostic log is `ProviderLog` in `provider-api`. Every line is tagged `MD/<category>` (`Files`, `Http`,
`Auth`, `Config`, …), so `adb logcat | grep "MD/"` shows just these.

**A credential is never logged.** Code logs what kind of credential is used, or its length, never its value:

```kotlin
ProviderLog.d("Auth", "${config.name}: ${auth.header?.first ?: "no header"}, ${if (auth.fromSession) "session token" else "credentials"}")
```

That logs the header's *name* (`Authorization`), not its value. Other examples: `saveApiKey` logs "saved the API key
of host …", and the Upload screen logs whether the key is "Present" or "Missing". `ProviderLog.snippet` cuts a
response body to 300 characters for an error report: enough to read the server's message, not a whole page.
`KeystoreCipher` only logs the class name of a decryption error.

**Why so careful?** Logs end up in bug reports, in `adb` output pasted into an issue, and in other tools that read
logcat. A token there is as good as a password.

## Network security

`AndroidManifest.xml` points to `res/xml/network_security_config.xml`. The app targets Android 16 (SDK 36), where
Android blocks plain `http://` by default. The config only adds one exception:

```xml
<domain-config cleartextTrafficPermitted="true">
    <domain includeSubdomains="false">127.0.0.1</domain>
</domain-config>
```

- **Everything goes over HTTPS.** A host with an `http://` address can't be reached; Android refuses the connection
  before anything is sent. A sign-in can't travel unencrypted by mistake.
- **127.0.0.1 is the one exception**, for the app's own server that hands SMB files to the image loader and the
  players. That traffic never leaves the phone. See [SMB files by address](media.md#smb-files-by-address).
- SMB itself doesn't use HTTP. With `"encrypt": true`, its traffic is encrypted with SMB 3 (see
  [SMB](../hosts/smb.md)).

## See also

- [Sign-in](../hosts/sign-in.md) for how a config describes its sign-in.
- [Sharing and updates](../hosts/sharing-and-updates.md) for `meta` and the update flow from the user's side.
- [Architecture](architecture.md) for `AppContainer`, which creates the stores.
