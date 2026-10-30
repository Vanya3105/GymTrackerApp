package com.example.gymtracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryTest {
    private val hour = 3_600_000L
    private val now = 1_800_000_000_000L

    private fun loads(name: String, muscle: String = "", split: String = "Any") =
        RECOVERY_GROUPS.zip(muscleLoads(split, name, muscle).toList()).filter { it.second > 0f }.toMap()

    private fun workout(hoursAgo: Int, vararg exercises: Pair<String, Int>) = CompletedWorkout(
        id = now - hoursAgo * hour, splitTitle = "Any", dateKey = "", displayDate = "", durationSeconds = 0,
        bodyWeightKg = 80f, photoPath = null, skippedExercises = emptyList(),
        exercises = exercises.map { (name, sets) ->
            ExerciseEntry(name, ExerciseDatabase.find(name)?.muscle ?: "", sets = List(sets) { SetEntry(it + 1, "50", "8", isCompleted = true) })
        }
    )

    private fun status(history: List<CompletedWorkout>) = computeMuscleRecoveryStatus(history, now).associateBy { it.muscleGroup }

    // --- Which muscles an exercise works ---

    @Test
    fun armsAreThreeSeparateGroups() {
        assertEquals(mapOf("Biceps" to 1f, "Forearms" to 0.25f), loads("Barbell Curl"))
        assertEquals(mapOf("Triceps" to 1f), loads("Rope Pushdown"))
        assertEquals(mapOf("Forearms" to 1f), loads("Wrist Curl"))
        assertEquals(0.5f, loads("Hammer Curl")["Forearms"])
    }

    @Test
    fun compoundLiftsLoadTheirHelpersAtHalf() {
        assertEquals(mapOf("Chest" to 1f, "Triceps" to 0.5f, "Shoulders" to 0.5f), loads("Barbell Bench Press"))
        assertEquals(mapOf("Back" to 1f, "Biceps" to 0.5f, "Shoulders" to 0.25f, "Forearms" to 0.25f), loads("Barbell Row"))
        assertEquals(mapOf("Back" to 1f, "Biceps" to 0.5f, "Forearms" to 0.25f), loads("Lat Pulldown"))
        assertEquals(mapOf("Shoulders" to 1f, "Triceps" to 0.5f), loads("Overhead Press"))
        assertEquals(mapOf("Quads" to 1f, "Glutes" to 0.5f), loads("Back Squat"))
        val deadlift = loads("Deadlift")
        assertEquals(0.5f, deadlift["Forearms"])
        assertEquals(0.5f, deadlift["Hamstrings"])
        assertEquals(mapOf("Hamstrings" to 1f, "Glutes" to 0.5f, "Back" to 0.25f, "Forearms" to 0.25f), loads("Romanian Deadlift"))
    }

    @Test
    fun isolationLiftsLoadOneMuscle() {
        assertEquals(mapOf("Chest" to 1f), loads("Cable Fly"))
        assertEquals(mapOf("Quads" to 1f), loads("Leg Extension"))
        assertEquals(mapOf("Shoulders" to 1f), loads("Lateral Raise"))
        assertEquals(mapOf("Calves" to 1f), loads("Standing Calf Raise"))
        assertEquals(mapOf("Back" to 1f), loads("Straight-Arm Pulldown"))
    }

    @Test
    fun cardioLoadsNothing() = assertTrue(loads("Running").isEmpty())

    @Test
    fun customExercises_guessedFromTheirNameAndTag() {
        assertEquals(1f, loads("Cable Bicep Curl")["Biceps"])
        assertEquals(1f, loads("My Hanging Leg Raise")["Core"])
        assertEquals(1f, loads("45° Hyperextension")["Back"])
        assertEquals(1f, loads("Odd Lift", muscle = "Triceps")["Triceps"])
        assertEquals(1f, loads("Mystery", split = "Push Day")["Chest"])
        // Old "Arms" tag with no clue in the name: half to each.
        assertEquals(mapOf("Biceps" to 0.5f, "Triceps" to 0.5f), loads("Mystery", muscle = "Arms"))
    }

    // --- Recovery over time ---

    @Test
    fun backDayTiresTheBicepsAndForearmsToo() {
        val s = status(listOf(workout(12, "Barbell Row" to 4, "Lat Pulldown" to 4)))
        assertEquals(RecoveryStatus.TIRED, s["Back"]!!.status)
        assertTrue(s["Biceps"]!!.recoveryPercent < 100)
        assertTrue(s["Forearms"]!!.recoveryPercent < 100)
        assertEquals(100, s["Triceps"]!!.recoveryPercent)
        assertEquals(4, s["Biceps"]!!.weeklyWorkingSets) // 8 sets × 0.5
    }

    @Test
    fun smallMusclesRecoverFasterThanBigOnes() {
        val s = status(listOf(workout(36, "Barbell Curl" to 6, "Back Squat" to 6)))
        assertTrue(s["Biceps"]!!.recoveryPercent > s["Quads"]!!.recoveryPercent)
        assertEquals(RecoveryStatus.READY, s["Biceps"]!!.status)
    }

    @Test
    fun moreSetsTakeLonger() {
        val few = status(listOf(workout(30, "Barbell Bench Press" to 3)))["Chest"]!!.recoveryPercent
        val many = status(listOf(workout(30, "Barbell Bench Press" to 10)))["Chest"]!!.recoveryPercent
        assertTrue("$few vs $many", few > many)
    }

    @Test
    fun sessionsCloseTogetherStack() {
        val once = status(listOf(workout(40, "Back Squat" to 5)))["Quads"]!!.recoveryPercent
        val twice = status(listOf(workout(20, "Back Squat" to 5), workout(40, "Back Squat" to 5)))["Quads"]!!.recoveryPercent
        assertTrue("$once vs $twice", twice < once)
    }

    @Test
    fun gripWorkAloneIsntTraining() {
        val s = status(listOf(workout(5, "Lat Pulldown" to 2)))["Forearms"]!!
        assertNull(s.hoursSinceTrained) // 2 × 0.25 sets
        assertTrue(s.recoveryPercent > 85)
    }

    @Test
    fun fullyRecoveredAfterTheWindow() {
        val s = status(listOf(workout(110, "Back Squat" to 12)))
        assertEquals(100, s["Quads"]!!.recoveryPercent)
        assertEquals(110, s["Quads"]!!.hoursSinceTrained)
    }
}
