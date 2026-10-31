package com.example.gymtracker

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** Every tr("…") text in the app has a Hebrew line, so nothing shows up in English by mistake. */
class HebrewTextTest {
    private val literal = Regex("""\btr\("((?:[^"\\]|\\.)*)"""")
    private val placeholder = Regex("""\{\d+\}""")

    // The code keeps \uXXXX escapes; the table may hold the characters themselves.
    private fun unescape(s: String) = Regex("""\\u([0-9A-Fa-f]{4})""")
        .replace(s) { it.groupValues[1].toInt(16).toChar().toString() }
        .replace("\\\"", "\"")

    @Test
    fun everyTranslatedTextHasHebrew() {
        val sources = File("src/main/java/com/example/gymtracker").listFiles { f -> f.extension == "kt" }!!
        val used = sources.flatMap { f -> literal.findAll(f.readText()).map { unescape(it.groupValues[1]) }.toList() }.toSortedSet()
        val missing = used.filter { HebrewText.of(it) == null }
        assertEquals(emptyList<String>(), missing)
    }

    @Test
    fun placeholdersSurviveTranslation() {
        val wrong = HebrewText.english.filter { en ->
            placeholder.findAll(en).map { it.value }.toSet() != placeholder.findAll(HebrewText.of(en)!!).map { it.value }.toSet()
        }
        assertEquals(emptyList<String>(), wrong)
    }
}
