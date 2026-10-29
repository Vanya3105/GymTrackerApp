package com.example.gymtracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PreviousSetsTest {
    private fun set(w: String, r: String, kind: String = "NORMAL") =
        SetEntry(0, w, r, isWarmup = kind == "WARMUP", intensityType = kind)

    private fun workout(id: Long, split: String, vararg exercises: ExerciseEntry) = CompletedWorkout(
        id = id, splitTitle = split, dateKey = "", displayDate = "", durationSeconds = 0, bodyWeightKg = 80f,
        photoPath = null, skippedExercises = emptyList(), exercises = exercises.toList()
    )

    @Test
    fun setsLineUpByKind_warmUpsWithWarmUps() {
        val last = listOf(set("40", "8", "WARMUP"), set("80", "8"), set("80", "7"), set("60", "10", "DROP"))
        // Today has an extra warm-up and no drop set yet.
        val today = listOf(set("30", "10", "WARMUP"), set("50", "5", "WARMUP"), set("82.5", "8"), set("82.5", "8"), set("82.5", "8"))
        assertEquals("40", previousSetFor(today, 0, last)?.weight)
        assertNull(previousSetFor(today, 1, last))
        assertEquals("8", previousSetFor(today, 2, last)?.reps)
        assertEquals("7", previousSetFor(today, 3, last)?.reps)
        assertNull(previousSetFor(today, 4, last))
        assertEquals("60", previousSetFor(today + set("65", "10", "DROP"), 5, last)?.weight)
    }

    @Test
    fun sameDayFirst_elseTheLatestWorkoutWithIt() {
        val pushBench = ExerciseEntry("Bench Press", sets = listOf(set("80", "8")))
        val upperBench = ExerciseEntry("bench press", sets = listOf(set("70", "12")))
        val push = workout(1, "Push", pushBench)
        val upper = workout(2, "Upper", upperBench)
        val newestFirst = listOf(upper, push)

        assertEquals("80", lastSessionSets("Bench Press", ExerciseType.WEIGHT_REPS, push, newestFirst)?.single()?.weight)
        // Today's day didn't have it last time (or it's a quick workout): the latest one that did.
        val pushWithoutBench = workout(3, "Push", ExerciseEntry("Dip", sets = listOf(set("0", "10"))))
        assertEquals("70", lastSessionSets("Bench Press", ExerciseType.WEIGHT_REPS, pushWithoutBench, newestFirst)?.single()?.weight)
        assertEquals("70", lastSessionSets("Bench Press", ExerciseType.WEIGHT_REPS, null, newestFirst)?.single()?.weight)
    }

    @Test
    fun skippedOrEmpty_doesNotCount() {
        val skipped = workout(2, "Push", ExerciseEntry("Bench Press", isSkipped = true, sets = listOf(set("80", "8"))))
        val blank = workout(3, "Push", ExerciseEntry("Bench Press", sets = listOf(set("", ""))))
        assertNull(lastSessionSets("Bench Press", ExerciseType.WEIGHT_REPS, blank, listOf(blank, skipped)))
    }
}
