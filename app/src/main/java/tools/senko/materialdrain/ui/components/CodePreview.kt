package tools.senko.materialdrain.ui.components

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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.WrapText
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInFull
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
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tools.senko.materialdrain.files.SourceLanguage
import tools.senko.materialdrain.files.TokenKind
import tools.senko.materialdrain.files.tokenize
import tools.senko.materialdrain.ui.LocalTextWrap
import java.text.NumberFormat

/** How high the preview on the details page gets; the whole file is in the fullscreen view (see [CodeFullscreen]). */
private val InlineMaxHeight = 200.dp

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
private fun codeColors(): CodeColors =
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

private fun lineCount(text: String) = text.count { it == '\n' } + if (text.isEmpty() || text.endsWith('\n')) 0 else 1

/**
 * A text file, coloured for its language (null when it isn't known, then it's shown as it is), in a box of a few lines'
 * height; the fullscreen button opens the whole of it (see [CodeFullscreen]).
 *
 * @param content the start of the file, shown here
 * @param fullContent the whole file, for the fullscreen view; null when [content] is all of it
 */
@Composable
fun CodePreview(
    content: String,
    language: SourceLanguage?,
    modifier: Modifier = Modifier,
    fullContent: String? = null,
    title: String = language?.name ?: "Text"
) {
    val colors = codeColors()
    val wrap = LocalTextWrap.current
    val shown = remember(content) { expandTabs(content) }
    val highlighted = remember(shown, language, colors) { highlightedCode(shown, language, colors) }
    val whole = fullContent ?: content
    val totalLines = remember(whole) { lineCount(whole) }
    var fullscreen by rememberSaveable { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = listOf(language?.name ?: "Text", "${NumberFormat.getIntegerInstance().format(totalLines)} lines").joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { fullscreen = true }) {
                Icon(Icons.Filled.OpenInFull, contentDescription = "Open the text fullscreen", modifier = Modifier.size(20.dp))
            }
        }
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
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
                        .heightIn(max = InlineMaxHeight)
                        .nestedScroll(keepScrollInside)
                        .verticalScroll(textScroll)
                        .then(if (wrap) Modifier else Modifier.horizontalScroll(rememberScrollState()))
                        .padding(12.dp)
                )
            }
        }
        if (fullContent != null && fullContent.length > content.length) {
            Text(
                "Showing the start: ${NumberFormat.getIntegerInstance().format(lineCount(content))} of " +
                    "${NumberFormat.getIntegerInstance().format(totalLines)} lines. Open it fullscreen for the rest.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .clickable { fullscreen = true }
            )
        }
    }

    if (fullscreen) CodeFullscreen(title = title, content = whole, language = language, onDismiss = { fullscreen = false })
}

/**
 * Keeps what's left of a vertical scroll or fling, once [state] is at its end, from reaching the scrolling page around
 * it. Lets it all through when [state] can't scroll at all, so a short text doesn't stop the page.
 */
private class ScrollStopsAtEnds(private val state: ScrollState) : NestedScrollConnection {
    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
        if (state.maxValue > 0) Offset(0f, available.y) else Offset.Zero

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity =
        if (state.maxValue > 0) Velocity(0f, available.y) else Velocity.Zero
}

/**
 * A text file over the whole screen: every line numbered, coloured for its language, wrapped or scrolling sideways (a
 * button switches, starting from the setting), and a button to copy all of it. Long files stay smooth: only the lines
 * on screen are laid out, and the colouring is worked out away from the main thread.
 */
@Composable
fun CodeFullscreen(title: String, content: String, language: SourceLanguage?, onDismiss: () -> Unit) {
    val colors = codeColors()
    val context = LocalContext.current
    var wrap by rememberSaveable { mutableStateOf(false) }
    val text = remember(content) { expandTabs(content) }
    // The whole text coloured, then cut into lines: a comment or string over several lines keeps its colour
    val lines by produceState<List<AnnotatedString>?>(null, text, language, colors) {
        value = withContext(Dispatchers.Default) {
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
            result
        }
    }
    val codeStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurface)
    val gutterStyle = codeStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f), textAlign = TextAlign.End)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        BackHandler(onBack = onDismiss)
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
            Column(modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 4.dp)) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, contentDescription = "Close") }
                    Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOf(language?.name ?: "Text", "${NumberFormat.getIntegerInstance().format(lines?.size ?: lineCount(text))} lines").joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconToggleButton(checked = wrap, onCheckedChange = { wrap = it }) {
                        Icon(Icons.AutoMirrored.Filled.WrapText, contentDescription = if (wrap) "Don't wrap long lines" else "Wrap long lines")
                    }
                    IconButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText(title, content))
                    }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Copy all of it")
                    }
                }

                val shownLines = lines
                if (shownLines == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else {
                    CodeLines(shownLines, wrap, codeStyle, gutterStyle)
                }
            }
        }
    }
}

/** The numbered lines; without [wrap], all of them as wide as the longest, scrolling sideways together. */
@Composable
private fun CodeLines(lines: List<AnnotatedString>, wrap: Boolean, codeStyle: TextStyle, gutterStyle: TextStyle) {
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val charWidth = remember(codeStyle) { measurer.measure("0", codeStyle).size.width }
    val digits = lines.size.toString().length
    val gutterWidth = with(density) { (charWidth * digits).toDp() } + 12.dp
    val longest = remember(lines) { lines.maxOfOrNull { it.length } ?: 0 }
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val gutterColor = MaterialTheme.colorScheme.surfaceContainerHigh

    val list = @Composable { modifier: Modifier ->
        SelectionContainer {
            LazyColumn(modifier = modifier, contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp + bottom)) {
                items(lines.size) { index ->
                    // As high as the line, wrapped or not, so the numbers' strip runs on without gaps
                    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                        // The numbers aren't part of the selection, so copying a selection copies only the code
                        DisableSelection {
                            Text(
                                "${index + 1}",
                                style = gutterStyle,
                                modifier = Modifier
                                    .width(gutterWidth)
                                    .fillMaxHeight()
                                    .background(gutterColor)
                                    .padding(end = 8.dp)
                            )
                        }
                        Text(
                            lines[index],
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
        val width = gutterWidth + with(density) { (charWidth * longest).toDp() } + 22.dp
        Box(modifier = Modifier.fillMaxSize().horizontalScroll(rememberScrollState())) {
            list(Modifier.width(width).fillMaxSize())
        }
    }
}
