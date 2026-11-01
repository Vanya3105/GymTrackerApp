package com.example.gymtracker.wear

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** Every t("…") in the watch app has Hebrew. */
class WatchTextTest {
    @Test
    fun everyTextHasHebrew() {
        val call = Regex("""\bt\("([^"]*)"""")
        val used = File("src/main/java").walk().filter { it.extension == "kt" }
            .flatMap { f -> call.findAll(f.readText()).map { it.groupValues[1] } }
            .toSet()
        assertEquals(emptyList<String>(), (used - WatchText.english).sorted())
    }
}
