package tools.senko.materialdrain

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.receiveAsFlow
import tools.senko.materialdrain.files.OpenableDownload

/** What the button on a snackbar does. */
sealed class UiMessageAction(val label: String) {
    /** Opens a finished download. */
    data class OpenDownload(val download: OpenableDownload) : UiMessageAction("Open")

    /** Takes back what was just done, e.g. a song closed from the miniplayer. */
    class Undo(val undo: () -> Unit) : UiMessageAction("Undo")
}

/** A message for the snackbar: the outcome of something the user did. [long]: it stays a while longer (errors, summaries). */
data class UiMessage(val text: String, val long: Boolean = false, val action: UiMessageAction? = null)

/**
 * The messages the ViewModels have for the snackbar, shown one after another by App.kt. Each is shown once. A message sent
 * while no screen shows them (the activity being recreated, a download finishing in the background) waits for the next one.
 * A new kind of message only needs a [show] where it happens: nothing in App.kt has to change for it.
 */
class UiMessages {
    private val channel = Channel<UiMessage>(Channel.UNLIMITED)
    val messages: Flow<UiMessage> = channel.receiveAsFlow()

    fun show(text: String, long: Boolean = false, action: UiMessageAction? = null) {
        channel.trySend(UiMessage(text, long, action))
    }
}

/**
 * Says that files of the active host were changed (deleted, added to a folder or a list), so the screens which may show
 * them load them again. Whoever changes files calls [notifyChanged]; whoever shows them listens to [changes].
 */
class ContentChanges {
    // Several changes in a row are one reload
    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val changes: SharedFlow<Unit> = _changes.asSharedFlow()

    fun notifyChanged() {
        _changes.tryEmit(Unit)
    }
}
