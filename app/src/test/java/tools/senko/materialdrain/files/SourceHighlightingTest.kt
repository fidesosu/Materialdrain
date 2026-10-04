package tools.senko.materialdrain.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceHighlightingTest {

    private fun kindsOf(code: String, language: SourceLanguage?) =
        tokenize(code, language).map { code.substring(it.start, it.end) to it.kind }

    @Test
    fun `the extension decides the language`() {
        assertEquals("Kotlin", languageFor("App.kt")?.name)
        assertEquals("Python", languageFor("script.PY")?.name)
        assertEquals("SQL", languageFor("query.sql")?.name)
    }

    @Test
    fun `a file with no known extension is found by its shebang`() {
        assertEquals("Python", languageFor("run", "#!/usr/bin/env python3")?.name)
        assertEquals("Shell", languageFor("deploy", "#!/bin/bash")?.name)
        assertNull(languageFor("notes", "just some words"))
    }

    @Test
    fun `dotfiles and extensionless names are not mistaken for a language`() {
        assertNull(languageFor(".gitignore"))
        assertNull(languageFor("Makefile"))
    }

    @Test
    fun `text is recognised by a known extension or a text type`() {
        assertTrue(isTextFile("Main.java", "application/octet-stream"))
        assertTrue(isTextFile("notes.bin", "text/plain"))
        assertFalse(isTextFile("photo.bin", "application/octet-stream"))
    }

    @Test
    fun `keywords, strings, comments and numbers are found in c-like code`() {
        val code = "val x = 42 // answer\nval s = \"hi\""
        val tokens = kindsOf(code, languageFor("a.kt"))
        assertTrue(tokens.contains("val" to TokenKind.KEYWORD))
        assertTrue(tokens.contains("42" to TokenKind.NUMBER))
        assertTrue(tokens.contains("// answer" to TokenKind.COMMENT))
        assertTrue(tokens.contains("\"hi\"" to TokenKind.STRING))
    }

    @Test
    fun `a comment marker inside a string is a string`() {
        val tokens = kindsOf("val url = \"https://x // y\"", languageFor("a.kt"))
        assertTrue(tokens.contains("\"https://x // y\"" to TokenKind.STRING))
        assertFalse(tokens.any { it.second == TokenKind.COMMENT })
    }

    @Test
    fun `hash comments belong to python and shell`() {
        val tokens = kindsOf("x = 1  # set x", languageFor("a.py"))
        assertTrue(tokens.contains("# set x" to TokenKind.COMMENT))
    }

    @Test
    fun `sql keywords are found in any case`() {
        val tokens = kindsOf("select * from t -- all\n", languageFor("a.sql"))
        assertTrue(tokens.contains("select" to TokenKind.KEYWORD))
        assertTrue(tokens.contains("-- all" to TokenKind.COMMENT))
        assertEquals(1, tokens.count { it.first == "from" && it.second == TokenKind.KEYWORD })
    }

    @Test
    fun `plain text has no tokens`() {
        assertTrue(tokenize("anything at all", languageFor("notes.txt")).isEmpty())
    }
}
