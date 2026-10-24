package com.example.gymtracker

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PercentProgramsTest {

    @After
    fun resetUnit() = Units.set(WeightUnit.KG)

    private val squat = PROGRAM_LIFTS.getValue("5/3/1 Squat").single()
    private val bench = PROGRAM_LIFTS.getValue("5/3/1 Bench").single()

    private fun session(id: Long, name: String = "Back Squat", day: String = "5/3/1 Squat") = CompletedWorkout(
        id = id, splitTitle = day, dateKey = "", displayDate = "", durationSeconds = 0, bodyWeightKg = 80f,
        photoPath = null, skippedExercises = emptyList(),
        exercises = listOf(ExerciseEntry(name, sets = listOf(SetEntry(1, "100", "5", isCompleted = true))))
    )

    @Test
    fun week1_isThe5sWave_withWarmups() {
        val day = planLiftDay(squat, TrainingMax(100f, 1000), emptyList())
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
        assertTrue(planLiftDay(squat, tm, listOf(session(2000), before)).title.contains("Week 2"))
        assertEquals("5/3/1", planLiftDay(squat, tm, (2000L..2001L).map { session(it) }).title.substringAfter("(").removeSuffix(")"))
        assertEquals("deload", planLiftDay(squat, tm, (2000L..2002L).map { session(it) }).title.substringAfter("(").removeSuffix(")"))
        val nextCycle = planLiftDay(squat, tm, (2000L..2003L).map { session(it) })
        assertTrue(nextCycle.title.contains("Week 1"))
        assertEquals(105f, nextCycle.trainingMaxKg) // a finished cycle: lower body +5 kg
        assertEquals(102.5f, planLiftDay(bench, tm, (2000L..2003L).map { session(it, "Barbell Bench Press", "5/3/1 Bench") }).trainingMaxKg)
        // Another exercise on the same day doesn't move the squat along.
        assertTrue(planLiftDay(squat, tm, listOf(session(2000, "Leg Press"))).title.contains("Week 1"))
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

    // --- GZCLP ---

    private val gzT1 = liftPlanFor("GZCLP A1", "Back Squat")!!
    private val gzT2 = liftPlanFor("GZCLP A1", "Barbell Bench Press")!!

    private fun gz(id: Long, day: String, name: String, weight: String, vararg reps: Int) = CompletedWorkout(
        id = id, splitTitle = day, dateKey = "", displayDate = "", durationSeconds = 0, bodyWeightKg = 80f,
        photoPath = null, skippedExercises = emptyList(),
        exercises = listOf(ExerciseEntry(name, sets = reps.mapIndexed { i, r -> SetEntry(i + 1, weight, "$r", isCompleted = true) }))
    )

    @Test
    fun gzclp_startsFromTheTrainingMax() {
        val tm = TrainingMax(100f, 1000)
        val t1 = planLiftDay(gzT1, tm, emptyList())
        assertEquals("85", t1.weightFor(t1.sets.last()))
        assertEquals(5, t1.sets.count { !it.warmup })
        assertTrue(t1.sets.last().amrap)
        assertEquals("GZCLP T1 · 5×3+ (stage 1 of 3)", t1.title)
        val t2 = planLiftDay(gzT2, tm, emptyList())
        assertEquals("65", t2.weightFor(t2.sets.last()))
        assertEquals(listOf(10, 10, 10), t2.sets.map { it.reps })
    }

    @Test
    fun gzclp_t1_addsWeightOnSuccess_changesStageOnMiss_resetsAfterLastStage() {
        val tm = TrainingMax(100f, 1000)
        // 5×3 all hit at 85 → 90 (lower body +5)
        var day = planLiftDay(gzT1, tm, listOf(gz(2000, "GZCLP A1", "Back Squat", "85", 3, 3, 3, 3, 5)))
        assertEquals("90", day.weightFor(day.sets.last()))
        assertTrue(day.title.contains("stage 1"))
        // Missed a rep at 90 → 6×2 at 90
        day = planLiftDay(gzT1, tm, listOf(gz(3000, "GZCLP A1", "Back Squat", "90", 3, 3, 3, 2, 2)))
        assertEquals(6, day.sets.count { !it.warmup })
        assertEquals("90", day.weightFor(day.sets.last()))
        // Missed at 10×1 → stage 1 again at 85% of 90 = 76.5 → 75
        day = planLiftDay(gzT1, tm, listOf(gz(4000, "GZCLP A1", "Back Squat", "90", 1, 1, 1, 1, 1, 1, 1, 1, 1, 0)))
        assertTrue(day.title.contains("stage 1"))
        assertEquals("75", day.weightFor(day.sets.last()))
        // The same lift as T2 on another day doesn't count for T1.
        day = planLiftDay(gzT1, tm, listOf(gz(5000, "GZCLP A2", "Back Squat", "60", 10, 10, 10)))
        assertEquals("85", day.weightFor(day.sets.last()))
    }

    @Test
    fun gzclp_t2_stagesByReps() {
        val tm = TrainingMax(100f, 1000)
        val missed = planLiftDay(gzT2, tm, listOf(gz(2000, "GZCLP A1", "Barbell Bench Press", "65", 10, 9, 8)))
        assertEquals(listOf(8, 8, 8), missed.sets.map { it.reps })
        val hit = planLiftDay(gzT2, tm, listOf(gz(2000, "GZCLP A1", "Barbell Bench Press", "65", 10, 10, 10)))
        assertEquals("67.5", hit.weightFor(hit.sets.last())) // upper body +2.5
    }

    // --- nSuns ---

    @Test
    fun nsuns_secondaryLiftsUseAnotherLiftsTrainingMax() {
        val sumo = liftPlanFor("nSuns Day 2", "Sumo Deadlift")!!
        assertEquals("Deadlift", sumo.trainingMaxOf)
        val day = planLiftDay(sumo, TrainingMax(200f, 1000), emptyList())
        assertEquals(8, day.sets.size)
        assertEquals("100", day.weightFor(day.sets.first())) // 50% of the deadlift TM
        assertTrue(day.title.contains("T2"))
        assertNull(day.amrapText())
        val heavy = planLiftDay(liftPlanFor("nSuns Day 2", "Back Squat")!!, TrainingMax(100f, 1000), emptyList())
        assertEquals("sets 3 and 9: as many reps as you can", heavy.amrapText())
        assertEquals("last set: as many reps as you can", planLiftDay(squat, TrainingMax(100f, 1000), emptyList()).amrapText())
        assertEquals(listOf("5/3/1", "GZCLP", "nSuns"), programsUsingTrainingMaxOf("Deadlift"))
        assertTrue(programsUsingTrainingMaxOf("Sumo Deadlift").isEmpty())
    }

    @Test
    fun nsuns_onePlusSetsRaiseTheTrainingMax() {
        val tm = TrainingMax(100f, 1000)
        fun heavy(id: Long, onePlusReps: Int) = gz(id, "nSuns Day 2", "Back Squat", "95", 5, 3, onePlusReps, 3, 3, 3, 5, 5, 5)
        assertEquals(100f, currentTrainingMaxKg("Back Squat", tm, listOf(heavy(2000, 1))))
        assertEquals(102.5f, currentTrainingMaxKg("Back Squat", tm, listOf(heavy(2000, 2))))
        assertEquals(105f, currentTrainingMaxKg("Back Squat", tm, listOf(heavy(2000, 4))))
        assertEquals(107.5f + 5f, currentTrainingMaxKg("Back Squat", tm, listOf(heavy(3000, 6), heavy(2000, 4))))
        // Front squat (T2 off the squat TM) uses the raised TM too.
        val front = planLiftDay(liftPlanFor("nSuns Day 4", "Front Squat")!!, tm, listOf(heavy(2000, 4)))
        assertEquals(105f, front.trainingMaxKg)
    }

    @Test
    fun builtInPrograms_dayTitlesAllHaveLifts() {
        for (id in listOf("WENDLER_531", "GZCLP", "NSUNS_5")) {
            assertTrue(id, SPLIT_PROGRAMS.getValue(id).splits.all { it.title in PROGRAM_LIFTS })
        }
    }

    @Test
    fun programmedLifts_getNoWeightSuggestion() {
        val ex = ExerciseEntry("Back Squat", sets = listOf(SetEntry(1, "100", "5", isCompleted = true)), programmed = true)
        assertNull(suggestNextWeight("5/3/1 Squat", ex, emptyList(), 2.5f))
    }
}
