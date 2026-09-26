package tools.senko.materialdrain.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log

private const val TAG_URI_INFO = "ContentUriInfo"

data class ContentUriInfo(val displayName: String?, val sizeBytes: Long?)

/** Reads the display name and size of a content URI without opening the file. */
fun readContentUriInfo(context: Context, uri: Uri): ContentUriInfo {
    var name: String? = null
    var size: Long? = null
    try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameIndex >= 0) name = cursor.getString(nameIndex)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
            }
        }
    } catch (e: Exception) {
        Log.w(TAG_URI_INFO, "Could not read details for $uri: ${e.message}")
    }
    return ContentUriInfo(name, size)
}
