package tools.senko.materialdrain.files

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

/** A finished download which the app can open: where it was saved, and what kind of file it is. */
data class OpenableDownload(val uri: Uri, val mimeType: String)

/** Opens a downloaded file with an app which handles its type (an APK opens the installer, a zip an archive app). */
fun openDownloadedFile(context: Context, download: OpenableDownload) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(download.uri, download.mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No app on this device can open this file.", Toast.LENGTH_LONG).show()
    }
}
