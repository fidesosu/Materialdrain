package tools.senko.materialdrain.files

import tools.senko.materialdrain.provider.api.StorageNode

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
    SortableField.UPLOAD_DATE -> createdAt.orEmpty()
}

/**
 * Orders files by [field] and [ascending]. With [directoriesFirst], folders always come before files and [field] only
 * orders within each group — a file browser's sort never mixes the two kinds of row.
 */
fun fileComparator(field: SortableField, ascending: Boolean, directoriesFirst: Boolean = false): Comparator<StorageNode> {
    val byField = compareBy<StorageNode> { it.sortKey(field) }.let { if (ascending) it else it.reversed() }
    return if (directoriesFirst) compareBy<StorageNode> { !it.isDirectory }.then(byField) else byField
}
