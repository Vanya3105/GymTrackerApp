package com.example.gymtracker

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseTypesTest {

    @After
    fun resetUnit() = Units.set(WeightUnit.KG)

    private fun set(w: String = "", r: String = "", sec: String = "", m: String = "", warmup: Boolean = false) = SetEntry(
        setNumber = 1, weight = w, reps = r, seconds = sec, distanceM = m, isCompleted = true,
        isWarmup = warmup, intensityType = if (warmup) "WARMUP" else "NORMAL"
    )

    private fun ex(type: ExerciseType, vararg sets: SetEntry, name: String = "Ex") = ExerciseEntry(name, sets = sets.toList(), type = type)

    private fun workout(id: Long, vararg exercises: ExerciseEntry) = CompletedWorkout(
        id = id, splitTitle = "Any", dateKey = "", displayDate = "Day $id", durationSeconds = 0,
        bodyWeightKg = 80f, photoPath = null, skippedExercises = emptyList(), exercises = exercises.toList()
    )

    @Test
    fun validity_dependsOnType() {
        assertFalse(set(r = "10").isValidWorkSet(ExerciseType.WEIGHT_REPS)) // weight needed
        assertTrue(set(r = "10").isValidWorkSet(ExerciseType.BODYWEIGHT))
        assertTrue(set(w = "0", r = "8").isValidWorkSet(ExerciseType.WEIGHTED_BODYWEIGHT))
        assertTrue(set(w = "20", r = "8").isValidWorkSet(ExerciseType.ASSISTED))
        assertFalse(set(r = "8").isValidWorkSet(ExerciseType.DURATION))
        assertTrue(set(sec = "45").isValidWorkSet(ExerciseType.DURATION))
        assertTrue(set(m = "5000").isValidWorkSet(ExerciseType.DISTANCE_TIME))
        assertTrue(set(sec = "1500").isValidWorkSet(ExerciseType.DISTANCE_TIME))
        assertFalse(set(sec = "45", warmup = true).isValidWorkSet(ExerciseType.DURATION))
    }

    @Test
    fun formatting_perType() {
        assertEquals("80 kg × 6", formatSet(set("80", "6")))
        assertEquals("12 reps", formatSet(set(r = "12"), ExerciseType.BODYWEIGHT))
        assertEquals("1 rep", formatSet(set(r = "1"), ExerciseType.BODYWEIGHT))
        assertEquals("+20 kg × 8", formatSet(set("20", "8"), ExerciseType.WEIGHTED_BODYWEIGHT))
        assertEquals("−25 kg × 8", formatSet(set("25", "8"), ExerciseType.ASSISTED))
        assertEquals("1:30", formatSet(set(sec = "90"), ExerciseType.DURATION))
        assertEquals("5 km · 25:00", formatSet(set(m = "5000", sec = "1500"), ExerciseType.DISTANCE_TIME))
        assertEquals("2.5 km", formatSet(set(m = "2500"), ExerciseType.DISTANCE_TIME))
        Units.set(WeightUnit.LB)
        assertEquals("3.11 mi · 25:00", formatSet(set(m = "5000", sec = "1500"), ExerciseType.DISTANCE_TIME))
        assertEquals("12", formatSetShort(set(r = "12"), ExerciseType.BODYWEIGHT))
    }

    @Test
    fun workoutStats_countSetsOfEveryType_butVolumeOnlyForLoadedLifts() {
        val stats = computeWorkoutStats(listOf(
            ex(ExerciseType.WEIGHT_REPS, set("100", "5")),
            ex(ExerciseType.BODYWEIGHT, set(r = "12"), set(r = "10"), name = "Push-Up"),
            ex(ExerciseType.WEIGHTED_BODYWEIGHT, set("20", "8"), name = "Weighted Dips"),
            ex(ExerciseType.ASSISTED, set("30", "8"), name = "Assisted Pull-Up"),
            ex(ExerciseType.DURATION, set(sec = "60"), name = "Plank"),
            ex(ExerciseType.DISTANCE_TIME, set(m = "5000", sec = "1500"), name = "Running")
        ), emptyList())
        assertEquals(7, stats.workSets)
        assertEquals(100 * 5 + 20 * 8, stats.volumeKg)
    }

    @Test
    fun personalRecords_perType_needEarlierHistory() {
        fun pr(type: ExerciseType, past: SetEntry, now: SetEntry): Boolean {
            val history = listOf(workout(1, ex(type, past)))
            return isSetPersonalRecord(now, computeExerciseRecords("Ex", history), type)
        }
        assertTrue(pr(ExerciseType.BODYWEIGHT, set(r = "10"), set(r = "11")))
        assertFalse(pr(ExerciseType.BODYWEIGHT, set(r = "10"), set(r = "10")))
        assertTrue(pr(ExerciseType.ASSISTED, set("30", "8"), set("25", "6"))) // less help = better
        assertFalse(pr(ExerciseType.ASSISTED, set("30", "8"), set("35", "12")))
        assertTrue(pr(ExerciseType.DURATION, set(sec = "60"), set(sec = "75")))
        assertTrue(pr(ExerciseType.DISTANCE_TIME, set(m = "5000"), set(m = "5500")))
        assertTrue(pr(ExerciseType.WEIGHTED_BODYWEIGHT, set("20", "8"), set("25", "8")))
        // First time ever: nothing to beat.
        assertFalse(isSetPersonalRecord(set(r = "20"), computeExerciseRecords("Ex", emptyList()), ExerciseType.BODYWEIGHT))
    }

    @Test
    fun records_bestSetFollowsTheType() {
        val history = listOf(
            workout(2, ex(ExerciseType.ASSISTED, set("20", "5"), set("20", "7"))),
            workout(1, ex(ExerciseType.ASSISTED, set("40", "12")))
        )
        val rec = computeExerciseRecords("Ex", history)
        assertEquals(ExerciseType.ASSISTED, rec.type)
        assertEquals(20f, rec.minAssistKg)
        assertEquals("7", rec.bestSet?.reps) // least help, then most reps
        assertEquals(listOf(40f, 20f), rec.chartPoints.map { it.second }) // oldest first, least assistance per session
    }

    @Test
    fun weightSuggestions_onlyForLoadedLifts() {
        for (type in ExerciseType.entries) {
            val advice = suggestNextWeight("Any", ex(type, set("40", "8"), set("40", "8")), emptyList(), 2.5f)
            if (type.hasWeightSuggestions) assertTrue(type.name, advice != null) else assertNull(type.name, advice)
        }
        // Weighted with nothing added (blank or zero) has no load to suggest from.
        assertNull(suggestNextWeight("Any", ex(ExerciseType.WEIGHTED_BODYWEIGHT, set("", "8"), set("0", "8")), emptyList(), 2.5f))
    }

    @Test
    fun timeTyping_fillsFromTheRightLikeAMicrowave() {
        assertEquals(5, clockDigitsToSeconds("5"))
        assertEquals(45, clockDigitsToSeconds("45"))
        assertEquals(90, clockDigitsToSeconds("130"))
        assertEquals(25 * 60, clockDigitsToSeconds("2500"))
        assertEquals(3600, clockDigitsToSeconds("10000"))
        assertEquals("1:30", formatClock(clockDigitsToSeconds("130").toLong()))
    }

    @Test
    fun library_hasTypesForBodyweightAndCardio() {
        assertEquals(ExerciseType.BODYWEIGHT, ExerciseDatabase.find("Pull-Up")?.type)
        assertEquals(ExerciseType.ASSISTED, ExerciseDatabase.find("Assisted Pull-Up")?.type)
        assertEquals(ExerciseType.DURATION, ExerciseDatabase.find("plank")?.type)
        assertEquals(ExerciseType.DISTANCE_TIME, ExerciseDatabase.find("Rowing Machine")?.type)
        assertEquals(ExerciseType.WEIGHT_REPS, ExerciseDatabase.find("Barbell Bench Press")?.type)
        assertTrue(ExerciseDatabase.search("", muscle = "Cardio").isNotEmpty())
    }
}
