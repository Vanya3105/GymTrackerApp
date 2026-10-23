package com.example.gymtracker

import androidx.compose.runtime.Immutable
import kotlin.math.floor

// ============================================================================
// PERCENTAGE PROGRAMS (5/3/1 …)
// ============================================================================
// Some built-in program days are built around one main lift whose sets are percentages of a
// training max (TM). Where you are in the program is worked out from history — the number of
// sessions of that lift since its TM was set — so nothing extra is stored and it can't drift.

/** A lift's training max and when it was set; sessions after [setAtMillis] advance the program. */
@Immutable
data class TrainingMax(val kg: Float, val setAtMillis: Long)

@Immutable
data class PlannedSet(val percent: Float, val reps: Int, val amrap: Boolean = false, val warmup: Boolean = false)

enum class PercentScheme(val label: String) { WENDLER_531("5/3/1") }

/** The main lift a program day is built around. */
@Immutable
data class LiftPlan(val exercise: String, val scheme: PercentScheme, val lowerBody: Boolean)

/**
 * Program day title → its main lift. Keyed by title, like everything else about days, so a
 * customized copy of the program keeps working; renaming the day turns it into a normal day.
 */
val PROGRAM_LIFTS: Map<String, LiftPlan> = mapOf(
    "5/3/1 Squat" to LiftPlan("Back Squat", PercentScheme.WENDLER_531, lowerBody = true),
    "5/3/1 Bench" to LiftPlan("Barbell Bench Press", PercentScheme.WENDLER_531, lowerBody = false),
    "5/3/1 Deadlift" to LiftPlan("Deadlift", PercentScheme.WENDLER_531, lowerBody = true),
    "5/3/1 Press" to LiftPlan("Overhead Press", PercentScheme.WENDLER_531, lowerBody = false)
)

/** The plan for [exerciseName] on [dayTitle], if that day is built around it. */
fun liftPlanFor(dayTitle: String, exerciseName: String): LiftPlan? =
    PROGRAM_LIFTS[dayTitle]?.takeIf { exerciseKey(it.exercise) == exerciseKey(exerciseName) }

/** What a percentage lift calls for today. */
@Immutable
data class LiftDay(
    /** 0-based week within the cycle. */
    val week: Int,
    val cycle: Int,
    val trainingMaxKg: Float,
    val sets: List<PlannedSet>,
    /** "5/3/1 · Week 1 of 4 (5s)". */
    val title: String
)

private val WENDLER_WARMUP = listOf(PlannedSet(0.40f, 5, warmup = true), PlannedSet(0.50f, 5, warmup = true), PlannedSet(0.60f, 3, warmup = true))

private val WENDLER_WEEKS = listOf(
    "5s" to listOf(PlannedSet(0.65f, 5), PlannedSet(0.75f, 5), PlannedSet(0.85f, 5, amrap = true)),
    "3s" to listOf(PlannedSet(0.70f, 3), PlannedSet(0.80f, 3), PlannedSet(0.90f, 3, amrap = true)),
    "5/3/1" to listOf(PlannedSet(0.75f, 5), PlannedSet(0.85f, 3), PlannedSet(0.95f, 1, amrap = true)),
    "deload" to listOf(PlannedSet(0.40f, 5), PlannedSet(0.50f, 5), PlannedSet(0.60f, 5))
)

/** Training max increase per completed cycle, in kg: 2.5/5 kg, or 5/10 lb for pound users. */
fun cycleIncrementKg(plan: LiftPlan, unit: WeightUnit = Units.current): Float =
    if (unit == WeightUnit.LB) unit.toKg(if (plan.lowerBody) 10f else 5f) else if (plan.lowerBody) 5f else 2.5f

/**
 * Today's sets for [plan]. Week = sessions of the lift (with logged work sets) since the TM was
 * set, modulo 4; every finished cycle adds [cycleIncrementKg] to the TM.
 */
fun planLiftDay(plan: LiftPlan, tm: TrainingMax, history: List<CompletedWorkout>, unit: WeightUnit = Units.current): LiftDay {
    val key = exerciseKey(plan.exercise)
    val sessions = history.count { w ->
        w.id > tm.setAtMillis && w.exercises.any { !it.isSkipped && exerciseKey(it.name) == key && it.workSets.isNotEmpty() }
    }
    val week = sessions % 4
    val cycle = sessions / 4
    val trainingMax = tm.kg + cycle * cycleIncrementKg(plan, unit)
    val (weekName, work) = WENDLER_WEEKS[week]
    return LiftDay(
        week = week,
        cycle = cycle,
        trainingMaxKg = trainingMax,
        sets = WENDLER_WARMUP + work,
        title = "5/3/1 · Week ${week + 1} of 4 ($weekName)"
    )
}

/** [percent] of [trainingMaxKg], rounded down to the unit's plate step (2.5 kg / 5 lb), as a stored kg string. */
fun percentWeight(trainingMaxKg: Float, percent: Float, unit: WeightUnit = Units.current): String {
    val value = unit.fromKg(trainingMaxKg * percent)
    val step = unit.defaultStep
    return unit.stored((floor(value / step + 0.0001f) * step).coerceAtLeast(step))
}

/** The suggested starting TM: 90% of the best estimated 1RM, rounded down to the plate step; 0 with nothing logged. */
fun suggestedTrainingMaxKg(best1RmKg: Float, unit: WeightUnit = Units.current): Float =
    if (best1RmKg <= 0f) 0f else parseWeightClean(percentWeight(best1RmKg, 0.9f, unit), 0f)
