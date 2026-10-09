package tools.senko.materialdrain.util

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.system.Os
import android.system.OsConstants
import android.util.Log
import java.io.File

private const val TAG_URI_INFO = "ContentUriInfo"

/** @param lastModifiedMillis when the file was last changed, as Android knows it; null when it doesn't say */
data class ContentUriInfo(val displayName: String?, val sizeBytes: Long?, val lastModifiedMillis: Long? = null)

/** Reads the display name, size and last change of a content URI without opening the file. */
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
    return ContentUriInfo(name, size, readLastModified(context, uri))
}

/**
 * When the file at [uri] was last changed, in milliseconds, or null when nothing says. In this order:
 * 1. the document's last change, for files picked through the system's file picker;
 * 2. MediaStore's (in seconds), for the device's own media;
 * 3. the file's own, from the opened file itself: for apps which share a file but don't answer either question (a
 *    gallery, Google Photos), whatever their link looks like. Only a real file counts: a stream an app writes as it's
 *    read (a pipe) has no date of its own.
 * Each is asked on its own: a provider that doesn't know a column may refuse the whole query.
 */
private fun readLastModified(context: Context, uri: Uri): Long? {
    if (uri.scheme == ContentResolver.SCHEME_FILE) {
        return uri.path?.let { File(it).lastModified() }?.takeIf { it > 0 }
    }
    fun queryLong(column: String): Long? = try {
        context.contentResolver.query(uri, arrayOf(column), null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(column)
            if (cursor.moveToFirst() && index >= 0 && !cursor.isNull(index)) cursor.getLong(index) else null
        }
    } catch (e: Exception) {
        null
    }
    queryLong(DocumentsContract.Document.COLUMN_LAST_MODIFIED)?.takeIf { it > 0 }?.let { return it }
    queryLong(MediaStore.MediaColumns.DATE_MODIFIED)?.takeIf { it > 0 }?.let { return it * 1000 }
    return try {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
            val stat = Os.fstat(descriptor.fileDescriptor)
            if (OsConstants.S_ISREG(stat.st_mode)) (stat.st_mtime * 1000).takeIf { it > 0 } else null
        }
    } catch (e: Exception) {
        Log.w(TAG_URI_INFO, "No last change known for $uri: ${e.message}")
        null
    }
}
