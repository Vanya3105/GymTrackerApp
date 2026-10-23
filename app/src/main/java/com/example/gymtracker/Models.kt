package com.example.gymtracker

import androidx.compose.runtime.Immutable
import kotlin.math.roundToInt

@Immutable
data class UserProfile(
    val hasCompletedOnboarding: Boolean = false,
    val age: Int = 0,
    val currentWeightKg: Float = 0f,
    val splitProgram: String = "PPL_ARNOLD"
)

@Immutable
data class WeightLogEntry(
    val id: Long,
    val dateKey: String,
    val displayDate: String,
    val weightKg: Float
)

@Immutable
data class AppSettings(
    val themeChoice: String = THEME_SYSTEM,
    val targetFps: Int = 120,
    val hapticsEnabled: Boolean = true,
    /** +/− step for set weights, in [weightUnit]. */
    val weightStep: Float = 2.5f,
    val weightUnit: WeightUnit = WeightUnit.KG,
    val restTimerSeconds: Int = 90,
    val keepScreenAwake: Boolean = true,
    val restSoundEnabled: Boolean = true
)

const val THEME_SYSTEM = "SYSTEM"
const val THEME_LIGHT = "LIGHT"
const val THEME_DARK = "DARK"

@Immutable
data class CompletedWorkout(
    val id: Long,
    val splitTitle: String,
    val dateKey: String,
    val displayDate: String,
    val durationSeconds: Long,
    val bodyWeightKg: Float,
    val photoPath: String?,
    val skippedExercises: List<String>,
    val exercises: List<ExerciseEntry>,
    val totalVolumeKg: Int = 0,
    val prsHit: Int = 0
)

@Immutable
data class ExerciseEntry(
    val name: String,
    val targetMuscle: String = "",
    val restSeconds: Int = 90,
    val isSkipped: Boolean = false,
    val sets: List<SetEntry> = emptyList(),
    val isSuperset: Boolean = false,
    /** Copied from the exercise's definition when loaded; not stored per workout. */
    val type: ExerciseType = ExerciseType.WEIGHT_REPS
)

/** What a set of this exercise records. Belongs to the exercise, not to each workout. */
enum class ExerciseType(val label: String, val example: String) {
    WEIGHT_REPS("Weight & reps", "Barbell, dumbbell, machine, cable"),
    BODYWEIGHT("Bodyweight reps", "Push-ups, pull-ups, sit-ups"),
    WEIGHTED_BODYWEIGHT("Weighted bodyweight", "Dips or pull-ups with a belt: log the added weight"),
    ASSISTED("Assisted bodyweight", "Band or machine help: log the assistance"),
    DURATION("Time", "Planks, hangs, holds"),
    DISTANCE_TIME("Distance & time", "Running, rowing, cycling");

    val usesWeight: Boolean get() = this == WEIGHT_REPS || this == WEIGHTED_BODYWEIGHT || this == ASSISTED
    val usesReps: Boolean get() = usesWeight || this == BODYWEIGHT
    val usesTime: Boolean get() = this == DURATION || this == DISTANCE_TIME
    val usesDistance: Boolean get() = this == DISTANCE_TIME

    /** Weight suggestions ("go up to 62.5 kg") apply where more weight means harder. */
    val hasWeightSuggestions: Boolean get() = this == WEIGHT_REPS || this == WEIGHTED_BODYWEIGHT

    /** Volume is weight × reps; for weighted bodyweight only the added weight counts. */
    val countsVolume: Boolean get() = this == WEIGHT_REPS || this == WEIGHTED_BODYWEIGHT

    companion object {
        fun fromKey(key: String?): ExerciseType = entries.firstOrNull { it.name == key } ?: WEIGHT_REPS
    }
}

@Immutable
data class SetEntry(
    val setNumber: Int,
    val weight: String,
    val reps: String,
    val isWarmup: Boolean = false,
    val intensityType: String = "NORMAL", // NORMAL, WARMUP, DROP, REST_PAUSE
    val isCompleted: Boolean = false,
    val restSeconds: Int = 90,
    /** Time-based sets: seconds as typed digits ("" when unused). */
    val seconds: String = "",
    /** Distance sets: metres ("" when unused). */
    val distanceM: String = ""
)

val SetEntry.isWarmupSet: Boolean get() = isWarmup || intensityType == "WARMUP"
val SetEntry.weightKg: Float get() = parseWeightClean(weight, 0f)
val SetEntry.repCount: Int get() = parseRepsClean(reps, 0)

val SetEntry.secondCount: Int get() = seconds.filter(Char::isDigit).toIntOrNull() ?: 0
val SetEntry.distanceMeters: Float get() = distanceM.toFloatOrNull()?.coerceAtLeast(0f) ?: 0f

/** Non-warmup set with real numbers for [type] — the unit every stat and suggestion counts. */
fun SetEntry.isValidWorkSet(type: ExerciseType): Boolean = !isWarmupSet && when (type) {
    ExerciseType.WEIGHT_REPS -> weightKg > 0f && repCount > 0
    // Weighted with nothing added is still a bodyweight set; assistance can be zero too.
    ExerciseType.BODYWEIGHT, ExerciseType.WEIGHTED_BODYWEIGHT, ExerciseType.ASSISTED -> repCount > 0
    ExerciseType.DURATION -> secondCount > 0
    ExerciseType.DISTANCE_TIME -> distanceMeters > 0f || secondCount > 0
}

val ExerciseEntry.workSets: List<SetEntry> get() = sets.filter { it.isValidWorkSet(type) }

@Immutable
data class WorkoutStats(
    val workSets: Int,
    val volumeKg: Int,
    val prCount: Int
)

fun computeWorkoutStats(exercises: List<ExerciseEntry>, history: List<CompletedWorkout>): WorkoutStats {
    var workSets = 0
    var volume = 0f
    var prs = 0
    for (ex in exercises) {
        if (ex.isSkipped) continue
        val records = computeExerciseRecords(ex.name, history)
        for (s in ex.sets) {
            if (s.isValidWorkSet(ex.type) || (ex.type == ExerciseType.WEIGHT_REPS && !s.isWarmupSet && s.repCount > 0)) {
                workSets++
                if (ex.type.countsVolume) volume += s.weightKg * s.repCount
            }
            if (isSetPersonalRecord(s, records, ex.type)) prs++
        }
    }
    return WorkoutStats(workSets, volume.roundToInt(), prs)
}

@Immutable
data class WorkoutSplit(
    val title: String,
    val subtitle: String,
    val code: String
)

@Immutable
data class SplitProgramInfo(
    val id: String,
    val name: String,
    val tagline: String,
    val weeklyGoal: Int,
    val splits: List<WorkoutSplit>
)

@Immutable
data class WeekSummaryData(
    val weekOffset: Int,
    val titleLabel: String,
    val dateRangeLabel: String,
    val workouts: List<CompletedWorkout>,
    val completedCoreSplits: Set<String>,
    val recommendedSplit: String,
    val weeklyGoal: Int
)

enum class SuggestionTone { GOOD, CAUTION, NEUTRAL }

/** A weight recommendation shown on an exercise card, in plain words. */
@Immutable
data class Suggestion(
    val message: String,
    val tone: SuggestionTone,
    val suggestedWeight: String
)
