package com.example.gymtracker.watchlink

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WatchLinkTest {
    @Test
    fun stateRoundTrips() {
        val full = WatchState(
            active = true, workoutName = "Push", exerciseName = "Bench Press", setLabel = "Set 2",
            primary = "80 kg", primaryLabel = "weight", secondary = "8", secondaryLabel = "reps",
            summary = "80 kg × 8", setsLeft = 7, restEndsAtMillis = 1_800_000_000_000, restTotalMillis = 90_000
        )
        assertEquals(full, WatchState.fromJson(full.toJson()))
        val idle = WatchState(active = false)
        assertEquals(idle, WatchState.fromJson(idle.toJson()))
        assertEquals(WatchState(active = true, exerciseName = null), WatchState.fromJson("""{"active":true,"newField":1}"""))
        assertNull(WatchState.fromJson("not json"))
    }

    @Test
    fun actionPaths() {
        WatchAction.entries.forEach { assertEquals(it, WatchLink.actionFromPath(WatchLink.actionPath(it))) }
        assertNull(WatchLink.actionFromPath(WatchLink.REQUEST_STATE))
        assertNull(WatchLink.actionFromPath(WatchLink.ACTION_PREFIX + "UNKNOWN"))
    }
}
