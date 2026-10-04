package tools.senko.materialdrain.transfer

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.ConcurrentHashMap

enum class TransferKind { UPLOAD, DOWNLOAD }

enum class TransferOutcome { COMPLETED, FAILED, CANCELLED }

data class TransferInfo(
    val id: String,
    val kind: TransferKind,
    /** File name, or a description such as "3 files" for a batch. */
    val title: String,
    val transferredBytes: Long = 0L,
    val totalBytes: Long? = null,
    val bytesPerSecond: Long = 0L,
    val etaSeconds: Long? = null
)

/**
 * Process-wide list of running uploads and downloads. The ViewModels report to it, [TransferService]
 * turns it into a foreground notification (which keeps the process alive while the app is in the
 * background) and results are announced with a notification when the app is not in the foreground.
 */
class TransferRegistry(context: Context) {

    private val appContext = context.applicationContext
    private val notifier = TransferNotifier(appContext)

    private val _active = MutableStateFlow<Map<String, TransferInfo>>(emptyMap())
    val active: StateFlow<Map<String, TransferInfo>> = _active.asStateFlow()

    private val cancelHandlers = ConcurrentHashMap<String, () -> Unit>()

    /** Set by the activity; result notifications are only needed when the user is not looking at the app. */
    @Volatile
    var appInForeground: Boolean = false

    init {
        notifier.ensureChannels()
    }

    fun start(info: TransferInfo, onCancel: () -> Unit) {
        cancelHandlers[info.id] = onCancel
        _active.update { it + (info.id to info) }
        TransferService.start(appContext)
    }

    fun progress(id: String, transferredBytes: Long, totalBytes: Long?, bytesPerSecond: Long, etaSeconds: Long?) {
        _active.update { current ->
            val info = current[id] ?: return@update current
            current + (id to info.copy(
                transferredBytes = transferredBytes,
                totalBytes = totalBytes ?: info.totalBytes,
                bytesPerSecond = bytesPerSecond,
                etaSeconds = etaSeconds
            ))
        }
    }

    /**
     * Ends a transfer. [message] describes the result (or the error), [openUri]/[mimeType] let the
     * notification of a finished download open the file.
     */
    fun finish(id: String, outcome: TransferOutcome, message: String? = null, openUri: Uri? = null, mimeType: String? = null) {
        cancelHandlers.remove(id)
        val info = _active.value[id] ?: return
        _active.update { it - id }
        if (outcome != TransferOutcome.CANCELLED && !appInForeground) {
            notifier.notifyResult(info, outcome, message, openUri, mimeType)
        }
    }

    fun cancelAll() {
        cancelHandlers.values.toList().forEach { it() }
    }
}
