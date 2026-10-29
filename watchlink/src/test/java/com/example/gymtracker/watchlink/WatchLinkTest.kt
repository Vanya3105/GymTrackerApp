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
            summary = "80 kg × 8", setsLeft = 7, restEndsAtMillis = 1_800_000_000_000, restTotalMillis = 90_000,
            startedAtMillis = 1_799_999_000_000, exercises = listOf(WatchExercise("Bench Press", 1, 3), WatchExercise("Dips", 0, 3)),
            currentExercise = 0, setNumber = 2, setCount = 3, canUndo = true
        )
        assertEquals(full, WatchState.fromJson(full.toJson()))
        val idle = WatchState(active = false)
        assertEquals(idle, WatchState.fromJson(idle.toJson()))
        val saved = WatchState(
            active = false, days = listOf("Pull", "Legs", "Push"),
            lastWorkout = WatchFinished("Push", 1_800_000_000_000, 3_120, 18, "4,250 kg", 2)
        )
        assertEquals(saved, WatchState.fromJson(saved.toJson()))
        assertEquals(saved.copy(lastWorkout = saved.lastWorkout!!.copy(volume = null)), WatchState.fromJson(saved.copy(lastWorkout = saved.lastWorkout.copy(volume = null)).toJson()))
        // What a phone from before these fields sends.
        assertEquals(WatchState(active = true, exerciseName = "Squat"), WatchState.fromJson("""{"v":1,"active":true,"exercise":"Squat"}"""))
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
