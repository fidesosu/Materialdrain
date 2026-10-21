package tools.senko.materialdrain.files

// Markdown read into its blocks, for showing it as it's meant to look (see MarkdownPreview) rather than as its source. The
// common parts of CommonMark and GitHub's: headings, paragraphs, lists (and their task boxes), quotes, code, tables and
// rules. What's inside a block (emphasis, code, links) is left as written, for the renderer to read.

/** A block of a Markdown text, as [parseMarkdown] reads it. */
sealed interface MdBlock {
    /** A heading of [level] 1 to 6 */
    data class Heading(val level: Int, val text: String) : MdBlock

    data class Paragraph(val text: String) : MdBlock

    /**
     * An item of a list, [depth] levels in (0 at the start): [marker] is its number ("3."), null for a bullet; [checked]
     * whether its task box is ticked, null for one without
     */
    data class ListItem(val depth: Int, val marker: String?, val text: String, val checked: Boolean?) : MdBlock

    /** A quote, of blocks of its own */
    data class Quote(val blocks: List<MdBlock>) : MdBlock

    /** Code, of the language its fence names ([info], e.g. "kotlin"; empty when it names none) */
    data class Code(val info: String, val code: String) : MdBlock

    /** A table: its [header] cells, how each column lines up, and the cells of its [rows] */
    data class Table(val header: List<String>, val alignments: List<MdAlign>, val rows: List<List<String>>) : MdBlock

    /** A line across */
    data object Rule : MdBlock
}

/** How a column of a Markdown table lines up. */
enum class MdAlign { START, CENTER, END }

private val HEADING = Regex("^ {0,3}(#{1,6})(?:[ \\t]+(.*?))?(?:[ \\t]+#+)?[ \\t]*$")
private val FENCE = Regex("^( {0,3})(`{3,}|~{3,})[ \\t]*([^`]*)$")
private val RULE = Regex("^ {0,3}([-*_])(?:[ \\t]*\\1){2,}[ \\t]*$")
private val QUOTE = Regex("^ {0,3}> ?(.*)$")
private val LIST_ITEM = Regex("^([ \\t]*)([-*+]|\\d{1,9}[.)])(?:[ \\t]+(.*))?$")
private val TASK = Regex("^\\[([ xX])][ \\t]+(.*)$")
private val SETEXT = Regex("^ {0,3}(=+|-+)[ \\t]*$")
private val TABLE_SEPARATOR = Regex("^[ \\t]*\\|?[ \\t]*:?-+:?[ \\t]*(\\|[ \\t]*:?-+:?[ \\t]*)*\\|?[ \\t]*$")
private val INDENTED_CODE = Regex("^(?: {4}|\\t)(.*)$")

/** The blocks of the Markdown [text]. */
fun parseMarkdown(text: String): List<MdBlock> = parseBlocks(text.replace("\r\n", "\n").split('\n'))

private fun parseBlocks(lines: List<String>): List<MdBlock> {
    val blocks = ArrayList<MdBlock>()
    val paragraph = ArrayList<String>()
    fun endParagraph() {
        if (paragraph.isNotEmpty()) {
            blocks += MdBlock.Paragraph(joinLines(paragraph))
            paragraph.clear()
        }
    }
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        if (line.isBlank()) {
            endParagraph()
            i++
            continue
        }

        val fence = FENCE.matchEntire(line)
        if (fence != null) {
            endParagraph()
            val marks = fence.groupValues[2]
            val indent = fence.groupValues[1].length
            val code = ArrayList<String>()
            i++
            while (i < lines.size && !isClosingFence(lines[i], marks)) {
                code += lines[i].dropIndent(indent)
                i++
            }
            // Past the closing fence; a block never closed goes on to the end
            i++
            blocks += MdBlock.Code(fence.groupValues[3].trim().substringBefore(' '), code.joinToString("\n"))
            continue
        }

        // Under a paragraph's lines, === or --- makes them a heading (before --- could be read as a rule)
        val setext = SETEXT.matchEntire(line)
        if (setext != null && paragraph.isNotEmpty()) {
            blocks += MdBlock.Heading(if (setext.groupValues[1].startsWith('=')) 1 else 2, joinLines(paragraph))
            paragraph.clear()
            i++
            continue
        }

        if (RULE.matches(line)) {
            endParagraph()
            blocks += MdBlock.Rule
            i++
            continue
        }

        val heading = HEADING.matchEntire(line)
        if (heading != null) {
            endParagraph()
            blocks += MdBlock.Heading(heading.groupValues[1].length, heading.groupValues[2].trim())
            i++
            continue
        }

        if (QUOTE.matches(line)) {
            endParagraph()
            val quoted = ArrayList<String>()
            while (i < lines.size) {
                val inside = QUOTE.matchEntire(lines[i]) ?: break
                quoted += inside.groupValues[1]
                i++
            }
            blocks += MdBlock.Quote(parseBlocks(quoted))
            continue
        }

        val item = LIST_ITEM.matchEntire(line)
        if (item != null && (paragraph.isEmpty() || item.groupValues[3].isNotBlank())) {
            endParagraph()
            val indent = item.groupValues[1].replace("\t", "    ").length
            val marker = item.groupValues[2].takeIf { it.first().isDigit() }
            val text = StringBuilder(item.groupValues[3])
            i++
            // Its own lines after the first, up to a blank line or the next block
            while (i < lines.size && lines[i].isNotBlank() && !startsBlock(lines[i])) {
                text.append('\n').append(lines[i].trim())
                i++
            }
            val task = TASK.matchEntire(text)
            blocks += MdBlock.ListItem(
                depth = (indent / 2).coerceAtMost(6),
                marker = marker,
                text = joinLines((task?.groupValues?.get(2) ?: text.toString()).split('\n')),
                checked = task?.groupValues?.get(1)?.let { it != " " }
            )
            continue
        }

        if (paragraph.isEmpty() && line.contains('|') && i + 1 < lines.size && TABLE_SEPARATOR.matches(lines[i + 1]) && lines[i + 1].contains('-')) {
            val header = tableCells(line)
            val alignments = tableCells(lines[i + 1]).map { cell ->
                when {
                    cell.startsWith(':') && cell.endsWith(':') -> MdAlign.CENTER
                    cell.endsWith(':') -> MdAlign.END
                    else -> MdAlign.START
                }
            }
            i += 2
            val rows = ArrayList<List<String>>()
            while (i < lines.size && lines[i].contains('|') && lines[i].isNotBlank()) {
                rows += tableCells(lines[i])
                i++
            }
            blocks += MdBlock.Table(header, alignments, rows)
            continue
        }

        val indented = INDENTED_CODE.matchEntire(line)
        if (indented != null && paragraph.isEmpty()) {
            val code = ArrayList<String>()
            while (i < lines.size) {
                val codeLine = INDENTED_CODE.matchEntire(lines[i])
                // A blank line goes on with the code when more of it follows
                if (codeLine != null) code += codeLine.groupValues[1]
                else if (lines[i].isBlank() && lines.getOrNull(i + 1)?.let { INDENTED_CODE.matches(it) } == true) code += ""
                else break
                i++
            }
            blocks += MdBlock.Code("", code.joinToString("\n"))
            continue
        }

        paragraph += line
        i++
    }
    endParagraph()
    return blocks
}

/** Whether [line] starts a block of its own, which ends a list item's lines (or a paragraph's). */
private fun startsBlock(line: String): Boolean =
    LIST_ITEM.matches(line) || HEADING.matches(line) || QUOTE.matches(line) || FENCE.matches(line) || RULE.matches(line)

/** Whether [line] closes a code block opened with [marks]: as many of the same mark or more, and nothing else. */
private fun isClosingFence(line: String, marks: String): Boolean {
    val trimmed = line.trim()
    return trimmed.length >= marks.length && trimmed.all { it == marks[0] }
}

/** [this] without up to [count] spaces at its start: a fence's own indent, which its code lines share. */
private fun String.dropIndent(count: Int): String {
    var drop = 0
    while (drop < count && drop < length && this[drop] == ' ') drop++
    return substring(drop)
}

/**
 * The lines of a paragraph as one text: joined with a space, except after a line ending in two spaces or a backslash,
 * which breaks the line there.
 */
private fun joinLines(lines: List<String>): String = buildString {
    lines.forEachIndexed { index, raw ->
        val line = raw.trimStart()
        val last = index == lines.lastIndex
        when {
            last -> append(line.trimEnd())
            line.endsWith("  ") -> append(line.trimEnd()).append('\n')
            line.endsWith('\\') -> append(line.dropLast(1).trimEnd()).append('\n')
            else -> append(line.trimEnd()).append(' ')
        }
    }
}

/** The cells of a row of a table, without the pipes at its ends; a pipe written \| is part of a cell. */
private fun tableCells(line: String): List<String> {
    var row = line.trim()
    if (row.startsWith('|')) row = row.substring(1)
    if (row.endsWith('|') && !row.endsWith("\\|")) row = row.dropLast(1)
    val cells = ArrayList<String>()
    val cell = StringBuilder()
    var at = 0
    while (at < row.length) {
        val c = row[at]
        when {
            c == '\\' && row.getOrNull(at + 1) == '|' -> {
                cell.append('|')
                at++
            }
            c == '|' -> {
                cells += cell.toString().trim()
                cell.clear()
            }
            else -> cell.append(c)
        }
        at++
    }
    cells += cell.toString().trim()
    return cells
}
