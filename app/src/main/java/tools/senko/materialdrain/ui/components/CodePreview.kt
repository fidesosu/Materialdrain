package tools.senko.materialdrain.ui.components

import tools.senko.materialdrain.ui.media.MediaScrim
import tools.senko.materialdrain.ui.theme.AppShapes
import tools.senko.materialdrain.ui.media.MediaCardShape
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import tools.senko.materialdrain.ui.icons.automirrored.filled.WrapText
import tools.senko.materialdrain.ui.icons.filled.Close
import tools.senko.materialdrain.ui.icons.filled.ContentCopy
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.LinearProgressIndicator
import tools.senko.materialdrain.ui.media.FullscreenBadge
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tools.senko.materialdrain.files.SourceLanguage
import tools.senko.materialdrain.files.TokenKind
import tools.senko.materialdrain.files.tokenize
import tools.senko.materialdrain.ui.LocalTextWrap
import java.text.NumberFormat
import tools.senko.materialdrain.ui.preview.PreviewTheme
import tools.senko.materialdrain.ui.preview.ThemePreviews
import tools.senko.materialdrain.ui.preview.ScreenPreviews
import tools.senko.materialdrain.ui.preview.PreviewSamples
import tools.senko.materialdrain.files.languageFor
import androidx.compose.ui.platform.LocalInspectionMode
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.input.ImeAction
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import tools.senko.materialdrain.ui.icons.automirrored.filled.ArrowBack
import tools.senko.materialdrain.ui.icons.filled.Add
import tools.senko.materialdrain.ui.icons.filled.Code
import tools.senko.materialdrain.ui.icons.filled.Description
import tools.senko.materialdrain.ui.icons.filled.ExpandLess
import tools.senko.materialdrain.ui.icons.filled.ExpandMore
import tools.senko.materialdrain.ui.icons.filled.Numbers
import tools.senko.materialdrain.ui.icons.filled.Remove
import tools.senko.materialdrain.ui.icons.filled.Search

/** Tabs as spaces, so columns line up in the monospaced text whatever the tab width would have been. */
private fun expandTabs(text: String) = text.replace("\t", "    ")

/** The text with its tokens coloured. Plain text in between is left as it is. */
fun highlightedCode(code: String, language: SourceLanguage?, colors: CodeColors): AnnotatedString {
    // A failure to match must never take the app down: the text is then shown plain
    val tokens = try {
        tokenize(code, language)
    } catch (e: StackOverflowError) {
        emptyList()
    } catch (e: RuntimeException) {
        emptyList()
    }
    return buildAnnotatedString {
        append(code)
        tokens.forEach { token ->
            colors.styleOf(token.kind)?.let { addStyle(it, token.start, token.end.coerceAtMost(code.length)) }
        }
    }
}

/**
 * The colour of each kind of token. Two sets, made for code on a light and on a dark background (after GitHub's), as
 * the theme's own colours are too few and too close to each other to tell a type from a call from a key.
 */
data class CodeColors(
    val keyword: Color,
    val string: Color,
    val escape: Color,
    val comment: Color,
    val number: Color,
    val type: Color,
    val function: Color,
    val property: Color,
    val annotation: Color,
    val variable: Color,
    val tag: Color,
    val attribute: Color,
    val heading: Color,
    val inserted: Color,
    val deleted: Color,
) {
    fun styleOf(kind: TokenKind): SpanStyle? = when (kind) {
        TokenKind.PLAIN -> null
        TokenKind.KEYWORD -> SpanStyle(color = keyword, fontWeight = FontWeight.SemiBold)
        TokenKind.STRING -> SpanStyle(color = string)
        TokenKind.ESCAPE -> SpanStyle(color = escape, fontWeight = FontWeight.SemiBold)
        TokenKind.COMMENT -> SpanStyle(color = comment, fontStyle = FontStyle.Italic)
        TokenKind.NUMBER, TokenKind.LITERAL -> SpanStyle(color = number)
        TokenKind.TYPE -> SpanStyle(color = type)
        TokenKind.FUNCTION -> SpanStyle(color = function)
        TokenKind.PROPERTY -> SpanStyle(color = property)
        TokenKind.ANNOTATION -> SpanStyle(color = annotation)
        TokenKind.VARIABLE -> SpanStyle(color = variable)
        TokenKind.TAG -> SpanStyle(color = tag)
        TokenKind.ATTRIBUTE -> SpanStyle(color = attribute)
        TokenKind.HEADING -> SpanStyle(color = heading, fontWeight = FontWeight.Bold)
        TokenKind.STRONG -> SpanStyle(fontWeight = FontWeight.Bold)
        TokenKind.EMPHASIS -> SpanStyle(fontStyle = FontStyle.Italic)
        TokenKind.INSERTED -> SpanStyle(color = inserted, background = inserted.copy(alpha = 0.12f))
        TokenKind.DELETED -> SpanStyle(color = deleted, background = deleted.copy(alpha = 0.12f))
    }
}

private val LightCodeColors = CodeColors(
    keyword = Color(0xFFCF222E), string = Color(0xFF0A3069), escape = Color(0xFF0550AE), comment = Color(0xFF6E7781),
    number = Color(0xFF0550AE), type = Color(0xFF953800), function = Color(0xFF8250DF), property = Color(0xFF116329),
    annotation = Color(0xFF8250DF), variable = Color(0xFF953800), tag = Color(0xFF116329), attribute = Color(0xFF0550AE),
    heading = Color(0xFF0550AE), inserted = Color(0xFF116329), deleted = Color(0xFF82071E)
)

private val DarkCodeColors = CodeColors(
    keyword = Color(0xFFFF7B72), string = Color(0xFFA5D6FF), escape = Color(0xFF79C0FF), comment = Color(0xFF8B949E),
    number = Color(0xFF79C0FF), type = Color(0xFFFFA657), function = Color(0xFFD2A8FF), property = Color(0xFF7EE787),
    annotation = Color(0xFFD2A8FF), variable = Color(0xFFFFA657), tag = Color(0xFF7EE787), attribute = Color(0xFF79C0FF),
    heading = Color(0xFF79C0FF), inserted = Color(0xFF7EE787), deleted = Color(0xFFFFA198)
)

/** The set that reads on the code's background, which follows light and dark mode. */
@Composable
internal fun codeColors(): CodeColors =
    if (MaterialTheme.colorScheme.surfaceContainerHighest.luminance() < 0.5f) DarkCodeColors else LightCodeColors

/**
 * Code typed in a text field, coloured as the previews colour it; only the colours change, not the text, so the cursor
 * stays where it is. The last text and its colours are kept, as a field asks again on every change of its own.
 */
private class CodeHighlighting(private val language: SourceLanguage?, private val colors: CodeColors) : VisualTransformation {
    private var last: AnnotatedString? = null
    private var lastHighlighted: AnnotatedString? = null

    override fun filter(text: AnnotatedString): TransformedText {
        val highlighted = lastHighlighted?.takeIf { text == last } ?: highlightedCode(text.text, language, colors)
        last = text
        lastHighlighted = highlighted
        return TransformedText(highlighted, OffsetMapping.Identity)
    }
}

/** A text field's [VisualTransformation] that colours its text as code of [language], see [CodeHighlighting]. */
@Composable
fun rememberCodeHighlighting(language: SourceLanguage?): VisualTransformation {
    val colors = codeColors()
    return remember(language, colors) { CodeHighlighting(language, colors) }
}

internal fun lineCount(text: String) = text.count { it == '\n' } + if (text.isEmpty() || text.endsWith('\n')) 0 else 1

/**
 * A text file, coloured for its language (null when it isn't known, then it's shown as it is), in a card the size of the
 * other previews (a picture, a PDF): 16:9, scrolling inside. Over its bottom corners, as on a PDF's preview, what it is
 * and how long, and the badge that opens the whole of it fullscreen (see [CodeFullscreen]).
 *
 * @param content the start of the file, shown here
 * @param fullContent the whole file, for the fullscreen view; null when [content] is all of it
 * @param outerPadding the space around the card, as around the other previews
 * @param onFullScreen opens the fullscreen view elsewhere (a file's details have one for all their files, see
 *   FullScreenFileDialog); null, the badge opens [CodeFullscreen] itself
 */
@Composable
fun CodePreview(
    content: String,
    language: SourceLanguage?,
    modifier: Modifier = Modifier,
    fullContent: String? = null,
    title: String = language?.name ?: "Text",
    outerPadding: PaddingValues = PaddingValues(vertical = 8.dp),
    onFullScreen: (() -> Unit)? = null
) {
    val colors = codeColors()
    val wrap = LocalTextWrap.current
    val shown = remember(content) { expandTabs(content) }
    val highlighted = remember(shown, language, colors) { highlightedCode(shown, language, colors) }
    val whole = fullContent ?: content
    val totalLines = remember(whole) { lineCount(whole) }
    val shownLines = remember(content) { lineCount(content) }
    val cut = fullContent != null && fullContent.length > content.length
    var fullscreen by rememberSaveable { mutableStateOf(false) }

    CodePreviewFrame(modifier, outerPadding) {
        val textScroll = rememberScrollState()
        // Scrolling the text stops at its ends rather than carrying on into the page around it. Only while there's
        // text to scroll: a short one leaves its drags to the page, as any other part of it
        val keepScrollInside = remember(textScroll) { ScrollStopsAtEnds(textScroll) }
        SelectionContainer {
            Text(
                text = highlighted,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                // Without wrapping the lines keep their length, and the text scrolls sideways
                softWrap = wrap,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(keepScrollInside)
                    .verticalScroll(textScroll)
                    .then(if (wrap) Modifier else Modifier.horizontalScroll(rememberScrollState()))
                    // Room under the last line for the labels over the bottom, so it can be scrolled clear of them
                    .padding(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 44.dp)
            )
        }
        // A file cut short says how much of it is here; the rest is in the fullscreen view
        TextPreviewLabels(
            label = textPreviewLabel(language?.name ?: "Text", shownLines, totalLines, cut),
            onOpen = { onFullScreen?.invoke() ?: run { fullscreen = true } }
        )
    }

    if (fullscreen) CodeFullscreen(title = title, content = whole, language = language, onDismiss = { fullscreen = false })
}

/** What a text's preview says it is: its [kind] and how long it is, or how much of it is shown when it's [cut] short. */
internal fun textPreviewLabel(kind: String, shownLines: Int, totalLines: Int, cut: Boolean): String {
    val count = NumberFormat.getIntegerInstance()
    return listOf(
        kind,
        if (cut) "first ${count.format(shownLines)} of ${count.format(totalLines)} lines" else "${count.format(totalLines)} lines"
    ).joinToString(" · ")
}

/**
 * Over the bottom corners of a text's preview card (see [CodePreviewFrame]), as on a PDF's preview: what it is ([label])
 * and the badge that opens it fullscreen ([onOpen]). Only the badge opens it: the text is scrolled and selected where it is.
 */
@Composable
internal fun BoxScope.TextPreviewLabels(label: String, onOpen: () -> Unit) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        color = Color.White,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .align(Alignment.BottomStart)
            .padding(start = 8.dp, bottom = 8.dp, end = 48.dp)
            .background(MediaScrim.Button, AppShapes.Small)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
    // The same badge as on a picture's or a PDF's preview
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .size(44.dp)
            .clickable(onClickLabel = "Open the text fullscreen", onClick = onOpen)
    ) {
        FullscreenBadge("Open the text fullscreen")
    }
}

/** [CodePreview] while its text loads: the same card, so nothing around it moves when the text comes in. */
@Composable
fun CodePreviewLoading(modifier: Modifier = Modifier, outerPadding: PaddingValues = PaddingValues(vertical = 8.dp)) {
    CodePreviewFrame(modifier, outerPadding) {
        LinearProgressIndicator(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth())
    }
}

/** The card of [CodePreview]: as wide as the page, 16:9 and as round as the other previews, in the code's colour. */
@Composable
internal fun CodePreviewFrame(modifier: Modifier, outerPadding: PaddingValues, content: @Composable BoxScope.() -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = MediaCardShape,
        modifier = modifier.fillMaxWidth().aspectRatio(16f / 9f).padding(outerPadding)
    ) {
        Box(modifier = Modifier.fillMaxSize(), content = content)
    }
}

/**
 * Keeps what's left of a vertical scroll or fling, once [state] is at its end, from reaching the scrolling page around
 * it. Lets it all through when [state] can't scroll at all, so a short text doesn't stop the page.
 */
internal class ScrollStopsAtEnds(private val state: ScrollState) : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
        if (state.maxValue > 0) Offset(0f, available.y) else Offset.Zero

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
        if (state.maxValue > 0) Velocity(0f, available.y) else Velocity.Zero
}

/**
 * A text file over the whole screen in a dialog of its own: see [CodeFullscreenContent]. A file's details open theirs in
 * the fullscreen view of all their files instead (see FullScreenFileDialog).
 */
@Composable
fun CodeFullscreen(title: String, content: String, language: SourceLanguage?, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        BackHandler(onBack = onDismiss)
        CodeFullscreenContent(title = title, content = content, language = language, onDismiss = onDismiss)
    }
}

/**
 * How big the fullscreen text is (of the theme's size) and whether its lines are numbered: the same for every text opened
 * while the app runs, as set in the last one's menu.
 */
private object CodeViewLook {
    var textScale by mutableFloatStateOf(1f)
    var lineNumbers by mutableStateOf(true)
}

/** The sizes the fullscreen text steps through, of the theme's. */
private val CodeTextScales = listOf(0.7f, 0.85f, 1f, 1.15f, 1.3f, 1.5f, 1.75f, 2f)

/** Past this many characters a text is shown without its colours: working them out for all of it would take too long. */
private const val MAX_COLOURED_LENGTH = 2_000_000

/** The most matches a search finds; the count says when there are more. */
private const val MAX_MATCHES = 10_000

/** A match of the search in the fullscreen text: on [line], from [start] to [end] in it. */
private class TextMatch(val line: Int, val start: Int, val end: Int)

/**
 * A text file over the whole screen: every line numbered, coloured for its language, wrapped or scrolling sideways. The
 * bar at the top finds words in it (each match lit, stepped through with the arrows); its menu wraps the lines or not
 * (starting from the setting), numbers them or not, makes the text bigger or smaller, and copies all of it. A long press
 * on a line's number starts picking lines, a tap on another one picks the lines up to it, to copy just those. Long files
 * stay smooth: only the lines on screen are laid out, and the colouring is worked out away from the main thread.
 *
 * @param content the whole text; null while it loads, or with [message] saying why it isn't there
 * @param onLoadAnyway loads a text that was too big for the preview, when it can be (see FileInfoViewModel.loadWholeText)
 */
@Composable
fun CodeFullscreenContent(
    title: String,
    content: String?,
    language: SourceLanguage?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    message: String? = null,
    onLoadAnyway: (() -> Unit)? = null
) {
    val colors = codeColors()
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val wrapSetting = LocalTextWrap.current
    var wrap by rememberSaveable { mutableStateOf(wrapSetting) }
    val text = remember(content) { content?.let(::expandTabs) }
    // Worked out away from the main thread; a preview (see the end of the file) has them at once, as it shows one frame
    val inPreview = LocalInspectionMode.current
    val lines by produceState(if (inPreview && text != null) codeLines(text, language, colors) else null, text, language, colors) {
        value = text?.let { withContext(Dispatchers.Default) { codeLines(it, language, colors) } }
    }
    // The lines as they are in the file, tabs and all, for copying
    val fileLines = remember(content) { content?.split('\n') }

    /** Copies [copied], and says so where the system doesn't (before Android 13). */
    fun copy(label: String, copied: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, copied))
        if (Build.VERSION.SDK_INT < 33) Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
    }

    // ---- Finding ----
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val matches by produceState(emptyList<TextMatch>(), lines, query) {
        val shown = lines
        value = if (shown == null || query.isEmpty()) emptyList() else withContext(Dispatchers.Default) { findMatches(shown, query) }
    }
    var current by remember { mutableIntStateOf(0) }
    val listState = rememberLazyListState()
    val sideways = rememberScrollState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val baseStyle = MaterialTheme.typography.bodySmall
    val scale = CodeViewLook.textScale
    val codeStyle = baseStyle.copy(
        fontFamily = FontFamily.Monospace,
        color = scheme.onSurface,
        fontSize = baseStyle.fontSize * scale,
        lineHeight = baseStyle.lineHeight * scale
    )
    val measurer = rememberTextMeasurer()
    val charWidth = remember(codeStyle) { measurer.measure("0", codeStyle).size.width }
    var viewportWidth by remember { mutableIntStateOf(0) }

    /** Brings [match] into view: its line a little below the top, and (without wrapping) its words on the screen. */
    fun reveal(match: TextMatch) {
        scope.launch {
            listState.animateScrollToItem((match.line - 3).coerceAtLeast(0))
            if (!wrap) {
                val gutter = if (CodeViewLook.lineNumbers) gutterWidthPx(lines?.size ?: 0, charWidth, density) else 0
                val x = gutter + match.start * charWidth - viewportWidth / 3
                sideways.animateScrollTo(x.coerceAtLeast(0))
            }
        }
    }

    fun step(forward: Boolean) {
        if (matches.isEmpty()) return
        current = (current + if (forward) 1 else matches.size - 1) % matches.size
        reveal(matches[current])
    }
    // New matches start from the first one at or below the top of the screen
    LaunchedEffect(matches) {
        if (matches.isEmpty()) return@LaunchedEffect
        val top = listState.firstVisibleItemIndex
        current = matches.indexOfFirst { it.line >= top }.takeIf { it >= 0 } ?: 0
        reveal(matches[current])
    }
    val focus = remember { FocusRequester() }
    LaunchedEffect(searching) { if (searching) focus.requestFocus() }
    BackHandler(enabled = searching) {
        searching = false
        query = ""
    }

    // ---- Picking lines ----
    var rangeStart by remember { mutableStateOf<Int?>(null) }
    var rangeEnd by remember { mutableStateOf<Int?>(null) }
    val range = rangeStart?.let { start -> rangeEnd?.let { end -> minOf(start, end)..maxOf(start, end) } }
    BackHandler(enabled = range != null) {
        rangeStart = null
        rangeEnd = null
    }

    // ---- Markdown: as it's meant to look (see MarkdownDocument), or its source ----
    val markdown = isMarkdown(language)
    var rendered by rememberSaveable { mutableStateOf(markdown) }
    val renderedState = rememberLazyListState()

    Surface(modifier = modifier.fillMaxSize(), color = scheme.surfaceContainerHighest) {
        Column(modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 4.dp)) {
                // Back, as on the other fullscreen views (a picture's, a PDF's, a video's)
                IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                    Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val count = lines?.size ?: text?.let(::lineCount)
                    Text(
                        listOfNotNull(language?.name ?: "Text", count?.let { "${NumberFormat.getIntegerInstance().format(it)} lines" }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }
                if (lines != null) {
                    if (markdown) {
                        IconButton(onClick = {
                            rendered = !rendered
                            rangeStart = null
                            rangeEnd = null
                        }) {
                            Icon(
                                if (rendered) Icons.Filled.Code else Icons.Filled.Description,
                                contentDescription = if (rendered) "Show the source" else "Show it as it looks"
                            )
                        }
                    }
                    // Finding is in the source, which it switches to
                    IconToggleButton(checked = searching, onCheckedChange = { searching = it; if (it) rendered = false else query = "" }) {
                        Icon(Icons.Filled.Search, contentDescription = if (searching) "Stop finding" else "Find in the text")
                    }
                    OverflowMenuButton(contentDescription = "More options", buttonSize = 48.dp) { close ->
                        if (!rendered) {
                            AppMenuItem("Wrap long lines", leadingIcon = Icons.AutoMirrored.Filled.WrapText, active = wrap, onClick = { wrap = !wrap })
                            AppMenuItem(
                                "Line numbers",
                                leadingIcon = Icons.Filled.Numbers,
                                active = CodeViewLook.lineNumbers,
                                onClick = { CodeViewLook.lineNumbers = !CodeViewLook.lineNumbers }
                            )
                        }
                        val sizeAt = CodeTextScales.indexOfFirst { it >= scale - 0.01f }.coerceAtLeast(0)
                        AppMenuItem(
                            "Bigger text",
                            leadingIcon = Icons.Filled.Add,
                            trailingText = "${(scale * 100).roundToInt()}%",
                            enabled = sizeAt < CodeTextScales.lastIndex,
                            onClick = { CodeViewLook.textScale = CodeTextScales[(sizeAt + 1).coerceAtMost(CodeTextScales.lastIndex)] }
                        )
                        AppMenuItem(
                            "Smaller text",
                            leadingIcon = Icons.Filled.Remove,
                            enabled = sizeAt > 0,
                            onClick = { CodeViewLook.textScale = CodeTextScales[(sizeAt - 1).coerceAtLeast(0)] }
                        )
                        AppMenuDivider()
                        AppMenuItem("Copy all of it", leadingIcon = Icons.Filled.ContentCopy, onClick = {
                            close()
                            content?.let { copy(title, it) }
                        })
                    }
                }
            }
            if (searching) {
                FindBar(
                    query = query,
                    onQueryChange = { query = it },
                    found = matches.size,
                    current = current,
                    onStep = ::step,
                    onClose = { searching = false; query = "" },
                    focus = focus
                )
            }

            Box(modifier = Modifier.fillMaxSize().onSizeChanged { viewportWidth = it.width }) {
                val shownLines = lines
                when {
                    rendered && content != null -> {
                        MarkdownDocument(content, renderedState, scale)
                        DotScrollbar(
                            state = renderedState,
                            modifier = Modifier.padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
                        )
                    }
                    shownLines != null -> {
                        val byLine = remember(matches) { matches.withIndex().groupBy { it.value.line } }
                        CodeLines(
                            lines = shownLines,
                            wrap = wrap,
                            codeStyle = codeStyle,
                            charWidth = charWidth,
                            numbered = CodeViewLook.lineNumbers,
                            listState = listState,
                            sideways = sideways,
                            matches = byLine,
                            current = current.takeIf { matches.isNotEmpty() },
                            range = range,
                            onNumberLongPress = { line ->
                                rangeStart = line
                                rangeEnd = line
                            },
                            onNumberTap = { line -> if (rangeStart != null) rangeEnd = line }
                        )
                        DotScrollbar(
                            state = listState,
                            modifier = Modifier.padding(bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())
                        )
                    }
                    text == null && message != null -> Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.align(Alignment.Center).padding(32.dp)
                    ) {
                        Text(message, style = MaterialTheme.typography.bodyMedium, color = scheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        onLoadAnyway?.let { TextButton(onClick = it, modifier = Modifier.padding(top = 8.dp)) { Text("Load anyway") } }
                    }
                    // Loading, or being coloured
                    else -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                // The lines picked: how many, and copying them
                if (range != null && fileLines != null) {
                    Surface(
                        shape = AppShapes.ExtraLarge,
                        color = scheme.surfaceContainer,
                        shadowElevation = 4.dp,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 20.dp, end = 4.dp)) {
                            val first = range.first + 1
                            val last = range.last + 1
                            Text(
                                if (first == last) "Line $first" else "Lines $first–$last",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Spacer(Modifier.width(8.dp))
                            TextButton(onClick = {
                                val picked = fileLines.subList(range.first, (range.last + 1).coerceAtMost(fileLines.size))
                                copy(if (first == last) "Line $first" else "Lines $first–$last", picked.joinToString("\n"))
                                rangeStart = null
                                rangeEnd = null
                            }) { Text("Copy") }
                            IconButton(onClick = { rangeStart = null; rangeEnd = null }) {
                                Icon(Icons.Filled.Close, contentDescription = "Stop picking lines")
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The search field of [CodeFullscreenContent]: the words, how many matches there are and which one is lit ([current] of
 * [found]), the arrows to the one before and after ([onStep]), and closing it. Enter goes to the next match.
 */
@Composable
private fun FindBar(
    query: String,
    onQueryChange: (String) -> Unit,
    found: Int,
    current: Int,
    onStep: (forward: Boolean) -> Unit,
    onClose: () -> Unit,
    focus: FocusRequester
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = AppShapes.ExtraLarge,
        color = scheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(48.dp).padding(start = 16.dp, end = 4.dp)) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
                cursorBrush = SolidColor(scheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onStep(true) }),
                modifier = Modifier.weight(1f).focusRequester(focus),
                decorationBox = { field ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) Text("Find in the text", style = MaterialTheme.typography.bodyLarge, color = scheme.onSurfaceVariant)
                        field()
                    }
                }
            )
            if (query.isNotEmpty()) {
                val count = NumberFormat.getIntegerInstance()
                Text(
                    when {
                        found == 0 -> "None"
                        found >= MAX_MATCHES -> "${count.format(current + 1)} of ${count.format(found)}+"
                        else -> "${count.format(current + 1)} of ${count.format(found)}"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
            IconButton(onClick = { onStep(false) }, enabled = found > 0) { Icon(Icons.Filled.ExpandLess, contentDescription = "Match before") }
            IconButton(onClick = { onStep(true) }, enabled = found > 0) { Icon(Icons.Filled.ExpandMore, contentDescription = "Next match") }
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, contentDescription = "Stop finding") }
        }
    }
}

/** Where [query] is in [lines], whatever its case: up to [MAX_MATCHES] of them, in order. */
private fun findMatches(lines: List<AnnotatedString>, query: String): List<TextMatch> {
    val found = ArrayList<TextMatch>()
    lines.forEachIndexed { index, line ->
        var at = line.text.indexOf(query, ignoreCase = true)
        while (at >= 0) {
            found += TextMatch(index, at, at + query.length)
            if (found.size >= MAX_MATCHES) return found
            at = line.text.indexOf(query, at + query.length, ignoreCase = true)
        }
    }
    return found
}

/** The lines of [text], coloured for [language] unless it's too long for that (see [MAX_COLOURED_LENGTH]). */
private fun codeLines(text: String, language: SourceLanguage?, colors: CodeColors): List<AnnotatedString> =
    if (text.length > MAX_COLOURED_LENGTH) {
        text.split('\n').let { if (it.size > 1 && text.endsWith('\n')) it.dropLast(1) else it }.map(::AnnotatedString)
    } else {
        highlightedLines(text, language, colors)
    }

/** The whole [text] coloured, then cut into lines: a comment or string over several lines keeps its colour. */
private fun highlightedLines(text: String, language: SourceLanguage?, colors: CodeColors): List<AnnotatedString> {
    val highlighted = highlightedCode(text, language, colors)
    val result = ArrayList<AnnotatedString>()
    var start = 0
    while (start <= text.length) {
        val end = text.indexOf('\n', start).let { if (it < 0) text.length else it }
        result += highlighted.subSequence(start, end)
        if (end == text.length) break
        start = end + 1
    }
    if (result.size > 1 && text.endsWith('\n')) result.removeAt(result.lastIndex)
    return result
}

/** How wide the strip of line numbers is for [lineCount] lines, of characters [charWidth] wide. */
private fun gutterWidthPx(lineCount: Int, charWidth: Int, density: androidx.compose.ui.unit.Density): Int =
    charWidth * lineCount.toString().length + with(density) { 12.dp.roundToPx() }

/**
 * The lines, numbered when [numbered]; without [wrap], all of them as wide as the longest, scrolling sideways together
 * ([sideways]). The [matches] of a search are lit on their lines, the [current] one more; the lines of [range] are lit
 * too. A long press on a number starts picking lines ([onNumberLongPress]), a tap on one picks up to it ([onNumberTap]).
 */
@Composable
private fun CodeLines(
    lines: List<AnnotatedString>,
    wrap: Boolean,
    codeStyle: TextStyle,
    charWidth: Int,
    numbered: Boolean,
    listState: LazyListState,
    sideways: ScrollState,
    matches: Map<Int, List<IndexedValue<TextMatch>>>,
    current: Int?,
    range: IntRange?,
    onNumberLongPress: (Int) -> Unit,
    onNumberTap: (Int) -> Unit
) {
    val density = LocalDensity.current
    val scheme = MaterialTheme.colorScheme
    val gutterStyle = codeStyle.copy(color = scheme.onSurfaceVariant.copy(alpha = 0.6f), textAlign = TextAlign.End)
    val gutterWidth = with(density) { gutterWidthPx(lines.size, charWidth, density).toDp() }
    val longest = remember(lines) { lines.maxOfOrNull { it.length } ?: 0 }
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val gutterColor = scheme.surfaceContainerHigh
    val matchColor = scheme.primary.copy(alpha = 0.25f)
    val currentColor = scheme.primary.copy(alpha = 0.6f)
    val pickedColor = scheme.primary.copy(alpha = 0.12f)

    val list = @Composable { modifier: Modifier ->
        SelectionContainer {
            LazyColumn(state = listState, modifier = modifier, contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp + bottom)) {
                items(lines.size) { index ->
                    val line = lines[index]
                    val lineMatches = matches[index]
                    val shown = if (lineMatches == null) line else remember(line, lineMatches, current) {
                        buildAnnotatedString {
                            append(line)
                            lineMatches.forEach { (number, match) ->
                                addStyle(SpanStyle(background = if (number == current) currentColor else matchColor), match.start, match.end.coerceAtMost(line.length))
                            }
                        }
                    }
                    // As high as the line, wrapped or not, so the numbers' strip runs on without gaps
                    Row(
                        modifier = Modifier
                            .height(IntrinsicSize.Min)
                            .then(if (range != null && index in range) Modifier.background(pickedColor) else Modifier)
                    ) {
                        if (numbered) {
                            // The numbers aren't part of the selection, so copying a selection copies only the code
                            DisableSelection {
                                Text(
                                    "${index + 1}",
                                    style = gutterStyle,
                                    modifier = Modifier
                                        .width(gutterWidth)
                                        .fillMaxHeight()
                                        .background(gutterColor)
                                        .combinedClickable(
                                            onClickLabel = "Pick the lines up to this one",
                                            onLongClickLabel = "Start picking lines from this one",
                                            onLongClick = { onNumberLongPress(index) },
                                            onClick = { onNumberTap(index) }
                                        )
                                        .padding(end = 8.dp)
                                )
                            }
                        }
                        Text(
                            shown,
                            style = codeStyle,
                            softWrap = wrap,
                            modifier = Modifier.padding(start = 10.dp, end = 12.dp).then(if (wrap) Modifier.weight(1f) else Modifier)
                        )
                    }
                }
            }
        }
    }

    if (wrap) {
        list(Modifier.fillMaxSize())
    } else {
        // As wide as the longest line, inside one sideways scroll, so every line moves together and the numbers with them
        val width = (if (numbered) gutterWidth else 0.dp) + with(density) { (charWidth * longest).toDp() } + 22.dp
        Box(modifier = Modifier.fillMaxSize().horizontalScroll(sideways)) {
            list(Modifier.width(width).fillMaxSize())
        }
    }
}


// ---- Previews ----

@ThemePreviews
@Composable
private fun CodePreviewPreview() {
    PreviewTheme(padding = 16.dp) {
        CodePreview(content = PreviewSamples.CODE, language = languageFor(PreviewSamples.text.name), title = PreviewSamples.text.name)
    }
}

/** Cut short: only the start is shown, its label says how much of it that is. */
@ThemePreviews
@Composable
private fun CodePreviewCutPreview() {
    PreviewTheme(padding = 16.dp) {
        val whole = (1..400).joinToString("\n") { "val line$it = \"$it\" // a comment" }
        CodePreview(content = whole.lines().take(30).joinToString("\n"), language = languageFor("lines.kt"), fullContent = whole)
    }
}

/** Loading: the same card, so nothing moves once the text is in. */
@ThemePreviews
@Composable
private fun CodePreviewLoadingPreview() {
    PreviewTheme(padding = 16.dp) { CodePreviewLoading() }
}

/** Code fullscreen: numbered, coloured lines. */
@ScreenPreviews
@Composable
private fun CodeFullscreenPreview() {
    PreviewTheme {
        val code = (1..60).joinToString("\n") { "val line$it = \"$it\" // a comment" }
        CodeFullscreen(title = "Lines.kt", content = code, language = languageFor("Lines.kt"), onDismiss = {})
    }
}

/** Markdown fullscreen: as it's meant to look, with the button to its source. */
@ScreenPreviews
@Composable
private fun CodeFullscreenMarkdownPreview() {
    PreviewTheme {
        CodeFullscreen(title = PreviewSamples.text.name, content = PreviewSamples.MARKDOWN, language = languageFor(PreviewSamples.text.name), onDismiss = {})
    }
}

/** A text too big for the preview, which can be loaded anyway. */
@ScreenPreviews
@Composable
private fun CodeFullscreenTooBigPreview() {
    PreviewTheme {
        CodeFullscreenContent(
            title = "server.log",
            content = null,
            language = null,
            onDismiss = {},
            message = "File is too large (3.2 MB) for text preview. Max 1 MB.",
            onLoadAnyway = {}
        )
    }
}
