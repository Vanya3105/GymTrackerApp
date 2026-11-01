package com.example.gymtracker.wear

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RestOverTest {
    private val end = 1_000_000L

    @Test fun ranOut() = assertTrue(restOver(end, 0L, end + 300))
    @Test fun skippedEarly() = assertFalse(restOver(end, 0L, end - 40_000))
    @Test fun stillResting() = assertFalse(restOver(end, end + 15_000, end - 5_000))
    @Test fun noRest() = assertFalse(restOver(0L, 0L, end))
}
