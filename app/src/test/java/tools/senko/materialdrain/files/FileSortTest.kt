package tools.senko.materialdrain.files

import org.junit.Assert.assertEquals
import org.junit.Test
import tools.senko.materialdrain.provider.api.StorageNode
import tools.senko.materialdrain.provider.api.StorageRef

class FileSortTest {

    private fun node(name: String, modified: String? = null, created: String? = null, size: Long? = null, dir: Boolean = false) =
        StorageNode(ref = StorageRef(path = name), name = name, isDirectory = dir, size = size, modifiedAt = modified, createdAt = created)

    @Test
    fun `dates in every host's format sort by the moment`() {
        val files = listOf(
            node("webdav", modified = "Tue, 2 Jan 2024 10:00:00 GMT"),
            node("offset", modified = "2024-01-02T09:00:00-05:00"), // 14:00 UTC
            node("local", modified = "2024-01-01T00:00:00"),
            node("uploaded", created = "2024-01-03T00:00:00Z"),
            node("undated")
        )
        assertEquals(
            listOf("undated", "local", "webdav", "offset", "uploaded"),
            files.sortedWith(fileComparator(SortableField.UPLOAD_DATE, ascending = true)).map { it.name }
        )
        assertEquals(
            listOf("uploaded", "offset", "webdav", "local", "undated"),
            files.sortedWith(fileComparator(SortableField.UPLOAD_DATE, ascending = false)).map { it.name }
        )
    }

    @Test
    fun `folders come first, then the field orders each group`() {
        val files = listOf(node("b.txt", size = 1), node("Zeta", dir = true), node("A.txt", size = 3), node("alpha", dir = true))
        assertEquals(
            listOf("alpha", "Zeta", "A.txt", "b.txt"),
            files.sortedWith(fileComparator(SortableField.NAME, ascending = true, directoriesFirst = true)).map { it.name }
        )
        assertEquals(
            listOf("Zeta", "alpha", "A.txt", "b.txt"),
            files.sortedWith(fileComparator(SortableField.SIZE, ascending = false, directoriesFirst = true)).map { it.name }
        )
    }

    @Test
    fun `numbers in names sort by their value`() {
        val files = listOf("Chainsaw Man 1x11.mkv", "Chainsaw Man 1x2.mkv", "chainsaw man 1x1.mkv", "Chainsaw Man 1x12.mkv", "Chainsaw Man 1x10.mkv")
            .map { node(it) }
        assertEquals(
            listOf("chainsaw man 1x1.mkv", "Chainsaw Man 1x2.mkv", "Chainsaw Man 1x10.mkv", "Chainsaw Man 1x11.mkv", "Chainsaw Man 1x12.mkv"),
            files.sortedWith(fileComparator(SortableField.NAME, ascending = true)).map { it.name }
        )
    }

    @Test
    fun `leading zeros only decide between the same numbers`() {
        assertEquals(listOf("a1", "a01", "a2", "a010"), listOf("a010", "a2", "a01", "a1").sortedWith { x, y -> compareNatural(x, y) })
        assertEquals(listOf("a01b", "a1c"), listOf("a1c", "a01b").sortedWith { x, y -> compareNatural(x, y) })
        assertEquals(listOf("track", "track 0", "track 9", "track 10"), listOf("track 10", "track", "track 9", "track 0").sortedWith { x, y -> compareNatural(x, y) })
    }

    @Test
    fun `a comparator used again sorts the same way`() {
        val comparator = fileComparator(SortableField.NAME, ascending = true)
        val files = listOf(node("c"), node("a"), node("b"))
        assertEquals(files.sortedWith(comparator), files.reversed().sortedWith(comparator))
    }
}
