package com.example.gymtracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseStatsTest {

    private fun set(w: String, r: String, type: String = "NORMAL") =
        SetEntry(setNumber = 1, weight = w, reps = r, intensityType = type, isWarmup = type == "WARMUP", isCompleted = true)

    private fun workout(id: Long, vararg exercises: ExerciseEntry) = CompletedWorkout(
        id = id, splitTitle = "Push", dateKey = "", displayDate = "Day $id", durationSeconds = 0,
        bodyWeightKg = 80f, photoPath = null, skippedExercises = emptyList(), exercises = exercises.toList()
    )

    private fun bench(vararg sets: SetEntry) = ExerciseEntry("Barbell Bench Press", sets = sets.toList())

    private val history = listOf(
        workout(3, bench(set("40", "10", "WARMUP"), set("85", "3"), set("70", "8"))),
        workout(2, bench(set("80", "5"), set("80", "5")), ExerciseEntry("Row", sets = listOf(set("60", "10")))),
        workout(1, bench(set("75", "8"))),
        workout(0, ExerciseEntry("Row", sets = listOf(set("50", "10"))))
    )

    @Test
    fun sessions_areNewestFirst_andOnlyIncludeThisExercise() {
        val h = buildExerciseHistory("barbell bench press", history)
        assertEquals(listOf(3L, 2L, 1L), h.sessions.map { it.workoutId })
    }

    @Test
    fun sessionNumbers_ignoreWarmups() {
        val s = buildExerciseHistory("Barbell Bench Press", history).sessions.first()
        assertEquals(85f, s.topWeightKg)
        assertEquals(85f * 3 + 70f * 8, s.volumeKg)
        assertEquals(93.5f, s.estimated1RmKg) // 85×3 = 93.5 beats 70×8 ≈ 88.7
        assertEquals(3, s.sets.size) // warm-up still listed for the history view
    }

    @Test
    fun records_markOnlySessionsThatBeatAllEarlierOnes() {
        val h = buildExerciseHistory("Barbell Bench Press", history)
        // 75×8 = 95.0, 80×5 = 93.3, 85×3 = 93.5 → no later session beats the first.
        assertEquals(listOf(false, false, false), h.sessions.map { it.isRecord })
        val better = buildExerciseHistory("Barbell Bench Press", history + workout(9, bench(set("90", "5"))))
        assertTrue(better.sessions.first().isRecord)
        assertEquals(105f, better.best1RmKg)
    }

    @Test
    fun repRecords_useAtLeastNReps_andKeepTheFirstWorkoutThatHitIt() {
        val h = buildExerciseHistory("Barbell Bench Press", history + workout(4, bench(set("85", "3"))))
        val byReps = h.repRecords.associateBy { it.reps }
        assertEquals(85f, byReps.getValue(1).weightKg)
        assertEquals(3L, byReps.getValue(1).workoutId) // matched later in workout 4, but 3 was first
        assertEquals(85f, byReps.getValue(3).weightKg)
        assertEquals(80f, byReps.getValue(5).weightKg)
        assertEquals(75f, byReps.getValue(8).weightKg)
        assertFalse(10 in byReps) // nothing done for 10+ work reps
        assertEquals(85f, h.heaviestKg)
    }

    @Test
    fun equalLiftsWithFloatNoise_areNotARecord() {
        // 92.5×8 and 95×7 are the same estimated 1RM on paper (117.17), not in floats.
        val h = buildExerciseHistory("Barbell Bench Press", listOf(
            workout(2, bench(set("95", "7"))),
            workout(1, bench(set("92.5", "8")))
        ))
        assertEquals(listOf(false, false), h.sessions.map { it.isRecord })
        assertEquals(117f, h.best1RmKg)
    }

    @Test
    fun duplicateEntriesInOneWorkout_areMerged() {
        val w = workout(5, bench(set("60", "10")), bench(set("100", "1")))
        val s = buildExerciseHistory("Barbell Bench Press", listOf(w)).sessions.single()
        assertEquals(100f, s.topWeightKg)
        assertEquals(2, s.sets.size)
    }

    @Test
    fun skippedOrEmptyExercises_produceNoSession() {
        val w = workout(5, bench(set("60", "")), ExerciseEntry("Row", isSkipped = true, sets = listOf(set("60", "8"))))
        assertTrue(buildExerciseHistory("Barbell Bench Press", listOf(w)).sessions.isEmpty())
        assertTrue(buildExerciseHistory("Row", listOf(w)).sessions.isEmpty())
    }

    @Test
    fun chartPoints_areOldestFirst_andRespectTheRange() {
        val day = 86_400_000L
        val now = 400 * day
        val h = buildExerciseHistory("Barbell Bench Press", listOf(
            workout(now - 10 * day, bench(set("80", "5"))),
            workout(now - 200 * day, bench(set("70", "5"))),
            workout(now - 390 * day, bench(set("60", "5")))
        ))
        assertEquals(listOf(60f, 70f, 80f), h.chartPoints(ChartMetric.TOP_WEIGHT, ChartRange.ALL, now).map { it.second })
        assertEquals(listOf(70f, 80f), h.chartPoints(ChartMetric.TOP_WEIGHT, ChartRange.YEAR, now).map { it.second })
        assertEquals(listOf(80f), h.chartPoints(ChartMetric.TOP_WEIGHT, ChartRange.THREE_MONTHS, now).map { it.second })
        assertEquals(400f, h.chartPoints(ChartMetric.VOLUME, ChartRange.ALL, now).last().second)
    }

    @Test
    fun loggedExerciseNames_mostRecentFirst_caseInsensitiveUnique() {
        val names = loggedExerciseNames(history + workout(10, ExerciseEntry("row", sets = listOf(set("70", "8")))))
        assertEquals(listOf("row", "Barbell Bench Press"), names)
    }
}
