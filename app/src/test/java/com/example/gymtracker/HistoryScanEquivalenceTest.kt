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

    // Straightforward versions without the early exit and caching, kept as references.
    private fun recoveryReference(history: List<CompletedWorkout>): List<MuscleRecoveryItem> {
        val weekAgo = now - 7L * day
        val base = floatArrayOf(48f, 72f, 48f, 36f, 42f, 30f, 72f, 72f, 60f, 36f, 30f)
        return RECOVERY_GROUPS.mapIndexed { g, group ->
            var weeklySets = 0f
            var readiness = 1f
            var lastTrained: Long? = null
            for (workout in history) {
                // Summed in Float, in the same order, so rounding matches exactly.
                var sets = 0f
                for (ex in workout.exercises.filter { !it.isSkipped }) {
                    sets += ex.sets.count { !it.isWarmupSet && (it.repCount > 0 || it.isValidWorkSet(ex.type)) } *
                        muscleLoads(workout.splitTitle, ex.name, ex.targetMuscle)[g]
                }
                if (sets <= 0f) continue
                if (sets >= 1f && (lastTrained == null || workout.id > lastTrained)) lastTrained = workout.id
                if (workout.id < weekAgo) continue
                weeklySets += sets
                readiness *= 1f - recoveryLeft(base[g], sets, ((now - workout.id) / 3_600_000f).coerceAtLeast(0f))
            }
            val pct = (readiness * 100f).roundToInt().coerceIn(12, 100)
            val status = when {
                pct < 55 -> RecoveryStatus.TIRED
                pct < 85 -> RecoveryStatus.RECOVERING
                else -> RecoveryStatus.READY
            }
            val hoursAgo = lastTrained?.let { ((now - it) / (1000L * 60 * 60)).toInt().coerceAtLeast(0) }
            MuscleRecoveryItem(group, pct, status, hoursAgo, weeklySets.roundToInt())
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
