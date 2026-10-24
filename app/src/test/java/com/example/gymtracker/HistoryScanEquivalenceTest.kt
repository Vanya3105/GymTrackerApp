package com.example.gymtracker

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.roundToInt
import kotlin.random.Random

/** The faster history scans must give exactly what the straightforward versions gave. */
class HistoryScanEquivalenceTest {
    private val now = 1_800_000_000_000L
    private val day = 86_400_000L
    private val names = listOf(
        "Bench Press", "bench press", "BENCH PRESS", "Bench Press ", "Barbell Row", "Lat Pulldown", "Back Squat",
        "Leg Curl", "Bicep Curl", "Lateral Raise", "İncline Press", "incline press", "Plank", "Pull-Up", "Dip"
    )
    private val muscles = listOf("", "Chest", "Back", "Legs", "Shoulders", "Arms")
    private val types = ExerciseType.entries

    private fun randomHistory(rnd: Random): List<CompletedWorkout> {
        val list = (0 until rnd.nextInt(0, 60)).map { i ->
            CompletedWorkout(
                id = now - rnd.nextLong(0, 40 * day),
                splitTitle = listOf("Push", "Pull", "Legs", "Upper")[rnd.nextInt(4)],
                dateKey = "", displayDate = "Mon, Jan ${i + 1}", durationSeconds = 3600, bodyWeightKg = 80f,
                photoPath = null, skippedExercises = emptyList(),
                exercises = (0 until rnd.nextInt(0, 6)).map {
                    ExerciseEntry(
                        name = names[rnd.nextInt(names.size)],
                        targetMuscle = muscles[rnd.nextInt(muscles.size)],
                        isSkipped = rnd.nextInt(8) == 0,
                        type = types[rnd.nextInt(types.size)],
                        sets = (0 until rnd.nextInt(0, 5)).map { s ->
                            val warm = rnd.nextInt(5) == 0
                            SetEntry(
                                s + 1, if (rnd.nextInt(6) == 0) "" else "${rnd.nextInt(0, 120)}",
                                if (rnd.nextInt(6) == 0) "" else "${rnd.nextInt(0, 15)}",
                                isWarmup = warm, intensityType = if (warm) "WARMUP" else "NORMAL", isCompleted = true,
                                seconds = if (rnd.nextBoolean()) "${rnd.nextInt(0, 120)}" else "",
                                distanceM = if (rnd.nextBoolean()) "${rnd.nextInt(0, 5000)}" else ""
                            )
                        }
                    )
                }
            )
        }
        // Usually stored newest first; sometimes not, which must not change the answer.
        return if (rnd.nextInt(4) == 0) list.shuffled(rnd) else list.sortedByDescending { it.id }
    }

    // The versions before the optimization, kept as references.
    private fun recoveryReference(history: List<CompletedWorkout>): List<MuscleRecoveryItem> {
        val weekAgo = now - 7L * day
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
            if (lastTrained == null) MuscleRecoveryItem(group, 100, RecoveryStatus.READY, null, weeklySets)
            else {
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

    private fun loggedNamesReference(history: List<CompletedWorkout>): List<String> =
        history.sortedByDescending { it.id }
            .flatMap { w -> w.exercises.filter { ex -> !ex.isSkipped && ex.workSets.isNotEmpty() }.map { it.name } }
            .distinctBy { exerciseKey(it) }

    @Test
    fun sameResultsOnRandomHistories() {
        val rnd = Random(42)
        repeat(400) { round ->
            val h = randomHistory(rnd)
            assertEquals("recovery, round $round", recoveryReference(h), computeMuscleRecoveryStatus(h, now))
            val names = loggedExerciseNames(h)
            assertEquals("logged names, round $round", loggedNamesReference(h), names)
            val byExercise = historyByExercise(h)
            for (name in names + "Not Logged") {
                assertEquals(
                    "records for $name, round $round",
                    computeExerciseRecords(name, h),
                    computeExerciseRecords(name, byExercise[ignoreCaseKey(name)].orEmpty())
                )
            }
        }
    }

    @Test
    fun ignoreCaseKey_matchesEqualsIgnoreCase() {
        val samples = names + listOf("İ", "i", "I", "ı", "Straße", "STRASSE", "ǅ", "ǆ", "Ǆ")
        for (a in samples) for (b in samples) {
            assertEquals("$a / $b", a.equals(b, ignoreCase = true), ignoreCaseKey(a) == ignoreCaseKey(b))
        }
    }
}
