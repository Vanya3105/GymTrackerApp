package com.example.gymtracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutLogicTest {

    private fun set(weight: String, reps: String, warmup: Boolean = false, done: Boolean = true, type: String = if (warmup) "WARMUP" else "NORMAL") =
        SetEntry(setNumber = 1, weight = weight, reps = reps, isWarmup = warmup, intensityType = type, isCompleted = done)

    private fun workout(id: Long, split: String, vararg exercises: ExerciseEntry) = CompletedWorkout(
        id = id, splitTitle = split, dateKey = "", displayDate = "Oct $id, 2025 • 10:00", durationSeconds = 0,
        bodyWeightKg = 80f, photoPath = null, skippedExercises = emptyList(), exercises = exercises.toList()
    )

    // --- Input sanitizing ---

    @Test
    fun sanitizeDecimalInput_acceptsCommaDecimal() = assertEquals("62.5", sanitizeDecimalInput("62,5"))

    @Test
    fun sanitizeDecimalInput_dropsStrayCharacters() {
        assertEquals("40", sanitizeDecimalInput("40kg"))
        assertEquals("62.", sanitizeDecimalInput("62.")) // mid-typing state must be allowed
        assertEquals("", sanitizeDecimalInput(""))
    }

    @Test
    fun sanitizeDecimalInput_rejectsSecondDecimalPoint() {
        assertNull(sanitizeDecimalInput("..62.5"))
        assertNull(sanitizeDecimalInput("62.5,"))
    }

    // --- Set helpers & stats ---

    @Test
    fun isValidWorkSet_excludesWarmupsAndEmptySets() {
        assertTrue(set("60", "8").isValidWorkSet(ExerciseType.WEIGHT_REPS))
        assertFalse(set("30", "8", warmup = true).isValidWorkSet(ExerciseType.WEIGHT_REPS))
        assertFalse(set("60", "8", type = "WARMUP").isValidWorkSet(ExerciseType.WEIGHT_REPS)) // legacy: warmup only via intensityType
        assertFalse(set("0", "8").isValidWorkSet(ExerciseType.WEIGHT_REPS))
        assertFalse(set("60", "").isValidWorkSet(ExerciseType.WEIGHT_REPS))
    }

    @Test
    fun computeWorkoutStats_countsVolumeAndSkipsWarmupsAndSkippedExercises() {
        val exercises = listOf(
            ExerciseEntry("Bench", sets = listOf(set("30", "10", warmup = true), set("60", "8"), set("60", "6"))),
            ExerciseEntry("Fly", isSkipped = true, sets = listOf(set("20", "12")))
        )
        val stats = computeWorkoutStats(exercises, emptyList())
        assertEquals(2, stats.workSets)
        assertEquals(60 * 8 + 60 * 6, stats.volumeKg)
        assertEquals(0, stats.prCount) // no history → nothing to beat
    }

    @Test
    fun computeWorkoutStats_detectsPrAgainstHistory() {
        val history = listOf(workout(1, "Push", ExerciseEntry("Bench", sets = listOf(set("60", "9")))))
        val today = listOf(ExerciseEntry("Bench", sets = listOf(set("62.5", "8"), set("55", "8"))))
        assertEquals(1, computeWorkoutStats(today, history).prCount)
    }

    @Test
    fun exerciseRecords_useBestEstimated1Rm() {
        val history = listOf(
            workout(2, "Push", ExerciseEntry("Bench", sets = listOf(set("70", "3")))),
            workout(1, "Push", ExerciseEntry("Bench", sets = listOf(set("60", "10"))))
        )
        val rec = computeExerciseRecords("bench", history) // name match is case-insensitive
        assertEquals(70f, rec.maxWeightKg)
        assertEquals(80f, rec.bestEstimated1Rm) // 60 × (1 + 10/30) = 80 beats 70 × 1.1 = 77
        assertEquals("60", rec.bestSet?.weight)
        assertEquals("10", rec.bestSet?.reps)
    }

    // --- Split-isolated progression AI ---

    @Test
    fun suggestion_returnsNullWithoutHistory() =
        assertNull(suggestFromSplitHistory("Push", ExerciseEntry("Bench"), emptyList(), 2.5f))

    @Test
    fun suggestion_increasesAfterTwoSessionsOf8Plus() {
        val history = listOf(
            workout(2, "Push", ExerciseEntry("Bench", sets = listOf(set("60", "8")))),
            workout(1, "Push", ExerciseEntry("Bench", sets = listOf(set("60", "9"))))
        )
        val advice = suggestFromSplitHistory("Push", ExerciseEntry("Bench"), history, 2.5f)
        assertNotNull(advice)
        assertEquals(SuggestionTone.GOOD, advice!!.tone)
        assertEquals("62.5", advice.suggestedWeight)
    }

    @Test
    fun suggestion_dropsAfterTwoSessionsOf6OrLess() {
        val history = listOf(
            workout(2, "Push", ExerciseEntry("Bench", sets = listOf(set("70", "5")))),
            workout(1, "Push", ExerciseEntry("Bench", sets = listOf(set("70", "6"))))
        )
        val advice = suggestFromSplitHistory("Push", ExerciseEntry("Bench"), history, 2.5f)!!
        assertEquals(SuggestionTone.CAUTION, advice.tone)
        assertEquals("67.5", advice.suggestedWeight)
    }

    @Test
    fun suggestion_ignoresOtherSplits() {
        val history = listOf(workout(1, "Chest & Back", ExerciseEntry("Bench", sets = listOf(set("60", "8")))))
        assertNull(suggestFromSplitHistory("Push", ExerciseEntry("Bench"), history, 2.5f))
    }

    @Test
    fun suggestion_deloadsAfterThreeSessionsOfFallingVolume() {
        val history = listOf(
            workout(3, "Push", ExerciseEntry("Bench", sets = listOf(set("60", "7")))),
            workout(2, "Push", ExerciseEntry("Bench", sets = listOf(set("60", "8")))),
            workout(1, "Push", ExerciseEntry("Bench", sets = listOf(set("60", "9"))))
        )
        val advice = suggestFromSplitHistory("Push", ExerciseEntry("Bench"), history, 2.5f)!!
        assertTrue(advice.message.contains("lighter"))
        assertEquals("54", advice.suggestedWeight)
    }

    // --- Targets (sets × rep range) ---

    private val target = ExerciseTarget(3, 8, 12)

    @Test
    fun target_movesUpOnlyAtTheTopOfItsRange() {
        val tenAndTen = listOf(
            workout(2, "Push", ExerciseEntry("Bench", sets = listOf(set("60", "10")))),
            workout(1, "Push", ExerciseEntry("Bench", sets = listOf(set("60", "10"))))
        )
        // 10 reps is "8+" by default, but not the top of an 8–12 target.
        assertEquals(SuggestionTone.GOOD, suggestFromSplitHistory("Push", ExerciseEntry("Bench"), tenAndTen, 2.5f)!!.tone)
        assertEquals(SuggestionTone.NEUTRAL, suggestFromSplitHistory("Push", ExerciseEntry("Bench", target = target), tenAndTen, 2.5f)!!.tone)

        val twelves = listOf(
            workout(2, "Push", ExerciseEntry("Bench", sets = listOf(set("60", "12")))),
            workout(1, "Push", ExerciseEntry("Bench", sets = listOf(set("60", "12"))))
        )
        val up = suggestFromSplitHistory("Push", ExerciseEntry("Bench", target = target), twelves, 2.5f)!!
        assertEquals("62.5", up.suggestedWeight)
        assertTrue(up.message, up.message.contains("12+"))
    }

    @Test
    fun target_movesDownBelowTheBottomOfItsRange() {
        val sevens = listOf(
            workout(2, "Push", ExerciseEntry("Bench", sets = listOf(set("60", "7")))),
            workout(1, "Push", ExerciseEntry("Bench", sets = listOf(set("60", "7"))))
        )
        // 7 reps is fine by default (not "6 or fewer") but under an 8–12 target.
        assertEquals(SuggestionTone.NEUTRAL, suggestFromSplitHistory("Push", ExerciseEntry("Bench"), sevens, 2.5f)!!.tone)
        val down = suggestFromSplitHistory("Push", ExerciseEntry("Bench", target = target), sevens, 2.5f)!!
        assertEquals("57.5", down.suggestedWeight)
        assertTrue(down.message, down.message.contains("Under 8"))
    }

    @Test
    fun target_rangeShowsInTodaysAdvice() {
        val advice = suggestNextWeight("Push", ExerciseEntry("Bench", sets = listOf(set("60", "", done = false)), target = target), emptyList(), 2.5f)!!
        assertTrue(advice.message, advice.message.contains("8–12"))
        assertEquals("3 × 8–12", target.label)
        assertEquals("3 × 5", ExerciseTarget(3, 5, 5).label)
    }

    // --- Exercise library ---

    @Test
    fun exerciseSearch_findsNicknames() {
        assertEquals("Romanian Deadlift", ExerciseDatabase.search("rdl").first().name)
        assertEquals("Overhead Press", ExerciseDatabase.search("ohp").first().name)
        assertEquals("Skull Crusher", ExerciseDatabase.search("skull").first().name)
    }

    @Test
    fun exerciseSearch_ranksNamePrefixFirstAndMatchesEveryWord() {
        assertTrue(ExerciseDatabase.search("lat").first().name.startsWith("Lat"))
        // Nicknames can match too (chest-supported row is "incline row"), but name matches come first.
        val inclineDb = ExerciseDatabase.search("incline dumbbell")
        assertTrue(inclineDb.size >= 3)
        assertTrue(inclineDb.take(3).all { it.name.startsWith("Incline Dumbbell") })
    }

    @Test
    fun exerciseSearch_filtersByMuscle() {
        val calves = ExerciseDatabase.search("", muscle = "Calves")
        assertTrue(calves.isNotEmpty())
        assertTrue(calves.all { it.muscle == "Calves" })
        assertTrue(ExerciseDatabase.search("squat", muscle = "Chest").isEmpty())
    }

    @Test
    fun exerciseLibrary_hasUniqueNamesAndKnownMuscles() {
        val names = ExerciseDatabase.all.map { it.name.lowercase() }
        assertEquals(names.size, names.toSet().size)
        assertTrue(ExerciseDatabase.all.all { it.muscle in ExerciseDatabase.muscleGroups })
    }

    @Test
    fun defaultSplitTemplates_onlyUseLibraryExercises() {
        SPLIT_PROGRAMS.values.flatMap { it.splits }.forEach { split ->
            defaultTemplateForSplit(split.title).forEach { (name, muscle) ->
                assertNotNull("$name (${split.title}) missing from library", ExerciseDatabase.find(name))
                assertTrue(muscle.isNotEmpty())
            }
        }
    }
}
