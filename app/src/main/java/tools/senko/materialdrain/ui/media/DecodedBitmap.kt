package tools.senko.materialdrain.ui.media

import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

private const val TAG_DECODE = "DecodedBitmap"

/**
 * [bytes] (a song's embedded cover, a video's frame, an APK's icon) as a picture, decoded once for those bytes; null when
 * they aren't one the device can read, so the caller shows its placeholder instead.
 */
@Composable
fun rememberDecodedBitmap(bytes: ByteArray): ImageBitmap? = remember(bytes) {
    try {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    } catch (e: Exception) {
        Log.w(TAG_DECODE, "Couldn't decode ${bytes.size} bytes as a picture", e)
        null
    }
}
