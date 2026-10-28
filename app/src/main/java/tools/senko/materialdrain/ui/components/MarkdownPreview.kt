package tools.senko.materialdrain.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tools.senko.materialdrain.files.MdAlign
import tools.senko.materialdrain.files.MdBlock
import tools.senko.materialdrain.files.SourceLanguage
import tools.senko.materialdrain.files.SyntaxFamily
import tools.senko.materialdrain.files.languageFor
import tools.senko.materialdrain.files.parseMarkdown
import tools.senko.materialdrain.ui.icons.filled.Check
import tools.senko.materialdrain.ui.preview.PreviewSamples
import tools.senko.materialdrain.ui.preview.PreviewTheme
import tools.senko.materialdrain.ui.preview.ThemePreviews
import tools.senko.materialdrain.ui.theme.AppShapes
import androidx.compose.ui.res.stringResource
import tools.senko.materialdrain.R
import androidx.compose.ui.platform.LocalResources

/** Whether [language] is Markdown, which the previews show as it's meant to look rather than as its source. */
fun isMarkdown(language: SourceLanguage?): Boolean = language?.family == SyntaxFamily.MARKDOWN

/** How much smaller the Markdown is in the preview's card than over the whole screen, so more of it fits. */
private const val CARD_SCALE = 0.85f

/**
 * A Markdown file as it's meant to look (see parseMarkdown): its headings, lists, quotes, code, tables and links, in the
 * same card as the other texts (see CodePreview), with what it is and how long over its bottom corners, and the badge
 * which opens all of it fullscreen. The fullscreen view shows it the same way, or its source (see CodeFullscreenContent).
 *
 * @param content the start of the file, shown here
 * @param fullContent the whole file, for the fullscreen view; null when [content] is all of it
 * @param language the file's language (Markdown), for its source in the fullscreen view
 * @param onFullScreen opens the fullscreen view elsewhere (see FullScreenFileDialog); null, the badge opens [CodeFullscreen]
 */
@Composable
fun MarkdownPreview(
    content: String,
    language: SourceLanguage?,
    modifier: Modifier = Modifier,
    fullContent: String? = null,
    title: String = "Markdown",
    outerPadding: PaddingValues = PaddingValues(vertical = 8.dp),
    onFullScreen: (() -> Unit)? = null
) {
    val blocks = remember(content) { parseMarkdown(content) }
    val whole = fullContent ?: content
    val totalLines = remember(whole) { lineCount(whole) }
    val shownLines = remember(content) { lineCount(content) }
    val cut = fullContent != null && fullContent.length > content.length
    var fullscreen by rememberSaveable { mutableStateOf(false) }

    CodePreviewFrame(modifier, outerPadding) {
        val scroll = rememberScrollState()
        // Scrolling stops at its ends rather than carrying on into the page around it (see ScrollStopsAtEnds)
        val keepScrollInside = remember(scroll) { ScrollStopsAtEnds(scroll) }
        SelectionContainer {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(keepScrollInside)
                    .verticalScroll(scroll)
                    // Room under the last block for the labels over the bottom, so it can be scrolled clear of them
                    .padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 44.dp)
            ) {
                blocks.forEach { MarkdownBlockView(it, CARD_SCALE) }
            }
        }
        TextPreviewLabels(
            label = textPreviewLabel(LocalResources.current, language?.name ?: "Markdown", shownLines, totalLines, cut),
            onOpen = { onFullScreen?.invoke() ?: run { fullscreen = true } }
        )
    }

    if (fullscreen) CodeFullscreen(title = title, content = whole, language = language, onDismiss = { fullscreen = false })
}

/**
 * All of a Markdown [text] as it's meant to look, over the whole screen (see CodeFullscreenContent): only the blocks on
 * screen are laid out, and it's read away from the main thread. Its text is [scale] times the theme's.
 */
@Composable
internal fun MarkdownDocument(text: String, listState: LazyListState, scale: Float, modifier: Modifier = Modifier) {
    // A preview (see the end of the file) has it at once, as it shows one frame
    val inPreview = LocalInspectionMode.current
    val blocks by produceState(if (inPreview) parseMarkdown(text) else null, text) {
        value = withContext(Dispatchers.Default) { parseMarkdown(text) }
    }
    val shown = blocks
    if (shown == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    SelectionContainer(modifier = modifier) {
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp + bottom),
            modifier = Modifier.fillMaxSize()
        ) {
            items(shown.size) { index -> MarkdownBlockView(shown[index], scale) }
        }
    }
}

// ---- Blocks ----

/** [this] [scale] times as big, its lines as much further apart. */
private fun TextStyle.scaled(scale: Float): TextStyle =
    if (scale == 1f) this else copy(fontSize = fontSize * scale, lineHeight = lineHeight * scale)

/** One block of Markdown; [muted] in the quieter colour, as inside a quote. */
@Composable
private fun MarkdownBlockView(block: MdBlock, scale: Float, muted: Boolean = false) {
    val scheme = MaterialTheme.colorScheme
    val type = MaterialTheme.typography
    val styles = rememberInlineStyles()
    val color = if (muted) scheme.onSurfaceVariant else scheme.onSurface
    when (block) {
        is MdBlock.Heading -> Column(modifier = Modifier.padding(top = if (block.level <= 2) 8.dp else 4.dp)) {
            val style = when (block.level) {
                1 -> type.headlineSmall
                2 -> type.titleLarge
                3 -> type.titleMedium
                else -> type.titleSmall
            }
            Text(
                remember(block, styles) { markdownInline(block.text, styles) },
                style = style.scaled(scale).copy(fontWeight = FontWeight.SemiBold),
                color = color
            )
            // The two biggest are underlined, as GitHub shows them
            if (block.level <= 2) HorizontalDivider(modifier = Modifier.padding(top = 4.dp), color = scheme.outlineVariant)
        }
        is MdBlock.Paragraph -> Text(remember(block, styles) { markdownInline(block.text, styles) }, style = type.bodyMedium.scaled(scale), color = color)
        is MdBlock.ListItem -> Row(modifier = Modifier.padding(start = 20.dp * block.depth)) {
            Box(modifier = Modifier.width(24.dp * scale), contentAlignment = Alignment.TopStart) {
                val checked = block.checked
                if (checked != null) {
                    TaskBox(checked, scale)
                } else {
                    Text(block.marker ?: Bullets[block.depth % Bullets.size], style = type.bodyMedium.scaled(scale), color = scheme.onSurfaceVariant)
                }
            }
            Text(
                remember(block, styles) { markdownInline(block.text, styles) },
                style = type.bodyMedium.scaled(scale),
                color = if (block.checked == true) scheme.onSurfaceVariant else color,
                modifier = Modifier.weight(1f)
            )
        }
        is MdBlock.Quote -> Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Box(modifier = Modifier.width(3.dp).fillMaxHeight().background(scheme.outlineVariant, AppShapes.ExtraSmall))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(start = 12.dp)) {
                block.blocks.forEach { MarkdownBlockView(it, scale, muted = true) }
            }
        }
        is MdBlock.Code -> {
            val colors = codeColors()
            // Coloured for the language its fence names, as the code previews are
            val language = remember(block.info) { block.info.takeIf { it.isNotBlank() }?.let { languageFor("code.${it.lowercase()}") } }
            val highlighted = remember(block, language, colors) { highlightedCode(block.code.replace("\t", "    "), language, colors) }
            Surface(color = scheme.surfaceContainerLow, shape = AppShapes.Small, modifier = Modifier.fillMaxWidth()) {
                Text(
                    highlighted,
                    style = type.bodySmall.scaled(scale),
                    fontFamily = FontFamily.Monospace,
                    color = scheme.onSurface,
                    softWrap = false,
                    modifier = Modifier.horizontalScroll(rememberScrollState()).padding(12.dp)
                )
            }
        }
        is MdBlock.Table -> MarkdownTable(block, scale, styles)
        MdBlock.Rule -> HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = scheme.outlineVariant)
    }
}

/** The bullets of a list's items, by how far in they are. */
private val Bullets = listOf("•", "◦", "▪")

/** The box of a task in a list, ticked when [checked]. */
@Composable
private fun TaskBox(checked: Boolean, scale: Float) {
    val scheme = MaterialTheme.colorScheme
    val size = 16.dp * scale
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .padding(top = 2.dp * scale)
            .size(size)
            .clip(AppShapes.ExtraSmall)
            .then(
                if (checked) Modifier.background(scheme.primary)
                else Modifier.border(1.5.dp, scheme.onSurfaceVariant, AppShapes.ExtraSmall)
            )
    ) {
        if (checked) Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.common_done), tint = scheme.onPrimary, modifier = Modifier.size(size * 0.8f))
    }
}

/**
 * A table: its columns as wide as their widest cell (up to a limit, past which a cell wraps), lined up as the table says,
 * the header on a tinted row. A table wider than the screen scrolls sideways.
 */
@Composable
private fun MarkdownTable(table: MdBlock.Table, scale: Float, styles: InlineStyles) {
    val scheme = MaterialTheme.colorScheme
    val style = MaterialTheme.typography.bodyMedium.scaled(scale).copy(color = scheme.onSurface)
    val headerStyle = style.copy(fontWeight = FontWeight.SemiBold)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val header = remember(table, styles) { table.header.map { markdownInline(it, styles) } }
    val rows = remember(table, styles) { table.rows.map { row -> row.map { markdownInline(it, styles) } } }
    val columns = maxOf(header.size, rows.maxOfOrNull { it.size } ?: 0)
    val widest = with(density) { 240.dp.roundToPx() }
    val widths = remember(header, rows, headerStyle) {
        (0 until columns).map { column ->
            val cells = listOfNotNull(header.getOrNull(column)) + rows.mapNotNull { it.getOrNull(column) }
            (cells.maxOfOrNull { measurer.measure(it, headerStyle).size.width } ?: 0).coerceAtMost(widest)
        }
    }

    @Composable
    fun TableRow(cells: List<AnnotatedString>, cellStyle: TextStyle, modifier: Modifier = Modifier) {
        Row(modifier = modifier) {
            widths.forEachIndexed { column, width ->
                Text(
                    cells.getOrNull(column) ?: AnnotatedString(""),
                    style = cellStyle,
                    textAlign = when (table.alignments.getOrNull(column)) {
                        MdAlign.CENTER -> TextAlign.Center
                        MdAlign.END -> TextAlign.End
                        else -> TextAlign.Start
                    },
                    modifier = Modifier
                        .width(with(density) { width.toDp() } + 24.dp)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
        }
    }

    Box(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        Column(
            modifier = Modifier
                .width(IntrinsicSize.Max)
                .clip(AppShapes.Small)
                .border(1.dp, scheme.outlineVariant, AppShapes.Small)
        ) {
            TableRow(header, headerStyle, Modifier.fillMaxWidth().background(scheme.surfaceContainerHigh))
            rows.forEach { row ->
                HorizontalDivider(color = scheme.outlineVariant)
                TableRow(row, style)
            }
        }
    }
}

// ---- Inside a block ----

/** How code and links inside the blocks look. */
@Immutable
internal class InlineStyles(val code: SpanStyle, val link: TextLinkStyles)

@Composable
private fun rememberInlineStyles(): InlineStyles {
    val scheme = MaterialTheme.colorScheme
    return remember(scheme.onSurface, scheme.primary) {
        InlineStyles(
            code = SpanStyle(fontFamily = FontFamily.Monospace, background = scheme.onSurface.copy(alpha = 0.08f)),
            link = TextLinkStyles(SpanStyle(color = scheme.primary, textDecoration = TextDecoration.Underline))
        )
    }
}

/** What a backslash keeps from being a mark. */
private const val ESCAPABLE = "\\`*_{}[]()#+-.!|~<>"

/**
 * The text inside a block, with what Markdown marks in it: `code`, *emphasis*, **strong**, ~~struck~~, [links](https://…)
 * (opened in the browser when tapped; one to a file beside it is only coloured), <https://…> and bare addresses. A
 * picture is its description, as a link to it.
 */
internal fun markdownInline(text: String, styles: InlineStyles): AnnotatedString = buildAnnotatedString { appendInline(text, styles) }

private fun AnnotatedString.Builder.appendInline(text: String, styles: InlineStyles) {
    val plain = StringBuilder()
    fun flush() {
        if (plain.isNotEmpty()) {
            append(plain.toString())
            plain.setLength(0)
        }
    }
    var i = 0
    while (i < text.length) {
        val c = text[i]
        // An escaped mark is only itself
        if (c == '\\' && i + 1 < text.length && text[i + 1] in ESCAPABLE) {
            plain.append(text[i + 1])
            i += 2
            continue
        }
        if (c == '`') {
            val run = runOf(text, i, c)
            val close = closingRun(text, i + run, c, run)
            if (close >= 0) {
                flush()
                withStyle(styles.code) { append(text.substring(i + run, close).trim()) }
                i = close + run
            } else {
                plain.append(text, i, i + run)
                i += run
            }
            continue
        }
        if (c == '[' || (c == '!' && text.getOrNull(i + 1) == '[')) {
            val image = c == '!'
            val link = linkAt(text, if (image) i + 1 else i)
            if (link != null) {
                flush()
                appendLink(if (image) link.label.ifBlank { "Image" } else link.label, link.url, styles, inner = !image)
                i = link.end
                continue
            }
        }
        if (c == '<') {
            val end = text.indexOf('>', i + 1)
            val inside = if (end > 0) text.substring(i + 1, end) else ""
            if (isWebAddress(inside) && inside.none { it.isWhitespace() }) {
                flush()
                appendLink(inside, inside, styles, inner = false)
                i = end + 1
                continue
            }
        }
        if ((text.startsWith("https://", i) || text.startsWith("http://", i)) && (i == 0 || !text[i - 1].isLetterOrDigit())) {
            var end = i
            while (end < text.length && !text[end].isWhitespace() && text[end] != '<') end++
            // A full stop or a bracket after the address is the sentence's
            while (end > i && text[end - 1] in ".,;:!?)'\"") end--
            val url = text.substring(i, end)
            flush()
            appendLink(url, url, styles, inner = false)
            i = end
            continue
        }
        if (c == '*' || c == '_' || c == '~') {
            val whole = runOf(text, i, c)
            val run = whole.coerceAtMost(3)
            // An opening mark is followed by text; an underscore inside a word (snake_case) is no mark
            val opens = i + run < text.length && !text[i + run].isWhitespace() &&
                (c != '_' || i == 0 || !text[i - 1].isLetterOrDigit()) && (c != '~' || run == 2)
            val close = if (opens) closingEmphasis(text, i + run, c, run) else -1
            if (close > i + run) {
                flush()
                val style = when {
                    c == '~' -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                    run == 1 -> SpanStyle(fontStyle = FontStyle.Italic)
                    run == 2 -> SpanStyle(fontWeight = FontWeight.Bold)
                    else -> SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
                }
                withStyle(style) { appendInline(text.substring(i + run, close), styles) }
                i = close + run
            } else {
                plain.append(text, i, i + whole)
                i += whole
            }
            continue
        }
        plain.append(c)
        i++
    }
    flush()
}

private fun isWebAddress(url: String) = url.startsWith("https://") || url.startsWith("http://") || url.startsWith("mailto:")

/** [label] as a link to [url]: one that opens, for an address on the web; [inner], with the marks inside it read too. */
private fun AnnotatedString.Builder.appendLink(label: String, url: String, styles: InlineStyles, inner: Boolean) {
    fun label() = if (inner) appendInline(label, styles) else append(label)
    if (isWebAddress(url)) {
        withLink(LinkAnnotation.Url(url, styles.link)) { label() }
    } else {
        withStyle(styles.link.style ?: SpanStyle()) { label() }
    }
}

/** How many of [c] there are in a row in [text] from [at]. */
private fun runOf(text: String, at: Int, c: Char): Int {
    var end = at
    while (end < text.length && text[end] == c) end++
    return end - at
}

/** Where a run of exactly [run] of [c] starts in [text], from [from]; -1 for none. */
private fun closingRun(text: String, from: Int, c: Char, run: Int): Int {
    var at = text.indexOf(c, from)
    while (at >= 0) {
        val length = runOf(text, at, c)
        if (length == run) return at
        at = text.indexOf(c, at + length)
    }
    return -1
}

/**
 * Where the mark closing an emphasis of [run] of [c] is in [text], from [from]: as many of it (the last of a longer run),
 * after text rather than a space; an underscore not inside a word. -1 for none.
 */
private fun closingEmphasis(text: String, from: Int, c: Char, run: Int): Int {
    var at = text.indexOf(c, from)
    while (at >= 0) {
        val length = runOf(text, at, c)
        val close = at + length - run
        val afterText = at > from && !text[at - 1].isWhitespace()
        val endsWord = c != '_' || at + length >= text.length || !text[at + length].isLetterOrDigit()
        if (length >= run && afterText && endsWord) return close
        at = text.indexOf(c, at + length)
    }
    return -1
}

/** A link in Markdown, `[label](url)`, ending at [end]. */
private class MdLink(val label: String, val url: String, val end: Int)

/** The link starting at [at] (its opening bracket) in [text]; null when what's there isn't one. */
private fun linkAt(text: String, at: Int): MdLink? {
    if (text.getOrNull(at) != '[') return null
    var depth = 0
    var close = -1
    var i = at
    while (i < text.length) {
        when (text[i]) {
            '\\' -> i++
            '[' -> depth++
            ']' -> if (--depth == 0) {
                close = i
                break
            }
        }
        i++
    }
    if (close < 0 || text.getOrNull(close + 1) != '(') return null
    var parens = 0
    var end = -1
    i = close + 1
    while (i < text.length) {
        when (text[i]) {
            '\\' -> i++
            '(' -> parens++
            ')' -> if (--parens == 0) {
                end = i
                break
            }
        }
        i++
    }
    if (end < 0) return null
    // Without its title ("…" after the address), and the <> an address may be written in
    val url = text.substring(close + 2, end).trim().substringBefore(' ').removePrefix("<").removeSuffix(">")
    return MdLink(text.substring(at + 1, close), url, end + 1)
}

// ---- Previews ----

/** A Markdown file's card: headings, a list with tasks, a quote, code and a table. */
@ThemePreviews
@Composable
private fun MarkdownPreviewPreview() {
    PreviewTheme(padding = 16.dp) {
        MarkdownPreview(content = PreviewSamples.MARKDOWN, language = languageFor("notes.md"), title = "notes.md")
    }
}
