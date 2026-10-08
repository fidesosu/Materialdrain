package tools.senko.materialdrain.files

import tools.senko.materialdrain.provider.api.StorageNode
import tools.senko.materialdrain.util.parseDateTime
import java.util.Collections
import java.util.IdentityHashMap

/**
 * The date a file is listed and sorted by: when it was last modified where the host knows (SMB, WebDAV, S3, Pixeldrain's
 * filesystem), else when it was uploaded (Pixeldrain's files, which never change).
 */
val StorageNode.listDate: String? get() = modifiedAt ?: createdAt

/** The field a file list is sorted by. One choice for every sortable list in the app (see AppSettings.sortField). */
enum class SortableField {
    NAME,
    SIZE,
    UPLOAD_DATE
}

/** The labels and the order they're offered in, wherever a sort menu is shown. */
val SortOptions: List<Pair<String, SortableField>> = listOf(
    "Name" to SortableField.NAME,
    "Size" to SortableField.SIZE,
    "Date" to SortableField.UPLOAD_DATE
)

private fun StorageNode.sortKey(field: SortableField): Comparable<*> = when (field) {
    SortableField.NAME -> name.lowercase()
    SortableField.SIZE -> size ?: 0L
    // By the moment itself, not the text: hosts write dates differently, and RFC 1123 doesn't sort as text
    SortableField.UPLOAD_DATE -> parseDateTime(listDate)?.toEpochMilli() ?: Long.MIN_VALUE
}

/**
 * Orders files by [field] and [ascending]. With [directoriesFirst], folders always come before files and [field] only
 * orders within each group — a file browser's sort never mixes the two kinds of row.
 */
fun fileComparator(field: SortableField, ascending: Boolean, directoriesFirst: Boolean = false): Comparator<StorageNode> {
    // Each file's key is worked out once, not at every comparison: a sort compares each file many times, and a date has
    // to be read from its text first. Kept for as long as the comparator is, by the file itself (not its equality, which
    // would hash every field of it); synchronized, as a search may still be sorting when the next one starts
    val keys = Collections.synchronizedMap(IdentityHashMap<StorageNode, Comparable<*>>())
    val byField = compareBy<StorageNode> { node -> keys.getOrPut(node) { node.sortKey(field) } }
        .let { if (ascending) it else it.reversed() }
    return if (directoriesFirst) compareBy<StorageNode> { !it.isDirectory }.then(byField) else byField
}
