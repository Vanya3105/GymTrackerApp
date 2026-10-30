package com.example.gymtracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExercisePhotosTest {
    /** Library exercises with no photo close enough to help; anything new must be mapped or listed here. */
    private val noPhoto = setOf(
        "Machine Pullover", "Machine Lateral Raise", "Lean-Away Lateral Raise", "Cable Y-Raise",
        "Bayesian Cable Curl", "Pendulum Squat", "Belt Squat", "Machine Hip Thrust",
        "Hollow Body Hold", "Dead Hang"
    )

    @Test
    fun everyMappedNameIsInTheLibrary() {
        val library = ExerciseDatabase.all.map { exerciseKey(it.name) }.toSet()
        val stray = ExercisePhotos.mappedNames - library
        assertTrue("Photos for names not in the library: $stray", stray.isEmpty())
    }

    @Test
    fun everyLibraryExerciseIsMappedOrKnownToHaveNone() {
        val unmapped = ExerciseDatabase.all.map { it.name }.filter { !ExercisePhotos.has(it) }.toSet()
        assertEquals(noPhoto, unmapped)
    }

    @Test
    fun caseDoesNotMatterAndCustomExercisesHaveNone() {
        assertEquals("Barbell_Bench_Press_-_Medium_Grip", ExercisePhotos.idFor(" barbell bench press"))
        assertNull(ExercisePhotos.idFor("My Own Exercise"))
    }
}
