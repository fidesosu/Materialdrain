package tools.senko.materialdrain.files

/**
 * Syntax highlighting for text previews. Two parts:
 * - the language list, [SourceLanguages.byExtension]: which file extension is which language. This is the part to
 *   extend by hand when a file type is missing.
 * - automatic: the tokenizer, which finds comments, strings, numbers and keywords for the language it's given, and
 *   the detection of a language from a `#!` line when the extension is not in the list.
 *
 * Everything here is plain Kotlin, so it can be tested on the JVM. The colours are applied by the UI.
 */

/** How a language writes its comments and strings, which decides what the tokenizer looks for. */
enum class SyntaxFamily {
    /** Line comments with two slashes and block comments in slash-star form; double, single and backtick strings: C-like languages, JavaScript, Kotlin, Go... */
    C_LIKE,
    /** # comments: Python, shell, YAML, TOML, INI */
    HASH,
    /** -- comments: SQL, Lua */
    DASH,
    /** Strings and numbers only: JSON, markup, plain config */
    DATA,
    /** No highlighting at all */
    PLAIN,
}

data class SourceLanguage(
    val name: String,
    val family: SyntaxFamily,
    val keywords: Set<String> = emptySet(),
    /** SQL keywords are written in any case */
    val caseInsensitive: Boolean = false,
)

private fun words(text: String) = text.split(' ', '\n').filter { it.isNotBlank() }.toSet()

object SourceLanguages {
    private val kotlin = SourceLanguage("Kotlin", SyntaxFamily.C_LIKE, words("fun val var class object interface return if else when for while do try catch finally import package null true false is in as override private public internal data sealed companion init this super suspend typealias"))
    private val java = SourceLanguage("Java", SyntaxFamily.C_LIKE, words("class public private protected static void int long boolean double float char return if else for while new import package null true false try catch finally final extends implements interface enum this super"))
    private val javascript = SourceLanguage("JavaScript", SyntaxFamily.C_LIKE, words("function const let var return if else for while class import export from default null undefined true false new this async await try catch finally throw typeof"))
    private val typescript = SourceLanguage("TypeScript", SyntaxFamily.C_LIKE, javascript.keywords + words("type interface enum implements readonly as is keyof"))
    private val python = SourceLanguage("Python", SyntaxFamily.HASH, words("def class return if elif else for while import from as with try except finally raise None True False lambda pass yield in is not and or async await"))
    private val shell = SourceLanguage("Shell", SyntaxFamily.HASH, words("if then else elif fi for do done while case esac function return export local echo in"))
    private val c = SourceLanguage("C", SyntaxFamily.C_LIKE, words("int char void return if else for while struct enum typedef include define sizeof static const unsigned long float double break continue switch case default"))
    private val cpp = SourceLanguage("C++", SyntaxFamily.C_LIKE, c.keywords + words("class namespace public private protected template using new delete virtual"))
    private val csharp = SourceLanguage("C#", SyntaxFamily.C_LIKE, words("class public private protected internal static void int string bool return if else for foreach while new using namespace var null true false async await"))
    private val go = SourceLanguage("Go", SyntaxFamily.C_LIKE, words("func var const return if else for range package import struct interface type map go defer nil true false switch case default"))
    private val rust = SourceLanguage("Rust", SyntaxFamily.C_LIKE, words("fn let mut pub struct enum impl use mod match return if else for while loop trait self Self true false const static"))
    private val swift = SourceLanguage("Swift", SyntaxFamily.C_LIKE, words("func let var class struct enum protocol return if else for while import nil true false guard in extension"))
    private val php = SourceLanguage("PHP", SyntaxFamily.C_LIKE, words("function class public private protected return if else for foreach while echo new null true false use namespace"))
    private val ruby = SourceLanguage("Ruby", SyntaxFamily.HASH, words("def end class module if elsif else unless do return require include nil true false yield self"))
    private val sql = SourceLanguage("SQL", SyntaxFamily.DASH, words("select from where insert update delete join left right inner on and or not null create table into values set order by group having limit as distinct drop alter index primary key"), caseInsensitive = true)
    private val lua = SourceLanguage("Lua", SyntaxFamily.DASH, words("local function end if then else elseif for while do return nil true false and or not in repeat until"))
    private val css = SourceLanguage("CSS", SyntaxFamily.C_LIKE, words("important media import"))
    private val json = SourceLanguage("JSON", SyntaxFamily.DATA, words("true false null"))
    private val yaml = SourceLanguage("YAML", SyntaxFamily.HASH, words("true false null yes no"))
    private val toml = SourceLanguage("TOML", SyntaxFamily.HASH, words("true false"))
    private val ini = SourceLanguage("INI", SyntaxFamily.HASH, words("true false"))
    private val markup = SourceLanguage("Markup", SyntaxFamily.DATA)
    private val gradle = SourceLanguage("Gradle", SyntaxFamily.C_LIKE, words("def task plugins dependencies repositories implementation api testImplementation android true false null"))
    private val plain = SourceLanguage("Text", SyntaxFamily.PLAIN)
    private val markdown = SourceLanguage("Markdown", SyntaxFamily.PLAIN)

    /**
     * THE LIST: file extension (without the dot, lowercase) to language. Add a line here to make a type previewable
     * and highlighted. An extension which is not in this list is still previewed when its type is text.
     */
    val byExtension: Map<String, SourceLanguage> = mapOf(
        "kt" to kotlin, "kts" to kotlin,
        "java" to java,
        "js" to javascript, "mjs" to javascript, "cjs" to javascript, "jsx" to javascript,
        "ts" to typescript, "tsx" to typescript,
        "py" to python, "pyw" to python,
        "sh" to shell, "bash" to shell, "zsh" to shell,
        "c" to c, "h" to c,
        "cpp" to cpp, "cc" to cpp, "cxx" to cpp, "hpp" to cpp, "hh" to cpp,
        "cs" to csharp,
        "go" to go,
        "rs" to rust,
        "swift" to swift,
        "php" to php,
        "rb" to ruby,
        "sql" to sql,
        "lua" to lua,
        "css" to css, "scss" to css, "less" to css,
        "json" to json,
        "yml" to yaml, "yaml" to yaml,
        "toml" to toml,
        "ini" to ini, "cfg" to ini, "conf" to ini, "properties" to ini,
        "gradle" to gradle,
        "html" to markup, "htm" to markup, "xhtml" to markup, "xml" to markup, "svg" to markup,
        "md" to markdown, "markdown" to markdown,
        "txt" to plain, "log" to plain, "csv" to plain, "tsv" to plain, "rtf" to plain,
    )

    /** Shebang names (`#!/usr/bin/env python3`) that tell the language when there is no extension to go by. */
    val byShebang: Map<String, SourceLanguage> = mapOf(
        "python" to python, "sh" to shell, "bash" to shell, "zsh" to shell, "node" to javascript, "ruby" to ruby, "php" to php, "lua" to lua,
    )
}

/** The language of a file: from its extension when that's in the list, otherwise from a `#!` first line. */
fun languageFor(fileName: String, firstLine: String? = null): SourceLanguage? {
    val extension = fileName.substringAfterLast('.', "").lowercase()
    if (extension.isNotEmpty() && fileName.contains('.')) {
        SourceLanguages.byExtension[extension]?.let { return it }
    }
    val shebang = firstLine?.takeIf { it.startsWith("#!") } ?: return null
    val interpreter = shebang.substringAfterLast('/').substringAfterLast(' ').trim()
    return SourceLanguages.byShebang.entries.firstOrNull { interpreter.startsWith(it.key) }?.value
}

/** Whether the file is shown as text: it has a known source or text extension, or a text type from the server. */
fun isTextFile(fileName: String, mimeType: String?): Boolean {
    val extension = fileName.substringAfterLast('.', "").lowercase()
    if (extension.isNotEmpty() && SourceLanguages.byExtension.containsKey(extension)) return true
    return mimeType?.let { it.startsWith("text/") || it == "application/json" || it == "application/xml" || it == "application/javascript" } ?: false
}

enum class TokenKind { PLAIN, COMMENT, STRING, NUMBER, KEYWORD }

data class Token(val start: Int, val end: Int, val kind: TokenKind)

/**
 * Splits [code] into tokens. Plain text between the tokens is left as [TokenKind.PLAIN] (the gaps are not listed).
 * It's a plain scan, not a parser: a comment marker inside a string is part of the string, and a string ends at its
 * quote, or at the end of its line. It's done character by character with no regular expressions, so a long input
 * can't overflow the stack.
 */
fun tokenize(code: String, language: SourceLanguage?): List<Token> {
    if (language == null || language.family == SyntaxFamily.PLAIN) return emptyList()
    val tokens = mutableListOf<Token>()
    val length = code.length
    val cLike = language.family == SyntaxFamily.C_LIKE
    val quotes = if (cLike) "\"'`" else "\"'"
    var i = 0
    while (i < length) {
        val c = code[i]

        // Line comments run to the end of the line
        val isLineComment = when (language.family) {
            SyntaxFamily.C_LIKE -> code.startsWith("//", i)
            SyntaxFamily.HASH -> c == '#'
            SyntaxFamily.DASH -> code.startsWith("--", i)
            SyntaxFamily.DATA, SyntaxFamily.PLAIN -> false
        }
        if (isLineComment) {
            val newline = code.indexOf('\n', i)
            val end = if (newline < 0) length else newline
            tokens += Token(i, end, TokenKind.COMMENT)
            i = end
            continue
        }

        // Block comments run to the closing */
        if (cLike && code.startsWith("/*", i)) {
            val close = code.indexOf("*/", i + 2)
            val end = if (close < 0) length else close + 2
            tokens += Token(i, end, TokenKind.COMMENT)
            i = end
            continue
        }

        // Strings: to the matching quote, with backslash escapes; a plain string ends at its line
        if (quotes.indexOf(c) >= 0) {
            var j = i + 1
            while (j < length && code[j] != c) {
                when {
                    code[j] == '\\' -> j += 2
                    code[j] == '\n' && c != '`' -> break
                    else -> j++
                }
            }
            val end = if (j < length && code[j] == c) j + 1 else minOf(j, length)
            tokens += Token(i, end, TokenKind.STRING)
            i = end
            continue
        }

        // Words: keywords are coloured, anything else is plain
        if (c.isLetter() || c == '_') {
            var j = i + 1
            while (j < length && (code[j].isLetterOrDigit() || code[j] == '_')) j++
            val word = code.substring(i, j)
            val known = if (language.caseInsensitive) word.lowercase() in language.keywords else word in language.keywords
            if (known) tokens += Token(i, j, TokenKind.KEYWORD)
            i = j
            continue
        }

        // Numbers, with an optional fraction
        if (c.isDigit()) {
            var j = i + 1
            while (j < length && (code[j].isDigit() || code[j] == '_')) j++
            if (j + 1 < length && code[j] == '.' && code[j + 1].isDigit()) {
                j++
                while (j < length && (code[j].isDigit() || code[j] == '_')) j++
            }
            tokens += Token(i, j, TokenKind.NUMBER)
            i = j
            continue
        }

        i++
    }
    return tokens
}
