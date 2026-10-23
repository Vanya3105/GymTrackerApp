package com.example.gymtracker

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PercentProgramsTest {

    @After
    fun resetUnit() = Units.set(WeightUnit.KG)

    private val squat = PROGRAM_LIFTS.getValue("5/3/1 Squat")
    private val bench = PROGRAM_LIFTS.getValue("5/3/1 Bench")

    private fun session(id: Long, name: String = "Back Squat") = CompletedWorkout(
        id = id, splitTitle = "5/3/1 Squat", dateKey = "", displayDate = "", durationSeconds = 0, bodyWeightKg = 80f,
        photoPath = null, skippedExercises = emptyList(),
        exercises = listOf(ExerciseEntry(name, sets = listOf(SetEntry(1, "100", "5", isCompleted = true))))
    )

    @Test
    fun week1_isThe5sWave_withWarmups() {
        val day = planLiftDay(squat, TrainingMax(100f, 1000), emptyList())
        assertEquals(0, day.week)
        assertEquals(100f, day.trainingMaxKg)
        assertEquals(listOf(0.40f, 0.50f, 0.60f, 0.65f, 0.75f, 0.85f), day.sets.map { it.percent })
        assertEquals(listOf(5, 5, 3, 5, 5, 5), day.sets.map { it.reps })
        assertEquals(3, day.sets.count { it.warmup })
        assertTrue(day.sets.last().amrap)
        assertEquals("5/3/1 · Week 1 of 4 (5s)", day.title)
    }

    @Test
    fun weeksAdvanceWithSessionsAfterTheTrainingMax_andCyclesRaiseIt() {
        val tm = TrainingMax(100f, 1000)
        val before = session(500) // logged before the TM was set: doesn't count
        assertEquals(1, planLiftDay(squat, tm, listOf(session(2000), before)).week)
        assertEquals("5/3/1", planLiftDay(squat, tm, (2000L..2001L).map { session(it) }).title.substringAfter("(").removeSuffix(")"))
        assertEquals("deload", planLiftDay(squat, tm, (2000L..2002L).map { session(it) }).title.substringAfter("(").removeSuffix(")"))
        val nextCycle = planLiftDay(squat, tm, (2000L..2003L).map { session(it) })
        assertEquals(0, nextCycle.week)
        assertEquals(1, nextCycle.cycle)
        assertEquals(105f, nextCycle.trainingMaxKg) // lower body +5 kg
        assertEquals(102.5f, planLiftDay(bench, tm, (2000L..2003L).map { session(it, "Barbell Bench Press") }).trainingMaxKg)
        // Another exercise on the same day doesn't move the squat along.
        assertEquals(0, planLiftDay(squat, tm, listOf(session(2000, "Leg Press"))).week)
    }

    @Test
    fun weights_roundDownToPlates() {
        assertEquals("85", percentWeight(100f, 0.85f))
        assertEquals("85", percentWeight(102f, 0.85f)) // 86.7 → 85
        assertEquals("107.5", formatKgClean(suggestedTrainingMaxKg(120f)))
        assertEquals(0f, suggestedTrainingMaxKg(0f)) // nothing logged: no suggestion
        Units.set(WeightUnit.LB)
        val tm225lb = WeightUnit.LB.toKg(225f)
        assertEquals("145", WeightUnit.LB.number(parseWeightClean(percentWeight(tm225lb, 0.65f), 0f))) // 146.25 → 145
        assertEquals(WeightUnit.LB.toKg(10f), cycleIncrementKg(squat)) // +10 lb for lower body
    }

    @Test
    fun liftPlan_onlyForTheDaysMainLift() {
        assertEquals(squat, liftPlanFor("5/3/1 Squat", "back squat"))
        assertNull(liftPlanFor("5/3/1 Squat", "Leg Press"))
        assertNull(liftPlanFor("Legs", "Back Squat"))
        assertTrue(SPLIT_PROGRAMS.getValue("WENDLER_531").splits.all { it.title in PROGRAM_LIFTS })
    }

    @Test
    fun programmedLifts_getNoWeightSuggestion() {
        val ex = ExerciseEntry("Back Squat", sets = listOf(SetEntry(1, "100", "5", isCompleted = true)), programmed = true)
        assertNull(suggestNextWeight("5/3/1 Squat", ex, emptyList(), 2.5f))
    }
}
