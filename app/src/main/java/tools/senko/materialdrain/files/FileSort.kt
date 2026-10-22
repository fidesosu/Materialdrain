package tools.senko.materialdrain.files

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.flowOf
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
    SortableField.NAME -> NaturalName(name)
    SortableField.SIZE -> size ?: 0L
    // By the moment itself, not the text: hosts write dates differently, and RFC 1123 doesn't sort as text
    SortableField.UPLOAD_DATE -> parseDateTime(listDate)?.toEpochMilli() ?: Long.MIN_VALUE
}

/**
 * A name as it's sorted: without regard to case, and with the numbers in it by their value rather than digit by digit,
 * so "Episode 2" comes before "Episode 11" (and "1x9" before "1x10"), as a person would put them.
 */
internal class NaturalName(name: String) : Comparable<NaturalName> {
    private val text = name.lowercase()
    override fun compareTo(other: NaturalName): Int = compareNatural(text, other.text)
}

private fun Char.isAsciiDigit() = this in '0'..'9'

/**
 * Compares [a] and [b] character by character, but each run of digits as the number it is. The same number written with
 * more leading zeros ("01" against "1") only decides when nothing else does, the one with fewer first.
 */
internal fun compareNatural(a: String, b: String): Int {
    var i = 0
    var j = 0
    var zeros = 0
    while (i < a.length && j < b.length) {
        if (a[i].isAsciiDigit() && b[j].isAsciiDigit()) {
            // The numbers without their leading zeros: the longer one is the bigger, else the first digit that differs
            var startA = i
            while (startA < a.length - 1 && a[startA] == '0' && a[startA + 1].isAsciiDigit()) startA++
            var startB = j
            while (startB < b.length - 1 && b[startB] == '0' && b[startB + 1].isAsciiDigit()) startB++
            var endA = startA
            while (endA < a.length && a[endA].isAsciiDigit()) endA++
            var endB = startB
            while (endB < b.length && b[endB].isAsciiDigit()) endB++
            val lengthA = endA - startA
            val lengthB = endB - startB
            if (lengthA != lengthB) return lengthA - lengthB
            for (k in 0 until lengthA) {
                val difference = a[startA + k] - b[startB + k]
                if (difference != 0) return difference
            }
            if (zeros == 0) zeros = (startA - i) - (startB - j)
            i = endA
            j = endB
        } else {
            val difference = a[i] - b[j]
            if (difference != 0) return difference
            i++
            j++
        }
    }
    val rest = (a.length - i) - (b.length - j)
    return if (rest != 0) rest else zeros
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

/**
 * [files] narrowed to the names containing [filterQuery], in the shared sort order. Only recomputed when one of the inputs
 * really changes: they're usually read out of a UI state which also changes with every progress tick of a transfer, and
 * sorting a long list again for each of those ticks is wasted work.
 */
fun filteredAndSorted(
    files: Flow<List<StorageNode>>,
    filterQuery: Flow<String>,
    sortField: Flow<SortableField>,
    sortAscending: Flow<Boolean>,
    /** Folders before files (the filesystem's, AppSettings.foldersFirst) */
    directoriesFirst: Flow<Boolean> = flowOf(false)
): Flow<List<StorageNode>> = combine(
    files.distinctUntilChanged(),
    filterQuery.distinctUntilChanged(),
    sortField,
    sortAscending,
    directoriesFirst
) { list, query, field, ascending, foldersFirst ->
    list.filter { it.name.contains(query, ignoreCase = true) }
        .sortedWith(fileComparator(field, ascending, foldersFirst))
}.flowOn(Dispatchers.Default)
