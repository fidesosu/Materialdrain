package tools.senko.materialdrain.files

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** The finer kinds of token: keys, types, calls, annotations, filled-in strings, markup, Markdown, diffs and logs. */
class RichHighlightingTest {

    private fun kindsOf(code: String, file: String) =
        tokenize(code, languageFor(file)).map { code.substring(it.start, it.end) to it.kind }

    @Test
    fun `kotlin types, calls, annotations and templates`() {
        val code = "@Composable\nfun greet(name: String) {\n    println(\"Hi \$name, \${name.length}\\n\")\n}"
        val tokens = kindsOf(code, "a.kt")
        assertTrue(tokens.contains("@Composable" to TokenKind.ANNOTATION))
        assertTrue(tokens.contains("greet" to TokenKind.FUNCTION))
        assertTrue(tokens.contains("String" to TokenKind.TYPE))
        assertTrue(tokens.contains("println" to TokenKind.FUNCTION))
        assertTrue(tokens.contains("\$name" to TokenKind.ESCAPE))
        assertTrue(tokens.contains("\${name.length}" to TokenKind.ESCAPE))
        assertTrue(tokens.contains("\\n" to TokenKind.ESCAPE))
    }

    @Test
    fun `constants and values are literals`() {
        val tokens = kindsOf("val x = MAX_SIZE ?: null", "a.kt")
        assertTrue(tokens.contains("MAX_SIZE" to TokenKind.LITERAL))
        assertTrue(tokens.contains("null" to TokenKind.LITERAL))
    }

    @Test
    fun `python triple-quoted strings run over lines`() {
        val code = "x = \"\"\"one\ntwo\"\"\"\n# done"
        val tokens = kindsOf(code, "a.py")
        assertTrue(tokens.contains("\"\"\"one\ntwo\"\"\"" to TokenKind.STRING))
        assertTrue(tokens.contains("# done" to TokenKind.COMMENT))
    }

    @Test
    fun `json keys differ from string values`() {
        val tokens = kindsOf("{\"name\": \"x\", \"n\": -1.5e3, \"ok\": true}", "a.json")
        assertTrue(tokens.contains("\"name\"" to TokenKind.PROPERTY))
        assertTrue(tokens.contains("\"x\"" to TokenKind.STRING))
        assertTrue(tokens.contains("-1.5e3" to TokenKind.NUMBER))
        assertTrue(tokens.contains("true" to TokenKind.LITERAL))
    }

    @Test
    fun `yaml and ini keys, sections and comments`() {
        val yaml = kindsOf("server:\n  - port: 8080 # main\n  url: \"https://x\"", "a.yml")
        assertTrue(yaml.contains("server" to TokenKind.PROPERTY))
        assertTrue(yaml.contains("port" to TokenKind.PROPERTY))
        assertTrue(yaml.contains("8080" to TokenKind.NUMBER))
        assertTrue(yaml.contains("# main" to TokenKind.COMMENT))
        assertTrue(yaml.contains("\"https://x\"" to TokenKind.STRING))

        val ini = kindsOf("[core]\neditor = vim\n; note", "a.ini")
        assertTrue(ini.contains("[core]" to TokenKind.TYPE))
        assertTrue(ini.contains("editor" to TokenKind.PROPERTY))
        assertTrue(ini.contains("; note" to TokenKind.COMMENT))
    }

    @Test
    fun `markup tags, attributes and entities`() {
        val tokens = kindsOf("<!-- hi --><a href=\"/x\">&amp;</a>", "a.html")
        assertTrue(tokens.contains("<!-- hi -->" to TokenKind.COMMENT))
        assertTrue(tokens.contains("a" to TokenKind.TAG))
        assertTrue(tokens.contains("href" to TokenKind.ATTRIBUTE))
        assertTrue(tokens.contains("\"/x\"" to TokenKind.STRING))
        assertTrue(tokens.contains("&amp;" to TokenKind.ESCAPE))
    }

    @Test
    fun `css properties, selectors and colours`() {
        val tokens = kindsOf(".title { color: #fff; margin: 4px; }", "a.css")
        assertTrue(tokens.contains(".title" to TokenKind.TYPE))
        assertTrue(tokens.contains("color" to TokenKind.PROPERTY))
        assertTrue(tokens.contains("#fff" to TokenKind.NUMBER))
        assertTrue(tokens.contains("4px" to TokenKind.NUMBER))
    }

    @Test
    fun `markdown headings, code, links and lists`() {
        val tokens = kindsOf("# Title\n- see `code` and [docs](https://x)\n**bold**", "a.md")
        assertTrue(tokens.contains("# Title" to TokenKind.HEADING))
        assertTrue(tokens.contains("-" to TokenKind.KEYWORD))
        assertTrue(tokens.contains("`code`" to TokenKind.STRING))
        assertTrue(tokens.contains("[docs]" to TokenKind.FUNCTION))
        assertTrue(tokens.contains("**bold**" to TokenKind.STRONG))
    }

    @Test
    fun `shell variables, also inside strings`() {
        val tokens = kindsOf("echo \"\$HOME\" \$1 # x", "a.sh")
        assertTrue(tokens.contains("\$HOME" to TokenKind.ESCAPE))
        assertTrue(tokens.contains("\$1" to TokenKind.VARIABLE))
        assertTrue(tokens.contains("# x" to TokenKind.COMMENT))
    }

    @Test
    fun `diff lines and log levels`() {
        val diff = kindsOf("@@ -1 +1 @@\n-old\n+new", "a.diff")
        assertTrue(diff.contains("-old" to TokenKind.DELETED))
        assertTrue(diff.contains("+new" to TokenKind.INSERTED))
        val log = kindsOf("2024-01-02 10:11:12 ERROR failed\nINFO ok", "a.log")
        assertTrue(log.contains("2024-01-02" to TokenKind.NUMBER))
        assertTrue(log.contains("ERROR" to TokenKind.DELETED))
        assertTrue(log.contains("INFO" to TokenKind.FUNCTION))
    }

    @Test
    fun `odd and unfinished input ends, with tokens inside the text`() {
        val pieces = listOf("\"", "'", "`", "\"\"\"", "/*", "//", "#", "--", "<", "<!--", "&", "\$", "\${", "{", "}", "[", "]", ":", "=",
            "@", "**", "*", "_", "```", "\\", "\n", " ", "x", "Foo", "1", "0x", "1e", "-", ".", "(", "@@", "+", ";")
        val random = Random(42)
        val files = listOf("a.kt", "a.py", "a.sh", "a.sql", "a.lua", "a.json", "a.yml", "a.ini", "a.css", "a.html", "a.md", "a.diff", "a.log", "a.rs", "a.php", "a.rb")
        repeat(300) {
            val code = buildString { repeat(random.nextInt(1, 60)) { append(pieces[random.nextInt(pieces.size)]) } }
            files.forEach { file ->
                val tokens = tokenize(code, languageFor(file))
                assertFalse("$file: $code", tokens.any { it.start < 0 || it.end > code.length || it.start >= it.end })
            }
        }
    }
}
