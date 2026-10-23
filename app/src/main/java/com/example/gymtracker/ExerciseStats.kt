package com.example.gymtracker

import androidx.compose.runtime.Immutable
import kotlin.math.roundToInt

// ============================================================================
// PER-EXERCISE HISTORY & RECORDS (powers the exercise detail screen)
// ============================================================================

/** Rep counts shown in the "rep records" table. */
val REP_RECORD_TARGETS = listOf(1, 3, 5, 8, 10, 12)

/** One workout's worth of an exercise. Only valid work sets count toward the numbers. */
@Immutable
data class ExerciseSession(
    val workoutId: Long,
    val splitTitle: String,
    val displayDate: String,
    val sets: List<SetEntry>,
    val topWeightKg: Float,
    val estimated1RmKg: Float,
    val volumeKg: Float,
    /** Beat every earlier session's estimated 1-rep max (never set on the first session). */
    val isRecord: Boolean
)

/** Heaviest weight lifted for at least [reps] reps, and the workout where that first happened. */
@Immutable
data class RepRecord(val reps: Int, val weightKg: Float, val workoutId: Long)

@Immutable
data class ExerciseHistory(
    val name: String,
    /** Newest first; sessions with no valid work sets are left out. */
    val sessions: List<ExerciseSession>,
    val repRecords: List<RepRecord>,
    val best1RmKg: Float,
    val heaviestKg: Float
)

enum class ChartMetric(val label: String) {
    EST_1RM("Est. 1RM"),
    TOP_WEIGHT("Top weight"),
    VOLUME("Volume");

    fun valueOf(session: ExerciseSession): Float = when (this) {
        EST_1RM -> session.estimated1RmKg
        TOP_WEIGHT -> session.topWeightKg
        VOLUME -> session.volumeKg
    }
}

enum class ChartRange(val label: String, val days: Int?) {
    THREE_MONTHS("3M", 91),
    YEAR("1Y", 365),
    ALL("All", null)
}

fun buildExerciseHistory(exerciseName: String, history: List<CompletedWorkout>): ExerciseHistory {
    val key = exerciseKey(exerciseName)
    val chronological = ArrayList<ExerciseSession>()
    val repBest = HashMap<Int, RepRecord>()
    var best1Rm = 0f

    for (workout in history.sortedBy { it.id }) {
        // An exercise can appear twice in one workout (added again later); merge its sets.
        val sets = workout.exercises
            .filter { !it.isSkipped && exerciseKey(it.name) == key }
            .flatMap { it.sets }
        val workSets = sets.filter { it.isValidWorkSet }
        if (workSets.isEmpty()) continue

        // Rounded once here so records, tiles and the chart all agree with what's shown,
        // and float noise between equal lifts (92.5×8 vs 95×7) never counts as a record.
        val session1Rm = roundToHalf(workSets.maxOf { calculateEstimated1Rm(it.weightKg, it.repCount) })
        chronological += ExerciseSession(
            workoutId = workout.id,
            splitTitle = workout.splitTitle,
            displayDate = workout.displayDate,
            sets = sets.filter { it.weightKg > 0f && it.repCount > 0 },
            topWeightKg = workSets.maxOf { it.weightKg },
            estimated1RmKg = session1Rm,
            volumeKg = workSets.sumOf { (it.weightKg * it.repCount).toDouble() }.toFloat(),
            isRecord = chronological.isNotEmpty() && session1Rm > best1Rm
        )
        best1Rm = maxOf(best1Rm, session1Rm)

        for (target in REP_RECORD_TARGETS) {
            val heaviest = workSets.filter { it.repCount >= target }.maxOfOrNull { it.weightKg } ?: continue
            if (heaviest > (repBest[target]?.weightKg ?: 0f)) repBest[target] = RepRecord(target, heaviest, workout.id)
        }
    }

    return ExerciseHistory(
        name = exerciseName,
        sessions = chronological.asReversed().toList(),
        repRecords = REP_RECORD_TARGETS.mapNotNull { repBest[it] },
        best1RmKg = best1Rm,
        heaviestKg = chronological.maxOfOrNull { it.topWeightKg } ?: 0f
    )
}

/** Oldest-first (time, value) points for the chart, limited to [range] counted back from [nowMillis]. */
fun ExerciseHistory.chartPoints(metric: ChartMetric, range: ChartRange, nowMillis: Long): List<Pair<Long, Float>> {
    val since = range.days?.let { nowMillis - it * 86_400_000L } ?: Long.MIN_VALUE
    return sessions.asReversed().filter { it.workoutId >= since }.map { it.workoutId to metric.valueOf(it) }
}

fun roundToHalf(v: Float): Float = (v * 2f).roundToInt() / 2f

/** Every exercise that has at least one valid work set, most recently trained first. */
fun loggedExerciseNames(history: List<CompletedWorkout>): List<String> =
    history.sortedByDescending { it.id }
        .flatMap { w -> w.exercises.filter { ex -> !ex.isSkipped && ex.sets.any { it.isValidWorkSet } }.map { it.name } }
        .distinctBy { exerciseKey(it) }
