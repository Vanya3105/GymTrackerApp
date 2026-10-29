package com.example.gymtracker

import org.junit.Assert.assertEquals
import org.junit.Test

class NextSetTest {

    private fun ex(vararg done: Boolean) = ExerciseEntry(
        name = "Bench",
        sets = done.mapIndexed { i, d -> SetEntry(i + 1, "60", "8", isCompleted = d) }
    )

    @Test
    fun inOrder() {
        assertEquals(0, ex(false, false, false).nextSetIndex())
        assertEquals(1, ex(true, false, false).nextSetIndex())
        assertEquals(-1, ex(true, true).nextSetIndex())
    }

    @Test
    fun skippedSetDoesNotHoldItBack() {
        // Warm-up skipped, set 1 done: next is set 2, not the warm-up.
        assertEquals(2, ex(false, true, false).nextSetIndex())
    }

    @Test
    fun onlyEarlierSetsLeft_goesBackToThem() {
        assertEquals(0, ex(false, true, true).nextSetIndex())
    }
}
