package com.example.gymtracker

import androidx.compose.runtime.Immutable
import java.util.Locale
import kotlin.math.roundToInt

// ============================================================================
// MUSCLE RECOVERY
// ============================================================================

enum class RecoveryStatus(val label: String) { TIRED("Tired"), RECOVERING("Recovering"), READY("Ready") }

@Immutable
data class MuscleRecoveryItem(
    val muscleGroup: String,
    val recoveryPercent: Int,
    val status: RecoveryStatus,
    val hoursSinceTrained: Int?,
    val weeklyWorkingSets: Int
)

/** Weekly hard sets per muscle that tends to work well for building muscle. */
val WEEKLY_SET_TARGET = 6..16

val RECOVERY_GROUPS = listOf("Chest", "Back", "Shoulders", "Arms", "Legs")

private fun hitsMuscleGroup(splitTitle: String, exName: String, targetMuscle: String, group: String): Boolean {
    val text = "${exName.lowercase(Locale.US)} ${targetMuscle.lowercase(Locale.US)}"
    val split = splitTitle.lowercase(Locale.US)
    fun any(vararg words: String) = words.any { text.contains(it) }
    return when (group) {
        "Chest" -> any("chest", "bench", "pec", "fly", "dip", "push-up") || (targetMuscle.isBlank() && split.contains("push"))
        "Back" -> any("lat", "back", "row", "pull-up", "pullup", "chin", "deadlift", "shrug") || (targetMuscle.isBlank() && split.contains("pull"))
        "Shoulders" -> any("shoulder", "delt", "lateral", "overhead press", "face pull", "arnold", "raise")
        "Arms" -> any("bicep", "tricep", "curl", "pushdown", "extension", "brachialis", "hammer", "skull", "forearm") && !any("leg curl", "leg extension")
        "Legs" -> any("quad", "hamstring", "glute", "calf", "calves", "squat", "rdl", "leg", "lunge", "hip thrust")
        else -> false
    }
}

/**
 * Rough recovery model: a muscle needs 48–78 hours depending on how many sets it got last time.
 * Also counts this week's work sets per group.
 */
fun computeMuscleRecoveryStatus(history: List<CompletedWorkout>): List<MuscleRecoveryItem> {
    val now = System.currentTimeMillis()
    val weekAgo = now - 7L * 24 * 60 * 60 * 1000

    return RECOVERY_GROUPS.map { group ->
        var weeklySets = 0
        var lastTrained: Long? = null
        var lastSessionSets = 0

        for (workout in history) {
            val sets = workout.exercises
                .filter { !it.isSkipped && hitsMuscleGroup(workout.splitTitle, it.name, it.targetMuscle, group) }
                .sumOf { ex -> ex.sets.count { !it.isWarmupSet && (it.repCount > 0 || it.isValidWorkSet(ex.type)) } }
            if (sets == 0) continue
            if (lastTrained == null || workout.id > lastTrained) {
                lastTrained = workout.id
                lastSessionSets = sets
            }
            if (workout.id >= weekAgo) weeklySets += sets
        }

        if (lastTrained == null) {
            MuscleRecoveryItem(group, 100, RecoveryStatus.READY, null, weeklySets)
        } else {
            val hoursAgo = ((now - lastTrained) / (1000L * 60 * 60)).toInt().coerceAtLeast(0)
            val neededHours = (44f + lastSessionSets * 3.5f).coerceIn(48f, 78f)
            val pct = (hoursAgo / neededHours * 100f).roundToInt().coerceIn(12, 100)
            val status = when {
                pct < 55 -> RecoveryStatus.TIRED
                pct < 85 -> RecoveryStatus.RECOVERING
                else -> RecoveryStatus.READY
            }
            MuscleRecoveryItem(group, pct, status, hoursAgo, weeklySets)
        }
    }
}

// ============================================================================
// WEIGHT SUGGESTIONS
// ============================================================================

/** A stored kg string as "62.5 kg" / "135 lb". */
private fun shown(storedKg: String) = formatWeightKg(parseWeightClean(storedKg, 0f))

private fun isCompoundLift(exerciseName: String): Boolean {
    val lower = exerciseName.lowercase(Locale.US)
    return listOf("squat", "bench", "press", "deadlift", "rdl", "row", "pull-up", "pullup", "pulldown", "dip", "lunge").any {
        lower.contains(it)
    } && !lower.contains("pushdown") && !lower.contains("extension") && !lower.contains("fly")
}

/**
 * What to lift next for this exercise. Checks, in order: a big rep drop within today's sets,
 * same-split history ([suggestFromSplitHistory]), then today's sets alone (double progression:
 * hit the top of the rep range on every set, then add weight). Null for exercise types
 * where weight suggestions don't apply (bodyweight, assisted, time, distance).
 */
fun suggestNextWeight(
    splitTitle: String,
    exercise: ExerciseEntry,
    history: List<CompletedWorkout>,
    step: Float
): Suggestion? {
    if (!exercise.type.hasWeightSuggestions) return null
    val isCompound = isCompoundLift(exercise.name)
    val repRange = exercise.target?.repText ?: if (isCompound) "6–8" else "8–12"
    val topReps = exercise.target?.repMax ?: if (isCompound) 8 else 10

    val workSets = exercise.sets.filter { !it.isWarmupSet }
    val doneSets = workSets.filter { it.isCompleted && it.repCount > 0 }

    if (doneSets.size >= 2) {
        val firstReps = parseRepsClean(doneSets.first().reps, 8)
        val latestReps = parseRepsClean(doneSets.last().reps, 8)
        if (firstReps >= 6 && latestReps <= (firstReps * 0.62f).toInt()) {
            val lighter = scaleWeight(doneSets.last().weight, 0.9f, 40f)
            return Suggestion(
                "Your reps fell from $firstReps to $latestReps. Try ${shown(lighter)}, or rest a bit longer.",
                SuggestionTone.CAUTION, lighter
            )
        }
    }

    suggestFromSplitHistory(splitTitle, exercise, history, step)?.let { return it }
    // Weighted bodyweight with nothing added yet: there's no load to build on.
    if (exercise.type == ExerciseType.WEIGHTED_BODYWEIGHT && workSets.none { it.weightKg > 0f }) return null

    val unit = Units.current
    val topWeight = unit.stored(unit.fromKg(workSets.maxOfOrNull { parseWeightClean(it.weight, 40f) } ?: 40f))
    if (doneSets.isNotEmpty()) {
        if (doneSets.any { parseRepsClean(it.reps, 8) in 1..4 }) {
            val lighter = scaleWeight(topWeight, 0.9f, 40f)
            return Suggestion("That's heavy — 4 reps or fewer. Try ${shown(lighter)}.", SuggestionTone.CAUTION, lighter)
        }
        if (doneSets.all { parseRepsClean(it.reps, 8) >= topReps }) {
            val heavier = stepWeight(topWeight, step, 40f)
            return Suggestion("Every set hit $topReps+ reps. Go up to ${shown(heavier)}.", SuggestionTone.GOOD, heavier)
        }
    }

    return Suggestion(
        "Aim for $repRange reps at ${shown(topWeight)}. Hit $topReps on every set, then add ${formatKgClean(step)} ${unit.label}.",
        SuggestionTone.NEUTRAL, topWeight
    )
}
