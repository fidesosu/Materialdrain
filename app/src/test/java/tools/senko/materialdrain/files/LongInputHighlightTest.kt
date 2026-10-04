package tools.senko.materialdrain.files

import org.junit.Assert.assertTrue
import org.junit.Test

/** A long string literal used to overflow the stack of the regular expression matcher. */
class LongInputHighlightTest {

    @Test
    fun `a very long string literal is tokenized without overflowing`() {
        val longString = "\"" + "x\\\"y".repeat(20_000) + "\""
        val tokens = tokenize("val s = $longString", languageFor("a.kt"))
        assertTrue(tokens.any { it.kind == TokenKind.STRING && it.end - it.start == longString.length })
    }

    @Test
    fun `a very long line of code is tokenized`() {
        val code = "val a = 1 // " + "word ".repeat(20_000)
        val tokens = tokenize(code, languageFor("a.kt"))
        assertTrue(tokens.any { it.kind == TokenKind.COMMENT })
    }
}
