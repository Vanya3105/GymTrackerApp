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

    private fun everyone(): List<UserProfile> = buildList {
        for (level in Experience.entries) for (goal in TrainingGoal.entries) for (eq in Equipment.entries) for (days in 2..6)
            for (minutes in listOf(30, 45, 60, 90)) for (focus in Focus.entries)
                add(UserProfile(experience = level, goal = goal, equipment = eq, daysPerWeek = days, sessionMinutes = minutes, focus = focus))
    }

    @Test
    fun everyAnswerGetsAPlanThatFitsTheRules() {
        for (p in everyone()) {
            val (id, why) = recommendedProgram(p)!!
            assertEquals(RECOMMENDED_PLAN_ID, id)
            assertTrue(why.isNotBlank())
            val week = planWeek(p)
            assertEquals("$p", p.daysPerWeek, week.size)
            // Every big muscle at least twice a week (each day of a 2-day week trains it).
            for (m in MAJOR_MUSCLES) assertTrue("$m in $p", week.count { m in it.muscles } >= 2)
            for (day in week) {
                assertTrue("${day.title} has ${day.exercises.size} for $p", day.exercises.size >= minOf(3, day.muscles.size))
                day.exercises.forEach { e ->
                    assertTrue("${e.name} doesn't fit ${p.equipment}", fitsEquipment(e.name, p.equipment))
                    assertTrue("${e.name} not in the library", ExerciseDatabase.find(e.name) != null)
                }
                assertEquals("repeat on ${day.title} for $p", day.exercises.size, day.exercises.map { exerciseKey(it.name) }.distinct().size)
                // Never a day of one muscle when everything is trained.
                assertTrue("${day.title} trains only ${day.muscles} for $p", day.muscles.count { it in MAJOR_MUSCLES } >= 2)
            }
        }
    }

    @Test
    fun differentAnswersGiveDifferentPlans() {
        fun fingerprint(p: UserProfile) = planWeek(p).map { d -> d.title to d.exercises.map { "${it.name} ${it.target.label}" } }
        val gym = everyone().filter { it.equipment == Equipment.GYM && it.sessionMinutes == 60 && it.focus == Focus.BALANCED }
        // Experience, goal and days each change the plan, whatever the others are.
        val plans = gym.map(::fingerprint)
        assertEquals(gym.size, plans.distinct().size)
        // More experience: more weekly sets for size.
        fun setsFor(level: Experience, goal: TrainingGoal) =
            planWeek(UserProfile(experience = level, goal = goal, equipment = Equipment.GYM, daysPerWeek = 4, sessionMinutes = 90))
                .flatMap { it.exercises }.filter { it.muscle == "Chest" }.sumOf { it.target.sets }
        assertTrue(setsFor(Experience.BEGINNER, TrainingGoal.MUSCLE) < setsFor(Experience.INTERMEDIATE, TrainingGoal.MUSCLE))
        assertTrue(setsFor(Experience.INTERMEDIATE, TrainingGoal.MUSCLE) < setsFor(Experience.ADVANCED, TrainingGoal.MUSCLE))
        assertTrue(setsFor(Experience.INTERMEDIATE, TrainingGoal.GENERAL) < setsFor(Experience.INTERMEDIATE, TrainingGoal.MUSCLE))
        // Shorter sessions: fewer exercises a day.
        fun perDay(minutes: Int) = planWeek(UserProfile(experience = Experience.ADVANCED, goal = TrainingGoal.MUSCLE, equipment = Equipment.GYM, daysPerWeek = 3, sessionMinutes = minutes))
            .sumOf { it.exercises.size }
        assertTrue(perDay(30) < perDay(90))
    }

    @Test
    fun skippingBigMusclesMakesAPlanThatTrainsTheRestTwiceAWeek() {
        val skips = listOf(
            setOf("Quads", "Hamstrings", "Glutes", "Calves"), setOf("Chest"), setOf("Chest", "Shoulders", "Triceps"),
            setOf("Back", "Biceps"), setOf("Chest", "Back", "Shoulders", "Biceps", "Triceps", "Forearms")
        )
        for (skip in skips) for (days in 2..6) for (eq in Equipment.entries) {
            val p = UserProfile(experience = Experience.INTERMEDIATE, goal = TrainingGoal.MUSCLE, equipment = eq, daysPerWeek = days, skipMuscles = skip)
            val week = planWeek(p)
            assertEquals(days, week.size)
            for (m in trainedMuscles(p) - "Core" - "Forearms" - "Calves") {
                assertTrue("$m only ${week.count { m in it.muscles }}x in $days days skipping $skip", week.count { m in it.muscles } >= minOf(2, days))
            }
            week.forEachIndexed { i, day ->
                val ex = planDayExercises(p, i)
                assertTrue("${day.title} empty, $eq skipping $skip", ex.isNotEmpty())
                ex.forEach { (name, muscle) -> assertTrue("$name on ${day.title} trains skipped $muscle", muscle !in skip) }
            }
        }
    }

    @Test
    fun skippingSmallMusclesDropsThem() {
        val p = beginner.copy(skipMuscles = setOf("Calves", "Core"))
        assertTrue(planWeek(p).flatMap { it.exercises }.none { it.muscle == "Calves" || it.muscle == "Core" })
        val legs = personalize(defaultTemplateForSplit("Legs A"), p)
        assertTrue(legs.none { it.second == "Calves" })
    }

    @Test
    fun noLegsOnFourDaysIsPushPull() {
        val p = beginner.copy(experience = Experience.INTERMEDIATE, daysPerWeek = 4, skipMuscles = setOf("Quads", "Hamstrings", "Glutes", "Calves"))
        assertEquals(listOf("Push A", "Pull A", "Push B", "Pull B"), generatedPlan(p)!!.splits.map { it.title })
    }

    @Test
    fun aFocusGetsMoreSetsSessionsAndGoesFirst() {
        for (focus in Focus.entries - Focus.BALANCED) for (days in 2..6) {
            val base = UserProfile(experience = Experience.INTERMEDIATE, goal = TrainingGoal.MUSCLE, equipment = Equipment.GYM, daysPerWeek = days, sessionMinutes = 90)
            val balanced = planWeek(base)
            val focused = planWeek(base.copy(focus = focus))
            fun sets(week: List<PlannedDay>) = week.flatMap { it.exercises }.filter { it.muscle in focus.muscles }.sumOf { it.target.sets }
            assertTrue("$focus $days days: ${sets(focused)} vs ${sets(balanced)}", sets(focused) > sets(balanced))
            // First exercise of any day that trains a priority muscle is for one.
            focused.filter { d -> d.exercises.any { it.muscle in focus.muscles } }.forEach { d ->
                assertTrue("$focus ${d.title} starts with ${d.exercises.first()}", d.exercises.first().muscle in focus.muscles)
            }
            // More sessions for the priority, except where it gets its own day instead (chest & back, shoulders & arms, legs).
            if (days >= 4 && focus != Focus.ARMS && layoutFor(days, base.experience!!, base.goal!!, focus) != Layout.ARNOLD) {
                val m = focus.muscles.first()
                assertTrue("$focus $days days", focused.count { m in it.muscles } > balanced.count { m in it.muscles } ||
                    focused.count { m in it.muscles } >= 3)
            }
        }
    }
}
