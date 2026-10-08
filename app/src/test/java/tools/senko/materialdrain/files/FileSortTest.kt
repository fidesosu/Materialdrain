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
    fun `a comparator used again sorts the same way`() {
        val comparator = fileComparator(SortableField.NAME, ascending = true)
        val files = listOf(node("c"), node("a"), node("b"))
        assertEquals(files.sortedWith(comparator), files.reversed().sortedWith(comparator))
    }
}
