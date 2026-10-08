package tools.senko.materialdrain.files

/**
 * Syntax highlighting for text previews. Two parts:
 * - the language list, [SourceLanguages.byExtension]: which file extension is which language. This is the part to
 *   extend by hand when a file type is missing.
 * - automatic: the tokenizer, which finds comments, strings, numbers, keywords, types, calls, keys and the like for the
 *   language it's given, and the detection of a language from a `#!` line when the extension is not in the list.
 *
 * Everything here is plain Kotlin, so it can be tested on the JVM. The colours are applied by the UI.
 */

/** How a language is written, which decides what the tokenizer looks for. */
enum class SyntaxFamily {
    /** Line comments with two slashes and block comments in slash-star form; double, single and backtick strings: C-like languages, JavaScript, Kotlin, Go... */
    C_LIKE,
    /** # comments: Python, shell, Ruby, PowerShell */
    HASH,
    /** -- comments: SQL, Lua */
    DASH,
    /** JSON: keys, strings, numbers and true/false/null */
    DATA,
    /** YAML, TOML, INI and properties: keys before `:` or `=`, `[section]` headers, # comments */
    CONFIG,
    /** Style sheets: selectors, properties, values */
    CSS,
    /** HTML, XML, SVG: tags, attributes, entities, comments */
    MARKUP,
    /** Headings, emphasis, code, links, lists and quotes */
    MARKDOWN,
    /** Added and removed lines of a diff or patch */
    DIFF,
    /** Log files: the levels (ERROR, WARN...), times and numbers */
    LOG,
    /** No highlighting at all */
    PLAIN,
}

/**
 * A language the previews can highlight.
 *
 * @param literals words which are values rather than keywords (true, null...); coloured like numbers
 * @param types capitalized words are types, and ALL_CAPS ones constants
 * @param templates `$name` and `${...}` inside double-quoted (and backtick) strings are filled in, as in Kotlin or shell
 * @param variables `$name` outside strings is a variable, as in shell or PHP
 * @param tripleQuotes `"""` strings, which can run over several lines
 * @param annotations `@Name` is an annotation (or a decorator, in Python)
 */
data class SourceLanguage(
    val name: String,
    val family: SyntaxFamily,
    val keywords: Set<String> = emptySet(),
    /** SQL keywords are written in any case */
    val caseInsensitive: Boolean = false,
    val literals: Set<String> = setOf("true", "false", "null"),
    val types: Boolean = false,
    val templates: Boolean = false,
    val variables: Boolean = false,
    val tripleQuotes: Boolean = false,
    val annotations: Boolean = false,
)

private fun words(text: String) = text.split(' ', '\n').filter { it.isNotBlank() }.toSet()

object SourceLanguages {
    private val kotlin = SourceLanguage(
        "Kotlin", SyntaxFamily.C_LIKE,
        words(
            "fun val var class object interface return if else when for while do try catch finally throw import package is in as " +
                "override private public protected internal data sealed companion init this super suspend typealias enum annotation " +
                "open abstract final lateinit const inline reified vararg operator infix tailrec external break continue where by " +
                "get set constructor out value"
        ),
        types = true, templates = true, tripleQuotes = true, annotations = true
    )
    private val java = SourceLanguage(
        "Java", SyntaxFamily.C_LIKE,
        words(
            "class public private protected static void int long short byte boolean double float char return if else for while do " +
                "switch case default break continue new import package try catch finally throw throws final abstract extends " +
                "implements interface enum this super synchronized volatile transient instanceof record var yield sealed permits"
        ),
        types = true, annotations = true
    )
    private val javascript = SourceLanguage(
        "JavaScript", SyntaxFamily.C_LIKE,
        words(
            "function const let var return if else for while do switch case default break continue class extends import export " +
                "from default new this super async await try catch finally throw typeof instanceof in of delete void yield static get set"
        ),
        literals = setOf("true", "false", "null", "undefined", "NaN", "Infinity"),
        types = true, templates = true, annotations = true
    )
    private val typescript = javascript.copy(name = "TypeScript", keywords = javascript.keywords + words("type interface enum implements readonly as is keyof declare namespace abstract private public protected"))
    private val python = SourceLanguage(
        "Python", SyntaxFamily.HASH,
        words("def class return if elif else for while import from as with try except finally raise lambda pass yield in is not and or async await global nonlocal del assert break continue match case"),
        literals = setOf("True", "False", "None", "self", "cls"),
        types = true, tripleQuotes = true, annotations = true
    )
    private val shell = SourceLanguage(
        "Shell", SyntaxFamily.HASH,
        words("if then else elif fi for do done while until case esac function return export local in select break continue readonly declare unset shift source exit"),
        literals = setOf("true", "false"),
        templates = true, variables = true
    )
    private val powershell = SourceLanguage(
        "PowerShell", SyntaxFamily.HASH,
        words("function param if elseif else foreach for while do switch return try catch finally throw begin process end in"),
        literals = setOf("\$true", "\$false", "\$null"),
        templates = true, variables = true
    )
    private val c = SourceLanguage(
        "C", SyntaxFamily.C_LIKE,
        words("int char void short long float double signed unsigned return if else for while do struct union enum typedef sizeof static const extern volatile register inline break continue switch case default goto"),
        literals = setOf("NULL", "true", "false"),
        types = true
    )
    private val cpp = c.copy(name = "C++", keywords = c.keywords + words("class namespace public private protected template typename using new delete virtual override final this auto bool constexpr noexcept nullptr operator friend explicit mutable"))
    private val csharp = SourceLanguage(
        "C#", SyntaxFamily.C_LIKE,
        words("class struct record interface enum public private protected internal static readonly const void int long string bool double float decimal object var return if else for foreach while do switch case default break continue new using namespace async await try catch finally throw this base override virtual abstract sealed get set in out ref is as"),
        types = true, templates = true
    )
    private val go = SourceLanguage(
        "Go", SyntaxFamily.C_LIKE,
        words("func var const return if else for range package import struct interface type map chan go defer select switch case default break continue fallthrough goto"),
        literals = setOf("nil", "true", "false", "iota"),
        types = true
    )
    private val rust = SourceLanguage(
        "Rust", SyntaxFamily.C_LIKE,
        words("fn let mut pub struct enum impl use mod match return if else for while loop trait where as in ref move async await const static unsafe extern crate dyn type break continue self Self super"),
        literals = setOf("true", "false", "None", "Some", "Ok", "Err"),
        types = true
    )
    private val swift = SourceLanguage(
        "Swift", SyntaxFamily.C_LIKE,
        words("func let var class struct enum protocol extension return if else for while repeat guard in import switch case default break continue where self Self init deinit throws throw try catch async await private public internal fileprivate static override"),
        literals = setOf("nil", "true", "false"),
        types = true, tripleQuotes = true, annotations = true
    )
    private val dart = SourceLanguage(
        "Dart", SyntaxFamily.C_LIKE,
        words("class extends implements with mixin abstract final const var void return if else for while do switch case default break continue new import export library part async await try catch finally throw this super static get set late required"),
        types = true, templates = true, tripleQuotes = true, annotations = true
    )
    private val scala = SourceLanguage(
        "Scala", SyntaxFamily.C_LIKE,
        words("def val var class object trait extends with case match return if else for while do yield import package new this super override private protected sealed abstract implicit lazy type"),
        types = true, templates = true, tripleQuotes = true, annotations = true
    )
    private val php = SourceLanguage(
        "PHP", SyntaxFamily.C_LIKE,
        words("function class public private protected static return if else elseif for foreach while do switch case default break continue echo new use namespace try catch finally throw extends implements interface trait abstract final as fn match"),
        types = true, templates = true, variables = true
    )
    private val ruby = SourceLanguage(
        "Ruby", SyntaxFamily.HASH,
        words("def end class module if elsif else unless do while until for in case when return require require_relative include extend yield begin rescue ensure raise then attr_accessor attr_reader"),
        literals = setOf("nil", "true", "false", "self"),
        types = true, templates = true
    )
    private val sql = SourceLanguage(
        "SQL", SyntaxFamily.DASH,
        words("select from where insert update delete join left right inner outer full cross on and or not create table into values set order by group having limit offset as distinct drop alter index primary key foreign references union all exists in between like is case when then else end with returning view trigger begin commit rollback"),
        caseInsensitive = true,
        literals = setOf("null", "true", "false")
    )
    private val lua = SourceLanguage(
        "Lua", SyntaxFamily.DASH,
        words("local function end if then else elseif for while do return and or not in repeat until break goto"),
        literals = setOf("nil", "true", "false")
    )
    private val css = SourceLanguage("CSS", SyntaxFamily.CSS)
    private val json = SourceLanguage("JSON", SyntaxFamily.DATA)
    private val yaml = SourceLanguage("YAML", SyntaxFamily.CONFIG, literals = setOf("true", "false", "null", "yes", "no", "on", "off", "~"))
    private val toml = SourceLanguage("TOML", SyntaxFamily.CONFIG, literals = setOf("true", "false"))
    private val ini = SourceLanguage("INI", SyntaxFamily.CONFIG, literals = setOf("true", "false", "yes", "no", "on", "off"))
    private val markup = SourceLanguage("Markup", SyntaxFamily.MARKUP)
    private val gradle = SourceLanguage(
        "Gradle", SyntaxFamily.C_LIKE,
        words("def task plugins dependencies repositories implementation api testImplementation android apply id version val var fun"),
        types = true, templates = true, tripleQuotes = true
    )
    private val diff = SourceLanguage("Diff", SyntaxFamily.DIFF)
    private val log = SourceLanguage("Log", SyntaxFamily.LOG)
    private val plain = SourceLanguage("Text", SyntaxFamily.PLAIN)
    private val markdown = SourceLanguage("Markdown", SyntaxFamily.MARKDOWN)

    /**
     * THE LIST: file extension (without the dot, lowercase) to language. Add a line here to make a type previewable
     * and highlighted. An extension which is not in this list is still previewed when its type is text.
     */
    val byExtension: Map<String, SourceLanguage> = mapOf(
        "kt" to kotlin, "kts" to kotlin,
        "java" to java,
        "js" to javascript, "mjs" to javascript, "cjs" to javascript, "jsx" to javascript,
        "ts" to typescript, "tsx" to typescript, "mts" to typescript,
        "py" to python, "pyw" to python,
        "sh" to shell, "bash" to shell, "zsh" to shell,
        "ps1" to powershell, "psm1" to powershell,
        "c" to c, "h" to c,
        "cpp" to cpp, "cc" to cpp, "cxx" to cpp, "hpp" to cpp, "hh" to cpp,
        "cs" to csharp,
        "go" to go,
        "rs" to rust,
        "swift" to swift,
        "dart" to dart,
        "scala" to scala, "sc" to scala,
        "php" to php,
        "rb" to ruby,
        "sql" to sql,
        "lua" to lua,
        "css" to css, "scss" to css, "less" to css,
        "json" to json, "jsonc" to json, "json5" to json, "webmanifest" to json,
        "yml" to yaml, "yaml" to yaml,
        "toml" to toml,
        "ini" to ini, "cfg" to ini, "conf" to ini, "properties" to ini,
        "gradle" to gradle,
        "html" to markup, "htm" to markup, "xhtml" to markup, "xml" to markup, "svg" to markup, "vue" to markup, "svelte" to markup,
        "md" to markdown, "markdown" to markdown,
        "diff" to diff, "patch" to diff,
        "log" to log,
        "txt" to plain, "csv" to plain, "tsv" to plain, "rtf" to plain,
    )

    /** Shebang names (`#!/usr/bin/env python3`) that tell the language when there is no extension to go by. */
    val byShebang: Map<String, SourceLanguage> = mapOf(
        "python" to python, "sh" to shell, "bash" to shell, "zsh" to shell, "node" to javascript, "ruby" to ruby, "php" to php, "lua" to lua,
        "pwsh" to powershell,
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

enum class TokenKind {
    PLAIN, COMMENT, STRING,
    /** Inside a string: an escape (`\n`) or a filled-in part (`${name}`); also an entity like `&amp;` */
    ESCAPE,
    NUMBER, KEYWORD,
    /** A value written as a word: true, null, None, a CONSTANT */
    LITERAL,
    /** A type, a class: a capitalized word; a section header in a config */
    TYPE,
    /** A function being called or defined */
    FUNCTION,
    /** A key: in JSON, YAML, a config, a CSS property */
    PROPERTY,
    /** `@Annotation`, a Python `@decorator` */
    ANNOTATION,
    /** `$variable` in shell or PHP, an `@instance` variable in Ruby */
    VARIABLE,
    /** A markup tag's name */
    TAG,
    /** A markup attribute's name */
    ATTRIBUTE,
    /** A Markdown heading */
    HEADING,
    /** Markdown **strong** text */
    STRONG,
    /** Markdown *emphasized* text */
    EMPHASIS,
    /** A diff's added line */
    INSERTED,
    /** A diff's removed line */
    DELETED,
}

data class Token(val start: Int, val end: Int, val kind: TokenKind)

/**
 * Splits [code] into tokens. Plain text between the tokens is left as [TokenKind.PLAIN] (the gaps are not listed). An
 * [TokenKind.ESCAPE] lies inside the string it belongs to, so it's listed after it.
 *
 * It's a plain scan, not a parser: a comment marker inside a string is part of the string, and a string ends at its
 * quote, or at the end of its line. It's done character by character with no regular expressions, so a long input
 * can't overflow the stack.
 */
fun tokenize(code: String, language: SourceLanguage?): List<Token> {
    if (language == null) return emptyList()
    val scanner = Scanner(code, language)
    when (language.family) {
        SyntaxFamily.C_LIKE, SyntaxFamily.HASH, SyntaxFamily.DASH -> scanner.code()
        SyntaxFamily.DATA -> scanner.json()
        SyntaxFamily.CONFIG -> scanner.config()
        SyntaxFamily.CSS -> scanner.css()
        SyntaxFamily.MARKUP -> scanner.markup()
        SyntaxFamily.MARKDOWN -> scanner.markdown()
        SyntaxFamily.DIFF -> scanner.diff()
        SyntaxFamily.LOG -> scanner.log()
        SyntaxFamily.PLAIN -> Unit
    }
    return scanner.tokens
}

private class Scanner(val code: String, val language: SourceLanguage) {
    val tokens = ArrayList<Token>()
    val length = code.length

    fun add(start: Int, end: Int, kind: TokenKind) {
        if (end > start) tokens += Token(start, end, kind)
    }

    fun lineEnd(from: Int): Int = code.indexOf('\n', from).let { if (it < 0) length else it }

    fun skipSpaces(from: Int): Int {
        var i = from
        while (i < length && (code[i] == ' ' || code[i] == '\t')) i++
        return i
    }

    fun isWordStart(c: Char) = c.isLetter() || c == '_'
    fun isWordPart(c: Char) = c.isLetterOrDigit() || c == '_'

    fun wordEnd(from: Int): Int {
        var j = from
        while (j < length && isWordPart(code[j])) j++
        return j
    }

    // ---- Shared pieces ----

    /**
     * A string starting at [start] with [quote] (three of them for a [triple] one), to its closing quote; a plain one ends
     * at its line, except a backtick one. Escapes and filled-in parts inside it are listed as well. Returns its end.
     */
    fun string(start: Int, quote: Char, triple: Boolean = false, templates: Boolean = language.templates): Int {
        val open = if (triple) 3 else 1
        var j = start + open
        val escapes = ArrayList<Token>()
        val multiline = triple || quote == '`'
        var end = length
        while (j < length) {
            val ch = code[j]
            if (triple && code.startsWith("$quote$quote$quote", j)) { end = j + 3; break }
            if (!triple && ch == quote) { end = j + 1; break }
            if (ch == '\n' && !multiline) { end = j; break }
            if (ch == '\\' && j + 1 < length) {
                escapes += Token(j, j + 2, TokenKind.ESCAPE)
                j += 2
                continue
            }
            if (templates && ch == '$' && quote != '\'' && j + 1 < length) {
                val next = code[j + 1]
                if (next == '{') {
                    // To the matching brace, within the string
                    var depth = 0
                    var k = j + 1
                    while (k < length) {
                        if (code[k] == '{') depth++
                        if (code[k] == '}') { depth--; if (depth == 0) break }
                        if (code[k] == '\n' && !multiline) break
                        k++
                    }
                    val stop = if (k < length && code[k] == '}') k + 1 else k
                    escapes += Token(j, stop, TokenKind.ESCAPE)
                    j = stop
                    continue
                }
                if (isWordStart(next)) {
                    val stop = wordEnd(j + 1)
                    escapes += Token(j, stop, TokenKind.ESCAPE)
                    j = stop
                    continue
                }
            }
            j++
        }
        add(start, end, TokenKind.STRING)
        tokens.addAll(escapes)
        return end
    }

    /** A number at [start]: hex, binary, with a fraction or an exponent, and a suffix (10L, 1.5f, 12px). Returns its end. */
    fun number(start: Int): Int {
        var j = start
        if (code[j] == '-' || code[j] == '+') j++
        if (j + 1 < length && code[j] == '0' && (code[j + 1] == 'x' || code[j + 1] == 'X' || code[j + 1] == 'b' || code[j + 1] == 'B')) {
            j += 2
            while (j < length && (code[j].isLetterOrDigit() || code[j] == '_')) j++
            add(start, j, TokenKind.NUMBER)
            return j
        }
        while (j < length && (code[j].isDigit() || code[j] == '_')) j++
        if (j + 1 < length && code[j] == '.' && code[j + 1].isDigit()) {
            j++
            while (j < length && (code[j].isDigit() || code[j] == '_')) j++
        }
        if (j + 1 < length && (code[j] == 'e' || code[j] == 'E') &&
            (code[j + 1].isDigit() || ((code[j + 1] == '-' || code[j + 1] == '+') && j + 2 < length && code[j + 2].isDigit()))
        ) {
            j += 2
            while (j < length && code[j].isDigit()) j++
        }
        // A suffix: a type (10L, 1.5f, 0u) or a unit (12px, 1.5em, 100%)
        while (j < length && (code[j].isLetter() || code[j] == '%')) j++
        add(start, j, TokenKind.NUMBER)
        return j
    }

    /** What a word of code is: a keyword, a value, a constant, a call, a type, or nothing to colour. */
    fun classify(word: String, after: Int): TokenKind? {
        val known = if (language.caseInsensitive) word.lowercase() in language.keywords else word in language.keywords
        if (known) return TokenKind.KEYWORD
        if (word in language.literals || (language.caseInsensitive && word.lowercase() in language.literals)) return TokenKind.LITERAL
        val next = skipSpaces(after).let { if (it < length) code[it] else ' ' }
        if (language.types && word.length > 1 && word.any { it.isLetter() } && word.all { it.isUpperCase() || it.isDigit() || it == '_' }) {
            return TokenKind.LITERAL
        }
        if (next == '(') return TokenKind.FUNCTION
        if (language.types && word[0].isUpperCase()) return TokenKind.TYPE
        return null
    }

    // ---- Programming languages ----

    fun code() {
        val family = language.family
        val cLike = family == SyntaxFamily.C_LIKE
        val quotes = if (cLike) "\"'`" else "\"'"
        var i = 0
        var previousWord: String? = null
        while (i < length) {
            val c = code[i]

            // Line comments run to the end of the line; Lua's --[[ ]] is a block
            if (family == SyntaxFamily.DASH && code.startsWith("--[[", i)) {
                val close = code.indexOf("]]", i + 4)
                val end = if (close < 0) length else close + 2
                add(i, end, TokenKind.COMMENT); i = end; continue
            }
            val lineComment = when (family) {
                SyntaxFamily.C_LIKE -> code.startsWith("//", i)
                // Not $# in shell, nor # inside a word
                SyntaxFamily.HASH -> c == '#' && (i == 0 || code[i - 1] != '$')
                SyntaxFamily.DASH -> code.startsWith("--", i)
                else -> false
            }
            if (lineComment) {
                val end = lineEnd(i)
                add(i, end, TokenKind.COMMENT); i = end; continue
            }
            if (cLike && code.startsWith("/*", i)) {
                val close = code.indexOf("*/", i + 2)
                val end = if (close < 0) length else close + 2
                add(i, end, TokenKind.COMMENT); i = end; continue
            }

            // Strings, and the """ kind which runs over lines
            if (quotes.indexOf(c) >= 0) {
                val triple = language.tripleQuotes && c != '`' && code.startsWith("$c$c$c", i)
                i = string(i, c, triple)
                previousWord = null
                continue
            }

            // @Annotation, @decorator; Ruby's @instance variables
            if (c == '@' && i + 1 < length && isWordStart(code[i + 1])) {
                var j = wordEnd(i + 1)
                while (j + 1 < length && code[j] == '.' && isWordStart(code[j + 1])) j = wordEnd(j + 1)
                add(i, j, if (language.annotations) TokenKind.ANNOTATION else TokenKind.VARIABLE)
                i = j; continue
            }

            // $variables: shell, PHP, PowerShell
            if (language.variables && c == '$' && i + 1 < length) {
                val next = code[i + 1]
                val end = when {
                    next == '{' -> code.indexOf('}', i).let { if (it < 0) lineEnd(i) else it + 1 }
                    isWordStart(next) -> wordEnd(i + 1)
                    next.isDigit() || next in "@#?*!$-" -> i + 2
                    else -> i
                }
                if (end > i) {
                    val word = code.substring(i, end)
                    add(i, end, if (word in language.literals) TokenKind.LITERAL else TokenKind.VARIABLE)
                    i = end; continue
                }
            }

            // Words: keywords, values, calls, types
            if (isWordStart(c)) {
                val j = wordEnd(i)
                val word = code.substring(i, j)
                // A Rust macro: name!(...)
                if (cLike && j + 1 < length && code[j] == '!' && code[j + 1] in "([{") {
                    add(i, j + 1, TokenKind.FUNCTION)
                    i = j + 1; previousWord = word; continue
                }
                // The name being defined after def/fun/function is a function, after class a type
                val kind = when (previousWord) {
                    "def", "fun", "function", "func", "fn" -> TokenKind.FUNCTION
                    "class", "interface", "struct", "enum", "trait", "object", "record", "type", "typealias", "module", "protocol" ->
                        if (word in language.keywords) TokenKind.KEYWORD else TokenKind.TYPE
                    else -> classify(word, j)
                }
                kind?.let { add(i, j, it) }
                previousWord = word
                i = j; continue
            }

            // Numbers, not the digits at the end of a word (handled above)
            if (c.isDigit() || (c == '.' && i + 1 < length && code[i + 1].isDigit() && (i == 0 || !isWordPart(code[i - 1])))) {
                i = if (c == '.') { val start = i; i++; while (i < length && code[i].isDigit()) i++; add(start, i, TokenKind.NUMBER); i } else number(i)
                continue
            }

            if (!c.isWhitespace()) previousWord = null
            i++
        }
    }

    // ---- JSON ----

    fun json() {
        var i = 0
        while (i < length) {
            val c = code[i]
            when {
                code.startsWith("//", i) -> { val end = lineEnd(i); add(i, end, TokenKind.COMMENT); i = end }
                code.startsWith("/*", i) -> {
                    val close = code.indexOf("*/", i + 2)
                    val end = if (close < 0) length else close + 2
                    add(i, end, TokenKind.COMMENT); i = end
                }
                c == '"' || c == '\'' -> {
                    val start = i
                    val end = string(i, c, templates = false)
                    // A string followed by a colon is a key
                    val next = skipSpaces(end)
                    if (next < length && code[next] == ':') {
                        tokens.removeAll { it.start >= start && it.end <= end }
                        add(start, end, TokenKind.PROPERTY)
                    }
                    i = end
                }
                c.isDigit() || (c == '-' && i + 1 < length && code[i + 1].isDigit()) -> i = number(i)
                isWordStart(c) -> {
                    val j = wordEnd(i)
                    if (code.substring(i, j) in language.literals) add(i, j, TokenKind.LITERAL)
                    i = j
                }
                else -> i++
            }
        }
    }

    // ---- YAML, TOML, INI ----

    fun config() {
        var lineStart = 0
        while (lineStart < length) {
            val end = lineEnd(lineStart)
            configLine(lineStart, end)
            lineStart = end + 1
        }
    }

    private fun configLine(start: Int, end: Int) {
        var i = skipSpaces(start)
        if (i >= end) return
        // A whole-line comment
        if (code[i] == '#' || code[i] == ';') { add(i, end, TokenKind.COMMENT); return }
        // [section] and [[table]] headers
        if (code[i] == '[') {
            val close = code.indexOf(']', i).takeIf { it in i until end }
            if (close != null) {
                val stop = if (close + 1 < end && code[close + 1] == ']') close + 2 else close + 1
                add(i, stop, TokenKind.TYPE)
                i = stop
            }
        }
        // YAML list items: "- key: value"
        if (i + 1 < end && code[i] == '-' && code[i + 1] == ' ') {
            add(i, i + 1, TokenKind.KEYWORD)
            i = skipSpaces(i + 1)
        }
        // key: value (YAML) or key = value (TOML, INI): the key, plain or quoted
        var k = i
        if (k < end && (code[k] == '"' || code[k] == '\'')) {
            val q = code[k]
            k++
            while (k < end && code[k] != q) k++
            if (k < end) k++
        } else {
            while (k < end && code[k] != ':' && code[k] != '=' && code[k] != '#' && !(code[k] == ' ' && skipSpaces(k).let { it < end && code[it] != ':' && code[it] != '=' })) k++
        }
        val sep = skipSpaces(k)
        val isKey = sep < end && k > i && (code[sep] == '=' || (code[sep] == ':' && (sep + 1 >= end || code[sep + 1] == ' ' || code[sep + 1] == '\t')))
        if (isKey) {
            // Without the spaces before the = or :
            var keyEnd = k
            while (keyEnd > i && (code[keyEnd - 1] == ' ' || code[keyEnd - 1] == '\t')) keyEnd--
            add(i, keyEnd, TokenKind.PROPERTY)
            i = sep + 1
        }
        // The value
        while (i < end) {
            val c = code[i]
            when {
                // A comment after the value, after a space
                c == '#' && (i == start || code[i - 1] == ' ' || code[i - 1] == '\t') -> { add(i, end, TokenKind.COMMENT); return }
                c == '"' || c == '\'' -> i = minOf(string(i, c, templates = false), end)
                c == '$' && i + 1 < end && code[i + 1] == '{' -> {
                    val close = code.indexOf('}', i).let { if (it < 0 || it > end) end else it + 1 }
                    add(i, close, TokenKind.VARIABLE); i = close
                }
                // YAML anchors and aliases
                (c == '&' || c == '*') && i + 1 < end && isWordStart(code[i + 1]) -> {
                    val j = wordEnd(i + 1); add(i, j, TokenKind.VARIABLE); i = j
                }
                c.isDigit() || ((c == '-' || c == '+') && i + 1 < end && code[i + 1].isDigit()) -> i = number(i)
                isWordStart(c) || c == '~' -> {
                    val j = if (c == '~') i + 1 else wordEnd(i)
                    if (code.substring(i, j).lowercase() in language.literals) add(i, j, TokenKind.LITERAL)
                    i = j
                }
                else -> i++
            }
        }
    }

    // ---- CSS ----

    fun css() {
        var i = 0
        var depth = 0
        while (i < length) {
            val c = code[i]
            when {
                code.startsWith("/*", i) -> {
                    val close = code.indexOf("*/", i + 2)
                    val end = if (close < 0) length else close + 2
                    add(i, end, TokenKind.COMMENT); i = end
                }
                code.startsWith("//", i) && depth >= 0 && (i == 0 || code[i - 1] != ':') -> { val end = lineEnd(i); add(i, end, TokenKind.COMMENT); i = end }
                c == '"' || c == '\'' -> i = string(i, c, templates = false)
                c == '{' -> { depth++; i++ }
                c == '}' -> { depth = maxOf(0, depth - 1); i++ }
                c == '@' && i + 1 < length && isWordStart(code[i + 1]) -> { val j = wordEnd(i + 1); add(i, j, TokenKind.KEYWORD); i = j }
                c == '!' && code.startsWith("!important", i) -> { add(i, i + 10, TokenKind.KEYWORD); i += 10 }
                c == '$' || (c == '-' && code.startsWith("--", i)) -> {
                    // SCSS $variables and custom --properties
                    var j = i + (if (c == '$') 1 else 2)
                    while (j < length && (isWordPart(code[j]) || code[j] == '-')) j++
                    val next = skipSpaces(j)
                    add(i, j, if (next < length && code[next] == ':' && depth > 0 && c == '-') TokenKind.PROPERTY else TokenKind.VARIABLE)
                    i = j
                }
                depth > 0 && c == '#' && i + 1 < length && code[i + 1].isLetterOrDigit() -> {
                    // A colour
                    var j = i + 1
                    while (j < length && code[j].isLetterOrDigit()) j++
                    add(i, j, TokenKind.NUMBER); i = j
                }
                depth == 0 && (c == '.' || c == '#') && i + 1 < length && (isWordStart(code[i + 1]) || code[i + 1] == '-') -> {
                    // .class and #id selectors
                    var j = i + 1
                    while (j < length && (isWordPart(code[j]) || code[j] == '-')) j++
                    add(i, j, TokenKind.TYPE); i = j
                }
                c.isDigit() || (c == '.' && i + 1 < length && code[i + 1].isDigit()) || (c == '-' && i + 1 < length && code[i + 1].isDigit()) ->
                    i = if (c == '.') { val s = i; i++; while (i < length && code[i].isDigit()) i++; while (i < length && (code[i].isLetter() || code[i] == '%')) i++; add(s, i, TokenKind.NUMBER); i } else number(i)
                isWordStart(c) || c == '-' -> {
                    var j = i
                    while (j < length && (isWordPart(code[j]) || code[j] == '-')) j++
                    val next = skipSpaces(j)
                    val kind = when {
                        next < length && code[next] == '(' -> TokenKind.FUNCTION
                        depth > 0 && next < length && code[next] == ':' -> TokenKind.PROPERTY
                        depth == 0 -> TokenKind.TAG
                        else -> null
                    }
                    kind?.let { add(i, j, it) }
                    i = j
                }
                else -> i++
            }
        }
    }

    // ---- HTML, XML ----

    fun markup() {
        var i = 0
        while (i < length) {
            val c = code[i]
            when {
                code.startsWith("<!--", i) -> {
                    val close = code.indexOf("-->", i + 4)
                    val end = if (close < 0) length else close + 3
                    add(i, end, TokenKind.COMMENT); i = end
                }
                code.startsWith("<![CDATA[", i) -> {
                    val close = code.indexOf("]]>", i)
                    val end = if (close < 0) length else close + 3
                    add(i, end, TokenKind.STRING); i = end
                }
                c == '<' && i + 1 < length && (isWordStart(code[i + 1]) || code[i + 1] in "/?!") -> i = tag(i)
                c == '&' -> {
                    val semi = code.indexOf(';', i)
                    if (semi in (i + 2)..(i + 10) && code.substring(i + 1, semi).all { it.isLetterOrDigit() || it == '#' }) {
                        add(i, semi + 1, TokenKind.ESCAPE); i = semi + 1
                    } else i++
                }
                else -> i++
            }
        }
    }

    /** A tag at [start] (`<name attr="value">`, `</name>`, `<?xml ...?>`): its name and attributes. Returns its end. */
    private fun tag(start: Int): Int {
        var i = start + 1
        while (i < length && code[i] in "/?!") i++
        var j = i
        while (j < length && (isWordPart(code[j]) || code[j] in "-:.")) j++
        add(i, j, TokenKind.TAG)
        i = j
        while (i < length && code[i] != '>' && code[i] != '<') {
            val c = code[i]
            when {
                c == '"' || c == '\'' -> {
                    val close = code.indexOf(c, i + 1).let { if (it < 0) length else it + 1 }
                    add(i, close, TokenKind.STRING); i = close
                }
                isWordStart(c) -> {
                    var k = i
                    while (k < length && (isWordPart(code[k]) || code[k] in "-:.@")) k++
                    add(i, k, TokenKind.ATTRIBUTE); i = k
                }
                else -> i++
            }
        }
        return if (i < length && code[i] == '>') i + 1 else i
    }

    // ---- Markdown ----

    fun markdown() {
        var lineStart = 0
        var inFence = false
        var fenceStart = 0
        while (lineStart < length) {
            val end = lineEnd(lineStart)
            val i = skipSpaces(lineStart)
            val fence = code.startsWith("```", i) || code.startsWith("~~~", i)
            when {
                fence && !inFence -> { inFence = true; fenceStart = lineStart }
                fence -> { inFence = false; add(fenceStart, end, TokenKind.STRING) }
                inFence -> Unit
                i < end && code[i] == '#' -> {
                    var h = i
                    while (h < end && code[h] == '#') h++
                    if (h - i <= 6 && (h == end || code[h] == ' ')) add(lineStart, end, TokenKind.HEADING) else inline(i, end)
                }
                i < end && code[i] == '>' -> add(i, end, TokenKind.COMMENT)
                // A rule: --- or *** alone
                end - i >= 3 && code.substring(i, end).trim().let { line -> line.length >= 3 && (line.all { it == '-' } || line.all { it == '*' } || line.all { it == '_' }) } ->
                    add(i, end, TokenKind.COMMENT)
                else -> {
                    // List markers: -, *, + or 1.
                    var body = i
                    if (i + 1 < end && code[i] in "-*+" && code[i + 1] == ' ') {
                        add(i, i + 1, TokenKind.KEYWORD); body = i + 2
                    } else if (i < end && code[i].isDigit()) {
                        var d = i
                        while (d < end && code[d].isDigit()) d++
                        if (d + 1 < end && (code[d] == '.' || code[d] == ')') && code[d + 1] == ' ') { add(i, d + 1, TokenKind.KEYWORD); body = d + 2 }
                    }
                    inline(body, end)
                }
            }
            lineStart = end + 1
        }
        if (inFence) add(fenceStart, length, TokenKind.STRING)
    }

    /** Code, links and emphasis within one line of Markdown. */
    private fun inline(from: Int, end: Int) {
        var i = from
        while (i < end) {
            val c = code[i]
            when {
                c == '`' -> {
                    val close = code.indexOf('`', i + 1)
                    if (close in (i + 1) until end) { add(i, close + 1, TokenKind.STRING); i = close + 1 } else i++
                }
                c == '[' -> {
                    // [text](address)
                    val close = code.indexOf(']', i + 1)
                    if (close in (i + 1) until end && close + 1 < end && code[close + 1] == '(') {
                        val paren = code.indexOf(')', close + 2)
                        if (paren in (close + 2) until end + 1) {
                            add(i, close + 1, TokenKind.FUNCTION)
                            add(close + 1, paren + 1, TokenKind.STRING)
                            i = paren + 1
                            continue
                        }
                    }
                    i++
                }
                (c == '*' || c == '_') && i + 1 < end && code[i + 1] == c -> {
                    val close = code.indexOf("$c$c", i + 2)
                    if (close in (i + 2) until end) { add(i, close + 2, TokenKind.STRONG); i = close + 2 } else i += 2
                }
                (c == '*' || c == '_') && i + 1 < end && !code[i + 1].isWhitespace() && (i == 0 || !isWordPart(code[i - 1])) -> {
                    val close = code.indexOf(c, i + 1)
                    if (close in (i + 1) until end) { add(i, close + 1, TokenKind.EMPHASIS); i = close + 1 } else i++
                }
                code.startsWith("http://", i) || code.startsWith("https://", i) -> {
                    var j = i
                    while (j < end && !code[j].isWhitespace() && code[j] != ')' && code[j] != '>') j++
                    add(i, j, TokenKind.STRING); i = j
                }
                else -> i++
            }
        }
    }

    // ---- Diffs ----

    fun diff() {
        var lineStart = 0
        while (lineStart < length) {
            val end = lineEnd(lineStart)
            if (lineStart < end) {
                val kind = when {
                    code.startsWith("+++", lineStart) || code.startsWith("---", lineStart) || code.startsWith("diff ", lineStart) ||
                        code.startsWith("index ", lineStart) -> TokenKind.KEYWORD
                    code.startsWith("@@", lineStart) -> TokenKind.TYPE
                    code[lineStart] == '+' -> TokenKind.INSERTED
                    code[lineStart] == '-' -> TokenKind.DELETED
                    else -> null
                }
                kind?.let { add(lineStart, end, it) }
            }
            lineStart = end + 1
        }
    }

    // ---- Logs ----

    fun log() {
        var i = 0
        while (i < length) {
            val c = code[i]
            when {
                isWordStart(c) -> {
                    val j = wordEnd(i)
                    val kind = when (code.substring(i, j).uppercase()) {
                        "ERROR", "ERR", "FATAL", "SEVERE", "CRITICAL", "EXCEPTION", "FAILED", "FAILURE" -> TokenKind.DELETED
                        "WARN", "WARNING" -> TokenKind.TYPE
                        "INFO", "NOTICE" -> TokenKind.FUNCTION
                        "DEBUG", "TRACE", "VERBOSE", "FINE" -> TokenKind.COMMENT
                        else -> null
                    }
                    // Single letters only as a level: "E/Tag" or " E " in Android's logcat
                    val logcat = j == i + 1 && c in "VDIWEF" && j < length && (code[j] == '/' || code[j] == ' ') && (i == 0 || code[i - 1] == ' ')
                    val level = kind ?: if (logcat) when (c) {
                        'E', 'F' -> TokenKind.DELETED
                        'W' -> TokenKind.TYPE
                        'I' -> TokenKind.FUNCTION
                        else -> TokenKind.COMMENT
                    } else null
                    level?.let { add(i, j, it) }
                    i = j
                }
                c.isDigit() -> {
                    // Dates and times as one number: 2024-01-02 10:11:12.345
                    var j = i
                    while (j < length && (code[j].isDigit() || (code[j] in "-:.T" && j + 1 < length && code[j + 1].isDigit()))) j++
                    add(i, j, TokenKind.NUMBER); i = j
                }
                c == '"' -> i = string(i, c, templates = false)
                else -> i++
            }
        }
    }
}
