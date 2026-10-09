# Thumbnails and previews

The app shows pictures in three places: the small thumbnail in a file's row, the preview on the details page, and the
fullscreen view. Everything that shows a file works from a **URL**: Coil (the image loader) and ExoPlayer (the video
and audio player) both take one. This page explains where those URLs come from, how the login reaches them, and what
the app reads out of files itself.

Paths below are under `app/src/main/java/tools/senko/materialdrain/` unless they name a `provider-*` module.

## The image loader

The app uses **Coil 2.7** (`io.coil-kt:coil-compose` and `coil-gif`). There's one `ImageLoader` for the whole
process, set up in `AppContainer`'s `init` with `Coil.setImageLoader`. Every `AsyncImage` and `context.imageLoader`
uses it.

```kotlin
coil.Coil.setImageLoader {
    coil.ImageLoader.Builder(application)
        .components {
            add(tools.senko.materialdrain.ui.media.MediaCovers.Factory(application))
            add(tools.senko.materialdrain.ui.media.AnimatedImages.Factory())
        }
        .okHttpClient { okHttpClient.newBuilder().readTimeout(30, java.util.concurrent.TimeUnit.SECONDS).build() }
        .build()
}
```

| Part | What it adds |
| --- | --- |
| `MediaCovers.Factory` | A `Fetcher` for `audiocover://` URLs: a song's cover or a video's frame, read from the file. See [Covers and frames](#covers-and-frames-from-the-file) |
| `AnimatedImages.Factory` | A `Decoder` for GIFs and animated WebP, so they move wherever they're shown |
| `okHttpClient` | The API's own client, so thumbnails reuse the HTTP/2 connection already open to the host. It gets a 30-second read timeout of its own: the API client has none (for long downloads), and a stalled thumbnail should give up |

### Animated images

`AnimatedImages` recognizes an animated file by its first bytes (`GIF87a`/`GIF89a`, or a WebP with a `VP8X` header
whose animation flag is set), not by the type the host reports. Hosts often report none, or a generic one.

Android's `ImageDecoder` handles nearly all of them, but turns down some slightly broken GIFs that browsers still
play. So, for files up to 64 MB, the decoder tries in turn: `ImageDecoderDecoder`, then Coil's older `GifDecoder` (for
GIFs), then `BitmapFactoryDecoder`, which shows the first frame as a still picture. Bigger files go to `ImageDecoder`
alone, which streams them instead of reading them whole first.

### The login for media

A private file's URL only works with the host's login. Coil and ExoPlayer don't know about hosts, so the app adds the
headers itself. `HostRequestAuth` (in `ui/media/MediaPlayerSupport.kt`) holds one function, which `AppContainer`
points at the active host:

```kotlin
HostRequestAuth.headersFor = { url ->
    providerRegistry.resolve(providerConfigStore.activeProviderId.value).requestHeaders(url)
}
```

Every place that loads a file asks it: `imageRequest` in `ImagePreview.kt`, the thumbnail in `FileListItem`,
`createMediaPlayer` for ExoPlayer, and `MediaCovers` for the retriever. Each host's `requestHeaders(url)` only answers
for its own private URLs and returns nothing for any other address, so a login never goes to a site it doesn't belong
to. See [Data and security](data-and-security.md#credentials-only-go-to-their-host).

S3 is the exception: it needs no headers, because its file URLs are presigned (the signature is in the URL itself).

## Thumbnails

### Which thumbnail a file gets

The file rows (`browser/BrowserScreen.kt`), the search results and the details page all ask
`FileInfoViewModel.thumbnailFor(node)` which picture to show. The rules, in order:

1. **SMB**: whatever the host gives. An SMB share makes real thumbnails itself, on the device. See
   [SMB files by address](#smb-files-by-address).
2. **Audio**: an `audiocover://cover` URL for the file's own album cover. A song without one keeps the app's music
   tile, not the host's generic picture.
3. **Video**: an `audiocover://frame` URL for a frame read from the file. The host's thumbnail is the fallback, for a
   video the device can't read either.
4. **HEIC, HEIF and AVIF photos**: the file itself (`rawContentUrl`). Hosts' thumbnailers often can't read these
   formats, so the phone decodes the file and Coil scales it down to the row's size.
5. **Everything else**: the host's thumbnail, but **only for images and videos**. For text, archives, apps and
   unknown kinds, hosts hand out a generic picture that says nothing. The app's own tile, coloured for the kind of
   file, says more.

```kotlin
// Only pictures and videos have a thumbnail worth showing: for everything else (text, archives, apps, unknown
// kinds) the hosts hand out a generic picture, and the app's own tile for the kind of file is used instead
val mime = node.previewMimeType().orEmpty()
return thumbnail.takeIf { mime.startsWith("image/") || mime.startsWith("video/") }
```

`previewMimeType()` uses the type the host reports, unless it's missing or `application/octet-stream`; then it works
the type out from the file extension with `MimeTypeMap`.

The tile is always drawn first, and the thumbnail goes on top of it (`FileIcon` in `ui/components/FileListItem.kt`).
There's no separate placeholder or error picture: if the thumbnail fails, the tile is simply still there.

### What each host gives

This is `thumbnailUrl(node)` of each host, before the rules above are applied:

| Host | `thumbnailUrl` |
| --- | --- |
| Pixeldrain | `/api/file/<id>/thumbnail` for a file with an id; `/api/filesystem/<path>?thumbnail` for a filesystem file |
| REST API | The `thumbnail_id` endpoint for a file with an id, or `browse_thumbnail` for a path. None without them |
| WebDAV | An image is its own thumbnail (the file's URL); Coil scales it down. Nothing for other files |
| S3 | None |
| SMB | A `/thumb/` URL of `SmbContentServer`, for images, videos and audio |

### Covers and frames from the file

`ui/media/MediaCovers.kt` makes thumbnails for hosts that stream their files over HTTP(S). It builds URLs like this:

```text
audiocover://cover?src=<file URL>&v=<modified date>
audiocover://frame?src=<file URL>&fallback=<host thumbnail>&v=<modified date>
```

`v` is the file's modified (or created) date, so a changed file gets a new thumbnail. Coil hands these URLs to
`MediaCovers.Factory`, whose `CoverFetcher`:

1. Looks in its disk cache, keyed by the SHA-1 of the source URL and `v`. A cached `.jpg` is the thumbnail. A cached
   `.none` means the file was read before and has nothing.
2. Otherwise opens the file with `MediaMetadataRetriever.setDataSource(url, headers)`. The media framework fetches
   only the byte ranges it needs, not the whole file. At most two files are read at once (a `Semaphore(2)`), because
   each takes a decoder and several requests.
3. For a song, takes the `embeddedPicture`. For a video, takes the key frame nearest to a tenth of the way in (past a
   fade-in or a title card), but never more than 30 seconds in. If that gives nothing, it tries the key frame before,
   then the very first frame.
4. Scales the picture to 320 px on its longest side and saves it as a JPEG.
5. With nothing to show, fetches the `fallback` (the host's thumbnail, with its login) through Coil's normal fetchers,
   or fails so the tile stays.

Only a file that was read and really has no picture is remembered as `.none`. A read that failed (the network, a slow
host) is tried again next time.

??? note "Why read a frame when the host has a thumbnail?"
    Some hosts give a video they can't read (an unusual codec, an index at the end of the file) a generic picture of a
    film. The app can't tell that picture from a real thumbnail. Reading a frame on the device fixes most of them, and
    the host's picture is still the fallback.

Thumbnails are written as a `.part` file and then renamed, so one cut short by a crash is never read back.

## Caching

| Cache | Where | What |
| --- | --- | --- |
| Coil's memory and disk caches | Coil's defaults | Every image Coil loads: thumbnails and previews. The app doesn't configure them |
| Song covers | `cacheDir/audio-covers` | Made by `MediaCovers` |
| Video frames | `cacheDir/video-frames-2` | Made by `MediaCovers`. A new folder: the first version also remembered videos that only failed to load once, and never tried them again |
| SMB thumbnails | `cacheDir/smb-thumbnails` | Made by `SmbContentServer`, kept between runs |
| Media streaming | `cacheDir/media_cache` | `ExoPlayerCache`: a `SimpleCache` of up to 100 MB, least recently used first out |

All of them are in the app's cache folder, so Android can clear them when space runs low.

`util/ExoPlayerCache.kt` creates its `SimpleCache` once per process (a `SimpleCache` can't be opened twice on the same
folder). `createMediaPlayer` wraps the OkHttp data source in a `CacheDataSource` with `FLAG_IGNORE_CACHE_ON_ERROR`, so a
cache problem falls back to the network instead of failing playback.

## Previews

The details page (`files/FileDetailsScreen.kt`) picks one preview by the file's `previewMimeType()`:

| The file is | Shown with | File |
| --- | --- | --- |
| An archive (`zip`, `7z`, `rar`, `tar`, `tgz`, `apk`) on a host that can look inside | `ArchiveContents` | `files/ArchiveContents.kt` |
| `image/*` | `InlineImagePreview` | `ui/media/ImagePreview.kt` |
| `video/*` | `InlineVideoPreview`, then the fullscreen player | `ui/media/VideoPlayer.kt` |
| `audio/*` | `AudioPlayerPreview` | `ui/media/AudioPlayer.kt` |
| Text | `CodePreview` | `ui/components/CodePreview.kt` |
| Anything else with a thumbnail | The thumbnail in a card | |

### Images

`LayeredPreviewImage` shows an image in two steps. The thumbnail (small, and usually cached already) appears right
away, blurred, as a backdrop. The full image fades in over it once it has loaded, with a thin progress bar until then.

The thumbnail is always blurred, never shown as if it were the picture. Pixeldrain's thumbnails are made at a fixed
size, so they're framed differently from the full image; blurred, they're just its colours, and nothing seems to jump
when the full image arrives. Blurring needs Android 12; before that the backdrop is only made faint. The setting
**Blurred backdrop** (`AppSettings.blurredBackdrop`) turns it off.

### Fullscreen

`FullScreenMediaPreviewDialog` (`ui/media/FullScreenPreview.kt`) is a dialog that covers the whole screen, under the
system bars. Images go in a `ZoomableBox`: pinch to zoom, drag to pan, double-tap to zoom in and out, tap beside the
picture to close. The image is decoded at most 3072 px on a side (`maxPixels`), so a huge photo doesn't fill the
memory. Videos open `FullscreenVideoPlayer`.

Images use `FilterQuality.None` in the preview and fullscreen, so pixel art and zoomed-in pictures stay sharp instead
of blurry.

### Video and audio

Both players use ExoPlayer from Media3, made by `createMediaPlayer` in `ui/media/MediaPlayerSupport.kt`:

- A remote file streams over the API's OkHttp client (one connection pool, HTTP/2), through the cache above, with the
  host's headers from `HostRequestAuth`. A local file (on the Upload screen) is read directly.
- Playback starts after one second is buffered (the default waits for two and a half) and buffers up to 50 seconds
  ahead.
- `rememberManagedPlayer` ties the player to the composable: it pauses when the app goes to the background and is
  released when the composable leaves.

`AudioPlayerPreview` is created with `prepareNow = false`, so nothing loads until you press play. A song you only
glance at costs no data. The inline video preview is just the thumbnail with a play button; the player starts in
fullscreen.

`MediaControls.kt` has the shared controls: `MediaIconButton`, `MediaTextButton` (e.g. the playback speed) and
`MediaSeekBar`, which grows thicker while pressed, seeks on a tap and scrubs on a drag. The video player's settings
menu has **Loop** (one choice for all videos, `AppSettings.loopVideos`) and the playback speed.

### Text

`FileInfoViewModel.fetchTextFilePreviewContent` decides whether a file is text: a text MIME type, an extension that
`SourceHighlighting.kt` knows, or `application/octet-stream` with an extension like `.log`, `.ini`, `.conf` or `.md`.
Then:

| Limit | Value | Constant |
| --- | --- | --- |
| Largest file that's fetched | 1 MB | `MAX_TEXT_PREVIEW_FETCH_SIZE_BYTES` |
| Shown on the details page | The first 8 KB of text, cut at the last whole line | `MAX_TEXT_PREVIEW_DISPLAY_LENGTH` |
| Shown in fullscreen | Everything fetched | |

A bigger file shows "File is too large … for text preview" instead. The whole file is downloaded into memory through
the same `downloadNodeTo` as a normal download, so it works for every host.

On the details page, `CodePreview` shows the start in a box up to 200 dp high. Its fullscreen button opens
`CodeFullscreen`: numbered lines, wrapping or sideways scrolling (starting from the **Wrap long lines** setting), and a
button to copy everything. Only the lines on screen are laid out, and the colouring is worked out off the main thread,
so long files stay smooth.

The Upload screen has its own, simpler preview (`InlineTextPreview` in `ui/media/TextPreview.kt`): the first 4096
characters of the picked file.

### Syntax highlighting

`files/SourceHighlighting.kt` is plain Kotlin, with no Android in it, so it's tested on the JVM
(`SourceHighlightingTest`, `RichHighlightingTest`, `LongInputHighlightTest`). It has two parts:

- **The language list**, `SourceLanguages.byExtension`: which extension is which language. This is the part to edit
  when a file type is missing. A file whose extension isn't listed can still be recognized by a `#!` first line
  (`byShebang`: `python`, `bash`, `node`, …).
- **The tokenizer**, `tokenize(code, language)`: finds comments, strings, numbers, keywords, types, calls, keys and so
  on. It's a plain scan, character by character with no regular expressions, so a very long input can't overflow the
  stack. It isn't a parser: a string simply ends at its quote or at the end of its line.

`CodePreview.kt` colours the tokens with one of two sets of colours, for light and dark backgrounds. The theme's own
colours are too few and too close together to tell a type from a call from a key.

### Archives

A file named `.zip`, `.7z`, `.rar`, `.tar`, `.tgz` or `.apk` is opened as an archive when the host has `ArchiveOps`
(Pixeldrain, with its `?zip_info` and `?zip_file` API; a REST API config with `archive_info` and `archive_file`
endpoints). The archive itself is never downloaded just to look inside.

`ArchiveContents` shows the folder you're in, its folders to open and its files to save, and a button to go up. Files
inside can only be saved, not opened: `downloadArchiveEntry` starts an ordinary download, and `downloadNodeTo` reads
the entry with `ArchiveOps.read`.

## SMB files by address

An SMB share has no web address for its files. But everything that shows a file in the app works from a URL: Coil
for thumbnails and pictures, ExoPlayer for video and audio, `MediaMetadataRetriever` for covers. So the app gives
each file an address itself, from a tiny HTTP server on the phone: `SmbContentServer` in `provider-smb`.

### How it works

The server listens on **127.0.0.1** only, on a port Android picks (`ServerSocket(0, 50, 127.0.0.1)`). It starts the
first time a URL is asked for. Each connection is handled on a thread from a pool.

Every URL starts with a **token**: 16 random bytes from `SecureRandom`, made fresh each time the app starts.

```text
http://127.0.0.1:<port>/<token>/raw/<host id>/<path>
http://127.0.0.1:<port>/<token>/thumb/<host id>/<path>?v=<modified date>
```

| Request | Answer |
| --- | --- |
| Not `GET` or `HEAD` | `405` |
| A wrong or missing token | `403` |
| An unknown host id | `404` |
| `/raw/…` | The file, with `Content-Type`, `Content-Length` and `Accept-Ranges: bytes` |
| `/raw/…` with `Range: bytes=…` | `206 Partial Content` with `Content-Range`, or `416` for a range outside the file |
| `/thumb/…` | A JPEG (or a PNG when the picture has see-through parts), with `Cache-Control: private, max-age=86400` |

Range requests are what make a video seekable. When you drag the seek bar, ExoPlayer asks for the bytes from that
point on, and the server reads them with `SmbRandomAccessFile.seek` instead of everything before. Only the first range
of a multi-range request is served; players ask for one.

Each `SmbStorageProvider` registers its `SmbClient` under its host id, and its `thumbnailUrl` and `rawContentUrl`
return these addresses.

### Thumbnails on the device

For `/thumb/`, the server makes a thumbnail of up to 320 px from the file on the share:

| Kind | Extensions | How |
| --- | --- | --- |
| Image | `jpg`, `jpeg`, `png`, `webp`, `gif`, `bmp`, `heic`, `heif`, `avif` | Read whole and scaled down. Not for files over 40 MB, which would all have to come over the network |
| Video | `mp4`, `m4v`, `mkv`, `webm`, `mov`, `3gp`, `avi`, `ts` | A frame one second in, read with `MediaMetadataRetriever` through an `SmbMediaDataSource`, which reads only the parts it needs |
| Audio | `mp3`, `m4a`, `flac`, `ogg`, `opus`, `aac`, `wav` | The embedded cover |

Images are decoded with `ImageDecoder`, which turns them the way their EXIF data says. Some HEIC photos from phone
cameras are refused by `ImageDecoder` but read by `BitmapFactory`, so that's tried next. At most three thumbnails are
made at once. Each is saved in `cacheDir/smb-thumbnails` (set by `AppContainer`), keyed by the SHA-1 of the host id,
the path and `v`. A thumbnail that can't be made is not tried again until the app restarts.

### Cleartext, only for 127.0.0.1

Android blocks plain `http://` by default. `app/src/main/res/xml/network_security_config.xml` allows it for exactly
one address:

```xml
<network-security-config>
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="false">127.0.0.1</domain>
    </domain-config>
</network-security-config>
```

Everything else stays HTTPS-only. Traffic to 127.0.0.1 never leaves the phone, so there's nothing to encrypt.

### Why a server?

The alternative would be a second code path for SMB in every place that shows a file: a Coil fetcher, an ExoPlayer
`DataSource`, a retriever source, each with its own seeking and caching. With an address, SMB files go through
exactly the same code as files from any other host. Coil caches them, ExoPlayer streams and seeks them, and the
previews and the fullscreen view need no changes.

Why is that safe?

- **Only this phone can reach it.** It's bound to 127.0.0.1, not to the Wi-Fi network.
- **Only this app knows the token.** Another app on the phone could find the port, but without the token every
  request gets `403`. The token is new on every start, so an old URL stops working.
- **It only reads.** `GET` and `HEAD` are the only methods; there's no way to change a file through it.

## See also

- [Uploads and downloads](transfers.md) for `downloadNodeTo`, which text previews and archive entries also use.
- [SMB](../hosts/smb.md) for the SMB config fields.
- [Hosts in code](providers.md) for `thumbnailUrl`, `rawContentUrl` and `requestHeaders`.
