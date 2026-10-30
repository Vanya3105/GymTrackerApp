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
        "Hollow Body Hold", "Dead Hang",
        // The wider library
        "Knee Push-Up", "Archer Push-Up", "Negative Pull-Up", "Superman Hold", "Pike Push-Up", "Handstand Hold",
        "Shrimp Squat", "Cossack Squat", "Wall Sit", "Single-Leg Hip Thrust", "Fire Hydrant", "Curtsy Lunge",
        "Bodyweight Calf Raise", "Pogo Hops", "L-Sit", "Dragon Flag", "Bird Dog", "Bear Crawl", "Suitcase Carry",
        "Burpee", "Jumping Jacks", "High Knees", "Sprints", "Hiking", "Swimming", "Ski Erg", "Assault Bike"
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
