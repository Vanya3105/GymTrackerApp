package com.example.gymtracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommendationsTest {
    private val beginner = UserProfile(experience = Experience.BEGINNER, goal = TrainingGoal.MUSCLE, equipment = Equipment.GYM, daysPerWeek = 3)

    @Test
    fun everyNamedExerciseIsInTheLibrary() {
        val names = ALTERNATIVES.keys + ALTERNATIVES.values.flatten() + BEGINNER_EASIER.keys + BEGINNER_EASIER.values +
            FOR_MUSCLE.keys + FOR_MUSCLE.values
        val missing = names.filter { ExerciseDatabase.find(it) == null }.distinct()
        assertEquals(emptyList<String>(), missing)
        val hard = HARD_ON.values.flatten().filter { n -> ExerciseDatabase.all.none { it.name.equals(n, ignoreCase = true) } }
        assertEquals(emptyList<String>(), hard)
    }

    @Test
    fun withoutAnswersNothingChanges() {
        val day = defaultTemplateForSplit("Push A")
        assertEquals(day, personalize(day, UserProfile()))
        assertNull(prescriptionFor(UserProfile(), "Back Squat"))
        assertNull(recommendedProgram(UserProfile()))
    }

    @Test
    fun recommendedProgramsExistAndFitTheWeek() {
        for (level in Experience.entries) for (goal in TrainingGoal.entries) for (eq in Equipment.entries) for (days in 2..6) {
            val (id, why) = recommendedProgram(UserProfile(experience = level, goal = goal, equipment = eq, daysPerWeek = days))!!
            assertTrue(id, SPLIT_PROGRAMS.containsKey(id))
            assertTrue(why.isNotBlank())
            // Barbell percentage programs only with a gym.
            if (eq != Equipment.GYM) assertTrue(id, id !in setOf("WENDLER_531", "GZCLP", "NSUNS_5", "PHUL"))
        }
        assertEquals("FULL_BODY", recommendedProgram(beginner)!!.first)
    }

    @Test
    fun everyBuiltInDayFitsHomeEquipment() {
        val days = SPLIT_PROGRAMS.values.flatMap { p -> p.splits.map { it.title } }.distinct()
        for (eq in listOf(Equipment.DUMBBELLS, Equipment.BODYWEIGHT)) {
            val profile = UserProfile(experience = Experience.INTERMEDIATE, goal = TrainingGoal.MUSCLE, equipment = eq)
            for (title in days) {
                val day = personalize(defaultTemplateForSplit(title), profile)
                assertTrue("$title with $eq is empty", day.isNotEmpty())
                day.forEach { (name, _) -> assertTrue("$name on $title doesn't fit $eq", fitsEquipment(name, eq)) }
                assertEquals("$title has a repeat", day.size, day.map { exerciseKey(it.first) }.distinct().size)
            }
        }
    }

    @Test
    fun limitsSwapHardLifts() {
        val profile = beginner.copy(experience = Experience.INTERMEDIATE, limits = setOf(Limit.LOWER_BACK, Limit.SHOULDERS))
        val day = personalize(defaultTemplateForSplit("Upper Power"), profile).map { it.first }
        assertTrue(day.toString(), "Barbell Row" !in day && "Overhead Press" !in day)
        assertTrue(day.toString(), "Chest-Supported Row" in day)
    }

    @Test
    fun beginnersAndMuscleGoalGetTheirVersions() {
        val day = personalize(listOf("Weighted Pull-Up" to "Back", "Lying Leg Curl" to "Hamstrings", "Tricep Pushdown" to "Triceps"), beginner).map { it.first }
        assertEquals(listOf("Lat Pulldown", "Seated Leg Curl", "Overhead Cable Tricep Extension"), day)
        // A swap never doubles up an exercise already on the day.
        val both = personalize(listOf("Lying Leg Curl" to "Hamstrings", "Seated Leg Curl" to "Hamstrings"), beginner).map { it.first }
        assertEquals(listOf("Lying Leg Curl", "Seated Leg Curl"), both)
    }

    @Test
    fun prescriptionsFollowGoalAndLevel() {
        assertEquals(Prescription(3, 8, 12, 120), prescriptionFor(beginner, "Back Squat"))
        assertEquals(Prescription(2, 10, 15, 90), prescriptionFor(beginner, "Lateral Raise"))
        val strong = UserProfile(experience = Experience.ADVANCED, goal = TrainingGoal.STRENGTH)
        assertEquals(Prescription(5, 3, 6, 180), prescriptionFor(strong, "Deadlift"))
        assertEquals(Prescription(3, 8, 12, 90), prescriptionFor(strong, "Lateral Raise"))
    }
}
