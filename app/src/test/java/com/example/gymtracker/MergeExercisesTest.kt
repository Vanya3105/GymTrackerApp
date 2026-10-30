package com.example.gymtracker

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** One exercise logged under two names, joined back into one. */
@RunWith(RobolectricTestRunner::class)
class MergeExercisesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        LocalStorage.resetForTests()
        context.deleteDatabase("gymtracker.db")
        context.filesDir.listFiles()?.forEach { it.delete() }
    }

    @After
    fun tearDown() = LocalStorage.resetForTests()

    private fun set(n: Int, w: String, r: String) = SetEntry(n, w, r, isCompleted = true)
    private fun workout(id: Long, vararg exercises: ExerciseEntry) = CompletedWorkout(
        id = id, splitTitle = "Legs", dateKey = "", displayDate = "", durationSeconds = 3600, bodyWeightKg = 80f,
        photoPath = null, skippedExercises = emptyList(), exercises = exercises.toList()
    )

    @Test
    fun mergeMovesHistoryAndJoinsAWorkoutThatHadBoth() {
        LocalStorage.saveCompletedWorkout(context, workout(1, ExerciseEntry("Back Squat", "Quads", sets = listOf(set(1, "100", "5")))))
        LocalStorage.saveCompletedWorkout(context, workout(2, ExerciseEntry("Barbell Back Squat", "Quads", sets = listOf(set(1, "105", "5")))))
        LocalStorage.saveCompletedWorkout(
            context,
            workout(
                3,
                ExerciseEntry("Barbell Back Squat", "Quads", sets = listOf(set(1, "110", "3"))),
                ExerciseEntry("Back Squat", "Quads", sets = listOf(set(1, "90", "8"), set(2, "90", "8")))
            )
        )
        LocalStorage.saveExerciseNote(context, "Back Squat", "Safety pins at 6")
        LocalStorage.setTrainingMax(context, "Back Squat", 120f)
        LocalStorage.saveTemplateForSplit(context, "Legs", listOf("Back Squat" to "Quads", "Leg Press" to "Quads"))
        LocalStorage.saveTemplateForSplit(context, "Lower", listOf("Barbell Back Squat" to "Quads", "Back Squat" to "Quads"))

        assertTrue(LocalStorage.mergeExercise(context, "Back Squat", "Barbell Back Squat"))

        val history = LocalStorage.loadHistory(context)
        val names = history.flatMap { w -> w.exercises.map { it.name } }
        assertFalse("no Back Squat left", names.any { it == "Back Squat" })
        val both = history.first { it.id == 3L }.exercises
        assertEquals(1, both.size)
        assertEquals(listOf("110", "90", "90"), both.single().sets.map { it.weight })
        assertEquals(listOf(1, 2, 3), both.single().sets.map { it.setNumber })
        assertEquals("Barbell Back Squat", history.first { it.id == 1L }.exercises.single().name)

        // Its note and training max come along, since the kept one had none.
        assertEquals("Safety pins at 6", LocalStorage.getExerciseNote(context, "Barbell Back Squat"))
        assertEquals(120f, LocalStorage.getTrainingMax(context, "Barbell Back Squat")?.kg)
        assertNull(LocalStorage.getTrainingMax(context, "Back Squat"))
        // Day lists: renamed in place, or dropped where the day already had the kept one.
        assertEquals(listOf("Barbell Back Squat", "Leg Press"), LocalStorage.loadTemplateForSplit(context, "Legs").map { it.first })
        assertEquals(listOf("Barbell Back Squat"), LocalStorage.loadTemplateForSplit(context, "Lower").map { it.first })
        // A copy of the data was kept first.
        assertTrue(context.filesDir.listFiles().orEmpty().any { it.name.startsWith("before-import-") })
    }

    @Test
    fun theRunningWorkoutFollows() {
        LocalStorage.saveCompletedWorkout(context, workout(1, ExerciseEntry("Back Squat", "Quads", sets = listOf(set(1, "100", "5")))))
        LocalStorage.saveCompletedWorkout(context, workout(2, ExerciseEntry("Barbell Back Squat", "Quads", sets = listOf(set(1, "105", "5")))))
        LocalStorage.saveActiveSession(
            context, "Legs",
            listOf(ExerciseEntry("Barbell Back Squat", sets = listOf(set(1, "105", "5"))), ExerciseEntry("Back Squat", sets = listOf(set(1, "95", "8"))))
        )
        assertTrue(LocalStorage.mergeExercise(context, "Back Squat", "Barbell Back Squat"))
        val today = LocalStorage.loadActiveSession(context)!!.second
        assertEquals(listOf("Barbell Back Squat"), today.map { it.name })
        assertEquals(listOf("105", "95"), today.single().sets.map { it.weight })
    }

    @Test
    fun refusesMissingOrSameExercise() {
        LocalStorage.saveCompletedWorkout(context, workout(1, ExerciseEntry("Back Squat", "Quads", sets = listOf(set(1, "100", "5")))))
        assertFalse(LocalStorage.mergeExercise(context, "Back Squat", "back squat"))
        assertFalse(LocalStorage.mergeExercise(context, "Back Squat", "Front Squat"))
    }

    @Test
    fun likelyDuplicates_onlyNamesThatAreReallyTheSame() {
        val pairs = likelyDuplicates(
            listOf(
                "Back Squat", "Barbell Back Squat", // "barbell" adds nothing
                "Tricep Pushdown", "Triceps Pushdowns", // plural
                "Pull-Up", "Pull Ups", // hyphen
                "Romanian Deadlift", "RDL", // library nickname
                "Barbell Bench Press", "Incline Barbell Bench Press", "Comp Bench Press", // different lifts
                "Dumbbell Row", "Barbell Row"
            )
        ).map { setOf(it.first, it.second) }.toSet()
        assertEquals(
            setOf(
                setOf("Back Squat", "Barbell Back Squat"),
                setOf("Tricep Pushdown", "Triceps Pushdowns"),
                setOf("Pull-Up", "Pull Ups"),
                setOf("Romanian Deadlift", "RDL")
            ),
            pairs
        )
    }

    @Test
    fun mergeEntries_whenOnlyTheMergedOneIsThere() {
        val list = listOf(ExerciseEntry("Leg Press"), ExerciseEntry("Back Squat", sets = listOf(set(1, "100", "5"))))
        assertEquals(listOf("Leg Press", "Barbell Back Squat"), mergeEntries(list, "Back Squat", "Barbell Back Squat").map { it.name })
    }
}
