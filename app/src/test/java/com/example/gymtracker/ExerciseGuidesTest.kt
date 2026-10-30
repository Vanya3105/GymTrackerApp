package com.example.gymtracker

import org.junit.Assert.assertTrue
import org.junit.Test

class ExerciseGuidesTest {
    @Test
    fun everyLibraryExerciseHasAGuideAndMuscles() {
        val missing = ExerciseDatabase.all.filter { ExerciseGuides.find(it.name) == null }.map { it.name }
        assertTrue("No guide for $missing", missing.isEmpty())
        ExerciseDatabase.all.forEach { e ->
            val g = ExerciseGuides.find(e.name)!!
            assertTrue(e.name, g.steps.size in 3..5 && g.tip.isNotBlank())
            if (e.muscle != "Cardio") assertTrue("${e.name} works no muscle", muscleLoads("", e.name, e.muscle).any { it >= 1f })
        }
    }
}
