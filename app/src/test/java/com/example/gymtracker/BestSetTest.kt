package com.example.gymtracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BestSetTest {

    private fun set(w: String, r: String, warmup: Boolean = false, sec: String = "", dist: String = "") =
        SetEntry(1, w, r, isWarmup = warmup, intensityType = if (warmup) "WARMUP" else "NORMAL", seconds = sec, distanceM = dist)

    @Test
    fun loadedLifts_pickHighestEstimatedMax_notJustHeaviest() {
        // 100×3 ≈ 110 e1RM, 90×8 ≈ 114: the 90×8 set is the better set.
        val best = bestSetOf(listOf(set("100", "3"), set("90", "8"), set("60", "12")), ExerciseType.WEIGHT_REPS)
        assertEquals("90", best?.weight)
    }

    @Test
    fun warmupsDontCount() {
        val best = bestSetOf(listOf(set("140", "5", warmup = true), set("100", "5")), ExerciseType.WEIGHT_REPS)
        assertEquals("100", best?.weight)
        assertNull(bestSetOf(listOf(set("60", "10", warmup = true)), ExerciseType.WEIGHT_REPS))
    }

    @Test
    fun otherTypes() {
        assertEquals("15", bestSetOf(listOf(set("", "12"), set("", "15")), ExerciseType.BODYWEIGHT)?.reps)
        // Least assistance wins; on a tie, more reps.
        assertEquals("8", bestSetOf(listOf(set("30", "10"), set("20", "6"), set("20", "8")), ExerciseType.ASSISTED)?.reps)
        assertEquals("90", bestSetOf(listOf(set("", "", sec = "60"), set("", "", sec = "90")), ExerciseType.DURATION)?.seconds)
        assertEquals("5000", bestSetOf(listOf(set("", "", sec = "1500", dist = "5000"), set("", "", sec = "600", dist = "2000")), ExerciseType.DISTANCE_TIME)?.distanceM)
    }

    @Test
    fun weightedBodyweight_withoutAddedWeight_fallsBackToReps() {
        assertEquals("14", bestSetOf(listOf(set("0", "10"), set("0", "14")), ExerciseType.WEIGHTED_BODYWEIGHT)?.reps)
    }
}
