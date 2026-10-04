package tools.senko.materialdrain.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import tools.senko.materialdrain.files.SourceLanguage
import tools.senko.materialdrain.files.TokenKind
import tools.senko.materialdrain.files.tokenize
import tools.senko.materialdrain.ui.LocalTextWrap

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
            val style = when (token.kind) {
                TokenKind.KEYWORD -> SpanStyle(color = colors.keyword, fontWeight = FontWeight.SemiBold)
                TokenKind.STRING -> SpanStyle(color = colors.string)
                TokenKind.COMMENT -> SpanStyle(color = colors.comment, fontStyle = FontStyle.Italic)
                TokenKind.NUMBER -> SpanStyle(color = colors.number)
                TokenKind.PLAIN -> null
            }
            style?.let { addStyle(it, token.start, token.end) }
        }
    }
}

/** The colour of each kind of token, given by the theme so the code follows light and dark mode. */
data class CodeColors(val keyword: Color, val string: Color, val comment: Color, val number: Color)

@Composable
private fun codeColors(): CodeColors {
    val scheme = MaterialTheme.colorScheme
    return CodeColors(
        keyword = scheme.primary,
        string = scheme.tertiary,
        comment = scheme.onSurfaceVariant,
        number = scheme.secondary,
    )
}

/**
 * A text file, with its language's keywords, strings, comments and numbers coloured. [language] is null when the file's
 * language isn't known, then the text is shown as it is.
 */
@Composable
fun CodePreview(content: String, language: SourceLanguage?, modifier: Modifier = Modifier) {
    val colors = codeColors()
    val wrap = LocalTextWrap.current
    val highlighted = remember(content, language, colors) { highlightedCode(content, language, colors) }
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = language?.name ?: "Text",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            SelectionContainer {
                Text(
                    text = highlighted,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    // Without wrapping the lines keep their length, and the text scrolls sideways
                    softWrap = wrap,
                    modifier = Modifier
                        .padding(12.dp)
                        .then(if (wrap) Modifier else Modifier.horizontalScroll(rememberScrollState()))
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                )
            }
        }
    }
}
