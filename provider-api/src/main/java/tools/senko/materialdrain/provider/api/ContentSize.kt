package tools.senko.materialdrain.provider.api

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

/**
 * The size of the file behind a content [uri], as the app which shares it reports it; null when it doesn't say (or can't
 * be asked). Uploads send it as the Content-Length and use it for their progress.
 */
fun queryContentSize(context: Context, uri: Uri): Long? = try {
    context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val index = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (index >= 0 && !cursor.isNull(index)) cursor.getLong(index) else null
        } else null
    }
} catch (e: Exception) {
    ProviderLog.w("Upload", "could not read the size of $uri: ${e.message}")
    null
}
