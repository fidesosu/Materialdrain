package tools.senko.materialdrain.ui.preview

import android.content.res.Configuration
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.app.ActivityOptionsCompat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tools.senko.materialdrain.provider.api.PixeldrainRichDetails
import tools.senko.materialdrain.provider.api.StorageNode
import tools.senko.materialdrain.provider.api.StorageRef
import tools.senko.materialdrain.ui.theme.MaterialdrainTheme

/*
 * What the previews of the app's UI share (Android Studio draws a function marked with @Preview beside the code, in the
 * Split or Design view): the theme around them, light and dark, and made-up files to show. Nothing here runs in the app
 * itself; each file's previews are at its end, under "Previews".
 */

/** A part of the UI in the light and the dark theme, side by side. */
@Preview(name = "Light", showBackground = true)
@Preview(name = "Dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL)
annotation class ThemePreviews

/** A whole screen on a phone, in the light and the dark theme. */
@Preview(name = "Light", device = Devices.PIXEL_7, showBackground = true)
@Preview(
    name = "Dark",
    device = Devices.PIXEL_7,
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL
)
annotation class ScreenPreviews

/**
 * A whole long page at once (the account, a file's details), in the light and the dark theme: a phone's width, as tall as
 * the page needs. On a phone it scrolls; in a preview it doesn't, so a phone's height would cut it off.
 */
@Preview(name = "Light", device = "spec:width=412dp,height=3600dp,dpi=420", showBackground = true)
@Preview(
    name = "Dark",
    device = "spec:width=412dp,height=3600dp,dpi=420",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL
)
annotation class LongScreenPreviews

/**
 * The app's theme around a preview, on the app's background, [padding] in from its edges. Settings which the app provides
 * at its root (see App.kt) have their defaults: animations on, the blurred backdrop on, no data saver.
 */
@Composable
fun PreviewTheme(padding: Dp = 0.dp, content: @Composable BoxScope.() -> Unit) {
    // A screen which picks files asks for a launcher to pick them with; in a preview it picks nothing
    CompositionLocalProvider(LocalActivityResultRegistryOwner provides NoResults) {
        MaterialdrainTheme {
            Surface(color = MaterialTheme.colorScheme.background) {
                Box(Modifier.padding(padding), content = content)
            }
        }
    }
}

/** Where a preview's file pickers and permission requests go: nowhere, nothing is opened. */
private object NoResults : ActivityResultRegistryOwner {
    override val activityResultRegistry = object : ActivityResultRegistry() {
        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {}
    }
}

/** Made-up files for the previews: one of each kind the app shows differently. */
object PreviewSamples {
    private const val DATE = "2026-10-12T14:30:00Z"

    private fun file(name: String, size: Long, mime: String?, id: String = name.hashCode().toUInt().toString(36)) =
        StorageNode(ref = StorageRef(id = id), name = name, isDirectory = false, size = size, createdAt = DATE, mimeType = mime)

    val folder = StorageNode(ref = StorageRef(path = "me/Holiday photos"), name = "Holiday photos", isDirectory = true, createdAt = DATE)
    val image = file("beach-sunset.jpg", 3_482_112, "image/jpeg")
    val video = file("drone-flight.mp4", 248_313_856, "video/mp4")
    val song = file("Some Artist - A Song.mp3", 8_912_430, "audio/mpeg")
    val pdf = file("Invoice 2026-10.pdf", 182_044, "application/pdf")
    val text = file("notes.md", 4_210, "text/markdown")
    val archive = file("backup.zip", 52_428_800, "application/zip")
    val app = file("Materialdrain-1.5.apk", 9_437_184, "application/vnd.android.package-archive")
    val unknown = file("RetroMusic-2026-10-12.rmbak", 412_330, "application/zip")
    val longName = file("A very long file name that does not fit on one line of the list, not even close.tar.gz", 1_073_741_824, "application/gzip")

    /** A Pixeldrain file with the details only Pixeldrain gives: views, downloads, the hash, when it deletes itself. */
    val pixeldrainFile = image.copy(
        richDetails = PixeldrainRichDetails(
            fileId = "aBcD1234",
            sha256 = "9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08",
            views = 1_284,
            downloads = 312,
            bandwidthUsed = 1_093_412_864,
            canDownload = true,
            dateLastView = DATE,
            deleteAfterDownloads = 500
        )
    )

    val files = listOf(folder, image, video, song, pdf, text, archive, app, unknown, longName)

    /** A text file's content, for the text and code previews. */
    const val CODE = """# Notes

Some **Markdown** to show the colours of a text preview.

```kotlin
fun greet(name: String) = println("Hello, ${'$'}name!")
```

- one
- two
"""

    /** A Markdown file with each kind of block, for the Markdown previews (see MarkdownPreview). */
    const val MARKDOWN = """# Release notes

Version **1.5** brings *swiping* between files, `code` in notes and [a link](https://pixeldrain.com).

## Done
- [x] Fullscreen texts
- [ ] Markdown tables
  - nested, with ~~struck~~ text

> A quote, with **its own** marks.

```kotlin
val greeting = "Hello"
```

| Kind | Count |
|------|------:|
| Pictures | 12 |
| Songs | 3 |

---
"""
}
