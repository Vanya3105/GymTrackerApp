package com.example.gymtracker

import androidx.compose.runtime.Immutable
import kotlin.math.floor

// ============================================================================
// PERCENTAGE PROGRAMS (5/3/1, GZCLP, nSuns)
// ============================================================================
// Some built-in program days are built around main lifts whose sets come from a training max
// (TM). Where you are in the program is worked out from history (sessions of the lift since its
// TM was set), so nothing extra is stored and it can't drift out of sync.

/** A lift's training max and when it was set; sessions after [setAtMillis] move the program on. */
@Immutable
data class TrainingMax(val kg: Float, val setAtMillis: Long)

@Immutable
data class PlannedSet(val percent: Float, val reps: Int, val amrap: Boolean = false, val warmup: Boolean = false)

enum class PercentScheme(val program: String) {
    WENDLER_531("5/3/1"),
    GZCLP_T1("GZCLP"),
    GZCLP_T2("GZCLP"),
    NSUNS_BENCH_VOLUME("nSuns"),
    NSUNS_HEAVY("nSuns"),
    NSUNS_DEADLIFT("nSuns"),
    NSUNS_BENCH_HEAVY("nSuns"),
    NSUNS_T2_70("nSuns"),
    NSUNS_T2_SUMO("nSuns"),
    NSUNS_T2_60("nSuns"),
    NSUNS_T2_55("nSuns");

    val label: String get() = program
}

/**
 * A main lift on a program day. [trainingMaxOf] is the lift whose TM sets the weights (nSuns'
 * secondary lifts work off another lift's TM, e.g. sumo deadlift off the deadlift).
 */
@Immutable
data class LiftPlan(
    val exercise: String,
    val scheme: PercentScheme,
    val lowerBody: Boolean,
    val trainingMaxOf: String = exercise
)

/**
 * Program day title → its main lifts. Keyed by title, like everything else about days, so a
 * customized copy of a program keeps working; renaming a day turns it into a normal day.
 */
val PROGRAM_LIFTS: Map<String, List<LiftPlan>> = mapOf(
    "5/3/1 Squat" to listOf(LiftPlan("Back Squat", PercentScheme.WENDLER_531, lowerBody = true)),
    "5/3/1 Bench" to listOf(LiftPlan("Barbell Bench Press", PercentScheme.WENDLER_531, lowerBody = false)),
    "5/3/1 Deadlift" to listOf(LiftPlan("Deadlift", PercentScheme.WENDLER_531, lowerBody = true)),
    "5/3/1 Press" to listOf(LiftPlan("Overhead Press", PercentScheme.WENDLER_531, lowerBody = false)),

    "GZCLP A1" to listOf(
        LiftPlan("Back Squat", PercentScheme.GZCLP_T1, lowerBody = true),
        LiftPlan("Barbell Bench Press", PercentScheme.GZCLP_T2, lowerBody = false)
    ),
    "GZCLP B1" to listOf(
        LiftPlan("Overhead Press", PercentScheme.GZCLP_T1, lowerBody = false),
        LiftPlan("Deadlift", PercentScheme.GZCLP_T2, lowerBody = true)
    ),
    "GZCLP A2" to listOf(
        LiftPlan("Barbell Bench Press", PercentScheme.GZCLP_T1, lowerBody = false),
        LiftPlan("Back Squat", PercentScheme.GZCLP_T2, lowerBody = true)
    ),
    "GZCLP B2" to listOf(
        LiftPlan("Deadlift", PercentScheme.GZCLP_T1, lowerBody = true),
        LiftPlan("Overhead Press", PercentScheme.GZCLP_T2, lowerBody = false)
    ),

    "nSuns Day 1" to listOf(
        LiftPlan("Barbell Bench Press", PercentScheme.NSUNS_BENCH_VOLUME, lowerBody = false),
        LiftPlan("Overhead Press", PercentScheme.NSUNS_T2_70, lowerBody = false)
    ),
    "nSuns Day 2" to listOf(
        LiftPlan("Back Squat", PercentScheme.NSUNS_HEAVY, lowerBody = true),
        LiftPlan("Sumo Deadlift", PercentScheme.NSUNS_T2_SUMO, lowerBody = true, trainingMaxOf = "Deadlift")
    ),
    "nSuns Day 3" to listOf(
        LiftPlan("Overhead Press", PercentScheme.NSUNS_HEAVY, lowerBody = false),
        LiftPlan("Incline Barbell Bench Press", PercentScheme.NSUNS_T2_60, lowerBody = false, trainingMaxOf = "Barbell Bench Press")
    ),
    "nSuns Day 4" to listOf(
        LiftPlan("Deadlift", PercentScheme.NSUNS_DEADLIFT, lowerBody = true),
        LiftPlan("Front Squat", PercentScheme.NSUNS_T2_55, lowerBody = true, trainingMaxOf = "Back Squat")
    ),
    "nSuns Day 5" to listOf(
        LiftPlan("Barbell Bench Press", PercentScheme.NSUNS_BENCH_HEAVY, lowerBody = false),
        LiftPlan("Close-Grip Bench Press", PercentScheme.NSUNS_T2_60, lowerBody = false, trainingMaxOf = "Barbell Bench Press")
    )
)

/** The plan for [exerciseName] on [dayTitle], if that day is built around it. */
fun liftPlanFor(dayTitle: String, exerciseName: String): LiftPlan? =
    PROGRAM_LIFTS[dayTitle]?.firstOrNull { exerciseKey(it.exercise) == exerciseKey(exerciseName) }

/** Programs whose weights come from [exerciseName]'s training max ("5/3/1", "nSuns"), or empty. */
fun programsUsingTrainingMaxOf(exerciseName: String): List<String> =
    PROGRAM_LIFTS.values.flatten()
        .filter { exerciseKey(it.trainingMaxOf) == exerciseKey(exerciseName) }
        .map { it.scheme.program }
        .distinct()

/** What a programmed lift calls for today. */
@Immutable
data class LiftDay(
    val sets: List<PlannedSet>,
    /** The day's working weight when the program works in weights rather than TM percentages (GZCLP). */
    val weightKg: Float?,
    val trainingMaxKg: Float,
    /** "5/3/1 · Week 1 of 4 (5s)", "GZCLP T1 · 5×3+ (stage 1 of 3)", "nSuns T1". */
    val title: String
)

// --- Set tables -------------------------------------------------------------

private fun sets(vararg s: Pair<Float, Int>, amrapAt: Set<Int> = emptySet()) =
    s.mapIndexed { i, (p, r) -> PlannedSet(p, r, amrap = i in amrapAt) }

private val WARMUP = listOf(PlannedSet(0.40f, 5, warmup = true), PlannedSet(0.50f, 5, warmup = true), PlannedSet(0.60f, 3, warmup = true))

private val WENDLER_WEEKS = listOf(
    "5s" to sets(0.65f to 5, 0.75f to 5, 0.85f to 5, amrapAt = setOf(2)),
    "3s" to sets(0.70f to 3, 0.80f to 3, 0.90f to 3, amrapAt = setOf(2)),
    "5/3/1" to sets(0.75f to 5, 0.85f to 3, 0.95f to 1, amrapAt = setOf(2)),
    "deload" to sets(0.40f to 5, 0.50f to 5, 0.60f to 5)
)

private val NSUNS_TABLES: Map<PercentScheme, List<PlannedSet>> = mapOf(
    PercentScheme.NSUNS_BENCH_VOLUME to sets(0.65f to 8, 0.75f to 6, 0.85f to 4, 0.85f to 4, 0.85f to 4, 0.80f to 5, 0.75f to 6, 0.70f to 7, 0.65f to 8, amrapAt = setOf(8)),
    PercentScheme.NSUNS_HEAVY to sets(0.75f to 5, 0.85f to 3, 0.95f to 1, 0.90f to 3, 0.85f to 3, 0.80f to 3, 0.75f to 5, 0.70f to 5, 0.65f to 5, amrapAt = setOf(2, 8)),
    PercentScheme.NSUNS_DEADLIFT to sets(0.75f to 5, 0.85f to 3, 0.95f to 1, 0.90f to 3, 0.85f to 3, 0.80f to 3, 0.75f to 3, 0.70f to 3, 0.65f to 3, amrapAt = setOf(2, 8)),
    PercentScheme.NSUNS_BENCH_HEAVY to sets(0.75f to 5, 0.85f to 3, 0.95f to 1, 0.90f to 3, 0.85f to 5, 0.80f to 3, 0.75f to 5, 0.70f to 3, 0.65f to 5, amrapAt = setOf(2, 8)),
    PercentScheme.NSUNS_T2_70 to sets(0.50f to 6, 0.60f to 5, 0.70f to 3, 0.70f to 5, 0.70f to 7, 0.70f to 4, 0.70f to 6, 0.70f to 8),
    PercentScheme.NSUNS_T2_SUMO to sets(0.50f to 5, 0.60f to 5, 0.70f to 3, 0.70f to 5, 0.70f to 7, 0.70f to 4, 0.70f to 6, 0.70f to 8),
    PercentScheme.NSUNS_T2_60 to sets(0.40f to 6, 0.50f to 5, 0.60f to 3, 0.60f to 5, 0.60f to 7, 0.60f to 4, 0.60f to 6, 0.60f to 8),
    PercentScheme.NSUNS_T2_55 to sets(0.35f to 5, 0.45f to 5, 0.55f to 3, 0.55f to 5, 0.55f to 7, 0.55f to 4, 0.55f to 6, 0.55f to 8)
)

/** GZCLP stages as sets × reps; T1's last set is "as many as you can". */
private val GZCLP_T1_STAGES = listOf(5 to 3, 6 to 2, 10 to 1)
private val GZCLP_T2_STAGES = listOf(3 to 10, 3 to 8, 3 to 6)

private val NSUNS_PROGRESSION = setOf(PercentScheme.NSUNS_HEAVY, PercentScheme.NSUNS_DEADLIFT, PercentScheme.NSUNS_BENCH_HEAVY)

// --- Progression ------------------------------------------------------------

/** One progression step for this lift: 2.5/5 kg, or 5/10 lb for pound users. */
fun cycleIncrementKg(plan: LiftPlan, unit: WeightUnit = Units.current): Float =
    if (unit == WeightUnit.LB) unit.toKg(if (plan.lowerBody) 10f else 5f) else if (plan.lowerBody) 5f else 2.5f

/**
 * Entries of [exercise] logged with work sets on a day that uses it with one of [schemes],
 * after [since], newest first.
 */
private fun sessionsOf(exercise: String, schemes: Set<PercentScheme>, history: List<CompletedWorkout>, since: Long): List<ExerciseEntry> {
    val key = exerciseKey(exercise)
    return history.sortedByDescending { it.id }.filter { it.id > since }.mapNotNull { w ->
        val onSchemeDay = PROGRAM_LIFTS[w.splitTitle].orEmpty().any { exerciseKey(it.exercise) == key && it.scheme in schemes }
        if (!onSchemeDay) return@mapNotNull null
        w.exercises.firstOrNull { !it.isSkipped && exerciseKey(it.name) == key && it.workSets.isNotEmpty() }
    }
}

/**
 * The training max in effect today for [trainingMaxOf]: the saved value plus what the programs
 * have added since. 5/3/1 adds a step per finished four-week cycle; nSuns adds 0–3 plate steps
 * for each "1+" set, depending on its reps (0–1, 2–3, 4–5, 6+).
 */
fun currentTrainingMaxKg(trainingMaxOf: String, tm: TrainingMax, history: List<CompletedWorkout>, unit: WeightUnit = Units.current): Float {
    val key = exerciseKey(trainingMaxOf)
    val plan531 = PROGRAM_LIFTS.values.flatten().firstOrNull { it.scheme == PercentScheme.WENDLER_531 && exerciseKey(it.exercise) == key }
    val fromCycles = plan531?.let {
        sessionsOf(it.exercise, setOf(PercentScheme.WENDLER_531), history, tm.setAtMillis).size / 4 * cycleIncrementKg(it, unit)
    } ?: 0f

    val nsunsSteps = sessionsOf(trainingMaxOf, NSUNS_PROGRESSION, history, tm.setAtMillis).sumOf { entry ->
        // The "1+" set is the third work set (95%).
        when (entry.workSets.getOrNull(2)?.repCount ?: 0) {
            in 0..1 -> 0
            in 2..3 -> 1
            in 4..5 -> 2
            else -> 3
        }.toInt()
    }
    return tm.kg + fromCycles + nsunsSteps * unit.toKg(unit.defaultStep)
}

/** Today's sets for [plan], given the TM of [LiftPlan.trainingMaxOf]. */
fun planLiftDay(plan: LiftPlan, tm: TrainingMax, history: List<CompletedWorkout>, unit: WeightUnit = Units.current): LiftDay =
    when (plan.scheme) {
        PercentScheme.WENDLER_531 -> {
            val week = sessionsOf(plan.exercise, setOf(PercentScheme.WENDLER_531), history, tm.setAtMillis).size % 4
            val (weekName, work) = WENDLER_WEEKS[week]
            LiftDay(WARMUP + work, null, currentTrainingMaxKg(plan.trainingMaxOf, tm, history, unit), "5/3/1 · Week ${week + 1} of 4 ($weekName)")
        }
        PercentScheme.GZCLP_T1, PercentScheme.GZCLP_T2 -> planGzclp(plan, tm, history, unit)
        else -> {
            val tier = if (exerciseKey(plan.trainingMaxOf) == exerciseKey(plan.exercise)) "T1" else "T2 (off your ${plan.trainingMaxOf} TM)"
            LiftDay(NSUNS_TABLES.getValue(plan.scheme), null, currentTrainingMaxKg(plan.trainingMaxOf, tm, history, unit), "nSuns $tier")
        }
    }

/**
 * GZCLP: hit every rep and the weight goes up a step next time; miss and you move to the next
 * stage at the same weight; miss the last stage and you start stage 1 again at 85% of it. The
 * last session of the lift in the same tier says where you are; the first starts at 85% (T1) or
 * 65% (T2) of the TM.
 */
private fun planGzclp(plan: LiftPlan, tm: TrainingMax, history: List<CompletedWorkout>, unit: WeightUnit): LiftDay {
    val t1 = plan.scheme == PercentScheme.GZCLP_T1
    val stages = if (t1) GZCLP_T1_STAGES else GZCLP_T2_STAGES
    val last = sessionsOf(plan.exercise, setOf(plan.scheme), history, tm.setAtMillis).firstOrNull()

    var stage = 0
    var weightKg = tm.kg * if (t1) 0.85f else 0.65f
    if (last != null) {
        // Every non-warm-up set as logged: a missed single (0 reps) still counts as a set.
        val work = last.sets.filter { !it.isWarmupSet }
        val lastWeight = work.maxOf { it.weightKg }
        // T1 stages differ in set count; T2 stages (always 3 sets) only in reps.
        val lastStage = if (t1) stages.indexOfFirst { it.first == work.size }.takeIf { it >= 0 } ?: 0
        else stages.indexOfFirst { (_, reps) -> work.maxOf { it.repCount } >= reps }.takeIf { it >= 0 } ?: stages.lastIndex
        val success = work.all { it.repCount >= stages[lastStage].second }
        when {
            success -> { stage = lastStage; weightKg = lastWeight + cycleIncrementKg(plan, unit) }
            lastStage < stages.lastIndex -> { stage = lastStage + 1; weightKg = lastWeight }
            else -> { stage = 0; weightKg = lastWeight * 0.85f }
        }
    }
    val (setCount, reps) = stages[stage]
    val work = List(setCount) { i -> PlannedSet(1f, reps, amrap = t1 && i == setCount - 1) }
    val warmups = if (t1) listOf(PlannedSet(0.5f, 5, warmup = true), PlannedSet(0.7f, 3, warmup = true)) else emptyList()
    return LiftDay(
        sets = warmups + work,
        weightKg = parseWeightClean(percentWeight(weightKg, 1f, unit), 0f),
        trainingMaxKg = tm.kg,
        title = "GZCLP ${if (t1) "T1" else "T2"} · $setCount×$reps${if (t1) "+" else ""} (stage ${stage + 1} of 3)"
    )
}

/** [percent] of [baseKg], rounded down to the unit's plate step (2.5 kg / 5 lb), as a stored kg string. */
fun percentWeight(baseKg: Float, percent: Float, unit: WeightUnit = Units.current): String {
    val value = unit.fromKg(baseKg * percent)
    val step = unit.defaultStep
    return unit.stored((floor(value / step + 0.0001f) * step).coerceAtLeast(step))
}

/** "last set: as many reps as you can" / "sets 3 and 9: …", counting work sets only; null if none. */
fun LiftDay.amrapText(): String? {
    val work = sets.filter { !it.warmup }
    val numbers = work.mapIndexedNotNull { i, s -> if (s.amrap) i + 1 else null }
    return when {
        numbers.isEmpty() -> null
        numbers == listOf(work.size) -> "last set: as many reps as you can"
        else -> "sets ${numbers.joinToString(" and ")}: as many reps as you can"
    }
}

/** The weight of one planned set: a percentage of the TM, or of the day's working weight (GZCLP). */
fun LiftDay.weightFor(set: PlannedSet, unit: WeightUnit = Units.current): String =
    percentWeight(weightKg ?: trainingMaxKg, set.percent, unit)

/** The suggested starting TM: 90% of the best estimated 1RM, rounded down to the plate step; 0 with nothing logged. */
fun suggestedTrainingMaxKg(best1RmKg: Float, unit: WeightUnit = Units.current): Float =
    if (best1RmKg <= 0f) 0f else parseWeightClean(percentWeight(best1RmKg, 0.9f, unit), 0f)
